"""Conservative resource admission for sequential, CPU-only local models.

Usage: python desktop/astra_resource_plan.py primary.gguf [critic.gguf]
This is a planning heuristic, not a measured inference benchmark.
"""
import argparse
import ctypes
import json
import os
from pathlib import Path
import shutil
import sys
from dataclasses import dataclass

MIB = 1024 * 1024
GIB = 1024 * MIB

@dataclass(frozen=True)
class Resources:
    available_ram: int
    logical_cpus: int
    free_disk: int

def available_memory():
    if sys.platform == "win32":
        class MemoryStatus(ctypes.Structure):
            _fields_ = [
                ("length", ctypes.c_ulong), ("load", ctypes.c_ulong),
                ("total_phys", ctypes.c_ulonglong), ("avail_phys", ctypes.c_ulonglong),
                ("total_page", ctypes.c_ulonglong), ("avail_page", ctypes.c_ulonglong),
                ("total_virtual", ctypes.c_ulonglong), ("avail_virtual", ctypes.c_ulonglong),
                ("avail_extended_virtual", ctypes.c_ulonglong),
            ]
        status = MemoryStatus()
        status.length = ctypes.sizeof(status)
        if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
            raise OSError("Unable to measure available physical memory.")
        return int(status.avail_phys)
    if sys.platform.startswith("linux"):
        for line in Path("/proc/meminfo").read_text().splitlines():
            if line.startswith("MemAvailable:"):
                return int(line.split()[1]) * 1024
    raise OSError("No supported available-memory probe; admission refused.")

def probe(workspace):
    return Resources(available_memory(), os.cpu_count() or 1,
                     shutil.disk_usage(workspace).free)

def model_file(path):
    path = Path(path)
    if not path.is_file() or path.suffix.lower() != ".gguf":
        raise ValueError("Expected an existing local GGUF file.")
    with path.open("rb") as source:
        if source.read(4) != b"GGUF":
            raise ValueError("Invalid GGUF header.")
    size = path.stat().st_size
    if size < 1024:
        raise ValueError("GGUF file too small.")
    return path.name, size

def plan(resources, sizes, context=2048):
    if not 512 <= context <= 4096:
        raise ValueError("Pilot context must be between 512 and 4096 tokens.")
    if resources.available_ram <= 0 or resources.logical_cpus <= 0:
        raise ValueError("Resource measurements must be positive.")
    if resources.free_disk < 0:
        raise ValueError("Invalid disk measurement.")
    reserve = 1536 * MIB
    usable = max(0, resources.available_ram - reserve)
    # File bytes do not equal runtime RAM. A deliberately conservative
    # allowance is used until actual model peak memory is measured.
    scratch = (512 + context // 4) * MIB
    rows = []
    for name, size in sizes:
        if not isinstance(size, int) or size <= 0:
            raise ValueError("Model size must be a positive byte count.")
        estimate = (size * 5 + 3) // 4 + scratch
        rows.append({
            "model": name,
            "file_mib": round(size / MIB, 1),
            "estimated_peak_mib": round(estimate / MIB, 1),
            "admitted": estimate <= usable,
        })
    if not rows:
        raise ValueError("At least one model is required.")
    return {
        "mode": "sequential_cpu_only",
        "available_ram_mib": round(resources.available_ram / MIB, 1),
        "reserved_ram_mib": reserve // MIB,
        "free_disk_mib": resources.free_disk // MIB,
        "threads": max(1, min(2, resources.logical_cpus - 1)),
        "context_tokens": context,
        "models": rows,
        "all_admitted_individually": all(row["admitted"] for row in rows),
        "warning": "Estimated only. Reprobe before each load. Never load concurrently.",
    }


# Keep the full response allowance. Context reduction is optional and never
# drops evidence or silently shortens the response budget.
ADAPTIVE_CONTEXTS = (4096, 3072, 2048, 1536)
PROMPT_OVERHEAD = 256

def adaptive_plan(resources, size, prompt_bytes, *, generation_tokens=512, observed=None):
    """Choose largest admissible context; byte-count screening is heuristic.

    Observations are per-model, per-context high-water working-set counters.
    They can raise admission requirements, never lower the static estimate.
    Tokenizer/template overhead is not known until native inference.
    """
    if type(size) is not int or size <= 0 or type(prompt_bytes) is not int or prompt_bytes <= 0:
        raise ValueError("Invalid model size or prompt byte count.")
    if type(generation_tokens) is not int or not 1 <= generation_tokens <= 512:
        raise ValueError("Invalid generation allowance.")
    if observed is None:
        observed = {}
    if type(observed) is not dict or any(
        key not in ADAPTIVE_CONTEXTS or type(value) is not int or value <= 0
        for key,value in observed.items()
    ):
        raise ValueError("Invalid observed-memory profile.")
    usable = max(0, resources.available_ram - 1536*MIB)
    for context in ADAPTIVE_CONTEXTS:
        if prompt_bytes + PROMPT_OVERHEAD + generation_tokens > context:
            continue
        admission = plan(resources, [("local_model",size)], context=context)
        if not admission["all_admitted_individually"]:
            continue
        # Only reuse a measurement from the same or a smaller context;
        # larger-context observations must not prevent a smaller retry.
        peaks = [value for key,value in observed.items() if key <= context]
        measured_floor = (max(peaks)*5+3)//4 if peaks else 0
        if measured_floor > usable:
            continue
        return {"context_tokens":context,"generation_tokens":generation_tokens,
                "threads":admission["threads"],"available_ram_mib":admission["available_ram_mib"],
                "measured_floor_bytes":measured_floor,
                "scope":"Conservative admission heuristic; no tokenizer or OS memory-limit guarantee."}
    raise RuntimeError("No adaptive context fits the prompt and available memory; narrow the question or free RAM.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("models", nargs="+")
    parser.add_argument("--context", type=int, default=2048)
    args = parser.parse_args(argv)
    try:
        sizes = [model_file(path) for path in args.models]
        result = plan(probe(Path.cwd()), sizes, args.context)
    except (OSError, ValueError) as failure:
        print(str(failure), file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2))
    return 0 if result["all_admitted_individually"] else 1

if __name__ == "__main__":
    sys.exit(main())

"""Explicitly approved local text-generation adapter for the desktop orchestrator.

The adapter uses a trusted llama-cli binary, sequential CPU-only inference,
resource admission, bounded inputs and a finite subprocess timeout. It does not
grant tools, write knowledge, or interpret model output as instructions.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import threading

from astra_local_finance import file_digest
from astra_native_subprocess import run_bounded
from astra_resource_plan import model_file, plan, probe, adaptive_plan, ADAPTIVE_CONTEXTS, PROMPT_OVERHEAD, MIB
from astra_orchestrator import MAX_PROMPT_BYTES, MAX_REPLY_BYTES

CONTEXT = 4096
GENERATE_TOKENS = 512


def stamp(path):
    metadata = path.stat()
    return metadata.st_size, metadata.st_mtime_ns, metadata.st_ino


class LocalAnswerRunner:
    def __init__(self, binary, primary, critic=None, *, timeout=180, approved=False, context_policy="fixed"):
        if approved is not True:
            raise ValueError("Explicit local model approval required.")
        self.binary, primary = Path(binary).resolve(), Path(primary).resolve()
        if not self.binary.is_file() or self.binary.name.lower() not in ("llama-cli", "llama-cli.exe"):
            raise ValueError("Trusted local llama-cli executable required.")
        if type(timeout) is not int or not 30 <= timeout <= 300:
            raise ValueError("Timeout outside supported range.")
        if context_policy not in ("fixed", "adaptive"):
            raise ValueError("Unknown inference context policy.")
        self.context_policy = context_policy
        model_file(primary)
        self.models = {"primary": primary}
        if critic is not None:
            critic = Path(critic).resolve()
            model_file(critic)
            if primary == critic or file_digest(primary) == file_digest(critic):
                raise ValueError("Independent critic requires distinct weights.")
            self.models["critic"] = critic
        self.timeout = timeout
        self.last_profile = {}
        self._observed = {role:{} for role in self.models}
        self._stamps = {path: stamp(path) for path in (self.binary, *self.models.values())}
        self._lock = threading.Lock()

    def _check_files(self):
        if any(stamp(path) != saved for path, saved in self._stamps.items()):
            raise RuntimeError("Runtime or model changed; reconfigure before use.")

    def __call__(self, role, prompt, *, timeout_seconds=None):
        # A caller may reduce, never extend, the configured native timeout.
        if timeout_seconds is not None and (type(timeout_seconds) is not int
                                            or not 1 <= timeout_seconds <= self.timeout):
            raise ValueError("Invalid per-call timeout override.")
        effective_timeout = self.timeout if timeout_seconds is None else timeout_seconds
        if role not in self.models or not isinstance(prompt, str):
            raise ValueError("Requested local model unavailable.")
        payload = prompt.encode("utf-8")
        if not payload or len(payload) > MAX_PROMPT_BYTES or b"\x00" in payload:
            raise ValueError("Prompt outside local byte budget.")
        with self._lock:
            self.last_profile.clear()
            self.last_profile.update(role=role, context_policy=self.context_policy,
                                     outcome="preflight_file_check", prompt_bytes=len(payload))
            self._check_files()
            model = self.models[role]
            name, size = model_file(model)
            self.last_profile.update(outcome="resource_probe_failed",model_mib=round(size/MIB,1))
            resources = probe(model.parent)
            self.last_profile.update(available_ram_mib=round(resources.available_ram/MIB,1))
            context = CONTEXT
            if self.context_policy == "adaptive":
                eligible=[c for c in ADAPTIVE_CONTEXTS
                          if len(payload)+PROMPT_OVERHEAD+GENERATE_TOKENS<=c]
                if not eligible:
                    self.last_profile["outcome"]="prompt_context_budget_exceeded"
                    raise RuntimeError("Prompt exceeds conservative adaptive context allowance.")
                minimum=min(eligible)
                estimate=plan(resources,[("local_model",size)],context=minimum)
                peaks=[v for k,v in self._observed[role].items() if k<=minimum]
                measured=(max(peaks)*5+3)//4 if peaks else 0
                self.last_profile.update(outcome="memory_admission_refused",
                    minimum_context_tokens=minimum,
                    required_ram_mib=round(1536+max(estimate["models"][0]["estimated_peak_mib"],measured/MIB),1))
                admission = adaptive_plan(resources,size,len(payload),generation_tokens=GENERATE_TOKENS,
                                          observed=self._observed[role])
                context = admission["context_tokens"]
            else:
                admission = plan(resources, [(name, size)], context=CONTEXT)
                self.last_profile.update(outcome="memory_admission_refused",minimum_context_tokens=CONTEXT,
                    required_ram_mib=round(1536+admission["models"][0]["estimated_peak_mib"],1))
                if not admission["all_admitted_individually"]:
                    raise RuntimeError("Insufficient available RAM.")
            self.last_profile["outcome"]="prompt_preparation_failed"
            threads = str(admission["threads"])
            environment = {key: value for key, value in os.environ.items()
                           if not key.upper().startswith(("LLAMA_", "HF_", "HUGGINGFACE_", "MTMD_"))}
            with tempfile.TemporaryDirectory(prefix="astra-answer-") as directory:
                path = Path(directory) / "prompt.txt"
                path.write_bytes(payload)
                command = [str(self.binary), "-m", str(model), "-f", str(path),
                           "--offline", "--no-mmproj", "--no-escape", "--log-disable",
                           "--single-turn", "--no-display-prompt", "--simple-io",
                           "--color", "off", "--no-show-timings", "--reasoning", "off",
                           "--cache-ram", "0", "--no-context-shift",
                           "-c", str(context), "-n", str(GENERATE_TOKENS),
                           "-ngl", "0", "-t", threads, "-tb", threads,
                           "--temp", "0", "--seed", "42"]
                try:
                    process = run_bounded(command, timeout=effective_timeout,
                                             env=environment, cwd=directory,
                                             max_stdout_bytes=MAX_REPLY_BYTES,
                                             telemetry=self.last_profile)
                except subprocess.TimeoutExpired:
                    raise RuntimeError("Local answer generation timed out.") from None
                finally:
                    self.last_profile.update(role=role,context_policy=self.context_policy,
                                             context_tokens=context,generation_tokens=GENERATE_TOKENS,
                                             threads=int(threads),available_ram_mib=admission["available_ram_mib"])
                    peak = self.last_profile.get("observed_peak_working_set_bytes")
                    if type(peak) is int and peak>0:
                        self._observed[role][context] = max(peak,self._observed[role].get(context,0))
                if process.returncode or len(process.stdout) > MAX_REPLY_BYTES:
                    raise RuntimeError("Local answer generation failed.")
                self._check_files()
                return process.stdout.decode("utf-8", errors="strict").strip()

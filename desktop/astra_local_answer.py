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
from astra_native_subprocess import run_external
from astra_resource_plan import model_file, plan, probe
from astra_orchestrator import MAX_PROMPT_BYTES, MAX_REPLY_BYTES

CONTEXT = 4096
GENERATE_TOKENS = 512


def stamp(path):
    metadata = path.stat()
    return metadata.st_size, metadata.st_mtime_ns, metadata.st_ino


class LocalAnswerRunner:
    def __init__(self, binary, primary, critic=None, *, timeout=180, approved=False):
        if approved is not True:
            raise ValueError("Explicit local model approval required.")
        self.binary, primary = Path(binary).resolve(), Path(primary).resolve()
        if not self.binary.is_file() or self.binary.name.lower() not in ("llama-cli", "llama-cli.exe"):
            raise ValueError("Trusted local llama-cli executable required.")
        if type(timeout) is not int or not 30 <= timeout <= 300:
            raise ValueError("Timeout outside supported range.")
        model_file(primary)
        self.models = {"primary": primary}
        if critic is not None:
            critic = Path(critic).resolve()
            model_file(critic)
            if primary == critic or file_digest(primary) == file_digest(critic):
                raise ValueError("Independent critic requires distinct weights.")
            self.models["critic"] = critic
        self.timeout = timeout
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
            self._check_files()
            model = self.models[role]
            name, size = model_file(model)
            admission = plan(probe(model.parent), [(name, size)], context=CONTEXT)
            if not admission["all_admitted_individually"]:
                raise RuntimeError("Insufficient available RAM.")
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
                           "-c", str(CONTEXT), "-n", str(GENERATE_TOKENS),
                           "-ngl", "0", "-t", threads, "-tb", threads,
                           "--temp", "0", "--seed", "42"]
                try:
                    process = run_external(command, runner=subprocess.run, stdin=subprocess.DEVNULL,
                                             stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                             timeout=effective_timeout, shell=False, check=False,
                                             env=environment, cwd=directory)
                except subprocess.TimeoutExpired:
                    raise RuntimeError("Local answer generation timed out.") from None
                if process.returncode or len(process.stdout) > MAX_REPLY_BYTES:
                    raise RuntimeError("Local answer generation failed.")
                self._check_files()
                return process.stdout.decode("utf-8", errors="strict").strip()

"""Content-free diagnostics for one native child, not an OS memory limit."""
import ctypes
from pathlib import Path
import sys


def linux_memory(pid):
    values = {}
    for line in Path(f"/proc/{pid}/status").read_text(encoding="ascii").splitlines():
        key, sep, rest = line.partition(":")
        if sep and key in ("VmRSS", "VmHWM"):
            fields = rest.split()
            if len(fields) == 2 and fields[1] == "kB":
                values[key] = int(fields[0]) * 1024
    if "VmRSS" not in values:
        return None
    return values["VmRSS"], values.get("VmHWM", values["VmRSS"])


def windows_memory(pid):
    from ctypes import wintypes
    class Counters(ctypes.Structure):
        _fields_ = [("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD)] + [
            (name, ctypes.c_size_t) for name in (
                "PeakWorkingSetSize", "WorkingSetSize", "QuotaPeakPagedPoolUsage",
                "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage", "QuotaNonPagedPoolUsage",
                "PagefileUsage", "PeakPagefileUsage")]
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    open_process = kernel.OpenProcess
    open_process.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    open_process.restype = wintypes.HANDLE
    close = kernel.CloseHandle
    close.argtypes = [wintypes.HANDLE]
    close.restype = wintypes.BOOL
    query = kernel.K32GetProcessMemoryInfo
    query.argtypes = [wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD]
    query.restype = wintypes.BOOL
    handle = open_process(0x1000, False, pid)
    if not handle:
        return None
    try:
        counters = Counters()
        counters.cb = ctypes.sizeof(counters)
        if query(handle, ctypes.byref(counters), counters.cb):
            return int(counters.WorkingSetSize), int(counters.PeakWorkingSetSize)
    finally:
        close(handle)
    return None


def memory(pid):
    if type(pid) is not int or pid <= 0:
        return None
    try:
        if sys.platform == "win32":
            return windows_memory(pid)
        if sys.platform.startswith("linux"):
            return linux_memory(pid)
    except (OSError, ValueError, AttributeError, OverflowError):
        return None
    return None


class NativeProfile:
    def __init__(self):
        self.samples = 0
        self.peak_bytes = None

    def sample(self, pid):
        row = memory(pid)
        if row is not None:
            self.samples += 1
            self.peak_bytes = max(self.peak_bytes or 0, *row)

    def report(self, elapsed, outcome, output_bytes=None):
        return {"elapsed_seconds": round(max(0, elapsed), 3),
                "outcome": outcome, "output_bytes": output_bytes,
                "memory_samples": self.samples,
                "observed_peak_working_set_bytes": self.peak_bytes,
                "scope": "Direct child OS counters only; no prompt, model path, output content, process-tree peak or memory-limit enforcement."}

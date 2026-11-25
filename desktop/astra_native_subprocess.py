"""Keep external Windows native runtimes separate from frozen app DLL paths."""
import ctypes
import ntpath
import subprocess
import sys
import threading

_LOCK = threading.Lock()


def clean_environment(env, bundle):
    result = {}
    root = ntpath.normcase(ntpath.abspath(bundle))
    for key, value in env.items():
        if key.upper().startswith(("_PYI_", "PYINSTALLER_")):
            continue
        if key.upper() == "PATH":
            entries = []
            for entry in value.split(";"):
                path = entry.strip().strip('"')
                if not path:
                    continue
                try:
                    inside = ntpath.commonpath([root, ntpath.normcase(ntpath.abspath(path))]) == root
                except ValueError:
                    inside = False
                if not inside:
                    entries.append(entry)
            value = ";".join(entries)
        result[key] = value
    return result


def spawn_windows(command, *, bundle, env, popen, set_directory, **kwargs):
    # The DLL directory is process-global. Limit its change to child creation.
    with _LOCK:
        if not set_directory(None):
            raise OSError("Cannot reset DLL search directory.")
        child = None
        try:
            child = popen(command, env=clean_environment(env, bundle), **kwargs)
        finally:
            if not set_directory(bundle):
                if child is not None:
                    child.kill()
                    child.wait()
                raise OSError("Cannot restore bundled DLL search directory.")
    return child


def run_external(command, *, timeout, env, runner=subprocess.run, check=False, **kwargs):
    if sys.platform != "win32" or not getattr(sys, "frozen", False):
        return runner(command, timeout=timeout, env=env, check=check, **kwargs)
    bundle = getattr(sys, "_MEIPASS", None)
    if not isinstance(bundle, str) or not ntpath.isabs(bundle):
        raise RuntimeError("Frozen application DLL directory unavailable.")
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    setter = kernel.SetDllDirectoryW
    setter.argtypes = [ctypes.c_wchar_p]
    setter.restype = ctypes.c_int
    child = spawn_windows(command, bundle=bundle, env=env,
                          popen=subprocess.Popen, set_directory=setter, **kwargs)
    with child:
        try:
            stdout, stderr = child.communicate(timeout=timeout)
        except BaseException:
            child.kill()
            child.wait()
            raise
        result = subprocess.CompletedProcess(command, child.returncode, stdout, stderr)
        if check:
            result.check_returncode()
        return result

"""Keep external Windows native runtimes separate from frozen app DLL paths."""
import ctypes
import ntpath
import subprocess
import sys
import threading
import queue
import time

from astra_native_profile import NativeProfile

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


def _start(command, env, **kwargs):
    if sys.platform != "win32" or not getattr(sys, "frozen", False):
        return subprocess.Popen(command, env=env, **kwargs)
    bundle = getattr(sys, "_MEIPASS", None)
    if not isinstance(bundle, str) or not ntpath.isabs(bundle):
        raise RuntimeError("Frozen application DLL directory unavailable.")
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    setter = kernel.SetDllDirectoryW
    setter.argtypes = [ctypes.c_wchar_p]
    setter.restype = ctypes.c_int
    return spawn_windows(command, bundle=bundle, env=env,
                         popen=subprocess.Popen, set_directory=setter, **kwargs)


def run_bounded(command, *, timeout, env, max_stdout_bytes, cwd, clock=time.monotonic, telemetry=None):
    """Read at most limit + 1 bytes, reject excess, never return partial output.

    Timeout includes startup and reading, except an OS process-creation call
    cannot itself be interrupted. The directly launched trusted child is killed
    on failure; this is not a process-tree sandbox. A descendant retaining the
    pipe can delay the daemon reader's exit without blocking the caller.
    """
    if type(max_stdout_bytes) is not int or not 1 <= max_stdout_bytes <= 1048576:
        raise ValueError("Invalid stdout byte limit.")
    if type(timeout) is not int or not 1 <= timeout <= 300:
        raise ValueError("Invalid native timeout.")
    started = clock()
    deadline = started + timeout
    profile = NativeProfile()
    outcome = 'launch_error'
    output_bytes = None
    if telemetry is not None:
        telemetry.clear()
    try:
        child = _start(command, env, cwd=cwd, stdin=subprocess.DEVNULL,
                       stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                       shell=False, bufsize=0)
    except BaseException:
        if telemetry is not None:
            telemetry.update(profile.report(clock()-started,outcome))
        raise
    result = queue.Queue(maxsize=1)

    def reader():
        data = bytearray()
        try:
            with child.stdout as stream:
                while True:
                    chunk = stream.read(min(4096, max_stdout_bytes+1-len(data)))
                    if not chunk:
                        result.put(("ok", bytes(data)))
                        return
                    data.extend(chunk)
                    if len(data)>max_stdout_bytes:
                        result.put(("overflow", None))
                        return
        except Exception:
            result.put(("read_error", None))

    try:
        threading.Thread(target=reader, daemon=True, name="astra-native-output").start()
        while True:
            if telemetry is not None:
                profile.sample(child.pid)
            remaining = deadline-clock()
            if remaining<=0:
                raise subprocess.TimeoutExpired(command, timeout)
            try:
                status, output = result.get(timeout=min(0.2, remaining))
                break
            except queue.Empty:
                continue
        if status!="ok":
            outcome = "output_limit" if status=="overflow" else "read_error"
            raise RuntimeError("Native output exceeds limit." if status=="overflow" else
                               "Native output could not be read.")
        remaining = deadline-clock()
        if remaining<=0:
            raise subprocess.TimeoutExpired(command, timeout)
        while True:
            if telemetry is not None:
                profile.sample(child.pid)
            remaining = deadline-clock()
            if remaining<=0:
                raise subprocess.TimeoutExpired(command, timeout)
            try:
                code = child.wait(timeout=min(0.2,remaining))
                break
            except subprocess.TimeoutExpired:
                continue
        outcome = 'success' if code==0 else 'nonzero_exit'
        output_bytes = len(output)
        return subprocess.CompletedProcess(command, code, output, None)
    except BaseException as failure:
        if isinstance(failure,subprocess.TimeoutExpired):
            outcome = 'timeout'
        elif outcome=='launch_error':
            outcome = 'execution_error'
        if child.poll() is None:
            child.kill()
        # Bound cleanup as well; do not wait indefinitely for descendants.
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            raise RuntimeError("Native child termination could not be confirmed.") from None
        raise
    finally:
        if telemetry is not None:
            telemetry.update(profile.report(clock()-started,outcome,output_bytes))


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

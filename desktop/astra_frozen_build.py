"""Build the unsigned Windows x64 onedir pilot with pinned PyInstaller."""
import importlib.metadata
from pathlib import Path
import struct
import subprocess
import sys
import tempfile

from astra_desktop_distribution import CORE, GROUPS

ROOT = Path(__file__).resolve().parent
PIN = "6.22.3"


def hidden_imports(root=ROOT):
    names = {Path(n).stem for n in CORE if n.endswith(".py")}
    for files in GROUPS.values():
        if all((root/n).is_file() for n in files):
            names.update(Path(n).stem for n in files if n.endswith(".py"))
    return sorted(names | {"astra_frozen_smoke"})


def build(destination):
    if sys.platform != "win32" or struct.calcsize("P") != 8:
        raise RuntimeError("Windows x64 Python required.")
    if importlib.metadata.version("pyinstaller") != PIN:
        raise RuntimeError("Pinned PyInstaller version required.")
    destination = Path(destination).resolve()
    destination.mkdir(parents=True, exist_ok=False)
    with tempfile.TemporaryDirectory(prefix="astra-freeze-") as work:
        cmd = [sys.executable, "-m", "PyInstaller", "--clean", "--onedir",
               "--console", "--noupx", "--name", "AstraDesktop",
               "--distpath", str(destination), "--workpath", str(Path(work)/"work"),
               "--specpath", work, "--paths", str(ROOT)]
        for name in hidden_imports():
            cmd.extend(("--hidden-import", name))
        cmd.append(str(ROOT/"astra_launcher.py"))
        subprocess.run(cmd, cwd=ROOT, check=True, timeout=600)
    exe = destination/"AstraDesktop"/"AstraDesktop.exe"
    if not exe.is_file():
        raise RuntimeError("Expected executable missing.")
    subprocess.run([str(exe), "--smoke-test"], cwd=exe.parent, check=True, timeout=60)
    return exe


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Supply one new build directory.")
    print(build(sys.argv[1]))

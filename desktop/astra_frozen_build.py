"""Build the unsigned Windows x64 onedir pilot with pinned PyInstaller."""
import hashlib
import json
import platform
import os
import shutil
import importlib.metadata
from pathlib import Path
import struct
import subprocess
import sys
import tempfile

from astra_desktop_distribution import CORE, GROUPS, collect

ROOT = Path(__file__).resolve().parent
PIN = "6.22.3"
MAX_BYTES = 200 * 1024 * 1024
FORBIDDEN = {".gguf", ".sqlite", ".sqlite3", ".db", ".pem", ".key", ".pfx", ".env"}


def hidden_imports(root=ROOT):
    names = {Path(n).stem for n in CORE if n.endswith(".py")}
    for files in GROUPS.values():
        if all((root/n).is_file() for n in files):
            names.update(Path(n).stem for n in files if n.endswith(".py"))
    return sorted(names | {"astra_frozen_smoke"})


def bundle_manifest(folder):
    folder = Path(folder)
    if not (folder/"AstraDesktop.exe").is_file():
        raise ValueError("Expected executable missing.")
    files = []
    total = 0
    for path in sorted(folder.rglob("*")):
        if path.is_symlink():
            raise ValueError("Unexpected symlink.")
        if not path.is_file():
            continue
        if path.suffix.lower() in FORBIDDEN or path.name.lower() in ("credentials.json", ".env"):
            raise ValueError("Unexpected model, credential or database.")
        size = path.stat().st_size
        total += size
        if total > MAX_BYTES:
            raise ValueError("Bundle exceeds 200 MiB uncompressed budget.")
        digest = hashlib.sha256()
        with path.open("rb") as source:
            for block in iter(lambda: source.read(1024*1024), b""):
                digest.update(block)
        files.append({"path":path.relative_to(folder).as_posix(),
                      "bytes":size,"sha256":digest.hexdigest()})
    return {"format":"astra-windows-frozen-pilot-v1","bytes":total,
            "files":files,"signature":"unsigned",
            "scope":"Integrity only; no publisher authentication or hardware validation."}


def verify(folder, manifest):
    if bundle_manifest(folder) != manifest:
        raise ValueError("Bundle contents do not match manifest.")
    return True



def acceptance(exe, *, cwd, env=None):
    """Require a real frozen receipt with every declared engine and GUI import."""
    command=[str(exe), "--complete-smoke-test"]
    process=subprocess.run(command,cwd=cwd,env=env,check=True,timeout=90,
                           capture_output=True,encoding="utf-8",errors="strict")
    receipt=json.loads(process.stdout)
    if type(receipt) is not dict or any(receipt.get(key) is not True for key in
            ("passed","frozen","require_complete","require_gui")):
        raise ValueError("Expected successful complete frozen acceptance receipt.")
    checks=receipt.get("checks")
    required={"exact_finance","reviewed_evidence","finance_session_import",
              "assistant_gui_import","tkinter_import","analytics_gui_import",
              "analytics_present","professional_present","professional_renderer",
              "portfolio_analytics"}
    required.update("example_"+group for group in GROUPS
                    if group not in ("analytics","professional-report"))
    if type(checks) is not dict or not required.issubset(checks) or any(
            value is not True for value in checks.values()):
        raise ValueError("Incomplete frozen acceptance checks.")
    return receipt


def relocated_acceptance(folder, manifest):
    """Execute a verified copy outside the checkout with a system-only PATH."""
    system_root=os.environ.get("SystemRoot")
    if not system_root:
        raise RuntimeError("Windows system root unavailable.")
    verify(folder,manifest)
    env={key:value for key,value in os.environ.items()
         if key.upper() not in ("PATH","PYTHONHOME","PYTHONPATH")
         and not key.upper().startswith(("_PYI_","PYINSTALLER_"))}
    env["PATH"]=os.pathsep.join((str(Path(system_root)/"System32"),system_root))
    with tempfile.TemporaryDirectory(prefix="astra acceptance ") as workspace:
        root=Path(workspace)
        target=root/"Astra relocated"
        shutil.copytree(folder,target)
        verify(target,manifest)
        receipt=acceptance(target/"AstraDesktop.exe",cwd=root,env=env)
        verify(target,manifest)
    return receipt


def build(destination):
    if sys.platform != "win32" or struct.calcsize("P") != 8 or platform.machine().lower() not in ("amd64", "x86_64"):
        raise RuntimeError("Windows x64 Python required.")
    if importlib.metadata.version("pyinstaller") != PIN:
        raise RuntimeError("Pinned PyInstaller version required.")
    _, included = collect(ROOT)
    if set(included) != set(GROUPS):
        raise ValueError("Complete frozen release requires all eight analytical engines and both reporting groups.")
    destination = Path(destination).resolve()
    destination.mkdir(parents=True, exist_ok=False)
    with tempfile.TemporaryDirectory(prefix="astra-freeze-") as work:
        cmd = [sys.executable, "-m", "PyInstaller", "--clean", "--onedir",
               "--console", "--noupx", "--name", "AstraDesktop",
               "--distpath", str(destination), "--workpath", str(Path(work)/"work"),
               "--specpath", work, "--paths", str(ROOT)]
        for name in hidden_imports():
            cmd.extend(("--hidden-import", name))
        for group, files in GROUPS.items():
            if group not in ("analytics", "professional-report") and all((ROOT/n).is_file() for n in files):
                cmd.extend(("--add-data",str(ROOT/files[2])+":examples"))
        cmd.append(str(ROOT/"astra_launcher.py"))
        subprocess.run(cmd, cwd=ROOT, check=True, timeout=600)
    exe = destination/"AstraDesktop"/"AstraDesktop.exe"
    if not exe.is_file():
        raise RuntimeError("Expected executable missing.")
    manifest = bundle_manifest(exe.parent)
    original = acceptance(exe,cwd=exe.parent)
    verify(exe.parent,manifest)
    relocated = relocated_acceptance(exe.parent,manifest)
    (destination/"MANIFEST.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    (destination/"ACCEPTANCE.json").write_text(json.dumps({"original":original,"relocated":relocated},indent=2)+"\n",encoding="utf-8")
    verify(exe.parent,manifest)
    return exe


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Supply one new build directory.")
    print(build(sys.argv[1]))

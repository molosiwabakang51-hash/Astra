"""Build the unsigned Windows x64 onedir pilot with pinned PyInstaller."""
import hashlib
import json
import importlib.metadata
from pathlib import Path
import struct
import subprocess
import sys
import tempfile

from astra_desktop_distribution import CORE, GROUPS

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
    manifest = bundle_manifest(exe.parent)
    (destination/"MANIFEST.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    verify(exe.parent,manifest)
    return exe


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Supply one new build directory.")
    print(build(sys.argv[1]))

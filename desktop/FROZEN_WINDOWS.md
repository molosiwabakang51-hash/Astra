# Windows x64 executable pilot

The frozen build uses Python 3.11 and PyInstaller 6.22.3 on the pinned Windows Server 2025 CI image. The build host needs internet access to acquire the packaging dependency. The resulting onedir runtime does not require a separately installed Python interpreter or Tkinter. Model weights, llama-cli and personal data are excluded.

Build on an approved Windows x64 checkout:

~~~powershell
python -m pip install --only-binary=:all: pyinstaller==6.22.3
python desktop/astra_frozen_build.py frozen-dist
~~~

The command produces frozen-dist/AstraDesktop/AstraDesktop.exe and frozen-dist/MANIFEST.json. Keep the entire AstraDesktop directory intact, including _internal and its runtime libraries. The executable is intentionally console-enabled for pilot diagnostics. Run --diagnostics or --smoke-test to inspect module availability and synthetic functional checks. Launch normally to open the desktop workspace selector. Workspace switching invokes the same executable with a fixed workspace argument; no shell or automatic model loading.

The build refuses bundles over 200 MiB uncompressed and common private-data file types, records per-file sizes and SHA-256 digests, and verifies manifest consistency. CI uploads the directory only after successful build, verification and repeat smoke tests. These are integrity checks, not a digital signature, independently audited supply chain, or clean-machine acceptance test. Only the main PyInstaller version is pinned; transitive dependencies are not yet hash-locked.

The frozen artifact contains only analytical modules present on its build branch. A successful partial build must not be described as the complete Astra distribution. Native llama-cli interoperability, DLL search behavior, actual GUI interaction, startup time, peak memory, offline operation on the owner's computer, full module integration, third-party redistribution notices and licensing review remain release blockers. Do not distribute commercially until those gates are satisfied. The source ZIP remains the smaller fallback.

External local inference now uses a frozen-Windows launch adapter: it removes bundle-specific PATH entries, temporarily resets the process DLL search directory during child creation, and restores it immediately. Source Python execution retains its existing subprocess behavior. The adapter does not provide a sandbox or proof of llama-cli compatibility; the DLL setting is process-global and physical native-runtime tests remain mandatory. See https://pyinstaller.org/en/v6.22.3/common-issues-and-pitfalls.html.

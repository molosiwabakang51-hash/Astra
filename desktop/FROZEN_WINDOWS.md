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

## Adaptive local inference (opt-in)

The Runtime tab exposes an experimental adaptive context setting. It chooses the largest admissible context from 4096, 3072, 2048 and 1536 tokens using available RAM, the existing conservative model estimate and per-role observed working-set peaks with a 25% margin. It preserves the 512-token generation allowance, does not truncate the input, and refuses requests when no context passes the conservative UTF-8 byte screening. Byte counts are not a tokenizer guarantee. Observations stay in memory and are reset when models are reconfigured. Fixed context remains the default and is used by existing native benchmarks. Latest metrics exposes the most recent direct-child invocation without prompt or answer content. The policy is a heuristic, not an enforced memory limit or a measured quality or speed improvement.

## Request-wide reasoning budget
The Knowledge tab now applies a request budget (1–900 seconds) to approved generation. Without independent review it permits one primary call; when review is enabled it may add a critic only if the estimated time fits both the remaining budget and the configured native timeout. Estimates use recent session observations, including failures. Contextual evidence receipts remain intact; a skipped critic does not certify the primary draft. The Runtime tab can explicitly export up to 24 content-free timing receipts to a new JSON file. These exports do not contain prompts, answers, file paths or verified accuracy scores and are not reimported for routing. Model reconfiguration clears the in-memory history. Fixed native evaluation uses an updated source fingerprint; earlier benchmark configurations are distinct.

## Task-form classification and experimental routing validation
The Knowledge workspace displays a conservative English request-form label (comparison, quantitative, procedure, evaluation, explanation, contextual, context reset or unclassified). This is advisory; it never changes explicit review consent or enters the exported timing history. Source-checkout benchmark: `python desktop/astra_native_benchmark.py <llama-cli> <primary.gguf> <critic.gguf> --suite routing --approve-models`. Four seeds are chosen before inference; two are calibration and two validation. All 48 generated questions require 288 sequential native calls, so this is an expensive optional experiment, not a startup check. Each family needs >=2 net cooperative wins, <=1 loss and <=1.5x total arm latency in both splits. Reports expose aggregate correctness and timings, not user prompts. The small synthetic families and caller-supplied observations cannot authorize deployment or establish general intelligence; there is no automatic specialist selection. Actual native execution and broader independent validation are outstanding.

## Complete distribution gate
The integrated Windows build requires all eight analytic engine groups and both analytics/professional reporting groups before starting PyInstaller. Both Windows jobs use `--complete-smoke-test` to require all eight bundled examples and professional report generation. The ordinary `--smoke-test` remains a partial-installation diagnostic. The complete gate is necessary but not sufficient for release: native model compatibility, GUI operation, supply-chain review and physical resource measurements are still required.

## Relocated executable acceptance
The Windows CI packaging job copies the complete onedir bundle to a fresh runner-temporary path containing spaces, verifies its manifest, removes Python-specific environment paths, and runs the strict executable smoke test from outside the repository. Publication requires a receipt reporting frozen=true, require_complete=true and passed=true. This checks packaging independence from an installed Python on that runner, not compatibility with every clean Windows machine or the owner's CPU/RAM. The packaging workflow has explicit integration-branch push and PR review triggers; the branch remains experimental until the actual Windows job succeeds.

## Consolidated Windows workflow
One Windows job now runs the desktop regression suite and publishes the verified source archive, then builds the frozen pilot. The frozen build itself verifies both original and relocated complete acceptance receipts and writes ACCEPTANCE.json alongside MANIFEST.json. The previous two-job workflow failed before creating jobs; the exact GitHub workflow validation message was unavailable. This change is a targeted simplification, not evidence that the Windows build has passed. Source-only regression tests continue to verify extracted archive functionality. Native inference on physical hardware is still outstanding.

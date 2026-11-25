# Astra Desktop unified pilot

The orchestrator currently routes explicit JSON requests. It is not yet a natural-language router or full chat client. Run from the repository root.

For a local JSON request file containing:

```json
{"kind":"finance","calculation":{"operation":"present_value","inputs":{"amount":"121","rate":"0.1","periods":2}}}
```

Run:

```powershell
python desktop/astra_desktop.py request.json
```

The result is exactly 100 under the stated assumptions, without any model or internet connection.

For reviewed knowledge search, use an existing local Astra evidence ledger and a request such as:

```json
{"kind":"retrieve","question":"bond duration"}
```

```powershell
python desktop/astra_desktop.py request.json --ledger evidence.sqlite
```

For optional model-generated drafts use an answer request:

```json
{"kind":"answer","question":"Explain bond duration","review":true}
```

```powershell
python desktop/astra_desktop.py request.json --ledger evidence.sqlite --approve-models --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf" --critic "C:\Models\critic.gguf"
```

Use trusted local compatible binaries and distinct model files. The critic is optional when review is false. The pipeline abstains without matching evidence, rejects invalid citation identifiers, rechecks all evidence shown to the models before releasing a draft, and labels the result unverified. A critic finding no issue is not proof of correctness. Evidence can contain misleading instructions; prompts treat it as untrusted data but cannot guarantee model compliance. No model-produced content can change the ledger or execute tools through this interface.

The Python Orchestrator interface also accepts a consented vector query callback and compatible index identity. The command-line entry currently uses lexical retrieval only; native embedding CLI integration awaits separate validation.

CPU-only execution, resource admission, conservative byte/token budgets and timeouts are implemented. Native execution is still unverified on the target computer. The runner is not a sandbox, temporary files may survive a crash, and its output limit is checked after subprocess completion. No private material should be used for early native tests. This is an experimental integration, not a production or commercial release.

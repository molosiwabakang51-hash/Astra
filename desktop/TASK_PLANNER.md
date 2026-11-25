# Bounded task planning

The local planner decomposes an objective into at most five approved finance or read-only evidence retrieval steps. A model-generated plan is only a proposal; every assumption must be reviewed by the owner.

From the repository root:

```powershell
python desktop/astra_plan_cli.py inspect desktop/examples/task_plan.json
```

Review the normalized plan and copy its approval digest into:

```powershell
python desktop/astra_plan_cli.py execute desktop/examples/task_plan.json --approved-digest YOUR_REVIEWED_DIGEST
```

For a plan containing evidence retrieval, add `--ledger evidence.sqlite`. Execution is deterministic by default. Dependencies must point to earlier steps and control execution order and failure propagation, not implicit substitution of numerical outputs. All calculation inputs must be explicitly specified.

For model-assisted planning, prepare a local UTF-8 JSON file with a single objective string, then run:

```powershell
python desktop/astra_plan_cli.py propose objective.json --approve-models --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf"
```

Inspect the proposed plan, save the reviewed plan object (not the entire proposal response) as a local JSON file, and execute using its digest. A digest binds the exact normalized content; it is not an authentication signature, and the owner must actually review it.

Optional synthesis uses a separately approved local model: add `--synthesize --approve-models --binary PATH --primary PATH` to execution. The draft is never authoritative over the exact numerical outputs. Citation IDs are checked and evidence is revalidated at the end of generation; source correctness and factual entailment are not independently established. Incomplete or oversized plans do not receive synthesis.

The planner cannot issue shell commands, write evidence, download files or execute arbitrary model-generated actions. It does not currently infer missing assumptions, pass prior outputs as numerical inputs, adaptively replan or guarantee that a proposed decomposition satisfies an objective. Native runtime compatibility and target computer performance remain unverified. Use synthetic rather than confidential inputs for initial testing.

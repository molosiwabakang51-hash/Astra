# Contextual knowledge chat

Run from the repository root with an existing reviewed Astra evidence ledger and trusted compatible local llama.cpp files.

```powershell
python desktop/astra_knowledge_chat.py --ledger evidence.sqlite --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf" --approve-models
```

Enter a complete topic question, then supported follow-ups such as:

```text
Explain bond duration.
Give an example.
Simplify.
Why?
reset conversation
exit
```

For optional independent advisory review, add `--review --critic "C:\Models\critic.gguf"`. For separately approved local vector retrieval, supply `--approve-embeddings --embedding-binary PATH --embedding-model PATH --dimensions N --pooling mean`, using the actual embedding model's dimensions, pooling and document/query prefixes. See LOCAL_EMBEDDING.md. An unavailable embedding runtime falls back to lexical search.

The conversation retains only the root question, previous question, last successful draft and up to 15 evidence fingerprints in session memory. Follow-ups reuse context only through supported explicit English patterns. Prior drafts are unverified data, not new knowledge or instructions. A new topic, failed answer, explicit reset, stale evidence or exceeded context budget clears the anchor. All previously supplied evidence, including uncited records, is rechecked before and after generation. The result remains a tentative draft; citation identity checks are not factual entailment checks. Revalidation is point-in-time and cannot prevent edits after a result returns.

No automatic model training, tool execution, evidence writing or chat persistence. Terminal history, swap and temporary files may retain information; do not use confidential material in early testing. Native runtime and model compatibility remain unverified on the target hardware. This is bounded contextual knowledge chat, not unrestricted language understanding or a general-purpose autonomous planner.

# Local embedding pilot

Use a trusted local llama.cpp **llama-embedding** executable and an embedding-specific GGUF with the correct pooling, dimensions and prefixes specified by its publisher. Do not assume a conversational model is a suitable embedder.

Example PowerShell command (replace paths, dimensions and pooling with actual model configuration):

```powershell
python desktop/astra_embedding_smoke.py "C:\llama.cpp\llama-embedding.exe" "C:\Models\embedding.gguf" --dimensions 384 --pooling mean --approve-local-model
```

The example is illustrative, not a model recommendation. Use `--document-prefix` and `--query-prefix` if the model requires different retrieval instructions.

The adapter uses the documented array output mode, CPU-only settings, offline mode, resource admission before every batch, a bounded context, batch packing, and a per-process timeout. Reference: https://github.com/ggml-org/llama.cpp/blob/master/examples/embedding/README.md and https://manpages.debian.org/unstable/llama.cpp-examples/llama-embedding.1.en.html.

The hybrid index receives `adapter.documents` when building and `adapter.query` when searching; both calls must use `adapter.profile_sha` as their identity. The profile includes weights, executable, dimensions, pooling, prefixes and a fixed context setting. File stamps detect ordinary changes but are not tamper-proof runtime attestation. The model and runtime must be trusted.

Input text over the 3,500-byte per-sequence limit fails explicitly; chunking longer records remains outstanding. A refresh failure drops the old index. Use lexical retrieval when the embedding model cannot be admitted or the index is unavailable. Temporary prompt files are removed on ordinary exit but crash remnants and copies in swap or backups are possible. Subprocess output is checked after completion, not bounded by an OS sandbox. Do not use confidential material in this experimental runtime.

The smoke test uses four fixed synthetic evidence items and four paraphrased queries. It reports lexical and hybrid top-1 accuracy and timing. A failed embedding run produces no score. It does not establish general semantic retrieval quality or commercial readiness. No model download, network service or paid dependency is required by the code.

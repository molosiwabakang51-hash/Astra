"""Mocked native embedding runtime and end-to-end ledger integration tests."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_evidence", "astra_vector_memory",
             "astra_hybrid_evidence", "astra_local_embedding"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
L = sys.modules["astra_local_embedding"]
P = sys.modules["astra_resource_plan"]
E = sys.modules["astra_evidence"]
H = sys.modules["astra_hybrid_evidence"]


class LocalEmbeddingTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.binary = self.root / "llama-embedding.exe"
        self.binary.write_bytes(b"test binary")
        self.model = self.root / "embedding.gguf"
        self.model.write_bytes(b"GGUF" + b"x" * 1024)

    def adapter(self, **kwargs):
        return L.LocalEmbedder(self.binary, self.model, dimensions=2,
                               approved=True, **kwargs)

    def test_profile_separates_prefixes_pooling_and_weights(self):
        first = self.adapter()
        self.assertEqual(first.profile_sha, self.adapter().profile_sha)
        self.assertNotEqual(first.profile_sha, self.adapter(pooling="last").profile_sha)
        self.assertNotEqual(first.profile_sha, self.adapter(query_prefix="query: ").profile_sha)
        self.model.write_bytes(b"GGUF" + b"y" * 1025)
        self.assertNotEqual(first.profile_sha, self.adapter().profile_sha)
        with self.assertRaises(RuntimeError):
            first.documents(["sample"])

    def test_explicit_consent_and_configuration_validation(self):
        with self.assertRaises(ValueError):
            L.LocalEmbedder(self.binary, self.model, dimensions=2)
        for options in ({"pooling":"rank"}, {"timeout":1},
                        {"query_prefix": L.SEPARATOR}):
            with self.assertRaises(ValueError):
                self.adapter(**options)

    def test_batch_packing_preserves_order_and_refuses_oversize(self):
        groups = L.pack(["a"*2000, "b"*2000, "c"], "")
        self.assertEqual([[b"a"*2000], [b"b"*2000,b"c"]], groups)
        for texts in ([], ["x"]*17, ["x"*3501], ["bad"+L.SEPARATOR], ["\x00"], [""]):
            with self.assertRaises(ValueError):
                L.pack(texts, "")

    def test_parse_native_array_and_validate_dimensions(self):
        self.assertEqual([(1.0,0.0)], L.parse_vectors(b"[[2,0]]",1,2))
        for raw in (b"[]", b"[[0,0]]", b"[[NaN,1]]", b"[[1,2,3]]",
                    b"[[true,1]]", b"[[1,2],[1,2]]", b"banner\n[[1,2]]"):
            with self.assertRaises(ValueError):
                L.parse_vectors(raw,1,2)

    def test_memory_gate_prevents_subprocess_execution(self):
        adapter = self.adapter()
        with patch.object(L, "probe", return_value=P.Resources(P.GIB,4,P.GIB)), \
             patch.object(L.subprocess,"run") as run:
            with self.assertRaises(RuntimeError):
                adapter.documents(["test"])
            run.assert_not_called()

    def test_native_arguments_batching_and_prompt_cleanup(self):
        adapter = self.adapter(document_prefix="passage: ",query_prefix="query: ",timeout=60)
        payloads, paths = [], []
        def execute(args, **kwargs):
            path = Path(args[args.index("-f")+1])
            paths.append(path)
            payloads.append(path.read_bytes())
            self.assertEqual("0", args[args.index("-ngl")+1])
            self.assertEqual("array", args[args.index("--embd-output-format")+1])
            self.assertIn("--offline",args)
            self.assertNotIn("private passage", " ".join(args))
            self.assertFalse(kwargs["shell"])
            self.assertEqual(60,kwargs["timeout"])
            self.assertNotIn("HF_TOKEN",kwargs["env"])
            self.assertNotIn("LLAMA_ARG_HF_REPO",kwargs["env"])
            count = len(payloads[-1].split(L.SEPARATOR.encode()))
            return subprocess.CompletedProcess(args,0,json.dumps([[1,0]]*count).encode())
        with patch.object(L,"probe",return_value=P.Resources(6*P.GIB,4,P.GIB)) as probe, \
             patch.object(L.subprocess,"run",side_effect=execute), \
             patch.dict(os.environ,{"HF_TOKEN":"test","LLAMA_ARG_HF_REPO":"remote"}):
            self.assertEqual(2,len(adapter.documents(["private passage","second"])))
            self.assertEqual(1,len(adapter.query(["test query"])))
        self.assertEqual(b"passage: private passage"+L.SEPARATOR.encode()+b"passage: second",payloads[0])
        self.assertEqual(b"query: test query",payloads[1])
        self.assertEqual(2,probe.call_count)
        self.assertTrue(all(not p.exists() for p in paths))

    def test_timeout_removes_temporary_file(self):
        adapter = self.adapter()
        paths = []
        def execute(args, **kwargs):
            paths.append(Path(args[args.index("-f")+1]))
            raise subprocess.TimeoutExpired(args,kwargs["timeout"])
        with patch.object(L,"probe",return_value=P.Resources(6*P.GIB,4,P.GIB)), \
             patch.object(L.subprocess,"run",side_effect=execute):
            with self.assertRaisesRegex(RuntimeError,"timed out"):
                adapter.query(["test"])
        self.assertTrue(paths)
        self.assertFalse(paths[0].exists())

    def test_hybrid_retrieval_with_native_adapter_contract(self):
        adapter = self.adapter(document_prefix="passage: ",query_prefix="query: ")
        with E.Ledger(self.root/"evidence.sqlite") as ledger:
            target = ledger.add("Fixed income","Duration measures price sensitivity.","Reviewed source",reviewed=True)
            ledger.add("Astronomy","Stars and planets.","Reviewed source",reviewed=True)
            hybrid = H.HybridEvidence(ledger)
            def execute(args, **kwargs):
                raw = Path(args[args.index("-f")+1]).read_text(encoding="utf-8")
                vectors = [[0,1] if "Astronomy" in text else [1,0] for text in raw.split(L.SEPARATOR)]
                return subprocess.CompletedProcess(args,0,json.dumps(vectors).encode())
            with patch.object(L,"probe",return_value=P.Resources(6*P.GIB,4,P.GIB)), \
                 patch.object(L.subprocess,"run",side_effect=execute):
                hybrid.refresh(adapter.documents,model_sha=adapter.profile_sha,dimensions=2,approved=True)
                results = hybrid.search("interest-rate exposure",use_vector=True,approved=True,
                                        embed=adapter.query,model_sha=adapter.profile_sha)
                self.assertEqual([target],[row["id"] for row in results["results"]])
                ledger.clear(confirmed=True)
                results = hybrid.search("interest-rate exposure",use_vector=True,approved=True,
                                        embed=adapter.query,model_sha=adapter.profile_sha)
                self.assertEqual([],results["results"])


if __name__ == "__main__":
    unittest.main()

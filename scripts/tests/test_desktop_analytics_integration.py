"""CSV import and unified analytics integration tests."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_analytics_csv as C
import astra_analytics as A


class AnalyticsIntegrationTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.source=self.root/"input.csv"
        self.metadata={"currency":"BWP","start":"2025-01-01","end":"2026-01-01",
                       "basis":"unchanged_holdings_no_flows_income_excluded"}

    def write(self,text):
        self.source.write_text(text,encoding="utf-8")
        return self.source

    def test_csv_portfolio_matches_structured_analysis(self):
        source=ROOT/"desktop/examples/portfolio_holdings.csv"
        metadata=ROOT/"desktop/examples/portfolio_metadata.json"
        envelope,report=A.from_files("portfolio",source,metadata_path=metadata)
        self.assertEqual("0.15",envelope["result"]["total_return"]["decimal_approximation"])
        receipt=envelope["provenance"]["input"]
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(),receipt["source_sha256"])
        self.assertEqual(hashlib.sha256(metadata.read_bytes()).hexdigest(),receipt["metadata_sha256"])
        self.assertEqual(2,receipt["rows"])
        self.assertIn("Input traceability",A.render(envelope,report,"markdown"))
        self.assertEqual(envelope,json.loads(A.render(envelope,report,"json")))

    def test_cashflow_csv_integration(self):
        envelope,_=A.from_files("cashflow",ROOT/"desktop/examples/cashflow_events.csv",
                                metadata_path=ROOT/"desktop/examples/cashflow_metadata.json")
        self.assertEqual("0.21",envelope["result"]["time_weighted_return"]["decimal_approximation"])
        self.assertEqual("2025-07-02",envelope["provenance"]["input"]["first_date"])

    def test_empty_event_csv_is_valid(self):
        path=self.write("date,value_before_flow,external_flow\n")
        request,receipt=C.ingest("cashflow",path,{})
        self.assertEqual([],request["events"])
        self.assertEqual(0,receipt["rows"])

    def test_aligned_returns_preserve_order_and_benchmark(self):
        path=self.write("date,Equity,Bonds,benchmark\n2025-01-31,0.1,-0.05,0.04\n2025-02-28,-0.02,0.03,0.01\n")
        metadata={"assets":["Equity","Bonds"]}
        request,receipt=C.ingest("historical-risk",path,metadata)
        self.assertEqual([["0.1","-0.05"],["-0.02","0.03"]],request["returns"])
        self.assertEqual(["0.04","0.01"],request["benchmark_returns"])
        self.assertEqual("2025-02-28",receipt["last_date"])
        self.assertNotIn("returns",metadata)
        with self.assertRaises(ValueError):
            C.ingest("simulation",path,metadata)

    def test_no_silent_replacement_or_data_coercion(self):
        header="symbol,opening_value,closing_value,cash_income\n"
        for row in ("A,100,110,\n","A,100,110, 2\n","A,100,110,2%\n",
                    "=A,100,110,2\n","A,100,110,NaN\n","A,100,110,2,5\n"):
            with self.subTest(row=row),self.assertRaises(ValueError):
                C.ingest("portfolio",self.write(header+row),self.metadata)
        path=self.write(header+"A,100,110,2\n")
        with self.assertRaises(ValueError):
            C.ingest("portfolio",path,dict(self.metadata,holdings=[]))

    def test_strict_date_order_and_columns(self):
        for text in (
            "date,A,A\n2025-01-01,0,0\n",
            "date,A\n2025-01-01,0\n2025-01-01,0\n",
            "date,A\n2025-02-01,0\n2025-01-01,0\n",
            "date,A\n2025-02-30,0\n2025-03-01,0\n",
            "date,A\n2025-01-01,0\n2025-02-01,-1.1\n",
            "date,B\n2025-01-01,0\n2025-02-01,0\n",
        ):
            with self.subTest(text=text),self.assertRaises(ValueError):
                C.ingest("historical-risk",self.write(text),{"assets":["A"]})

    def test_bom_and_bounded_input(self):
        self.source.write_bytes(b"\xef\xbb\xbf"+"symbol,opening_value,closing_value,cash_income\nA,100,110,2\n".encode())
        request,_=C.ingest("portfolio",self.source,self.metadata)
        self.assertEqual("A",request["holdings"][0]["symbol"])
        self.source.write_bytes(b"x"*(C.MAX_BYTES+1))
        with self.assertRaises(ValueError):
            C.read_csv(self.source)
        self.source.write_text('{"currency":"BWP","currency":"USD"}')
        with self.assertRaises(ValueError):
            A.read_json(self.source)

    def test_registry_and_canonical_digest(self):
        self.assertTrue(A.available()["portfolio"])
        self.assertTrue(A.available()["cashflow"])
        self.assertEqual(A.canonical_digest({"a":1,"b":2}),A.canonical_digest({"b":2,"a":1}))
        with self.assertRaises(ValueError):
            A.run_analysis("unknown",{})

    def test_cli_export_is_exclusive(self):
        target=self.root/"report.md"
        args=["portfolio",str(ROOT/"desktop/examples/portfolio_holdings.csv"),
              "--metadata",str(ROOT/"desktop/examples/portfolio_metadata.json"),
              "--output",str(target)]
        self.assertEqual(0,A.main(args))
        before=target.read_bytes()
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1,A.main(args))
        self.assertEqual(before,target.read_bytes())


if __name__=="__main__":
    unittest.main()

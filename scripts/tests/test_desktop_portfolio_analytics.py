"""Portfolio analytics regression checks using synthetic data."""
import contextlib
from copy import deepcopy
from fractions import Fraction
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
for name in ("astra_quantitative_verify","astra_verified_finance","astra_portfolio_analytics","astra_portfolio_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_portfolio_analytics"]
R=sys.modules["astra_portfolio_report"]


def fraction(metric):
    return Fraction(int(metric["numerator"]),int(metric["denominator"]))


class PortfolioTests(unittest.TestCase):
    def setUp(self):
        self.request=json.loads((ROOT/"desktop/examples/portfolio_analysis.json").read_text(encoding="utf-8"))

    def test_exact_returns_and_contribution_reconciliation(self):
        result=A.analyze(self.request)
        self.assertEqual(100000,fraction(result["opening_value"]))
        self.assertEqual(110000,fraction(result["closing_value"]))
        self.assertEqual(5000,fraction(result["cash_income"]))
        self.assertEqual(15000,fraction(result["total_gain"]))
        self.assertEqual(Fraction(3,20),fraction(result["total_return"]))
        self.assertEqual(Fraction(1,10),fraction(result["capital_return"]))
        self.assertEqual(Fraction(1,20),fraction(result["income_return"]))
        self.assertEqual(Fraction(3,100),fraction(result["benchmark"]["active_return"]))
        self.assertEqual(fraction(result["total_return"]),sum(fraction(row["return_contribution"]) for row in result["positions"]))
        self.assertEqual(1,sum(fraction(row["closing_weight"]) for row in result["positions"]))

    def test_concentration(self):
        result=A.analyze(self.request)["concentration"]
        hhi=Fraction(72,110)**2+Fraction(38,110)**2
        self.assertEqual(hhi*10000,fraction(result["hhi"]))
        self.assertEqual(1/hhi,fraction(result["effective_positions"]))
        self.assertEqual(Fraction(72,110),fraction(result["largest_weight"]))
        self.assertEqual(1,fraction(result["top_three_weight"]))

    def test_stress_scenarios(self):
        rows=A.analyze(self.request)["scenarios"]
        self.assertEqual(-14400,fraction(rows[0]["value_change"]))
        self.assertEqual(95600,fraction(rows[0]["stressed_value"]))
        self.assertEqual(Fraction(-14400,110000),fraction(rows[0]["portfolio_change"]))
        self.assertEqual(-11000,fraction(rows[1]["value_change"]))
        self.assertEqual(Fraction(-1,10),fraction(rows[1]["portfolio_change"]))

    def test_zero_closing_value_has_no_concentration_or_relative_stress(self):
        for row in self.request["holdings"]:
            row["closing_value"]="0"
        result=A.analyze(self.request)
        self.assertIsNone(result["concentration"])
        self.assertIsNone(result["scenarios"][0]["portfolio_change"])
        self.assertEqual(Fraction(-95,100),fraction(result["total_return"]))

    def test_reject_unsupported_basis_and_input_domains(self):
        for field,value in (("basis","trades_allowed"),("currency",""),("end","2024-01-01"),("benchmark_total_return","-1.01")):
            request=deepcopy(self.request)
            request[field]=value
            with self.assertRaises(ValueError):
                A.analyze(request)
        for field,value in (("opening_value","0"),("closing_value","-1"),("cash_income",1.0),("symbol","Asset B")):
            request=deepcopy(self.request)
            request["holdings"][0][field]=value
            with self.assertRaises(ValueError):
                A.analyze(request)

    def test_reject_unknown_shocks_and_invalid_scenario_ranges(self):
        for shocks in ({"Unknown":"0.1"},{"Asset A":"-1.01"},{"Asset A":0.1},{}):
            request=deepcopy(self.request)
            request["scenarios"][0]["shocks"]=shocks
            with self.assertRaises(ValueError):
                A.analyze(request)

    def test_report_explains_assumptions_and_benchmark_percentage_points(self):
        output=R.report(A.analyze(self.request))
        for expected in ("BWP 110,000.00","15.00%","3.00 pp","BWP -14,400.00","no currency conversion","unchanged holdings"):
            self.assertIn(expected.lower(),output.lower())
        self.assertIn("does not recommend investments",output)

    def test_cli_report_export_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            source=root/"request.json"
            output=root/"report.md"
            source.write_text(json.dumps(self.request),encoding="utf-8")
            self.assertEqual(0,R.main([str(source),"--output",str(output)]))
            before=output.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1,R.main([str(source),"--output",str(output)]))
            self.assertEqual(before,output.read_bytes())
            stdout=io.StringIO()
            with contextlib.redirect_stdout(stdout):
                self.assertEqual(0,R.main([str(source),"--format","json"]))
            self.assertEqual(Fraction(3,20),fraction(json.loads(stdout.getvalue())["total_return"]))


if __name__=="__main__":
    unittest.main()

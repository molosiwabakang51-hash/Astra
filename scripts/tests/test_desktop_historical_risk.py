"""Historical-risk tests with independently checkable exact fixtures."""
import contextlib
from copy import deepcopy
from decimal import Decimal
from fractions import Fraction
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
for name in ("astra_quantitative_verify","astra_verified_finance",
             "astra_historical_risk","astra_historical_risk_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_historical_risk"]
R=sys.modules["astra_historical_risk_report"]
F=Fraction
D=Decimal


def f(metric):
    return F(int(metric["numerator"]),int(metric["denominator"]))


def fixture():
    return {"assets":["A","B"],"weights":["0.5","0.5"],"periods_per_year":12,
            "period_risk_free_rate":"0.001","confidence":"0.75",
            "basis":A.BASIS,
            "returns":[["-0.1","0.02"],["0.1","0.02"],["0.2","0.02"],["-0.2","0.02"]],
            "benchmark_returns":["-0.1","0.1","0.2","-0.2"]}


class HistoricalRiskTests(unittest.TestCase):
    def test_exact_moments_and_portfolio_covariance(self):
        result=A.analyze(fixture())
        self.assertEqual(F(2,5),f(result["annualized_covariance"][0][0]))
        self.assertEqual(0,f(result["annualized_covariance"][0][1]))
        self.assertEqual(0,f(result["annualized_covariance"][1][1]))
        self.assertIsNone(result["correlation"][1][0])
        self.assertEqual(1,D(result["correlation"][0][0]))
        p=result["portfolio"]
        self.assertEqual(F(1,100),f(p["period_arithmetic_mean"]))
        self.assertEqual(F(1,120),f(p["period_sample_variance"]))
        self.assertEqual(F(1,10),f(p["annualized_sample_variance"]))
        self.assertLess(abs(D(p["annualized_volatility"])**2-D("0.1")),D("1e-26"))

    def test_benchmark_beta_tracking_error_and_information_ratio(self):
        b=A.analyze(fixture())["benchmark"]
        self.assertEqual(F(1,2),f(b["beta"]))
        self.assertLess(abs(D(b["annualized_tracking_error"])**2-D("0.1")),D("1e-26"))
        self.assertEqual(F(3,25),f(b["annualized_arithmetic_active_return"]))
        self.assertLess(abs(D(b["annualized_information_ratio"])*D(b["annualized_tracking_error"])-D("0.12")),D("1e-26"))

    def test_empirical_quantile_and_fractional_tail_mass(self):
        req=fixture()
        risk=A.analyze(req)["portfolio"]["historical_tail"]
        self.assertEqual(F(4,100),f(risk["var_loss"]))
        self.assertEqual(F(9,100),f(risk["expected_shortfall_loss"]))
        self.assertTrue(risk["thin_tail_sample"])
        req["confidence"]="0.625"
        risk=A.analyze(req)["portfolio"]["historical_tail"]
        self.assertEqual(F(3,2),f(risk["tail_observation_equivalents"]))
        self.assertEqual(F(11,150),f(risk["expected_shortfall_loss"]))

    def test_drawdown_and_compounding(self):
        req=fixture()
        req.update(assets=["A"],weights=["1"],returns=[["0.1"],["-0.2"],["0.1"],["-0.1"]])
        req.pop("benchmark_returns")
        w=A.analyze(req)["portfolio"]["wealth"]
        self.assertEqual(D("-0.1288"),D(w["cumulative_return"]))
        self.assertEqual(D("0.208"),D(w["maximum_drawdown_loss"]))
        self.assertEqual(1,w["drawdown_peak_observation"])
        self.assertEqual(4,w["drawdown_trough_observation"])
        self.assertIsNone(w["annualized_geometric_return"])
        req["returns"]=[["0.01"]]*12
        w=A.analyze(req)["portfolio"]["wealth"]
        self.assertEqual(D("1.01")**12-1,D(w["cumulative_return"]))
        self.assertEqual(D(w["cumulative_return"]),D(w["annualized_geometric_return"]))

    def test_total_loss_and_constant_benchmark(self):
        req=fixture()
        req.update(assets=["A"],weights=["1"],returns=[["-1"],["0.5"]],
                   benchmark_returns=["0","0"])
        result=A.analyze(req)
        self.assertEqual(-1,D(result["portfolio"]["wealth"]["cumulative_return"]))
        self.assertEqual(1,D(result["portfolio"]["wealth"]["maximum_drawdown_loss"]))
        self.assertIsNone(result["benchmark"]["beta"])
        req["returns"]=[["0.01"],["0.01"]]
        result=A.analyze(req)
        self.assertIsNone(result["portfolio"]["annualized_sharpe"])
        self.assertIsNone(result["benchmark"]["annualized_information_ratio"])
        self.assertEqual(F(-1,100),f(result["portfolio"]["historical_tail"]["expected_shortfall_loss"]))

    def test_input_constraints(self):
        for changes in ({"basis":"unknown"},{"weights":["0.3","0.5"]},
                        {"weights":["-0.1","1.1"]},{"periods_per_year":365},
                        {"confidence":"1"},{"period_risk_free_rate":"-1"},
                        {"returns":[["0.1","0.2"]]},{"returns":[["0.1"],["0.2"]]},
                        {"returns":[["-1.1","0"],["0","0"]]},
                        {"benchmark_returns":["0"]},{"assets":["A","a"]}):
            with self.subTest(changes=changes),self.assertRaises(ValueError):
                A.analyze(dict(fixture(),**changes))

    def test_report_and_safe_export(self):
        request=json.loads((ROOT/"desktop/examples/historical_risk.json").read_text(encoding="utf-8"))
        rendered=R.report(A.analyze(request))
        for text in ("Historical tail risk","Expected shortfall","Annualized sample covariance",
                     "Sample beta","tracking error","Thin tail","not predicted losses"):
            self.assertIn(text,rendered)
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/"input.json"
            target=Path(tmp)/"report.md"
            source.write_text(json.dumps(request),encoding="utf-8")
            self.assertEqual(0,R.main([str(source),"--output",str(target)]))
            original=target.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1,R.main([str(source),"--output",str(target)]))
            self.assertEqual(original,target.read_bytes())
            stream=io.StringIO()
            with contextlib.redirect_stdout(stream):
                self.assertEqual(0,R.main([str(source),"--format","json"]))
            self.assertEqual(12,json.loads(stream.getvalue())["observations"])

    def test_duplicate_json_and_oversized_inputs_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"input.json"
            path.write_text('{"assets":[],"assets":[]}',encoding="utf-8")
            with self.assertRaises(ValueError):
                R.read_request(path)
            path.write_bytes(b"x"*(R.MAX_INPUT_BYTES+1))
            with self.assertRaises(ValueError):
                R.read_request(path)


if __name__=="__main__":
    unittest.main()

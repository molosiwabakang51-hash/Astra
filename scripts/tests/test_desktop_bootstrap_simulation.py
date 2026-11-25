"""Bootstrap simulation checks with deterministic fixtures and independent expectations."""
import contextlib
from decimal import Decimal, localcontext
from fractions import Fraction
import importlib.util
import io
import json
from pathlib import Path
from random import Random
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
for name in ("astra_quantitative_verify","astra_verified_finance",
             "astra_bootstrap_simulation","astra_simulation_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_bootstrap_simulation"]
R=sys.modules["astra_simulation_report"]
D=Decimal


def fixture():
    return {"currency":"BWP","basis":A.BASIS,"assets":["A","B"],
            "returns":[["0.1","-0.1"],["0.1","-0.1"]],
            "allocations":[{"name":"Baseline","weights":["1","0"]},
                           {"name":"Alternative","weights":["0","1"]}],
            "initial_wealth":"100","target_wealth":"110","paths":10,
            "horizon_periods":2,"block_length":1,"seed":42,"confidence":"0.9"}


class BootstrapTests(unittest.TestCase):
    def test_constant_returns_have_closed_form_terminal_wealth(self):
        result=A.analyze(fixture())
        p,q=result["allocations"]
        for value in p["terminal_quantiles"].values():
            self.assertEqual(D("121"),D(value))
        self.assertEqual(D("81"),D(q["mean_terminal_wealth"]))
        self.assertEqual(0,p["below_target_path_count"])
        self.assertEqual(10,q["loss_path_count"])
        self.assertEqual(10,q["below_target_path_count"])
        self.assertEqual(D("0.19"),D(q["p95_max_drawdown"]))
        self.assertEqual(D("-0.21"),D(p["terminal_var_loss"]))
        self.assertEqual(D("0.19"),D(q["terminal_expected_shortfall_loss"]))
        comparison=result["paired_comparisons"][0]
        self.assertEqual(10,comparison["underperform_path_count"])
        self.assertEqual(D("-40"),D(comparison["mean_terminal_wealth_difference"]))

    def test_full_block_preserves_order_and_drawdown(self):
        req=fixture()
        req.update(returns=[["0.1","-0.1"],["-0.2","0.2"]],block_length=2)
        result=A.analyze(req)
        p,q=result["allocations"]
        self.assertEqual(D("88"),D(p["mean_terminal_wealth"]))
        self.assertEqual(D("108"),D(q["mean_terminal_wealth"]))
        self.assertEqual(D("0.2"),D(p["p95_max_drawdown"]))
        self.assertEqual(D("0.1"),D(q["p95_max_drawdown"]))

    def test_cross_asset_dependence_preserved_and_identical_allocations_tie(self):
        req=fixture()
        req["returns"]=[["0.2","-0.2"],["-0.3","0.3"],["0.1","-0.1"]]
        req["allocations"]=[{"name":name,"weights":["0.5","0.5"]}
                            for name in ("Baseline","Alternative")]
        result=A.analyze(req)
        for p in result["allocations"]:
            self.assertEqual(100,D(p["mean_terminal_wealth"]))
            self.assertEqual(0,p["loss_path_count"])
            self.assertEqual(0,D(p["p95_max_drawdown"]))
        self.assertEqual(10,result["paired_comparisons"][0]["tie_path_count"])

    def test_total_loss_is_absorbing(self):
        req=fixture()
        req.update(returns=[["-1","0.1"],["0.5","0.1"]],block_length=2)
        p=A.analyze(req)["allocations"][0]
        self.assertEqual(0,D(p["mean_terminal_wealth"]))
        self.assertEqual(1,D(p["p95_max_drawdown"]))
        self.assertEqual(1,D(p["terminal_expected_shortfall_loss"]))

    def test_seeded_paths_and_independent_wealth_reconstruction(self):
        req=fixture()
        req.update(returns=[["0.1","0"],["-0.2","0.1"],["0.3","0.05"]],
                   paths=37,horizon_periods=5,block_length=2)
        result=A.analyze(req)
        self.assertEqual(result,A.analyze(req))
        with localcontext() as ctx:
            ctx.prec=50
            rng=Random(req["seed"])
            actual=[]
            for _ in range(req["paths"]):
                wealth=D(100)
                remaining=req["horizon_periods"]
                while remaining:
                    start=rng.randrange(2)
                    take=min(2,remaining)
                    for row in req["returns"][start:start+take]:
                        wealth*=1+D(row[0])
                    remaining-=take
                actual.append(wealth)
            self.assertEqual(sum(actual)/len(actual),D(result["allocations"][0]["mean_terminal_wealth"]))
            self.assertEqual(sum(x<100 for x in actual),result["allocations"][0]["loss_path_count"])

    def test_fractional_expected_shortfall_and_quantile(self):
        values=[D("0.4"),D("0.2"),D("0.1"),D("0")]
        with localcontext() as ctx:
            ctx.prec=50
            self.assertEqual(D("0.2"),A.empirical_quantile(sorted(values),Fraction(3,4)))
            self.assertEqual(D(1)/3,A.tail_mean(values,Fraction(5,8)))

    def test_invalid_inputs_and_computational_limits(self):
        for changes in (
            {"basis":"unknown"},{"seed":True},{"seed":-1},{"paths":10001},
            {"horizon_periods":253},{"block_length":3},{"confidence":"1"},
            {"initial_wealth":"0"},{"target_wealth":"-1"},{"returns":[["0","0"]]},
            {"returns":[["0","0"],["-1.1","0"]]},
            {"allocations":[{"name":"Invalid","weights":["0.4","0.5"]}]},
            {"paths":10000,"horizon_periods":252},
        ):
            with self.subTest(changes=changes),self.assertRaises(ValueError):
                A.analyze(dict(fixture(),**changes))

    def test_report_and_non_overwriting_export(self):
        request=fixture()
        rendered=R.report(A.analyze(request))
        for expected in ("Terminal wealth distribution","Expected shortfall loss",
                         "Paired allocation comparisons","BWP 121.00",
                         "not calibrated probabilities","Warning:"):
            self.assertIn(expected,rendered)
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
            self.assertEqual(10,json.loads(stream.getvalue())["paths"])


if __name__=="__main__":
    unittest.main()

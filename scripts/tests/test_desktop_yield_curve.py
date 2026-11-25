"""Yield-curve regression tests with independent analytic expectations."""
import contextlib
from copy import deepcopy
from decimal import Decimal, localcontext
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
for name in ("astra_quantitative_verify","astra_verified_finance","astra_yield_curve","astra_yield_curve_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_yield_curve"]
R=sys.modules["astra_yield_curve_report"]
D=Decimal


class YieldCurveTests(unittest.TestCase):
    def setUp(self):
        self.request=json.loads((ROOT/"desktop/examples/yield_curve.json").read_text(encoding="utf-8"))

    def test_flat_continuous_curve(self):
        result=A.analyze({
            "currency":"BWP","curve":{"kind":"continuous_spot","nodes":[
                {"months":12,"value":"0.05"},{"months":60,"value":"0.05"}]},
            "queries":[6,18,48],"forwards":[[0,6],[6,48]]})
        with localcontext() as ctx:
            ctx.prec=48
            for row in result["queries"]:
                expected=(-D("0.05")*D(row["months"])/12).exp()
                self.assertLess(abs(D(row["discount_factor"])-expected),D("1e-45"))
                self.assertLess(abs(D(row["continuous_zero"])-D("0.05")),D("1e-45"))
        for row in result["forwards"]:
            self.assertEqual(D("0.05"),D(row["continuous_forward"]))

    def test_log_linear_midpoint_and_forward_identity(self):
        result=A.analyze({"currency":"GBP","curve":{"kind":"discount_factors","nodes":[
            {"months":12,"value":"0.9"},{"months":36,"value":"0.729"}]},
            "queries":[24],"forwards":[[12,36]]})
        with localcontext() as ctx:
            ctx.prec=48
            expected=(D("0.9")*D("0.729")).sqrt()
            self.assertLess(abs(D(result["queries"][0]["discount_factor"])-expected),D("1e-45"))
            forward=(D("0.9").ln()-D("0.729").ln())/2
            self.assertLess(abs(D(result["forwards"][0]["continuous_forward"])-forward),D("1e-45"))

    def test_par_bootstrap_reprices_all_input_maturities(self):
        rates=["0.04","0.05","0.06","0.07"]
        for freq in (1,2,4,12):
            nodes=[{"months":(i+1)*12//freq,"value":r} for i,r in enumerate(rates)]
            result=A.analyze({"currency":"BWP","curve":{"kind":"regular_par",
                              "payments_per_year":freq,"nodes":nodes}})
            with localcontext() as ctx:
                ctx.prec=48
                discounts=[D(row["discount_factor"]) for row in result["nodes"]]
                for i,r in enumerate(rates):
                    coupon=D(r)/freq
                    self.assertLess(abs(coupon*sum(discounts[:i+1])+discounts[i]-1),D("1e-45"))

    def test_negative_continuous_rates_and_forwards_allowed(self):
        result=A.analyze({"currency":"BWP","curve":{"kind":"continuous_spot","nodes":[
            {"months":12,"value":"-0.02"},{"months":24,"value":"-0.02"}]},
            "forwards":[[12,24]]})
        self.assertGreater(D(result["nodes"][1]["discount_factor"]),1)
        self.assertEqual(D("-0.02"),D(result["forwards"][0]["continuous_forward"]))

    def test_zero_coupon_price_and_analytic_sensitivities(self):
        result=A.analyze({"currency":"BWP","curve":{"kind":"continuous_spot","nodes":[
            {"months":12,"value":"0.05"},{"months":60,"value":"0.05"}]},
            "cashflows":[{"months":60,"amount":"1000"}],"parallel_shocks_bps":[-100,100]})
        v=result["valuation"]
        with localcontext() as ctx:
            ctx.prec=48
            p=D(1000)*D("-0.25").exp()
            self.assertLess(abs(D(v["price"])-p),D("1e-43"))
            self.assertLess(abs(D(v["parallel_duration_central"])-5),D("0.000001"))
            self.assertLess(abs(D(v["parallel_convexity_central"])-25),D("0.000001"))
            self.assertEqual(0,D(v["key_rates"][0]["dv01_central"]))
            self.assertEqual(D(v["parallel_dv01_central"]),D(v["key_rates"][1]["dv01_central"]))
            self.assertGreater(D(v["parallel_scenarios"][0]["price"]),p)
            self.assertLess(D(v["parallel_scenarios"][1]["price"]),p)

    def test_key_rate_reconciliation_and_subset_disclosure(self):
        v=A.analyze(self.request)["valuation"]
        self.assertTrue(v["all_curve_nodes_selected"])
        self.assertLess(abs(D(v["selected_key_rate_dv01_sum"])-D(v["parallel_dv01_central"])),D("0.000001"))
        self.assertEqual(4,len(v["key_rates"]))
        subset=A.analyze(dict(self.request,key_rate_months=[12]))["valuation"]
        self.assertFalse(subset["all_curve_nodes_selected"])
        self.assertEqual(1,len(subset["key_rates"]))

    def test_reject_unsupported_extrapolation_and_input_shapes(self):
        for changes in (
            {"queries":[61]},{"queries":[12,12]},{"queries":[True]},
            {"forwards":[[24,12]]},{"key_rate_months":[18]},
            {"cashflows":[{"months":12,"amount":"-1"}]},
            {"cashflows":[{"months":12,"amount":"0"}]},
            {"parallel_shocks_bps":[True]},{"parallel_shocks_bps":[100,100]},
        ):
            with self.subTest(changes=changes),self.assertRaises(ValueError):
                A.analyze(dict(self.request,**changes))
        for curve in (
            {"kind":"discount_factors","nodes":[{"months":12,"value":"0"}]},
            {"kind":"continuous_spot","nodes":[{"months":12,"value":"0.05"},{"months":12,"value":"0.06"}]},
            {"kind":"regular_par","payments_per_year":2,"nodes":[{"months":12,"value":"0.05"}]},
            {"kind":"regular_par","payments_per_year":1,"nodes":[{"months":12,"value":"-0.01"}]},
            {"kind":"regular_par","payments_per_year":1,"nodes":[{"months":12,"value":"0"},{"months":24,"value":"2"}]},
        ):
            with self.subTest(curve=curve),self.assertRaises(ValueError):
                A.analyze({"currency":"BWP","curve":curve})

    def test_reports_and_exclusive_export(self):
        rendered=R.report(A.analyze(self.request))
        for expected in ("Forward rates","Key maturity","Parallel DV01","Modeled present value","No extrapolation"):
            self.assertIn(expected,rendered)
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/"curve.json"
            output=Path(tmp)/"report.md"
            source.write_text(json.dumps(self.request),encoding="utf-8")
            self.assertEqual(0,R.main([str(source),"--output",str(output)]))
            original=output.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1,R.main([str(source),"--output",str(output)]))
            self.assertEqual(original,output.read_bytes())
            stream=io.StringIO()
            with contextlib.redirect_stdout(stream):
                self.assertEqual(0,R.main([str(source),"--format","json"]))
            self.assertIn("valuation",json.loads(stream.getvalue()))


if __name__=="__main__":
    unittest.main()

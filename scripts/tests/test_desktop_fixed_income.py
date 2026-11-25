"""Fixed-income analytics tests using independently checkable synthetic bonds."""
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
for name in ("astra_quantitative_verify","astra_verified_finance","astra_fixed_income","astra_bond_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_fixed_income"]
R=sys.modules["astra_bond_report"]


def fraction(metric):
    return Fraction(int(metric["numerator"]),int(metric["denominator"]))


def zero_coupon():
    return {"currency":"BWP","face_value":"121","annual_coupon_rate":"0",
            "payments_per_year":1,"remaining_periods":2,
            "basis":"regular_fixed_rate_bullet_on_coupon_date",
            "annual_yield":"0.1","shocks_bps":[-100,0,100]}


class FixedIncomeTests(unittest.TestCase):
    def setUp(self):
        self.request=json.loads((ROOT/"desktop/examples/bond_analysis.json").read_text(encoding="utf-8"))

    def test_par_bond_prices_at_face(self):
        result=A.analyze(self.request)
        self.assertEqual(1000,fraction(result["price"]))
        self.assertEqual(100,fraction(result["price_per_100_face"]))
        self.assertEqual(30,fraction(result["coupon_payment"]))
        self.assertEqual(Fraction(3,50),fraction(result["current_yield"]))
        self.assertEqual(5,fraction(result["years_to_maturity"]))
        self.assertLess(fraction(result["macaulay_duration"]),5)
        self.assertLess(fraction(result["modified_duration"]),fraction(result["macaulay_duration"]))

    def test_zero_coupon_exact_duration_convexity_and_dv01(self):
        result=A.analyze(zero_coupon())
        self.assertEqual(100,fraction(result["price"]))
        self.assertEqual(2,fraction(result["macaulay_duration"]))
        self.assertEqual(Fraction(20,11),fraction(result["modified_duration"]))
        self.assertEqual(Fraction(600,121),fraction(result["convexity"]))
        self.assertEqual(Fraction(1,55),fraction(result["linear_dv01"]))
        self.assertEqual(100-Fraction(121)/(Fraction(11001,10000)**2),
                         fraction(result["exact_up_one_bp_loss"]))

    def test_exact_repricing_and_approximation_error(self):
        result=A.analyze(zero_coupon())
        for scenario in result["scenarios"]:
            change=Fraction(scenario["shock_bps"],10000)
            stressed=Fraction(121)/(Fraction(11,10)+change)**2
            actual=(stressed-100)/100
            estimated=-Fraction(20,11)*change+Fraction(600,121)*change**2/2
            self.assertEqual(stressed,fraction(scenario["stressed_price"]))
            self.assertEqual(actual,fraction(scenario["exact_price_change"]))
            self.assertEqual(estimated,fraction(scenario["duration_convexity_estimate"]))
            self.assertEqual(estimated-actual,fraction(scenario["approximation_error"]))
        self.assertGreater(fraction(result["scenarios"][0]["stressed_price"]),100)
        self.assertLess(fraction(result["scenarios"][2]["stressed_price"]),100)

    def test_numerical_yield_recovers_par_and_zero_coupon_values(self):
        for request,market,yield_expected in (
            (deepcopy(self.request),"1000",Fraction(3,50)),
            (zero_coupon(),"100",Fraction(1,10)),
            (zero_coupon(),"150",None)):
            del request["annual_yield"]
            request["market_price"]=market
            result=A.analyze(request)
            self.assertEqual("bounded_bisection_approximation",result["yield_method"])
            if yield_expected is not None:
                self.assertEqual(yield_expected,fraction(result["annual_yield"]))
            else:
                self.assertLess(fraction(result["annual_yield"]),0)
            self.assertLess(abs(fraction(result["pricing_residual"])),Fraction(1,10**8))

    def test_coupon_frequency_and_zero_yield(self):
        for frequency in A.FREQUENCIES:
            request=dict(self.request,payments_per_year=frequency,remaining_periods=frequency*2,
                         annual_yield="0",shocks_bps=[])
            result=A.analyze(request)
            self.assertEqual(1120,fraction(result["price"]))
            self.assertEqual(2,fraction(result["years_to_maturity"]))
            self.assertEqual(fraction(result["macaulay_duration"]),fraction(result["modified_duration"]))

    def test_reject_unsupported_terms_and_invalid_input(self):
        for key,value in (("basis","floating_rate"),("face_value","0"),("annual_coupon_rate","-0.01"),
                          ("payments_per_year",3),("payments_per_year",True),
                          ("remaining_periods",121),("annual_yield","-1"),
                          ("currency",""),("shocks_bps",[100,100]),("shocks_bps",[True])):
            request=dict(self.request)
            request[key]=value
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):
                A.analyze(request)
        with self.assertRaises(ValueError):
            A.analyze(dict(self.request,market_price="1000"))
        request=dict(self.request)
        del request["annual_yield"]
        with self.assertRaises(ValueError):
            A.analyze(dict(request,market_price="0.000000000001"))
        with self.assertRaises(ValueError):
            A.analyze(dict(self.request,annual_yield="2",shocks_bps=[100]))

    def test_boundary_yield_without_upward_dv01(self):
        result=A.analyze(dict(self.request,annual_yield="2",shocks_bps=[]))
        self.assertIsNone(result["exact_up_one_bp_loss"])
        self.assertGreater(fraction(result["linear_dv01"]),0)

    def test_report_and_non_overwriting_cli(self):
        result=A.analyze(self.request)
        text=R.report(result)
        for expected in ("BWP 1,000.00","Macaulay duration","Modified duration","Convexity",
                         "No accrued interest","No market-data validation"):
            self.assertIn(expected,text)
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/"bond.json"
            output=Path(tmp)/"bond.md"
            source.write_text(json.dumps(self.request),encoding="utf-8")
            self.assertEqual(0,R.main([str(source),"--output",str(output)]))
            original=output.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1,R.main([str(source),"--output",str(output)]))
            self.assertEqual(original,output.read_bytes())
            stdout=io.StringIO()
            with contextlib.redirect_stdout(stdout):
                self.assertEqual(0,R.main([str(source),"--format","json"]))
            self.assertEqual(1000,fraction(json.loads(stdout.getvalue())["price"]))


if __name__=="__main__":
    unittest.main()

"""Cash-flow performance tests with independently checkable fixtures."""
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
             "astra_cashflow_performance","astra_cashflow_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_cashflow_performance"]
R=sys.modules["astra_cashflow_report"]


def fraction(metric):
    return Fraction(int(metric["numerator"]),int(metric["denominator"]))


class CashflowPerformanceTests(unittest.TestCase):
    def setUp(self):
        self.request=json.loads((ROOT/"desktop/examples/cashflow_performance.json").read_text(encoding="utf-8"))

    def test_linked_returns_and_gain_reconcile(self):
        result=A.analyze(self.request)
        self.assertEqual(365,result["days"])
        self.assertEqual(500,fraction(result["net_external_flows"]))
        self.assertEqual(260,fraction(result["investment_gain"]))
        self.assertEqual(Fraction(21,100),fraction(result["time_weighted_return"]))
        self.assertEqual(Decimal("0.21"),Decimal(result["annualized_time_weighted_return"]))
        self.assertEqual([Fraction(1,10)]*2,[fraction(x["return"]) for x in result["segments"]])
        self.assertEqual(1600,fraction(result["segments"][0]["value_after_flow"]))
        self.assertEqual(1600,fraction(result["segments"][1]["opening_after_flow"]))
        expected=Fraction(1000)+Fraction(500*183,365)
        self.assertEqual(expected,fraction(result["modified_dietz_denominator"]))
        self.assertEqual(260/expected,fraction(result["modified_dietz"]))

    def test_no_external_flows_all_return_measures_agree_over_year(self):
        result=A.analyze(dict(self.request,closing_value="1100",events=[]))
        self.assertEqual(Fraction(1,10),fraction(result["time_weighted_return"]))
        self.assertEqual(Fraction(1,10),fraction(result["modified_dietz"]))
        irr=result["money_weighted"]
        self.assertEqual("numerical_estimate",irr["status"])
        self.assertLess(abs(Decimal(irr["annualized_return"])-Decimal("0.1")),Decimal("1e-30"))
        self.assertLess(Decimal(irr["relative_npv_residual"]),Decimal("1e-12"))

    def test_midperiod_contribution_irr_reconciles_investor_cashflows(self):
        result=A.analyze(self.request)
        irr=result["money_weighted"]
        self.assertEqual("numerical_estimate",irr["status"])
        rate=Decimal(irr["annualized_return"])
        npv=-Decimal(1000)-Decimal(500)/(1+rate)**(Decimal(182)/365)+Decimal(1760)/(1+rate)
        self.assertLess(abs(npv),Decimal("1e-20"))

    def test_withdrawal_is_not_an_investment_loss(self):
        event={"date":"2025-07-02","value_before_flow":"1000","external_flow":"-200"}
        result=A.analyze(dict(self.request,closing_value="800",events=[event]))
        self.assertEqual(0,fraction(result["investment_gain"]))
        self.assertEqual(0,fraction(result["time_weighted_return"]))
        self.assertEqual(0,fraction(result["modified_dietz"]))
        self.assertLess(abs(Decimal(result["money_weighted"]["annualized_return"])),Decimal("1e-30"))

    def test_nonconventional_cashflows_do_not_select_arbitrary_irr(self):
        events=[
            {"date":"2025-04-01","value_before_flow":"1000","external_flow":"-100"},
            {"date":"2025-07-02","value_before_flow":"900","external_flow":"100"},
        ]
        result=A.analyze(dict(self.request,closing_value="1000",events=events))
        self.assertEqual("unavailable_nonconventional_cash_flows",result["money_weighted"]["status"])
        self.assertEqual(3,result["money_weighted"]["sign_changes"])
        self.assertIsNone(result["money_weighted"]["annualized_return"])
        self.assertEqual(0,fraction(result["time_weighted_return"]))

    def test_short_period_twr_not_annualized_and_unbracketed_irr(self):
        result=A.analyze(dict(self.request,end="2025-01-02",closing_value="2000",events=[]))
        self.assertIsNone(result["annualized_time_weighted_return"])
        self.assertEqual(1,fraction(result["time_weighted_return"]))
        self.assertEqual("unavailable_outside_yield_bracket",result["money_weighted"]["status"])

    def test_total_loss_and_nonpositive_dietz_denominator(self):
        result=A.analyze(dict(self.request,closing_value="0",events=[]))
        self.assertEqual(-1,fraction(result["time_weighted_return"]))
        self.assertEqual("-1",result["annualized_time_weighted_return"])
        self.assertIsNone(result["money_weighted"]["annualized_return"])
        event={"date":"2025-01-02","value_before_flow":"3000","external_flow":"-2000"}
        result=A.analyze(dict(self.request,closing_value="1000",events=[event]))
        self.assertIsNone(result["modified_dietz"])
        self.assertLess(fraction(result["modified_dietz_denominator"]),0)

    def test_strict_event_dates_and_input_domains(self):
        for key,value in (("basis","unknown"),("opening_value","0"),("closing_value","-1"),
                          ("currency",""),("end","2024-01-01"),("opening_value",1000)):
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):
                A.analyze(dict(self.request,**{key:value}))
        for key,value in (("date","2025-01-01"),("date","2026-01-01"),
                          ("external_flow","0"),("external_flow","-1100"),
                          ("value_before_flow","-1")):
            request=deepcopy(self.request)
            request["events"][0][key]=value
            with self.assertRaises(ValueError):
                A.analyze(request)
        request=deepcopy(self.request)
        request["events"].append(deepcopy(request["events"][0]))
        with self.assertRaises(ValueError):
            A.analyze(request)

    def test_report_and_exclusive_file_export(self):
        text=R.report(A.analyze(self.request))
        for expected in ("21.0000%","BWP 260.00","Modified Dietz","Subperiod reconciliation",
                         "numerical estimate","ACT/365 fixed"):
            self.assertIn(expected,text)
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/"request.json"
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
            self.assertEqual(Fraction(21,100),fraction(json.loads(stream.getvalue())["time_weighted_return"]))


if __name__=="__main__":
    unittest.main()

"""Exact-grid optimizer tests using independent small exhaustive comparisons."""
import contextlib
from copy import deepcopy
from fractions import Fraction
import importlib.util
import io
import itertools
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
for name in ("astra_quantitative_verify","astra_verified_finance","astra_portfolio_optimize","astra_optimization_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_portfolio_optimize"]
R=sys.modules["astra_optimization_report"]


def f(metric):
    return Fraction(int(metric["numerator"]),int(metric["denominator"]))


def pair():
    return {"assets":["A","B"],"expected_returns":["0.10","0.06"],
            "covariance":[["0.04","0"],["0","0.01"]],
            "risk_free_rate":"0.02","grid_units":20}


class OptimizationTests(unittest.TestCase):
    def setUp(self):
        self.request=json.loads((ROOT/"desktop/examples/portfolio_optimization.json").read_text(encoding="utf-8"))

    def test_two_asset_closed_form_minimum_variance(self):
        result=A.analyze(pair())
        minimum=result["minimum_variance"]
        self.assertEqual(Fraction(1,5),f(minimum["weights"]["A"]))
        self.assertEqual(Fraction(4,5),f(minimum["weights"]["B"]))
        self.assertEqual(Fraction(1,125),f(minimum["variance"]))
        self.assertEqual(21,result["feasible_allocations"])
        self.assertEqual("maximum_positive_sharpe_on_grid",result["sharpe_status"])
        self.assertEqual(7,result["maximum_sharpe"]["weight_units"]["A"])

    def test_exact_grid_optima_against_independent_enumeration(self):
        result=A.analyze(self.request)
        mu=list(map(Fraction,self.request["expected_returns"]))
        cov=[list(map(Fraction,row)) for row in self.request["covariance"]]
        rf=Fraction(self.request["risk_free_rate"])
        grid=[]
        for a,b,c in itertools.product(range(13),range(21),range(2,11)):
            if a+b+c!=20:
                continue
            w=[Fraction(x,20) for x in (a,b,c)]
            ret=sum(x*y for x,y in zip(w,mu))
            var=sum(w[i]*w[j]*cov[i][j] for i in range(3) for j in range(3))
            grid.append((ret,var))
        self.assertEqual(len(grid),result["feasible_allocations"])
        self.assertEqual(min(v for _,v in grid),f(result["minimum_variance"]["variance"]))
        best=result["maximum_sharpe"]
        self.assertEqual(max((r-rf)**2/v for r,v in grid if r>rf and v>0),
                         (f(best["expected_return"])-rf)**2/f(best["variance"]))
        frontier={(r,v) for r,v in grid if not any(
            rr>=r and vv<=v and (rr>r or vv<v) for rr,vv in grid)}
        observed={(f(p["expected_return"]),f(p["variance"])) for p in result["efficient_frontier"]}
        self.assertEqual(frontier,observed)
        for row in result["targets"]:
            eligible=[v for r,v in grid if r>=f(row["target"])]
            if eligible:
                self.assertEqual(min(eligible),f(row["portfolio"]["variance"]))
            else:
                self.assertEqual("infeasible",row["status"])

    def test_singular_psd_and_zero_variance(self):
        request=pair()
        request["covariance"]=[["0","0"],["0","0.01"]]
        result=A.analyze(request)
        self.assertEqual("positive_excess_zero_variance_no_finite_sharpe",result["sharpe_status"])
        self.assertIsNone(result["maximum_sharpe"]["sharpe_ratio"])
        self.assertEqual(1,f(result["maximum_sharpe"]["weights"]["A"]))
        self.assertIsNone(result["correlation"][0][0])
        self.assertEqual("1",result["correlation"][1][1])
        request["covariance"]=[["0.04","-0.04"],["-0.04","0.04"]]
        result=A.analyze(request)
        self.assertEqual(0,f(result["minimum_variance"]["variance"]))

    def test_nonpositive_excess_abstention(self):
        result=A.analyze(dict(pair(),risk_free_rate="0.1"))
        self.assertIsNone(result["maximum_sharpe"])
        self.assertEqual("no_positive_excess_return_on_grid",result["sharpe_status"])

    def test_bounds_and_tie_breaking(self):
        request=pair()
        request["bounds"]={"A":{"min_units":10,"max_units":12}}
        result=A.analyze(request)
        self.assertEqual(3,result["feasible_allocations"])
        self.assertEqual(10,result["minimum_variance"]["weight_units"]["A"])
        tied=pair()
        tied["expected_returns"]=["0.1","0.1"]
        tied["covariance"]=[["0.01","0.01"],["0.01","0.01"]]
        result=A.analyze(tied)
        self.assertEqual(1,result["frontier_count"])
        self.assertEqual(0,result["minimum_variance"]["weight_units"]["A"])

    def test_reject_invalid_covariance_and_weight_constraints(self):
        for changes in (
            {"covariance":[["0.04","0.02"],["0","0.01"]]},
            {"covariance":[["0.04","0.03"],["0.03","0.01"]]},
            {"covariance":[["0","0.01"],["0.01","0.01"]]},
            {"covariance":[["-0.01","0"],["0","0.01"]]},
            {"bounds":{"A":{"min_units":15,"max_units":20},"B":{"min_units":15,"max_units":20}}},
            {"bounds":{"A":{"min_units":True,"max_units":20}}},
            {"grid_units":True},{"assets":["A","a"]},{"target_returns":["0.1","0.10"]},
        ):
            with self.subTest(changes=changes),self.assertRaises(ValueError):
                A.analyze(dict(pair(),**changes))
        with self.assertRaises(ValueError):
            A.analyze({"assets":list("ABCDEF"),"grid_units":100,
                       "expected_returns":["0.1"]*6,"covariance":[["0"]*6 for _ in range(6)],
                       "risk_free_rate":"0.02"})

    def test_report_and_non_overwriting_export(self):
        rendered=R.report(A.analyze(self.request))
        for text in ("Minimum variance","Maximum positive Sharpe","Efficient frontier",
                     "Correlation matrix","Infeasible","not continuous-space optima"):
            self.assertIn(text,rendered)
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/"input.json"
            target=Path(tmp)/"report.md"
            source.write_text(json.dumps(self.request),encoding="utf-8")
            self.assertEqual(0,R.main([str(source),"--output",str(target)]))
            original=target.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1,R.main([str(source),"--output",str(target)]))
            self.assertEqual(original,target.read_bytes())
            stream=io.StringIO()
            with contextlib.redirect_stdout(stream):
                self.assertEqual(0,R.main([str(source),"--format","json"]))
            self.assertIn("efficient_frontier",json.loads(stream.getvalue()))


if __name__=="__main__":
    unittest.main()

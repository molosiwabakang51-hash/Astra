"""Historical replay regression tests with independently derived fixtures."""
import contextlib
from decimal import Decimal, localcontext
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
for name in ("astra_quantitative_verify","astra_verified_finance",
             "astra_strategy_backtest","astra_backtest_report"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
A=sys.modules["astra_strategy_backtest"]
R=sys.modules["astra_backtest_report"]
D=Decimal


def fixture():
    return {"basis":A.BASIS,"currency":"BWP","assets":["A","B"],
            "returns":[["1","0"],["0","0"]],"initial_wealth":"100",
            "transaction_cost_bps":"100",
            "strategies":[{"name":"Hold","kind":"buy_and_hold","initial_weights":["0.5","0.5"]},
                          {"name":"Rebalance","kind":"fixed_weight","initial_weights":["0.5","0.5"],
                           "rebalance_every":1}],"benchmark_returns":["0.5","0"]}


class BacktestTests(unittest.TestCase):
    def test_self_financing_cost_and_turnover(self):
        result=A.analyze(fixture())
        hold,reb=result["strategies"]
        self.assertEqual(150,D(hold["terminal_wealth"]))
        self.assertEqual(0,D(hold["total_transaction_cost"]))
        self.assertLess(abs(D(reb["terminal_wealth"])-D("149.5")),D("1e-38"))
        self.assertLess(abs(D(reb["total_transaction_cost"])-D("0.5")),D("1e-38"))
        row=reb["series"][1]
        self.assertLess(abs(D(row["gross_traded_value"])-50),D("1e-38"))
        self.assertLess(abs(D(row["opening_wealth"])-D(row["transaction_cost"])-D(row["post_trade_wealth"])),D("1e-26"))
        self.assertLess(abs(D(reb["cumulative_one_way_turnover"])-D(1)/6),D("1e-26"))
        self.assertEqual(1,reb["rebalance_trade_count"])
        self.assertEqual(0,D(hold["active_cumulative_return"]))
        self.assertLess(abs(D(reb["active_cumulative_return"])+D("0.005")),D("1e-38"))

    def test_costless_rebalanced_and_buy_hold_diverge(self):
        req=fixture()
        req.update(transaction_cost_bps="0",returns=[["1","0"],["-0.5","0"]])
        hold,reb=A.analyze(req)["strategies"]
        self.assertEqual(100,D(hold["terminal_wealth"]))
        self.assertEqual(D("112.5"),D(reb["terminal_wealth"]))
        self.assertEqual(D("0.25"),D(reb["maximum_drawdown_loss"]))

    def test_lagged_momentum_does_not_read_current_or_future_row(self):
        req=fixture()
        req["returns"]=[["0.2","0"],["-0.2","0.5"],["0.1","0"]]
        req.pop("benchmark_returns")
        req["strategies"]=[{"name":"Momentum","kind":"lagged_momentum","initial_weights":["0.5","0.5"],
                            "rebalance_every":1,"lookback":1,"top_k":1}]
        req["transaction_cost_bps"]="0"
        original=A.analyze(req)["strategies"][0]["series"]
        changed=A.analyze(dict(req,returns=[req["returns"][0],["0.9","-0.5"],["0","0"]]))["strategies"][0]["series"]
        self.assertEqual(original[0],changed[0])
        self.assertEqual(original[1]["start_weights"],changed[1]["start_weights"])
        self.assertEqual([D(1),D(0)],list(map(D,original[1]["start_weights"])))
        self.assertEqual([D(0),D(1)],list(map(D,original[2]["start_weights"])))
        self.assertEqual([D(1),D(0)],list(map(D,changed[2]["start_weights"])))

    def test_total_loss_is_absorbing(self):
        req=fixture()
        req["returns"]=[["-1","-1"],["1","1"]]
        for strategy in A.analyze(req)["strategies"]:
            self.assertEqual(0,D(strategy["terminal_wealth"]))
            self.assertEqual(1,D(strategy["maximum_drawdown_loss"]))
            self.assertIsNone(strategy["series"][1]["net_return"])

    def test_no_trade_no_fee_and_repeating_weights(self):
        with localcontext() as ctx:
            ctx.prec=50
            h=[D(100),D(0)]
            target,cost,volume=A.rebalance(h,[D(1),D(0)],D("0.01"))
            self.assertEqual(h,target)
            self.assertEqual(0,cost)
            self.assertEqual(0,volume)
            h=[D(100),D(0),D(0)]
            w=[D(1)/3,D(2)/3,D(0)]
            target,cost,volume=A.rebalance(h,w,D("0.01"))
            self.assertTrue(all(x>=0 for x in target))
            self.assertLess(abs(sum(target)+cost-100),D("1e-38"))
            self.assertLess(abs(cost-volume*D("0.01")),D("1e-38"))

    def test_invalid_inputs(self):
        for changes in ({"basis":"unknown"},{"currency":""},{"initial_wealth":"0"},
                        {"transaction_cost_bps":"1001"},{"assets":["A","a"]},
                        {"returns":[["0","0"],["-1.1","0"]]},
                        {"benchmark_returns":["0"]},
                        {"strategies":[{"name":"Invalid","kind":"fixed_weight",
                                        "initial_weights":["0.5","0.4"],"rebalance_every":1}]},
                        {"strategies":[{"name":"Invalid","kind":"lagged_momentum",
                                        "initial_weights":["0.5","0.5"],"rebalance_every":1,
                                        "lookback":2,"top_k":1}]}):
            with self.subTest(changes=changes),self.assertRaises(ValueError):
                A.analyze(dict(fixture(),**changes))

    def test_report_and_safe_export(self):
        req=fixture()
        rendered=R.report(A.analyze(req))
        for expected in ("Benchmark comparison","period reconciliation","Gross trades","selection","BWP 149.5000"):
            self.assertIn(expected,rendered)
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/"input.json"
            target=Path(tmp)/"report.md"
            source.write_text(json.dumps(req),encoding="utf-8")
            self.assertEqual(0,R.main([str(source),"--output",str(target)]))
            original=target.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1,R.main([str(source),"--output",str(target)]))
            self.assertEqual(original,target.read_bytes())
            stream=io.StringIO()
            with contextlib.redirect_stdout(stream):
                self.assertEqual(0,R.main([str(source),"--format","json"]))
            self.assertEqual(2,len(json.loads(stream.getvalue())["strategies"]))


if __name__=="__main__":
    unittest.main()

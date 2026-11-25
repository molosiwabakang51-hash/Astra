"""Independent verification of exact arithmetic, without model evaluation."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("astra_quantitative_verify", ROOT / "desktop/astra_quantitative_verify.py")
V = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(V)

class QuantitativeVerificationTests(unittest.TestCase):
    def test_weighted_return_uses_exact_decimal_ratios(self):
        inputs = {"weights": ["0.3", "0.7"], "returns": ["0.10", "0.05"]}
        result = V.verify("weighted_return", inputs, "0.065")
        self.assertEqual("matches", result["status"])
        self.assertEqual("13", result["exact_numerator"])
        self.assertEqual("200", result["exact_denominator"])
        self.assertEqual("mismatch", V.verify("weighted_return", inputs, "6.5")["status"])

    def test_discount_and_compound(self):
        self.assertEqual("matches", V.verify("present_value", {"amount":"121","rate":"0.1","periods":2}, "100")["status"])
        self.assertEqual("matches", V.verify("compound_growth", {"amount":"100","rate":"0.1","periods":2}, "121")["status"])

    def test_tolerance_is_explicit_and_bounded(self):
        inputs = {"amount":"100","rate":"0.05","periods":1}
        self.assertEqual("mismatch", V.verify("present_value", inputs, "95.24")["status"])
        self.assertEqual("matches", V.verify("present_value", inputs, "95.24", "0.005")["status"])
        for tolerance in ("-0.1", "1"):
            with self.assertRaises(ValueError):
                V.verify("present_value", inputs, "95.24", tolerance)

    def test_rejects_wrong_weights_and_unsafe_values(self):
        for weights in (["0.1","0.7"],["-0.1","1.1"],["1e0","0"],[0.3,0.7]):
            with self.assertRaises(ValueError):
                V.exact_result("weighted_return", {"weights":weights,"returns":["0.1","0.2"]})
        for amount in ("NaN", "1e1000", "__import__('os')"):
            with self.assertRaises(ValueError):
                V.exact_result("compound_growth", {"amount":amount,"rate":"0.1","periods":2})
        for periods in (-1, 101, True):
            with self.assertRaises(ValueError):
                V.exact_result("present_value", {"amount":"1","rate":"0.1","periods":periods})

    def test_percentage_change_and_unknown_operations(self):
        self.assertEqual("matches", V.verify("percentage_change", {"initial":"80","final":"100"}, "0.25")["status"])
        with self.assertRaises(ValueError):
            V.exact_result("percentage_change", {"initial":"0","final":"100"})
        with self.assertRaises(ValueError):
            V.exact_result("eval", {})
        with self.assertRaises(ValueError):
            V.exact_result("percentage_change", {"initial":"80","final":"100","extra":"1"})

if __name__ == "__main__":
    unittest.main()

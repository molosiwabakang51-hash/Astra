"""Regression: user's typographic math symbols must reach exact parser.

Static checks supplement Kotlin tests; they do not represent Qwen reasoning.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
MATH = (JAVA / "AstraVerifiedExpression.kt").read_text(encoding="utf-8")
CHAT = (JAVA / "AstraVerifiedCalculationFollowUp.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class UnicodeMathHandsetRegression(unittest.TestCase):
    def test_typographic_ops_are_normalized_only_inside_strict_full_question(self):
        for line in ("expression.replace('×', '*')", ".replace('÷', '/')",
                     ".replace('−', '-')"):
            self.assertIn(line, MATH)
        self.assertIn("command.matchEntire(raw.trim())", MATH)
        self.assertIn("AstraVerifiedExpression.answer(clean)", MAIN)

    def test_followup_uses_verified_expression_not_model_prose(self):
        self.assertIn("AstraVerifiedExpression.verifiedWorking(", CHAT)
        self.assertIn("outcome.result != previousAnswer.trim()", MATH)
        self.assertNotIn("ScriptEngine", MATH)
        self.assertNotIn("HttpURLConnection", MATH)


if __name__ == "__main__":
    unittest.main()

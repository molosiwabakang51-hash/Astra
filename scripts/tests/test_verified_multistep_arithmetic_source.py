"""Multi-step arithmetic is computed locally, with bounded exact operators.

These static checks complement JVM parser tests and do not earn Qwen scores.
"""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "android/app/src/main/java/com/astra/agent"
PARSER = (JAVA / "AstraVerifiedExpression.kt").read_text(encoding="utf-8")
MAIN = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")


class VerifiedComplexArithmeticContracts(unittest.TestCase):
    def test_multi_step_parser_executes_before_untrusted_action_handlers(self):
        self.assertIn("AstraVerifiedExpression.answer(clean)", MAIN)
        self.assertLess(
            MAIN.index("AstraVerifiedExpression.answer(clean)"),
            MAIN.index("dialogue.respond(clean, tasks, notes, completed)")
        )
        self.assertIn("private const val MAX_TOKENS", PARSER)
        self.assertIn("private const val MAX_OPERANDS", PARSER)
        self.assertIn("private const val MAX_LEVELS", PARSER)

    def test_no_arbitrary_evaluation_rounding_or_network(self):
        self.assertIn("left.divide(right)", PARSER)
        self.assertIn("Division by zero is undefined.", PARSER)
        self.assertIn("tokens.count { numeric.matches(it) }", PARSER)
        self.assertIn("command.matchEntire(raw.trim())", PARSER)
        self.assertNotIn("ScriptEngine", PARSER)
        self.assertNotIn("eval(", PARSER)
        self.assertNotIn("java.net", PARSER)
        self.assertNotIn("HttpURLConnection", PARSER)

    def test_working_requires_exact_observed_value(self):
        self.assertIn("outcome.result != previousAnswer.trim()", PARSER)
        self.assertIn("These steps are recomputed locally", PARSER)
        self.assertIn("operator", PARSER.lower())


if __name__ == "__main__":
    unittest.main()

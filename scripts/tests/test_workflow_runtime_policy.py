"""Fail closed if Astra workflows regress to deprecated Node.js action runtimes.

Verified against published action.yml runtimes (Node.js 24):
checkout v5, setup-java v5, setup-python v6, setup-node v5,
upload-artifact v6, gradle/actions/setup-gradle v5.

This only checks the declared workflow policy. A green CI run is still
required to prove that the new action majors actually work together.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
NODE24_ACTIONS = {
    "actions/checkout": "v5",
    "actions/setup-java": "v5",
    "actions/setup-python": "v6",
    "actions/setup-node": "v5",
    "actions/upload-artifact": "v6",
    "gradle/actions/setup-gradle": "v5",
}
ACTION_LINE = re.compile(
    r"^\s*(?:-\s*)?uses:\s*([a-zA-Z0-9_.-]+/[a-zA-Z0-9_./-]+)@(v[0-9]+)\s*$",
    re.MULTILINE,
)
RUNNER_LINE = re.compile(r"^\s*runs-on:\s*(\S+)\s*$", re.MULTILINE)


class WorkflowRuntimePolicyTests(unittest.TestCase):
    def test_all_workflows_use_node24_compatible_action_majors(self):
        workflows = sorted(WORKFLOWS.glob("*.yml"))
        self.assertGreaterEqual(len(workflows), 5)
        found = set()

        for workflow in workflows:
            source = workflow.read_text(encoding="utf-8")
            uses = ACTION_LINE.findall(source)
            self.assertTrue(uses, workflow.name + " contains no action references")
            for action, major in uses:
                self.assertIn(
                    action,
                    NODE24_ACTIONS,
                    workflow.name + ": unreviewed action " + action,
                )
                self.assertEqual(
                    NODE24_ACTIONS[action],
                    major,
                    workflow.name + ": deprecated or unreviewed " + action,
                )
                found.add(action)
            self.assertNotIn("ubuntu-latest", source, workflow.name)
        self.assertEqual(set(NODE24_ACTIONS), found)

    def test_all_jobs_pin_a_supported_ubuntu_image(self):
        for workflow in sorted(WORKFLOWS.glob("*.yml")):
            source = workflow.read_text(encoding="utf-8")
            runners = RUNNER_LINE.findall(source)
            self.assertTrue(runners, workflow.name + " declares no runner")
            self.assertTrue(
                all(runner == "ubuntu-24.04" for runner in runners),
                workflow.name + ": unexpected moving or unreviewed runner " + str(runners),
            )


if __name__ == "__main__":
    unittest.main()

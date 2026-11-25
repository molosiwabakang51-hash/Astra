"""Adaptive native context planning, with synthetic model files and no inference."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_resource_plan as P
import astra_local_answer as A
import astra_workbench as W


class AdaptiveContextTests(unittest.TestCase):
    def test_full_context_when_memory_is_sufficient(self):
        plan=P.adaptive_plan(P.Resources(6*P.GIB,4,P.GIB),P.GIB,500)
        self.assertEqual(4096,plan["context_tokens"])
        self.assertEqual(512,plan["generation_tokens"])
        self.assertEqual(2,plan["threads"])

    def test_reduces_context_without_reducing_generation(self):
        resources=P.Resources(4400*P.MIB,4,P.GIB)
        plan=P.adaptive_plan(resources,P.GIB,500)
        self.assertEqual(4096,plan["context_tokens"])
        resources=P.Resources(4000*P.MIB,4,P.GIB)
        plan=P.adaptive_plan(resources,P.GIB,500)
        self.assertEqual(2048,plan["context_tokens"])
        self.assertEqual(512,plan["generation_tokens"])

    def test_refuses_prompt_that_cannot_fit(self):
        with self.assertRaises(RuntimeError):
            P.adaptive_plan(P.Resources(8*P.GIB,4,P.GIB),P.GIB,4096)
        with self.assertRaises(RuntimeError):
            P.adaptive_plan(P.Resources(2*P.GIB,4,P.GIB),P.GIB,500)

    def test_observations_can_only_raise_admission_requirement(self):
        resources=P.Resources(6*P.GIB,4,P.GIB)
        plan=P.adaptive_plan(resources,P.GIB,500,observed={4096:4*P.GIB})
        self.assertEqual(3072,plan["context_tokens"])
        with self.assertRaises(RuntimeError):
            P.adaptive_plan(resources,P.GIB,500,observed={1536:4*P.GIB})
        self.assertEqual(4096,P.adaptive_plan(resources,P.GIB,500,observed={4096:1})["context_tokens"])

    def test_fixed_and_adaptive_commands_and_prompt_preservation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            binary=root/"llama-cli.exe"
            binary.write_bytes(b"fixture")
            model=root/"primary.gguf"
            model.write_bytes(b"GGUF"+b"x"*1024)
            prompt="synthetic question"
            def capture(command,**kwargs):
                self.assertEqual(prompt,Path(command[command.index("-f")+1]).read_text())
                kwargs["telemetry"].update(observed_peak_working_set_bytes=2*P.GIB,outcome="success")
                return subprocess.CompletedProcess(command,0,b"answer")
            with patch.object(A,"probe",return_value=P.Resources(3100*P.MIB,4,P.GIB)),patch.object(A,"run_bounded",side_effect=capture) as run:
                fixed=A.LocalAnswerRunner(binary,model,approved=True)
                fixed("primary",prompt)
                self.assertEqual(4096,fixed.last_profile["context_tokens"])
                adaptive=A.LocalAnswerRunner(binary,model,approved=True,context_policy="adaptive")
                adaptive("primary",prompt)
                self.assertEqual(4096,adaptive.last_profile["context_tokens"])
                adaptive("primary",prompt)
                self.assertEqual(3072,adaptive.last_profile["context_tokens"])
                cmd=run.call_args.args[0]
                self.assertEqual("3072",cmd[cmd.index("-c")+1])
                self.assertEqual("512",cmd[cmd.index("-n")+1])

    def test_profile_does_not_expose_arbitrary_fields(self):
        wb=W.Workbench()
        self.assertEqual({},wb.runtime_profile()["profile"])
        class Runner:
            last_profile={"role":"primary","elapsed_seconds":2.5,
                          "observed_peak_working_set_bytes":P.MIB,
                          "prompt":"private fixture"}
        wb.runner=Runner()
        result=wb.runtime_profile()
        self.assertNotIn("prompt",result["profile"])
        self.assertIn("1.0 MiB",W.format_result(result))
        self.assertNotIn("private fixture",W.format_result(result))

    def test_refused_attempt_has_actionable_private_profile(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            binary=root/"llama-cli.exe"
            binary.write_bytes(b"fixture")
            model=root/"primary.gguf"
            model.write_bytes(b"GGUF"+b"x"*1024)
            runner=A.LocalAnswerRunner(binary,model,approved=True,context_policy="adaptive")
            with patch.object(A,"probe",return_value=P.Resources(1000*P.MIB,4,P.GIB)),patch.object(A,"run_bounded") as native:
                with self.assertRaises(RuntimeError):
                    runner("primary","synthetic secret")
                native.assert_not_called()
            wb=W.Workbench()
            wb.runner=runner
            profile=wb.runtime_profile()["profile"]
            self.assertEqual("memory_admission_refused",profile["outcome"])
            self.assertEqual(1000,profile["available_ram_mib"])
            self.assertGreater(profile["required_ram_mib"],1000)
            self.assertNotIn("synthetic secret",W.format_result(wb.runtime_profile()))
            self.assertIn("Free RAM",W.format_result(wb.runtime_profile()))
            with patch.object(A,"probe",return_value=P.Resources(8*P.GIB,4,P.GIB)),patch.object(A,"run_bounded") as native:
                with self.assertRaises(RuntimeError):
                    runner("primary","x"*4096)
                native.assert_not_called()
            self.assertEqual("prompt_context_budget_exceeded",runner.last_profile["outcome"])

    def test_workbench_grammar_is_opt_in_and_role_specific(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            binary=root/"llama-cli.exe"
            binary.write_bytes(b"fixture")
            model=root/"primary.gguf"
            model.write_bytes(b"GGUF"+b"x"*1024)
            critic=root/"critic.gguf"
            critic.write_bytes(b"GGUF"+b"y"*1024)
            plain=A.LocalAnswerRunner(binary,model,approved=True)
            structured=A.LocalAnswerRunner(binary,model,critic,approved=True,output_policy="grounded")
            with patch.object(A,"probe",return_value=P.Resources(6*P.GIB,4,P.GIB)),patch.object(A,"run_bounded",return_value=subprocess.CompletedProcess([],0,b"fixture")) as run:
                plain("primary","synthetic")
                self.assertNotIn("--grammar",run.call_args.args[0])
                for role,grammar in (("primary",A.ANSWER_GRAMMAR),("critic",A.CRITIC_GRAMMAR)):
                    structured(role,"synthetic")
                    command=run.call_args.args[0]
                    self.assertEqual(grammar,command[command.index("--grammar")+1])

if __name__=="__main__":
    unittest.main()

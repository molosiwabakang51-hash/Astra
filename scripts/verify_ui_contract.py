#!/usr/bin/env python3
"""Regression guard for the navigation and offline-AI UX requested for Astra.

This checks source-level invariants. It is not a substitute for phone UI tests.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "android/app/src/main/java/com/astra/agent/MainActivity.kt"
GRADLE = ROOT / "android/app/build.gradle.kts"
BRAIN = ROOT / "android/app/src/main/java/com/astra/agent/AstraOnDeviceBrain.kt"
IMPORT = ROOT / "android/app/src/main/java/com/astra/agent/AstraModelImport.kt"


def main():
    app = MAIN.read_text(encoding="utf-8")
    config = GRADLE.read_text(encoding="utf-8")
    brain = BRAIN.read_text(encoding="utf-8")
    importer = IMPORT.read_text(encoding="utf-8")

    required = {
        "Astra icon opens navigation": 'clickable { navigationPopupOpen = true }',
        "Astra logo contains chat/settings options": 'listOf(\n                                        "Chat", "Tasks", "Notes", "Tools", "Settings"',
        "Chat deck starts collapsed": 'var commandDeckExpanded by remember { mutableStateOf(false) }',
        "Chat takes remaining space": 'Modifier.weight(1f)',
        "Qwen is installed on device": 'AstraOnDeviceBrain(applicationContext)',
        "Unhandled chat routes to model": 'AstraOfflinePolicy.shouldUseModel(',
        "Finance explanations are wired into chat": 'AstraFinanceEducation.respond(clean)',
        "Cross-chat memory is in local submit handler": 'AstraMemoryRecall.parse(message)',
        "Grounded Q&A stays explicitly opt-in": 'AstraGroundedMemory.parse(message)',
        "On-phone source extraction is integrated": 'AstraGroundedMemory.retrieve(',
        "Memory analysis UI remains in icon menu": 'Text("Ask with evidence")',
        "Memory recall stays off-device model prompt": 'AstraMemoryRecall.find(',
        "Compact logo offers memory search": 'Text("Search all local memory")',
        "Strict on-device AI challenge is wired in": 'AstraOfflineSelfTest.evaluate(',
        "Build identity is in the chat header": '"v" + ASTRA_VERSION + "  ·  OFFLINE AI"',
    }

    forbidden = {
        "Legacy bottom navigation": r"\bNavigationBarItem\s*\(|\bNavigationBar\s*\(",
        "Legacy three-dot actions": r'Text\s*\(\s*["\']⋮["\']\s*\)',
        "Obsolete model-required response": r"General open-ended questions will require an additional language model",
    }
    for description, value in required.items():
        if value not in app:
            raise RuntimeError(f"Missing {description}: {value!r}")

    for description, pattern in forbidden.items():
        if re.search(pattern, app):
            raise RuntimeError(f"Regression: {description}")

    if "session.invalidate()" not in brain or "AstraInferenceSession()" not in brain:
        raise RuntimeError("Offline native inference must reset its KV-cache after errors")
    if "AstraModelStorage.installVerified(" not in importer or "AstraModelStorage.installVerified(" not in brain:
        raise RuntimeError("FULL and LITE model updates must use crash-safe storage")

    if '?: "0.11.7"' not in config or "?: 24" not in config:
        raise RuntimeError("Release identity is not Astra 0.11.7 (versionCode 24)")
    print("PASS: Astra icon menu, compact chat, offline model, version stamp")
    print("PASS: no legacy bottom tabs, top-right overflow, or obsolete fallback")


if __name__ == "__main__":
    main()

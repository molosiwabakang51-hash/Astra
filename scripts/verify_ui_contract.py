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
CONTEXT = ROOT / "android/app/src/main/java/com/astra/agent/AstraConversationContext.kt"
TRAY = ROOT / "android/app/src/main/java/com/astra/agent/AstraNavigationTray.kt"


def main():
    app = MAIN.read_text(encoding="utf-8")
    config = GRADLE.read_text(encoding="utf-8")
    brain = BRAIN.read_text(encoding="utf-8")
    importer = IMPORT.read_text(encoding="utf-8")
    conversation_context = CONTEXT.read_text(encoding="utf-8")
    tray = TRAY.read_text(encoding="utf-8")

    required = {
        "Astra icon opens navigation": 'clickable { navigationPopupOpen = true }',
        "Astra logo opens the new navigation sheet": 'AstraNavigationTray(',
        "Chat deck starts collapsed": 'var commandDeckExpanded by remember { mutableStateOf(false) }',
        "Chat takes remaining space": 'Modifier.weight(1f)',
        "Qwen is installed on device": 'AstraOnDeviceBrain(applicationContext',
        "Unhandled chat routes to model": 'AstraOfflinePolicy.shouldUseModel(',
        "Finance explanations are wired into chat": 'AstraFinanceEducation.respond(clean)',
        "Natural finance has deterministic arithmetic": 'AstraNaturalFinance.respond(clean)',
        "Cross-chat memory is in local submit handler": 'AstraMemoryRecall.parse(message)',
        "Grounded Q&A stays explicitly opt-in": 'AstraGroundedMemory.parse(message)',
        "On-phone source extraction is integrated": 'AstraGroundedMemory.retrieve(',
        "Memory analysis UI remains in icon menu": 'Text("Ask with evidence")',
        "Memory recall stays off-device model prompt": 'AstraMemoryRecall.find(',
        "Compact logo offers memory search": 'AstraTrayAction.MEMORY_SEARCH',
        "Strict on-device AI challenge is wired in": 'AstraOfflineSelfTest.evaluate(',
        "Build identity is in the chat header": '"●  LOCAL  ·  v" + ASTRA_VERSION',
    }

    forbidden = {
        "Legacy bottom navigation": r"\bNavigationBarItem\s*\(|\bNavigationBar\s*\(",
        "Legacy three-dot actions": r'Text\s*\(\s*["\']⋮["\']\s*\)',
        "Obsolete model-required response": r"General open-ended questions will require an additional language model",
    }
    for description, value in required.items():
        if value not in app:
            raise RuntimeError(f"Missing {description}: {value!r}")

    for title in ('"Chat"', '"Tasks"', '"Notes"', '"Tools"', '"Settings"', '"Study"'):
        if title not in tray:
            raise RuntimeError(f"Navigation tray lost destination: {title}")
    for action in ("NEW_CONVERSATION", "CONVERSATIONS", "MEMORY_SEARCH",
                   "FIND_IN_CHAT", "COPY_CHAT", "SHARE_CHAT", "RENAME_CHAT",
                   "MISSION_CONTROL", "READ_LAST_REPLY", "CLEAR_CHAT"):
        if "AstraTrayAction." + action not in app:
            raise RuntimeError(f"Navigation tray lost action routing: {action}")

    for description, pattern in forbidden.items():
        if re.search(pattern, app):
            raise RuntimeError(f"Regression: {description}")

    study = (ROOT / "android/app/src/main/java/com/astra/agent/AstraStudyCatalog.kt").read_text(encoding="utf-8")
    welcome = (ROOT / "android/app/src/main/java/com/astra/agent/AstraWelcomePanel.kt").read_text(encoding="utf-8")
    if ('AstraStudyHub(' not in app or 'onOpenStudy =' not in app or
            '"Study"' not in welcome or '"Teach me finance"' in welcome or
            '"finance"' not in study or '"day_trading"' not in study):
        raise RuntimeError("Study must open an explicit subject picker without auto-launching finance")

    if "session.invalidate()" not in brain or "AstraInferenceSession()" not in brain:
        raise RuntimeError("Offline native inference must reset its KV-cache after errors")
    if "AstraModelStorage.installVerified(" not in importer or "AstraModelStorage.installVerified(" not in brain:
        raise RuntimeError("FULL and LITE model updates must use crash-safe storage")

    if ("current.setSystemPrompt(AstraConversationContext.systemPrompt())" not in brain or
            "AstraConversationContext.userPrompt(" not in brain or
            "Earlier chat excerpts" in brain):
        raise RuntimeError("Prior chat must stay in bounded user-level context, never system policy")
    if ("fun systemPrompt()" not in conversation_context or
            "fun userPrompt(" not in conversation_context):
        raise RuntimeError("Local conversation prompt safety layer is missing")

    if '?: "0.12.1"' not in config or "?: 28" not in config:
        raise RuntimeError("Release identity is not Astra 0.12.1 (versionCode 28)")
    print("PASS: Astra core side tray, compact chat, offline model, version stamp")
    print("PASS: no legacy bottom tabs, top-right overflow, or obsolete fallback")


if __name__ == "__main__":
    main()

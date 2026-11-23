# Astra: stable-release acceptance criteria

**Status: EXPERIMENTAL.** Android CI and unit tests cannot substitute for a real
Android ARM64 device running an offline local model. Do not label a build
"final", "stable", or fully device-tested until every mandatory gate below
is supported by evidence.

## Release identity, security and cost

| Gate | Required evidence | Current state |
| --- | --- | --- |
| Correct installer | Manifest matches intended version, `com.astra.agent`, and ARM64. | Automated in GitHub Actions. |
| Data-efficient updates | **LITE APK ≤300 MB (300,000,000 bytes, decimal)** and contains no GGUF; FULL contains verified GGUF. This is a hard ceiling, not a size target. | Enforced for LITE debug and owner-signed builds by the APK verifier; an earlier LITE measured about 77.5 MiB. Installed-size and phone acceptance are separate. |
| Authentic weights | SHA-256 matches pinned model; native shared libraries and notices included. | Automated for FULL, model import checked during operation. |
| Stable signing | Protected `astra-release` key configured by owner, matching APK certificate for **two successive releases**. | BLOCKED: owner must privately configure signing secrets. |
| Data-preserving updates | Install a newer owner-signed LITE APK over the previous owner-signed build without uninstalling; chats, notes, tasks and private GGUF remain. | NOT VERIFIED on a phone. |
| Network independence | With airplane mode enabled, typed open-ended questions generate tokens without a PC, cloud gateway, model download or provider API. | NOT VERIFIED on a phone. |
| Privacy | No chat/text/notes/tokens included in shared diagnostics; user explicitly opts into any internet research. | JVM tests and source review; Android network behavior not device-verified. |

## Real-phone acceptance tests

Use an ARM64 Android 13+ phone with sufficient storage and RAM. Keep a backup
of existing Astra messages and the large model archive before uninstalling any
APK. GitHub Actions debug builds may use different signing certificates.

1. **Installer identity:** Install intended APK and verify its version under
   Android Settings → Apps → Astra. Compare with the version displayed in the
   Astra chat header. Confirm Astra-icon navigation and initially collapsed
   Mission Control; no legacy bottom tabs/overflow menu.
2. **Offline FULL:** In airplane mode, run Settings → **Test on-device AI** and
   verify it says `OFFLINE TEST VERIFIED` **only when** the model produces
   `ASTRA OFFLINE`. Repeat after closing and reopening the app.
3. **Offline LITE:** Start from a LITE installation with no model. Confirm that
   it clearly asks for a local import instead of pretending to run AI. Import
   one GGUF, a previously downloaded FULL APK or its GitHub artifact ZIP.
   The progress and SHA-256 check must complete without network access.
4. **Conversational competence:** Without connectivity, ask
   `explain compound interest` and check that the annual example reaches
   P1,331 from P1,000 at 10% for three years. Ask a genuinely open-ended
   non-command question and verify Qwen3 generates text.
5. **Streaming and Stop:** Stop a long response while tokens are appearing.
   The app must remain responsive, retain partial text, and allow another
   question after cancellation. Verify switching/clearing chat cannot write
   stale text into another conversation.
6. **Restart recovery:** Force-stop Astra while generating; reopen it and
   confirm the saved conversation is usable without a permanent `THINKING`
   placeholder.
7. **Signed upgrade:** Install two successive owner-signed versions using the
   **same** private key. Confirm chats, tasks, notes and imported Qwen3 1.7B survive
   without downloading another roughly 1.28 GB model.
8. **Resource usage:** Record time-to-first-token, tokens/second, RAM usage,
   5-minute battery drain and thermal behavior on the owner's phone. No
   unexpected native crash, lockup or storage exhaustion.
9. **Speech, backup and accessibility:** Test microphone permissions,
   offline voice availability, backup export/import, usable font scaling,
   screen-reader labels and small-screen keyboard behavior.
10. **Diagnostics:** Copy/share a report after the test; confirm it includes
   version, device model, storage/RAM and challenge status, **not** chat text
   or identifiers. Review contents before sending.

11. **Multilingual listening and spoken replies:** In airplane mode, test at least English, French, Russian and Setswana using separately installed offline ASR/TTS language packs where available. For each language record the spoken input, recognized transcript, text reply, voice locale/accent actually selected and whether speech is genuinely offline. Do not mark an unavailable Android voice or recognition pack as a pass. Test a language switch within one chat, mixed-script replies, and long answers beyond a single Android TTS utterance; verify Unicode characters are preserved and chunks play in order. Distinguish model translation quality from device-installed voice coverage.
12. **Speech privacy under interruption:** Start a long spoken response, then separately lock the phone, turn the screen off, switch to another app, and activate Astra's own app lock. In each case verify queued and currently speaking audio stops immediately and does not restart after unlocking/returning without an explicit user action. Repeat during speech chunk transitions and after rapid background/foreground changes. Verify no private answer is audible from the lock screen.
13. **Study depth and memory:** Open the Study Hub and choose different subjects rather than accepting a default Finance launch. Test one detailed workshop, quiz, spaced-review due/next controls and independent progress in two courses. Reopen Astra and verify progress persists. Record whether full lessons are substantively useful; the presence of a course button alone is not a pass.
14. **Device-library privacy and retrieval:** Authorize a folder once using the Android system picker, import a supported text-bearing document, then verify a relevant citation in airplane mode. Restart the app and confirm the retained permission works without repeated prompting. Revoke permission and verify further access fails safely; originals must remain unchanged. Separately verify that shared-storage filename searches do not silently read file contents. Record incomplete indexes and unsupported media types rather than reporting full-device learning.
15. **1.7B picker return regression (mandatory for LITE):** With a valid, fully downloaded `Qwen3-1.7B-Q4_K_M.gguf` in Downloads, use Settings → Import offline model once. Choose the file using Android Files. Verify Astra returns to **Settings → Model setup**, not a blank new chat, shows copied MiB during the import and a SHA-256-verified READY result. Repeat with app lock enabled and its delay set to immediately: unlocking must reveal the same progress/result, and an interrupted import must display a recoverable message without deleting the source file. Attempt with insufficient free storage and with a wrong GGUF variant; neither may silently return to Chat or claim success. Verify no new network usage and no 0.6B fallback. If a build is signed with a different key, use a separate test installation after backing up data—never uninstall a working copy just to test.
16. **Astra-core navigation tray:** Tap the Astra icon on Chat, Study and Settings. Verify the edge-aligned tray opens from the left, can be dismissed by tapping outside or using Close, and presents six fully labelled destinations with a single clear active indicator. In Chat verify New conversation, Conversations, Search, Copy, Share, Rename, Mission Control, Speak last response and Clear; Clear must still ask for confirmation. Test large Android font scaling, TalkBack, a narrow screen, landscape and system gesture navigation: controls must not overlap the status/navigation bars or require horizontal scrolling.

17. **Cross-feature continuity (experimental):** Open a Finance or Russian
    lesson from Study, then use the Chat **Continue [subject]** control and
    verify the same course is selected. Verify that returning via the Astra
    navigation tray intentionally opens the catalog. Write an unsent Chat draft
    before launching a Study lesson and confirm it survives. In Tasks and Notes,
    tap **Discuss** and inspect the quoted draft without sending it; confirm
    the source item is unchanged and a pre-existing unsent draft is never
    overwritten. After a non-sensitive Local tools calculation, use
    **Discuss result in Chat**; sensitive tools (including password generation)
    must not show this action. Long-press an Astra reply and use **Save message
    to Notes**; verify the separate confirmation and the *unverified* provenance
    label survive a restart. Test Android Back, landscape, small screens,
    TalkBack and large text. None of these flows may require network access,
    cross private conversations without consent, or silently execute a task.

18. **Adaptive reasoning and verified calculations (experimental):** Without
    importing any private notes, ask `What is 17 multiplied by 19? Reply only
    with the number.` and verify the local calculator returns `323`; separately
    ask a complex multi-step calculation and confirm the system does **not**
    misinterpret it as an exact two-number expression. With the Qwen3 1.7B
    model installed, leave **Adaptive reasoning** off and record two identical
    complex prompts and their time-to-first-token, final accuracy and blank
    response rate. Then enable the option in Settings and repeat the *same*
    prompts, model and device configuration, including after restart. Verify
    ordinary chat and explicit short-answer prompts remain fast, quoted
    instructions cannot select thinking mode, and diagnostic benchmarks are
    isolated from the owner toggle. Record both successes and failures.
    These source/JVM features are **not** a verified AI quality gain until
    independent device A/B evidence exists. App remains functional in airplane
    mode; no new permissions, network services or required model download.

19. **Private native inference latency sample:** After successfully generating
    a genuinely open-ended offline answer, open Settings → Offline AI models
    and record the displayed first-native-output and full-request milliseconds,
    including a cold start and at least three warm trials on the same handset.
    The chunk count is **not** a token count; Qwen hidden thinking can produce
    native chunks before any visible text. Confirm no prompt, model path,
    conversation ID, token or private-book excerpt appears in the timing
    report. Stop a generation and verify a cancelled or empty answer cannot
    overwrite a previous successful timing. Compare adaptive reasoning ON
    and OFF using identical prompts and the same 1.7B weights. Only phone
    observations, not JVM timing mocks, can satisfy the performance gate.

## Honest release labels

- **EXPERIMENTAL:** Compiles and passes automated source/unit/APK checks,
  but phone-based inference or update behavior is not yet verified.
- **RELEASE CANDIDATE:** Core phone inference/import/Stop tests pass;
  outstanding usability, long-session, signing or regression tests remain.
- **STABLE:** All mandatory gates above pass on real hardware with results
  recorded. GitHub's owner-signed workflow can publish a stable Release.
  A release is not stable merely because CI compiled it.

### What to capture from a failed device test

Keep screenshots of the app version and error, and copy Settings → Diagnostics
after reproducing the issue. Share observed behavior and approximate timings.
Do **not** upload personal chat backups, model GGUF files or signing material.

# Astra — Android personal assistant (v0.11.7 · native error recovery)

Astra is a **local-first Android/Kotlin Jetpack Compose assistant** with a futuristic dark interface. It is being developed directly in this repository. The original **Astra-AI** repository is kept separately as a backup.

**Release status: EXPERIMENTAL.** Compilation and APK checks pass, but stable status requires on-phone offline inference, one-time model import, chat/Stop usability, data-preserving same-key updates and privacy checks. See the [stable-release acceptance checklist](docs/stable-release-acceptance.md). Owner-signed builds remain GitHub **pre-releases** until those mandatory checks pass.

## Working capabilities

- Chat interface with **multiple persistent conversations**, an animated compact navigation dock, notes, tasks and completion tracking.
- **Mission Control**: progress summaries, explicit task priorities and next-task suggestions based on saved information.
- **Local memory recall**: search saved notes/tasks with commands such as `recall bond duration`. This is lexical retrieval, not generative AI. Message text can also be searched privately through the chat menus.
- Mathematics, finance calculations, unit conversions, text utilities and random tools.
- Shopping lists, habits, daily overviews and introductory Russian lessons/quizzes.
- Android speech recognition and text-to-speech, depending on installed system services and language packs.
- Explicit opt-in Wikipedia research, which sends the requested public topic to Wikipedia.
- Local JSON backup and confirmed restore under **Settings → Data backup & recovery**. Backups are **not encrypted** and should be kept private. Automatic Android cloud/device-transfer backups are intentionally disabled for conversation privacy; use manual export for migration.
- Dark adaptive launcher artwork, animated Astra branding and a live command-deck HUD.
- **Fully offline conversational AI** with Qwen3 0.6B Q4_K_M and native llama.cpp. A full APK includes the model; a small APK can import the exact same weights **once** from a saved GGUF or earlier Astra APK/ZIP, avoiding repeated cellular downloads.
- Bond Analytics Lab: fixed-coupon price, current yield, modified and Macaulay duration, convexity and DV01.

**Important:** Astra's tasks, notes, calculators and local commands remain deterministic. Unhandled free-form questions now go to a **model running on the Android phone**. A small model may hallucinate or make calculation errors; Astra does not provide live financial prices or unrestricted device automation.

## Recover after native prompt-processing errors (v0.11.7)

The pinned llama.cpp Android binding previously **returned silently on a native prompt-processing failure** without resetting its `ProcessingUserPrompt` state. Subsequent chat requests could time out waiting for an inference engine that never became ready. The build now patches that path to throw a structured error and enter `Error` state.

When clearing an error, the native wrapper now unloads a fully prepared model context and clears its conversation cache before reloading. It avoids calling `unload()` when preparation never completed. Both FULL and LITE include this fix without adding GGUF weights to LITE. The patch fails closed on source drift and has automated regression tests for state transition, cleanup, idempotence and error paths.

**Limitations:** A native crash or partially initialised allocation may still require a full app restart. Phone-based failures and inference latency remain to be verified; this does not make the application a stable release.

## No private prompts or generated tokens in Android inference logs (v0.11.6)

The pinned llama.cpp Android native binding previously logged raw system prompts, user prompts, assistant messages, individual tokens, and model file paths during diagnostics. Those logs were not sent to an AI server, but they should not be exposed through Android diagnostic logs.

The build now **redacts those sensitive log sites in both the C++ JNI code and upstream Kotlin wrapper before compiling**. Ordinary diagnostics retain generic status and error categories. The hardening is deterministic, idempotent and **fails the build** if the pinned upstream logging statements drift or could not be redacted; the FULL, LITE and protected owner-signed workflows verify it. Python regression tests also check fail-closed behaviour.

This protects the known llama.cpp Android binding log statements; it is **not a blanket guarantee** that every Android/OS logger, crash report or installed app is incapable of logging information. No network endpoint or remote analytics is introduced.

## Resilient native AI and safe model replacement (v0.11.5)

The on-device llama.cpp session now **invalidates native conversation state after a cancelled request, JNI error, startup failure or empty generation**. The next request reloads its local model rather than reusing a potentially incomplete response in the native KV-cache. This avoids context contamination following a Stop action; model weights are not downloaded again.

Importing a replacement GGUF now preserves the previous copy until the new file has passed its size and SHA-256 verification. Replacements use a recoverable backup rename: if Android kills the process between renames, Astra restores the previous private model file on the next launch. FULL-bundled model preparation uses the same mechanism. Additional JVM tests cover cancellation, reloading, replacement and interruption recovery.

**This is crash-recovery engineering, not proof of physical-device token generation.** The JNI engine, memory use, Stop latency and upgrade behaviour still require phone testing. App size stays small for LITE.

## Ask your private memory, with original evidence (v0.11.4)

Astra can now produce **source-backed answers from your own saved information** without an internet connection or separate PC. This is **explicitly opt-in**: type `ask my memory why did I choose bonds?` or `analyse my notes about risk`. Alternatively tap the Astra icon → **Search all local memory** → **Ask with evidence**. Regular chat and Wikipedia searches do not silently load saved notes or archived conversations.

The retrieval engine ranks up to four short excerpts from your notes, open tasks and **only your own** messages in current or archived chats. Assistant-generated replies are excluded. If nothing relevant is found, Astra says so rather than fabricating a personal memory. If Qwen3 is installed, it can attempt an interpretation using **only those selected excerpts**, through an isolated native on-phone inference context. The final response attaches original labelled source excerpts [S1]–[S4] for verification. If no model is installed, the excerpts remain available without a model download.

**Limitations:** Retrieval is lexical, not neural semantic search. The small 0.6B model can misunderstand evidence or produce inaccurate inferences; attached excerpts are the verification reference, not guaranteed model citations. Local storage and existing conversation backups are unchanged. No new model weights or network dependencies were added, so the LITE APK remains the low-data update option.

## Chat that stays readable while AI is generating (v0.11.3)

Astra now follows the latest reply only until you deliberately scroll upward.
During on-device Qwen3 streaming, you can review older messages without Astra
dragging you back to the bottom on every token batch. A compact **↓ Latest
messages** action appears only while you are reading older chat content.
Tapping it, sending another message, or manually scrolling to the end resumes
following. Choosing a **Find in chat** search result pauses auto-follow too.
This change runs entirely within the Android UI, needs no network, and
does not alter existing saved conversations or model weights.

## Private, source-grounded recall across chats (v0.11.2)

Astra can now answer explicit questions about **what you previously wrote** without using mobile data, the 462 MiB model, or any external AI service. Its memory search indexes saved notes, open tasks, user-written messages in the active chat, and user-written messages from archived conversations. AI-generated replies are **excluded** so Astra does not misrepresent model output as something you said.

Try `memory overview`, `recall bond duration`, `what did I say about dividends?`, or `find my notes about risk`. Results include the **original source** (note number, task number, active message or archived chat title) and a short excerpt. If nothing matches, Astra explicitly states no evidence was found instead of inventing a memory.

To search without typing commands, tap **Astra icon → Search all local memory**. The search runs in a background coroutine, with bounded result sizes, and never transmits user data. It is a **literal token-based retrieval engine**, not semantic embeddings; wording differences may require a refined search. The full text remains in your private local backup/history. A user must explicitly initiate retrieval; there is no automatic model-memory injection.

## Offline finance education and verified AI diagnostics

**v0.11.1** adds deterministic offline finance explanations for common natural-language questions, including `explain compound interest`, bond duration, convexity, yield to maturity, diversification, present value, annuities and dividend yield. These are available **without Qwen3** or mobile data. Unsupported open-ended questions still use the locally installed Qwen3 model.

The **Test on-device AI** control now requires the model to generate the expected `ASTRA OFFLINE` phrase. Blank output, fallback errors and incorrect responses are never recorded as a successful test. The shareable diagnostic report distinguishes VERIFIED, UNVERIFIED and FAILED statuses. This is a local challenge test, **not** a remote verification or a guarantee of model accuracy.

## Avoid downloading a 539 MiB APK after every change

**Astra v0.11.7** has two installable Android options built automatically on GitHub:

| Variant | What you download | Best for |
| --- | --- | --- |
| **LITE** | Android UI, tasks and llama.cpp native inference, **without** GGUF model weights (enforced under 150 MiB) | Regular app updates over mobile data. |
| **FULL** | The same Android app with Qwen3 Q4_K_M included (~539 MiB) | First installation when no model file is available. |

Open [GitHub Actions](https://github.com/molosiwabakang51-hash/Astra/actions), choose a **successful v0.11.7 build**, and under Artifacts select **`Astra-v0.11.7-LITE-Offline-APK`** for the much smaller download. The full alternative is **`Astra-v0.11.7-FULL-Offline-APK`**. Extract the downloaded artifact ZIP; inside is the versioned APK. Both have the same app ID and UI but do **not** have the same bundled assets.

**Import the model once without using internet data:** In the LITE app, tap **Astra icon → Settings → Import offline model once**. Choose a GGUF file already saved on the phone, a **previously downloaded large Astra APK**, or its original **GitHub artifact ZIP containing that APK**. Astra extracts the Qwen3 weights locally, checks their exact SHA-256, and stores the model privately. The original selected file is not modified. Once imported, airplane-mode chat works entirely on-device, subject to real-phone performance.

If you already downloaded a 500+ MB Astra APK, **retain the APK or artifact ZIP in Downloads** instead of downloading another copy of Qwen3. The import feature can reuse it directly. If you do not have any old model source saved, use Wi-Fi once to get the GGUF or FULL variant rather than repeatedly using cellular bundles.

**Signing is essential for preserving data and the imported model across app updates.** GitHub Actions test/debug APKs often use **different signing keys** across workflow runs. Android will not install one as an update over a differently signed version. Export your chat/task backup **before uninstalling** a debug build, and retain the GGUF/APK/ZIP in Downloads for offline re-import. After a one-time migration, use the **protected owner-signed LITE release workflow** with the same permanent keystore for every new version; the signed installers are published to [GitHub Releases](https://github.com/molosiwabakang51-hash/Astra/releases) and support in-place upgrades. This workflow cannot publish until the owner privately configures the signing environment in [release-signing.md](docs/release-signing.md).

**Important limitation:** GitHub Pages serves static websites. It cannot execute this native Android APK or host a full llama.cpp inference server. GitHub Actions builds the app, GitHub Releases distributes signed updates, and AI inference runs on the Android phone.

### Confirming the installed version

The chat header displays `v0.11.7 · OFFLINE AI` (or the full signed release build version, such as `0.11.7.123`). The Astra icon opens the menu, the optional mission deck starts hidden and there is no old permanent bottom navigation bar. Settings clearly distinguishes a model **bundled in FULL**, **imported on the phone**, or **missing on LITE**. A missing model does not silently revert to unreliable canned answers; it prompts local import.

## Offline model and low-data builds

**v0.11.0 data-saving release:** The new LITE APK carries all Kotlin UI and llama.cpp native inference code but excludes the ~462 MiB model file. The model is imported once from Downloads, a prior APK or a GitHub artifact ZIP. GitHub Actions checks that model weights are **absent** from LITE and **present/verified** in FULL, and reports both download sizes. The owner-signed workflow can publish persistent LITE releases with the verified certificate.

**v0.10.4 install clarity:** The chat header displays the installed build version and offline AI identity. GitHub publishes one versioned APK file per workflow, and CI rejects a version or package-name mismatch. A regression guard ensures the icon-tap navigation, initially collapsed mission deck, and on-device model fallback remain implemented.

**v0.10.3 diagnostics:** Settings → Offline AI now offers **Copy diagnostics** and **Share report**. Reports are generated locally and show device model, Android version, supported ABIs, free app storage, available RAM, the bundled/extracted model sizes and the offline self-test result. They exclude conversations, tasks, notes, device identifiers, secrets, access tokens and raw model output; there is no automatic upload. Review before sharing.

**v0.10.2 first-run resilience:** When a model is first verified, extracted or loaded, Astra reports the current phase in chat. The Stop control now cancels disk copying/checksumming cooperatively, cleans temporary files, and avoids leaving a stalled model preparation. Any saved "THINKING" placeholder left by an Android process restart is displayed as an interrupted response.

**v0.10.1 user experience:** Text generation now appears progressively in the chat bubble, and the Send button becomes a **Stop** control during inference. Stopping a response preserves the text already generated. Switching or clearing a conversation cancels its inference request so an old response cannot overwrite a different chat.


Qwen3 0.6B (Q4_K_M) occupies roughly **462 MiB**. In the FULL variant, weights are downloaded and verified on the **build machine**, then packaged inside the APK. In the LITE variant, **no weights are packaged**; the owner must import a previously saved GGUF/APK/ZIP (or obtain model weights just once). **The app never downloads a language model automatically**.

- **Model:** QuantFactory's Qwen3-0.6B Q4_K_M GGUF on Hugging Face: https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF. SHA-256: `7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d`. Base Qwen3 model license: Apache 2.0.
- **Engine:** Open-source llama.cpp's Android JNI binding, pinned to `b12248` (`3837bf0`). Documentation: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md.
- **Compatibility:** ARM64 (`arm64-v8a`) devices running Android 13 / API 33 or newer. App ID remains `com.astra.agent`. The native binding raises the former API 26 minimum. RAM usage and token speed vary by device.
- **First message:** FULL verifies and extracts its bundled GGUF once. LITE uses the same private verified model file after the owner imports it. Keep ~665 MB free for import and retain your selected GGUF/APK/ZIP outside the app in case you must uninstall a differently signed debug build. A same-signature upgrade preserves the private model and chats.
- **Privacy:** The model runs on the same phone even in airplane mode. Explicit Wikipedia research and Android's optional speech services are separate, and may use network access. No AI chat text is transmitted to an external model service.
- **Quality:** A 0.6B model is substantially less capable than large hosted models; local command handling and deterministic calculators retain priority.
- **On-phone self-test:** Open the Astra-logo menu → Settings → **Test on-device AI**. With airplane mode enabled, the test prompts the packaged Qwen3 model without using the network and shows a response or the actual error and elapsed time. This is a practical device-level acceptance check, not a benchmark.
- **Open-source notices:** Model Apache 2.0 license and native inference MIT/dependency licenses are bundled inside the APK under `assets/open_source_licenses/`.


### Reproducible Android builds

On the **build machine only**, install Java 17, Android SDK platform 36, NDK `29.0.13113456`, CMake `3.31.6`, Gradle `8.13`, Git and Python 3. Run:

```sh
python3 scripts/prepare_offline_android.py
cd android
gradle --no-daemon testDebugUnitTest assembleDebug
cd ..
python3 scripts/verify_offline_apk.py
```

For a **small APK** build, run the following instead (requires the same Android toolchain):

```sh
python3 scripts/prepare_offline_android.py --lite
cd android
ASTRA_BUNDLE_MODEL=false gradle --no-daemon testDebugUnitTest assembleDebug
cd ..
python3 scripts/verify_lite_apk.py
```

GitHub Actions performs both builds and verifies their contents. The GGUF stays **outside Git history**. FULL includes verified GGUF in the APK; LITE explicitly rejects GGUF inside the APK, includes all native inference libraries and remains below the size gate. Neither build uploads private chat data.

The previous `gateway/` server remains as historical code but is **not invoked by Astra v0.10.0**. It is not required for offline conversation.
## Builds

The workflow at `.github/workflows/android.yml` builds **FULL and LITE** on pushes, PRs, manual dispatch and weekly schedule. Successful [GitHub Actions runs](https://github.com/molosiwabakang51-hash/Astra/actions) publish separate versioned APK artifacts and size reports. LITE must be under 150 MiB and exclude the model, while FULL must pass Qwen3 checksum verification.

The Android project is in `android/`, with application ID `com.astra.agent`, Android API 33 minimum, Java 17 and Gradle 8.13 on CI.

The **FULL debug APK** includes model weights and is ~539 MiB. The **LITE debug APK** contains only the app plus native inference and must be under 150 MiB. Both debug APKs are for testing only. GitHub runners may use different debug signing keys, preventing in-place updates. **Before uninstalling any older debug build, export your data.** An owner-signed release workflow is now provided but cannot produce durable APKs until the owner privately configures a permanent keystore and signing secrets. See **[Owner-signed release setup](docs/release-signing.md)**. Its first install over a debug build still requires export → uninstall → install → restore; later releases signed by the same owner key can update without uninstalling. Stable private release signing and device verification are tracked in [issue #14](https://github.com/molosiwabakang51-hash/Astra/issues/14). Never put keystores or API keys in this public repository.

## Conversation management and privacy

- Tap the **ASTRA** logo for navigation and all chat options in one menu. Mission Control is shown only when requested; microphone dictation is beside the message field, so the chat takes more screen space.
- In the Astra-logo menu, choose **Find in chat** to search the current conversation locally and jump directly to matching messages.
- In the Astra-logo menu, choose **New conversation** to archive the current thread without deleting it, **Conversations** to find and switch between previous threads, or **Rename current chat** to give the active thread a custom title. In the conversation picker, search **saved chat titles and message text** with local-only matching excerpts, rename saved chats, or individually delete unwanted conversations with confirmation.
- Typed or spoken `new chat` / `new conversation` now opens another conversation **without deleting the old one**. `switch chat` opens the conversation picker. `clear chat` still requires confirmation before it deletes the active history.
- Astra keeps local conversations in app-private storage. Automatic Android system backup and device-transfer restore are disabled to reduce unintended copies of private data.
- Astra's current local conversation archive supports **up to 40 saved threads**. When it is full, Astra does **not** delete old chats automatically. It shows a storage-management prompt instead. Switching between existing chats continues to work at capacity without dropping an unrelated conversation.
- **Before uninstalling or changing devices**, export a JSON backup through Settings, keep it somewhere private, and restore it manually after installing Astra. Deleting the app without exporting can permanently lose its local notes, chats, and tasks.

## Quick command examples

- `Mission control`, `What's next?`, `Set task 2 priority high`
- `Hey Astra, please show me my tasks`
- `Remember that bond prices usually move inversely to yields`
- `What did I say about bond duration?`
- `Teach me Russian`, `Russian quiz`
- `Plan my day`, `Add milk to shopping list`
- `Calculate 12 * 5`, `Compound interest 10000 10 2 12`
- `Research the solar system`, `Help`

See the repository [issues](https://github.com/molosiwabakang51-hash/Astra/issues) for outstanding model integration, quality testing, and interface development.

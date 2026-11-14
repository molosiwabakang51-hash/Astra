# Astra — Android personal assistant (v0.11.1 · verified offline education)

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

## Offline finance education and verified AI diagnostics

**v0.11.1** adds deterministic offline finance explanations for common natural-language questions, including `explain compound interest`, bond duration, convexity, yield to maturity, diversification, present value, annuities and dividend yield. These are available **without Qwen3** or mobile data. Unsupported open-ended questions still use the locally installed Qwen3 model.

The **Test on-device AI** control now requires the model to generate the expected `ASTRA OFFLINE` phrase. Blank output, fallback errors and incorrect responses are never recorded as a successful test. The shareable diagnostic report distinguishes VERIFIED, UNVERIFIED and FAILED statuses. This is a local challenge test, **not** a remote verification or a guarantee of model accuracy.

## Avoid downloading a 539 MiB APK after every change

**Astra v0.11.1** has two installable Android options built automatically on GitHub:

| Variant | What you download | Best for |
| --- | --- | --- |
| **LITE** | Android UI, tasks and llama.cpp native inference, **without** GGUF model weights (enforced under 150 MiB) | Regular app updates over mobile data. |
| **FULL** | The same Android app with Qwen3 Q4_K_M included (~539 MiB) | First installation when no model file is available. |

Open [GitHub Actions](https://github.com/molosiwabakang51-hash/Astra/actions), choose a **successful v0.11.1 build**, and under Artifacts select **`Astra-v0.11.1-LITE-Offline-APK`** for the much smaller download. The full alternative is **`Astra-v0.11.1-FULL-Offline-APK`**. Extract the downloaded artifact ZIP; inside is the versioned APK. Both have the same app ID and UI but do **not** have the same bundled assets.

**Import the model once without using internet data:** In the LITE app, tap **Astra icon → Settings → Import offline model once**. Choose a GGUF file already saved on the phone, a **previously downloaded large Astra APK**, or its original **GitHub artifact ZIP containing that APK**. Astra extracts the Qwen3 weights locally, checks their exact SHA-256, and stores the model privately. The original selected file is not modified. Once imported, airplane-mode chat works entirely on-device, subject to real-phone performance.

If you already downloaded a 500+ MB Astra APK, **retain the APK or artifact ZIP in Downloads** instead of downloading another copy of Qwen3. The import feature can reuse it directly. If you do not have any old model source saved, use Wi-Fi once to get the GGUF or FULL variant rather than repeatedly using cellular bundles.

**Signing is essential for preserving data and the imported model across app updates.** GitHub Actions test/debug APKs often use **different signing keys** across workflow runs. Android will not install one as an update over a differently signed version. Export your chat/task backup **before uninstalling** a debug build, and retain the GGUF/APK/ZIP in Downloads for offline re-import. After a one-time migration, use the **protected owner-signed LITE release workflow** with the same permanent keystore for every new version; the signed installers are published to [GitHub Releases](https://github.com/molosiwabakang51-hash/Astra/releases) and support in-place upgrades. This workflow cannot publish until the owner privately configures the signing environment in [release-signing.md](docs/release-signing.md).

**Important limitation:** GitHub Pages serves static websites. It cannot execute this native Android APK or host a full llama.cpp inference server. GitHub Actions builds the app, GitHub Releases distributes signed updates, and AI inference runs on the Android phone.

### Confirming the installed version

The chat header displays `v0.11.0 · OFFLINE AI` (or the full signed release build version, such as `0.11.0.123`). The Astra icon opens the menu, the optional mission deck starts hidden and there is no old permanent bottom navigation bar. Settings clearly distinguishes a model **bundled in FULL**, **imported on the phone**, or **missing on LITE**. A missing model does not silently revert to unreliable canned answers; it prompts local import.

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

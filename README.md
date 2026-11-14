# Astra — Android personal assistant (v0.10.0 · on-device AI)

Astra is a **local-first Android/Kotlin Jetpack Compose assistant** with a futuristic dark interface. It is being developed directly in this repository. The original **Astra-AI** repository is kept separately as a backup.

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
- **Bundled, fully offline conversational AI** through Qwen3 0.6B Q4_K_M and the native llama.cpp Android binding. No computer, model server, account, subscription, or phone-side model download.
- Bond Analytics Lab: fixed-coupon price, current yield, modified and Macaulay duration, convexity and DV01.

**Important:** Astra's tasks, notes, calculators and local commands remain deterministic. Unhandled free-form questions now go to a **model running on the Android phone**. A small model may hallucinate or make calculation errors; Astra does not provide live financial prices or unrestricted device automation.

## Bundled on-device Qwen3 (no separate computer)

Qwen3 0.6B (Q4_K_M) is **approximately 484 MB**. The final debug APK should therefore be over 450 MB—not the old 8 MB. The weights are downloaded and verified on the **build machine**, then packaged inside the APK. **The phone never downloads a model after installation.**

- **Model:** QuantFactory's Qwen3-0.6B Q4_K_M GGUF on Hugging Face: https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF. SHA-256: `7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d`. Base Qwen3 model license: Apache 2.0.
- **Engine:** Open-source llama.cpp's Android JNI binding, pinned to `b12248` (`3837bf0`). Documentation: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md.
- **Compatibility:** ARM64 (`arm64-v8a`) devices running Android 13 / API 33 or newer. App ID remains `com.astra.agent`. The native binding raises the former API 26 minimum. RAM usage and token speed vary by device.
- **First message:** The built-in model is verified and copied once into Astra's private no-backup storage because the Android engine loads models from a file path. Leave at least **1 GB additional storage** available for this extraction and installation. Later requests do not require a model download.
- **Privacy:** The model runs on the same phone even in airplane mode. Explicit Wikipedia research and Android's optional speech services are separate, and may use network access. No AI chat text is transmitted to an external model service.
- **Quality:** A 0.6B model is substantially less capable than large hosted models; local command handling and deterministic calculators retain priority.

### Reproducible offline APK build

On the **build machine only**, install Java 17, Android SDK platform 36, NDK `29.0.13113456`, CMake `3.31.6`, Gradle `8.13`, Git and Python 3. Run:

```sh
python3 scripts/prepare_offline_android.py
cd android
gradle --no-daemon testDebugUnitTest assembleDebug
cd ..
python3 scripts/verify_offline_apk.py
```

GitHub Actions performs these preparation and verification steps automatically. The reproducible script pins llama.cpp and checks the GGUF's SHA-256 before packaging. The binary weights are intentionally **not in Git**, avoiding an enormous source repository; they **are in the resulting APK**. The build will fail rather than ship an 8 MB APK missing the model.

The previous `gateway/` server remains as historical code but is **not invoked by Astra v0.10.0**. It is not required for offline conversation.
## Builds

The workflow at `.github/workflows/android.yml` prepares the model and native runtime, then compiles and tests the app on pushes to `main`, pull requests, manual dispatch and a weekly schedule. It publishes the **Astra-offline-Qwen3-debug-APK** artifact from successful runs under [GitHub Actions](https://github.com/molosiwabakang51-hash/Astra/actions).

The Android project is in `android/`, with application ID `com.astra.agent`, Android API 33 minimum, Java 17 and Gradle 8.13 on CI.

The generated **offline debug APK is for development/testing**, includes model weights, and should be roughly 500 MB or more. GitHub runners may use different debug-signing keys, preventing in-place updates. **Before uninstalling any older debug build, export your data.** An owner-signed release workflow is now provided but cannot produce durable APKs until the owner privately configures a permanent keystore and signing secrets. See **[Owner-signed release setup](docs/release-signing.md)**. Its first install over a debug build still requires export → uninstall → install → restore; later releases signed by the same owner key can update without uninstalling. Stable private release signing and device verification are tracked in [issue #14](https://github.com/molosiwabakang51-hash/Astra/issues/14). Never put keystores or API keys in this public repository.

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

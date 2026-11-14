# Astra — Android personal assistant (v0.9.3)

Astra is a **local-first Android/Kotlin Jetpack Compose assistant** with a futuristic dark interface. It is being developed directly in this repository. The original **Astra-AI** repository is kept separately as a backup.

## Working capabilities

- Chat interface with **multiple persistent conversations**, an animated compact navigation dock, notes, tasks and completion tracking.
- **Mission Control**: progress summaries, explicit task priorities and next-task suggestions based on saved information.
- **Local memory recall**: search saved notes/tasks with commands such as `recall bond duration`. This is lexical retrieval, not generative AI or a full chat-history search.
- Mathematics, finance calculations, unit conversions, text utilities and random tools.
- Shopping lists, habits, daily overviews and introductory Russian lessons/quizzes.
- Android speech recognition and text-to-speech, depending on installed system services and language packs.
- Explicit opt-in Wikipedia research, which sends the requested public topic to Wikipedia.
- Local JSON backup and confirmed restore under **Settings → Data backup & recovery**. Backups are **not encrypted** and should be kept private.
- Dark adaptive launcher artwork, animated Astra branding and a live command-deck HUD.
- Optional **natural-language Local AI** routing for unsupported questions to a model running on the owner's own computer through an authenticated private HTTPS gateway. No paid model API or subscription is required. Offline commands remain local.
- Bond Analytics Lab: fixed-coupon price, current yield, modified and Macaulay duration, convexity and DV01.

**Important:** Astra's offline engine uses deterministic command routing. A generative model becomes available **only after the user installs a local Ollama model, runs their own authenticated gateway and enables networked Local AI**. There is no preconfigured live AI provider. Unrestricted device automation, camera reasoning, autonomous scheduled tasks and live financial quotes are not included.

## Zero-API-fee local model conversation

Astra can send unanswered natural-language questions to a **free-to-run local model hosted on your own computer** after explicit opt-in. The model runs using [Ollama](https://ollama.com), without paid inference APIs or subscription charges. The Android phone connects privately over HTTPS with a session-only gateway access token. A documented no-fee personal setup uses Tailscale Serve, which keeps your gateway private. See [gateway/README.md](gateway/README.md) for Windows setup, free networking, limits and tests.

The Android gateway token is **session-only**, not saved in preferences or backups; re-enter it after app restart. Up to 16 recent chat messages can be sent to your own computer, including messages displaying task or note content. The local model cannot execute device actions. **There is no metered model API fee**, but your computer consumes electricity and initial model downloads use data. This is not on-device phone inference; the computer must be running.

## Builds

The workflow at `.github/workflows/android.yml` compiles and tests the app on pushes to `main`, pull requests, manual dispatch and a weekly schedule. It publishes the **Astra-debug-APK** artifact from successful runs under [GitHub Actions](https://github.com/molosiwabakang51-hash/Astra/actions).

The Android project is in `android/`, with application ID `com.astra.agent`, Android API 26 minimum, Java 17 and Gradle 8.9 on CI.

The generated **debug APK is for development/testing**. Build runners may use different debug-signing keys, which can prevent an in-place update from a previously installed APK. Before reinstalling, export your data. Stable private release signing is tracked in [issue #14](https://github.com/molosiwabakang51-hash/Astra/issues/14). Never put keystores or API keys in this public repository.

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

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
- Optional **natural-language Cloud AI** routing for unsupported questions through a private, authenticated model gateway. Offline commands remain local and operate when no gateway is configured.
- Bond Analytics Lab: fixed-coupon price, current yield, modified and Macaulay duration, convexity and DV01.

**Important:** Astra's offline engine uses deterministic command routing. A generative model becomes available **only after the user independently deploys and authenticates a private AI gateway and enables Cloud AI**. There is no preconfigured live AI provider. Unrestricted device automation, camera reasoning, autonomous scheduled tasks and live financial quotes are not included.

## Optional cloud conversation

Astra can send unanswered natural-language questions to a model **only after explicit opt-in**. The phone must be configured with an HTTPS gateway URL and a private gateway client token. No provider API key is embedded in the APK. The owner-operated Node.js backend, API protocol tests, deployment requirements and privacy guidance are in [gateway/README.md](gateway/README.md).

The Android gateway token is **session-only**, not saved in preferences or backups; it must be re-entered after restart. Up to 16 recent chat messages can be sent to the gateway, including messages displaying content from local tasks or notes. The model cannot execute device actions. A ChatGPT subscription does not pay for the separate API service.

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

# Astra — Android personal assistant (v0.9.4)

Astra is a **local-first Android/Kotlin Jetpack Compose assistant** with a futuristic dark interface. It is being developed directly in this repository. The original **Astra-AI** repository is kept separately as a backup.

## Working capabilities

- Chat interface with **multiple persistent conversations**, an animated compact navigation dock, notes, tasks and completion tracking.
- **Mission Control**: progress summaries, explicit task priorities and next-task suggestions based on saved information.
- **Local memory recall**: search saved notes/tasks with commands such as `recall bond duration`. This is lexical retrieval, not generative AI or a full chat-history search.
- Mathematics, finance calculations, unit conversions, text utilities and random tools.
- Shopping lists, habits, daily overviews and introductory Russian lessons/quizzes.
- Android speech recognition and text-to-speech, depending on installed system services and language packs.
- Explicit opt-in Wikipedia research, which sends the requested public topic to Wikipedia.
- Local JSON backup and confirmed restore under **Settings → Data backup & recovery**. Backups are **not encrypted** and should be kept private. Automatic Android cloud/device-transfer backups are intentionally disabled for conversation privacy; use manual export for migration.
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

The generated **debug APK is for development/testing**. Build runners may use different debug-signing keys, which can prevent an in-place update from a previously installed APK. Before reinstalling, export your data. The repo also contains a **separate manual owner-signed release workflow** that can deliver consistently signed updates once you configure your own private keystore and four GitHub Actions secrets. See [owner release signing instructions](docs/RELEASE_SIGNING.md). This workflow has not produced an owner-signed artifact until the secrets are configured and a build succeeds. Never put keystores or API keys in this public repository. Release-signing completion remains tracked in [issue #14](https://github.com/molosiwabakang51-hash/Astra/issues/14).

## Conversation management and privacy

- Tap the **ASTRA** logo to navigate between Chat, Tasks, Notes, Tools and Settings.
- In Chat's three-dot menu, choose **New conversation** to archive the current thread without deleting it, **Conversations** to find and switch between previous threads, or **Rename current chat** to give the active thread a custom title. In the conversation picker, search **saved chat titles and message text** with local-only matching excerpts, rename saved chats, or individually delete unwanted conversations with confirmation.
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

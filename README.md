# Astra Offline — zero-cost Android starter

This version runs **without an API key or server**. It is an offline command assistant, **not yet a generative AI model**. It stores notes, tasks, and chat history in Android private app preferences, supports arithmetic, and offers Android voice recognition and text-to-speech where available. Speech recognition may use a network connection depending on the device's installed speech service.

## Build from an Android phone using GitHub Actions

1. Create a GitHub account and a **private** repository. GitHub Actions on private repositories may be subject to free-tier usage quotas.
2. Upload the **contents** of this ZIP to the repository root. Ensure `.github/workflows/android.yml` is present (GitHub's mobile upload interface may hide dot-directories; use GitHub's web editor if necessary).
3. Open the repository's **Actions** tab; choose **Build free Android APK**, then **Run workflow**. A push to the `main` branch also triggers a build.
4. Open the completed run, download the `astra-offline-debug-apk` artifact, unzip it and install `app-debug.apk` on the phone. Android may ask to allow installs from your browser/files app.
5. The debug APK is for personal testing, not a signed Play Store release.

## Commands

- `help`
- `note Buy groceries`
- `notes`
- `task Finish assignment`
- `tasks`
- `done 1`
- `calculate (12 + 3) * 2`
- `time`
- `clear`

## Security and limitations

No Internet permission, no backend and no paid AI APIs. No autonomous access to other apps or unrestricted device control. The app does not execute shell commands. Data remains in the app's private storage, but is not encrypted beyond Android's normal device protections. Removing app data removes stored notes/tasks. The app is not yet a fully featured AI assistant; a future release would need a compatible on-device LLM runtime and a separately downloaded quantized model. The model download and RAM/storage requirements depend on the selected model. No APK was compiled or device-tested in this environment.

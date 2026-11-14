# Astra owner-signed Android updates

Astra's ordinary **Astra-debug-APK** GitHub Actions artifact is suitable for testing only.
GitHub-hosted runners generate disposable debug signing keys, so different debug
builds may not update one another in place. An uninstall can delete Astra's
private conversation history, notes and tasks.

This guide configures a **separate, manually triggered owner-signed release**.
The app keeps the same Android package name: **com.astra.agent**. The existing
local preference namespace remains **astra**.

**Status:** The workflow and Gradle signing configuration are provided in this
repository. No real release keystore or environment secrets are supplied with
the source, and a successful owner-signed release cannot be produced until the
app owner performs the private steps below.

## 1. Generate and protect your permanent signing key

Run this locally on a trusted computer with Java installed. Generate the key
**once**, preserve its original bytes, and reuse it for every release.

### macOS / Linux

~~~sh
keytool -genkeypair \
  -keystore astra-owner-release.jks -storetype PKCS12 \
  -alias astra-owner -keyalg RSA -keysize 3072 -validity 10000 \
  -dname "CN=Astra Owner, O=Astra, C=BW"
keytool -list -v -keystore astra-owner-release.jks -alias astra-owner
base64 -w 0 astra-owner-release.jks > astra-owner-release.base64
~~~

On macOS, replace the last line with:
**base64 -i astra-owner-release.jks | tr -d '\n' > astra-owner-release.base64**.

### Windows PowerShell

~~~powershell
keytool -genkeypair -keystore astra-owner-release.jks -storetype PKCS12 -alias astra-owner -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Astra Owner, O=Astra, C=BW"
keytool -list -v -keystore astra-owner-release.jks -alias astra-owner
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\astra-owner-release.jks))) | Set-Content -NoNewline .\astra-owner-release.base64
~~~

Save the **SHA256:** certificate fingerprint from keytool -list -v.
Base64 is **not encryption**. Treat the .base64 output like the private
keystore. Never commit, share or paste either value in a chat. Use a private,
encrypted backup under your control, and store passwords separately.

**Critical:** Losing this signing key or its passwords may prevent future
in-place updates. Rotating the key without an Android-supported signing
migration can require reinstalling and restoring data.

## 2. Configure protected GitHub environment secrets

In **molosiwabakang51-hash/Astra**, go to **Settings → Environments** and
create the environment named **astra-release**. Restrict deployment to
**main**, and enable required reviewer approval if your GitHub plan supports
that setting.

Create these **environment secrets** (not committed files or repository source):

| Secret name | Value |
|---|---|
| ASTRA_RELEASE_KEYSTORE_BASE64 | Entire Base64 encoding of the original keystore |
| ASTRA_RELEASE_STORE_PASSWORD | Original keystore password |
| ASTRA_RELEASE_KEY_ALIAS | astra-owner or the alias you actually chose |
| ASTRA_RELEASE_KEY_PASSWORD | Original signing-key password |
| ASTRA_RELEASE_CERT_SHA256 | Owner-approved certificate SHA-256 fingerprint from keytool -list -v |

The fingerprint may include colons. The workflow normalizes case and removes
colons before comparison. **If the APK does not match that exact fingerprint,
publication fails**, preventing accidental signing-key rotation.

These secrets are referenced only by **.github/workflows/owner-release.yml**.
The normal debug and pull-request workflows never receive the owner's key.
Only trigger release builds on the protected main branch.

## 3. Build and verify a private release

Go to GitHub **Actions → Build owner-signed Astra release → Run workflow**
and select **main**. The workflow:

1. Checks out main using read-only permissions.
2. Decodes the keystore into temporary runner storage.
3. Runs Android unit tests and builds a release APK using the protected key.
4. Checks the APK with Android's apksigner verify and compares the signer
   certificate SHA-256 digest to the owner-approved fingerprint.
5. Uploads only **Astra-owner-signed-release**, with 30-day artifact retention.
   A failed signing check never uploads an APK.

The workflow uses a monotonically increasing code derived from the GitHub
workflow's run number (100000 + run_number). Re-running the same workflow
run retains the same version code. Avoid resetting the release workflow or
reducing version codes across signed updates.

The normal Android workflow also builds a release APK with a **temporary CI-only
certificate** solely to smoke-test the signing configuration. That APK is
deleted and must never be installed or distributed as an owner release.

## 4. Upgrade without losing Astra data

**If you have a debug build installed:** Its signing certificate is different.
Android cannot install the first owner-signed release over that debug build.
Before doing anything destructive, open **Astra → Settings → Data backup &
recovery** and export a JSON backup. Verify the file exists and keep it private.
Only then uninstall the debug build, install your first owner-signed APK and
import that backup. The exported file is **not encrypted**.

**For later owner-signed releases:** Keep the same keystore, alias and app ID.
Download the next verified owner-signed APK and install it as an update without
uninstalling. Android normally retains app-private data on a same-signature,
higher-version-code update. Export a manual backup before important upgrades
regardless.

Real-device installation/update and backup/restore smoke tests are still
required; passing a CI build does not prove device-specific behavior.

## Security boundaries

- Never store passwords or keystores in the public repository or debug APK.
- Never change the owner certificate without a deliberate migration plan.
- Never trigger signing with untrusted pull-request source.
- Owner release artifacts are installable distribution files, not a secret
  replacement for protecting the signing keystore.
- If the workflow reports missing secrets or a certificate mismatch, **stop**.
  Do not bypass the verification just to make a release pass.

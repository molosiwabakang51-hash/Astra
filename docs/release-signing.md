# Astra owner-signed Android updates

Astra's ordinary **LITE/FULL debug APK** GitHub Actions artifact is suitable for testing only.
GitHub-hosted runners generate disposable debug signing keys, so different debug
builds may not update one another in place. An uninstall can delete Astra's
private conversation history, notes and tasks.

This guide configures a **separate, manually triggered owner-signed LITE release**.
The small Android installer does not include the ~462 MiB Qwen3 model weights;
import the model ONCE from a locally saved GGUF, old Astra FULL APK, or downloaded
GitHub artifact ZIP. On correctly signed upgrades the imported model remains
in app-private storage and never needs re-downloading.

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

## 3. Build and publish a small owner-signed release on GitHub

After privately configuring all five secrets in the protected
`astra-release` environment, go to GitHub **Actions → Publish owner-signed
Astra LITE update → Run workflow**, selecting **main**.

The workflow downloads the pinned llama.cpp Android library on GitHub's build
computer, **does not download/compile Qwen3 GGUF into the APK**, and runs the
Android unit tests. It compiles the small release, verifies that the model is
absent and native ARM64 inference libraries remain, and checks the signed APK's
certificate fingerprint against `ASTRA_RELEASE_CERT_SHA256`. Publication
fails if the signature differs.

A successful run creates both:

- A 30-day **Astra-v0.12.2-Owner-Signed-LITE** workflow artifact.
- A **persistent GitHub Release** with an APK
  `Astra-v0.12.2-LITE-owner-signed.apk` under a versioned tag such as
  `v0.12.2.123`.

Get the release from:
https://github.com/molosiwabakang51-hash/Astra/releases

The workflow uses monotonically increasing Android version codes based on
the workflow run number (`100000 + GITHUB_RUN_NUMBER`). It uses a version name
such as `0.12.2.123`. When the source version changes in a future release,
the workflow's pinned release/version text must also be updated.

**Status:** An unconfigured or mismatched signing key prevents this process.
GitHub Releases is not an Android app server or a runtime model server; it only
hosts the small installer. GitHub Pages cannot run native Android llama.cpp.

The ordinary Android CI workflow separately creates FULL and LITE **debug**
APKs for testing. Those use disposable GitHub-runner signing certificates
and should not be installed as though they were stable release updates.

## 4. Upgrade without losing Astra data

**Before every update:** Run the read-only `python scripts/check_apk_update.py PATH_TO_APK` compatibility check with an attached authorized device and Android SDK Build Tools. It compares the installed base APK's signer, package name **and installed version code from the APK itself** with the candidate. It only labels an upgrade compatible when certificates match and the candidate versionCode is **strictly greater**. The script temporarily copies the installed base APK to the **computer over USB** for comparison; a FULL installation may require significant computer disk space and USB transfer time. That temporary copy is deleted when checking finishes, and the command **never installs, uninstalls, clears data or reads private chats**.

If it says `BLOCKED` or cannot prove compatibility, keep Astra installed. Do not try to bypass Android signature enforcement by uninstalling. Before any deliberate, owner-authorized migration, verify **both** an accessible, separate chat JSON backup **and** a usable original GGUF/FULL APK outside Astra's app-private directories. A JSON backup does **not** contain model weights; having the JSON file alone is insufficient to preserve offline inference. Keep original data and app untouched until all backups and future installation requirements are confirmed.

**If you have a debug build installed:** Its signing certificate can differ
from the permanent owner release key. Confirm this with the preflight above;
never assume the fingerprint from the package name. If the signer differs,
Android will not accept a first owner-signed release over that debug build.

**Do not uninstall just because the JSON export succeeded.** Open **Astra →
Settings → Data backup & recovery**, export the JSON backup and verify that it
exists in storage outside Astra's app-private area. It is **not encrypted**.
Separately verify that the Qwen GGUF is backed up outside the app, or that an
original FULL APK/artifact remains accessible for offline reimport. If the
model exists only inside Astra's private storage, keep the current app
installed. Migration is a separate deliberate owner decision after verifying
both the chats and model source; the verifier does not perform that migration.

**Import the model without paying for another large download:** After your
first LITE install, open Astra's **logo → Settings → Import offline model once**.
Choose the original Qwen3 Q4_K_M GGUF, an old downloaded Astra FULL APK, or the
GitHub artifact ZIP that contains that APK. The phone verifies SHA-256 before
installing the model privately. **Keep the original chosen file in Downloads**
or other persistent phone storage if there is any chance you will need to
uninstall an older debug build. Import copies model data locally; it never
contacts an external server.

**For later owner-signed releases:** Keep the same keystore, alias and app ID.
Download only the next small verified owner-signed APK and install it as an
update without uninstalling. Android normally retains private imported model
weights, chats and tasks on same-signature, higher-version-code updates. If
you uninstall a build because its certificate differs, the imported private
copy will be erased; reimport from your saved GGUF/APK/ZIP without cellular
data. Export a JSON backup before important upgrades regardless.

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

# Astra owner-signed Android updates

Astra's normal **Astra-debug-APK** artifact is suitable for testing but does not guarantee stable signing across GitHub Actions runners. An Android app can update in place only when its package ID and signing identity are compatible, and the installed version code is lower than the new build's version code. An old debug-signed installation cannot be upgraded directly to an owner-signed release with a different key.

This project provides a manual GitHub Actions workflow that creates a **consistently owner-signed APK** once its owner securely configures the required repository secrets. The key remains yours; the workflow does not generate, commit or publish signing material. No paid signing service is required.

## 1. Generate and protect your signing keystore once

On your own machine with Java 17 installed:

```sh
keytool -genkeypair -v -keystore astra-owner-release.jks -storetype PKCS12 -alias astra-release -keyalg RSA -keysize 3072 -validity 10000
```

Set a strong keystore password and a key password when prompted. Keep the passwords in a private password manager. Store at least one **offline, encrypted backup** of the keystore. Losing the signing key can prevent future updates to apps installed with that key. Never paste a keystore, key password or token into a public chat, commit, issue or pull request.

The repository intentionally ignores `*.jks`, `*.keystore` and common credential file extensions.

## 2. Configure GitHub Actions repository secrets

In the Astra repository, open **Settings → Secrets and variables → Actions → New repository secret**, and add:

| Secret | Content |
| --- | --- |
| `ASTRA_RELEASE_KEYSTORE_B64` | Base64 encoding of the `.jks` file (one line) |
| `ASTRA_RELEASE_STORE_PASSWORD` | Keystore password |
| `ASTRA_RELEASE_KEY_ALIAS` | `astra-release` (or your chosen alias) |
| `ASTRA_RELEASE_KEY_PASSWORD` | Private key password |

To get the base64 text without uploading the keystore elsewhere:

**Windows PowerShell**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\private\astra-owner-release.jks")) | Set-Clipboard
```

**Linux**
```sh
base64 -w0 astra-owner-release.jks
```

**macOS**
```sh
base64 < astra-owner-release.jks | tr -d '\n'
```

Treat the resulting base64 as **secret key material**, not as encryption. Do not attach it to issues or send it to anyone.

## 3. Build and verify an owner-signed APK

In **GitHub → Actions**, select **Build owner-signed Astra APK → Run workflow** on the trusted `main` branch.

The workflow:
1. Refuses to build unless all four signing secrets are present.
2. Decodes the keystore only into the temporary GitHub runner directory.
3. Runs Android JVM unit tests and `assembleRelease`.
4. Verifies the resulting APK using Android `apksigner verify`.
5. Deletes the temporary keystore and publishes only the signed APK as **Astra-owner-signed-APK**, retained for 14 days.

Repeat the workflow with the **same keystore and alias** for every release. The APK uses package ID `com.astra.agent`. Version **0.9.4** has version code **9**; increment the version code for every later upgrade.

Never assume that a successful debug APK is equivalent to an owner-signed release. The workflow requires configured secrets and a successful run before a distributable signed artifact exists.

## Existing Astra installations

If the current Astra installation came from a debug APK and the signer differs from this new release keystore, Android will refuse the in-place update. **Before removing the old installation**, use **Settings → Data backup & recovery** to export a JSON backup and keep it private. Then install the owner-signed release and restore the backup. Manual backups are unencrypted JSON, so protect them appropriately.

Once you have installed Astra using the owner-signed release key, future releases signed by that same key with increasing version codes can update it without deleting local data. The Android platform's normal signing and version checks still apply.

## Remaining requirements

This workflow **does not configure your GitHub secrets** or verify your existing app's signing identity automatically. The release is not published to Google Play and no public distribution is performed beyond the downloadable GitHub Actions artifact. Physical-device installation and upgrade tests are still required.

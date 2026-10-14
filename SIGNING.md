# Signing a release build of XCSABER

The **debug APK** from the normal workflow is already signed with a throw-away debug key. It installs fine, but you cannot rely on it for updates over time. A **release APK** is signed with *your own* key. Phones only accept an update if it is signed with the same key as the installed app, so **if you lose the key you can never update that install.**

## 1. Create a key (once)

### Option A, in GitHub (no extra apps)

1. Use a **private** repository.
2. Add the helper workflow once: **Add file → Create new file**, name it `.github/workflows/create-keystore.yml`, paste the text from the bottom of this page, and commit.
3. **Actions → Create release signing key (one time) → Run workflow.**
4. Open the finished run, download the artifact **XCSABER-signing-key-DELETE-AFTER-DOWNLOAD**, and unzip it. You get `release.jks`, `KEYSTORE_BASE64.txt` and `SECRETS.txt`.
5. **Back these up now** somewhere private (password manager, encrypted cloud storage). The artifact expires after one day and holds the key's password in plain text, so delete it from the run page once you have saved it.

### Option B, in Termux

```
pkg install openjdk-17
keytool -genkeypair -v -keystore release.jks -alias xcsaber -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 release.jks > KEYSTORE_BASE64.txt
```
Choose a strong password and remember the alias (`xcsaber`).

## 2. Give GitHub the key as secrets

Repository → **Settings → Secrets and variables → Actions → New repository secret**. Create four secrets:

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | the whole text inside `KEYSTORE_BASE64.txt` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `xcsaber` (or the alias you chose) |
| `KEY_PASSWORD` | the key password (the same as the keystore password if you used Option A) |

Secrets are hidden after saving and never appear in logs. Never commit `release.jks` or `keystore.properties` (the project's `.gitignore` already blocks both).

## 3. Build the signed APK

**Actions → Build XCSABER APK → Run workflow.** After the debug job, the **Signed release APK** job runs. It restores the key, builds with R8 shrinking, checks the signature with `apksigner verify`, and uploads **XCSABER-release-apk**. If the secrets are missing, that job reports a notice and builds nothing, it never produces an unsigned file and calls it signed. Download and install it like the debug APK. The release build has the plain app id `com.xcsaber.app`.

## 4. Releasing updates

1. Raise `versionCode` (a whole number, +1 each release) and `versionName` in `app/build.gradle.kts`.
2. Run the workflow again with the same secrets. Install over the old version; your data stays.

## 5. Signing on a computer instead

Create `keystore.properties` next to `settings.gradle.kts`:

```
storeFile=release.jks
storePassword=YOUR_PASSWORD
keyAlias=xcsaber
keyPassword=YOUR_PASSWORD
```
Then `./gradlew assembleRelease`. The APK appears in `app/build/outputs/apk/release/`.

## 6. Google Play

Play requires an Android App Bundle (`./gradlew bundleRelease`) and its own upload-key flow, plus a privacy policy and store listing. That is outside what this project sets up.

---

## Key-maker workflow (paste into `.github/workflows/create-keystore.yml`)

```yaml
name: Create release signing key (one time)

# Makes a new signing key and hands it to you as a downloadable artifact.
# Use this only in a PRIVATE repository, download the artifact, then delete it. See SIGNING.md.
on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  keystore:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Generate key
        run: |
          mkdir out
          PASS=$(openssl rand -hex 16)
          keytool -genkeypair -v -keystore out/release.jks -storetype JKS \
            -alias xcsaber -keyalg RSA -keysize 4096 -validity 10000 \
            -storepass "$PASS" -keypass "$PASS" \
            -dname "CN=XCSABER, O=XCSABER, C=US" > /dev/null
          base64 -w 0 out/release.jks > out/KEYSTORE_BASE64.txt
          {
            echo "KEYSTORE_PASSWORD=$PASS"
            echo "KEY_ALIAS=xcsaber"
            echo "KEY_PASSWORD=$PASS"
            echo "Copy KEYSTORE_BASE64.txt's contents into the KEYSTORE_BASE64 secret."
          } > out/SECRETS.txt

      - uses: actions/upload-artifact@v4
        with:
          name: XCSABER-signing-key-DELETE-AFTER-DOWNLOAD
          path: out/
          retention-days: 1
          if-no-files-found: error
```

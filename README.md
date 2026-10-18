# XCSABER

An Android search engine and private web browser, written in Kotlin with the real Android WebView. It is built to be compiled by **GitHub Actions**, so you can produce an APK using only an Android phone.

> **Honest status (v1.1.0).** All the logic that does not need Android itself (URL handling, search URLs, tracker blocking, block statistics, plugin registry, settings model and search, tabs, history, bookmarks, downloads policy, preferences) was compiled and its 105 unit tests passed on a computer. The Android screens and layouts of the 1.1.0 upgrade were cross-checked by script (every resource, string, style and view-binding reference resolves, all XML parses) but **have not been compiled or run on a device yet**. The first GitHub Actions run is the real compile test. If it fails, the log shows the exact line, so send me the red error text and I will fix it.

## What it does

| Area | How it works |
|---|---|
| Web search | DuckDuckGo by default. You can switch to Bing, Brave Search or Startpage in **Privacy → Search provider**. Results are the provider's own real result pages shown in the WebView. Nothing is faked, and no API key is needed. |
| Images, Videos, News | The tab strip opens the provider's own Images, Videos and News pages (thumbnails, titles, publishers and dates come from the provider). Long-press an image for: open source page, open image, download, share link, copy address. |
| Maps | OpenStreetMap in the WebView (free, no key). Menu → *Directions to this place* opens OpenStreetMap directions. Menu → *Open in a maps app* hands a `geo:` link to any installed maps app. |
| Shopping | Bing Shopping page for your query (DuckDuckGo has no shopping vertical). Product names, prices and merchants are whatever Bing shows. A richer in-app product list would need a paid or approval-gated shopping API, so this is the honest free fallback. |
| Suggestions | Live suggestions from DuckDuckGo's public suggestion endpoint, plus your recent searches, bookmarks and history. Turn live suggestions off in Privacy if you do not want typed text sent to DuckDuckGo. |
| Voice search | Android's built-in speech recognizer (needs a speech-recognition app, normally the Google app). |
| Address bar | Typing `example.com` opens it; anything else is searched. `javascript:`, `file:`, `data:` and `intent:` text is never run, it becomes a plain search. |
| Tabs | Many tabs, tab switcher with page previews, private tabs, open links in a new tab or new private tab. |
| Browser | Back, forward, reload/stop, home, share, bookmark, find in page, desktop-site mode, full-screen video, file upload, pop-up control, downloads through Android's DownloadManager. |
| Home | Customizable shortcuts (tap **+** to add; long-press a tile to remove; long-press **+** to reset). Recent searches. |
| Look | Dark-first midnight design (light and system themes too) with electric-blue, cyan and violet accents, an original logo, splash screen, and a shared design system (`values/colors.xml`, `dimens.xml`, `type.xml`, `styles.xml`). Animations follow Android's "remove animations" setting. |
| Privacy Center | Big protection switch, real blocked-request counts (this session and lifetime, normal tabs only), blocklist source, domain count and last update, an **Update list** action with progress and error states, and a plain summary of which protections are active. All of it drives the real `TrackerBlocker` used by `XcWebViewClient`. |
| Extensions & Plugins | Native plugins: **Ad & Tracker Blocker**, **Privacy Guard** (master switch for third-party cookie blocking, HTTPS-only and Global Privacy Control), **Page Tools** (text zoom, block images, darken websites, print / save as PDF) and **Developer Tools** (debug builds only: remote debugging, page info, view source, clear cache). |
| Script add-ons | 14 built-in add-ons in the Extensions Center: Dark Pages, Night Shift, Reading Mode, Force Zoom, Unlock Selection, Cookie Banner Hider, Pop-up Remover, Stop Autoplay, Sticky Header Remover, Distraction-free YouTube, Clean Links, Same-tab Links, Password Peek and a Back-to-top button. Each switches on and off and applies to pages as they load. |
| Userscripts | A Tampermonkey-style script manager (**Extensions → Userscripts**): install `.user.js` scripts from a web address, by pasting code or from a file, or just tap a `.user.js` link (Greasy Fork, GitHub raw). Supports `@match`, `@include`, `@exclude`, `@run-at` and a subset of the `GM_` API. |
| Settings | Categorised settings with descriptions, search, theme, search engine, privacy, downloads, and an About page with version and build info. |

## Privacy and security (what is and is not true)

- **Private tabs** never write to history, recent searches, the download list or saved tab sessions, hide their screenshots in the recent-apps view, and never get tab previews. Where the phone's WebView supports *profiles* (modern Android System WebView), private tabs get their own cookies, storage and cache, deleted when the last private tab closes. On older WebViews that is impossible, so the Privacy screen says so and tells you to use **Clear browsing data**.
- **Tracker blocking** is host-based and uses a maintained public list: tap **Update tracker list** to download the Peter Lowe ad/tracking server list over HTTPS. A small built-in list is used until then. It blocks third-party sub-resources only, never the page you chose to open.
- **Third-party cookies** can be blocked (on by default). **HTTPS-only mode** is optional. Do-Not-Track and Global Privacy Control headers are sent.
- **Certificate errors are never bypassed.** There is no "proceed anyway" button anywhere.
- **Permissions** (camera, microphone, location) are asked only when a website needs them, with Allow/Block per site, reviewable under **Website permissions**.
- Links with other schemes (`mailto:`, `tel:`, `sms:`, `geo:`) ask before opening another app. `intent:` and `file:` links are refused.
- Installer and program downloads (`.apk`, `.exe`, …) show a warning, and XCSABER will not open them for you.
- Settings, history and bookmarks are stored with AndroidX `EncryptedSharedPreferences` (Android Keystore). No analytics, no accounts, no passwords collected.
- **It does not hide your IP address.** Websites, your network and your internet provider can still see you connected. For that you need a trustworthy VPN or Tor. XCSABER does not claim to be anonymous.

## Get the code onto GitHub (from a phone)

You need a free GitHub account. In your phone browser open github.com and, for the steps below, switch the browser menu to **Desktop site** if buttons look missing.

### Route A, no extra apps (recommended)

1. Create a new repository (**+ → New repository**). Name it `xcsaber`. **Private** is a good choice.
2. Tick **Add a README file** so the repository is not empty, then **Create repository**.
3. **Add file → Create new file.** In the name box type `.github/workflows/build-apk.yml` (typing the `/` makes folders). Paste the *Build workflow* text from the bottom of this file. **Commit changes**.
4. Do the same for `.github/workflows/unpack.yml` with the *Unpack workflow* text.
5. **Add file → Upload files**, choose `xcsaber.zip` (the file I gave you), **Commit changes**. Do not unzip it first.
6. Open the **Actions** tab → **Unpack project zip** → **Run workflow**. After about 20 seconds the repository fills with the whole project.

### Route B, Termux (a terminal app, if you prefer git)

```
pkg install git unzip
unzip xcsaber.zip && cd xcsaber
git init -b main && git add -A && git commit -m "XCSABER"
git remote add origin https://github.com/YOUR-NAME/xcsaber.git
git push -u origin main
```
GitHub asks for a username and a *personal access token* as the password (Settings → Developer settings → Tokens, with the `repo` and `workflow` permissions).

## Build the APK

1. Repository → **Actions** tab → **Build XCSABER APK** → **Run workflow** → **Run workflow**.
2. Wait about 6 to 10 minutes. Tap the run to watch it.
3. **Green tick = the app compiled and the tests passed. Red cross = something failed**, and the failing step shows the error. The workflow can never go green without producing an APK.
4. Open the finished run, scroll to **Artifacts**, tap **XCSABER-debug-apk**. A zip downloads (you must be signed in to GitHub).
5. Open the zip with your Files app (tap → Extract). The APK is inside.

## Install on your phone

1. Tap the `.apk`. Android asks to allow installs from this source (your browser or Files app). Allow it for that app only.
2. Tap **Install**. Play Protect may say the app is from an unknown developer; that is normal for a self-built APK. Choose **Install anyway**.
3. Open **XCSABER**. The debug build has its own app id (`com.xcsaber.app.debug`), so it can sit beside a later signed release build.

Want an update-safe, properly signed build? Follow **[SIGNING.md](SIGNING.md)**.

## Project layout

```
xcsaber/
├── .github/workflows/        build-apk.yml, unpack.yml, create-keystore.yml
├── build.gradle.kts          plugin versions (AGP 8.7.3, Kotlin 2.0.21)
├── settings.gradle.kts       repositories, module list
├── gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/   Gradle 8.10.2 wrapper
├── SIGNING.md  README.md
└── app/
    ├── build.gradle.kts      SDK 24–35, dependencies, optional release signing
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/trackers.txt           built-in tracker starter list
        │   ├── java/com/xcsaber/app/
        │   │   ├── XcApp.kt                  app-wide singletons, theme
        │   │   ├── search/                   SearchProvider, SearchEngine, SuggestionClient, UrlUtils, SearchCategory
        │   │   ├── browser/                  Tab, TabManager, XcWebViewClient, XcChromeClient, BrowserHost,
        │   │   │                             SitePermissionStore, UserAgents
        │   │   ├── privacy/                  PrivacyManager, TrackerBlocker, TrackerListManager, BlockStats
        │   │   ├── plugins/                  BrowserPlugin, PluginRegistry, BuiltinPlugins (native plugin framework)
        │   │   ├── data/                     SecureStore, Prefs, BookmarkStore, HistoryStore,
        │   │   │                             RecentSearchStore, ShortcutStore, models
        │   │   ├── downloads/                DownloadHelper, DownloadPolicy, DownloadStore
        │   │   └── ui/                       MainActivity, Adapters, ListDialog, Sheets, Motion,
        │   │   │                             PrivacyCenter, ExtensionsCenter, SettingsScreen, SettingsAdapter, SettingItem
        │   └── res/                          layouts, themes (light/dark), icons, strings, menus
        └── test/java/com/xcsaber/app/        105 JVM unit tests
```

## Plugin framework

Plugins implement `BrowserPlugin` (`plugins/BrowserPlugin.kt`) and are registered in `PluginRegistry`. They are compiled into the app. **Nothing is downloaded or executed at runtime**, and Chrome/Firefox extensions are not supported, because Android WebView cannot load them. ### Script add-ons

Add-ons are small content scripts bundled in `app/src/main/assets/addons/<id>.js` and listed in `plugins/AddonCatalog.kt`. `AddonEngine` picks the enabled ones for a page (web pages only, host-matched) and wraps each so it runs once and cannot break the page or other add-ons; `XcWebViewClient` triggers injection when a page becomes visible and when it finishes loading. Add-ons only run inside the web page. They get no access to XCSABER's data or Android APIs, and nothing is downloaded. To add one: write the `.js` file (helpers `xcCss(text)` and `xcReady(fn)` are provided), add a `ScriptAddon(...)` line to the catalog.

### Userscripts (Tampermonkey-style)

The real Tampermonkey extension cannot run in Android WebView, so XCSABER has its own compatible script manager (`userscripts/`). Every install shows a confirmation with the name, version and the sites the script will run on, a clear warning for scripts that run on every site, and a *View code* button. Only `https://` addresses can be downloaded, scripts are limited to 128 KB each and 40 in total, and they are stored in the app's encrypted storage.

How they run: injected into the main page when it becomes visible and when it finishes loading, each in its own try/catch. XCSABER exposes **no** JavaScript bridge, so a script can do what the page itself can do (read and change that page, use its cookies and storage) and nothing else. Private tabs run scripts too.

Supported: `@name @namespace @version @description @match @include @exclude @exclude-match @run-at @grant`; `GM_addStyle GM_getValue GM_setValue GM_deleteValue GM_listValues GM_log GM_info GM_openInTab GM_setClipboard GM_registerMenuCommand`, the `GM.*` promise forms of the same, and `unsafeWindow`. **Not supported:** `GM_xmlhttpRequest`, `GM_download`, `GM_notification`, `@require` and `@resource` downloads, and menu commands (they are accepted but do nothing). Many popular scripts need `GM_xmlhttpRequest`, so those will only partly work. Scripts run in the page itself rather than a separate sandbox, and `document-start` means "as soon as the page is visible", slightly later than in desktop Tampermonkey. Values saved with `GM_setValue` live in the site's own storage, so they are per website.

The built-in add-ons above are **not** Chrome extensions: Android WebView cannot load those. Installing arbitrary third-party scripts is deliberately not offered yet, because that would run untrusted code on pages where you are signed in.

To add a native plugin: implement the interface, give it a `PluginInfo`, add it to the registry list, and add any new preference to `Prefs`.

## Tests

`testDebugUnitTest` runs in every GitHub Actions build before the APK is built. On a computer with Android Studio: `./gradlew testDebugUnitTest`. They cover URL/scheme safety, search URL building for every provider and category, suggestion parsing, tracker-list parsing and first-party exemption, tab selection and session saving (private tabs excluded), site permissions, history/bookmark/recent-search/shortcut storage, download name sanitising and risky-file detection, and preference defaults.

## Known limits

- Search results are the providers' web pages, so layout and ads are theirs. There is no custom result renderer.
- Videos play on the website's own player (full-screen supported). `blob:` and `data:` downloads are not supported.
- Tab previews are small in-memory snapshots of normal tabs.
- Tracker blocking matches domains only; it does not hide page elements, and it cannot remove all ads. Lifetime counts exclude private tabs by design.
- Darken websites only works while the app theme is dark, and only on WebView versions that support algorithmic darkening.
- Private-tab cookie isolation depends on the phone's WebView (see above).
- Not built for the Play Store yet (that needs a signed `.aab`, see SIGNING.md).

## If the build fails

Open the red step in the run and read the first line starting with `e:` (Kotlin error) or `error:` (resources). Common causes: a typo from copying a file by hand (use the zip), or a dependency version the runner cannot fetch (re-run once). The newest piece of Android API used is the WebView profile API in `privacy/PrivacyManager.kt`; if the compiler complains about `ProfileStore`, that file is the first place to look.

---

## Build workflow (paste into `.github/workflows/build-apk.yml`)

```yaml
name: Build XCSABER APK

# Run it by hand: Actions tab -> "Build XCSABER APK" -> Run workflow.
# It also runs on every push and pull request.
on:
  workflow_dispatch:
  push:
    branches: [main, master]
  pull_request:

permissions:
  contents: read

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Test and build debug APK
    runs-on: ubuntu-latest
    timeout-minutes: 40
    steps:
      - name: Check out the code
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      # Installs Gradle itself, so this works even if gradlew lost its "executable"
      # flag when the files were uploaded from a phone.
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.10.2"

      - name: Make sure the Android SDK pieces are present
        run: |
          yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
          "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35" "build-tools;34.0.0" > /dev/null || true

      - name: Run unit tests
        run: gradle --no-daemon --stacktrace testDebugUnitTest

      - name: Build debug APK
        run: gradle --no-daemon --stacktrace assembleDebug

      - name: Check that the APK really exists
        run: |
          apk=$(ls app/build/outputs/apk/debug/*.apk | head -n 1)
          test -s "$apk" || { echo "::error::No APK was produced"; exit 1; }
          echo "Built: $apk"
          ls -l "$apk"
          sha256sum "$apk"

      - name: Upload the APK
        uses: actions/upload-artifact@v4
        with:
          name: XCSABER-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
          retention-days: 30

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: app/build/reports/tests/
          if-no-files-found: ignore
          retention-days: 7

  release:
    name: Signed release APK (only if signing secrets exist)
    needs: build
    runs-on: ubuntu-latest
    timeout-minutes: 40
    # Only on manual runs, so ordinary pushes never need your secrets.
    if: github.event_name == 'workflow_dispatch'
    env:
      KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
      KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
      KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
      KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
    steps:
      - name: Look for signing secrets
        id: check
        run: |
          if [ -n "$KEYSTORE_BASE64" ] && [ -n "$KEYSTORE_PASSWORD" ] && [ -n "$KEY_ALIAS" ] && [ -n "$KEY_PASSWORD" ]; then
            echo "have=true" >> "$GITHUB_OUTPUT"
          else
            echo "have=false" >> "$GITHUB_OUTPUT"
            echo "::notice::Signing secrets are not set, so no release APK was built. See SIGNING.md."
          fi

      - uses: actions/checkout@v4
        if: steps.check.outputs.have == 'true'

      - uses: actions/setup-java@v4
        if: steps.check.outputs.have == 'true'
        with:
          distribution: temurin
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4
        if: steps.check.outputs.have == 'true'
        with:
          gradle-version: "8.10.2"

      - name: Restore the signing key
        if: steps.check.outputs.have == 'true'
        run: |
          echo "$KEYSTORE_BASE64" | base64 --decode > release.jks
          {
            echo "storeFile=release.jks"
            echo "storePassword=$KEYSTORE_PASSWORD"
            echo "keyAlias=$KEY_ALIAS"
            echo "keyPassword=$KEY_PASSWORD"
          } > keystore.properties

      - name: Build signed release APK
        if: steps.check.outputs.have == 'true'
        run: gradle --no-daemon --stacktrace assembleRelease

      - name: Verify the signature
        if: steps.check.outputs.have == 'true'
        run: |
          apk=$(ls app/build/outputs/apk/release/*.apk | head -n 1)
          test -s "$apk" || { echo "::error::No release APK was produced"; exit 1; }
          apksigner=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n 1)
          "$apksigner" verify --print-certs "$apk"
          sha256sum "$apk"

      - name: Upload the signed APK
        if: steps.check.outputs.have == 'true'
        uses: actions/upload-artifact@v4
        with:
          name: XCSABER-release-apk
          path: app/build/outputs/apk/release/*.apk
          if-no-files-found: error
          retention-days: 30

      - name: Remove the key from the runner
        if: always()
        run: rm -f release.jks keystore.properties
```

## Unpack workflow (paste into `.github/workflows/unpack.yml`)

```yaml
name: Unpack project zip

# One-time helper for phones: upload xcsaber.zip to the repository root, then run this.
# It unzips the project into the repository and commits it.
on:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  unpack:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Unzip into the repository
        run: |
          test -f xcsaber.zip || { echo "::error::Upload xcsaber.zip to the top level of the repository first"; exit 1; }
          rm -rf _unz && mkdir _unz
          unzip -q xcsaber.zip -d _unz
          top=$(ls _unz | head -n 1)
          test -d "_unz/$top/app" || { echo "::error::The zip does not look like the XCSABER project"; exit 1; }
          # GitHub does not let a workflow overwrite workflow files, so those are skipped.
          rm -rf "_unz/$top/.github"
          cp -a "_unz/$top/." .
          rm -rf _unz xcsaber.zip

      - name: Commit
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add -A
          git commit -m "Add XCSABER project files"
          git push
```

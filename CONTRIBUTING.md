# Contributing to XCSABER

Thanks for helping! XCSABER is a Kotlin Android browser built on the system WebView.

## Ground rules
- **No fake features and no false privacy claims.** If something is partial, say so in the UI and the README.
- **Safety first.** No JavaScript bridges to web pages, no bypassing certificate errors, no running downloaded code outside the userscript manager (which always asks first).
- **Keep it light.** The app targets low-to-mid-range phones. Avoid heavy dependencies.

## Build
Builds run on GitHub Actions: **Actions → Build XCSABER APK → Run workflow** (pick your branch). Locally, use JDK 17 and `./gradlew testDebugUnitTest assembleDebug`.

## Pull requests
1. Fork, create a branch, make a focused change.
2. Add or update unit tests for any logic that does not need Android (see `app/src/test`).
3. Make sure the Actions build is green and say how you tried the change on a device.
4. Use the design tokens in `res/values` (colors, dimens, type, styles) instead of hard-coded values.

## Adding things
- **Built-in add-on:** a script in `app/src/main/assets/addons/<id>.js` plus one line in `plugins/AddonCatalog.kt`.
- **Native plugin:** implement `BrowserPlugin` and register it in `XcApp`.
- **Search provider:** see `search/SearchProvider.kt`.

By contributing you agree your work is released under the MIT license.

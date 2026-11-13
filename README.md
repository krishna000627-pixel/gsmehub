# Gamehub

Native Android app that imports, stores and runs offline HTML apps in a fast WebView, with shake-to-do-anything.

Kotlin, Jetpack Compose (Material 3), Room, minSdk 26.

## Build
Push to `main`; GitHub Actions builds a signed APK and publishes it as release `build-<run number>` (secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Locally, put `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) in the project root; it is git-ignored.
The Gradle wrapper is not committed; CI installs Gradle 8.9. Locally run `gradle wrapper --gradle-version 8.9` once.

## Termux
```
bash termux/setup.sh                      # once
bash termux/release.sh "v1: message"      # every release
```

## App format
Optional `gamehub.json` at the app root:
```json
{"name":"My app","version":"1.0","icon":"icon.png","orientation":"auto","fullscreen":false,"keepAwake":false,"shake":"onshake"}
```
`shake` is `true`/`false` or one of `onshake`, `library`, `flashlight`, `screenshot`, `back`, `reload`, `none`.

## JS bridge
`window.Gamehub` (imported apps only). Every call returns a Promise. Feature-detect with `Gamehub.has("flashlight")`.
`vibrate(ms) flashlight(on) toast(text) share(text) clipboardGet() clipboardSet(text) saveFile(name, base64) readFile() notify(title, body) keepAwake(on) setOrientation(mode) appInfo() openApp(id) registerShake(on)`

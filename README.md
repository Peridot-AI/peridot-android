# Peridot for Android

[Peridot AI](https://peridot.ai) on Android: a native app (Kotlin, Jetpack Compose) that talks to the same API as the website, the desktop app and the [CLI](https://github.com/Peridot-AI/peridot-cli).

- **Chat** with streamed answers, rendered as Markdown (code blocks with a copy button, tables, lists, links). Stop an answer at any time; a stopped answer is kept.
- **Models and effort**: tap the model name at the top. Models your plan doesn't include are shown locked.
- **History** (signed in): every exchange is saved to your account, so chats from the website or the CLI show up in the side menu, and chats from the phone show up there. Long-press a chat to rename or delete it.
- **Account**: plan, messages used today and this month's usage.
- Without signing in you can chat with the free limits; history needs an account.
- In English, Spanish, Chinese, Japanese, French, German and Italian, like the website.

Sign-in is by email and password. New accounts are created on [peridot.ai](https://peridot.ai/signup); accounts created with Google can't sign in to the app yet.

## Privacy

The app only talks to the Peridot API (`https://peridot.ai/api`). The sign-in token is stored encrypted with a key from the Android Keystore, and it is excluded from backups and device transfers.

## Build

Requires JDK 17+ and the Android SDK (Android Studio sets both up). Open the folder in Android Studio, or:

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected device or emulator
./gradlew testDebugUnitTest      # unit tests (SSE parser, Markdown)
./gradlew lintDebug
```

### Another server

The API address is fixed at build time:

```bash
# The Android emulator reaches a `bun run dev` server on your computer at 10.0.2.2
./gradlew installDebug -PperidotApiUrl=http://10.0.2.2:3001/api -PperidotWebUrl=http://10.0.2.2:5173
```

Debug builds allow plain HTTP to `10.0.2.2` and `localhost` only; release builds are HTTPS only.

### Release

`./gradlew assembleRelease` (or `bundleRelease` for Google Play) shrinks the app with R8. It is signed when these environment variables are set, otherwise the output is unsigned:

| Variable | |
|---|---|
| `PERIDOT_KEYSTORE` | Path to the keystore (`.jks`) |
| `PERIDOT_KEYSTORE_PASSWORD` | Keystore password |
| `PERIDOT_KEY_ALIAS` | Key alias |
| `PERIDOT_KEY_PASSWORD` | Key password |

Keep the keystore out of the repo (`.gitignore` already excludes `*.jks` and `*.keystore`).

## Layout

```
app/src/main/java/ai/peridot/app/
  MainActivity.kt
  data/        PeridotApi (REST + streaming), SseParser, Settings (encrypted token), API models
  markdown/    Markdown parser (plain Kotlin, unit-tested)
  ui/          ChatViewModel, ChatScreen, Sidebar, Sheets (model, account), Dialogs (sign-in), MarkdownView, theme
```

CI (GitHub Actions) runs the unit tests and lint and builds the debug and release APKs on every push; the debug APK is attached to each run.

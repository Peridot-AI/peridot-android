# Peridot for Android

[Peridot AI](https://tryperidot.io) on Android: a native app (Kotlin, Jetpack Compose) that talks to the same API as the website, the desktop app and the [CLI](https://github.com/Peridot-AI/peridot-cli).

- **Chat** with streamed answers, rendered as Markdown (code blocks with a copy button, tables, lists, links). Stop an answer at any time; a stopped answer is kept.
- **Models and effort**: tap the model name at the top. Models your plan doesn't include are shown locked.
- **History** (signed in): every exchange is saved to your account, so chats from the website or the CLI show up in the side menu, and chats from the phone show up there. Long-press a chat to rename or delete it.
- **Account**: plan, messages used today and this month's usage.
- Without signing in you can chat with the free limits; history needs an account.
- In English, Spanish, Chinese, Japanese, French, German and Italian, like the website.

Sign in with Google (the system account picker, through Credential Manager) or with email and password. New email accounts are created on [tryperidot.io](https://tryperidot.io/signup).

## Privacy

The app only talks to the Peridot API (`https://tryperidot.io/api`). The sign-in token is stored encrypted with a key from the Android Keystore, and it is excluded from backups and device transfers.

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

### Google sign-in (one-time setup)

The app asks Google for an ID token for the **website's** OAuth client (it reads that client ID from `GET /api/auth/config`, so nothing is built in), and the server accepts it like a website sign-in. Google only hands the token to the app when the same Google Cloud project also has an **Android** OAuth client for it. In [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials), in the project of the server's `GOOGLE_CLIENT_ID`:

1. **Create credentials → OAuth client ID → Android**, package name `ai.peridot.app`, and the SHA-1 of the certificate the app is signed with:
   - release keystore: `keytool -list -v -keystore "$PERIDOT_KEYSTORE" -alias "$PERIDOT_KEY_ALIAS"`
   - debug builds (your machine's debug key): `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - apps from Google Play (Play App Signing): the SHA-1 of the *app signing key* in Play Console → Test and release → App integrity
2. Add one Android client per certificate (release, Play signing, each developer's debug key). An Android client has no secret, and nothing goes into the repo or the server's `.env`.
3. The OAuth consent screen must be published (or your account listed as a test user).

Without a matching Android client the picker fails and the app says Google sign-in isn't available; email sign-in keeps working.

## Layout

```
app/src/main/java/ai/peridot/app/
  MainActivity.kt
  data/        PeridotApi (REST + streaming), SseParser, Settings (encrypted token), API models
  markdown/    Markdown parser (plain Kotlin, unit-tested)
  ui/          ChatViewModel, ChatScreen, Sidebar, Sheets (model, account), Dialogs (sign-in), GoogleSignIn, MarkdownView, theme
```

CI (GitHub Actions) runs the unit tests and lint and builds the debug and release APKs on every push; the debug APK is attached to each run.

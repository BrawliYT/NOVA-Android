# NOVA Pro for Android — 0.3.2

Native Android/Kotlin + Jetpack Compose companion app for NOVA Pro.

## Included in 0.3.2

- NOVA launcher icon and N mark in the UI.
- Cosmic dark theme with saved accent choices.
- Local chat history, chat search, rename/delete, and Enter-to-send.
- Speech-to-text through Android speech recognition; response read-aloud through Android TextToSpeech.
- Provider/model selector for Gemini, OpenAI, Claude and DeepSeek, with editable model IDs and a live model-list fetch from each provider API.
- Provider API keys stored locally with Android Keystore encryption.
- Local LiteRT-LM `.litertlm` model import.

API providers require your own key and may charge for API use. Use “Modelle vom Anbieter laden” to fetch the IDs exposed to your key, then select one in the searchable list. Suggestions are only quick picks; provider model catalogs and access can change. Never put API keys in the repository.

## Google account sync

Google sign-in and cross-device chat synchronization are **not implemented/connected** in this version. They require Firebase/OAuth configuration and security rules. Until configured and tested, chats remain local to the device.

## Build locally

Requirements: Android Studio, JDK 17, Android SDK Platform 36. Open this folder in Android Studio and sync Gradle, or use the installed Gradle 8.14.4 executable:

```powershell
gradle --no-daemon :app:assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub build and releases

- Every push to `main` builds a debug APK and uploads it as a workflow artifact.
- To publish an installable signed APK release, configure the signing secrets in `BUILD-AND-PUBLISH.md`, then push a tag such as `android-v0.3.2`.
- The Android tag namespace is deliberately separate from the Windows app's `v0.3.2` tag.

This repository's CI workflow is designed to build the source. A successful build on CI is still not a substitute for testing the APK on a real phone.

# Build, sign, and publish Android 0.3.2

## Create the GitHub repository

Run `Create-NOVA-Android-Repo.ps1` in PowerShell from this extracted project folder. It checks GitHub CLI authentication, asks you to choose public/private visibility, commits the source and creates/pushes `BrawliYT/NOVA-Android` through `gh repo create`.

## Preserve update compatibility for your current test APK

Android refuses to install an APK update if it has a different signing certificate. For personal testing, the helper `Set-NOVA-Android-Release-Secrets.ps1` can store the current PC's standard Android debug keystore as repository secrets. This is only suitable if the APK already installed on your phone was built on this same Windows user profile using the standard debug keystore at `%USERPROFILE%\.android\debug.keystore` (usual alias/password: `androiddebugkey` / `android`).

The script sends the key to GitHub Secrets via GitHub CLI; it never writes the key into the source tree. Do not commit any `.keystore`, `.jks`, `.p12`, API key or password files.

For a public production release, create a dedicated Android release keystore and store it securely rather than relying on the debug key. Losing a signing key can prevent in-place app updates.

## Set the signing secrets

After the repository exists and `gh auth status` shows the correct GitHub account, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\Set-NOVA-Android-Release-Secrets.ps1
```

Secrets written:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

No secret values should be pasted into chat or committed to Git.

## Publish a release

First push the source to `main`. Open the Actions tab and make sure the debug build succeeds. Then create the matching Android tag:

```powershell
git tag -a android-v0.3.2 -m "NOVA Android 0.3.2"
git push origin android-v0.3.2
```

The tag version must match `versionName` in `app/build.gradle.kts`. The workflow builds `assembleRelease` signed with the configured keystore and creates a GitHub Release containing `app-release.apk`.

If the installed APK was not signed with this same key, Android will reject the update. Do not uninstall the current app until chats/settings have been backed up, because uninstalling removes app-private local data.

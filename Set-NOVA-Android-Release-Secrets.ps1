$ErrorActionPreference = 'Stop'
$Repo = 'BrawliYT/NOVA-Android'
$Keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI (gh) is not installed.' }
& gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Run gh auth login first.' }
& gh repo view $Repo --json name *> $null
if ($LASTEXITCODE -ne 0) { throw "Repository $Repo does not exist yet. Run Create-NOVA-Android-Repo.ps1 first." }
if (-not (Test-Path $Keystore)) {
    throw "Local debug keystore not found at $Keystore. Cannot configure stable signing for the existing app. Do not generate a new key if you need to update the installed APK; that may break in-place upgrades."
}
Write-Warning 'This shares your local Android debug signing key with GitHub Actions as encrypted repository secrets. Use only for personal/test builds. Never commit the keystore into Git.'
$confirm = Read-Host 'If your installed NOVA APK was built using this Windows profile standard debug keystore, type YES to configure update-compatible test signing'
if ($confirm -ne 'YES') { Write-Host 'Cancelled; no secrets were changed.'; exit 0 }

$base64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($Keystore))
$tmp = Join-Path $env:TEMP ('nova-android-keystore-' + [guid]::NewGuid().ToString('N') + '.txt')
try {
    [System.IO.File]::WriteAllText($tmp, $base64, [System.Text.Encoding]::ASCII)
    Get-Content -LiteralPath $tmp -Raw | & gh secret set ANDROID_KEYSTORE_BASE64 --repo $Repo
    if ($LASTEXITCODE -ne 0) { throw 'Could not set ANDROID_KEYSTORE_BASE64 secret.' }
}
finally {
    Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    $base64 = $null
}
& gh secret set ANDROID_KEYSTORE_PASSWORD --repo $Repo --body 'android'
if ($LASTEXITCODE -ne 0) { throw 'Could not set ANDROID_KEYSTORE_PASSWORD secret.' }
& gh secret set ANDROID_KEY_ALIAS --repo $Repo --body 'androiddebugkey'
if ($LASTEXITCODE -ne 0) { throw 'Could not set ANDROID_KEY_ALIAS secret.' }
& gh secret set ANDROID_KEY_PASSWORD --repo $Repo --body 'android'
if ($LASTEXITCODE -ne 0) { throw 'Could not set ANDROID_KEY_PASSWORD secret.' }
Write-Host 'Android signing secrets saved. Values were not printed.' -ForegroundColor Green

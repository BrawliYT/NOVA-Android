$ErrorActionPreference = 'Stop'
# PowerShell 7.3+ can promote native-command stderr/non-zero exits to terminating errors.
# Git uses a non-zero exit code normally for checks such as 'HEAD does not exist yet'.
if ($PSVersionTable.PSVersion -ge [version]'7.3') {
    $PSNativeCommandUseErrorActionPreference = $false
}
$RepoSlug = 'BrawliYT/NOVA-Android'
$RepoUrl = "https://github.com/$RepoSlug.git"
$Root = (Resolve-Path $PSScriptRoot).Path

if (-not (Test-Path (Join-Path $Root 'settings.gradle.kts')) -or -not (Test-Path (Join-Path $Root 'app\build.gradle.kts'))) {
    throw "Run this script from the extracted NOVA Android project root: $Root"
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git is not installed or not available in PATH.' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI is required. Install gh, then run gh auth login.' }
& gh auth status
if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI is not authenticated. Run gh auth login and retry.' }
$login = (& gh api user --jq .login).Trim()
if ($LASTEXITCODE -ne 0 -or $login -ne 'BrawliYT') { throw "Expected GitHub account BrawliYT, but current gh account is '$login'. Run gh auth switch --user BrawliYT if available." }

# Check the owner’s repository list instead of calling `gh repo view` on a
# potentially missing repository. With some PowerShell 7 settings, gh's expected
# 404 stderr is promoted to a terminating NativeCommandError before we can inspect
# $LASTEXITCODE.
$repoNames = @(& gh repo list 'BrawliYT' --limit 1000 --json name --jq '.[].name')
if ($LASTEXITCODE -ne 0) { throw 'Could not list repositories for BrawliYT. Check gh auth and repository permissions.' }
$existing = $repoNames -contains 'NOVA-Android'

$visibility = Read-Host 'Repository visibility: type PUBLIC or PRIVATE (PUBLIC publishes the source code; no API keys belong in source files)'
$visibility = $visibility.Trim().ToUpperInvariant()
if ($visibility -notin @('PUBLIC','PRIVATE')) { throw 'Type exactly PUBLIC or PRIVATE.' }
$confirm = Read-Host "Create/push this Android source to $RepoSlug as $visibility? Type YES to continue"
if ($confirm -ne 'YES') { Write-Host 'Cancelled; no remote repository was created.'; exit 0 }

Push-Location $Root
try {
    if (-not (Test-Path (Join-Path $Root '.git'))) { git init -b main }
    if ($LASTEXITCODE -ne 0) { throw 'git init failed.' }
    $branch = (& git branch --show-current).Trim()
    if ($branch -ne 'main') {
        git branch -M main
        if ($LASTEXITCODE -ne 0) { throw 'Could not set branch to main.' }
    }

    git add -A
    if ($LASTEXITCODE -ne 0) { throw 'git add failed.' }
    git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'Staged files contain whitespace errors.' }
    # An empty repository has no HEAD commit yet. --quiet avoids expected fatal output.
    git rev-parse --verify --quiet HEAD *> $null
    $hasHead = ($LASTEXITCODE -eq 0)
    git diff --cached --quiet
    $stagedChanges = ($LASTEXITCODE -eq 1)
    if (-not $hasHead -or $stagedChanges) {
        git commit -m 'NOVA Android 0.3.2 source and CI workflow'
        if ($LASTEXITCODE -ne 0) { throw 'Initial commit failed.' }
    }

    if (-not $existing) {
        $visFlag = if ($visibility -eq 'PUBLIC') { '--public' } else { '--private' }
        & gh repo create $RepoSlug $visFlag --description 'NOVA Pro Android app source, CI builds and signed APK releases.'
        if ($LASTEXITCODE -ne 0) { throw 'GitHub repository creation failed. Local source is committed; fix gh permissions and rerun this script.' }
    }

    $origin = (& git remote get-url origin 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($origin)) {
        git remote add origin $RepoUrl
        if ($LASTEXITCODE -ne 0) { throw 'Could not add origin remote.' }
    } elseif ($origin -ne $RepoUrl) {
        throw "Origin already points to '$origin'; refusing to change it automatically. Review the remote before continuing."
    }

    git push -u origin main
    if ($LASTEXITCODE -ne 0) { throw 'Push to GitHub failed. The local commit remains; correct authentication/remote and retry.' }
    Write-Host "Android source pushed successfully: https://github.com/$RepoSlug" -ForegroundColor Green
    Write-Host 'Next: run Set-NOVA-Android-Release-Secrets.ps1, wait for the main-branch CI build, then tag android-v0.3.2 to publish an APK release.' -ForegroundColor Cyan
}
finally { Pop-Location }

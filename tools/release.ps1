# Publish a GitHub Release with the APK attached.
#
# Usage (PowerShell, from the project root):
#   .\tools\release.ps1 -Token <your_personal_access_token>
#
# Optional:
#   -Tag v1.16.3        tag name (default: v + versionName from app/build.gradle.kts)
#   -Notes notes.md     release body from a file (default: a generated summary)
#   -IncludeDebug      also attach the debug APK
#
# The token needs "repo" scope. Plain ASCII on purpose: non-ASCII text in a PowerShell script
# breaks when the console code page is not UTF-8.

param(
    [Parameter(Mandatory = $true)]
    [string]$Token,

    [string]$Owner = 'RokishiSensei',
    [string]$Repo = 'roxy-notebook',
    [string]$Tag,
    [string]$Notes,
    [switch]$IncludeDebug
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- read the version
$gradleFile = Join-Path $PSScriptRoot '..\app\build.gradle.kts'
$versionName = (Select-String -Path $gradleFile -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
$versionCode = (Select-String -Path $gradleFile -Pattern 'versionCode\s*=\s*(\d+)').Matches[0].Groups[1].Value
if (-not $versionName) { throw 'Could not read versionName from app/build.gradle.kts' }
if (-not $Tag) { $Tag = "v$versionName" }
Write-Host "==> Version $versionName (versionCode $versionCode), tag $Tag" -ForegroundColor Cyan

# ---------------------------------------------------------------- locate the APKs
$dist = Join-Path $PSScriptRoot '..\dist'
$releaseApk = Join-Path $dist "Roxy-$versionName-release.apk"
if (-not (Test-Path $releaseApk)) { throw "Release APK not found: $releaseApk`nRun: .\gradlew.bat :app:assembleRelease then copy it into dist\." }
$assets = @($releaseApk)
if ($IncludeDebug) {
    $debugApk = Join-Path $dist "Roxy-$versionName-debug.apk"
    if (Test-Path $debugApk) { $assets += $debugApk } else { Write-Host "    (debug APK missing, skipping)" -ForegroundColor Yellow }
}
foreach ($a in $assets) {
    Write-Host ("    {0}  {1:N2} MB" -f (Split-Path $a -Leaf), ((Get-Item $a).Length / 1MB))
}

# ---------------------------------------------------------------- release body
if (-not $Notes) {
    $Notes = @"
## Roxy notebook $versionName

Alphabetically grouped English vocabulary notebook for Android.
Material 3 + Jetpack Compose, all data stored locally, bring-your-own AI endpoint.

**Install:** download ``Roxy-$versionName-release.apk`` below and open it on your phone.
You may need to allow "install unknown apps" for your browser or file manager.

- versionCode $versionCode
- minSdk 26 (Android 8.0), targetSdk 37 (Android 17)
- Signed release build

See [README.md](../blob/main/README.md) for the full feature list and the version history.
"@
}

# ---------------------------------------------------------------- GitHub API
$headers = @{
    Authorization = "token $Token"
    'User-Agent'  = 'roxy-notebook-release'
    Accept        = 'application/vnd.github+json'
}

Write-Host '==> Creating the release' -ForegroundColor Cyan
$bodyObj = @{
    tag_name         = $Tag
    target_commitish = 'main'
    name             = "Roxy notebook $versionName"
    body             = $Notes
    draft            = $false
    prerelease       = $false
}
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Owner/$Repo/releases" `
    -Headers $headers -Method Post -Body ($bodyObj | ConvertTo-Json) -ContentType 'application/json'
Write-Host "    $($release.html_url)"

Write-Host '==> Uploading assets' -ForegroundColor Cyan
$uploadBase = ($release.upload_url -replace '\{.*$', '')
foreach ($file in $assets) {
    $name = Split-Path $file -Leaf
    $uri = '{0}?name={1}' -f $uploadBase, [uri]::EscapeDataString($name)
    $uploadHeaders = $headers.Clone()
    $uploadHeaders['Content-Type'] = 'application/vnd.android.package-archive'
    Invoke-RestMethod -Uri $uri -Headers $uploadHeaders -Method Post -InFile $file | Out-Null
    Write-Host "    uploaded $name"
}

Write-Host ''
Write-Host "Done: $($release.html_url)" -ForegroundColor Green

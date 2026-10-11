# Publish this project to GitHub.
#
# Usage (PowerShell, from the project root):
#   .\tools\publish.ps1 -Token <your_personal_access_token>
#
# The token needs "repo" scope:
#   GitHub -> Settings -> Developer settings -> Personal access tokens -> Tokens (classic)
#   -> Generate new token (classic) -> tick "repo" -> copy the token
#
# The script pushes local "main" to <Owner>/<Repo>, creating the repository (public) if missing.
# It is plain ASCII on purpose: a PowerShell script containing non-ASCII text breaks when the
# console code page is not UTF-8.

param(
    [Parameter(Mandatory = $true)]
    [string]$Token,

    [string]$Owner = 'RokishiSensei',
    [string]$Repo = 'roxy-notebook',
    [switch]$Private
)

$ErrorActionPreference = 'Stop'

$git = 'C:\Program Files\Git\cmd\git.exe'
if (-not (Test-Path $git)) { $git = 'git' }

Write-Host '==> Checking local repository' -ForegroundColor Cyan
& $git rev-parse --is-inside-work-tree | Out-Null
$branch = (& $git branch --show-current).Trim()
if ($branch -ne 'main') {
    throw "Current branch is '$branch'; this script only pushes main."
}
$commit = (& $git rev-parse --short HEAD).Trim()
Write-Host "    branch main, commit $commit"

Write-Host '==> Checking whether the remote repository exists' -ForegroundColor Cyan
$headers = @{
    Authorization = "token $Token"
    'User-Agent'  = 'roxy-notebook-publish'
    Accept        = 'application/vnd.github+json'
}
$api = "https://api.github.com/repos/$Owner/$Repo"
$exists = $true
try {
    Invoke-RestMethod -Uri $api -Headers $headers -Method Get | Out-Null
    Write-Host "    exists: $Owner/$Repo"
}
catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        $exists = $false
    }
    else {
        throw
    }
}

if (-not $exists) {
    Write-Host "==> Creating repository $Owner/$Repo" -ForegroundColor Cyan
    $body = @{
        name        = $Repo
        description = 'Alphabetically grouped English vocabulary notebook for Android (Material 3 + Compose, bring-your-own AI endpoint)'
        private     = [bool]$Private
        has_issues  = $true
        has_wiki    = $false
    } | ConvertTo-Json
    Invoke-RestMethod -Uri 'https://api.github.com/user/repos' -Headers $headers -Method Post -Body $body | Out-Null
    Write-Host '    created'
}

Write-Host '==> Pushing' -ForegroundColor Cyan
# https://<owner>:<token>@github.com/<owner>/<repo>.git -- built with -f to avoid escaping ':'.
$remote = 'https://{0}:{1}@github.com/{2}/{3}.git' -f $Owner, $Token, $Owner, $Repo
& $git remote remove origin 2>$null
& $git remote add origin $remote
& $git push -u origin main
# Drop the token from .git/config as soon as the push succeeded.
& $git remote set-url origin "https://github.com/$Owner/$Repo.git"

Write-Host ''
Write-Host "Done. Repository: https://github.com/$Owner/$Repo" -ForegroundColor Green
Write-Host ''
Write-Host 'Suggested next steps:' -ForegroundColor Yellow
Write-Host '  1. Create a Release and attach dist\Roxy-1.16.3-release.apk'
Write-Host '  2. Fill in the About box + topics: android kotlin jetpack-compose vocabulary material3'
Write-Host '  3. Make sure the CI workflow (.github/workflows/android.yml) turns green'

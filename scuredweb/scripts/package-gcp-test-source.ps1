param(
    [string]$OutputPath = "dist/scuredweb-gcp-test-source.zip"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputPath))
$outputDirectory = Split-Path -Parent $resolvedOutput
$stagingRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("scuredweb-gcp-source-" + $PID)

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
if (Test-Path -LiteralPath $resolvedOutput) {
    Remove-Item -LiteralPath $resolvedOutput -Force
}

try {
    New-Item -ItemType Directory -Path $stagingRoot | Out-Null
    foreach ($item in @("pom.xml", "src", "deploy", "scripts", "docs", "CHANGELOG-GCP-TEST.md", "VERIFICATION-GCP-TEST.md")) {
        $source = Join-Path $projectRoot $item
        if (Test-Path -LiteralPath $source) {
            Copy-Item -LiteralPath $source -Destination $stagingRoot -Recurse
        }
    }

    foreach ($excluded in @(
        "src/main/resources/sql/scuredweb-seed.sql",
        "src/main/resources/sql/create-users-3-6.sql",
        "deploy/gcp-test/results"
    )) {
        $path = Join-Path $stagingRoot $excluded
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
    }

    $jar = (Get-Command jar -ErrorAction Stop).Source
    & $jar --create --file $resolvedOutput --no-manifest -C $stagingRoot .
    if ($LASTEXITCODE -ne 0) {
        throw "jar failed with exit code $LASTEXITCODE"
    }
    try {
        & (Join-Path $PSScriptRoot "verify-packaged-artifacts.ps1") `
            -ArtifactPath $resolvedOutput `
            -ProjectRoot $projectRoot
    } catch {
        Remove-Item -LiteralPath $resolvedOutput -Force -ErrorAction SilentlyContinue
        throw
    }
} finally {
    if (Test-Path -LiteralPath $stagingRoot) {
        $resolvedStaging = (Resolve-Path -LiteralPath $stagingRoot).Path
        $resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\')
        if ($resolvedStaging.StartsWith($resolvedTemp + '\')) {
            Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
        }
    }
}

Write-Output $resolvedOutput

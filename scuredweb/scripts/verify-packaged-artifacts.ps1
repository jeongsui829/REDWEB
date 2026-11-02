param(
    [Parameter(Mandatory = $true)]
    [string[]]$ArtifactPath,
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [int]$ExpectedWebappFileCount = 43
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-NormalizedEntryName {
    param([string]$Name)

    $normalized = $Name.Replace('\', '/')
    while ($normalized.StartsWith('./')) {
        $normalized = $normalized.Substring(2)
    }
    return $normalized.TrimStart('/')
}

function Test-TextEntry {
    param([string]$Name)

    $extension = [System.IO.Path]::GetExtension($Name).ToLowerInvariant()
    return $extension -in @(
        '.bat', '.cmd', '.conf', '.config', '.css', '.env', '.example', '.html',
        '.java', '.js', '.json', '.jsp', '.md', '.properties', '.ps1', '.service',
        '.sh', '.sql', '.txt', '.xml', '.yaml', '.yml'
    )
}

function Get-EntryText {
    param([System.IO.Compression.ZipArchiveEntry]$Entry)

    if ($Entry.Length -gt 4MB -or -not (Test-TextEntry $Entry.FullName)) {
        return $null
    }
    $reader = [System.IO.StreamReader]::new($Entry.Open())
    try {
        return $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
    }
}

function Test-NonEmptyValue {
    param([string]$Value)

    if ($null -eq $Value) {
        return $false
    }
    $normalized = $Value.Trim().Trim('"').Trim("'")
    return -not [string]::IsNullOrWhiteSpace($normalized)
}

function Get-Sha256FromStream {
    param([System.IO.Stream]$Stream)

    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($algorithm.ComputeHash($Stream))).Replace('-', '')
    } finally {
        $algorithm.Dispose()
    }
}

$violations = [System.Collections.Generic.List[string]]::new()
$resolvedArtifacts = @($ArtifactPath | ForEach-Object {
    $candidate = if ([System.IO.Path]::IsPathRooted($_)) {
        $_
    } else {
        Join-Path (Get-Location) $_
    }
    $resolved = [System.IO.Path]::GetFullPath($candidate)
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "Artifact does not exist: $resolved"
    }
    $resolved
})
$warArchive = $null
$warPath = $null

foreach ($resolvedArtifact in $resolvedArtifacts) {
    $artifactName = [System.IO.Path]::GetFileName($resolvedArtifact)
    $isWar = $artifactName.EndsWith('.war', [System.StringComparison]::OrdinalIgnoreCase)
    $isGcpSource = $artifactName -eq 'scuredweb-gcp-test-source.zip'
    $isFullSource = $artifactName -eq 'scuredweb-current-full-source.zip'
    if (-not ($isWar -or $isGcpSource -or $isFullSource)) {
        throw "Unsupported artifact name: $artifactName"
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedArtifact)
    if ($isWar) {
        $warArchive = $archive
        $warPath = $resolvedArtifact
    }
    try {
        foreach ($entry in $archive.Entries) {
            $entryName = Get-NormalizedEntryName $entry.FullName
            if ([string]::IsNullOrWhiteSpace($entryName) -or $entryName.EndsWith('/')) {
                continue
            }

            if (-not $isWar) {
                if ($entryName -match '(^|/)(target|dist|\.git)(/|$)') {
                    $violations.Add("${artifactName}:forbidden-build-or-vcs-path")
                }
                if ($entryName -match '^deploy/gcp-test/results(/|$)') {
                    $violations.Add("${artifactName}:forbidden-verification-results")
                }
                if ($entryName -match '(?i)(^|/)(final-smoke-tomcat|tomcat-runtime|previous-scuredweb-runtime|apache-tomcat[^/]*)(/|$)') {
                    $violations.Add("${artifactName}:forbidden-local-tomcat-runtime")
                }
                if ($entryName -match '(?i)\.war$') {
                    $violations.Add("${artifactName}:forbidden-previous-deployment")
                }
                if ($entryName -match '(?i)\.log$|(^|/)(logs?|screenshots?|playwright-report)(/|$)|screenshot[^/]*\.(png|jpg|jpeg|webp)$') {
                    $violations.Add("${artifactName}:forbidden-local-log-or-screenshot")
                }
            }
            if ($isGcpSource -and $entryName -match '(?i)(scuredweb-seed|create-users-3-6)\.sql$') {
                $violations.Add("${artifactName}:forbidden-development-seed")
            }
            if ($isWar -and $entryName -match '(?i)(^|/)(servlet-api|jakarta\.servlet-api|ojdbc|oraclepki|osdt).*\.jar$') {
                $violations.Add("${artifactName}:forbidden-runtime-library")
            }
            if ($isWar -and $entryName -match '(?i)(scuredweb-seed|create-users-3-6)\.sql$') {
                $violations.Add("${artifactName}:forbidden-development-seed")
            }

            $text = Get-EntryText $entry
            if ($null -eq $text) {
                continue
            }
            foreach ($line in ($text -split "`r?`n")) {
                $trimmed = $line.Trim()
                if ($trimmed.StartsWith('#') -or $trimmed.StartsWith('//')) {
                    continue
                }
                if ($trimmed -match '^(?:export\s+)?db\.password\s*=\s*(.*)$' -and
                        (Test-NonEmptyValue $matches[1])) {
                    $violations.Add("${artifactName}:non-empty-db-password-assignment")
                }
                if ($trimmed -match '^(?:export\s+)?(?:\$env:)?REDWEB_DB_PASSWORD\s*=\s*(.*)$' -and
                        (Test-NonEmptyValue $matches[1])) {
                    $violations.Add("${artifactName}:non-empty-redweb-password-assignment")
                }
                if ($trimmed -match 'REDWEB_DB_PASSWORD\s*=\s*([^\s"'']+)' -and
                        (Test-NonEmptyValue $matches[1])) {
                    $violations.Add("${artifactName}:non-empty-redweb-password-assignment")
                }
                if ($trimmed -match '-Dredweb\.db\.password\s*=\s*([^\s"'']+)' -and
                        (Test-NonEmptyValue $matches[1])) {
                    $violations.Add("${artifactName}:non-empty-db-password-assignment")
                }
            }

            foreach ($resourceMatch in [regex]::Matches($text, '(?is)<Resource\b[^>]*>')) {
                $passwordMatch = [regex]::Match($resourceMatch.Value, '(?i)\bpassword\s*=\s*["'']([^"'']*)["'']')
                if ($passwordMatch.Success -and
                        (Test-NonEmptyValue $passwordMatch.Groups[1].Value) -and
                        $passwordMatch.Groups[1].Value.Trim() -ne 'CHANGE_ME') {
                    $violations.Add("${artifactName}:non-placeholder-jndi-password")
                }
            }

            $runtimeResultPath = $entryName -match '(?i)(^|/)(target|runtime|final-smoke-tomcat|previous-scuredweb-runtime)(/|$)' -or
                $entryName -match '^deploy/gcp-test/results(/|$)'
            if ($runtimeResultPath -and $text -match '(?i)jdbc:oracle') {
                $violations.Add("${artifactName}:oracle-url-in-runtime-result")
            }
        }
    } finally {
        if (-not $isWar) {
            $archive.Dispose()
        }
    }
}

if ($null -ne $warArchive) {
    try {
        $webappRoot = Join-Path ([System.IO.Path]::GetFullPath($ProjectRoot)) 'src\main\webapp'
        $sourceFiles = @(Get-ChildItem -LiteralPath $webappRoot -Recurse -File)
        if ($sourceFiles.Count -ne $ExpectedWebappFileCount) {
            $violations.Add("$(Split-Path -Leaf $warPath):unexpected-webapp-file-count")
        }
        foreach ($sourceFile in $sourceFiles) {
            $relativePath = $sourceFile.FullName.Substring($webappRoot.Length + 1).Replace('\', '/')
            $entry = $warArchive.Entries | Where-Object { $_.FullName -eq $relativePath } | Select-Object -First 1
            if ($null -eq $entry) {
                $violations.Add("$(Split-Path -Leaf $warPath):missing-webapp-entry")
                continue
            }
            $sourceStream = [System.IO.File]::OpenRead($sourceFile.FullName)
            $entryStream = $entry.Open()
            try {
                $sourceHash = Get-Sha256FromStream $sourceStream
                $entryHash = Get-Sha256FromStream $entryStream
            } finally {
                $sourceStream.Dispose()
                $entryStream.Dispose()
            }
            if ($sourceHash -ne $entryHash) {
                $violations.Add("$(Split-Path -Leaf $warPath):webapp-content-mismatch")
            }
        }
    } finally {
        $warArchive.Dispose()
    }
}

if ($violations.Count -gt 0) {
    $summary = @($violations | Sort-Object -Unique) -join ', '
    throw "Artifact verification failed without displaying credential values: $summary"
}

Write-Output "ARTIFACT_VERIFICATION_PASSED=$($resolvedArtifacts.Count)"
Write-Output "WEBAPP_EXPECTED_COUNT=$ExpectedWebappFileCount"

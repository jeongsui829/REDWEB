$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $workspace
try {
    & mvn -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
    $packageDir = Join-Path $workspace ('dist/production-oci-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $packageDir | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $packageDir 'conf'),(Join-Path $packageDir 'sql') | Out-Null
    Copy-Item -LiteralPath 'target/scuredweb.war' -Destination $packageDir
    foreach ($name in @('nginx-redweb.conf','scuredweb.xml','remote-ip-valve.xml','wait-oracle-free.sh','redweb-runtime.env.example','oracle-free.service','oracle-free-ready.service','tomcat.service','check-oracle-container.sh','check-redweb-runtime.sh','nginx-mount.conf','connector.xml.example','journald-redweb.conf','logrotate-redweb')) {
        Copy-Item -LiteralPath (Join-Path 'deploy/oci/conf' $name) -Destination (Join-Path $packageDir 'conf')
    }
    foreach ($name in @('20260928-auth-version.sql','verify-legacy-passwords.sql','runtime-grants-DRAFT.sql')) {
        Copy-Item -LiteralPath (Join-Path 'deploy/oci/sql' $name) -Destination (Join-Path $packageDir 'sql')
    }
    Copy-Item -LiteralPath 'deploy/oci/README.md','docs/OCI-FOLLOWUPS.md' -Destination $packageDir
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $packageDir 'scuredweb.war'))
    try {
        $forbidden = @($archive.Entries | Where-Object { $_.FullName -match '(?i)WEB-INF/lib/(ojdbc|h2-|junit|mockito|jakarta.servlet-api)|(^|/)(src/test|test-classes|test-resources)/|\.(zip|key|pem)$' })
        if ($forbidden.Count) { throw 'Forbidden entries in WAR' }
    } finally { $archive.Dispose() }
    & python scripts/scan-deployment-secrets.py $packageDir
    if ($LASTEXITCODE -ne 0) { throw 'Package requires secret review' }
    $packageFiles = @(Get-ChildItem -LiteralPath $packageDir -Recurse -File)
    $packageFiles | Get-FileHash -Algorithm SHA256 |
        Select-Object Hash,@{Name='File';Expression={$_.Path.Substring($packageDir.Length + 1)}} |
        Export-Csv -NoTypeInformation -Path (Join-Path $packageDir 'SHA256.csv')
    Write-Output $packageDir
} finally { Pop-Location }

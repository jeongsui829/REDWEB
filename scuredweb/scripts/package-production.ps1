$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $workspace
try {
    & mvn -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
    $packageDir = Join-Path $workspace ('dist/production-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $packageDir -ErrorAction Stop | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $packageDir 'conf'),(Join-Path $packageDir 'sql') | Out-Null
    Copy-Item -LiteralPath 'target/scuredweb.war' -Destination $packageDir
    # Explicit allowlist; never copy local runtime env or secrets.
    foreach ($name in @('nginx-redweb.conf','tomcat.service','oracle-free-ready.service','wait-oracle-free.sh','remote-ip-valve.xml','scuredweb.xml','redweb-https.env.example','persistent-volumes.conf.example','oracle-volume.conf.example')) {
        Copy-Item -LiteralPath (Join-Path 'deploy/gcp-test/conf' $name) -Destination (Join-Path $packageDir 'conf')
    }
    foreach ($name in @('predeploy-indexes.sql','verify-legacy-passwords.sql')) {
        Copy-Item -LiteralPath (Join-Path 'src/main/resources/sql' $name) -Destination (Join-Path $packageDir 'sql')
    }
    Copy-Item -LiteralPath 'src/main/resources/sql/migrations/20260928-auth-version.sql' -Destination (Join-Path $packageDir 'sql')
    & python scripts/scan-deployment-secrets.py $packageDir
    if ($LASTEXITCODE -ne 0) { throw 'Package requires secret review; do not deploy' }
    $packageFiles = @(Get-ChildItem -LiteralPath $packageDir -Recurse -File)
    $packageFiles | Get-FileHash -Algorithm SHA256 |
        Select-Object Hash,@{Name='File';Expression={$_.Path.Substring($packageDir.Length + 1)}} |
        Export-Csv -NoTypeInformation -Path (Join-Path $packageDir 'SHA256.csv')
    Write-Output $packageDir
} finally { Pop-Location }

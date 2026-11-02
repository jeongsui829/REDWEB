$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot "target/online-user-tracker-verification"

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

& javac -encoding UTF-8 -d $outputDirectory `
    (Join-Path $projectRoot "src/main/java/com/scuredweb/user/dto/UserDTO.java") `
    (Join-Path $projectRoot "src/main/java/com/scuredweb/common/util/OnlineUserTracker.java") `
    (Join-Path $projectRoot "scripts/verification/OnlineUserTrackerVerifier.java")
if ($LASTEXITCODE -ne 0) {
    throw "OnlineUserTracker verifier compilation failed with exit code $LASTEXITCODE"
}

& java -cp $outputDirectory com.scuredweb.common.util.OnlineUserTrackerVerifier
if ($LASTEXITCODE -ne 0) {
    throw "OnlineUserTracker verification failed with exit code $LASTEXITCODE"
}

$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot

Write-Host "[1/3] Maven clean package 실행 중..." -ForegroundColor Cyan
mvn clean package -DskipTests

Write-Host "[2/3] 빌드 완료" -ForegroundColor Green
Write-Host "WAR 경로: $PSScriptRoot\target\scuredweb.war" -ForegroundColor Yellow
Write-Host "[3/3] Eclipse에서 Tomcat Clean 후 다시 시작하면 반영됩니다." -ForegroundColor Cyan

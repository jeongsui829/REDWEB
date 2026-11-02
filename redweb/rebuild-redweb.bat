@echo off
cd /d "%~dp0"
call mvn clean package -DskipTests
pause

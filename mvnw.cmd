@echo off
setlocal
set "BASE_DIR=%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%BASE_DIR%.mvn\wrapper\bootstrap-maven.ps1" %*
exit /b %ERRORLEVEL%

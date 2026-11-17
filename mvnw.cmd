@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "BASE_DIR=%~dp0"
cd /d "%BASE_DIR%"
set "WRAPPER_DIR=%BASE_DIR%.mvn\wrapper"
set "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
set "PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties"

if not exist "%PROPERTIES%" (
  echo Missing Maven wrapper properties: %PROPERTIES% 1>&2
  exit /b 1
)
if not exist "%WRAPPER_JAR%" (
  for /f "tokens=1,* delims==" %%A in ('findstr /b "wrapperUrl=" "%PROPERTIES%"') do set "WRAPPER_URL=%%B"
  if not defined WRAPPER_URL (
    echo Missing wrapperUrl in %PROPERTIES% 1>&2
    exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri '!WRAPPER_URL!' -OutFile '%WRAPPER_JAR%.tmp'; Move-Item -Force '%WRAPPER_JAR%.tmp' '%WRAPPER_JAR%'"
  if errorlevel 1 exit /b 1
)
if defined JAVA_HOME (
  set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_CMD=java.exe"
)
"%JAVA_CMD%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%

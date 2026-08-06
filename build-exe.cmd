@echo off
setlocal
pushd "%~dp0"

set "APP_NAME=scumRoadTo855"
set "MAIN_JAR=fx17.jar"
set "JDKHOME=C:\JDK\jdk17"
set "JAVAFX_HOME=H:\1\javafx-jmods-17.0.16"
set "DIST_DIR=target\dist"
set "RUNTIME_DIR=target\runtime"

if not exist "%JDKHOME%\bin\jlink.exe" (
  echo [ERROR] JDKHOME is invalid: %JDKHOME%
  exit /b 1
)

if not exist "%JDKHOME%\bin\jpackage.exe" (
  echo [ERROR] jpackage not found under JDKHOME: %JDKHOME%
  exit /b 1
)

if not exist "%JAVAFX_HOME%" (
  echo [ERROR] JAVAFX_HOME is invalid: %JAVAFX_HOME%
  exit /b 1
)

echo === maven package ===
if exist "%~dp0mvnw.cmd" (
  call "%~dp0mvnw.cmd" clean package
) else (
  where mvn >nul 2>nul
  if not errorlevel 1 (
    call mvn clean package
  ) else (
    echo [WARN] Maven not found. Reusing existing %DIST_DIR%\%MAIN_JAR%.
  )
)

if not exist "%DIST_DIR%\%MAIN_JAR%" (
  echo [ERROR] %DIST_DIR%\%MAIN_JAR% not found
  exit /b 1
)

echo === clean ===
rmdir /s /q "%RUNTIME_DIR%" 2>nul
rmdir /s /q "target\%APP_NAME%" 2>nul

echo === jlink ===
"%JDKHOME%\bin\jlink" ^
  --module-path "%JDKHOME%\jmods;%JAVAFX_HOME%" ^
  --add-modules java.base,java.desktop,java.logging,javafx.controls,javafx.fxml ^
  --output "%RUNTIME_DIR%"

if not exist "%RUNTIME_DIR%" (
  echo [ERROR] jlink failed, runtime not generated
  exit /b 1
)

echo === copy native dll ===
if exist "%USERPROFILE%\.m2\repository\com\github\kwhat\jnativehook\2.2.2\JNativeHook.dll" (
  copy /y "%USERPROFILE%\.m2\repository\com\github\kwhat\jnativehook\2.2.2\JNativeHook.dll" "%RUNTIME_DIR%\bin\"
) else if exist "src\main\resources\JNativeHook.dll" (
  copy /y "src\main\resources\JNativeHook.dll" "%RUNTIME_DIR%\bin\"
) else (
  echo [WARN] JNativeHook.dll not found. Global keyboard hook may fail at runtime.
)

echo === jpackage ===
"%JDKHOME%\bin\jpackage" ^
  --name "%APP_NAME%" ^
  --input "%DIST_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --runtime-image "%RUNTIME_DIR%" ^
  --type app-image ^
  --dest target

popd
endlocal

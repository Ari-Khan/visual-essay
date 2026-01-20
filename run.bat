@echo off
setlocal enabledelayedexpansion

REM Writer's Block - Visual Essay
REM Portable launcher that uses the java folder on the USB

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"

REM Set Java to use the portable Java folder on this USB
set "JAVA_HOME=%SCRIPT_DIR%java"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

REM Change to script directory
cd /d "%SCRIPT_DIR%"

REM Check if portable Java exists
if not exist "%JAVA_EXE%" (
    echo Error: Java folder not found on USB.
    echo Please ensure the 'java' folder is in the same directory as this script.
    pause
    exit /b 1
)

REM Run the JAR file
echo Starting Writer's Block...
"%JAVA_EXE%" -jar "%SCRIPT_DIR%visual-essay.jar"
pause

REM Run the application with the portable Java
"%JAVA_HOME%\bin\java.exe" -cp visual-essay.jar Main
pause

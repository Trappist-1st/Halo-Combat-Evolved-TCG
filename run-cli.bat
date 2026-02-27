@echo off
echo ========================================
echo Halo CE TCG - CLI Interface
echo ========================================
echo.

cd /d "%~dp0"

REM Check if Maven is available
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven not found in PATH!
    echo Please install Maven and add it to your PATH environment variable.
    pause
    exit /b 1
)

echo [INFO] Compiling project...
call mvn clean compile
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)

echo.
echo [INFO] Starting Halo CE TCG CLI...
echo.
echo ========================================
echo Type 'help' for available commands
echo Type 'init' to start a new game
echo Type 'exit' to quit
echo ========================================
echo.

call mvn exec:java -Dexec.mainClass="com.haloce.tcg.cli.GameCLI"

pause

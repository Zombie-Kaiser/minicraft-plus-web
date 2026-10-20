@echo off
rem Minicraft+ web port - start the dev server and open the game in the browser.
setlocal
cd /d "%~dp0"

set PORT=%1
if "%PORT%"=="" set PORT=8137

where python >nul 2>nul
if %errorlevel%==0 (
    set "PY=python"
) else (
    where py >nul 2>nul
    if %errorlevel%==0 (
        set "PY=py -3"
    ) else (
        echo Python 3 not found. Install it from https://www.python.org/
        pause
        exit /b 1
    )
)

if not exist "web\game.js" (
    echo web\game.js not found - building with Gradle ^(needs JDK 21^)...
    call gradlew.bat :client:teavmJs --console=plain
    if errorlevel 1 (
        echo Build failed.
        pause
        exit /b 1
    )
)

start "minicraft-server" /min cmd /c "%PY% web\serve.py %PORT%"
timeout /t 1 /nobreak >nul
start "" "http://127.0.0.1:%PORT%/"

echo Minicraft web server running at http://127.0.0.1:%PORT%/
echo (minimized "minicraft-server" window - close it to stop the server)
pause

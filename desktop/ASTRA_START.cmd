@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if not errorlevel 1 (
    py -3 -E -s astra_launcher.py %*
    exit /b %errorlevel%
)
where python >nul 2>nul
if errorlevel 1 (
    echo Python 3.10 or later with Tkinter is required.
    pause
    exit /b 1
)
python -E -s astra_launcher.py %*
exit /b %errorlevel%

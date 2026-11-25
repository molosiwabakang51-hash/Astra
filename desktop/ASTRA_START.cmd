@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if not errorlevel 1 goto use_py
where python >nul 2>nul
if not errorlevel 1 goto use_python
echo Python 3.10 or later with Tkinter is required.
pause
exit /b 1
:use_py
py -3 -E -s astra_launcher.py
exit /b %errorlevel%
:use_python
python -E -s astra_launcher.py
exit /b %errorlevel%

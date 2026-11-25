@echo off
powershell.exe -NoProfile -File "%~dp0launch-isolated.ps1"
if errorlevel 1 pause

@echo off
powershell.exe -NoProfile -File "%~dp0configure-exe.ps1" -Mode Restore
pause

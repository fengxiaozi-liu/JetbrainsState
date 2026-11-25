@echo off
powershell.exe -NoProfile -File "%~dp0launch-isolated.ps1" -ProjectPath "%~dp0smoke-project"
if errorlevel 1 pause

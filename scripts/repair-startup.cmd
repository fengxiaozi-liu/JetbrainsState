@echo off
powershell.exe -NoProfile -File "%~dp0configure-exe.ps1" -Mode Install
if errorlevel 1 (
  echo Repair stopped. Keep the error output and check agent-startup-check.log.
) else (
  echo Startup configuration checked and repaired. Reopen the original GoLand EXE.
)
pause

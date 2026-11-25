@echo off
python "%~dp0verify-toolchain.py"
if errorlevel 1 pause

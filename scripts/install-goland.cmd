@echo off
call "%~dp0run-config.cmd" -Product GoLand -Mode Install
exit /b %ERRORLEVEL%

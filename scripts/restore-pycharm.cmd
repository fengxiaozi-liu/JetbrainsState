@echo off
call "%~dp0run-config.cmd" -Product PyCharm -Mode Restore
exit /b %ERRORLEVEL%

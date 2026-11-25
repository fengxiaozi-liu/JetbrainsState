@echo off
call "%~dp0run-config.cmd" -Product PyCharm -Mode Install
exit /b %ERRORLEVEL%

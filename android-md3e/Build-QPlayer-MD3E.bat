@echo off
chcp 65001 >nul
setlocal
title QPlayer MD3E Build and Install (Physical Device Only)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-QPlayer-MD3E.ps1" %*
echo.
echo Process finished. This window will stay open.
pause

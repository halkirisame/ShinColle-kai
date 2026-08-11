@echo off
rem Double-click to launch the dev client (深これ re:fork + Jade + Forge Config Screens).
rem The window stays open only if something fails, so the error is readable.
cd /d "%~dp0"
call gradlew.bat runClient
if errorlevel 1 (
    echo.
    echo ---- launch failed; see the messages above ----
    pause
)

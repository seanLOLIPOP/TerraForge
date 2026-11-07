@echo off
setlocal
cd /d "%~dp0"
"%~dp0runtime\bin\java.exe" -XX:MaxRAMPercentage=70 "-Dterraforge.home=%~dp0." -jar "%~dp0app\TerraForge.jar" %*
if errorlevel 1 (
  echo.
  echo TerraForge stopped with an error. Copy the message above when reporting it.
  pause
)

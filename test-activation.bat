@echo off
chcp 65001 >nul
echo ========================================
echo   Testing License Activation
echo ========================================
echo.

set LICENSE_KEY=jjjP0CaWugjMH2TTNb1V8oHaKPkOmMZt
set DEVICE_ID=E56F09A72D4507727218BC5117B2AFE580874795CA101129433F10A93189B8

echo 📱 Testing activation with:
echo License: %LICENSE_KEY%
echo Device: %DEVICE_ID%
echo.

curl -X POST https://hotapp.lastofanarchy.workers.dev/activate ^
  -H "Content-Type: application/json" ^
  -d "{\"license_key\":\"%LICENSE_KEY%\",\"device_id\":\"%DEVICE_ID%\",\"device_info\":\"Test Device\"}"

echo.
echo.
echo ========================================
pause

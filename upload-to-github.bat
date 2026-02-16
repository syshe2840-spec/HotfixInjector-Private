@echo off
chcp 65001 >nul
echo ========================================
echo   GitHub Upload Script
echo ========================================
echo.

REM Check if git is installed
git --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Git is not installed!
    echo Please install Git from: https://git-scm.com/download/win
    pause
    exit /b 1
)

echo ✅ Git detected
echo.

REM Get GitHub token
set /p GITHUB_TOKEN="Enter your GitHub Personal Access Token: "
if "%GITHUB_TOKEN%"=="" (
    echo ❌ Token cannot be empty!
    pause
    exit /b 1
)

echo.
echo 📦 Repository: https://github.com/syshe2840-spec/HotfixInjector-Private
echo.

REM Confirm
set /p CONFIRM="Continue with upload? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo ❌ Upload cancelled
    pause
    exit /b 1
)

echo.
echo ========================================
echo   Starting Upload...
echo ========================================
echo.

REM Initialize git if needed
if not exist .git (
    echo [1/5] Initializing Git repository...
    git init
    if %errorlevel% neq 0 goto :error
    echo ✅ Git initialized
) else (
    echo [1/5] Git repository already exists
)

echo.
echo [2/5] Adding files...
git add .
if %errorlevel% neq 0 goto :error
echo ✅ Files added

echo.
echo [3/5] Creating commit...
git commit -m "Initial commit - HotFix Injector with License Protection"
if %errorlevel% neq 0 (
    echo ⚠️ Nothing to commit or already committed
)

echo.
echo [4/5] Setting up remote...
git remote remove origin 2>nul
git remote add origin https://%GITHUB_TOKEN%@github.com/syshe2840-spec/HotfixInjector-Private.git
if %errorlevel% neq 0 goto :error
echo ✅ Remote configured

echo.
echo [5/5] Pushing to GitHub...
git branch -M main
git push -u origin main --force
if %errorlevel% neq 0 goto :error

echo.
echo ========================================
echo   ✅ Upload Successful!
echo ========================================
echo.
echo 🎉 Your code is now on GitHub!
echo 📍 Repository: https://github.com/syshe2840-spec/HotfixInjector-Private
echo.
echo Next steps:
echo 1. Go to: https://github.com/syshe2840-spec/HotfixInjector-Private/actions
echo 2. Wait for build to complete (5-10 minutes)
echo 3. Download APK from Artifacts
echo.
pause
exit /b 0

:error
echo.
echo ========================================
echo   ❌ Error occurred!
echo ========================================
echo.
echo Please check:
echo - GitHub token is correct
echo - Repository exists: https://github.com/syshe2840-spec/HotfixInjector-Private
echo - Repository is Private
echo - You have write access
echo.
pause
exit /b 1

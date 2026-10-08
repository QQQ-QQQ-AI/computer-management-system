@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "JAR=target\cafe-management-system-1.0.0.jar"

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

echo ============================================
echo   Cafe Management System - starting
echo ============================================
echo.

if not exist "%JAR%" (
    echo [ERROR] jar not found: %JAR%
    echo Please run:  mvn package -DskipTests
    echo.
    pause
    exit /b 1
)

echo Using java : %JAVA%
echo Using jar  : %JAR%
echo Port       : 8080  ^(passed as command-line arg^)
echo.

rem 端口用命令行参数显式指定：命令行参数优先级最高，
rem 不会被环境变量或配置文件意外覆盖。
"%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --server.port=8080

echo.
echo ============================================
echo   Application stopped.
echo ============================================
pause

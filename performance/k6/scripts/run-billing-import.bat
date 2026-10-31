@echo off
rem Usage: scripts\run-billing-import.bat [VUS] [ROWS] [TENANT]
rem   smoke : scripts\run-billing-import.bat 1 5
rem   full  : scripts\run-billing-import.bat 20 1000          (defaults, qafeature)
rem   tenant2: scripts\run-billing-import.bat 1 5 qa-erp-mt-01
cd /d "%~dp0.."
set VUS=%1
if "%VUS%"=="" set VUS=20
set ROWS=%2
if "%ROWS%"=="" set ROWS=1000
set TENANT=%3
if "%TENANT%"=="" set TENANT=qafeature
if not exist reports\%TENANT% mkdir reports\%TENANT%
rem users: the PERF users of UsersList.xlsx, exported by Java first:  mvn -o test -Dtest=K6UsersExportTest -Dk6.users.write=true -Denv=<env>
k6 run -e TENANT=%TENANT% -e USERS_FILE=data/generated/users-%TENANT%.json -e VUS=%VUS% -e ROWS=%ROWS% --console-output=reports\%TENANT%\billing-import.log scenarios\backfill\importEcommerceBilling.js

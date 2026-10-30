@echo off
rem Runs the Billing-by-Brand import on qafeature (t=0) and qa-erp-mt-01 (t=+60s), concurrently.
rem Per-tenant settings (VUS, ROWS, startDelaySeconds) live in erp\config\multiTenantRun.billingImport.json.
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-multi-tenant.ps1 -ConfigPath erp\config\multiTenantRun.billingImport.json

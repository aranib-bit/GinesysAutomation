# performance/

Everything k6 lives under `performance/k6/` (bulk data entry + performance testing), merged into this project from the former
standalone `D:\PerformanceAutomationTesting` folder (which was not a git repository, so there is no history to carry over).

```
performance/k6/
├── erp/config/            tenant files (environments/<tenant>.json) and multi-tenant run definitions - the ERP application's config
├── flows/                 reusable API flows (one per business action), shared by both scenario families
├── lib/                   auth, http, config, user pool, load profile, report helpers
├── scenarios/
│   ├── backfill/          BULK DATA ENTRY: creates documents/masters/users (TARGET_DOCS, VUs) - launched from Java backfill
│   └── performance/       PERFORMANCE TESTING: smoke, load, login/logout, chain load - thresholds, ramping profiles
├── data/                  business/, pools/, attachments/, generated/ (written by Java: users, bulk data files)
├── data-export/           TypeScript tool that exports item/service-invoice data from a tenant DB (npm install first)
├── monitoring/ scripts/
```

**Backfill vs performance:** a *backfill* scenario's job is to leave data behind (its result is a count of created records, e.g.
`K6_SUMMARY created=N failed=M`); a *performance* scenario's job is to measure (its result is thresholds and percentiles). They share
`flows/` and `lib/`, never each other's scenario files.

Java launches k6 through `com.gsl.api.bulk.K6ProcessBulkDataProvisioner`; script files are derived from the `k6.home`
setting (default `performance/k6`) by `K6Scripts`, never from an absolute path. `datafallback.k6.scriptPath.<key>` stays as a per-key override.

## Files that are NOT in git (create them locally)
| File | What | How |
|---|---|---|
| `k6/data/generated/users-<tenant>.json` | the perf-test users (PERF rows of the environment's `UsersList.xlsx`) | written by Java before every launch; standalone: `mvn -o test -Dtest=K6UsersExportTest -Dk6.users.write=true -Denv=<env>`, then `-e USERS_FILE=data/generated/users-<tenant>.json` |
| `k6/data-export/src/config/<tenant>.json` | DB connection of a tenant for the export tool | shape in `db.sample.json` |
| `k6/reports/` | k6 run output | created by k6 |
| `k6/data/generated/` | data files the Java side hands to k6 | created by Java |
| `k6/data-export/node_modules/` | npm packages | `npm install` in `k6/data-export/` |

Never commit credentials: `.gitignore` already excludes the paths above.

## Running a scenario by hand
```bash
cd performance/k6
TENANT=qaprod TARGET_DOCS=1 k6 run scenarios/backfill/createGeneralVoucher.js     # bulk data entry
TENANT=qaprod k6 run scenarios/performance/smoke.js                                # performance smoke
```

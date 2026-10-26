# Jenkins profiles — mandatory & regression (AR / AP / GV)

One build = one browser. For all three browsers run the job three times with
`ENV_NAME=clone-staging` and `BROWSER=chrome`, `firefox`, `edge`.

| Job (name must contain) | Default `MAVEN_PROFILE` |
|---|---|
| `AR-MANDATORY` / `AP-MANDATORY` / `GV-MANDATORY` (e.g. `SeleniumFramework-AR-Mandatory-Staging`) | `ar-mandatory` / `ap-mandatory` / `gv-mandatory` |
| `SeleniumFramework-AR-Regression-P1` | `ar-regression-phase1` |
| `SeleniumFramework-AR-Regression-P2` | `ar-regression-phase2` |
| `SeleniumFramework-AR-Regression-P3` | `ar-regression-phase3` |
| `SeleniumFramework-AP-Regression-P1` | `ap-regression-phase1` |
| `SeleniumFramework-AP-Regression-P2` | `ap-regression-phase2` |
| `SeleniumFramework-AP-Regression-P3` | `ap-regression-phase3` |
| `SeleniumFramework-GV-Regression-P1` | `regression-generalvoucher-phase1` |
| `SeleniumFramework-GV-Regression-P2` | `regression-generalvoucher-phase2` |
| `SeleniumFramework-GV-Regression-P3` | `regression-generalvoucher-phase3` |
| `SeleniumFramework-AR-UI-Validation` | `ar-ui` |
| `SeleniumFramework-AP-UI-Validation` | `ap-ui` |
| `SeleniumFramework-GV-UI-Validation` | `gv-ui` |

Use `MAVEN_PROFILE` to override per build. The allowed list lives in
`allowedProfiles()` in the `Jenkinsfile`; keep it in sync with the profile ids in `pom.xml`.

Suites: `testng-regression-arvoucher-phase{1,2,3}.xml` (AR only), `testng-regression-apvoucher-phase{1,2,3}.xml`,
`testng-regression-generalvoucher-phase{1,2,3}.xml`.

Local run: `mvn clean test -Par-regression-phase1 -Denv=clone-staging -Dbrowser=firefox`

## Source repo, credentials and report output
- Every job: Pipeline script from SCM → Git, URL `https://github.com/aranib-bit/GinesysAutomation.git`,
  branch `*/GinesysAutomation`, Credentials = the shared Jenkins credential `gsl-github-ginesys`
  (kind *Username with password*, Global scope; password = a read-only fine-grained GitHub token for that repo).
  Team members run builds through their Jenkins permissions and need no GitHub access.
- After each run, reports are copied to `<Jenkins workspace parent>\GinesysAutomation\<env>\reports\runs\<runId>\...`
  (one folder per environment: staging, clone-staging, feature-01..08, qaprod). The path is derived from the build's
  workspace, so it follows whatever drive Jenkins uses. To place it elsewhere, set a Jenkins global environment
  variable `GINESYS_HOME` (the `<env>` folder is appended).
- `DOCKER_EXE` / `DOCKER_CONFIG` can be overridden with Jenkins global environment variables of the same name;
  the Jenkinsfile only carries fallback defaults.

## qaprod (no database)
`ENV_NAME=qaprod` has `db.available=no` and `testdata.source=EXCEL`: no database connection is ever attempted
(`DatabaseManager` refuses), and DB-derived validation is replaced by API/audit validation
(`validation.nodb.mode=API`, or `SKIP`). Profiles whose data sources or validators still need the database
(the `*-mandatory` / `*-regression-*` DB-mode suites) fail fast with "DB disabled by config" until the API
data sources and validators of the DB-optional plan are in place; run only UI/API-capable profiles there.
`api.tenant.active` in `env/qaprod/api.properties` must be filled in (it is `CHANGE_ME` on purpose) and
`env/qaprod/data/UsersList.xlsx` must hold the real qaprod users.

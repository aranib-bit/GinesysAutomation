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

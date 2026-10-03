# Jenkins profiles — mandatory & regression (AR / AP / GV)

One build = one browser. For all three browsers run the job three times with
`ENV_NAME=clone-staging` and `BROWSER=chrome`, `firefox`, `edge`.

| Job (name must contain) | Default `MAVEN_PROFILE` | Other allowed profiles |
|---|---|---|
| `AR-MANDATORY` | `ar-mandatory` | – |
| `AP-MANDATORY` | `ap-mandatory` | – |
| `GV-MANDATORY` | `gv-mandatory` | – |
| `AR-REGRESSION` | `ar-regression-phase1` | `ar-regression-phase2`, `ar-regression-phase3` |
| `AP-REGRESSION` | `ap-regression-phase1` | `ap-regression-phase2`, `ap-regression-phase3` |
| `GV-REGRESSION` | `regression-generalvoucher-phase1` | `regression-generalvoucher-phase2`, `regression-generalvoucher-phase3` |

UI-contract profiles `ar-ui`, `ap-ui`, `gv-ui` are also accepted. The allowed list lives in
`allowedProfiles()` in the `Jenkinsfile`; keep it in sync with the profile ids in `pom.xml`.

Suites: `testng-regression-arvoucher-phase{1,2,3}.xml` (AR only), `testng-regression-apvoucher-phase{1,2,3}.xml`,
`testng-regression-generalvoucher-phase{1,2,3}.xml`.

Local run: `mvn clean test -Par-regression-phase1 -Denv=clone-staging -Dbrowser=firefox`

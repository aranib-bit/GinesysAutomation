// Each Jenkins job's default MAVEN_PROFILE is derived from its job name so that
// the 3 (soon more) jobs sharing this one Jenkinsfile don't fight over a single
// hardcoded default. Manually editing "Default Value" in a job's Configure page
// does NOT stick — this block is re-applied from source on every build. To give
// a new job its own default, add a case here instead.
def pickDefaultProfile(jobName) {
    def name = (jobName ?: '').toUpperCase()
    // Job names: SeleniumFramework-<AR|AP|GV>-<Mandatory-Staging | UI-Validation | Regression-P1/P2/P3>.
    // Regression is one job per phase; the phase number in the name picks the profile.
    // contains() only - no Matcher (not CPS-serializable; see the pickDefaultEnv commit).
    for (def n in ['1', '2', '3']) {
        if (name.contains('REGRESSION-P' + n)) {
            if (name.contains('AR-REGRESSION')) return 'ar-regression-phase' + n
            if (name.contains('AP-REGRESSION')) return 'ap-regression-phase' + n
            if (name.contains('GV-REGRESSION')) return 'regression-generalvoucher-phase' + n
        }
    }
    if (name.contains('AR-UI-VALIDATION')) return 'ar-ui'
    if (name.contains('AP-UI-VALIDATION')) return 'ap-ui'
    if (name.contains('GV-UI-VALIDATION')) return 'gv-ui'
    if (name.contains('AR-MANDATORY')) return 'ar-mandatory'
    if (name.contains('GV-MANDATORY')) return 'gv-mandatory'
    if (name.contains('AP-MANDATORY')) return 'ap-mandatory'
    return 'ap-mandatory' // safe fallback for any job not yet mapped
}

// Dropdown values, repeated here (declarative `choices:` needs literal lists) so a value that did not
// come from the dropdown is rejected. KEEP IN SYNC with the ENV_NAME / BROWSER choices below.
def allowedEnvs() {
    return ['staging', 'clone-staging',
            'feature-01', 'feature-02', 'feature-03', 'feature-04',
            'feature-05', 'feature-06', 'feature-07', 'feature-08',
            'qaprod']
}
def allowedBrowsers() {
    return ['chrome', 'firefox', 'edge']
}
// Profiles a build may run (pom.xml profile ids). KEEP IN SYNC with pom.xml when adding a suite.
def allowedProfiles() {
    return ['ar-mandatory', 'ap-mandatory', 'gv-mandatory',
            'ar-regression-phase1', 'ar-regression-phase2', 'ar-regression-phase3',
            'ap-regression-phase1', 'ap-regression-phase2', 'ap-regression-phase3',
            'regression-generalvoucher-phase1', 'regression-generalvoucher-phase2', 'regression-generalvoucher-phase3',
            'gv-edit-mandatory', 'ar-ui', 'ap-ui', 'gv-ui']
}

// The environment / browser this run uses. Falls back to the dropdown defaults when the parameter is
// missing — the first run after the parameter definitions changed (Jenkins registers new definitions only
// once a build has started, so that run still gets the old form), and runs started by a trigger or the API.
def effectiveEnv() {
    return (params.ENV_NAME ?: 'staging').toString().trim()
}
def effectiveBrowser() {
    return (params.BROWSER ?: 'chrome').toString().trim().toLowerCase()
}

// Lockable Resource that guards an environment AND its Selenium Grid. Resource names are the
// environment names (clone-staging, feature-05, ...), except staging, which keeps the pre-existing
// "staging-slot" resource. Two builds on the same environment queue; different environments run in parallel.
def lockResourceFor(envName) {
    return envName == 'staging' ? 'staging-slot' : envName
}

// Fixed position of each environment, used to give every environment its own Grid ports
// (hub 4500 + index, VNC 7950 + index) so Grids of different environments never collide.
// Keep the order stable: appending is safe, reordering changes the ports.
// Names are the folders under src/test/resources/env/ (see com.gsl.enums.TestEnvironment).
def gridIndexFor(envName) {
    def known = ['staging', 'clone-staging',
                 'feature-01', 'feature-02', 'feature-03', 'feature-04',
                 'feature-05', 'feature-06', 'feature-07', 'feature-08',
                 'mnt-01', 'oracle-staging', 'oracle-mnt', 'qaprod']
    def idx = known.indexOf(envName)
    if (idx < 0) {
        error "Unknown environment '${envName}'. Valid environments: ${known.join(', ')}."
    }
    return idx
}

pipeline {
    agent any

    options {
        // The lock is NOT taken here: its name depends on the ENV_NAME parameter, so it is taken by
        // the 'Environment' stage below (one Lockable Resource per environment). Taking it in a stage
        // also means the build name set in 'Init' is visible while a build waits for its environment.
        // Prefixes every console log line with a wall-clock timestamp, making it possible
        // to correlate stage duration (grid startup, test run, etc.) directly from the log.
        timestamps()
    }

    tools {
        jdk 'jdk17'
    }

    // Windows browsers are NOT offered here yet. The framework side is ready (-Dtarget.os=windows,
    // max.concurrent.sessions.windows, browser/OS version recording) but no Windows Grid exists.
    // To enable later: add an OS choice (linux/windows) below, pass -Dtarget.os=%TARGET_OS% in 'Run Tests',
    // and branch the Grid stages on it (compose file, DOCKER_HOST of the KVM host, hub URL, a '-win' lock suffix).
    // See docs/windows-grid-readiness.md for the full checklist. Until then -Dtarget.os is deliberately not
    // passed, so every run uses the default (linux) exactly as before.
    parameters {
        string(
            name: 'MAVEN_PROFILE',
            defaultValue: pickDefaultProfile(env.JOB_NAME),
            description: 'Maven profile / suite to run (mandatory, regression phase1/2/3, or *-ui). Allowed list: allowedProfiles() in the Jenkinsfile'
        )
        // Dropdowns: the first item is the preselected one. Names are folders under
        // src/test/resources/env/ — add mnt-01, oracle-staging, oracle-mnt here once their folders exist
        // (gridIndexFor above already knows them). qaprod has NO database (db.available=no): run only
        // profiles whose tests work without DB access (see docs/jenkins-profiles.md).
        choice(
            name: 'ENV_NAME',
            choices: ['staging', 'clone-staging',
                      'feature-01', 'feature-02', 'feature-03', 'feature-04',
                      'feature-05', 'feature-06', 'feature-07', 'feature-08',
                      'qaprod'],
            description: 'Target environment (src/test/resources/env/<name>)'
        )
        choice(
            name: 'BROWSER',
            choices: ['chrome', 'firefox', 'edge'],
            description: 'Browser to run on (the Grid image runs all three)'
        )
    }

    environment {
        GRID_COMPOSE_FILE = 'docker/docker-compose.selenium-grid.yml'
        // Jenkins service runs as LocalSystem, a different Windows identity than the
        // interactive user session Docker Desktop (per-user install) runs under — so
        // neither the docker CLI's location nor its default named-pipe daemon connection
        // are visible here. Full path sidesteps the missing PATH entry; DOCKER_HOST
        // points at the TCP daemon endpoint enabled in Docker Desktop → Settings → General.
        DOCKER_EXE = 'C:\\Users\\arani.barman\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'
        DOCKER_HOST = 'tcp://localhost:2375'
        // LocalSystem has no .docker\cli-plugins of its own, so the "compose" plugin
        // isn't found unless we point Docker's config dir explicitly at a real one.
        // Uses a dedicated CI config (not the personal one) — the personal config's
        // credsStore=desktop tries to exec docker-credential-desktop.exe, which isn't
        // on LocalSystem's PATH either; these images are public and need no auth anyway.
        DOCKER_CONFIG = 'C:\\ProgramData\\Jenkins\\.docker'
    }

    stages {
        stage('Init') {
            steps {
                script {
                    def targetEnv = effectiveEnv()
                    def targetBrowser = effectiveBrowser()
                    // Reject anything that did not come from the dropdowns (a typed or API-supplied value).
                    if (!allowedEnvs().contains(targetEnv)) {
                        error "Unknown environment '${targetEnv}'. Choose one of: ${allowedEnvs().join(', ')}."
                    }
                    if (!allowedBrowsers().contains(targetBrowser)) {
                        error "Unknown browser '${targetBrowser}'. Choose one of: ${allowedBrowsers().join(', ')}."
                    }
                    if (!allowedProfiles().contains(params.MAVEN_PROFILE)) {
                        error "Unknown Maven profile '${params.MAVEN_PROFILE}'. Choose one of: ${allowedProfiles().join(', ')}."
                    }
                    if (!params.ENV_NAME || !params.BROWSER) {
                        echo "NOTE: ENV_NAME='${params.ENV_NAME}' BROWSER='${params.BROWSER}' were not supplied " +
                             "(first run after the parameters changed, or started without parameters) - using " +
                             "${targetEnv} / ${targetBrowser}."
                    }
                    // Everything below reads TARGET_ENV / TARGET_BROWSER, never params.* directly.
                    env.TARGET_ENV = targetEnv
                    env.TARGET_BROWSER = targetBrowser
                    def idx = gridIndexFor(targetEnv)
                    env.LOCK_RESOURCE = lockResourceFor(targetEnv)
                    // Per-environment Grid: compose project name and host ports. The tests reach this
                    // Grid through -Ddocker.grid.url (see 'Run Tests'), and the compose file publishes
                    // GRID_PORT / VNC_PORT.
                    env.GRID_PROJECT = "grid-${targetEnv}"
                    env.GRID_PORT = (4500 + idx).toString()
                    env.VNC_PORT = (7950 + idx).toString()
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${targetEnv} | ${targetBrowser}"
                    echo "Environment=${targetEnv}, browser=${targetBrowser}, profile=${params.MAVEN_PROFILE}, " +
                         "lock=${env.LOCK_RESOURCE}, grid project=${env.GRID_PROJECT}, hub port=${env.GRID_PORT}, vnc port=${env.VNC_PORT}"
                }
            }
        }

        stage('Environment') {
            options {
                lock(resource: lockResourceFor(effectiveEnv()))
            }
            stages {
                stage('Checkout') {
                    steps {
                        script {
                            // The job's git SCM remote is configured as the dev folder's local path
                            // itself (see Jenkins job config) — capturing GIT_URL here derives that
                            // path at runtime instead of hardcoding it, so the post-build robocopy
                            // step below never needs a literal absolute path.
                            def scmVars = checkout scm
                            if (!scmVars.GIT_URL) {
                                error "GIT_URL was not returned by checkout scm — cannot derive PROJECT_ROOT."
                            }
                            env.PROJECT_ROOT = scmVars.GIT_URL
                        }
                    }
                }

                stage('Start Selenium Grid') {
                    steps {
                        powershell '''
                            & $env:DOCKER_EXE version --format "{{.Server.Version}}" *>$null
                            if ($LASTEXITCODE -ne 0) {
                                Write-Error "Docker Desktop is not running (or its daemon is unreachable at $env:DOCKER_HOST). Start Docker Desktop on the host and re-run this job."
                                exit 1
                            }
                            Write-Host "Docker daemon is reachable."

                            # One Grid per environment: compose project grid-<env>, host ports from GRID_PORT / VNC_PORT.
                            $running = & $env:DOCKER_EXE compose -p $env:GRID_PROJECT -f $env:GRID_COMPOSE_FILE ps --status running -q
                            if ($running) {
                                Write-Host "Grid $env:GRID_PROJECT is already running - skipping deploy."
                            } else {
                                Write-Host "Grid $env:GRID_PROJECT is not running - deploying via compose (hub port $env:GRID_PORT, VNC port $env:VNC_PORT)."
                                & $env:DOCKER_EXE compose -p $env:GRID_PROJECT -f $env:GRID_COMPOSE_FILE up -d
                                if ($LASTEXITCODE -ne 0) {
                                    Write-Error "docker compose up failed."
                                    exit 1
                                }
                            }
                        '''
                        powershell '''
                            $statusUrl = "http://localhost:$($env:GRID_PORT)/status"
                            Write-Host "Polling grid status at $statusUrl"

                            $deadline = (Get-Date).AddSeconds(90)
                            while ((Get-Date) -lt $deadline) {
                                try {
                                    $r = Invoke-RestMethod -Uri $statusUrl -TimeoutSec 3
                                    if ($r.value.ready -eq $true) { Write-Host "Grid ready"; exit 0 }
                                } catch {}
                                Start-Sleep -Seconds 2
                            }
                            Write-Error "Grid did not become ready in time at $statusUrl"
                            exit 1
                        '''
                    }
                }

                stage('Run Tests') {
                    steps {
                        // * NOTE on live progress: reports/latest/index.html is refreshed by the
                        // framework after every single test case (ExtentReport.flushReports(),
                        // gated by report.flush.batch.enabled=false in report.properties). To
                        // watch it update live *during* this stage, open this build's
                        // "Workspace" link in Jenkins and browse to reports/latest/index.html
                        // directly — do NOT call publishHTML on a timer here: the HTML
                        // Publisher plugin registers a brand-new project-level "Extent Report"
                        // sidebar action on every single invocation regardless of the keepAll
                        // setting (keepAll only affects whether past builds' archived HTML is
                        // retained on disk, not whether a call registers a fresh sidebar
                        // action) — a periodic call therefore produces one duplicate link per
                        // poll. publishHTML must be called at most once per build; that one
                        // call is the authoritative post.always publish below.
                        // Secrets are not in git. Each environment has one Jenkins "Secret file" credential,
                        // id gsl-secrets-<env> (e.g. gsl-secrets-feature-05), whose content is a
                        // secrets.local.properties file (template: src/test/resources/env/secrets.local.properties.example).
                        // It is copied next to the environment's config for the run and deleted afterwards.
                        // -Ddocker.grid.url points the tests at THIS environment's Grid; Maven forwards -D
                        // user properties to the forked test JVM, and BrowserConfig reads system properties.
                        script {
                            withCredentials([file(credentialsId: "gsl-secrets-${env.TARGET_ENV}", variable: 'SECRETS_FILE')]) {
                                try {
                                    bat 'copy /Y "%SECRETS_FILE%" "src\\test\\resources\\env\\%TARGET_ENV%\\secrets.local.properties"'
                                    bat ".\\mvnw.cmd clean test -P%MAVEN_PROFILE% -Denv=%TARGET_ENV% -Dbrowser=%TARGET_BROWSER% -Ddocker.grid.url=http://localhost:%GRID_PORT%/wd/hub -Drunmode=docker_grid -Dheadless=true"
                                } finally {
                                    bat 'if exist "src\\test\\resources\\env\\%TARGET_ENV%\\secrets.local.properties" del /Q "src\\test\\resources\\env\\%TARGET_ENV%\\secrets.local.properties"'
                                }
                            }
                        }
                    }
                }
            }
            post {
                always {
                    // Teardown of THIS environment's Grid only (compose project grid-<env>), run while the
                    // environment lock is still held so another build on the same environment cannot
                    // start a Grid that this step then removes.
                    bat "\"%DOCKER_EXE%\" compose -p %GRID_PROJECT% -f %GRID_COMPOSE_FILE% down"
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
            archiveArtifacts artifacts: 'reports/**', allowEmptyArchive: true
            publishHTML(target: [
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'reports/latest',
                reportFiles: 'index.html',
                reportName: 'Extent Report'
            ])
            // Jenkins clones into its own workspace (no customWorkspace configured), so
            // reports/ never reaches the dev folder on its own — sync a copy back after
            // every run. /XO = only copy files newer than what's already there;
            // "& exit 0" because robocopy's own success exit codes are non-zero.
            // Destination is PROJECT_ROOT (derived at Checkout time from checkout scm's
            // GIT_URL) + \reports — no profile-specific subfolder, so every profile's run
            // lands at reports\runs\<runId>\... the same way a local (non-Jenkins) run does.
            bat 'robocopy "%WORKSPACE%\\reports" "%PROJECT_ROOT%\\reports" /E /XO /R:2 /W:2 & exit 0'
        }
    }
}

// Each Jenkins job's default MAVEN_PROFILE is derived from its job name so that
// the 3 (soon more) jobs sharing this one Jenkinsfile don't fight over a single
// hardcoded default. Manually editing "Default Value" in a job's Configure page
// does NOT stick — this block is re-applied from source on every build. To give
// a new job its own default, add a case here instead.
def pickDefaultProfile(jobName) {
    def name = (jobName ?: '').toUpperCase()
    if (name.contains('AR-MANDATORY')) return 'ar-mandatory'
    if (name.contains('GV-MANDATORY')) return 'gv-mandatory'
    if (name.contains('AP-MANDATORY')) return 'ap-mandatory'
    return 'ap-mandatory' // safe fallback for any job not yet mapped (e.g. future regression jobs)
}

pipeline {
    agent any

    options {
        // Only one staging build (any feature: AP, AR, or future ones) may run at a time —
        // two builds racing here would both try to stand up the Selenium Grid via the same
        // docker-compose file, colliding on container names/ports. A second build queues
        // ("Waiting for lock") until the first finishes instead of colliding.
        // Any future staging Jenkinsfile must reuse this exact resource name to stay in
        // the same mutual-exclusion group.
        lock(resource: 'staging-slot')
        // Prefixes every console log line with a wall-clock timestamp, making it possible
        // to correlate stage duration (grid startup, test run, etc.) directly from the log.
        timestamps()
    }

    tools {
        jdk 'jdk17'
    }

    parameters {
        string(
            name: 'MAVEN_PROFILE',
            defaultValue: pickDefaultProfile(env.JOB_NAME),
            description: 'Maven profile / suite to run'
        )
    }

    environment {
        GRID_COMPOSE_FILE = 'docker/docker-compose.selenium-grid.yml'
        // Single source of truth for the grid hub URL — same key BrowserConfig.dockerGridUrl()
        // reads at test-runtime (via DockerGridProvider), so the health-check below can never
        // drift from what the framework itself actually connects to.
        BROWSER_CONFIG_FILE = 'src/test/resources/config/browser.properties'
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

                    $running = & $env:DOCKER_EXE ps --filter "name=selenium-chrome-node" --filter "status=running" --format "{{.Names}}"
                    if ($running -contains "selenium-chrome-node") {
                        Write-Host "selenium-chrome-node is already running - skipping deploy."
                    } else {
                        Write-Host "selenium-chrome-node is not running - deploying via compose."
                        & $env:DOCKER_EXE compose -f $env:GRID_COMPOSE_FILE up -d
                        if ($LASTEXITCODE -ne 0) {
                            Write-Error "docker compose up failed."
                            exit 1
                        }
                    }
                '''
                powershell '''
                    $line = Get-Content $env:BROWSER_CONFIG_FILE | Where-Object { $_ -match "^\\s*docker\\.grid\\.url\\s*=" } | Select-Object -First 1
                    if (-not $line) {
                        Write-Error "docker.grid.url not found in $env:BROWSER_CONFIG_FILE"
                        exit 1
                    }
                    $gridUrl = ($line -split "=", 2)[1].Trim()
                    $statusUrl = "$gridUrl/status"
                    Write-Host "Polling grid status at $statusUrl"

                    $deadline = (Get-Date).AddSeconds(60)
                    while ((Get-Date) -lt $deadline) {
                        try {
                            $r = Invoke-RestMethod -Uri $statusUrl -TimeoutSec 3
                            if ($r.value.ready -eq $true) { Write-Host "Grid ready"; exit 0 }
                        } catch {}
                        Start-Sleep -Seconds 2
                    }
                    Write-Error "Grid did not become ready in time"
                    exit 1
                '''
            }
        }

        stage('Run Tests') {
            steps {
                script {
                    // Run the suite and periodically re-publish the "Extent Report" build-page
                    // link while it's still running, so it refreshes during the build instead of
                    // only appearing once at the very end (post.always still does the final,
                    // authoritative publish/archive/robocopy after this stage completes).
                    def testsFinished = false
                    parallel(
                        'tests': {
                            try {
                                bat ".\\mvnw.cmd clean test -P%MAVEN_PROFILE% -Drunmode=docker_grid -Dheadless=true"
                            } finally {
                                testsFinished = true
                            }
                        },
                        'live-report': {
                            while (!testsFinished) {
                                sleep(time: 30, unit: 'SECONDS')
                                if (!testsFinished) {
                                    catchError(buildResult: null, stageResult: null) {
                                        publishHTML(target: [
                                            allowMissing: true,
                                            alwaysLinkToLastBuild: true,
                                            keepAll: true,
                                            reportDir: 'reports/latest',
                                            reportFiles: 'index.html',
                                            reportName: 'Extent Report'
                                        ])
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    post {
        always {
            bat "\"%DOCKER_EXE%\" compose -f %GRID_COMPOSE_FILE% down"
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

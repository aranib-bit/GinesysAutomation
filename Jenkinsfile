pipeline {
    agent any

    tools {
        jdk 'jdk17'
    }

    parameters {
        string(name: 'MAVEN_PROFILE', defaultValue: 'ap-mandatory', description: 'Maven profile / suite to run')
    }

    environment {
        GRID_COMPOSE_FILE = 'docker/docker-compose.selenium-grid.yml'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Start Selenium Grid') {
            steps {
                bat "docker compose -f %GRID_COMPOSE_FILE% up -d"
                bat """
                    powershell -Command ^
                    "\$deadline = (Get-Date).AddSeconds(60); ^
                    while ((Get-Date) -lt \$deadline) { ^
                        try { ^
                            \$r = Invoke-RestMethod -Uri http://localhost:4444/wd/hub/status -TimeoutSec 3; ^
                            if (\$r.value.ready -eq \$true) { Write-Host 'Grid ready'; exit 0 } ^
                        } catch {} ^
                        Start-Sleep -Seconds 2 ^
                    }; ^
                    Write-Error 'Grid did not become ready in time'; exit 1"
                """
            }
        }

        stage('Run Tests') {
            steps {
                bat ".\\mvnw.cmd clean test -P%MAVEN_PROFILE% -Drunmode=docker_grid -Dheadless=true"
            }
        }
    }

    post {
        always {
            bat "docker compose -f %GRID_COMPOSE_FILE% down"
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
        }
    }
}

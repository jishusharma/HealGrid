@NonCPS
def parseRerunTests(String reportText) {
    def json = new groovy.json.JsonSlurper().parseText(reportText)
    return json.clusters
        .findAll { it.decision == 'RERUN' }
        .collectMany { it.tests }
        .unique()
        .join(',')
}

pipeline {
    agent any
    environment {
        COMPOSE_FILE = 'docker-compose.yml'
        API_GATE_PASSED = 'false'
    }
    triggers {
        githubPush()
    }
    parameters {
        choice(
            name: 'TEST_PROFILE',
            choices: ['ci-default', 'api', 'grid-ui', 'mobile', 'healing-proof', 'reporting-validation', 'full-regression'],
            description: 'Select the test profile for manual Jenkins runs. Webhook builds use ci-default.'
        )
        booleanParam(name: 'RUN_MOBILE', defaultValue: false, description: 'Run mobile tests on BrowserStack')
    }
    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out code from GitHub...'
                checkout scm
            }
        }
        stage('Build') {
            steps {
                echo 'Compiling project...'
                sh 'mvn clean compile -q'
            }
        }
        stage('Prepare Test Output') {
            steps {
                echo 'Cleaning stale test output...'
                sh '''
                    rm -rf target/allure-results target/allure-report target/surefire-reports
                    rm -f target/ai-failure-report.json target/allure-history.tar.gz
                    rm -f target/observability/flaky-report.html target/observability/trend-report.html
                    mkdir -p target/allure-results target/surefire-reports/junitreports target/observability
                    touch target/.test-output-start
                '''
            }
        }
        stage('Start DB') {
            steps {
                echo 'Starting PostgreSQL database...'
                sh 'docker-compose -f $COMPOSE_FILE up -d postgres-db'
                sh '''
                    set +e
                    echo "Waiting for PostgreSQL to become ready..."
                    for i in $(seq 1 15); do
                        docker-compose -f $COMPOSE_FILE exec -T postgres-db pg_isready -U healenium_user -d healenium
                        if [ $? -eq 0 ]; then
                            echo "PostgreSQL is ready."
                            break
                        fi
                        echo "PostgreSQL not ready yet. Retrying in 2 seconds... (attempt $i/15)"
                        sleep 2
                    done
                '''
                echo 'PostgreSQL is ready.'
            }
        }
        stage('API Tests') {
            when {
                expression { ['ci-default', 'api', 'full-regression'].contains(params.TEST_PROFILE ?: 'ci-default') }
            }
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    echo 'Running REST Assured API tests...'
                    withCredentials([string(credentialsId: 'REQRES_API_KEY', variable: 'REQRES_API_KEY')]) {
                        sh 'mvn test -Dsurefire.suiteXmlFiles=testNgXmls/api-suite.xml -Dsuite.name=api'
                    }
                    sh 'cp target/surefire-reports/TEST-TestSuite.xml target/surefire-reports/TEST-API-TestSuite.xml'
                    script {
                        env.API_GATE_PASSED = 'true'
                    }
                }
            }
        }
        stage('Test') {
            when {
                expression {
                    def profile = params.TEST_PROFILE ?: 'ci-default'
                    if (profile == 'grid-ui') {
                        return true
                    }
                    return ['ci-default', 'full-regression'].contains(profile) && env.API_GATE_PASSED == 'true'
                }
            }
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    script {
                        def profile = params.TEST_PROFILE ?: 'ci-default'
                        def suiteXml = profile == 'full-regression' ? 'testNgXmls/ui-grid-crossbrowser.xml' : 'testNgXmls/ui-grid-parallel-classes.xml'
                        def suiteName = profile == 'full-regression' ? 'full-regression-grid' : 'grid'
                        echo "Starting Grid + Healenium tests for ${profile}: ${suiteXml}"
                        withEnv(["TEST_SUITE_XMLS=${suiteXml}", "SUITE_NAME=${suiteName}"]) {
                            sh 'mkdir -p target/surefire-reports/junitreports'
                            sh 'docker-compose -f $COMPOSE_FILE up --build --abort-on-container-exit --exit-code-from test-runner healenium selector-imitator selenium-hub chrome firefox test-runner'
                            sh 'docker cp $(docker-compose -f $COMPOSE_FILE ps -q --all test-runner):/app/target/surefire-reports/. target/surefire-reports/'
                            sh 'docker cp $(docker-compose -f $COMPOSE_FILE ps -q --all test-runner):/app/target/allure-results/. target/allure-results/'
                        }
                    }
                }
            }
            post {
                always {
                    sh 'docker-compose -f $COMPOSE_FILE stop test-runner chrome firefox selenium-hub healenium selector-imitator || true'
                    sh 'docker-compose -f $COMPOSE_FILE rm -f test-runner chrome firefox selenium-hub healenium selector-imitator || true'
                }
            }
        }
        stage('Mobile Tests') {
            when {
                expression {
                    def profile = params.TEST_PROFILE ?: 'ci-default'
                    def requested = params.RUN_MOBILE || ['mobile', 'full-regression'].contains(profile)
                    def apiGateRequired = ['ci-default', 'api', 'full-regression'].contains(profile)
                    return requested && (!apiGateRequired || env.API_GATE_PASSED == 'true')
                }
            }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    script {
                        withCredentials([string(credentialsId: 'BROWSERSTACK_USERNAME', variable: 'BROWSERSTACK_USERNAME'),
                                         string(credentialsId: 'BROWSERSTACK_ACCESS_KEY', variable: 'BROWSERSTACK_ACCESS_KEY')]) {
                            echo 'Running mobile Android and iOS tests on BrowserStack...'
                            sh "mvn test -Dsurefire.suiteXmlFiles=testNgXmls/mobile.xml -Dbuild.name=\"HealGrid-Mobile-${env.BUILD_NUMBER}\" -Dsuite.name=mobile"
                        }
                    }
                }
            }
        }
        stage('Healing Proof') {
            when {
                expression {
                    def profile = params.TEST_PROFILE ?: 'ci-default'
                    return profile == 'healing-proof' || (profile == 'full-regression' && env.API_GATE_PASSED == 'true')
                }
            }
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    echo 'Running focused Healenium healing proof...'
                    withEnv(["TEST_SUITE_XMLS=testNgXmls/healenium-healing-proof.xml", "SUITE_NAME=healing-proof"]) {
                        sh 'mkdir -p target/surefire-reports/junitreports'
                        sh 'docker-compose -f $COMPOSE_FILE up --build --abort-on-container-exit --exit-code-from test-runner healenium selector-imitator selenium-hub chrome firefox test-runner'
                        sh 'docker cp $(docker-compose -f $COMPOSE_FILE ps -q --all test-runner):/app/target/surefire-reports/. target/surefire-reports/'
                        sh 'docker cp $(docker-compose -f $COMPOSE_FILE ps -q --all test-runner):/app/target/allure-results/. target/allure-results/'
                    }
                }
            }
            post {
                always {
                    sh 'docker-compose -f $COMPOSE_FILE stop test-runner chrome firefox selenium-hub healenium selector-imitator || true'
                    sh 'docker-compose -f $COMPOSE_FILE rm -f test-runner chrome firefox selenium-hub healenium selector-imitator || true'
                }
            }
        }
        stage('Reporting Validation') {
            when {
                expression { (params.TEST_PROFILE ?: 'ci-default') == 'reporting-validation' }
            }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    echo 'Running intentional failure suite for report validation...'
                    sh 'mvn test -Dsurefire.suiteXmlFiles=testNgXmls/failures-demo.xml -Dsuite.name=reporting-validation'
                }
            }
        }
        stage('Sanitize Test Output') {
            steps {
                echo 'Removing stale test output reintroduced by mounted Docker paths...'
                sh '''
                    if [ -f target/.test-output-start ]; then
                        find target/allure-results -mindepth 1 -maxdepth 1 ! -newer target/.test-output-start -exec rm -rf {} +
                        find target/surefire-reports -mindepth 1 ! -newer target/.test-output-start -exec rm -rf {} +
                        mkdir -p target/surefire-reports/junitreports
                    fi
                '''
            }
        }
        stage('AI Failure Analysis') {
            steps {
                echo 'Analysing failures with Claude AI...'
                withCredentials([string(credentialsId: 'GROQ_API_KEY', variable: 'CLAUDE_API_KEY')]) {
                    sh 'mvn exec:java -Dexec.mainClass=ai.AiFailureAnalyzer -Dexec.classpathScope=runtime -Dfork=true'
                }
                script {
                    if (fileExists('target/ai-failure-report.json')) {
                        archiveArtifacts artifacts: 'target/ai-failure-report.json', allowEmptyArchive: true
                        echo 'AI failure report archived.'
                    } else {
                        echo 'No failures detected - report not generated.'
                    }
                }
            }
        }
        stage('Rerun RERUN-tagged Tests') {
            steps {
                script {
                    if (fileExists('target/ai-failure-report.json')) {
                        def rerunTests = parseRerunTests(readFile('target/ai-failure-report.json'))
                        def profile = params.TEST_PROFILE ?: 'ci-default'
                        def localRerunProfiles = ['api', 'reporting-validation']
                        if (rerunTests) {
                            if (localRerunProfiles.contains(profile)) {
                                echo "Rerunning RERUN-tagged tests: ${rerunTests}"
                                withCredentials([string(credentialsId: 'REQRES_API_KEY', variable: 'REQRES_API_KEY')]) {
                                    sh "mvn test -Dtest=${rerunTests} -DfailIfNoTests=false"
                                }
                            } else {
                                echo "Skipping rerun for profile '${profile}' - Grid/Healenium stack no longer running."
                            }
                        } else {
                            echo 'No tests marked RERUN. Skipping.'
                        }
                    } else {
                        echo 'No AI report found. Skipping rerun.'
                    }
                }
            }
        }
        stage('Persist Results') {
            steps {
                echo 'Persisting test results to Postgres...'
                withEnv(["DB_HOST=host.docker.internal", "DB_PORT=5432", "DB_NAME=healenium", "DB_USER=healenium_user", "DB_PASSWORD=healenium_password"]) {
                    sh 'mvn verify -P observability -DskipTests'
                }
            }
        }
        stage('Flaky Detection') {
            steps {
                echo 'Detecting flaky tests from history...'
                withEnv(["DB_HOST=host.docker.internal", "DB_PORT=5432", "DB_NAME=healenium", "DB_USER=healenium_user", "DB_PASSWORD=healenium_password"]) {
                    sh 'mvn exec:java -Dexec.mainClass=observability.FlakyDetector -Dexec.classpathScope=runtime'
                }
                archiveArtifacts artifacts: 'target/observability/flaky-report.html', allowEmptyArchive: true
            }
        }
        stage('Trend Report') {
            steps {
                echo 'Generating build-based trend report...'
                withEnv(["DB_HOST=host.docker.internal", "DB_PORT=5432", "DB_NAME=healenium", "DB_USER=healenium_user", "DB_PASSWORD=healenium_password"]) {
                    sh 'mvn exec:java -Dexec.mainClass=observability.TrendReporter -Dexec.classpathScope=runtime'
                }
                archiveArtifacts artifacts: 'target/observability/trend-report.html', allowEmptyArchive: true
            }
        }
        stage('Report') {
            steps {
                echo 'Preparing Allure history and generating report...'
                script {
                    def prevBuild = currentBuild.previousSuccessfulBuild
                    if (prevBuild != null) {
                        def prevArchive = "${JENKINS_HOME}/jobs/${env.JOB_NAME}/builds/${prevBuild.number}/archive"
                        def prevHistoryArchive = "${prevArchive}/target/allure-history.tar.gz"
                        def prevHist = "${prevArchive}/target/allure-report/history"
                        sh """
                            mkdir -p "${WORKSPACE}/target/allure-results"
                            if [ -f "${prevHistoryArchive}" ]; then
                                tar -xzf "${prevHistoryArchive}" -C "${WORKSPACE}/target/allure-results"
                            elif [ -d "${prevHist}" ]; then
                                cp -r "${prevHist}" "${WORKSPACE}/target/allure-results/history"
                            fi
                        """
                    }
                }
                sh 'allure generate target/allure-results --clean -o target/allure-report'
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/allure-report',
                    reportFiles: 'index.html',
                    reportName: 'Allure Report'
                ])
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/observability',
                    reportFiles: 'flaky-report.html',
                    reportName: 'Flaky Report'
                ])
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/observability',
                    reportFiles: 'trend-report.html',
                    reportName: 'Trend Report'
                ])
            }
        }
    }
    post {
        always {
            echo 'Archiving Allure history for next build...'
            sh '''
                if [ -d target/allure-report/history ]; then
                    tar -czf target/allure-history.tar.gz -C target/allure-report history
                else
                    rm -f target/allure-history.tar.gz
                fi
            '''
            archiveArtifacts artifacts: 'target/allure-history.tar.gz', allowEmptyArchive: true
            echo 'Cleaning up containers...'
            sh 'docker-compose -f $COMPOSE_FILE stop healenium selector-imitator selenium-hub chrome firefox test-runner || true'
            sh 'docker-compose -f $COMPOSE_FILE rm -f healenium selector-imitator selenium-hub chrome firefox test-runner || true'
            emailext(
                subject: "HealGrid Test Results - ${currentBuild.currentResult}",
                body: """
                    <p>Build: <a href="${env.BUILD_URL}">${env.JOB_NAME} #${env.BUILD_NUMBER}</a></p>
                    <p>Status: ${currentBuild.currentResult}</p>
                    <p>Allure Report: <a href="${env.BUILD_URL}Allure_20Report/">View Report</a></p>
                """,
                to: 'jishu.sharma@gmail.com'
            )
        }
        success {
            echo 'Pipeline passed!'
        }
        failure {
            echo 'Pipeline failed! Check logs above.'
        }
    }
}

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
    }
    triggers {
        githubPush()
    }
    parameters {
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
            steps {
                echo 'Running REST Assured API tests...'
                withCredentials([string(credentialsId: 'REQRES_API_KEY', variable: 'REQRES_API_KEY')]) {
                    sh 'mvn test -Dsurefire.suiteXmlFiles=testNgXmls/api.xml -Dsuite.name=api'
                }
                sh 'cp target/surefire-reports/TEST-TestSuite.xml target/surefire-reports/TEST-API-TestSuite.xml'
            }
        }
        stage('Mobile Tests') {
            when {
                expression { params.RUN_MOBILE }
            }
            steps {
                script {
                    withCredentials([string(credentialsId: 'BROWSERSTACK_USERNAME', variable: 'BROWSERSTACK_USERNAME'),
                                     string(credentialsId: 'BROWSERSTACK_ACCESS_KEY', variable: 'BROWSERSTACK_ACCESS_KEY')]) {
                        echo 'Running mobile Android tests on BrowserStack...'
                        sh "mvn test -Dsurefire.suiteXmlFiles=testNgXmls/mobile.xml -Dexecution=browserstack -Dbs.device=\"Samsung Galaxy S23\" -Dbs.os.version=13.0 -Dbuild.name=\"HealGrid-Mobile-${env.BUILD_NUMBER}\" -Dsuite.name=mobile -Dbrowser.name=android"
                        sh "mvn test -Dsurefire.suiteXmlFiles=testNgXmls/mobile_ios.xml -Dexecution=browserstack -Dbs.device=\"iPhone 14\" -Dbs.os.version=16 -Dbuild.name=\"HealGrid-Mobile-${env.BUILD_NUMBER}\" -Dsuite.name=mobile -Dbrowser.name=ios"
                    }
                }
            }
        }
        stage('Test') {
            steps {
                echo 'Starting Grid + Healenium + Running tests...'
                sh 'mkdir -p target/surefire-reports/junitreports'
                sh 'docker-compose -f $COMPOSE_FILE up --build --abort-on-container-exit healenium selector-imitator selenium-hub chrome firefox test-runner'
                sh 'docker cp $(docker-compose -f $COMPOSE_FILE ps -q --all test-runner):/app/target/surefire-reports/. target/surefire-reports/'
                sh 'ls -la target/surefire-reports/'
                sh 'docker cp $(docker-compose -f $COMPOSE_FILE ps -q --all test-runner):/app/target/allure-results/. target/allure-results/'
                sh 'ls -la target/allure-results/'
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
                        echo 'No failures detected — report not generated.'
                    }
                }
            }
        }
        stage('Rerun RERUN-tagged Tests') {
            steps {
                script {
                    if (fileExists('target/ai-failure-report.json')) {
                        def report = readFile('target/ai-failure-report.json')
                        def rerunTests = parseRerunTests(report)
                        if (rerunTests) {
                            echo "Rerunning RERUN-tagged tests: ${rerunTests}"
                            sh "mvn test -Dtest=${rerunTests} -DfailIfNoTests=false"
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
                archiveArtifacts artifacts: 'target/observability/flaky-report.json', allowEmptyArchive: true
            }
        }
        stage('Trend Report') {
            steps {
                echo 'Generating build‑based trend report...'
                withEnv(["DB_HOST=host.docker.internal", "DB_PORT=5432", "DB_NAME=healenium", "DB_USER=healenium_user", "DB_PASSWORD=healenium_password"]) {
                    sh 'mvn exec:java -Dexec.mainClass=observability.TrendReporter -Dexec.classpathScope=runtime'
                }
                archiveArtifacts artifacts: 'target/observability/trend-report.txt', allowEmptyArchive: true
            }
        }
        stage('Report') {
            steps {
                echo 'Publishing Allure report...'
                sh 'allure generate target/allure-results --clean -o target/allure-report'
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/allure-report',
                    reportFiles: 'index.html',
                    reportName: 'Allure Report'
                ])
            }
        }
    }
    post {
        always {
            echo 'Cleaning up containers...'
            sh 'docker-compose stop postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
            sh 'docker-compose rm -f postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
            emailext(
                subject: "HealGrid Test Results - ${currentBuild.currentResult}",
                body: """
                    <p>Build: <a href="${env.BUILD_URL}">${env.JOB_NAME} #${env.BUILD_NUMBER}</a></p>
                    <p>Status: ${currentBuild.currentResult}</p>
                    <p>Allure Report: <a href="${env.BUILD_URL}Allure_20Report/">View Report</a></p>
                """,
                to: 'your-team@example.com'
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
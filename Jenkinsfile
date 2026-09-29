// Declarative pipeline equivalent of .github/workflows/ci.yml, for teams on Jenkins.
// Expects agents with Docker (for Testcontainers and image builds), a SonarQube server
// configured as "sonarqube", and credentials "registry-credentials" / "kubeconfig-staging".
pipeline {
    agent { label 'docker' }

    options {
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
        disableConcurrentBuilds(abortPrevious: true)
        timestamps()
    }

    environment {
        REGISTRY = 'ghcr.io/rkunameneni'
        IMAGE_TAG = "${env.GIT_COMMIT.take(12)}"
        SERVICES = 'api-gateway order-service inventory-service payment-service notification-service'
    }

    stages {
        stage('Unit tests') {
            steps {
                sh './mvnw -B -ntp -Punit verify'
            }
        }

        stage('Integration and acceptance tests') {
            steps {
                // Testcontainers (Postgres, Kafka, Redis) + the Cucumber saga suite.
                sh './mvnw -B -ntp verify'
            }
        }

        stage('SonarQube analysis') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh './mvnw -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121:sonar'
                }
            }
        }

        stage('Quality gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Validate infrastructure') {
            parallel {
                stage('Terraform') {
                    steps {
                        sh '''
                          terraform -chdir=infra/terraform fmt -check -recursive
                          terraform -chdir=infra/terraform init -backend=false -input=false
                          terraform -chdir=infra/terraform validate
                        '''
                    }
                }
                stage('Helm') {
                    steps {
                        sh 'helm lint deploy/helm/eventflow'
                    }
                }
            }
        }

        stage('Build and push images') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry-credentials',
                        usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_TOKEN')]) {
                    sh '''
                      echo "$REGISTRY_TOKEN" | docker login ghcr.io -u "$REGISTRY_USER" --password-stdin
                      for service in $SERVICES; do
                        docker build -f "$service/Dockerfile" -t "$REGISTRY/eventflow-$service:$IMAGE_TAG" .
                        docker push "$REGISTRY/eventflow-$service:$IMAGE_TAG"
                      done
                    '''
                }
            }
        }

        stage('Deploy to staging') {
            when { branch 'main' }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-staging', variable: 'KUBECONFIG')]) {
                    sh '''
                      helm upgrade --install eventflow deploy/helm/eventflow \
                        --namespace eventflow --create-namespace \
                        --set image.tag="$IMAGE_TAG" \
                        --wait --timeout 10m
                    '''
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true,
                  testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'acceptance-tests/target/cucumber-report.html'
        }
        cleanup {
            cleanWs()
        }
    }
}

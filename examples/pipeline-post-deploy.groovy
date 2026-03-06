// Single marker after a successful deploy.
// Use this when you don't need a window — just a point-in-time annotation.

pipeline {
  agent any
  stages {
    stage('Deploy') {
      steps {
        sh './deploy.sh'
      }
    }
  }
  post {
    success {
      last9DeploymentMarker(
        serviceName: 'payments-api',
        environment: 'production'
      )
    }
  }
}

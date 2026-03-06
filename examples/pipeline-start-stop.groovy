// Standard deployment window — send start before deploying, stop after.
// Last9 shows the full window so you can correlate metrics with the rollout.

pipeline {
  agent any
  stages {
    stage('Deploy') {
      steps {
        last9DeploymentMarker(
          serviceName: 'payments-api',
          environment: 'production',
          eventState: 'start'
        )

        sh './deploy.sh'

        last9DeploymentMarker(
          serviceName: 'payments-api',
          environment: 'production',
          eventState: 'stop'
        )
      }
    }
  }
}

// Deploy multiple services in parallel, each with its own deployment window.
// Each last9DeploymentMarker call is independent — run as many as you need.

pipeline {
  agent any
  stages {
    stage('Deploy') {
      parallel {
        stage('API') {
          steps {
            last9DeploymentMarker(
              serviceName: 'api',
              environment: 'production',
              eventState: 'start'
            )
            sh './deploy-api.sh'
            last9DeploymentMarker(
              serviceName: 'api',
              environment: 'production',
              eventState: 'stop'
            )
          }
        }
        stage('Worker') {
          steps {
            last9DeploymentMarker(
              serviceName: 'worker',
              environment: 'production',
              eventState: 'start'
            )
            sh './deploy-worker.sh'
            last9DeploymentMarker(
              serviceName: 'worker',
              environment: 'production',
              eventState: 'stop'
            )
          }
        }
        stage('Scheduler') {
          steps {
            last9DeploymentMarker(
              serviceName: 'scheduler',
              environment: 'production',
              eventState: 'start'
            )
            sh './deploy-scheduler.sh'
            last9DeploymentMarker(
              serviceName: 'scheduler',
              environment: 'production',
              eventState: 'stop'
            )
          }
        }
      }
    }
  }
}

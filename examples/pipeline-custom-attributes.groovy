// Attach custom attributes to the deployment event.
// Useful for tracking version, deploy method, or anything else you query in Last9.

pipeline {
  agent any
  stages {
    stage('Deploy') {
      steps {
        last9DeploymentMarker(
          serviceName:    'payments-api',
          environment:    'production',
          eventState:     'start',
          customAttributes: [
            'deploy_version':      env.BUILD_TAG,
            'deploy_triggered_by': env.BUILD_USER ?: 'ci',
            'deploy_method':       'blue-green'
          ]
        )

        sh './deploy.sh'

        last9DeploymentMarker(
          serviceName: 'payments-api',
          environment: 'production',
          eventState:  'stop',
          customAttributes: [
            'deploy_version': env.BUILD_TAG
          ]
        )
      }
    }
  }
}

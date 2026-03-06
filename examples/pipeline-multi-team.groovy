// Multi-team Jenkins: override org slug and credential per step.
// Use when a single Jenkins serves multiple teams with separate Last9 accounts.

pipeline {
  agent any
  stages {
    stage('Deploy') {
      steps {
        last9DeploymentMarker(
          serviceName:  'payments-api',
          environment:  'production',
          eventState:   'start',
          orgSlug:      'payments-team',
          credentialId: 'last9-token-payments'
        )

        sh './deploy.sh'

        last9DeploymentMarker(
          serviceName:  'payments-api',
          environment:  'production',
          eventState:   'stop',
          orgSlug:      'payments-team',
          credentialId: 'last9-token-payments'
        )
      }
    }
  }
}

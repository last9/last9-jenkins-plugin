/**
 * Multi-endpoint routing: route deploy markers to different Last9 orgs/credentials
 * from one Jenkins instance using global routing profiles.
 *
 * Prerequisites (Manage Jenkins → System → Last9):
 *   Routing profile "acme-primary":
 *     org=acme, credential=last9-token-primary, api=https://app.last9.io
 *   Routing profile "acme-eu":
 *     org=acme-eu, credential=last9-token-eu, api=https://app.last9.io
 */

pipeline {
  agent any

  parameters {
    choice(name: 'DEPLOY_REGION', choices: ['primary', 'eu'])
  }

  environment {
    // Map region to routing profile name (must match global routing profile names)
    LAST9_ROUTING_PROFILE = "${params.DEPLOY_REGION == 'eu' ? 'acme-eu' : 'acme-primary'}"
  }

  stages {
    stage('Deploy') {
      steps {
        withLast9Deployment(
          serviceName:          'payments-api',
          environment:          params.DEPLOY_REGION,
          routingProfileEnvVar: 'LAST9_ROUTING_PROFILE'
        ) {
          sh './deploy.sh'
        }
      }
    }
  }
}

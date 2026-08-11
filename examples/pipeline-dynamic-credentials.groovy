/**
 * pipeline-dynamic-credentials.groovy
 *
 * Demonstrates passing the Last9 org slug and credential ID as build parameters so a
 * single shared Jenkins instance can serve multiple teams or deployment environments
 * without hard-coding secrets in the pipeline script.
 *
 * Priority order for resolving org slug and credential ID:
 *   1. Literal value set directly on the step (orgSlug / credentialId)
 *   2. Value of the named build parameter (orgSlugParam / credentialIdParam)
 *   3. Global default from Manage Jenkins → System → Last9
 */
pipeline {
  agent any

  parameters {
    // The team / job trigger provides these at queue time.
    string(
      name:         'LAST9_ORG_SLUG',
      defaultValue: '',
      description:  'Last9 organization slug (overrides global config)'
    )
    string(
      name:         'LAST9_CRED_ID',
      defaultValue: '',
      description:  'Jenkins Secret-text credential ID containing the Last9 refresh token'
    )
    string(
      name:         'DEPLOY_VERSION',
      defaultValue: 'latest',
      description:  'Artifact version being deployed'
    )
  }

  stages {
    stage('Deploy') {
      steps {
        // The deployment window step resolves org slug and credential from the build
        // parameters named above, falling back to the global plugin config if blank.
        withLast9Deployment(
          serviceName:       'payments-api',
          environment:       'production',
          orgSlugParam:      'LAST9_ORG_SLUG',
          credentialIdParam: 'LAST9_CRED_ID'
        ) {
          echo "Deploying version ${params.DEPLOY_VERSION}…"
          sh './deploy.sh'
        }
      }
    }
  }
}

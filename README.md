# Last9 Jenkins Plugin

Send deployment markers to [Last9](https://last9.io) from Jenkins.

Every deploy fires a change event to Last9 with the commit SHA, branch, build URL, deployer, environment, and service name. Deployment annotations appear on every Last9 dashboard. Correlate latency spikes with deploys in seconds.

Deployments are **windows**, not points. Send `start` when the deploy begins, `stop` when it finishes. Last9 shows the full window — you see performance during the rollout, not just after.

## Setup

**1. Create a credential**

**Manage Jenkins → Credentials** → add a **Secret text** credential. The secret is your Last9 refresh token from `app.last9.io → Settings → API Tokens`.

**2. Configure the plugin**

**Manage Jenkins → System → Last9**:

- **Organization Slug** — your org identifier from the Last9 URL (e.g. `acme`)
- **API Credential** — the credential you just created
- **Default Data Source Name** — optional
- **Additional Environment Variables** — comma-separated env var names to auto-capture (e.g. `BUILD_TAG,DEPLOY_REGION`)
- **Additional Build Parameters** — comma-separated build parameter names to auto-capture (password params never exported)

Hit **Test Connection** to verify before saving.

## Pipeline

### Deployment window (recommended)

Use the `withLast9Deployment` block — the stop marker is guaranteed even on failure:

```groovy
pipeline {
  agent any
  stages {
    stage('Deploy') {
      steps {
        withLast9Deployment(serviceName: 'payments-api', environment: 'production') {
          sh './deploy.sh'
        }
      }
    }
  }
}
```

### Manual start/stop

```groovy
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
```

### Single marker

```groovy
post {
  success {
    last9DeploymentMarker serviceName: 'payments-api', environment: 'production'
  }
}
```

`eventState` defaults to `stop`.

### All options

```groovy
last9DeploymentMarker(
  serviceName:    'payments-api',       // required
  environment:    'production',         // recommended
  eventState:     'start',              // 'start' or 'stop' (default: 'stop')
  eventName:      'deployment',         // default: 'deployment'
  dataSourceName: 'payments-ds',        // overrides global default
  customAttributes: [
    'deploy_version': '1.4.2',
    'deploy_triggered_by': 'release-bot'
  ],
  // Override global config per-step (useful for multi-team Jenkins)
  orgSlug:           'acme',
  orgSlugParam:      'LAST9_ORG_SLUG',
  orgSlugEnvVar:     'LAST9_ORG',
  credentialId:      'last9-token-prod',
  credentialIdParam: 'LAST9_CRED_ID',
  credentialIdEnvVar: 'LAST9_CREDENTIAL_ID',
  apiBaseUrl:        'https://app.last9.io',
  apiBaseUrlParam:   'LAST9_API_URL',
  routingProfile:    'acme-eu',
  routingProfileParam: 'LAST9_ROUTING_PROFILE'
)
```

### Dynamic credentials via build parameters

Pass the org slug and credential ID as build parameters — useful when a shared Jenkins instance serves multiple teams or environments:

```groovy
pipeline {
  agent any
  parameters {
    string(name: 'LAST9_ORG_SLUG', defaultValue: '', description: 'Last9 org slug')
    string(name: 'LAST9_CRED_ID',  defaultValue: '', description: 'Last9 Jenkins credential ID')
  }
  stages {
    stage('Deploy') {
      steps {
        withLast9Deployment(
          serviceName:       'payments-api',
          environment:       'production',
          orgSlugParam:      'LAST9_ORG_SLUG',
          credentialIdParam: 'LAST9_CRED_ID'
        ) {
          sh './deploy.sh'
        }
      }
    }
  }
}
```

See [examples/pipeline-dynamic-credentials.groovy](examples/pipeline-dynamic-credentials.groovy) for a complete example.

### Multi-org and multi-endpoint routing

Define **routing profiles** in **Manage Jenkins → System → Last9** (org + credential + API URL per profile). Select per job/step via `routingProfile`, `routingProfileParam`, or `routingProfileEnvVar`. Individual `orgSlug`, `credentialId`, and `apiBaseUrl` overrides (literal, param, or env var) still win over the profile.

Freestyle jobs can pick a profile from the dropdown — no Pipeline Groovy required.

See [examples/pipeline-multi-endpoint-routing.groovy](examples/pipeline-multi-endpoint-routing.groovy).

Connection resolution order for each field:

1. Literal value on the step
2. Build parameter
3. Environment variable
4. Selected routing profile
5. Global default

### Auto-captured attributes

These are wired up automatically:

| Attribute | Source |
|---|---|
| `scm_commit_sha` | `$GIT_COMMIT` |
| `scm_branch` | `$GIT_BRANCH` |
| `scm_url` | `$GIT_URL` |
| `scm_author` | `$GIT_AUTHOR_NAME` |
| `jenkins_job_name` | build metadata |
| `jenkins_build_number` | build metadata |
| `jenkins_build_url` | build metadata |
| `jenkins_build_result` | build metadata |
| `jenkins_build_start_time` | build start time (ISO-8601) |
| `jenkins_build_end_time` | build end time (ISO-8601, only when complete) |
| `jenkins_build_duration_ms` | build duration in milliseconds |
| `jenkins_build_user` | triggered-by user |
| `jenkins_node_name` | executor node |
| `build_param_<name>` | allowlisted build parameters (name lowercased) |
| `env_<name>` | env vars from the global allowlist (name lowercased) |

### Deployment window attributes

When using `withLast9Deployment` or paired `start`/`stop` markers, Last9 additionally receives:

| Attribute | Description |
|---|---|
| `deployment_id` | UUID assigned at `start`, echoed at `stop` |
| `deployment_window_duration_ms` | elapsed milliseconds between `start` and `stop` |

## Freestyle

### Deployment window (start + stop)

Add **Track Last9 Deployment Window (start + stop)** in the **Build Environment** section. Sends `start` before the first build step, `stop` after the last — including on failure.

### Single marker

Add **Send Last9 Deployment Marker** as a post-build action. Configure when to send:

- **Send on Success** (default: on)
- **Send on Failure** (default: off)
- **Send on Unstable** (default: off)
- **Send on Aborted** (default: off)

## Error handling

API failures never fail your build. The plugin logs a warning and moves on. Deployments ship; observability is best-effort.

Transient failures (5xx, network timeouts) are retried up to 3 times with exponential backoff.

## Multi-service pipelines

```groovy
stage('Deploy Services') {
  parallel {
    stage('API') {
      steps {
        last9DeploymentMarker serviceName: 'api', environment: 'production', eventState: 'start'
        sh './deploy-api.sh'
        last9DeploymentMarker serviceName: 'api', environment: 'production', eventState: 'stop'
      }
    }
    stage('Worker') {
      steps {
        last9DeploymentMarker serviceName: 'worker', environment: 'production', eventState: 'start'
        sh './deploy-worker.sh'
        last9DeploymentMarker serviceName: 'worker', environment: 'production', eventState: 'stop'
      }
    }
  }
}
```

## License

MIT. See [LICENSE](LICENSE).

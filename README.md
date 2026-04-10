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
    'deploy.version': '1.4.2',
    'deploy.triggered_by': 'release-bot'
  ],
  // Override global config per-step (useful for multi-team Jenkins)
  orgSlug:      'acme',
  credentialId: 'last9-token-prod'
)
```

### Auto-captured attributes

These are wired up automatically:

| Attribute | Source |
|---|---|
| `scm.commit_sha` | `$GIT_COMMIT` |
| `scm.branch` | `$GIT_BRANCH` |
| `scm.url` | `$GIT_URL` |
| `scm.author` | `$GIT_AUTHOR_NAME` |
| `jenkins.job_name` | build metadata |
| `jenkins.build_number` | build metadata |
| `jenkins.build_url` | build metadata |
| `jenkins.build_result` | build metadata |
| `jenkins.build_duration_ms` | build metadata |
| `jenkins.build_user` | triggered-by user |
| `jenkins.node_name` | executor node |

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

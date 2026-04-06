# Last9 Jenkins Plugin

Send deployment markers to [Last9](https://last9.io) from Jenkins. When a deploy goes sideways, you want to know exactly when it landed — this does that.

## What it does

Every deploy fires a change event to Last9 with the commit SHA, branch, build URL, deployer, environment, and service name. You get a deployment annotation on every Last9 dashboard. Correlate latency spikes with deploys without digging through logs.

The plugin models deployments as **windows**, not points. You send a `start` event when the deploy begins and a `stop` event when it finishes. Last9 shows you the entire window, so you can see how performance changed during a rollout, not just after it.

## Installation

Install from the Jenkins Plugin Center: search for **Last9** in **Manage Jenkins → Plugins**.

## Setup

**1. Create a credential**

In **Manage Jenkins → Credentials**, add a **Secret text** credential. The secret is your Last9 refresh token, found at `app.last9.io → Settings → API Tokens`.

**2. Configure the plugin**

Go to **Manage Jenkins → System → Last9**:

- **Organization Slug** — your org identifier from the Last9 URL (e.g. `acme`)
- **API Credential** — the credential you just created
- **Default Data Source Name** — optional, ties events to a specific Last9 data source

Hit **Test Connection** to verify before saving.

## Pipeline usage

### The standard pattern: start/stop window

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

        // your actual deploy steps here
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

### Single marker (post-deploy)

If you just want a point-in-time marker after the deploy finishes:

```groovy
post {
  success {
    last9DeploymentMarker serviceName: 'payments-api', environment: 'production'
  }
}
```

`eventState` defaults to `stop`, so this sends a single stop event — which is the right thing when you only have one step.

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

### What gets captured automatically

You don't need to wire these up yourself:

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

## Freestyle jobs

### Deployment window (start + stop)

Add **Track Last9 Deployment Window (start + stop)** as a build wrapper (the "Build Environment" section in the job config). This is the recommended option. It sends a `start` event before your first build step runs and a `stop` event after the last step finishes — even if the build fails.

Configure the service name and environment. That's it.

### Single marker (stop only)

Add **Send Last9 Deployment Marker** as a post-build action. Sends a single `stop` event after the build completes.

Configure when to send:

- **Send on Success** (default: on)
- **Send on Failure** (default: off)
- **Send on Unstable** (default: off)
- **Send on Aborted** (default: off)

## Error handling

API failures never fail your build. If Last9 is unreachable, the plugin logs a warning and moves on. Deployments ship; observability is best-effort.

The plugin retries transient failures (5xx, network timeouts) up to 3 times with exponential backoff before giving up.

## Multi-environment, multi-service pipelines

Each `last9DeploymentMarker` step is independent. Run as many as you need:

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

## Requirements

- Jenkins 2.462.3 or newer
- Java 17 or newer
- A Last9 account with API token access

## License

MIT. See [LICENSE](LICENSE).

# Contributing

Bug reports and pull requests are welcome.

## Setup

```bash
git clone https://github.com/last9/last9-jenkins-plugin.git
cd last9-jenkins-plugin
mvn verify
```

Requires Java 17+ and Maven 3.9+.

## Run locally

```bash
mvn hpi:run
```

Opens at `http://localhost:8080/jenkins`. Plugin reloads on rebuild.

## Tests

```bash
mvn test
```

JUnit 4 + Mockito. Integration tests spin up a real Jenkins via `JenkinsRule` — slower but they catch what unit tests miss.

## Rules

- API errors must never fail a build. Observability is best-effort, deployments are not.
- Auto-captured context (Git, build metadata) degrades gracefully when the source plugin is absent.
- New config fields need form validation in the descriptor and an entry in the Jelly view.
- Test the failure path. It matters more than the happy path.

## Pull requests

1. Fork and create a branch from `main`
2. Add tests
3. Run `mvn verify` (SpotBugs + tests)
4. Open the PR

## Bug reports

Open an issue at https://github.com/last9/last9-jenkins-plugin/issues with:
- Jenkins version and plugin version
- What you expected vs. what happened
- Relevant build log (redact credentials)

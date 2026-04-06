# Contributing

Bug reports and pull requests are welcome.

## Setup

```bash
git clone https://github.com/last9/last9-jenkins-plugin.git
cd last9-jenkins-plugin
mvn verify
```

Java 17+ and Maven 3.9+ required.

## Run locally

```bash
mvn hpi:run
```

Jenkins starts at `http://localhost:8080/jenkins`. The plugin is hot-loaded automatically when you rebuild.

## Run the tests

```bash
mvn test
```

Tests use JUnit 4 and Mockito. Integration tests spin up a real Jenkins instance via `JenkinsRule` — they're slower but they catch things unit tests miss.

## Making changes

- Keep the plugin non-fatal: API errors must never fail a build. Observability is best-effort.
- Auto-captured context (git, build metadata) should degrade gracefully when the source plugin isn't installed.
- New configuration fields need form validation in the descriptor and a corresponding entry in the Jelly view.
- Test the happy path and the failure path. The failure path matters more.

## Submitting a pull request

1. Fork the repo and create a branch from `main`
2. Make your changes with tests
3. Run `mvn verify` — it runs SpotBugs and tests
4. Open the PR

We'll review it. If it's good, we'll merge it.

## Reporting bugs

Open an issue at https://github.com/last9/last9-jenkins-plugin/issues with:
- Jenkins version
- Plugin version
- What you expected vs. what happened
- Relevant build log output (redact credentials)

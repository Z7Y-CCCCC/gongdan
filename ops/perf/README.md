# API performance and smoke tests

## What is covered

- `ApiRouteSmokeTest` discovers controller mappings at runtime and exercises every mapped route with authentication, malformed input, missing resources, and common request payloads.
- `api-load-test.mjs` is a dependency-free Node.js load runner for the high-frequency HTTP read and mixed read/write paths.
- The `login` scenario isolates password verification and persistent session rotation from ordinary API traffic.

The route smoke test intentionally allows expected `2xx`, `4xx`, and `410` responses for empty or invalid test data. Any `5xx` response, route count drift, or load error is a failure.

## Run the route smoke test

```powershell
Set-Location backend
mvn.cmd -B -Dtest=ApiRouteSmokeTest test
```

The test uses the H2 test profile and does not touch the local MySQL database.

## Run HTTP load tests

Start an isolated backend instance first, then run from the repository root:

```powershell
node ops/perf/api-load-test.mjs --base-url=http://127.0.0.1:18081 --scenario=read --concurrency=10 --duration=30 --output=output/perf/read-c10.json
node ops/perf/api-load-test.mjs --base-url=http://127.0.0.1:18081 --scenario=mixed --concurrency=20 --duration=45 --output=output/perf/mixed-c20.json
node ops/perf/api-load-test.mjs --base-url=http://127.0.0.1:18081 --scenario=read --concurrency=50 --duration=45 --output=output/perf/read-c50.json
node ops/perf/api-load-test.mjs --base-url=http://127.0.0.1:18081 --scenario=login --concurrency=5 --duration=20 --output=output/perf/login-c5.json
```

Defaults are designed for the isolated `PERF` factory and redact the password in JSON reports. Set `--max-error-rate=0` for a zero-error gate and adjust `--p95-limit-ms` to match the environment's service-level objective.

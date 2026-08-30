# API pressure and smoke test report

Date: 2026-08-30
Target: isolated backend on `127.0.0.1:18081`, MySQL `heihu_workorder_perf` on port `3308`, Redis database `15`
Scope: backend API routes, frontend build, mobile H5 build, persistence and cache behavior

## Result

The final verification passed. All `177` runtime controller mappings were exercised by the authenticated route smoke test with no `5xx` response. The route status distribution was `51 x 200`, `1 x 204`, `78 x 400`, `43 x 404`, `3 x 409`, and `1 x 410`; the non-`2xx` responses are expected validation or missing-resource outcomes from the negative smoke cases.

| Scenario | Concurrency | Duration | Requests | Errors | RPS | P50 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Read | 10 | 30s | 15,160 | 0 | 504 | 17.2ms | 34.8ms | 55.7ms |
| Mixed reads and preference writes | 20 | 45s | 21,453 | 0 | 476 | 39.9ms | 63.2ms | 81.3ms |
| Read | 50 | 45s | 24,432 | 0 | 542 | 89.9ms | 117.3ms | 151.0ms |
| Login and session rotation | 5 | 20s | 543 | 0 | 26.9 | 166.7ms | 227.8ms | 665.8ms |

Reports are retained under `output/perf/` when that directory is available.

## Checks after load

- Actuator health remained `UP`.
- MySQL reported `151` maximum connections and `31` connected connections during the post-load check.
- No duplicate preference keys were created by mixed writes.
- Inbound/outbound receipt item and product orphan checks were zero.
- Redis contained the expected hot-cache entries; the previous `ArrayList$SubList` deserialization warning did not recur.
- After concurrent login, the persistent session invariant was verified directly in MySQL: `1` active session for the test user and no user with more than one active session.
- The observed backend process remained alive without OOM or connection-pool exhaustion; working set snapshots were approximately `560MB` before the higher-concurrency run, `708MB` at its start, and `629MB` after the load sequence.

## Defects found and fixed during testing

1. The smoke runner initially used zero-based page parameters and two incorrect query names. The runner now follows the API contract (`page=1`, `moduleName`, and `status`).
2. Inventory detail pagination returned an internal `SubList` view, which made Redis cache values non-deserializable. Results now return a concrete `ArrayList`.
3. Concurrent logins for one account could leave multiple valid persistent sessions. Session invalidation now uses a database bulk update and the user row lock path; the final MySQL check verified one active session.
4. Malformed request binding previously reached the generic exception handler and could become `500`. It now returns a controlled `400` response.
5. Department and work-order category validation and missing-category handling were tightened to avoid null-driven `500` responses.
6. System monitor online-user loading was changed to batch-load departments, and notification “mark all read” was made portable across H2 and MySQL.

## Regression verification

- Backend Maven suite: `646` tests, `0` failures, `0` errors.
- Frontend Vitest: `7` files, `47` tests, all passed.
- Frontend production build: passed.
- Mobile typecheck and H5 build: passed.

The load runner intentionally stresses safe read paths and an idempotent preference write. Destructive CRUD, file upload, and plugin proxy operations are covered by the all-route smoke test and the existing service/integration suites rather than being repeated concurrently against shared records.

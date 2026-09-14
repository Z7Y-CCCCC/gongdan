# Combined desktop and mobile image

The Compose `frontend` service builds this Dockerfile from the repository root.
It includes both desktop assets and the H5 mobile client under `/mobile/`, with
same-origin `/api` access and QR links pointing at the deployed mobile entry.
The Dockerfile-specific ignore list excludes environment files, node_modules,
build outputs, browser sessions and unrelated workspace data from the context.

The optional Superset analysis page adds `APP_ANALYTICS_ALLOWED_ORIGINS` to the
same frontend build-time CSP allow-list. The backend-only Superset credentials
(`APP_ANALYTICS_SUPERSET_USERNAME` and `APP_ANALYTICS_SUPERSET_PASSWORD`) are
passed only to the backend container.

Both asset build stages use Node.js 22. The mobile dependency lock also references
the small local adapters in `mobile/vendor/`; these source files must be copied
before `npm ci`. They are build-time code and are not copied into the final Nginx
image. Keep the Dockerfile's vendor-copy step when customizing the build.

Optional `SPRING_REDIS_PASSWORD` is applied to Redis startup, its healthcheck and
the backend together. Do not publish Redis directly on a host/public port.

Configuration-only regression checks (no containers started):

```sh
docker compose --env-file .env.deploy.example config --format json | node ops/test-compose-config.mjs
SPRING_REDIS_PASSWORD=configuration-test-only docker compose --env-file .env.deploy.example config --format json | node ops/test-compose-config.mjs
```

Use actual deployment secrets for a real deployment, never the example values.

## Plugin iframe policy

`APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS` is the single explicit origin list used by
the backend and the frontend image build. It defaults to
`http://localhost:9101,http://localhost:9102` for local plugins. Production plugin
pages must use separate origins, for example
`https://quality.workorder.example.com,https://inspection.workorder.example.com`;
do not put the main application's origin in this list. An HTTPS main site also
needs HTTPS plugin pages to avoid browser mixed-content blocking.

Both this combined Dockerfile and `frontend/Dockerfile` run
`frontend/scripts/render-nginx.mjs` during their Node build stage. The renderer
rejects wildcard hosts, CSP keywords, credentials, paths, invalid ports and
configuration injection, then produces an explicit `frame-src` map. The map
removes any plugin with the current request's authority (including default-port
and HTTP-to-HTTPS variants); it never adds `'self'` or `*`. Other security headers
are retained. `/mobile/` has its own `frame-src 'none'` policy. TLS reverse proxies
must preserve the original browser `Host` header for this additional exclusion.
The UI's independent same-origin plugin rejection remains in place.

This is **build-time configuration**, not a runtime `docker run -e` setting.
After changing the origin list in `.env`, rebuild the frontend image and recreate
both frontend/backend containers so their policies agree:

```sh
docker compose build frontend
docker compose up -d backend frontend
```

For the standalone desktop image, pass the same list explicitly:

```sh
docker build --build-arg APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS=https://quality.example.com,https://inspection.example.com -t gongdan-frontend ./frontend
```

`frontend/nginx.conf` is now a template. To use it outside these Dockerfiles,
generate the final config first with Node and the same environment value:

```sh
node frontend/scripts/render-nginx.mjs frontend/nginx.conf /tmp/gongdan-nginx.conf
node --test frontend/scripts/test-plugin-csp.mjs
```

For browser regression checks without Docker or real plugins, run
`node frontend/scripts/plugin-csp-browser-fixture.mjs`, open the printed URL,
then `/mobile/`, and evaluate `await window.assertPluginCsp()` on each page. It
uses the generated production policies, verifies that two cross-origin plugins
load on desktop, and that unlisted/main-origin frames and all mobile frames are
blocked. All fixture servers use ephemeral loopback ports; stop the fixture
afterward. CSP rejection messages for the intentionally blocked frames are
expected during this test.

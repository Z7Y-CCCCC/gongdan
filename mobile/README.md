# Mobile work-order client

H5 signs in through `/api/auth/login` with the factory/account/password contract.
WeChat Mini Program builds use WeChat authorization and account binding; no
fabricated ticket is used after a failed authorization request. Production Mini
Programs still require a real app ID and an absolute HTTPS `VITE_API_BASE_URL`.

Use Node.js 22 LTS (22.12 or newer) or Node.js 24 LTS. CI and the build
containers use Node.js 22. After `npm ci`, run:

```text
npm test
npm run build
npm run build:mp-weixin
```

The regression suite compiles the real TypeScript and Vue setup scripts, with
isolated uni-app adapters. It covers authentication races, uploads, reporter
permissions, duplicate writes, draft isolation, pagination and form validation.
It does not replace testing on a physical WeChat device.

For isolated browser QA, build H5 and run `node test/browser-fixture.mjs`. The
printed loopback URL serves the app with the production CSP and an in-memory
fake backend. Requests never reach a real database or account. The fixture also
supports `/mobile/` for a production-base build. Stop it when QA is complete.

List reads paginate up to 5,000 records. Larger datasets display the loaded and
total counts, explicitly qualifying statistics; use a narrower date range or
the administrative client for additional records. Drafts are scoped to the
factory/user identity. Legacy unscoped drafts are not automatically restored
into another account and are not deleted by the migration.

## Dependency security baseline (2026-09-12)

The full audit, **including development dependencies**, now reports zero known
vulnerabilities. CI runs `npm audit --audit-level=low --registry=https://registry.npmjs.org`;
there are no advisory exclusions or `--omit=dev` loopholes. CI also runs
`npm ls --all` after the builds, so a valid install-cache snapshot cannot hide
an inconsistent actual dependency tree. The explicit registry
is necessary because the configured package mirror has no audit endpoint.
Audit results are a dated known-advisory check, not a guarantee about unknown
vulnerabilities or our local adapter code.

The uni-app compiler remains `3.0.0-4020920240930001` and the declared `vue`
dependency remains `3.4.34`. H5 still aliases DCloud's bundled private runtime,
whose embedded Vue version is `3.4.21`; this fork was not rewritten or upgraded.
This project validates Vite `6.4.3`, esbuild `0.25.12`,
plugin-vue `5.2.4`, plugin-vue-jsx `4.2.0`, and plugin-legacy `6.1.1` together;
this is not a claim that DCloud officially supports this combination. The
pre-existing `3.5.34` compiler helpers used by Vue's TypeScript/JSX tooling remain
separate from the pinned standard Vue/compiler packages and uni's private H5
runtime. The two Vue helper overrides select `~3.4.0`, which includes both the
original `3.4.21` and the replacement `3.4.34` but excludes `3.5.34`. Including
the replacement in the selector also keeps npm's actual-tree reload consistent
after its hidden install-cache lock becomes stale. This does not falsely claim
that every compiler helper uses the same version.

- Vite/esbuild upgrades address the Windows file-access and development-server
  advisories. `server.fs.strict` is explicitly enabled because the legacy uni
  plugin otherwise disables it. Public assets are explicitly served from
  `public/`, including the CSP-compatible bootstrap script. H5 keeps its existing
  manual bootstrap rather than enabling a second legacy H5 compiler pipeline.
- The vulnerable upstream `adm-zip` implementation is actually replaced by
  [`@gongdan/uni-zip-adapter`](vendor/uni-zip/README.md), not relabeled as an
  imaginary upstream fix. It uses fflate for ZIP writing and bounded native zlib
  for reading, with complete metadata/path/type/CRC checks before extraction.
  Only its documented ZIP32 subset and uni-facing synchronous APIs are supported.
  Linked paths, unsupported formats and size/path limits fail closed. Use an
  exclusively owned local cache; this is not a sandbox against concurrent local
  attackers or a universal cloud-ZIP decoder. Multi-file extraction is not an
  atomic transaction.
- Jimp `0.22.12` replaces the old phin 2 image-loading chain. On the supported
  Node runtime it uses native Fetch; do not override `globalThis.fetch` with a
  legacy polyfill. Remote reads use Fetch's `redirect` and an explicit
  `signal: AbortSignal.timeout(milliseconds)`, not phin's old `timeout`, `core`,
  or `followRedirects` options. Embedded URL credentials are rejected. Bare
  `Jimp.read(url)` does not gain a total-request deadline; callers must provide
  a signal for untrusted remote URLs. uni itself uses Buffer-to-bitmap QR reads,
  which are covered by a real QR decode test. Fixed phin `3.7.1` still appears
  in a separate font-loading dependency, not the image HTTP path.
- Jimp's old file-type parser is replaced by a
  [small CommonJS bridge](vendor/uni-file-type/README.md) to unmodified official
  `file-type@21.3.4`, covering both the ASF non-termination and ZIP-probing
  inflation fixes. The only compatibility API is asynchronous `fromBuffer`.
- Express keeps its compatible `4.22.2` line with patched `qs@6.16.0` and
  `path-to-regexp@0.1.13`. Existing i18n, JPEG, Babel and related pins remain.

Keep `vendor/` with `package.json` and the lockfile. The local dependencies are
root file aliases reused by overrides; do not replace them with parent-relative
overrides in nested packages. The combined Docker build copies `vendor/` before
`npm ci` and copies only built static assets into the Nginx runtime image.

`npm test` runs every `test/*.test.mjs` through a cross-platform runner, including
real loopback HTTP/TLS redirect checks, malformed-image workers, ZIP link/budget
regressions and actual Vite development-module/source-map/HMR/security tests.
It never starts the long-lived manual `browser-fixture.mjs` automatically.

Development and preview bind to `127.0.0.1` by default. For necessary LAN
debugging, explicitly set `VITE_DEV_HOST` to a trusted interface and apply a
firewall. Do not expose development servers or esbuild serve endpoints to
untrusted networks. Upstream npm/Sass/vue-router deprecation messages may remain;
they are not hidden and are distinct from known vulnerability advisories.

See the [dependency remediation and verification report](../docs/DEPENDENCY_HARDENING_2026-09-12.md)
for evidence and the production acceptance work that remains outstanding.

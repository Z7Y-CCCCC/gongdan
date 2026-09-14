import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { DEFAULT_PLUGIN_ORIGINS, frameSourcesForHost, parsePluginOrigins, renderNginx } from './render-nginx.mjs'

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const root = path.resolve(frontend, '..')
const template = readFileSync(path.join(frontend, 'nginx.conf'), 'utf8')

test('default policy allows both explicit cross-origin local plugins', () => {
  const origins = parsePluginOrigins()
  assert.deepEqual(origins, ['http://localhost:9101', 'http://localhost:9102'])
  assert.equal(frameSourcesForHost(origins, 'localhost:8080'), DEFAULT_PLUGIN_ORIGINS.replace(',', ' '))
})

test('origins are trimmed, canonicalized and deduplicated without adding sources', () => {
  assert.deepEqual(parsePluginOrigins(' HTTPS://Quality.Example.test:443 ,https://quality.example.test,http://127.0.0.1:9101'),
    ['https://quality.example.test', 'http://127.0.0.1:9101'])
  assert.deepEqual(parsePluginOrigins('http://127.1:9101,http://127.0.0.1:9101'), ['http://127.0.0.1:9101'])
})

test('CSP/Nginx injection, schemes, wildcard hosts, credentials and paths fail closed', () => {
  for (const invalid of [
    '*', "'self'", 'https:', 'javascript:alert(1)', 'data:text/html,test',
    'https://*.example.test', 'https://name:secret@example.test', 'https://example.test/',
    'https://example.test/path', 'https://example.test?query', 'https://example.test#hash',
    'https://example.test;frame-src *', 'https://example.test\nscript-src *',
    'https://example.test"; include /tmp/untrusted;', 'https://$host',
    'https://example.test:65536', 'https://example.test:0', 'https://example.test,',
    'https://exa_mple.test', 'https://example..test', 'https://%65xample.test'
  ]) assert.throws(() => parsePluginOrigins(invalid), undefined, invalid)
})

test('origin-list bounds and empty disable policy are explicit', () => {
  assert.throws(() => parsePluginOrigins('a'.repeat(4097)))
  assert.throws(() => parsePluginOrigins(Array.from({ length: 33 }, (_, i) => `https://p${i}.example.test`).join(',')))
  assert.equal(frameSourcesForHost(parsePluginOrigins(''), 'localhost:8080'), "'none'")
})

test('same authority is excluded at request time including case and TLS proxy variants', () => {
  const origins = parsePluginOrigins('http://localhost:9101,http://localhost:9102,https://localhost:9101')
  assert.equal(frameSourcesForHost(origins, 'LOCALHOST:9101'), 'http://localhost:9102')
  assert.equal(frameSourcesForHost(origins, 'localhost:9102'), 'http://localhost:9101 https://localhost:9101')
})

test('default ports and HTTP-to-HTTPS CSP upgrades cannot permit a same-origin frame', () => {
  const origins = parsePluginOrigins('https://app.example.test,http://app.example.test:443,https://isolated.example.test')
  for (const host of ['app.example.test', 'app.example.test:80', 'app.example.test:443']) {
    assert.equal(frameSourcesForHost(origins, host), 'https://isolated.example.test')
  }
  assert.equal(frameSourcesForHost(parsePluginOrigins('https://app.example.test'), 'app.example.test'), "'none'")
})

test('IPv6 literals use canonical authorities in the same-origin exclusion', () => {
  const origins = parsePluginOrigins('http://[0:0:0:0:0:0:0:1]:9101,https://plugin.example.test')
  assert.equal(frameSourcesForHost(origins, '[::1]:9101'), 'https://plugin.example.test')
})

test('rendered Nginx contains an explicit map, no placeholder or broad frame source', () => {
  const rendered = renderNginx(template)
  assert.match(rendered, /map_hash_bucket_size 512;/)
  assert.match(rendered, /map \$http_host \$plugin_frame_src \{/)
  assert.match(rendered, /default "http:\/\/localhost:9101 http:\/\/localhost:9102";/)
  assert.match(rendered, /"localhost:9101" "http:\/\/localhost:9102";/)
  assert.doesNotMatch(rendered, /__PLUGIN_FRAME_SOURCE_MAP__/)
  assert.doesNotMatch(rendered, /frame-src (?:\*|'self'|https?:;)/)
  const reservedHosts = renderNginx(template, 'http://include,http://default')
  assert.ok(reservedHosts.includes('"\\\\include"'))
  assert.ok(reservedHosts.includes('"\\\\default"'))
  assert.throws(() => renderNginx('server {}'))
})

test('mobile rejects frames while desktop keeps all existing restrictive directives', () => {
  const rendered = renderNginx(template)
  const mobile = rendered.match(/location \/mobile\/ \{([\s\S]*?)\n\s*\}/)?.[1]
  assert.ok(mobile)
  assert.match(mobile, /frame-src 'none'/)
  assert.doesNotMatch(mobile, /\$plugin_frame_src/)
  for (const policy of rendered.matchAll(/add_header Content-Security-Policy "([^"]+)" always;/g)) {
    for (const directive of ["script-src 'self'", "connect-src 'self'", "object-src 'none'", "frame-ancestors 'none'", "form-action 'self'"]) {
      assert.ok(policy[1].includes(directive), directive)
    }
    assert.ok(!policy[1].includes("script-src 'self' 'unsafe-inline'"))
  }
})

test('standalone and combined images build and copy the validated configuration', () => {
  for (const file of ['frontend/Dockerfile', 'deploy/docker/Dockerfile']) {
    const dockerfile = readFileSync(path.join(root, file), 'utf8')
    assert.match(dockerfile, /ARG APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS=http:\/\/localhost:9101,http:\/\/localhost:9102/)
    assert.match(dockerfile, /RUN node scripts\/render-nginx\.mjs nginx\.conf \/tmp\/gongdan-nginx\.conf/)
    assert.match(dockerfile, /COPY --from=(?:build|frontend-build) \/tmp\/gongdan-nginx\.conf \/etc\/nginx\/conf\.d\/default\.conf/)
    assert.doesNotMatch(dockerfile, /COPY (?:frontend\/)?nginx\.conf \/etc\/nginx\/conf.d/)
  }
})

test('combined image includes the local mobile adapter before dependency installation', () => {
  const dockerfile = readFileSync(path.join(root, 'deploy/docker/Dockerfile'), 'utf8')
  const mobileStage = dockerfile.split('AS mobile-build')[1]?.split('\nFROM ')[0]
  assert.ok(mobileStage, 'combined image must retain its mobile build stage')
  const copyVendor = mobileStage.indexOf('COPY mobile/vendor/ ./vendor/')
  const install = mobileStage.indexOf('RUN npm ci --include=optional')
  assert.ok(copyVendor >= 0 && copyVendor < install, 'local file dependencies must exist before npm ci')
  const runtimeStage = dockerfile.split('\nFROM nginx:')[1]
  assert.ok(runtimeStage)
  assert.doesNotMatch(runtimeStage, /COPY .*\/(?:vendor|node_modules)(?:\/|\s)/)
})

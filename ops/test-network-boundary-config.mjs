import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const read = relative => readFileSync(new URL(`../${relative}`, import.meta.url), 'utf8').replace(/^\uFEFF/, '')
test('network trust and report TLS flags reach the maintained backend, not frontend build arguments', () => {
  const compose = read('docker-compose.yml')
  const backend = compose.split(/^  backend:/m)[1].split(/^  frontend:/m)[0]
  assert.match(backend, /APP_NETWORK_TRUSTED_PROXIES: \$\{APP_NETWORK_TRUSTED_PROXIES:-\}/)
  assert.match(backend, /APP_UREPORT_COOKIE_SECURE: \$\{APP_UREPORT_COOKIE_SECURE:-false\}/)
  const rest = compose.split(/^  frontend:/m)[1]
  assert.doesNotMatch(rest, /APP_NETWORK_TRUSTED_PROXIES|APP_UREPORT_COOKIE_SECURE/)
  const properties = read('backend/src/main/resources/application.properties')
  assert.match(properties, /app\.network\.trusted-proxies=\$\{APP_NETWORK_TRUSTED_PROXIES:\}/)
  assert.match(properties, /app\.ureport\.cookie-secure=\$\{APP_UREPORT_COOKIE_SECURE:false\}/)
})
test('deployment example retains explicit deny-by-default proxy trust and local HTTP compatibility', () => {
  const example = read('.env.deploy.example')
  assert.match(example, /^APP_NETWORK_TRUSTED_PROXIES=\s*$/m)
  assert.match(example, /^APP_UREPORT_COOKIE_SECURE=false\s*$/m)
  const notes = read('DEPLOYMENT.md')
  assert.match(notes, /APP_UREPORT_COOKIE_SECURE=true/)
  assert.match(notes, /actual, controlled proxy socket peers/)
})
test('unmaintained credentialed demo is conspicuously local-only and publishes only loopback ports', () => {
  const legacy = read('deploy_compose.yml')
  assert.match(legacy.slice(0, 400), /LEGACY LOCAL DEMO ONLY/)
  assert.match(legacy.slice(0, 400), /Do not expose it/)
  const bindings = []
  let inPorts = false, indentation = 0
  for (const line of legacy.split(/\r?\n/)) {
    const match = /^(\s*)ports:\s*$/.exec(line)
    if (match) { inPorts = true; indentation = match[1].length; continue }
    if (!line.trim()) continue
    if (inPorts && line.search(/\S/) <= indentation) inPorts = false
    if (inPorts) bindings.push(line.trim().replace(/^[-\s]+/, '').replace(/^['"]|['"]$/g, ''))
  }
  assert.ok(bindings.length > 0)
  assert.ok(bindings.every(binding => /^127\.0\.0\.1:\d+:\d+$/.test(binding)),
    'Every legacy demo port must stay bound to IPv4 loopback')
})

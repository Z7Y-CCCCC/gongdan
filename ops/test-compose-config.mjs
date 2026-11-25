// Validate a rendered Compose document from stdin; never starts containers.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const args = process.argv.slice(2)
assert.ok(args.length <= 1 && args.every(value => ['--external-data-defaults', '--external-data-from-env'].includes(value)),
  'Use either --external-data-defaults or --external-data-from-env to check external data configuration')
const config = JSON.parse(readFileSync(0, 'utf8').replace(/^\uFEFF/, ''))
const redis = config.services.redis
const backend = config.services.backend
for (const name of ['APP_NETWORK_TRUSTED_PROXIES', 'APP_UREPORT_COOKIE_SECURE']) {
  assert.ok(Object.hasOwn(backend.environment, name) && typeof backend.environment[name] === 'string',
    `Backend must explicitly receive ${name}`)
}
assert.ok(['true', 'false'].includes(backend.environment.APP_UREPORT_COOKIE_SECURE),
  'Report cookie TLS override must be an explicit boolean')
assert.equal(redis.environment.REDIS_PASSWORD, backend.environment.SPRING_REDIS_PASSWORD,
  'Redis and backend must use the same configured password')
const command = redis.command.join(' ')
assert.match(command, /--requirepass\s+"\$+REDIS_PASSWORD"/, 'Redis startup must apply a nonempty configured password')
assert.match(command, /exec docker-entrypoint\.sh redis-server/, 'Redis startup must retain the image user/permission entrypoint')
assert.match(redis.healthcheck.test.join(' '), /REDISCLI_AUTH/, 'The healthcheck must authenticate when a password is configured')
assert.match(redis.healthcheck.test.join(' '), /PONG/, 'The healthcheck must verify the actual ping response')
assert.equal(redis.ports, undefined, 'Redis must not be published on host interfaces')
assert.equal(config.services.frontend.build.dockerfile, 'deploy/docker/Dockerfile', 'The served image must include both desktop and mobile applications')
assert.equal(config.services.frontend.build.args.VITE_MOBILE_BASE_URL, '/mobile', 'QR codes must use the deployed mobile entry, not a development port')
assert.equal(config.services.frontend.build.args.APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS, backend.environment.APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS,
  'Nginx iframe sources and the backend plugin origin allowlist must match')

const externalDataNames = [
  'APP_EXTERNAL_DATA_KEYS', 'APP_EXTERNAL_DATA_ACTIVE_KEY',
  'APP_EXTERNAL_DATA_ALLOWED_TARGETS', 'APP_EXTERNAL_DATA_ALLOWED_ADDRESSES',
  'APP_EXTERNAL_DATA_PLAINTEXT_TARGETS', 'APP_EXTERNAL_DATA_SQLITE_ROOT', 'APP_EXTERNAL_DATA_SQLITE_FILES',
]
for (const name of externalDataNames) {
  assert.ok(Object.hasOwn(backend.environment, name) && typeof backend.environment[name] === 'string',
    `Backend must receive ${name} explicitly, even when it is empty`)
  if (args.includes('--external-data-defaults')) {
    assert.ok(backend.environment[name] === '', `${name} must default to empty (deny by default)`)
  } else if (args.includes('--external-data-from-env')) {
    // Boolean assertions deliberately avoid printing credential values on a mismatch.
    assert.ok(backend.environment[name] === (process.env[name] ?? ''), `${name} was not passed through unchanged`)
  }
}
for (const [serviceName, service] of Object.entries(config.services)) {
  if (serviceName !== 'backend') {
    assert.ok(!Object.keys(service.environment ?? {}).some(name => name.startsWith('APP_EXTERNAL_DATA_')),
      `External data configuration must not be exposed to the ${serviceName} service`)
  }
  assert.ok(!Object.keys(service.build?.args ?? {}).some(name => name.startsWith('APP_EXTERNAL_DATA_')),
    `External data configuration must not be baked into the ${serviceName} image`)
}
console.log(`Compose regression checks passed (${redis.environment.REDIS_PASSWORD ? 'authenticated' : 'empty-password'} Redis configuration); no containers started.`)

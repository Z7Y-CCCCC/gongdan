// Unit-test the rendered-config guard with synthetic data; never invokes Docker or a service.
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const externalFixture = {
  APP_EXTERNAL_DATA_KEYS: 'ci-render-only:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
  APP_EXTERNAL_DATA_ACTIVE_KEY: 'ci-render-only',
  APP_EXTERNAL_DATA_ALLOWED_TARGETS: '12@mes.example.invalid:443,12@plain.example.invalid:80',
  APP_EXTERNAL_DATA_ALLOWED_ADDRESSES: '12@192.0.2.12,12@192.0.2.13',
  APP_EXTERNAL_DATA_PLAINTEXT_TARGETS: '12@plain.example.invalid:80',
  APP_EXTERNAL_DATA_SQLITE_ROOT: '/external-snapshots',
  APP_EXTERNAL_DATA_SQLITE_FILES: '12@equipment_snapshot',
}
const emptyExternal = Object.fromEntries(Object.keys(externalFixture).map(name => [name, '']))

function fixture(external = emptyExternal) {
  return {
    services: {
      redis: {
        environment: { REDIS_PASSWORD: '' },
        command: ['sh', '-c', 'exec docker-entrypoint.sh redis-server --requirepass "$REDIS_PASSWORD"'],
        healthcheck: { test: ['CMD-SHELL', 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping | grep PONG'] },
      },
      backend: { environment: {
        SPRING_REDIS_PASSWORD: '', APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS: 'https://plugin.example.invalid', ...external,
      } },
      frontend: { build: { dockerfile: 'deploy/docker/Dockerfile', args: {
        VITE_MOBILE_BASE_URL: '/mobile', APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS: 'https://plugin.example.invalid',
      } } },
    },
  }
}

function validate(config, flag = '--external-data-defaults') {
  return spawnSync(process.execPath, [fileURLToPath(new URL('./test-compose-config.mjs', import.meta.url)), flag], {
    input: JSON.stringify(config), encoding: 'utf8', env: { ...process.env, ...externalFixture }, windowsHide: true,
  })
}

test('accepts explicit deny-by-default external source configuration', () => {
  const result = validate(fixture())
  assert.equal(result.status, 0, result.stderr)
})

test('accepts unchanged configured external source variables without printing credentials', () => {
  const result = validate(fixture(externalFixture), '--external-data-from-env')
  assert.equal(result.status, 0, result.stderr)
  assert.ok(!(result.stdout + result.stderr).includes(externalFixture.APP_EXTERNAL_DATA_KEYS))
})

for (const name of Object.keys(externalFixture)) {
  test(`rejects omitted ${name}`, () => {
    const config = fixture()
    delete config.services.backend.environment[name]
    assert.notEqual(validate(config).status, 0)
  })
}

test('rejects permissive defaults', () => {
  assert.notEqual(validate(fixture(externalFixture)).status, 0)
})

test('rejects damaged or misrouted configuration without printing credentials', () => {
  const config = fixture(externalFixture)
  config.services.backend.environment.APP_EXTERNAL_DATA_KEYS = 'unexpected-secret-value'
  const result = validate(config, '--external-data-from-env')
  assert.notEqual(result.status, 0)
  assert.ok(!(result.stdout + result.stderr).includes(externalFixture.APP_EXTERNAL_DATA_KEYS))
  assert.ok(!(result.stdout + result.stderr).includes('unexpected-secret-value'))
})

test('rejects external source configuration in another service', () => {
  const config = fixture()
  config.services.frontend.environment = { APP_EXTERNAL_DATA_KEYS: 'must-not-be-public' }
  assert.notEqual(validate(config).status, 0)
})

test('rejects external source configuration in any image build arguments', () => {
  const config = fixture()
  config.services.backend.build = { args: { APP_EXTERNAL_DATA_KEYS: 'must-not-be-in-image' } }
  assert.notEqual(validate(config).status, 0)
})

test('normal attachment deployment defaults retain the supported 20MB upload limit', () => {
  const compose = readFileSync(new URL('../docker-compose.yml', import.meta.url), 'utf8')
  assert.match(compose, /SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE:\s*\$\{SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE:-20MB\}/)
})

for (const [service, volume] of [['quality-plugin', 'quality-plugin-data'], ['inspection-plugin', 'inspection-plugin-data']]) {
  test(`${service} retains records in an independent named data volume`, () => {
    const compose = readFileSync(new URL('../docker-compose.yml', import.meta.url), 'utf8')
    const block = compose.match(new RegExp(`^  ${service}:\\s*\\r?\\n([\\s\\S]*?)(?=^  [a-z]|^volumes:|$(?![\\s\\S]))`, 'm'))?.[1]
    assert.ok(block, `missing ${service} service`)
    assert.match(block, /PLUGIN_DATA_DIR:\s*\/var\/lib\/gongdan-plugin/)
    assert.ok(block.includes(`${volume}:/var/lib/gongdan-plugin`), 'database path must be mounted')
    const declarations = compose.slice(compose.lastIndexOf('\nvolumes:'))
    assert.ok(declarations.includes(`\n  ${volume}:`), 'named volume must be declared')
  })
}

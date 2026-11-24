// Real core-gateway lifecycle against an explicitly marked, isolated H2 fixture.
// Reuses the browser's test-admin token: logging that admin in again would kick it out.
// Usage: node tools/functional/check-plugin-gateway.mjs <fixture-session-json>
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import net from 'node:net'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const sessionFile = process.argv[2] ? path.resolve(workspace, process.argv[2]) : ''
const outputFile = path.join(workspace, 'output/functional-plugin-gateway-round2.json')
const gatewaySecret = 'functional-loopback-gateway-round2' // Deliberately public, local-fixture-only secret.
const superPassword = 'test-super-admin-pass' // Deliberately public, local-fixture-only seed password.
const checks = []
const services = new Set()
const bindings = new Map()
const jarSha256 = {}
const secrets = new Set([gatewaySecret, superPassword])
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
let tempRoot
let failure
let superToken = ''
let coreOrigin = ''
let frontendOrigin = ''

function fixtureOrigin(value, host, label) {
  assert.ok(typeof value === 'string', `${label} must be an explicit loopback HTTP origin`)
  const pattern = new RegExp(`^http://${host.replaceAll('.', '\\.')}:([1-9][0-9]{0,4})/?$`)
  const match = pattern.exec(value)
  assert.ok(match, `${label} must use http, exactly ${host}, an explicit port, and no credentials/path/query/fragment`)
  const port = Number(match[1])
  assert.ok(port >= 1 && port <= 65535, `${label} must use a valid TCP port`)
  const parsed = new URL(value)
  assert.ok(parsed.protocol === 'http:' && parsed.hostname === host && !parsed.username && !parsed.password,
    `${label} must stay on the expected loopback IP`)
  return `http://${host}:${port}`
}

function sanitized(value) {
  let result = String(value)
  for (const secret of secrets) if (secret) result = result.replaceAll(secret, '[REDACTED]')
  return result
}
function passed(label) { checks.push(label); console.log(`PASS ${label}`) }
async function json(url, options = {}, expected = 200) {
  const response = await fetch(url, { ...options, redirect: 'manual', signal: AbortSignal.timeout(15000) })
  const statuses = Array.isArray(expected) ? expected : [expected]
  assert.ok(statuses.includes(response.status), `${options.method || 'GET'} ${new URL(url).pathname}: HTTP ${response.status}, expected ${statuses.join('/')}`)
  const body = await response.text()
  return body ? JSON.parse(body) : null
}
async function api(token, method, route, payload, expected = 200, extraHeaders = {}) {
  return json(`${coreOrigin}/api${route}`, {
    method, headers: { ...(payload !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extraHeaders },
    ...(payload !== undefined ? { body: JSON.stringify(payload) } : {})
  }, expected)
}
const gatewayPath = (key, endpoint) => `/plugins/${key}/api/plugin/${key}/${endpoint}`
const platformPath = key => `/platform/plugins/${key}`
async function catalog(token, key) { return (await api(token, 'GET', '/platform/plugins')).find(item => item.pluginKey === key) }
async function runtime(token, key) { return (await api(token, 'GET', '/platform/plugins/runtime-manifest')).find(item => item.pluginKey === key) }

async function assertFree(port) {
  const server = net.createServer()
  await new Promise((resolve, reject) => {
    server.once('error', error => reject(new Error(`Loopback port ${port} is occupied; no process was stopped: ${error.code}`)))
    server.listen(port, '127.0.0.1', resolve)
  })
  await new Promise(resolve => server.close(resolve))
}
function realJava() {
  const settings = spawnSync('java', ['-XshowSettings:properties', '-version'], { encoding: 'utf8', windowsHide: true })
  assert.equal(settings.status, 0, 'Java is required; existing services were not modified')
  const home = `${settings.stdout}${settings.stderr}`.match(/java\.home\s*=\s*([^\r\n]+)/)?.[1]?.trim()
  assert.ok(home, 'Resolve the real JVM, not an Oracle Windows shim')
  const binary = path.join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
  assert.ok(existsSync(binary), 'Resolved JVM must exist')
  return binary
}
function cleanJavaEnvironment() {
  const env = { ...process.env }
  for (const key of Object.keys(env)) {
    if (/^(SPRING_|PLUGIN_|SERVER_|MANAGEMENT_|LOGGING_)/i.test(key)
      || /^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|JAVA_OPTS|CLASSPATH)$/i.test(key)) delete env[key]
  }
  return env
}
function assertListenerOwner(service) {
  if (process.platform !== 'win32') return
  const shell = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32/WindowsPowerShell/v1.0/powershell.exe')
  const command = `$owners=@(Get-NetTCPConnection -State Listen -LocalPort ${service.port} -ErrorAction SilentlyContinue | Where-Object { $_.LocalAddress -eq '127.0.0.1' } | Select-Object -ExpandProperty OwningProcess -Unique); if($owners.Count -ne 1 -or $owners[0] -ne ${service.child.pid}) { exit 1 }`
  const result = spawnSync(shell, ['-NoProfile', '-NonInteractive', '-Command', command], { encoding: 'utf8', windowsHide: true })
  assert.equal(result.status, 0, `${service.key}: the fixed listener must belong to this real JVM`)
}
async function start(key, port, java) {
  await assertFree(port)
  const jar = path.join(workspace, 'plugins', key, 'target', `${key}-1.0.0.jar`)
  assert.ok(existsSync(jar), `Package ${key} before running this fixture`)
  jarSha256[key] = createHash('sha256').update(readFileSync(jar)).digest('hex')
  const storage = path.join(tempRoot, key)
  mkdirSync(storage, { recursive: true })
  const child = spawn(java, ['-jar', jar, '--server.address=127.0.0.1', `--server.port=${port}`,
    `--management.server.port=${port}`, '--spring.profiles.active=', '--spring.config.location=classpath:/application.properties',
    `--spring.datasource.url=jdbc:h2:file:${path.join(storage, 'records')};DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`,
    '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.datasource.driver-class-name=org.h2.Driver',
    `--plugin.gateway.secret=${gatewaySecret}`, '--spring.main.banner-mode=off', '--logging.level.root=WARN'],
  { cwd: storage, env: cleanJavaEnvironment(), windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  const service = { key, child, port, origin: `http://127.0.0.1:${port}`, closed: false, log: '' }
  services.add(service)
  service.done = new Promise(resolve => child.once('close', () => { service.closed = true; resolve() }))
  child.once('error', error => { service.error = error })
  for (const stream of [child.stdout, child.stderr]) stream.on('data', bytes => { service.log += bytes.toString() })
  const end = Date.now() + 90000
  while (Date.now() < end) {
    if (service.error || service.closed) throw new Error(`${key} JVM startup failed: ${sanitized(service.error?.message || service.log.slice(-1800))}`)
    try {
      const health = await json(`${service.origin}/actuator/health`)
      if (health.status === 'UP') {
        assertListenerOwner(service)
        passed(`${key}: own packaged JVM is healthy on the isolated fixed loopback port`)
        return service
      }
    } catch { /* Only this previously empty, loopback port is polled. */ }
    await delay(250)
  }
  throw new Error(`${key} JVM did not become healthy: ${sanitized(service.log.slice(-1800))}`)
}
async function stop(service) {
  if (!services.has(service)) return
  if (!service.closed) service.child.kill('SIGTERM')
  if (!(await Promise.race([service.done.then(() => true), delay(10000).then(() => false)]))) {
    service.child.kill('SIGKILL'); await service.done
  }
  services.delete(service)
  await assertFree(service.port)
}
async function install(token, key) {
  const result = await api(token, 'POST', `${platformPath(key)}/install`)
  if (!bindings.has(key)) bindings.set(key, new Set())
  bindings.get(key).add(token)
  return result
}
async function uninstall(token, key) {
  const result = await api(token, 'DELETE', `${platformPath(key)}/install`)
  bindings.get(key)?.delete(token)
  return result
}

async function scenario(service, normalSession, normalFactory, superFactory) {
  const { key, origin } = service
  const token = normalSession.token
  const name = key === 'quality-management' ? 'Functional Quality Gateway' : 'Functional Inspection Gateway'
  const endpoint = key === 'quality-management' ? 'records' : 'inspections'
  const manifest = { frontendEntry: `${origin}/`, backendUrl: origin, healthUrl: '/actuator/health',
    requiredRoles: ['ADMIN', 'PRODUCTION'], menus: [{ label: name, path: '/' }],
    configSchema: { properties: { inspectionIntervalDays: { type: 'integer', minimum: 1, maximum: 30 },
      note: { type: 'string', maxLength: 40 } }, required: ['inspectionIntervalDays'] } }
  const normalConfig = { inspectionIntervalDays: 7, note: 'normal factory fixture' }
  const otherConfig = { inspectionIntervalDays: 14, note: 'other factory fixture' }
  const original = await catalog(token, key)
  if (original) {
    assert.equal(original.factoryStatus, 'NOT_INSTALLED', 'Never alter an existing factory installation')
    assert.equal(original.name, name, 'Never repurpose an unrelated catalog entry')
    assert.deepEqual(JSON.parse(original.manifestJson), manifest, 'Existing fixture catalog must match these local endpoints')
    assert.equal((await catalog(superToken, key)).factoryStatus, 'NOT_INSTALLED')
  } else {
    const created = await api(superToken, 'POST', '/platform/plugins', { pluginKey: key, name,
      description: 'Isolated real core-gateway lifecycle fixture', pluginType: 'CUSTOM', runtimeMode: 'IFRAME',
      minCoreVersion: '1.0.0', manifestJson: JSON.stringify(manifest) })
    assert.equal(created.pluginKey, key); assert.equal(created.name, name)
  }
  passed(`${key}: super administrator created/read the English catalog entry`)
  const versionCatalog = await catalog(superToken, key)
  if (!versionCatalog.versions.some(item => item.version === '1.0.0')) {
    await api(superToken, 'POST', `${platformPath(key)}/versions`, { version: '1.0.0', checksum: jarSha256[key],
      releaseNotes: 'Original local packaged fixture', manifestJson: JSON.stringify(manifest) })
  }
  const ready = (await catalog(superToken, key)).versions.find(item => item.version === '1.0.0')
  assert.equal(ready.status, 'READY'); assert.equal(ready.checksum, jarSha256[key])
  assert.deepEqual(JSON.parse(ready.manifestJson), manifest, 'Never route through an unrelated existing version')
  passed(`${key}: real 1.0.0 JAR checksum registered as a READY version`)
  await api(token, 'POST', `${platformPath(key)}/versions`, { version: '9.9.9' }, 403)
  assert.ok(!(await catalog(token, key)).versions.some(item => item.version === '9.9.9'))
  passed(`${key}: ordinary administrator cannot register platform versions`)
  await json(`${origin}/api/plugin/${key}/${endpoint}`, {}, 403)
  await api(token, 'GET', gatewayPath(key, endpoint), undefined, 404)
  passed(`${key}: unsigned direct API and uninstalled core-gateway access are rejected`)

  assert.equal((await install(token, key)).factoryStatus, 'DISABLED')
  assert.equal((await catalog(token, key)).factoryStatus, 'DISABLED')
  await api(token, 'GET', gatewayPath(key, endpoint), undefined, 404)
  passed(`${key}: ordinary administrator installed without prematurely exposing an API`)
  const enabled = await api(token, 'POST', `${platformPath(key)}/enable`, { version: '1.0.0', rolloutPercent: 100 })
  assert.equal(enabled.factoryStatus, 'ENABLED'); assert.equal(enabled.enabledVersion, '1.0.0'); assert.equal(enabled.healthStatus, 'UP')
  const health = await api(token, 'POST', `${platformPath(key)}/health/refresh`)
  assert.equal(health.healthStatus, 'UP'); assert.ok(health.lastHealthAt)
  passed(`${key}: enable and real upstream health refresh succeeded`)
  await api(token, 'PUT', `${platformPath(key)}/config`, { configJson: JSON.stringify(normalConfig) })
  assert.deepEqual(JSON.parse((await catalog(token, key)).configJson), normalConfig)
  await api(token, 'PUT', `${platformPath(key)}/config`, { configJson: JSON.stringify({ inspectionIntervalDays: 99 }) }, 400)
  assert.deepEqual(JSON.parse((await catalog(token, key)).configJson), normalConfig)
  passed(`${key}: configuration persisted; rejected edits retained the previous valid configuration`)
  const visible = await runtime(token, key)
  assert.equal(visible.version, '1.0.0'); assert.equal(visible.manifest.frontendEntry, `${origin}/`)
  assert.deepEqual(visible.config, {}); assert.ok(!('backendUrl' in visible.manifest)); assert.ok(!('healthUrl' in visible.manifest))
  passed(`${key}: healthy runtime appeared immediately without leaking gateway configuration`)
  const ping = await api(token, 'GET', gatewayPath(key, 'ping'))
  assert.equal(ping.pluginKey, key); assert.equal(ping.status, 'UP')
  if (key === 'equipment-inspection') {
    assert.equal(ping.factoryId, normalFactory.id); assert.equal(ping.userId, normalSession.user.id)
    assert.equal(ping.role, 'ADMIN'); assert.deepEqual(ping.config, normalConfig)
  }
  passed(`${key}: real core-signed gateway ping accepted authenticated context${key === 'equipment-inspection' ? ' and decoded configuration' : ''}`)
  const payload = key === 'quality-management' ? { issue: 'Core gateway quality record', status: 'OPEN', remark: 'normal factory record' }
    : { equipmentCode: 'EQ-CORE-GATEWAY', result: '正常', remark: 'normal factory record' }
  const saved = await api(token, 'POST', gatewayPath(key, endpoint), payload, 201,
    { 'X-Plugin-Factory-Id': '999', 'X-Plugin-User-Id': '999', 'X-Plugin-Role': 'PRODUCTION', 'X-Plugin-Config-Base64': 'e30=' })
  assert.equal(saved.factoryId, String(normalFactory.id)); assert.equal(saved.operatorUserId, String(normalSession.user.id))
  assert.deepEqual((await api(token, 'GET', gatewayPath(key, endpoint)))[0], saved)
  passed(`${key}: real gateway POST/read-back persisted; caller-supplied context did not override the authenticated factory/user`)

  await install(superToken, key)
  await api(superToken, 'POST', `${platformPath(key)}/enable`, { version: '1.0.0', rolloutPercent: 100 })
  await api(superToken, 'PUT', `${platformPath(key)}/config`, { configJson: JSON.stringify(otherConfig) })
  assert.deepEqual(await api(superToken, 'GET', gatewayPath(key, endpoint)), [])
  const other = await api(superToken, 'POST', gatewayPath(key, endpoint), payload, 201)
  assert.equal(other.factoryId, String(superFactory.id)); assert.notEqual(other.factoryId, saved.factoryId)
  assert.equal((await api(token, 'GET', gatewayPath(key, endpoint))).length, 1)
  assert.equal((await api(superToken, 'GET', gatewayPath(key, endpoint))).length, 1)
  if (key === 'equipment-inspection') assert.deepEqual((await api(superToken, 'GET', gatewayPath(key, 'ping'))).config, otherConfig)
  assert.deepEqual(JSON.parse((await catalog(token, key)).configJson), normalConfig)
  passed(`${key}: two authenticated factories retained separate gateway records and configurations`)
  await api(superToken, 'POST', `${platformPath(key)}/disable`)
  await uninstall(superToken, key)
  assert.equal(await runtime(superToken, key), undefined)
  await api(superToken, 'GET', gatewayPath(key, endpoint), undefined, 404)

  const versions = (await catalog(superToken, key)).versions
  if (!versions.some(item => item.version === '1.0.1')) {
    await api(superToken, 'POST', `${platformPath(key)}/versions`, { version: '1.0.1', checksum: jarSha256[key],
      releaseNotes: 'Registry lifecycle test only; same packaged 1.0.0 binary', manifestJson: JSON.stringify(manifest) })
  }
  const nextVersion = (await catalog(superToken, key)).versions.find(item => item.version === '1.0.1')
  assert.equal(nextVersion.status, 'READY'); assert.equal(nextVersion.checksum, jarSha256[key])
  assert.deepEqual(JSON.parse(nextVersion.manifestJson), manifest, 'Never activate an unrelated existing version')
  const activated = await api(token, 'POST', `${platformPath(key)}/versions/1.0.1/activate`)
  assert.equal(activated.enabledVersion, '1.0.1'); assert.equal(activated.healthStatus, 'UNKNOWN')
  assert.equal(await runtime(token, key), undefined)
  await api(token, 'POST', `${platformPath(key)}/health/refresh`)
  assert.equal((await runtime(token, key)).version, '1.0.1')
  assert.equal((await api(token, 'GET', gatewayPath(key, 'ping'))).pluginKey, key)
  passed(`${key}: registered-version activation became visible after health refresh (same test binary, not a software upgrade)`)
  const rolledBack = await api(token, 'POST', `${platformPath(key)}/rollback`)
  assert.equal(rolledBack.enabledVersion, '1.0.0'); assert.equal(rolledBack.healthStatus, 'UNKNOWN')
  await api(token, 'POST', `${platformPath(key)}/health/refresh`)
  assert.equal((await runtime(token, key)).version, '1.0.0')
  assert.deepEqual((await api(token, 'GET', gatewayPath(key, endpoint)))[0], saved)
  passed(`${key}: registry rollback restored 1.0.0 routing and retained saved data`)
  await api(token, 'POST', `${platformPath(key)}/disable`)
  assert.equal((await catalog(token, key)).factoryStatus, 'DISABLED')
  assert.equal(await runtime(token, key), undefined)
  await api(token, 'GET', gatewayPath(key, endpoint), undefined, 404)
  passed(`${key}: disable immediately evicted the runtime and rejected gateway access`)
  assert.equal((await uninstall(token, key)).factoryStatus, 'NOT_INSTALLED')
  assert.equal((await catalog(token, key)).configJson, null)
  assert.equal(await runtime(token, key), undefined)
  await api(token, 'GET', gatewayPath(key, endpoint), undefined, 404)
  passed(`${key}: uninstall removed the factory binding/configuration and left gateway access closed`)
}

try {
  assert.ok(sessionFile && existsSync(sessionFile), 'Explicitly supply the current marked browser-fixture session file; do not relogin that administrator')
  const session = JSON.parse(readFileSync(sessionFile, 'utf8'))
  if (typeof session?.token === 'string') secrets.add(session.token)
  assert.equal(session.fixtureOnly, true); assert.equal(session.factoryCode, 'FUNCTIONALTEST')
  assert.equal(session.user?.role, 'ADMIN')
  assert.ok(Number.isInteger(session.user?.id) && session.user.id > 0, 'Fixture administrator must have a positive integer ID')
  assert.ok(typeof session.token === 'string' && session.token.length > 0, 'Fixture administrator token is required but must not be printed')
  frontendOrigin = fixtureOrigin(session.frontendOrigin, '127.0.0.2', 'frontendOrigin')
  coreOrigin = fixtureOrigin(session.backendOrigin, '127.0.0.1', 'backendOrigin')
  assert.equal(session.apiOrigin, `${frontendOrigin}/api`)
  const marker = await json(`${frontendOrigin}/__functional-fixture`)
  assert.equal(marker.fixture, 'real-h2-api'); assert.equal(marker.factoryCode, 'FUNCTIONALTEST'); assert.equal(marker.apiOrigin, coreOrigin)
  const normalFactory = await api(session.token, 'GET', '/factory')
  assert.equal(normalFactory.code, 'FUNCTIONALTEST')
  const superSession = await api('', 'POST', '/auth/login', { factoryCode: 'ADMIN', username: 'admin', password: superPassword })
  assert.ok(superSession.token); assert.equal(superSession.isSuperAdmin, true)
  superToken = superSession.token; secrets.add(superToken)
  const superFactory = await api(superToken, 'GET', '/factory')
  assert.equal(superFactory.code, 'ADMIN'); assert.notEqual(superFactory.id, normalFactory.id)
  passed('Marked H2 fixture and both authenticated factories verified without relogging the shared browser administrator')
  await assertFree(9101); await assertFree(9102)
  tempRoot = mkdtempSync(path.join(os.tmpdir(), 'gongdan-plugin-gateway-'))
  const java = realJava()
  for (const [key, port] of [['quality-management', 9101], ['equipment-inspection', 9102]]) {
    const service = await start(key, port, java)
    await scenario(service, session, normalFactory, superFactory)
    await stop(service)
  }
} catch (error) {
  failure = sanitized(error.stack || error.message || error)
} finally {
  // Restore only installations created by this script, never core/browser services.
  for (const [key, tokens] of bindings) for (const token of [...tokens]) {
    try { await api(token, 'POST', `${platformPath(key)}/disable`); await uninstall(token, key) }
    catch (error) { failure = [failure, `Factory-installation cleanup failed: ${sanitized(error.message)}`].filter(Boolean).join('\n') }
  }
  for (const service of [...services]) {
    try { await stop(service) }
    catch (error) { failure = [failure, `Own JVM cleanup failed: ${sanitized(error.message)}`].filter(Boolean).join('\n') }
  }
  if (tempRoot && services.size === 0) {
    try {
      const resolved = realpathSync(tempRoot)
      assert.equal(path.dirname(resolved), realpathSync(os.tmpdir()))
      assert.ok(path.basename(resolved).startsWith('gongdan-plugin-gateway-'))
      rmSync(resolved, { recursive: true, force: true })
    } catch (error) { failure = [failure, `Own temporary-storage cleanup failed: ${sanitized(error.message)}`].filter(Boolean).join('\n') }
  }
  const result = { fixtureOnly: true, coreOrigin, frontendOrigin, passed: checks.length, checks, jarSha256,
    registeredVersionsUseSameBinary: true, success: !failure, ...(failure ? { error: failure } : {}) }
  mkdirSync(path.dirname(outputFile), { recursive: true })
  writeFileSync(outputFile, JSON.stringify(result, null, 2))
  if (failure) { console.error(failure); process.exitCode = 1 }
  else console.log(JSON.stringify(result, null, 2))
}

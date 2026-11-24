// Launches only repository JARs in fresh temporary directories, never a deployed plugin.
import assert from 'node:assert/strict'
import { createHash, createHmac, randomBytes } from 'node:crypto'
import { spawn, spawnSync } from 'node:child_process'
import { appendFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import net from 'node:net'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const output = path.join(workspace, 'output')
mkdirSync(output, { recursive: true })
const settings = spawnSync('java', ['-XshowSettings:properties', '-version'], { encoding: 'utf8', windowsHide: true })
assert.equal(settings.status, 0, 'Java is required; no services were contacted')
const home = `${settings.stdout}${settings.stderr}`.match(/java\.home\s*=\s*([^\r\n]+)/)?.[1]?.trim()
const java = home ? path.join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java'
const root = mkdtempSync(path.join(os.tmpdir(), 'gongdan-plugin-http-'))
const secret = randomBytes(32).toString('hex')
const checks = []
const services = new Set()
const artifacts = {}
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

async function freePort() {
  const server = net.createServer()
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve) })
  const port = server.address().port
  await new Promise(resolve => server.close(resolve))
  return port
}

function headers(key, method, route, factoryId, body = '') {
  const timestamp = String(Math.floor(Date.now() / 1000))
  const bodyHash = createHash('sha256').update(body).digest('hex')
  const canonical = [method, route, key, '1.0.0', factoryId, '11', 'ADMIN', timestamp, bodyHash, ''].join('\n')
  return {
    'Content-Type': 'application/json', 'X-Plugin-Key': key, 'X-Plugin-Version': '1.0.0',
    'X-Plugin-Factory-Id': factoryId, 'X-Plugin-User-Id': '11', 'X-Plugin-Role': 'ADMIN',
    'X-Plugin-Body-SHA256': bodyHash, 'X-Plugin-Gateway-Timestamp': timestamp,
    'X-Plugin-Gateway-Signature': createHmac('sha256', secret).update(canonical).digest('hex'),
  }
}

async function start(key) {
  const jar = path.join(workspace, 'plugins', key, 'target', `${key}-1.0.0.jar`)
  assert.ok(existsSync(jar), `Package ${key} before running this fixture`)
  const checksum = createHash('sha256').update(readFileSync(jar)).digest('hex')
  if (artifacts[key]) assert.equal(checksum, artifacts[key], `${key} JAR changed during the restart test`)
  else artifacts[key] = checksum
  const storage = path.join(root, key)
  mkdirSync(storage, { recursive: true })
  const port = await freePort()
  const child = spawn(java, ['-jar', jar,
    '--server.address=127.0.0.1', `--server.port=${port}`, '--management.server.port=' + port,
    '--spring.profiles.active=', '--spring.config.location=classpath:/application.properties',
    `--spring.datasource.url=jdbc:h2:file:${path.join(storage, 'records')};DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`,
    '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.datasource.driver-class-name=org.h2.Driver',
    `--plugin.gateway.secret=${secret}`, '--spring.main.banner-mode=off', '--logging.level.root=WARN'],
  { cwd: storage, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  const service = { child, key, origin: `http://127.0.0.1:${port}`, log: '', closed: false }
  services.add(service)
  service.done = new Promise(resolve => child.once('close', () => { service.closed = true; resolve() }))
  child.once('error', error => { service.error = error })
  for (const stream of [child.stdout, child.stderr]) stream.on('data', bytes => { service.log += bytes.toString() })
  const end = Date.now() + 90000
  while (Date.now() < end) {
    if (service.error || service.closed) throw new Error(`${key} fixture failed to start: ${service.error?.message ?? service.log.slice(-1200)}`)
    try {
      const route = `/api/plugin/${key}/ping`
      const response = await fetch(service.origin + route, { headers: headers(key, 'GET', route, '101'), signal: AbortSignal.timeout(1500) })
      if (response.ok && (await response.json()).pluginKey === key) return service
    } catch { /* Wait for this isolated JVM; no shared service is used. */ }
    await delay(200)
  }
  throw new Error(`${key} fixture did not become ready: ${service.log.slice(-1200)}`)
}

async function stop(service) {
  if (!services.has(service)) return
  if (!service.closed) service.child.kill('SIGTERM')
  if (!(await Promise.race([service.done.then(() => true), delay(10000).then(() => false)]))) {
    service.child.kill('SIGKILL')
    await service.done
  }
  appendFileSync(path.join(output, `functional-plugin-http-${service.key}.log`), `\n--- isolated JVM lifecycle ---\n${service.log}`)
  services.delete(service)
}

async function request(service, method, endpoint, factory, payload, expected = 200) {
  const route = `/api/plugin/${service.key}/${endpoint}`
  const body = payload === undefined ? '' : JSON.stringify(payload)
  const response = await fetch(service.origin + route, {
    method, headers: headers(service.key, method, route, factory, body),
    ...(body ? { body } : {}), signal: AbortSignal.timeout(10000),
  })
  assert.equal(response.status, expected, `${service.key} ${method} ${endpoint} returned ${response.status}`)
  return response.json()
}

async function scenario(key, endpoint, payload) {
  let service = await start(key)
  try {
    const direct = await fetch(`${service.origin}/api/plugin/${key}/${endpoint}`)
    assert.equal(direct.status, 403)
    checks.push(`${key}: unsigned direct API rejected`)
    const saved = await request(service, 'POST', endpoint, '101', payload, 201)
    assert.ok(saved.id > 0)
    assert.equal(saved.factoryId, '101')
    assert.equal((await request(service, 'GET', endpoint, '101'))[0].id, saved.id)
    checks.push(`${key}: signed save/read succeeded over real HTTP`)
    assert.deepEqual(await request(service, 'GET', endpoint, '202'), [])
    checks.push(`${key}: another factory cannot read the record`)
    await stop(service)
    service = await start(key)
    const records = await request(service, 'GET', endpoint, '101')
    assert.equal(records.length, 1)
    assert.deepEqual(records[0], saved)
    checks.push(`${key}: saved record survived JVM termination and restart`)
    const next = await request(service, 'POST', endpoint, '202', payload, 201)
    assert.ok(next.id > saved.id)
    assert.equal((await request(service, 'GET', endpoint, '101')).length, 1)
    checks.push(`${key}: restart preserved ID sequence and factory isolation`)
    const writes = await Promise.all(Array.from({ length: 8 }, () => request(service, 'POST', endpoint, '101', payload, 201)))
    assert.equal(new Set(writes.map(record => record.id)).size, 8)
    assert.equal((await request(service, 'GET', endpoint, '101')).length, 9)
    checks.push(`${key}: eight simultaneous HTTP saves retained unique records`)
  } finally { await stop(service) }
}

try {
  await scenario('quality-management', 'records', { issue: 'Persisted HTTP quality issue', status: 'OPEN', remark: '真实持久记录' })
  await scenario('equipment-inspection', 'inspections', { equipmentCode: 'EQ-HTTP-PERSIST', result: '正常', remark: '真实持久记录' })
  const result = { fixtureOnly: true, passed: checks.length, checks, jarSha256: artifacts }
  writeFileSync(path.join(output, 'functional-plugin-http-round2.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} finally {
  await Promise.all([...services].map(stop))
  const resolved = realpathSync(root)
  assert.equal(path.dirname(resolved), realpathSync(os.tmpdir()), 'temporary cleanup must stay inside the OS temp root')
  assert.ok(path.basename(resolved).startsWith('gongdan-plugin-http-'))
  rmSync(resolved, { recursive: true, force: true })
}

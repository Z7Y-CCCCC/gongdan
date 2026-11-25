// Real HTTP checks against the explicitly recorded Windows/H2 acceptance JVM.
// Usage: node tools/functional/check-security-http.mjs [output/runtime.json]
// Never accepts an arbitrary API URL, follows redirects, or prints credentials,
// bearer tokens, report cookies, CSV bodies, or password hashes.
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const outputDir = path.join(workspace, 'output')
const resultFile = path.join(outputDir, 'security-resume-http-final.json')
const logFile = path.join(outputDir, 'security-resume-http-final.log')
const runtimeFile = path.resolve(workspace, process.argv[2] || 'output/security-resume-api-final-runtime.json')
const checks = [], requests = [], issuedTokens = new Set(), privateHashes = new Set(), log = []
let origin, metadata, processIdentity, frozenClassHashes

class AuditFailure extends Error {}
function requireThat(condition, message) { if (!condition) throw new AuditFailure(message) }
function checked(name, condition, facts = {}) {
  requireThat(condition, name)
  checks.push({ name, passed: true, ...facts })
}
function childOf(parent, candidate) {
  const relative = path.relative(parent, candidate)
  return relative && !relative.startsWith('..') && !path.isAbsolute(relative)
}
function jsonBody(response) {
  try { return JSON.parse(response.bytes.toString('utf8')) }
  catch { throw new AuditFailure(`${response.method} ${response.route.split('?')[0]} did not return valid JSON`) }
}
function expectStatus(response, status) {
  requireThat(response.status === status,
    `${response.method} ${response.route.split('?')[0]} expected HTTP ${status}, received ${response.status}`)
}
function expectError(response, status) {
  expectStatus(response, status)
  const body = jsonBody(response)
  requireThat(body && typeof body.message === 'string' && body.message.length > 0, 'Rejected response lacks its JSON error contract')
}
function expectPage(response, empty = false) {
  expectStatus(response, 200)
  const body = jsonBody(response)
  requireThat(Array.isArray(body.items) && Number.isSafeInteger(body.total) && body.total >= 0, 'Paged response has an invalid items/total contract')
  if (empty) requireThat(body.items.length === 0, 'An out-of-range page returned records')
  return body
}

function verifyRuntime() {
  requireThat(process.argv.length <= 3, 'Only a recorded runtime JSON argument is accepted')
  requireThat(childOf(outputDir, runtimeFile), 'Runtime metadata must be inside this workspace output directory')
  try { metadata = JSON.parse(readFileSync(runtimeFile, 'utf8').replace(/^\uFEFF/, '')) }
  catch { throw new AuditFailure('Cannot read the selected runtime metadata') }
  requireThat(metadata.fixture === 'real-h2-api' && metadata.factoryCode === 'FUNCTIONALTEST', 'Runtime is not the intended isolated H2 fixture')
  requireThat(typeof metadata.apiOrigin === 'string' && /^http:\/\/127\.0\.0\.1:[1-9][0-9]{0,4}$/.test(metadata.apiOrigin), 'Only a recorded numeric IPv4 loopback origin is accepted')
  const url = new URL(metadata.apiOrigin)
  requireThat(Number(url.port) <= 65535 && url.pathname === '/' && !url.username && !url.password && !url.search && !url.hash, 'Runtime origin is invalid')
  origin = url.origin
  requireThat(typeof metadata.tempRoot === 'string' && childOf(path.resolve(os.tmpdir()), path.resolve(metadata.tempRoot))
    && /^gongdan-functional-[a-f0-9]{32}$/i.test(path.basename(metadata.tempRoot)), 'Frozen root is not an isolated acceptance directory')
  requireThat(typeof metadata.classPath === 'string', 'Runtime has no frozen classpath')
  const classRoot = path.join(metadata.tempRoot, 'classes'), configRoot = path.join(metadata.tempRoot, 'config')
  const entries = metadata.classPath.split(path.delimiter)
  requireThat(path.resolve(entries[0]) === path.resolve(classRoot) && path.resolve(entries[1]) === path.resolve(configRoot)
    && entries.some(entry => /[/\\]h2[/\\].*\.jar$/i.test(entry)), 'Runtime classpath is not the frozen H2 test classpath')
  const config = readFileSync(path.join(configRoot, 'application-test.properties'), 'utf8')
  requireThat(/^spring\.datasource\.url=jdbc:h2:mem:/m.test(config) && /^spring\.jpa\.hibernate\.ddl-auto=create-drop$/m.test(config), 'Frozen profile is not the private test database profile')
  const classes = ['WorkorderApplication', 'controller/AuthController', 'controller/UserController',
    'controller/SystemMonitorController', 'service/TokenService', 'report/UReportAuthFilter', 'report/UReportController']
  frozenClassHashes = Object.fromEntries(classes.map(name => {
    const data = readFileSync(path.join(classRoot, 'com/heihu/workorder', `${name}.class`))
    return [name, createHash('sha256').update(data).digest('hex')]
  }))
  // Metadata alone cannot identify whichever process currently owns a port.
  // Refuse other platforms rather than silently dropping this safety boundary.
  requireThat(process.platform === 'win32', 'This recorded-JVM guard currently requires Windows')
  const command = `$owners=@(Get-NetTCPConnection -State Listen -LocalPort ${Number(url.port)} -ErrorAction SilentlyContinue | Where-Object { $_.LocalAddress -eq '127.0.0.1' } | Select-Object -ExpandProperty OwningProcess -Unique); @($owners | ForEach-Object { Get-CimInstance Win32_Process -Filter "ProcessId=$_" | Select-Object ProcessId,ExecutablePath,CommandLine }) | ConvertTo-Json -Compress`
  let processes
  try {
    const stdout = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', command], {
      encoding: 'utf8', windowsHide: true, timeout: 15000, maxBuffer: 256 * 1024
    })
    const parsed = JSON.parse(stdout.replace(/^\uFEFF/, '').trim())
    processes = Array.isArray(parsed) ? parsed : [parsed]
  } catch { throw new AuditFailure('Cannot verify the recorded loopback listener process') }
  requireThat(processes.length === 1 && processes[0] && typeof processes[0].CommandLine === 'string', 'Loopback listener is not uniquely identifiable')
  const actor = processes[0], line = actor.CommandLine
  requireThat(path.resolve(actor.ExecutablePath).toLowerCase() === path.resolve(metadata.java).toLowerCase()
    && line.includes(metadata.classPath) && line.includes('com.heihu.workorder.WorkorderApplication')
    && line.includes('--spring.profiles.active=test') && line.includes('--server.address=127.0.0.1')
    && line.includes(`--server.port=${url.port}`) && line.includes('--app.seed.factory-code=FUNCTIONALTEST')
    && (line.match(/--spring\.datasource\.url=/g) || []).length === 1
    && line.includes('--spring.datasource.url=jdbc:h2:mem:'), 'Listener is not the recorded frozen test-profile H2 JVM')
  processIdentity = { pid: actor.ProcessId, frozenRoot: metadata.tempRoot }
  checked('Recorded loopback listener, frozen classes and real H2 test JVM verified', true)
}

async function request(method, route, { token, cookie, body } = {}) {
  requireThat(typeof route === 'string' && route.startsWith('/') && !route.startsWith('//') && !/[\r\n]/.test(route), 'Invalid local request path')
  const target = new URL(route, origin)
  requireThat(target.origin === origin && !target.username && !target.password, 'Request escaped the recorded loopback origin')
  requireThat(!token || typeof token === 'string' && !/[\r\n]/.test(token), 'Invalid bearer shape')
  const started = Date.now()
  let response, bytes
  try {
    response = await fetch(target, { method, redirect: 'manual', signal: AbortSignal.timeout(15000),
      headers: { 'User-Agent': 'gongdan-local-security-http-fixture', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(cookie ? { Cookie: cookie } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
    bytes = Buffer.from(await response.arrayBuffer())
  } catch { throw new AuditFailure(`${method} ${route.split('?')[0]} local transport failed`) }
  const value = { method, route, status: response.status, headers: response.headers, bytes }
  requests.push({ method, path: route, status: response.status, contentType: response.headers.get('content-type'),
    bodyBytes: bytes.length, durationMs: Date.now() - started })
  requireThat(response.status < 300 || response.status >= 400, 'Unexpected redirect was blocked without following it')
  return value
}

async function login(factoryCode, username, password, mobile = false) {
  const response = await request('POST', mobile ? '/api/mobile/auth/password-login' : '/api/auth/login',
    { body: { factoryCode, username, password } })
  expectStatus(response, 200)
  const body = jsonBody(response)
  requireThat(typeof body.token === 'string' && body.token.length > 10 && body.token === body.token.trim()
    && !/[\r\n]/.test(body.token) && Number.isSafeInteger(body.user?.id), 'Login response shape is invalid')
  issuedTokens.add(body.token)
  return body
}

async function logout(token, cookie) {
  const response = await request('POST', '/api/auth/logout', { token, cookie })
  expectStatus(response, 204)
  requireThat(response.bytes.length === 0 && /\bno-store\b/i.test(response.headers.get('cache-control') || '')
    && !response.headers.has('set-cookie'), 'Logout changed the cookie or lacked its empty no-store contract')
  return response
}

// Strict local RFC-style record parsing for testing the server's raw CSV bytes.
// No rewriting or formula-prefix changes are applied to business or backup data.
function parseCsv(bytes) {
  requireThat(bytes.subarray(0, 3).equals(Buffer.from([0xef, 0xbb, 0xbf])), 'CSV export lost its UTF-8 BOM')
  const text = bytes.toString('utf8').replace(/^\uFEFF/, '')
  const rows = []; let row = [], field = '', quoted = false, afterQuote = false
  const finishField = () => { row.push(field); field = ''; afterQuote = false }
  const finishRow = () => { finishField(); rows.push(row); row = [] }
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { field += '"'; i++ }
      else if (ch === '"') { quoted = false; afterQuote = true }
      else field += ch
    } else if (ch === ',') finishField()
    else if (ch === '\n' || ch === '\r') { if (ch === '\r' && text[i + 1] === '\n') i++; finishRow() }
    else if (ch === '"' && field === '' && !afterQuote) quoted = true
    else { requireThat(!afterQuote && ch !== '"', 'CSV has malformed quote delimiters'); field += ch }
  }
  requireThat(!quoted, 'CSV has an unterminated quoted field')
  if (field || row.length || afterQuote) finishRow()
  requireThat(rows.length >= 2 && rows.every(value => value.length === rows[0].length), 'CSV record widths are inconsistent')
  return rows
}
function equalArray(a, b) { return a.length === b.length && a.every((item, index) => item === b[index]) }
async function exported(token, flag) {
  const response = await request('GET', `/api/users/export${flag === undefined ? '' : `?includePasswordHash=${flag}`}`, { token })
  expectStatus(response, 200)
  requireThat(/^text\/csv\b/i.test(response.headers.get('content-type') || '') && /\bno-store\b/i.test(response.headers.get('cache-control') || '')
    && response.headers.get('x-content-type-options') === 'nosniff'
    && /\battachment\b/i.test(response.headers.get('content-disposition') || ''), 'Export response privacy/download headers are invalid')
  return { response, rows: parseCsv(response.bytes) }
}
async function bridge(token) {
  const response = await request('GET', '/api/ureport/session', { token })
  expectStatus(response, 200)
  const body = jsonBody(response)
  requireThat(body.enabled === true && body.factoryCode === 'ADMIN' && body.path === '/ureport/designer' && !('token' in body), 'Platform report session bridge returned an invalid contract')
  requireThat(/\bno-store\b/i.test(response.headers.get('cache-control') || ''), 'Report session response is cacheable')
  const header = response.headers.get('set-cookie') || '', pair = header.split(';', 1)[0]
  requireThat(pair === `workorder_ureport_token=${token}` && /;\s*Path=\/ureport(?:;|$)/i.test(header)
    && /;\s*HttpOnly(?:;|$)/i.test(header) && /;\s*SameSite=Lax(?:;|$)/i.test(header)
    && /;\s*Max-Age=3600(?:;|$)/i.test(header), 'Report cookie did not retain its scoped HttpOnly/SameSite bridge')
  return { pair, properties: { path: '/ureport', httpOnly: true, sameSite: 'Lax', maxAgeSeconds: 3600,
    secure: /;\s*Secure(?:;|$)/i.test(header) } }
}
async function designer(cookie) {
  const response = await request('GET', '/ureport/designer', { cookie })
  expectStatus(response, 200)
  requireThat(/^text\/html\b/i.test(response.headers.get('content-type') || '')
    && /<html\b/i.test(response.bytes.toString('utf8')) && /ureport/i.test(response.bytes.toString('utf8')), 'Native designer did not return the actual UReport HTML')
  return response
}

async function main() {
  verifyRuntime()
  log.push('Verified recorded frozen Windows H2 JVM; all HTTP requests restricted to its exact loopback origin.')
  for (const flag of ['false', 'true']) expectError(await request('GET', `/api/users/export?includePasswordHash=${flag}`), 401)
  checked('Both user export modes remain anonymous-inaccessible', true)
  expectError(await request('GET', '/api/ureport/session'), 401)
  expectError(await request('GET', '/ureport/designer'), 401)
  checked('Anonymous bridge and native designer are both rejected', true)

  const admin = await login('FUNCTIONALTEST', 'admin', '123456')
  requireThat(admin.user.role === 'ADMIN' && admin.isSuperAdmin === false, 'Fixture factory admin was incorrectly treated as the platform admin')
  checked('Actual factory administrator HTTP login has ordinary-admin semantics', true)
  expectError(await request('GET', '/api/ureport/session', { token: admin.token }), 403)
  expectError(await request('GET', '/ureport/designer', { cookie: `workorder_ureport_token=${admin.token}` }), 403)
  checked('A real factory-admin token cannot establish or forge a native designer cookie', true)
  expectPage(await request('GET', '/api/reports?page=1&size=10', { token: admin.token }))
  expectPage(await request('GET', '/api/work-orders?page=1&size=10', { token: admin.token }))
  checked('Normal production-report data endpoints remain available to the factory admin', true)

  const fixtureAccount = 'security_http_csv_fixture', fixtureName = '=1+1,\n"<img data-sec=1>保存</img>"'
  const usersResponse = await request('GET', '/api/users', { token: admin.token }); expectStatus(usersResponse, 200)
  const users = jsonBody(usersResponse)
  requireThat(Array.isArray(users), 'User list is not an array')
  let fixtureUser = users.find(user => user.username === fixtureAccount)
  if (!fixtureUser) {
    const created = await request('POST', '/api/users', { token: admin.token,
      body: { username: fixtureAccount, name: fixtureName, password: 'FixtureCsvPassword7!', role: 'PRODUCTION', virtualUser: false } })
    expectStatus(created, 200); fixtureUser = jsonBody(created)
  }
  requireThat(fixtureUser.username === fixtureAccount && fixtureUser.name === fixtureName && fixtureUser.role === 'PRODUCTION'
    && Number.isSafeInteger(fixtureUser.id), 'Existing CSV fixture account does not match this checker; no unrelated account is overwritten')
  checked('A single marked local production-user record is created or reused for lossless text checks', true)
  const business = await exported(admin.token, false), explicit = await exported(admin.token, true), legacy = await exported(admin.token)
  const businessHeader = ['账号', '姓名', '角色', '手机号', '部门', '虚拟用户', '状态']
  const backupHeader = [...businessHeader.slice(0, 5), '密码哈希', ...businessHeader.slice(5)]
  requireThat(equalArray(business.rows[0], businessHeader) && equalArray(explicit.rows[0], backupHeader), 'Export modes have incorrect business/backup columns')
  const businessRow = business.rows.find(row => row[0] === fixtureAccount), backupRow = explicit.rows.find(row => row[0] === fixtureAccount)
  requireThat(businessRow?.[1] === fixtureName && backupRow?.[1] === fixtureName, 'Raw export changed formula-shaped HTML text, multiline content, or quoted fields')
  const hashes = explicit.rows.slice(1).map(row => row[5]).filter(Boolean)
  hashes.forEach(hash => privateHashes.add(hash))
  requireThat(/^\$2[aby]\$\d\d\$.{53}$/.test(backupRow[5]) && hashes.every(hash => !business.response.bytes.toString('utf8').includes(hash)), 'Business export contains a backup password hash or the fixture backup lost its hash')
  checked('The XLSX source request explicitly omits all known password hashes and preserves raw business text', true,
    { sourceFormat: 'raw CSV, not a server-generated XLSX', businessColumns: businessHeader.length, rows: business.rows.length - 1 })
  requireThat(explicit.response.bytes.equals(legacy.response.bytes), 'Explicit full backup and legacy no-query CSV export are not byte-identical')
  checked('Explicit sensitive backup and legacy CSV remain byte-identical with their hash column', true)
  log.push('Verified private raw CSV business export, explicit/legacy backup equivalence and multiline literal text; no CSV bodies/hashes logged.')

  const worker = await login('FUNCTIONALTEST', fixtureAccount, 'FixtureCsvPassword7!', true)
  requireThat(worker.user.role === 'PRODUCTION', 'Mobile fixture login did not retain its production role')
  for (const flag of ['false', 'true']) expectError(await request('GET', `/api/users/export?includePasswordHash=${flag}`, { token: worker.token }), 403)
  checked('Both user export modes remain forbidden to a real production-user session', true)
  const ordinary = expectPage(await request('GET', '/api/monitor/online?page=1&size=200', { token: admin.token }))
  requireThat(ordinary.total >= 2 && ordinary.items.length >= 2, 'The online pagination fixture did not expose the two actual local sessions')
  for (const size of [200, 2, 2147483647]) {
    const page = expectPage(await request('GET', `/api/monitor/online?page=2147483647&size=${size}`, { token: admin.token }), true)
    checked(`Maximum valid int online page with size=${size} returns a correct empty page without HTTP 500`, page.total === ordinary.total)
  }
  const hugeSize = expectPage(await request('GET', '/api/monitor/online?page=1&size=2147483647', { token: admin.token }))
  checked('Maximum int size retains the existing 200-row cap and first-page semantics', hugeSize.total === ordinary.total
    && hugeSize.items.length === Math.min(ordinary.total, 200) && hugeSize.items[0].id === ordinary.items[0].id)
  const clamped = expectPage(await request('GET', '/api/monitor/online?page=0&size=0', { token: admin.token }))
  checked('Nonpositive pagination retains the existing first-page/one-row clamp', clamped.total === ordinary.total
    && clamped.items.length === 1 && clamped.items[0].id === ordinary.items[0].id)
  expectError(await request('GET', '/api/monitor/online?page=2147483647&size=200'), 401)
  expectError(await request('GET', '/api/monitor/online?page=2147483647&size=200', { token: worker.token }), 403)
  checked('Extreme online pagination does not bypass anonymous/admin authorization', true)

  const newerAdmin = await login('FUNCTIONALTEST', 'admin', '123456')
  requireThat(newerAdmin.token !== admin.token && newerAdmin.user.id === admin.user.id, 'Second administrator login did not create a distinct session')
  await logout(admin.token); await logout(admin.token)
  expectError(await request('GET', '/api/mobile/work-orders', { token: admin.token }), 401)
  expectPage(await request('GET', '/api/mobile/work-orders', { token: newerAdmin.token }))
  checked('Repeated logout of an older kicked token cannot revoke the same user\'s newer login', true)
  expectPage(await request('GET', '/api/mobile/work-orders', { token: worker.token }))
  checked('Older administrator logout leaves the other local user session valid', true)
  await logout(newerAdmin.token)
  expectError(await request('GET', '/api/mobile/work-orders', { token: newerAdmin.token }), 401)
  checked('Confirmed current logout immediately invalidates its real API session', true)
  await logout(newerAdmin.token); await logout(); await logout('not-a-real-security-fixture-session')
  checked('Already revoked, anonymous and unknown logout are idempotent no-store operations without Set-Cookie', true)

  const superAdmin = await login('ADMIN', 'admin', 'test-super-admin-pass')
  requireThat(superAdmin.user.role === 'ADMIN' && superAdmin.isSuperAdmin === true, 'Platform fixture login has an incorrect role/factory flag')
  const report = await bridge(superAdmin.token)
  checked('Platform superadmin bridge establishes a scoped HttpOnly SameSite report cookie without body token', true, { cookie: report.properties })
  await designer(report.pair)
  checked('The cookie-authorized platform session reaches actual native UReport designer HTML', true)
  expectError(await request('GET', '/ureport/designer', { token: superAdmin.token }), 401)
  checked('A bearer header alone cannot bypass the native report-cookie bridge', true)
  await logout(undefined, report.pair)
  await designer(report.pair)
  checked('Ambient report cookie without Authorization is not a logout credential and remains usable', true)

  const newerSuperAdmin = await login('ADMIN', 'admin', 'test-super-admin-pass'), newerReport = await bridge(newerSuperAdmin.token)
  requireThat(newerSuperAdmin.token !== superAdmin.token, 'Platform re-login did not rotate its session')
  await logout(superAdmin.token, newerReport.pair)
  await designer(newerReport.pair)
  expectError(await request('GET', '/ureport/designer', { cookie: report.pair }), 401)
  checked('Late older platform logout neither overwrites nor revokes the newer report cookie/session', true)
  await logout(newerSuperAdmin.token)
  expectError(await request('GET', '/ureport/designer', { cookie: newerReport.pair }), 401)
  expectError(await request('GET', '/api/ureport/session', { token: newerSuperAdmin.token }), 401)
  checked('Current platform logout immediately rejects its existing native cookie and bridge bearer', true)
  await logout(worker.token)
  expectError(await request('GET', '/api/mobile/work-orders', { token: worker.token }), 401)
  checked('The production role can revoke its own precise current session', true)
  log.push('Verified live pagination, administrator/production/platform role boundaries, exact logout and native-cookie lifecycle.')
}

let failure
try { await main() }
catch (error) { failure = { name: error?.name || 'Error', message: error instanceof AuditFailure ? error.message : 'Unexpected local validation failure; no sensitive diagnostic payload is recorded' } }
// Revoke only sessions minted by this checker on an early failure. Never use an
// ambient cookie, shared account identity, or a broad factory logout operation.
if (failure && origin && processIdentity) {
  for (const token of issuedTokens) { try { await logout(token) } catch { /* Failure is preserved; cleanup is best-effort and local. */ } }
}
mkdirSync(outputDir, { recursive: true })
const result = { fixtureOnly: true, fixture: 'real-h2-api', completed: !failure, evidenceType: 'real Spring Boot HTTP / isolated in-memory H2 / native vendor UReport GET; not mocked API',
  apiOrigin: origin, runtimeMetadata: runtimeFile, process: processIdentity, frozenClassHashes,
  passed: checks.length, failed: failure ? [failure] : [], checks, requests,
  limits: ['Only the recorded Windows loopback H2 JVM is accepted; redirects and arbitrary URLs are refused.',
    'One marked production-user fixture is created once/reused; no production database or external origin is contacted.',
    'Raw CSV is tested as the client XLSX source, not as a safe Excel file or server-generated XLSX.',
    'Native designer GET and cookie boundary only: no template save/delete, datasource execution, report rendering/export, or TLS proxy/CSRF claim.',
    'No real MySQL, Redis, SMS, WeChat, production tenants, billions-of-session allocation, or complete application-security claim.',
    'No credentials, bearer tokens, cookie values, raw CSV bodies, or password hashes are written to results/logs.'] }
const privateValues = [...issuedTokens, ...privateHashes]
const draft = JSON.stringify(result)
result.sensitiveOutputGuardPassed = privateValues.every(value => !draft.includes(value))
if (!result.sensitiveOutputGuardPassed) {
  failure = { name: 'AuditFailure', message: 'Sensitive output guard rejected internal diagnostic serialization' }
  result.completed = false; result.failed = [failure]
}
const redact = text => privateValues.reduce((safe, value) => safe.replaceAll(value, '[REDACTED]'), text)
writeFileSync(resultFile, redact(JSON.stringify(result, null, 2)))
log.push(JSON.stringify({ completed: result.completed, passed: result.passed, failed: result.failed, requests: requests.length }))
writeFileSync(logFile, redact(log.join('\n') + '\n'))
console.log(JSON.stringify({ completed: result.completed, passed: result.passed, failed: result.failed, requests: requests.length, resultFile, logFile }))
if (failure) process.exitCode = 1

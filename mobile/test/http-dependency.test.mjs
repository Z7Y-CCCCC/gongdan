import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import dns from 'node:dns'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import http from 'node:http'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { Worker } from 'node:worker_threads'
import { deflateRawSync, deflateSync } from 'node:zlib'
import { after, before, test } from 'node:test'

const require = createRequire(import.meta.url)
// Resolve the actual application dependencies, not a substituted test client.
const platformFetch = globalThis.fetch
const Jimp = require('jimp')
const records = new Map()
const nativeLookup = dns.lookup
let source, destination, sourceOrigin, destinationOrigin
let sequence = 0

const credentials = {
  authorization: 'Bearer isolated-http-regression-secret',
  cookie: 'fixture-session=must-not-cross-origin',
  'proxy-authorization': 'Basic isolated-proxy-credential'
}
const headerVariants = [
  ['standard case', ['Authorization', 'Cookie', 'Proxy-Authorization']],
  ['lowercase', ['authorization', 'cookie', 'proxy-authorization']],
  ['uppercase', ['AUTHORIZATION', 'COOKIE', 'PROXY-AUTHORIZATION']],
  ['mixed case', ['aUtHoRiZaTiOn', 'cOoKiE', 'pRoXy-AuThOrIzAtIoN']]
]

function imageBuffer() {
  function chunk(name, data) {
    const type = Buffer.from(name)
    let crc = 0xffffffff
    for (const byte of Buffer.concat([type, data])) {
      crc ^= byte
      for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0)
    }
    const length = Buffer.alloc(4), checksum = Buffer.alloc(4)
    length.writeUInt32BE(data.length)
    checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0)
    return Buffer.concat([length, type, data, checksum])
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(1, 0)
  header.writeUInt32BE(1, 4)
  header[8] = 8
  header[9] = 6
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.from([0, 255, 0, 0, 255]))), chunk('IEND', Buffer.alloc(0))
  ])
}
const png = imageBuffer()

function deadline(promise, label, timeout = 3500) {
  let timer
  const limited = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label} did not settle within the test deadline`)), timeout)
  })
  return Promise.race([promise, limited]).finally(() => clearTimeout(timer))
}

function handler(serverName) {
  return (req, res) => {
    const url = new URL(req.url, 'http://fixture.invalid')
    const id = url.searchParams.get('id') || ''
    const history = records.get(id) || []
    history.push({
      server: serverName, pathname: url.pathname, headers: { ...req.headers }, rawHeaders: [...req.rawHeaders],
      closed: new Promise(resolve => res.once('close', resolve))
    })
    records.set(id, history)
    if (url.pathname.startsWith('/redirect/')) {
      const [, , kind, result] = url.pathname.split('/')
      let base = kind === 'cross' ? destinationOrigin : sourceOrigin
      if (kind === 'host') base = `http://other.fixture.test:${source.address().port}`
      if (kind === 'subdomain') base = `http://child.fixture.test:${source.address().port}`
      if (kind === 'userinfo') base = destinationOrigin.replace('://', '://fixture-user:fixture-password@')
      const location = kind === 'relative' ? `/${result}?id=${id}` : `${base}/${result}?id=${id}`
      res.writeHead(Number(url.searchParams.get('status')) || 302, { Location: location })
      return res.end('redirecting')
    }
    if (url.pathname === '/disconnect') return req.socket.destroy()
    if (url.pathname === '/loop') {
      res.writeHead(302, { Location: `/loop?id=${id}` })
      return res.end()
    }
    if (url.pathname === '/return-image') {
      res.writeHead(302, { Location: `${sourceOrigin}/image?id=${id}` })
      return res.end()
    }
    if (url.pathname === '/slow-headers') return
    if (url.pathname === '/slow-body') {
      res.writeHead(200, { 'Content-Type': 'image/png' })
      res.write(png.subarray(0, 1))
      const timer = setInterval(() => res.write(png.subarray(0, 1)), 20)
      res.once('close', () => clearInterval(timer))
      return
    }
    if (url.pathname === '/not-found') {
      res.writeHead(404)
      return res.end('fixture image not found')
    }
    if (url.pathname === '/image' || url.pathname === '/image-with-location') {
      const headers = { 'Content-Type': 'image/png', 'Content-Length': png.length }
      if (url.pathname === '/image-with-location') headers.Location = `${destinationOrigin}/image?id=${id}`
      res.writeHead(200, headers)
      return res.end(png)
    }
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ headers: req.headers, server: serverName }))
  }
}

async function listen(server) {
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  return `http://fixture.test:${server.address().port}`
}

before(async () => {
  // Only DNS is redirected, never fetch or Jimp. Reserved .test hosts reach our
  // loopback sockets; an unexpected hostname is rejected instead of using DNS.
  dns.lookup = (hostname, options, callback) => {
    if (typeof options === 'function') { callback = options; options = {} }
    if (!['127.0.0.1', 'fixture.test', 'child.fixture.test', 'other.fixture.test'].includes(hostname)) {
      return queueMicrotask(() => callback(new Error(`Unexpected fixture hostname: ${hostname}`)))
    }
    queueMicrotask(() => options?.all
      ? callback(null, [{ address: '127.0.0.1', family: 4 }])
      : callback(null, '127.0.0.1', 4))
  }
  destination = http.createServer(handler('destination'))
  destinationOrigin = await listen(destination)
  source = http.createServer(handler('source'))
  sourceOrigin = await listen(source)
})

after(async () => {
  try {
    await Promise.all([source, destination].filter(Boolean).map(server => {
      server.closeAllConnections()
      return new Promise(resolve => server.close(resolve))
    }))
  } finally { dns.lookup = nativeLookup }
})

function caseOptions(names, kind, result = 'image', status = 302, origin = sourceOrigin) {
  const id = String(++sequence)
  const headers = Object.fromEntries(names.map(name => [name, credentials[name.toLowerCase()]]))
  headers['X-Regression-Marker'] = id
  return { id, headers, url: `${origin}/redirect/${kind}/${result}?id=${id}&status=${status}` }
}

function sentCredentials(record) {
  for (const [name, value] of Object.entries(credentials)) assert.equal(record.headers[name], value, `source did not receive ${name}; test would be vacuous`)
}

function withheldCredentials(record) {
  for (const name of Object.keys(credentials)) assert.equal(record.headers[name], undefined, `${name} leaked across an origin boundary`)
}

function readImage(options) {
  // Jimp 0.22.12 uses Fetch options. A real AbortSignal, not a Promise.race or
  // the removed phin timeout option, must cancel the underlying slow request.
  return deadline(Jimp.read({ signal: AbortSignal.timeout(1500), ...options }), 'Jimp remote image')
}

test('Jimp replaces the vulnerable phin image loader while retaining the platform Fetch implementation', () => {
  assert.equal(require('jimp/package.json').version, '0.22.12')
  const jimpRequire = createRequire(require.resolve('jimp'))
  const customRequire = createRequire(jimpRequire.resolve('@jimp/custom'))
  const coreRequire = createRequire(customRequire.resolve('@jimp/core'))
  const core = coreRequire('../package.json')
  assert.equal(core.version, '0.22.12')
  assert.equal(core.dependencies.phin, undefined, 'the old callback HTTP dependency must be removed, not renamed')
  assert.equal(typeof platformFetch, 'function', 'run the mobile toolchain on its supported Node 22+ runtime')
  assert.equal(globalThis.fetch, platformFetch, 'isomorphic-fetch must not replace native Fetch on Node 22+')
  assert.notEqual(coreRequire('isomorphic-fetch'), globalThis.fetch, 'distinguish the unused node-fetch fallback from global Fetch')
})

for (const [label, names] of headerVariants) {
  test(`Jimp strips credentials across port, host and subdomain boundaries (${label}; 301/302/303/307/308)`, async () => {
    for (const kind of ['cross', 'host', 'subdomain']) {
      for (const status of [301, 302, 303, 307, 308]) {
        const { id, headers, url } = caseOptions(names, kind, 'image', status)
        const original = { ...headers }
        Object.freeze(headers)
        assert.equal((await readImage({ url, headers })).bitmap.width, 1)
        const history = records.get(id)
        assert.equal(history.length, 2, 'must decode the redirected image, not only test the first response')
        sentCredentials(history[0])
        if (kind === 'cross') assert.equal(history[1].server, 'destination')
        if (kind === 'host') assert.equal(history[1].headers.host, `other.fixture.test:${source.address().port}`)
        if (kind === 'subdomain') assert.equal(history[1].headers.host, `child.fixture.test:${source.address().port}`)
        withheldCredentials(history[1])
        assert.equal(history[1].headers['x-regression-marker'], id)
        assert.deepEqual(headers, original, 'redirect filtering must not mutate caller-owned headers')
      }
    }
  })
}

test('Jimp preserves credentials for same-origin absolute and relative redirects', async () => {
  for (const [, names] of headerVariants) {
    for (const kind of ['same', 'relative']) {
      const { id, headers, url } = caseOptions(names, kind)
      assert.equal((await readImage({ url, headers })).bitmap.width, 1)
      const history = records.get(id)
      assert.equal(history.length, 2)
      history.forEach(sentCredentials)
    }
  }
})

test('Jimp does not restore stripped credentials when a redirect returns to the original origin', async () => {
  const { id, headers, url } = caseOptions(headerVariants[3][1], 'cross', 'return-image')
  assert.equal((await readImage({ url, headers })).bitmap.width, 1)
  const history = records.get(id)
  assert.equal(history.length, 3)
  sentCredentials(history[0])
  withheldCredentials(history[1])
  withheldCredentials(history[2])
})

test('Jimp respects Fetch redirect=manual/error without its old Location-recursion bypass', async () => {
  for (const redirect of ['manual', 'error']) {
    const { id, headers, url } = caseOptions(headerVariants[0][1], 'cross')
    await assert.rejects(readImage({ url, headers, redirect }), /HTTP Status 302|fetch failed|redirect/i)
    assert.equal(records.get(id).length, 1)
    sentCredentials(records.get(id)[0])
  }
})

test('Jimp ignores Location on a successful PNG response instead of leaking headers through recursion', async () => {
  const id = String(++sequence)
  const headers = { ...credentials }
  assert.equal((await readImage({ url: `${sourceOrigin}/image-with-location?id=${id}`, headers })).bitmap.width, 1)
  assert.equal(records.get(id).length, 1)
  sentCredentials(records.get(id)[0])
})

test('Jimp retains the uni compiler Buffer-read API and decoded bitmap', async () => {
  assert.equal(typeof Jimp.read, 'function', 'uni requires require("jimp").read, not a differently named export')
  const image = await deadline(Jimp.read(png), 'Jimp Buffer read')
  assert.equal(image.bitmap.width, 1)
  assert.equal(image.bitmap.height, 1)
  assert.deepEqual([...image.bitmap.data], [255, 0, 0, 255])
})

test('file-type bridge retains the Jimp Promise API and delegates to the pinned patched parser', async () => {
  const FileType = require('file-type')
  assert.equal(require('file-type/package.json').name, '@gongdan/uni-file-type-adapter')
  const adapterRequire = createRequire(require.resolve('file-type'))
  const modernPackage = JSON.parse(readFileSync(path.join(path.dirname(adapterRequire.resolve('file-type-modern')), 'package.json'), 'utf8'))
  assert.equal(modernPackage.name, 'file-type')
  assert.equal(modernPackage.version, '21.3.4')
  assert.deepEqual(Object.keys(FileType), ['fromBuffer'], 'do not imply compatibility with unused legacy stream APIs')
  for (const input of [png, Uint8Array.from(png), Uint8Array.from(png).buffer]) {
    const result = FileType.fromBuffer(input)
    assert.equal(typeof result.then, 'function')
    assert.deepEqual(await result, { ext: 'png', mime: 'image/png' })
  }
  assert.equal(await FileType.fromBuffer(Buffer.alloc(0)), undefined)
  await assert.rejects(FileType.fromBuffer('not a byte buffer'), TypeError)
})

test('Jimp JPEG and PNG decoding remain compatible with the modern type detector', async () => {
  const jpeg = require('jpeg-js')
  const bytes = jpeg.encode({ data: Buffer.alloc(16, 255), width: 2, height: 2 }, 75).data
  assert.deepEqual(await require('file-type').fromBuffer(bytes), { ext: 'jpg', mime: 'image/jpeg' })
  const image = await deadline(Jimp.read(bytes), 'Jimp JPEG compatibility')
  assert.equal(image.bitmap.width, 2)
  assert.equal(image.bitmap.height, 2)
  assert.equal(image.bitmap.data.length, 16)
  const pngRoundTrip = await image.getBufferAsync(Jimp.MIME_PNG)
  assert.equal((await deadline(Jimp.read(pngRoundTrip), 'Jimp PNG round trip')).bitmap.width, 2)
})

test('uni QR decoding still accepts Jimp bitmap data and decodes the public Test fixture', async () => {
  const QrCode = require('qrcode-reader')
  // qrcode-reader ships this public "Test" image in its own test fixtures. It
  // contains no account/session data; the base64 round trip matches uni's API.
  const fixture = readFileSync(require.resolve('qrcode-reader/test/image.png'))
  const image = await deadline(Jimp.read(Buffer.from(fixture.toString('base64'), 'base64')), 'uni QR bitmap')
  const result = await deadline(new Promise((resolve, reject) => {
    const qr = new QrCode()
    qr.callback = (error, value) => error ? reject(error) : resolve(value)
    qr.decode(image.bitmap)
  }), 'uni QR decode')
  assert.equal(result.result, 'Test')
})

async function inspectUntrustedBufferInWorker() {
  const { parentPort, workerData } = require('node:worker_threads')
  const FileType = require(workerData.detector)
  const Jimp = require(workerData.jimp)
  const buffer = Buffer.from(workerData.buffer)
  const type = await FileType.fromBuffer(buffer)
  let jimpError
  try { await Jimp.read(buffer) } catch (error) { jimpError = error.message }
  parentPort.postMessage({ type, jimpError })
}

async function inspectUntrustedBuffer(buffer) {
  // A bad dependency pin can busy-loop the parser before any timer runs. Keep
  // these regressions in disposable workers, not the main test event loop.
  const code = `(${inspectUntrustedBufferInWorker.toString()})().catch(error => { throw error })`
  const worker = new Worker(code, {
    eval: true,
    workerData: { detector: require.resolve('file-type'), jimp: require.resolve('jimp'), buffer },
    resourceLimits: { maxOldGenerationSizeMb: 64, maxYoungGenerationSizeMb: 16 }
  })
  let timer
  try {
    return await new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error('file-type/Jimp failed to settle on a bounded malformed fixture')), 3500)
      worker.once('message', resolve)
      worker.once('error', reject)
      worker.once('exit', code => reject(new Error(`file-type worker exited without a result (${code})`)))
    })
  } finally {
    clearTimeout(timer)
    await worker.terminate()
  }
}

test('patched file-type and real Jimp settle on the published 55-byte zero-size ASF regression', async () => {
  // GHSA-5v7r-6r5c-r473 / upstream commit 319abf871b50ba2fa221b4a7050059f1ae096f4f.
  const malformedAsf = Buffer.alloc(55)
  Buffer.from('3026b2758e66cf11a6d9', 'hex').copy(malformedAsf)
  const advancingAsf = Buffer.from(malformedAsf)
  advancingAsf.writeBigUInt64LE(24n, 46)
  assert.deepEqual(await require('file-type').fromBuffer(advancingAsf), { ext: 'asf', mime: 'application/vnd.ms-asf' }, 'the control must actually reach the ASF parser')
  const result = await inspectUntrustedBuffer(malformedAsf)
  // 21.3.4 additionally declines to classify malformed ASF headers. Earlier
  // 21.3.1 merely broke the loop and returned generic ASF; neither may hang.
  assert.equal(result.type, undefined)
  assert.match(result.jimpError, /Could not find MIME for Buffer/)
})

function zipProbeEntry(filename, payload, declaredSize = payload.length) {
  const name = Buffer.from(filename)
  const compressed = deflateRawSync(payload)
  const header = Buffer.alloc(30 + name.length)
  header.writeUInt32LE(0x04034b50, 0)
  header.writeUInt16LE(20, 4)
  header.writeUInt16LE(8, 8)
  header.writeUInt32LE(compressed.length, 18)
  header.writeUInt32LE(declaredSize, 22)
  header.writeUInt16LE(name.length, 26)
  name.copy(header, 30)
  return Buffer.concat([header, compressed])
}

test('patched file-type bounds known-size ZIP inflation even when entry metadata understates the size', async () => {
  // GHSA-j47w-4g3g-c36v. Only 1 MiB + 1 of highly compressible fixture data is
  // used, not the advisory's large memory-stress payload.
  const epub = Buffer.from('application/epub+zip')
  assert.deepEqual(await require('file-type').fromBuffer(zipProbeEntry('mimetype', epub)), { ext: 'epub', mime: 'application/epub+zip' })
  for (const [filename, text] of [
    ['mimetype', epub.toString()],
    ['[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8"?><Types><Override ContentType="application/vnd.ms-word.document.macroenabled.main+xml"/></Types>']
  ]) {
    const payload = Buffer.alloc(1024 * 1024 + 1, 0x20)
    payload.write(text)
    const file = zipProbeEntry(filename, payload, 1)
    assert.ok(file.length < 4096, 'keep the compressed test fixture small and bounded')
    const result = await inspectUntrustedBuffer(file)
    assert.deepEqual(result.type, { ext: 'zip', mime: 'application/zip' }, 'oversized probes must not be inflated into an EPUB/OOXML classification')
    assert.match(result.jimpError, /Unsupported MIME type: application\/zip/)
  }
})

test('Jimp remote URL strings still decode an image', async () => {
  const url = `${sourceOrigin}/image?id=${++sequence}`
  assert.equal((await deadline(Jimp.read(url), 'Jimp remote URL read')).bitmap.width, 1)
})

test('Jimp constructor/read callbacks report success and failure exactly once', async () => {
  for (const useConstructor of [true, false]) {
    for (const pathname of ['/image', '/disconnect', '/not-found']) {
      let calls = 0
      const options = { url: sourceOrigin + pathname, signal: AbortSignal.timeout(1500) }
      const outcome = await deadline(new Promise(resolve => {
        const callback = (error, image) => { calls += 1; resolve({ error, image }) }
        if (useConstructor) new Jimp(options, callback)
        else Jimp.read(options, callback)
      }), 'Jimp legacy callback')
      await new Promise(resolve => setImmediate(resolve))
      assert.equal(calls, 1)
      if (pathname === '/image') {
        assert.equal(outcome.error, null)
        assert.equal(outcome.image.bitmap.width, 1)
      } else assert.ok(outcome.error instanceof Error)
    }
  }
})

test('Jimp safely rejects embedded URL credentials, including cross-origin Location credentials', async () => {
  const id = String(++sequence)
  const url = `${sourceOrigin.replace('://', '://fixture-user:fixture-password@')}/image?id=${id}`
  await assert.rejects(readImage({ url }), /credentials|URL/i)
  assert.equal(records.has(id), false, 'URL credentials must be rejected before the first network request')
  const redirected = caseOptions(headerVariants[0][1], 'userinfo')
  await assert.rejects(readImage({ url: redirected.url, headers: redirected.headers }), /credentials|fetch failed|redirect/i)
  assert.equal(records.get(redirected.id).length, 1)
  sentCredentials(records.get(redirected.id)[0])
})

test('Jimp default redirects stop after the native Fetch limit rather than looping indefinitely', async () => {
  const id = String(++sequence)
  await assert.rejects(readImage({ url: `${sourceOrigin}/loop?id=${id}` }), /redirect|fetch failed/i)
  assert.ok(records.get(id).length > 1)
  assert.ok(records.get(id).length <= 21, 'native Fetch permits at most twenty redirects')
})

test('Jimp Fetch AbortSignal cancels both stalled headers and an endless slow response body', async () => {
  for (const pathname of ['/slow-headers', '/slow-body']) {
    const id = String(++sequence)
    const started = performance.now()
    await assert.rejects(readImage({ url: `${sourceOrigin}${pathname}?id=${id}`, signal: AbortSignal.timeout(120) }), /abort|timeout/i)
    assert.ok(performance.now() - started < 1500, 'the transport must stop, not merely the test Promise')
    assert.equal(records.get(id).length, 1)
    await deadline(records.get(id)[0].closed, 'aborted image connection cleanup', 1500)
  }
})

// HTTPS must be exercised with actual certificate verification. A short-lived
// self-signed fixture is trusted only by this child process via EXTRA_CA_CERTS;
// neither the application nor the test suite disables TLS verification.
async function verifyTlsRedirects() {
  const { createRequire } = await import('node:module')
  const { readFileSync } = await import('node:fs')
  const http = await import('node:http')
  const https = await import('node:https')
  const { default: assert } = await import('node:assert/strict')
  const require = createRequire(import.meta.url)
  const Jimp = require(process.argv[1])
  const certificate = readFileSync(process.argv[2])
  const key = readFileSync(process.argv[3])
  const png = Buffer.from(process.argv[4], 'base64')
  const credentials = { authorization: 'Bearer tls-fixture', cookie: 'tls-fixture=1', 'proxy-authorization': 'Basic tls-fixture' }
  const variants = [
    ['Authorization', 'Cookie', 'Proxy-Authorization'],
    ['authorization', 'cookie', 'proxy-authorization'],
    ['AUTHORIZATION', 'COOKIE', 'PROXY-AUTHORIZATION'],
    ['aUtHoRiZaTiOn', 'cOoKiE', 'pRoXy-AuThOrIzAtIoN']
  ]
  const history = new Map()
  let sequence = 0, origin, destinationOrigin
  const handler = (site) => (req, res) => {
    const url = new URL(req.url, 'https://fixture.invalid')
    const id = url.searchParams.get('id')
    const requests = history.get(id) || []
    requests.push({ site, headers: req.headers })
    history.set(id, requests)
    if (url.pathname === '/image') {
      res.writeHead(200, { 'Content-Type': 'image/png', 'Content-Length': png.length })
      res.end(png)
    } else {
      const base = url.pathname === '/same' ? origin : destinationOrigin
      res.writeHead(Number(url.searchParams.get('status')) || 302, { Location: `${base}/image?id=${id}` })
      res.end()
    }
  }
  const destination = http.createServer(handler('http'))
  const source = https.createServer({ cert: certificate, key }, handler('https'))
  try {
    await Promise.all([source, destination].map(server => new Promise((resolve, reject) => {
      server.once('error', reject)
      server.listen(0, '127.0.0.1', resolve)
    })))
    origin = `https://127.0.0.1:${source.address().port}`
    destinationOrigin = `http://127.0.0.1:${destination.address().port}`
    for (const names of variants) {
      for (const status of [301, 302, 303, 307, 308]) {
        const id = String(++sequence)
        const headers = Object.fromEntries(names.map(name => [name, credentials[name.toLowerCase()]]))
        const image = await Jimp.read({ url: `${origin}/downgrade?id=${id}&status=${status}`, headers, signal: AbortSignal.timeout(1500) })
        assert.equal(image.bitmap.width, 1)
        const requests = history.get(id)
        assert.equal(requests.length, 2)
        assert.equal(requests[0].site, 'https')
        assert.equal(requests[1].site, 'http')
        for (const [name, value] of Object.entries(credentials)) {
          assert.equal(requests[0].headers[name], value, 'TLS source must receive fixture credentials')
          assert.equal(requests[1].headers[name], undefined, `${name} leaked on HTTPS downgrade`)
        }
      }
    }
    const id = String(++sequence)
    const image = await Jimp.read({ url: `${origin}/same?id=${id}`, headers: credentials, signal: AbortSignal.timeout(1500) })
    assert.equal(image.bitmap.width, 1)
    assert.equal(history.get(id).length, 2)
    for (const request of history.get(id)) {
      for (const [name, value] of Object.entries(credentials)) assert.equal(request.headers[name], value)
    }
    process.stdout.write(JSON.stringify({ verifiedTlsCases: sequence }))
  } finally {
    await Promise.all([source, destination].map(server => {
      server.closeAllConnections()
      return new Promise(resolve => server.close(resolve))
    }))
  }
}

test('Jimp verifies HTTPS, strips credentials on all downgrade redirects, and preserves same-origin TLS auth', { timeout: 15000 }, () => {
  const candidates = [process.env.OPENSSL_BIN, 'openssl', 'C:\\Program Files\\Git\\usr\\bin\\openssl.exe'].filter(Boolean)
  const openssl = candidates.find(candidate => spawnSync(candidate, ['version'], { windowsHide: true, timeout: 2000 }).status === 0)
  assert.ok(openssl, 'HTTPS regression fixtures require OpenSSL (PATH, Git for Windows, or OPENSSL_BIN)')
  const fixture = mkdtempSync(path.join(tmpdir(), 'heihu-image-tls-'))
  try {
    const certPath = path.join(fixture, 'cert.pem')
    const keyPath = path.join(fixture, 'key.pem')
    const generated = spawnSync(openssl, [
      'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', keyPath, '-out', certPath,
      '-days', '1', '-subj', '/CN=heihu-loopback-fixture', '-addext', 'subjectAltName=IP:127.0.0.1'
    ], { encoding: 'utf8', windowsHide: true, timeout: 10000 })
    assert.equal(generated.status, 0, generated.error?.message || generated.stderr)
    const code = `(${verifyTlsRedirects.toString()})().catch(error => { console.error(error); process.exitCode = 1 })`
    const result = spawnSync(process.execPath, [
      '--input-type=module', '-e', code, require.resolve('jimp'), certPath, keyPath, png.toString('base64')
    ], {
      cwd: path.dirname(require.resolve('jimp')), encoding: 'utf8', windowsHide: true, timeout: 10000,
      env: { ...process.env, NODE_EXTRA_CA_CERTS: certPath, NODE_TLS_REJECT_UNAUTHORIZED: '1' }
    })
    assert.equal(result.status, 0, result.error?.message || result.stderr)
    assert.equal(JSON.parse(result.stdout).verifiedTlsCases, 21)
  } finally {
    // This directory is returned by mkdtemp above and contains only this test's
    // disposable certificate/key; never use a user-provided cleanup directory.
    rmSync(fixture, { recursive: true, force: true })
  }
})

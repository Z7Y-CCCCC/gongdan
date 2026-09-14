import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { request } from 'node:http'
import { once } from 'node:events'
import { setTimeout as delay } from 'node:timers/promises'
import { createRequire } from 'node:module'
import { createServer, createLogger } from 'vite'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const normalizePath = value => value.replaceAll('\\', '/')
const require = createRequire(import.meta.url)
const { WebSocket } = require('ws')
const { SourceMapConsumer } = require('source-map-js')

function rawRequest(origin, requestPath, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = request(new URL(origin), { path: requestPath, headers }, res => {
      let body = ''
      res.setEncoding('utf8')
      res.on('data', chunk => { body += chunk })
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }))
    })
    req.on('error', reject)
    req.setTimeout(15000, () => req.destroy(new Error(`Timed out requesting ${requestPath}`)))
    req.end()
  })
}

async function moduleResponse(origin, url) {
  for (let attempt = 0; attempt < 40; attempt++) {
    const response = await rawRequest(origin, url)
    if (response.status !== 504) return response
    await delay(100)
  }
  throw new Error(`Dependency optimization never settled: ${url}`)
}

function inlineMap(code) {
  const match = code.match(/\/\/# sourceMappingURL=data:application\/json;(?:charset=utf-8;)?base64,([^\s]+)/)
  assert.ok(match, 'transformed module must include its inline source map')
  return JSON.parse(Buffer.from(match[1], 'base64').toString('utf8'))
}

function waitForMessage(socket, predicate, timeout = 15000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => finish(new Error('Timed out waiting for HMR update')), timeout)
    const onMessage = data => {
      const message = JSON.parse(data.toString())
      if (message.type === 'error') finish(new Error(message.err?.message || 'Vite HMR error'))
      else if (predicate(message)) finish(null, message)
    }
    const onError = error => finish(error)
    function finish(error, message) {
      clearTimeout(timer)
      socket.off('message', onMessage)
      socket.off('error', onError)
      error ? reject(error) : resolve(message)
    }
    socket.on('message', onMessage)
    socket.on('error', onError)
  })
}

function assertWebSocketRejected(url, options) {
  return new Promise((resolve, reject) => {
    const client = new WebSocket(url, 'vite-hmr', { ...options, handshakeTimeout: 3000 })
    let settled = false
    function finish(error) {
      if (settled) return
      settled = true
      client.terminate()
      error ? reject(error) : resolve()
    }
    client.on('open', () => {
      finish(new Error('Untrusted WebSocket handshake was accepted'))
    })
    // A timeout, disconnected server, or connection error is not evidence that
    // the security check rejected a handshake. Require its actual HTTP denial.
    client.on('error', finish)
    client.on('unexpected-response', (_request, response) => {
      response.resume()
      finish([400, 403].includes(response.statusCode)
        ? undefined
        : new Error(`Expected HTTP 400/403 handshake denial, got ${response.statusCode}`))
    })
    client.on('close', () => finish(new Error('WebSocket closed without an explicit HTTP handshake denial')))
  })
}

async function removeOwnedFixture(directory, parent, prefix) {
  if (!directory) return
  const resolved = path.resolve(directory)
  const relative = path.relative(path.resolve(parent), resolved)
  assert.equal(path.dirname(relative), '.', 'cleanup target must be a direct child of its intended temporary parent')
  assert.ok(path.basename(relative).startsWith(prefix), 'cleanup target must have the mkdtemp prefix')
  await rm(resolved, { recursive: true, force: true })
}

test('Vite 6 runs the real H5 config with source maps, HMR and closed security boundaries', { timeout: 120000 }, async t => {
  process.chdir(root)
  process.env.NODE_ENV = 'development'
  process.env.UNI_PLATFORM = 'h5'
  process.env.VITE_DEV_HOST = '127.0.0.1'
  process.env.VITE_PUBLIC_BASE_URL = '/'
  const fixtureParent = path.join(root, 'src')
  const outsideParent = tmpdir()
  let fixture, outside, server, socket
  // Register cleanup before the first allocation/write, including failed setup.
  t.after(async () => {
    try {
      socket?.terminate()
    } finally {
      try {
        await server?.close()
      } finally {
        // Only direct children returned by our mkdtemp calls may be removed.
        await Promise.all([
          removeOwnedFixture(fixture, fixtureParent, '.toolchain-regression-'),
          removeOwnedFixture(outside, outsideParent, 'gongdan-vite-denied-')
        ])
      }
    }
  })
  fixture = await mkdtemp(path.join(fixtureParent, '.toolchain-regression-'))
  outside = await mkdtemp(path.join(outsideParent, 'gongdan-vite-denied-'))
  const secret = `toolchain-denied-${path.basename(outside)}`
  const secretFile = path.join(outside, 'denied.txt')
  const envFile = path.join(fixture, '.env.local')
  const scriptFile = path.join(fixture, 'marker.ts')
  const vueFile = path.join(fixture, 'Probe.vue')
  const fixtureUrl = `/src/${path.basename(fixture)}`
  const scriptUrl = `${fixtureUrl}/marker.ts`
  const vueUrl = `${fixtureUrl}/Probe.vue`
  const marker = "export const message: string = 'toolchain-hmr-before'\nif (import.meta.hot) import.meta.hot.accept()\n"
  const source = '<script setup lang="ts">\nconst greeting: string = "toolchain-vue-before"\n</script>\n<template><view>{{ greeting }}</view></template>\n<style scoped>.probe { padding: 20rpx; }</style>\n'
  await Promise.all([writeFile(scriptFile, marker), writeFile(vueFile, source), writeFile(secretFile, secret), writeFile(envFile, secret)])
  const errors = []
  const logger = createLogger('warn')
  logger.error = message => errors.push(message)
  server = await createServer({ root, configFile: path.join(root, 'vite.config.ts'), customLogger: logger, server: { host: '127.0.0.1', port: 0, strictPort: true } })
  // Vite 6 treats port: 0 as its default port in server.listen(). The public
  // HTTP server accepts a real ephemeral port, avoiding any user's dev port.
  const listening = once(server.httpServer, 'listening')
  server.httpServer.listen(0, '127.0.0.1')
  await listening
  const address = server.httpServer.address()
  assert.equal(address.address, '127.0.0.1')
  const origin = `http://127.0.0.1:${address.port}`

  await t.test('resolved config keeps FS, host, CORS and WebSocket protections enabled', async () => {
    assert.equal(server.config.server.fs.strict, true)
    assert.notEqual(server.config.server.allowedHosts, true)
    assert.notEqual(server.config.server.cors, true)
    assert.notEqual(server.config.legacy?.skipWebSocketTokenCheck, true)
    // The app's manual H5 bootstrap does not register the old H5 compiler,
    // whose private Vite 5 request-cache hook is incompatible with Vite 6.
    assert.ok(!server.config.plugins.some(plugin => plugin.name === 'uni:post-sourcemap'))
    const index = await rawRequest(origin, '/')
    assert.equal(index.status, 200)
    assert.match(index.body, /\/src\/main\.ts/)
    const bootstrap = await rawRequest(origin, '/uni-bootstrap.js')
    assert.equal(bootstrap.status, 200)
    assert.match(bootstrap.headers['content-type'], /javascript/)
    assert.match(bootstrap.body, /__UNI_FEATURE_PAGES__/)
  })

  await t.test('real entry/page and TS/Vue probe modules transform with usable source maps', async () => {
    for (const url of ['/src/main.ts', '/src/pages/home/index.vue']) {
      const module = await moduleResponse(origin, url)
      assert.equal(module.status, 200, `${url}: ${module.body.slice(0, 300)}`)
      assert.match(module.headers['content-type'], /javascript/)
      const map = inlineMap(module.body)
      assert.ok(map.sources.length)
      assert.ok(map.sourcesContent?.some(content => typeof content === 'string' && content.length))
    }
    const script = await moduleResponse(origin, scriptUrl)
    assert.equal(script.status, 200)
    const scriptMap = inlineMap(script.body)
    const line = script.body.split('\n').findIndex(value => value.includes('toolchain-hmr-before')) + 1
    const column = script.body.split('\n')[line - 1].indexOf('toolchain-hmr-before')
    const original = new SourceMapConsumer(scriptMap).originalPositionFor({ line, column })
    assert.equal(normalizePath(path.resolve(path.dirname(scriptFile), original.source)), normalizePath(scriptFile))
    assert.equal(original.line, 1)
    assert.ok(scriptMap.sourcesContent.includes(marker))
    const component = await moduleResponse(origin, vueUrl)
    assert.equal(component.status, 200, component.body.slice(0, 300))
    const componentMap = inlineMap(component.body)
    assert.ok(componentMap.sources.some(file => normalizePath(path.resolve(path.dirname(vueFile), file)) === normalizePath(vueFile)))
    assert.ok(componentMap.sourcesContent.some(content => content?.includes('toolchain-vue-before')))
    const styleUrl = component.body.match(/"([^"\n]+\?vue&type=style[^"\n]+)"/)?.[1]
    assert.ok(styleUrl, 'Vue SFC style submodule is generated')
    const style = await moduleResponse(origin, styleUrl)
    assert.equal(style.status, 200)
    assert.match(style.body, /padding: 10px/)
    assert.doesNotMatch(style.body.split('sourceMappingURL=')[0], /padding: 20rpx/)
  })

  await t.test('file watcher delivers actual JS and Vue HMR updates over a token-authenticated socket', async () => {
    socket = new WebSocket(`ws://127.0.0.1:${address.port}/?token=${encodeURIComponent(server.config.webSocketToken)}`, 'vite-hmr', { origin })
    const connected = waitForMessage(socket, message => message.type === 'connected')
    await once(socket, 'open')
    await connected
    const jsUpdate = waitForMessage(socket, message => message.type === 'update' && message.updates.some(update => update.path === scriptUrl))
    await writeFile(scriptFile, marker.replace('toolchain-hmr-before', 'toolchain-hmr-after'))
    const jsMessage = await jsUpdate
    const jsTimestamp = jsMessage.updates.find(update => update.path === scriptUrl).timestamp
    const fresh = await moduleResponse(origin, `${scriptUrl}?t=${jsTimestamp}`)
    assert.match(fresh.body, /toolchain-hmr-after/)
    assert.doesNotMatch(fresh.body.split('sourceMappingURL=')[0], /toolchain-hmr-before/)
    const vueUpdate = waitForMessage(socket, message => message.type === 'update' && message.updates.some(update => update.path === vueUrl))
    await writeFile(vueFile, source.replace('toolchain-vue-before', 'toolchain-vue-after'))
    const vueMessage = await vueUpdate
    const vueTimestamp = vueMessage.updates.find(update => update.path === vueUrl).timestamp
    const freshVue = await moduleResponse(origin, `${vueUrl}?t=${vueTimestamp}`)
    assert.match(freshVue.body, /toolchain-vue-after/)
    assert.ok(inlineMap(freshVue.body).sourcesContent.some(content => content?.includes('toolchain-vue-after')))
  })

  await t.test('untrusted Host/Origin and filesystem escape variants cannot expose denied bytes', async () => {
    const hostileHost = await rawRequest(origin, '/', { Host: 'untrusted.invalid' })
    assert.equal(hostileHost.status, 403)
    const hostileOrigin = await rawRequest(origin, scriptUrl, { Origin: 'https://untrusted.invalid' })
    assert.equal(hostileOrigin.headers['access-control-allow-origin'], undefined)
    await assertWebSocketRejected(`ws://127.0.0.1:${address.port}/`, { origin: 'https://untrusted.invalid' })
    await assertWebSocketRejected(`ws://127.0.0.1:${address.port}/?token=invalid-token`, { origin })
    await assertWebSocketRejected(`ws://127.0.0.1:${address.port}/?token=${encodeURIComponent(server.config.webSocketToken)}`, { origin, headers: { Host: 'untrusted.invalid' } })
    const deniedPaths = [
      `/@fs/${normalizePath(secretFile)}`,
      `/@fs/${normalizePath(secretFile)}?raw`,
      `/@fs/${normalizePath(secretFile)}?import&raw`,
      `${fixtureUrl}/.env.local`,
      `${fixtureUrl}/.env.local?raw`,
      `${fixtureUrl}/.env.local?import&raw`,
      `${fixtureUrl}/.env.local?raw??`,
      `${fixtureUrl}/.env.local?import&raw??`
    ]
    if (process.platform === 'win32') {
      deniedPaths.push(`/@fs/${normalizePath(secretFile)}.`)
      deniedPaths.push(`/@fs/${normalizePath(secretFile)}?raw??`)
      deniedPaths.push(`/@fs//?/${normalizePath(secretFile)}`)
      deniedPaths.push(`${fixtureUrl}/.env.local.`)
      deniedPaths.push(`${fixtureUrl}/.env.local%20`)
      deniedPaths.push(`${fixtureUrl}/.env.local::$DATA`)
    }
    for (const url of deniedPaths) {
      const response = await rawRequest(origin, url)
      assert.ok(!response.body.includes(secret), url)
      // Unknown/invalid Windows paths can fall through to the SPA index.
      // They may not return an arbitrary file's bytes or raw-import module.
      if (response.status === 200) {
        assert.match(response.headers['content-type'], /text\/html/, url)
        assert.match(response.body, /\/src\/main\.ts/, url)
      } else {
        assert.ok([400, 403, 404].includes(response.status), `${url}: ${response.status}`)
      }
    }
  })
  assert.deepEqual(errors.filter(message => !message.includes('outside of Vite serving allow list')), [], 'no hidden transform/HMR errors')
})

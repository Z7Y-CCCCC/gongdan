// Loopback-only reverse proxy to the explicitly started, frozen H2 test API.
// No production DB is configured here. The caller must use the isolated launcher.
import http from 'node:http'
import path from 'node:path'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const parameter = process.argv.indexOf('--api')
const api = new URL(process.argv[parameter + 1] || '')
if (parameter < 0 || api.protocol !== 'http:' || api.hostname !== '127.0.0.1' || !api.port) throw new Error('Explicit loopback H2 API origin required')
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist')
const requests = []
const deliveries = []
const send = (res, status, value) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)) }
const sink = http.createServer(async (req, res) => {
  if (req.url === '/external-data') return send(res, 200, { items: [{ id: 1, name: 'Functional widget' }] })
  const chunks = []
  let bytes = 0
  for await (const chunk of req) { bytes += chunk.length; if (bytes > 1048576) return send(res, 413, {}); chunks.push(chunk) }
  deliveries.push({ method: req.method, path: req.url, body: Buffer.concat(chunks).toString('utf8') })
  send(res, 200, { ok: true })
})
await new Promise(resolve => sink.listen(0, '127.0.0.1', resolve))
const sinkOrigin = `http://127.0.0.1:${sink.address().port}`
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' }
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://fixture.invalid')
  if (url.pathname === '/__functional-fixture') return send(res, 200, { fixture: 'real-h2-api', apiOrigin: api.origin, factoryCode: 'FUNCTIONALTEST', sinkOrigin, requests, deliveries })
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/ureport') || url.pathname.startsWith('/actuator/')) {
    const record = { method: req.method, path: req.url }
    requests.push(record)
    const upstream = http.request(new URL(req.url, api), { method: req.method, headers: { ...req.headers, host: api.host } }, response => {
      record.status = response.statusCode
      res.writeHead(response.statusCode, response.headers)
      response.pipe(res)
    })
    upstream.on('error', error => send(res, 502, { message: error.message }))
    req.pipe(upstream)
    return
  }
  try {
    const file = path.resolve(root, `.${decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)}`)
    const relative = path.relative(root, file)
    if (relative.startsWith('..') || path.isAbsolute(relative)) return send(res, 403, {})
    const body = await readFile(file)
    res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' })
    res.end(body)
  } catch { send(res, 404, {}) }
})
server.listen(0, '127.0.0.2', () => console.log(JSON.stringify({ url: `http://127.0.0.2:${server.address().port}`, api: api.origin, sinkOrigin })))
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => { sink.close(); server.close(() => process.exit(0)) })

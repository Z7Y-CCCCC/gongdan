// Isolated, in-memory HTTP fixture for manual browser QA. Never contacts a real backend.
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist/build/h5')
const user = { id: 7, username: 'worker', name: '测试工人', role: 'PRODUCTION', roleLabel: '生产人员', departmentName: '装配' }
const order = { id: 42, code: 'QA-042', productId: 1, productCode: 'P-001', productName: '测试产品', plannedQuantity: 100, goodQuantity: 10, defectQuantity: 1, status: 'IN_PROGRESS', priority: 'NORMAL', progressPercent: 10, tasks: [{ sequenceNo: 1, processName: '装配', plannedQuantity: 100, completedQuantity: 10, defectQuantity: 1, reportPermission: '装配', enabled: true }] }
let reports = []
let writeCount = 0
const requests = []
const contentTypes = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf' }
function send(res, status, value) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' })
  res.end(JSON.stringify(value))
}
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://fixture.invalid')
  if (url.pathname.startsWith('/api/')) {
    requests.push({ method: req.method, path: url.pathname })
    if (url.pathname === '/api/fixture-state') return send(res, 200, { writeCount, requests })
    if (req.method === 'POST' && url.pathname === '/api/auth/login') return send(res, 200, { token: 'browser-fixture-token', user })
    if (req.headers.authorization !== 'Bearer browser-fixture-token') return send(res, 401, { message: '请登录测试账号' })
    if (url.pathname === '/api/mobile/work-orders') return send(res, 200, { items: [order], total: 1 })
    if (url.pathname.endsWith('/reporters')) return send(res, 200, [user])
    if (url.pathname === '/api/mobile/work-orders/42') return send(res, 200, order)
    if (url.pathname === '/api/mobile/reports' && req.method === 'POST') {
      let raw = ''
      for await (const chunk of req) raw += chunk
      const input = JSON.parse(raw)
      const report = { ...input, id: ++writeCount, reporterName: user.name, workOrderCode: order.code, status: 'PENDING', createdAt: new Date().toISOString(), images: [] }
      reports.unshift(report)
      await new Promise(resolve => setTimeout(resolve, 400))
      return send(res, 200, report)
    }
    if (url.pathname === '/api/mobile/reports') return send(res, 200, { items: reports, total: reports.length })
    if (url.pathname === '/api/mobile/field-events') return send(res, 200, { items: [], total: 0 })
    return send(res, 404, { message: 'Fixture endpoint not found' })
  }
  try {
    const pathname = url.pathname.startsWith('/mobile/') ? url.pathname.slice('/mobile'.length) : url.pathname
    const requested = path.resolve(root, `.${decodeURIComponent(pathname === '/' ? '/index.html' : pathname)}`)
    const relative = path.relative(root, requested)
    if (relative.startsWith('..') || path.isAbsolute(relative)) return send(res, 403, {})
    const body = await readFile(requested)
    res.writeHead(200, {
      'Content-Type': contentTypes[path.extname(requested)] || 'application/octet-stream',
      'Cache-Control': 'no-store',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"
    })
    res.end(body)
  } catch {
    send(res, 404, {})
  }
})
server.listen(0, '127.0.0.2', () => console.log(`Mobile isolated QA fixture: http://127.0.0.2:${server.address().port}`))
process.on('SIGINT', () => server.close(() => process.exit(0)))
process.on('SIGTERM', () => server.close(() => process.exit(0)))

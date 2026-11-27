// Isolated, in-memory HTTP fixture for manual browser QA. Never contacts a real backend.
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs } from 'node:util'

const { values: options } = parseArgs({ options: {
  dist: { type: 'string' }, port: { type: 'string' }, 'audit-ui': { type: 'boolean' },
  'seed-orders': { type: 'boolean' }, 'seed-rejected': { type: 'boolean' },
  'seed-reports': { type: 'boolean' }, 'mixed-recent': { type: 'boolean' }
} })
const root = options.dist ? path.resolve(options.dist) : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist/build/h5')
const port = Number(options.port ?? '0')
if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid loopback fixture port')
const auditUi = options['audit-ui']
const user = { id: 7, username: 'worker', name: '测试工人', role: 'PRODUCTION', roleLabel: '生产人员', departmentName: '装配' }
const order = { id: 42, code: 'QA-042', productId: 1, productCode: 'P-001', productName: '测试产品', unitName: '件', plannedQuantity: 100, goodQuantity: 10, defectQuantity: 1, status: 'IN_PROGRESS', priority: 'NORMAL', progressPercent: 10, tasks: [{ sequenceNo: 1, processName: '装配', plannedQuantity: 100, completedQuantity: 10, defectQuantity: 1, reportPermission: '装配', enabled: true }] }
if (auditUi) {
  order.productName = '长产品名称 ' + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(4) + ' <span data-audit-literal="1">示例文字</span>'
  order.unitName = '长计量单位' + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(2)
  order.tasks[0].processName = '长工序名称 ' + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(3)
  order.remark = '备注 <span data-audit-literal="2">示例文字</span> ' + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(4)
}
const orders = options['seed-orders']
  ? Array.from({ length: 42 }, (_, index) => ({
    ...order, id: 42 - index, code: `QA-${String(42 - index).padStart(3, '0')}`,
    productName: index === 41 ? '后页专用零件' : order.productName,
    status: index === 41 || (options['mixed-recent'] && index === 1) ? 'PAUSED' : order.status
  }))
  : [order]
function localTimestamp() {
  const now = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}
let reports = options['seed-rejected'] ? [{
  id: 100, workOrderId: order.id, workOrderCode: order.code, processSequenceNo: 1, processName: '装配',
  reporterId: user.id, reporterName: user.name, status: 'REJECTED', approvalRemark: '请核对实际良品数',
  productCode: order.productCode, productName: order.productName,
  approverName: '测试主管', approvedAt: localTimestamp(), createdAt: localTimestamp(),
  goodQuantity: 4, reportQuantity: 4, defectQuantity: 0, images: []
}] : []
let writeCount = 0
if (options['seed-reports']) reports.push(...Array.from({ length: 41 }, (_, index) => ({
  id: 200 + index, workOrderId: 42, workOrderCode: order.code, processSequenceNo: 1, processName: '装配',
  reporterId: user.id, reporterName: user.name, status: 'PENDING', createdAt: localTimestamp(),
  goodQuantity: 1, reportQuantity: 1, defectQuantity: 0, images: []
})))
const images = new Map()
let imageUploadCount = 0
let fieldWriteCount = 0
let logoutCount = 0
const fieldEvents = []
if (auditUi) {
  const body = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')
  for (const [index, report] of reports.slice(0, 2).entries()) {
    const id = 9001 + index
    const metadata = { id, originalFileName: 'audit-pixel.png', fileSize: body.length, contentType: 'image/png', accessPath: `/api/mobile/reports/images/${id}/file` }
    images.set(id, { metadata, body })
    report.images = [metadata]
    report.processName = order.tasks[0].processName
    report.remark = order.remark
    if (index === 1) report.status = 'APPROVED'
  }
  fieldEvents.push({ id: 9999, moduleName: 'FIELD_EVENT', title: '现场事件 ' + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(4), status: 'OPEN', payloadJson: JSON.stringify({ eventType: '异常', station: '工位-' + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(3), description: order.remark }), createdAt: localTimestamp() })
}
const requests = []
const contentTypes = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf' }
function send(res, status, value) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' })
  res.end(JSON.stringify(value))
}
function dashboard(day) {
  const own = reports.filter(item => item.reporterId === user.id)
  const approved = own.filter(item => item.status === 'APPROVED')
  const today = own.filter(item => (item.createdAt || '').startsWith(day))
  const todayApproved = today.filter(item => item.status === 'APPROVED')
  const sum = (items, field) => items.reduce((total, item) => total + (item[field] || 0), 0)
  const processes = new Map()
  for (const report of approved) {
    const name = report.processName || '未命名工序'
    processes.set(name, (processes.get(name) || 0) + (report.goodQuantity || 0))
  }
  return {
    workOrderTotal: orders.length,
    totalPlanned: sum(orders, 'plannedQuantity'), totalGood: sum(orders, 'goodQuantity'),
    activeOrderCount: orders.filter(item => !['COMPLETED', 'FINISHED', 'CLOSED', 'CANCELED', 'CANCELLED'].includes(item.status)).length,
    reportTotal: own.length, pendingReportCount: own.filter(item => item.status === 'PENDING').length,
    todayReportCount: today.length, totalDefect: sum(approved, 'defectQuantity'),
    todayGood: sum(todayApproved, 'goodQuantity'), todayDefect: sum(todayApproved, 'defectQuantity'),
    todayPending: today.filter(item => item.status === 'PENDING').length,
    recentOrders: orders.slice(0, 5),
    rankings: [...processes].map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value || a.name.localeCompare(b.name)).slice(0, 5)
  }
}
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://fixture.invalid')
  if (url.pathname.startsWith('/api/')) {
    requests.push({ method: req.method, path: url.pathname, query: url.search })
    if (url.pathname === '/api/fixture-state') return send(res, 200, { fixture: 'mobile-ui', writeCount, fieldWriteCount, imageUploadCount, logoutCount, requests })
    if (req.method === 'POST' && url.pathname === '/api/mobile/auth/password-login') return send(res, 200, { token: 'browser-fixture-token', user })
    if (req.headers.authorization !== 'Bearer browser-fixture-token') return send(res, 401, { message: '请登录测试账号' })
    if (url.pathname === '/api/auth/logout' && req.method === 'POST') { logoutCount++; res.writeHead(204, { 'Cache-Control': 'no-store' }); res.end(); return }
    if (url.pathname === '/api/mobile/dashboard') return send(res, 200, dashboard(url.searchParams.get('day') || ''))
    if (url.pathname === '/api/mobile/work-orders') {
      const keyword = (url.searchParams.get('keyword') || '').toLowerCase()
      const status = url.searchParams.get('status')
      const reportableOnly = url.searchParams.get('reportableOnly') === 'true'
      if (reportableOnly && status) return send(res, 400, { message: '状态筛选不能同时使用' })
      const executing = new Set(['EXECUTING', 'IN_PROGRESS'])
      const matching = orders.filter(item =>
        (!reportableOnly || ['NOT_STARTED', 'EXECUTING', 'IN_PROGRESS'].includes(item.status)) &&
        (!status || item.status === status || (executing.has(status) && executing.has(item.status))) &&
        [item.code, item.productName, item.productCode].some(value => value.toLowerCase().includes(keyword)))
      const page = Number(url.searchParams.get('page') || 1)
      const size = Number(url.searchParams.get('size') || 100)
      return send(res, 200, { items: matching.slice((page - 1) * size, page * size), total: matching.length })
    }
    if (url.pathname.endsWith('/reporters')) return send(res, 200, [user])
    const byCode = /^\/api\/mobile\/work-orders\/by-code\/(.+)$/.exec(url.pathname)
    if (byCode) {
      const item = orders.find(candidate => candidate.code === decodeURIComponent(byCode[1]))
      return item ? send(res, 200, item) : send(res, 404, { message: '工单不存在' })
    }
    const orderDetail = url.pathname.match(/^\/api\/mobile\/work-orders\/(\d+)$/)
    if (orderDetail) {
      const item = orders.find(candidate => candidate.id === Number(orderDetail[1]))
      return item ? send(res, 200, item) : send(res, 404, { message: '工单不存在' })
    }
    if (url.pathname === '/api/mobile/reports/images' && req.method === 'POST') {
      const chunks = []; let bytes = 0
      for await (const chunk of req) { bytes += chunk.length; if (bytes > 16 * 1024 * 1024) return send(res, 413, { message: '图片过大' }); chunks.push(chunk) }
      const input = Buffer.concat(chunks)
      const boundary = /boundary=([^;]+)/.exec(req.headers['content-type'] || '')?.[1]
      const start = input.indexOf(Buffer.from('\r\n\r\n')) + 4
      const end = boundary ? input.indexOf(Buffer.from(`\r\n--${boundary}`), start) : -1
      if (start < 4 || end < start) return send(res, 400, { message: '图片格式错误' })
      const body = input.subarray(start, end)
      const id = 1000 + ++imageUploadCount
      const metadata = { id, originalFileName: 'qa-photo.png', fileSize: body.length, contentType: 'image/png', accessPath: `/api/mobile/reports/images/${id}/file` }
      images.set(id, { metadata, body })
      return send(res, 200, metadata)
    }
    const imageFile = /^\/api\/mobile\/reports\/images\/(\d+)\/file$/.exec(url.pathname)
    if (imageFile) {
      const image = images.get(Number(imageFile[1]))
      if (!image) return send(res, 404, { message: '照片不存在' })
      res.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store' }); res.end(image.body); return
    }
    if (url.pathname === '/api/mobile/reports' && req.method === 'POST') {
      let raw = ''
      for await (const chunk of req) raw += chunk
      const input = JSON.parse(raw)
      const reportedOrder = orders.find(item => item.id === input.workOrderId) || order
      const report = { ...input, id: ++writeCount, reporterId: input.reporterUserId || user.id, reporterName: user.name, workOrderCode: reportedOrder.code, productCode: reportedOrder.productCode, productName: reportedOrder.productName, status: 'PENDING', createdAt: localTimestamp(), images: (input.imageIds || []).map(id => images.get(id)?.metadata).filter(Boolean) }
      reports.unshift(report)
      await new Promise(resolve => setTimeout(resolve, 400))
      return send(res, 200, report)
    }
    if (url.pathname === '/api/mobile/reports') {
      const status = url.searchParams.get('status'), period = url.searchParams.get('period'), workOrderId = url.searchParams.get('workOrderId')
      const now = new Date(), previous = new Date(now.getFullYear(), now.getMonth() - 1, 1)
      const month = `${previous.getFullYear()}-${String(previous.getMonth() + 1).padStart(2, '0')}`
      const matching = reports.filter(item => (!status || item.status === status) && (!workOrderId || item.workOrderId === Number(workOrderId)) &&
        (!period || period === 'today' && item.createdAt.startsWith(localTimestamp().slice(0, 10)) || period === 'lastMonth' && item.createdAt.startsWith(month)))
      const page = Number(url.searchParams.get('page') || 1), size = Number(url.searchParams.get('size') || 100)
      return send(res, 200, { items: matching.slice((page - 1) * size, page * size), total: matching.length })
    }
    if (url.pathname === '/api/mobile/field-events' && req.method === 'POST') {
      let raw = ''
      for await (const chunk of req) raw += chunk
      const input = JSON.parse(raw)
      const event = { ...input, id: 1000 + ++fieldWriteCount, moduleName: 'FIELD_EVENT', createdAt: localTimestamp() }
      fieldEvents.unshift(event)
      return send(res, 200, event)
    }
    if (url.pathname === '/api/mobile/field-events') return send(res, 200, { items: fieldEvents.slice(0, 50), total: fieldEvents.length })
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
server.listen(port, '127.0.0.2', () => console.log(`Mobile isolated QA fixture: http://127.0.0.2:${server.address().port}`))
process.on('SIGINT', () => server.close(() => process.exit(0)))
process.on('SIGTERM', () => server.close(() => process.exit(0)))

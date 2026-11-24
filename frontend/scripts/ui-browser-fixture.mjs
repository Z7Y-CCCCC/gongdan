// In-memory UI fixture. Only loopback traffic; no business backend or real data.
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist')
const token = 'local-ui-fixture-only'
const factory = { id: 2, code: 'UIFIXTURE', name: '测试工厂', enabled: true }
const user = { id: 7, username: 'admin', name: '测试管理员', role: 'ADMIN', roleLabel: '管理员',
  factoryId: 2, factoryCode: factory.code, enabled: true, virtualUser: false, departmentName: '生产运营', createdAt: '2026-11-24 08:00:00' }
const units = [{ id: 1, name: '件', createdAt: '2026-11-24 08:00:00' }]
const products = [
  { id: 1, code: 'FG-001', name: '精密支架', spec: '42×18', attribute: '自制', unitId: 1, unitName: '件', inventoryQty: 240, createdAt: '2026-11-24 08:00:00' },
  { id: 2, code: 'RM-002', name: '铝合金毛坯', spec: '6061', attribute: '外购', unitId: 1, unitName: '件', inventoryQty: 560, createdAt: '2026-11-24 08:00:00' }
]
const records = {
  '/work-orders': Array.from({ length: 3 }, (_, i) => ({ id: i + 1, code: `GD-20261124-00${i + 1}`,
    productId: 1, productCode: 'FG-001', productName: '精密支架', productSpec: '42×18', unitName: '件',
    plannedQuantity: 120, goodQuantity: i ? 0 : 80, defectQuantity: 2, progressPercent: i ? 0 : 67,
    status: ['EXECUTING', 'NOT_STARTED', 'PAUSED'][i], priority: i ? 'NORMAL' : 'HIGH', version: 1,
    planStart: '2026-11-24 08:00:00', planEnd: '2026-11-25 18:00:00', createdAt: '2026-11-24 08:00:00',
    creator: user.name, customerName: '华东精密制造有限公司', tasks: [{ sequenceNo: 1, processName: '铣削',
      plannedQuantity: 120, completedQuantity: i ? 0 : 80, defectQuantity: 2, enabled: true }], materials: [], customValues: [] })),
  '/products': products,
  '/units': units,
  '/processes': [{ id: 1, name: '铣削', createdAt: '2026-11-24 08:00:00' }],
  '/process-routes': [],
  '/defects': [{ id: 1, name: '尺寸偏差', createdAt: '2026-11-24 08:00:00' }],
  '/departments': [{ id: 1, name: '生产运营', code: 'D001', userNames: user.name, createdAt: '2026-11-24 08:00:00' }],
  '/customers': [{ id: 1, code: 'CUS-001', name: '华东精密制造有限公司', contactName: '李先生', phone: '13800000000', enabled: true, createdAt: '2026-11-24 08:00:00' }],
  '/suppliers': [{ id: 1, code: 'SUP-001', name: '华南金属材料', contactName: '陈女士', phone: '13900000000', enabled: true, createdAt: '2026-11-24 08:00:00' }],
  '/warehouses': [{ id: 1, code: 'WH-001', name: '成品仓', enabled: true, createdAt: '2026-11-24 08:00:00' }],
  '/boms': [{ id: 1, code: 'BOM-001', productId: 1, productCode: 'FG-001', productName: '精密支架', outputQuantity: '1', enabled: true,
    createdAt: '2026-11-24 08:00:00', items: [{ id: 1, productId: 2, productCode: 'RM-002', productName: '铝合金毛坯', quantity: '1.25', scrapRate: '0.5', unitName: '件' }] }],
  '/sales-orders': [], '/reports': [], '/custom-fields': [], '/work-order-categories': [], '/product-categories': []
}
const notifications = [
  { id: 1, eventType: 'REPORT_PENDING_APPROVAL', title: '报工待审批', content: 'GD-20261124-001 · 铣削 · 80 件', priority: 'HIGH',
    unread: true, state: 'UNREAD', targetMain: 'production', targetSub: 'workbench', createdAt: '2026-11-24 09:20:00' },
  { id: 2, eventType: 'WORK_ORDER_OVERDUE', title: '工单逾期', content: 'GD-20261123-012', priority: 'URGENT',
    unread: false, state: 'READ', targetMain: 'production', targetSub: 'workorder', createdAt: '2026-11-24 08:10:00' }
]
const requests = []
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.jpg': 'image/jpeg',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.ico': 'image/x-icon' }
function send(res, status, data) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(JSON.stringify(data))
}
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://fixture.invalid')
  if (url.pathname.startsWith('/api/')) {
    const route = url.pathname.slice(4)
    requests.push({ method: req.method, route, query: url.search })
    if (route === '/fixture-state') return send(res, 200, { fixture: 'management-ui', requests })
    if (route === '/auth/login') return send(res, 200, { token, user })
    if (req.headers.authorization !== `Bearer ${token}`) return send(res, 401, { message: '请登录测试账号' })
    if (route === '/factory') return send(res, 200, factory)
    if (route === '/users') return send(res, 200, [user])
    if (route === '/user/preferences') return send(res, 200, {})
    if (route === '/notifications/unread-count') return send(res, 200, notifications.filter(item => item.unread).length)
    if (route === '/notifications') return send(res, 200, { items: notifications, total: notifications.length, unreadCount: notifications.filter(item => item.unread).length })
    if (route === '/notifications/read-all') { notifications.forEach(item => { item.unread = false; item.state = 'READ' }); return send(res, 200, {}) }
    const notification = /^\/notifications\/(\d+)\/read$/.exec(route)
    if (notification) {
      const item = notifications.find(value => value.id === Number(notification[1]))
      if (!item) return send(res, 404, { message: '通知不存在' })
      item.unread = false; item.state = 'READ'; return send(res, 200, item)
    }
    if (route === '/platform/plugins/runtime-manifest') return send(res, 200, [])
    if (route === '/plugins/catalog' || route === '/plugins') return send(res, 200, [])
    if (route === '/external-data-sources/capabilities') return send(res, 200, { secretsReady: false, sqliteFiles: [], databaseDrivers: [] })
    if (route.startsWith('/external-data-sources')) return send(res, 200, { items: [], total: 0, hasMore: false })
    if (route === '/home-dashboard/process-cards') return send(res, 200, { items: [
      { processId: 1, processName: '铣削', taskCount: 3, plannedQuantity: 360, goodQuantity: 80, defectQuantity: 2 }
    ] })
    if (route === '/home-dashboard/performance') return send(res, 200, {
      plannedQuantity: 360, completedQuantity: 80, workOrderCount: 3, defectRatePercent: 2.44,
      averageCycleDays: 1.5, overdueUnfinishedCount: 0
    })
    if (route === '/home-dashboard/work-orders') return send(res, 200, { items: records['/work-orders'].map(item => ({
      ...item, statusLabel: { EXECUTING: '执行中', NOT_STARTED: '未开始', PAUSED: '已暂停' }[item.status]
    })) })
    if (route.startsWith('/home-dashboard/')) return send(res, 200, { items: [] })
    if (route === '/work-orders/categories' || route === '/products/categories') return send(res, 200, [])
    const one = /^(\/[^/]+)\/(\d+)$/.exec(route)
    if (one && records[one[1]]) {
      const list = records[one[1]], index = list.findIndex(item => item.id === Number(one[2]))
      if (index < 0) return send(res, 404, { message: '记录不存在' })
      if (req.method === 'DELETE') { list.splice(index, 1); return send(res, 200, {}) }
      return send(res, 200, list[index])
    }
    const list = records[route] || []
    if (req.method === 'GET') return send(res, 200, { items: list, total: list.length })
    return send(res, 200, {})
  }
  try {
    const target = path.resolve(root, `.${decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)}`)
    const relative = path.relative(root, target)
    if (relative.startsWith('..') || path.isAbsolute(relative)) return send(res, 403, {})
    const content = await readFile(target)
    res.writeHead(200, { 'Content-Type': types[path.extname(target)] || 'application/octet-stream', 'Cache-Control': 'no-store',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'" })
    res.end(content)
  } catch { send(res, 404, {}) }
})
server.listen(0, '127.0.0.2', () => console.log(`Management UI fixture: http://127.0.0.2:${server.address().port}`))
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)))

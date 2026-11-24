// Real HTTP/controllers/database workflow, only against functional-browser-fixture.mjs.
import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
const origin = process.argv[2]
if (!origin || new URL(origin).hostname !== '127.0.0.2') throw new Error('Use the explicit loopback functional fixture')
const probe = await (await fetch(`${origin}/__functional-fixture`)).json()
assert.equal(probe.fixture, 'real-h2-api')
assert.equal(probe.factoryCode, 'FUNCTIONALTEST')
let token = ''
const checks = []
async function api(method, route, body) {
  const response = await fetch(`${origin}/api${route}`, { method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(20000) })
  const text = await response.text()
  if (!response.ok) throw new Error(`${method} ${route}: ${response.status} ${text.slice(0, 350)}`)
  return text ? JSON.parse(text) : null
}
const id = Date.now().toString().slice(-8)
const login = await api('POST', '/auth/login', { factoryCode: 'FUNCTIONALTEST', username: 'admin', password: '123456' })
token = login.token
assert.ok(token)
assert.equal((await api('GET', '/factory')).code, 'FUNCTIONALTEST')
checks.push('Factory login and authenticated session')
for (const resource of ['units', 'defect-items', 'departments', 'processes', 'customers', 'suppliers', 'warehouses']) {
  const name = `Functional ${resource} ${id}`
  const item = await api('POST', `/${resource}`, { code: `F${id}`, name, enabled: true })
  assert.ok(item.id)
  assert.ok((await api('GET', `/${resource}?size=100`)).items.some(row => row.id === item.id))
  await api('PUT', `/${resource}/${item.id}`, { code: `F${id}`, name: `${name} edited`, enabled: true })
  assert.ok((await api('GET', `/${resource}?size=100`)).items.some(row => row.name === `${name} edited`))
  await api('DELETE', `/${resource}/${item.id}`)
  assert.ok(!(await api('GET', `/${resource}?size=100`)).items.some(row => row.id === item.id))
  checks.push(`${resource}: create/read/edit/delete persisted`)
}
await api('PUT', '/user/preferences/ui.functional-http', { value: 'actual persisted value' })
assert.equal((await api('GET', '/user/preferences/ui.functional-http')).value, 'actual persisted value')
checks.push('Personal preferences persist')
for (const moduleName of ['SOP', 'EQUIPMENT', 'QUALITY', 'PAYROLL_RULE', 'FIELD_EVENT']) {
  const item = await api('POST', '/advanced-records', { moduleName, title: `Functional ${moduleName} ${id}`, payloadJson: JSON.stringify({ content: 'Actual persisted content' }) })
  const stored = (await api('GET', `/advanced-records?moduleName=${moduleName}&size=100`)).items.find(row => row.id === item.id)
  assert.equal(JSON.parse(stored.payloadJson).content, 'Actual persisted content')
  checks.push(`${moduleName}: real payload persistence`)
}
const message = await api('POST', '/advanced-records', { moduleName: 'MESSAGE', title: `Functional webhook ${id}`, payloadJson: JSON.stringify({ channel: 'webhook', template: 'Actual HTTP delivery', webhook: `${probe.sinkOrigin}/messages` }) })
const result = await api('POST', `/advanced-records/${message.id}/send-test`)
assert.equal(result.success, true)
const received = await (await fetch(`${origin}/__functional-fixture`)).json()
assert.equal(received.deliveries.length, probe.deliveries.length + 1)
assert.equal(JSON.parse(received.deliveries.at(-1).body).content, 'Actual HTTP delivery')
checks.push('Message test made an actual HTTP delivery')
const unit = await api('POST', '/units', { name: `kg-${id}` })
const operation = await api('POST', '/processes', { name: `Operation-${id}`, code: `PROC-${id}`, reportPermission: ['ALL'] })
const product = await api('POST', '/products', { code: `PRODUCT-${id}`, name: `Functional product ${id}`, unitId: unit.id, attribute: '自制' })
const material = await api('POST', '/products', { code: `MATERIAL-${id}`, name: `Functional material ${id}`, unitId: unit.id, attribute: '外购' })
const bom = await api('POST', '/boms', { productId: product.id, outputQuantity: '0.5', items: [{ productId: material.id, quantity: '1.2345', scrapRate: '0.5' }] })
assert.equal(bom.items[0].quantity, '1.2345')
checks.push('Products and exact decimal BOM')
const order = await api('POST', '/work-orders', { code: `ORDER-${id}`, productId: product.id, plannedQuantity: 10, status: 'EXECUTING', tasks: [{ processName: operation.name, sequenceNo: 1, plannedQuantity: 10, enabled: true }] })
assert.equal((await api('GET', `/work-orders?code=${encodeURIComponent(order.code)}&size=100`)).items.find(row => row.id === order.id)?.unitName, unit.name)
assert.equal((await api('GET', `/mobile/work-orders/${order.id}`)).unitName, unit.name)
checks.push('Work order and mobile detail retain the real product unit')
const report = await api('POST', '/mobile/reports', { workOrderId: order.id, processSequenceNo: 1, processName: operation.name, reportQuantity: 2, goodQuantity: 2, defectQuantity: 0, remark: 'Functional HTTP report' })
assert.ok(report.id)
if (report.status !== 'APPROVED') await api('POST', `/reports/${report.id}/approve`, {})
assert.equal((await api('GET', `/reports/${report.id}`)).status, 'APPROVED')
checks.push('Mobile report to real approval')
const inbound = await api('POST', '/inbound-receipts', { inboundType: '采购入库', inboundTime: '2026-11-24T09:00:00', items: [{ productId: material.id, quantity: 20 }] })
assert.ok(inbound.id)
const outbound = await api('POST', '/outbound-receipts', { outboundType: '生产出库', outboundTime: '2026-11-24T10:00:00', items: [{ productId: material.id, quantity: 3 }] })
assert.ok(outbound.id)
assert.equal((await api('GET', `/products?code=${encodeURIComponent(material.code)}&size=100`)).items.find(row => row.id === material.id)?.inventoryQty, 17)
checks.push('Inbound/outbound receipts changed real stock')
assert.ok((await api('GET', '/mobile/dashboard')).recentOrders)
checks.push('Mobile aggregated dashboard')
const summary = { passed: checks.length, checks, data: { productId: product.id, materialId: material.id, workOrderId: order.id, reportId: report.id, unitId: unit.id, processId: operation.id }, fixtureOnly: true }
if (process.argv[3]) writeFileSync(process.argv[3], JSON.stringify(summary, null, 2))
console.log(JSON.stringify(summary, null, 2))

// Exact inventory-time HTTP acceptance for an explicitly marked isolated H2 fixture.
import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const supplied = process.argv[2]
assert.match(supplied ?? '', /^http:\/\/127\.0\.0\.2:[1-9][0-9]{0,4}\/?$/)
const target = new URL(supplied)
assert.ok(Number(target.port) > 0 && Number(target.port) <= 65535)
const origin = target.origin
const marker = await (await fetch(`${origin}/__functional-fixture`, { signal: AbortSignal.timeout(10000) })).json()
assert.equal(marker.fixture, 'real-h2-api')
assert.equal(marker.factoryCode, 'FUNCTIONALTEST')
let token = ''
async function api(method, route, payload) {
  const response = await fetch(`${origin}/api${route}`, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(payload === undefined ? {} : { body: JSON.stringify(payload) }), signal: AbortSignal.timeout(10000),
  })
  assert.ok(response.ok, `${method} ${route.split('?')[0]}: HTTP ${response.status}`)
  return response.json()
}
const checks = []
const session = await api('POST', '/auth/login', { factoryCode: 'FUNCTIONALTEST', username: 'admin', password: '123456' })
assert.ok(session.token)
token = session.token
checks.push('Marked isolated factory and actual HTTP administrator login verified')
const tag = Date.now().toString(36).toUpperCase()
const unit = await api('POST', '/units', { name: `Clock unit ${tag}` })
const product = await api('POST', '/products', { code: `CLOCK-${tag}`, name: 'Clock mapping fixture', unitId: unit.id, attribute: '自制' })
const time = '2026-09-11T12:00:00'
const inbound = await api('POST', '/inbound-receipts', { code: `CLOCK-IN-${tag}`, inboundType: '采购入库', inboundTime: time,
  items: [{ productId: product.id, quantity: 10 }, { productId: product.id, quantity: 10 }] })
const outbound = await api('POST', '/outbound-receipts', { code: `CLOCK-OUT-${tag}`, outboundType: '生产出库', outboundTime: time,
  items: [{ productId: product.id, quantity: 3 }] })
assert.ok(inbound.id && outbound.id)
checks.push('Real controllers and JPA saved two inbound lines and one outbound line at a fixed wall-clock time')
const params = new URLSearchParams({ productCode: product.code, startTime: time, endTime: time, page: '1', size: '2' })
const first = await api('GET', `/inventory-details?${params}`)
assert.equal(first.total, 3)
assert.deepEqual(first.items.map(row => row.changeQty), [10, 10])
assert.ok(first.items.every(row => row.operationTime === time && row.unitName === unit.name))
checks.push('Inclusive exact start=end filtering returns three real lines, with page one and original time/unit intact')
params.set('page', '2')
const second = await api('GET', `/inventory-details?${params}`)
assert.equal(second.total, 3)
assert.deepEqual(second.items.map(row => row.changeQty), [-3])
assert.equal(second.items[0].operationTime, time)
checks.push('Page two retains the outbound line and the same wall-clock timestamp')
params.set('startTime', '2026-09-11T12:00:01')
params.set('endTime', '2026-09-11T12:00:01')
params.set('page', '1')
assert.equal((await api('GET', `/inventory-details?${params}`)).total, 0)
checks.push('A one-second-outside range does not include the fixed-time records')
const stored = (await api('GET', `/products?code=${encodeURIComponent(product.code)}&size=10`)).items.find(row => row.id === product.id)
assert.equal(stored.inventoryQty, 17)
checks.push('Real stock remains the expected 20 inbound minus 3 outbound')
const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
mkdirSync(path.join(workspace, 'output'), { recursive: true })
const result = { fixtureOnly: true, passed: checks.length, checks }
writeFileSync(path.join(workspace, 'output/functional-inventory-http-final.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result, null, 2))

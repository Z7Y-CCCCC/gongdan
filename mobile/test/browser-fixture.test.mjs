import assert from 'node:assert/strict'
import { test } from 'node:test'
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

test('isolated browser fixture honors dashboard aggregates and reportable task pagination', async t => {
  const child = spawn(process.execPath, [fileURLToPath(new URL('./browser-fixture.mjs', import.meta.url)), '--seed-orders'], {
    cwd: path.dirname(fileURLToPath(import.meta.url)), stdio: ['ignore', 'pipe', 'pipe']
  })
  t.after(() => child.kill())
  const base = await new Promise((resolve, reject) => {
    let output = ''
    const timer = setTimeout(() => reject(new Error('Fixture did not start')), 10000)
    child.on('error', error => { clearTimeout(timer); reject(error) })
    child.on('exit', code => { clearTimeout(timer); reject(new Error(`Fixture exited: ${code}`)) })
    child.stdout.on('data', chunk => {
      output += chunk
      const match = output.match(/http:\/\/127\.0\.0\.2:\d+/)
      if (match) { clearTimeout(timer); resolve(match[0]) }
    })
  })
  const call = (endpoint, options = {}) => fetch(`${base}/api${endpoint}`, options)
  assert.equal((await call('/mobile/dashboard?day=2026-11-24')).status, 401)
  const login = await (await call('/mobile/auth/password-login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })).json()
  const headers = { Authorization: `Bearer ${login.token}`, 'Content-Type': 'application/json' }
  const aggregate = await (await call('/mobile/dashboard?day=2026-11-24', { headers })).json()
  assert.equal(aggregate.workOrderTotal, 42)
  assert.equal(aggregate.totalPlanned, 4200)
  assert.equal(aggregate.totalGood, 420)
  assert.equal(aggregate.recentOrders.length, 5)
  assert.equal(aggregate.reportTotal, 0)
  const first = await (await call('/mobile/work-orders?page=1&size=20&reportableOnly=true', { headers })).json()
  assert.equal(first.total, 41)
  assert.equal(first.items.length, 20)
  const last = await (await call('/mobile/work-orders?page=3&size=20&reportableOnly=true', { headers })).json()
  assert.equal(last.items.length, 1)
  assert.equal((await call('/mobile/work-orders?reportableOnly=true&status=PAUSED', { headers })).status, 400)
  const created = await (await call('/mobile/reports', { method: 'POST', headers, body: JSON.stringify({ workOrderId: 42, reporterUserId: 7, goodQuantity: 4, processName: '装配' }) })).json()
  const after = await (await call(`/mobile/dashboard?day=${created.createdAt.slice(0, 10)}`, { headers })).json()
  assert.equal(after.reportTotal, 1)
  assert.equal(after.todayReportCount, 1)
  assert.equal(after.todayPending, 1)
  assert.equal(after.todayGood, 0)
})

import { performance } from 'node:perf_hooks'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

function option(name, fallback) {
  const prefix = `--${name}=`
  const value = process.argv.find((item) => item.startsWith(prefix))
  return value ? value.slice(prefix.length) : fallback
}

const config = {
  baseUrl: option('base-url', process.env.API_BASE_URL || 'http://127.0.0.1:18081'),
  factoryCode: option('factory', process.env.API_FACTORY_CODE || 'PERF'),
  username: option('username', process.env.API_USERNAME || 'admin'),
  password: option('password', process.env.API_PASSWORD || 'PerfTest-2026!'),
  scenario: option('scenario', 'mixed'),
  concurrency: Number(option('concurrency', '20')),
  durationSeconds: Number(option('duration', '30')),
  output: option('output', 'output/perf/api-load-report.json'),
  p95LimitMs: Number(option('p95-limit-ms', '1000')),
  maxErrorRate: Number(option('max-error-rate', '0.01')),
}

if (!Number.isInteger(config.concurrency) || config.concurrency < 1 || config.concurrency > 500) {
  throw new Error('concurrency must be an integer between 1 and 500')
}
if (!Number.isFinite(config.durationSeconds) || config.durationSeconds < 1 || config.durationSeconds > 3600) {
  throw new Error('duration must be between 1 and 3600 seconds')
}

const readEndpoints = [
  '/api/factory',
  '/api/users',
  '/api/departments?page=1&size=20',
  '/api/units?page=1&size=20',
  '/api/warehouses?page=1&size=20',
  '/api/suppliers?page=1&size=20',
  '/api/customers?page=1&size=20',
  '/api/defect-items?page=1&size=20',
  '/api/processes?page=1&size=20',
  '/api/process-routes?page=1&size=20',
  '/api/products?page=1&size=20',
  '/api/boms?page=1&size=20',
  '/api/custom-fields?scope=workorder',
  '/api/work-orders?page=1&size=20',
  '/api/sales-orders?page=1&size=20',
  '/api/inbound-receipts?page=1&size=20',
  '/api/outbound-receipts?page=1&size=20',
  '/api/inventory-details?page=1&size=20',
  '/api/advanced-records?moduleName=SOP&page=1&size=20',
  '/api/reports?page=1&size=20',
  '/api/home-dashboard/process-cards?range=TODAY',
  '/api/home-dashboard/work-orders?status=in_progress',
  '/api/home-dashboard/performance?range=TODAY',
  '/api/home-dashboard/reports/recent?range=TODAY',
  '/api/notifications?page=1&size=20',
  '/api/notifications/unread-count',
  '/api/internal-chat/contacts',
  '/api/platform/plugins',
  '/api/platform/plugins/runtime-manifest',
  '/api/system-logs/imports?module=PERF&page=1&size=20',
  '/api/system-logs/operations?module=PERF&page=1&size=20',
  '/api/monitor/cache',
  '/api/monitor/cache/names',
  '/api/monitor/cache/recovery',
]

const samples = []
const endpointStats = new Map()
let requestCount = 0
let errorCount = 0
let stop = false
let sharedToken = null

async function request(method, endpoint, token, body) {
  const started = performance.now()
  let status = 0
  let error = null
  try {
    const response = await fetch(`${config.baseUrl}${endpoint}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    status = response.status
    await response.arrayBuffer()
    if (!response.ok) error = `HTTP ${status}`
  } catch (caught) {
    error = caught instanceof Error ? caught.message : String(caught)
  }
  const durationMs = performance.now() - started
  const key = `${method} ${endpoint.split('?')[0]}`
  const current = endpointStats.get(key) || { requests: 0, errors: 0, durations: [], statuses: {} }
  current.requests += 1
  current.errors += error ? 1 : 0
  current.durations.push(durationMs)
  current.statuses[status || 'network'] = (current.statuses[status || 'network'] || 0) + 1
  endpointStats.set(key, current)
  samples.push(durationMs)
  requestCount += 1
  if (error) errorCount += 1
  return { status, error }
}

async function login() {
  const response = await fetch(`${config.baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      factoryCode: config.factoryCode,
      username: config.username,
      password: config.password,
    }),
  })
  if (!response.ok) throw new Error(`Login failed: HTTP ${response.status} ${await response.text()}`)
  const payload = await response.json()
  return payload.token
}

async function worker(workerId) {
  const token = sharedToken
  let iteration = 0
  while (!stop) {
    if (config.scenario === 'login') {
      await request('POST', '/api/auth/login', null, {
        factoryCode: config.factoryCode,
        username: config.username,
        password: config.password,
      })
    } else {
      const write = config.scenario === 'mixed' && iteration % 12 === 0
      if (write) {
        await request('PUT', `/api/user/preferences/perf-worker-${workerId}`, token, {
          value: JSON.stringify({ workerId, iteration }),
        })
      } else {
        const endpoint = readEndpoints[(workerId + iteration) % readEndpoints.length]
        await request('GET', endpoint, token)
      }
    }
    iteration += 1
  }
}

function percentile(values, fraction) {
  if (values.length === 0) return 0
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * fraction) - 1)]
}

const health = await fetch(`${config.baseUrl}/actuator/health`)
if (!health.ok) throw new Error(`Backend is not healthy: HTTP ${health.status}`)
if (config.scenario !== 'login') sharedToken = await login()

const wallStarted = performance.now()
const workers = Array.from({ length: config.concurrency }, (_, index) => worker(index + 1))
await new Promise((resolve) => setTimeout(resolve, config.durationSeconds * 1000))
stop = true
await Promise.all(workers)
const elapsedSeconds = (performance.now() - wallStarted) / 1000

const endpoints = Object.fromEntries([...endpointStats.entries()].map(([key, item]) => [key, {
  requests: item.requests,
  errors: item.errors,
  errorRate: item.requests ? item.errors / item.requests : 0,
  p50Ms: percentile(item.durations, 0.50),
  p95Ms: percentile(item.durations, 0.95),
  p99Ms: percentile(item.durations, 0.99),
  maxMs: Math.max(...item.durations),
  statuses: item.statuses,
}]))

const report = {
  generatedAt: new Date().toISOString(),
  config: { ...config, password: '[redacted]' },
  summary: {
    requests: requestCount,
    errors: errorCount,
    errorRate: requestCount ? errorCount / requestCount : 0,
    requestsPerSecond: requestCount / elapsedSeconds,
    elapsedSeconds,
    p50Ms: percentile(samples, 0.50),
    p95Ms: percentile(samples, 0.95),
    p99Ms: percentile(samples, 0.99),
    maxMs: Math.max(...samples),
  },
  endpoints,
}

await mkdir(path.dirname(config.output), { recursive: true })
await writeFile(config.output, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
console.log(JSON.stringify(report.summary, null, 2))
console.log(`Report: ${config.output}`)

if (report.summary.errorRate > config.maxErrorRate || report.summary.p95Ms > config.p95LimitMs) {
  process.exitCode = 1
}

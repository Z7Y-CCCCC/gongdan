import http from 'node:http';
import { once } from 'node:events';
import { spawn } from 'node:child_process';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const key = 'protocol-test-mcp-key';
let loginCount = 0;
let expireCacheToken = false;
let redirectTargetCount = 0;
const backendMethods = [];

const backend = http.createServer(async (req, res) => {
  const requestUrl = new URL(req.url || '/', 'http://127.0.0.1');
  backendMethods.push([req.method, requestUrl.pathname]);
  if (req.method === 'POST' && requestUrl.pathname === '/api/auth/login') {
    loginCount += 1;
    return send(res, 200, { token: `mock-backend-token-${loginCount}` });
  }
  if (requestUrl.pathname === '/actuator/health/readiness' || requestUrl.pathname === '/actuator/health/liveness') {
    return send(res, 200, { status: 'UP' });
  }
  if (!req.headers.authorization) return send(res, 401, { message: 'missing auth' });
  if (expireCacheToken && requestUrl.pathname.startsWith('/api/monitor/cache')
      && req.headers.authorization === 'Bearer mock-backend-token-1') {
    const delay = requestUrl.pathname.endsWith('/recovery') ? 250 : requestUrl.pathname.endsWith('/names') ? 125 : 0;
    await new Promise((resolve) => setTimeout(resolve, delay));
    return send(res, 401, { message: 'expired' });
  }
  if (req.headers.authorization !== `Bearer mock-backend-token-${loginCount}`) {
    return send(res, 401, { message: 'superseded session' });
  }
  if (requestUrl.pathname === '/api/redirect-target') redirectTargetCount += 1;
  if (requestUrl.searchParams.get('name') === 'redirect') {
    res.writeHead(302, { Location: '/api/redirect-target' });
    return res.end();
  }
  if (requestUrl.searchParams.get('name') === 'oversized') {
    return send(res, 200, { value: 'x'.repeat(2 * 1024 * 1024 + 1) });
  }
  if (requestUrl.searchParams.get('name') === 'non-json') {
    res.writeHead(502, { 'Content-Type': 'text/plain' });
    return res.end('upstream error: password=must-hide token=must-hide');
  }
  if (requestUrl.searchParams.get('name') === 'timeout') {
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
  if (requestUrl.pathname === '/api/monitor/cache') return send(res, 200, { hitRate: 0.99, entries: 4 });
  if (requestUrl.pathname === '/api/monitor/cache/names') return send(res, 200, [{ name: 'products' }]);
  if (requestUrl.pathname === '/api/monitor/cache/recovery') return send(res, 200, []);
  if (requestUrl.pathname === '/api/system-logs/operations') return send(res, 200, { items: [{ operator: 'admin', token: 'must-hide' }], total: 1 });
  if (requestUrl.pathname === '/api/monitor/online') return send(res, 200, { items: [{ loginName: 'admin', phone: '13800000000' }], total: 1 });
  if (requestUrl.pathname === '/api/reports') return send(res, 503, { message: 'backend temporarily unavailable', token: 'must-hide' });
  return send(res, 200, {
    items: [{ id: 1, name: 'sample', phone: '13800000000', password: 'must-hide' }],
    total: 1,
  });
});

await new Promise((resolve) => backend.listen(0, '127.0.0.1', resolve));
const backendPort = backend.address().port;
const mcpPort = await freePort();
const mcp = spawn(process.execPath, [path.join(root, 'server.mjs')], {
  cwd: root,
  env: {
    ...process.env,
    NODE_ENV: 'test',
    MCP_HOST: '127.0.0.1',
    MCP_PORT: String(mcpPort),
    MCP_API_KEY: key,
    MCP_BACKEND_URL: `http://127.0.0.1:${backendPort}`,
    MCP_BACKEND_FACTORY_CODE: 'HHGD',
    MCP_BACKEND_USERNAME: 'mcp-test',
    MCP_BACKEND_PASSWORD: 'test-password',
    MCP_BACKEND_TOKEN: '',
    MCP_BACKEND_TIMEOUT_MS: '1000',
    MCP_RATE_LIMIT_PER_MINUTE: '1000',
    MCP_ALLOWED_HOSTS: `127.0.0.1:${mcpPort}`,
    MCP_ALLOWED_ORIGINS: 'https://diagnostics.example.test',
  },
  stdio: ['ignore', 'pipe', 'pipe'],
});

let output = '';
mcp.stdout.on('data', (chunk) => { output += chunk.toString(); });
mcp.stderr.on('data', (chunk) => { output += chunk.toString(); });

try {
  const base = `http://127.0.0.1:${mcpPort}`;
  await waitForHealth(mcp, base, () => output);

  const unauthorized = await fetch(`${base}/mcp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {} }),
  });
  assert(unauthorized.status === 401, 'unauthorized MCP request must be rejected');

  for (const extraHeaders of [{ Host: 'untrusted.example.test' }, { Origin: 'https://untrusted.example.test' }]) {
    // Node fetch normalizes Host to the URL authority; use raw HTTP to test it.
    const blocked = await rawRequest(`${base}/mcp`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream', ...extraHeaders },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} }),
    });
    assert(blocked.status === 403, `configured Host and Origin allowlists must be enforced (${JSON.stringify(extraHeaders)} returned ${blocked.status})`);
  }

  const initialized = await rpc(base, 'initialize', {
    protocolVersion: '2025-06-18',
    capabilities: {},
    clientInfo: { name: 'protocol-smoke', version: '1.0.0' },
  });
  assert(initialized.result?.serverInfo?.name === 'heihu-workorder-diagnostics', 'initialize must return server identity');

  const listed = await rpc(base, 'tools/list', {});
  const toolNames = listed.result.tools.map((tool) => tool.name);
  for (const name of ['system_health', 'list_diagnostic_endpoints', 'inspect_api', 'run_smoke_test', 'search_system_logs', 'inspect_online_sessions', 'inspect_cache']) {
    assert(toolNames.includes(name), `missing tool: ${name}`);
  }

  const health = await rpc(base, 'tools/call', { name: 'system_health', arguments: {} });
  const healthData = toolData(health);
  assert(healthData.ok === true && healthData.backend.readiness.status === 200, 'system_health must pass');

  const inspection = await rpc(base, 'tools/call', {
    name: 'inspect_api',
    arguments: { endpoint: 'products', query: { page: 1, size: 1 } },
  });
  const inspectionData = toolData(inspection);
  assert(inspectionData.ok === true, 'inspect_api must pass');
  const serialized = JSON.stringify(inspectionData);
  assert(!serialized.includes('13800000000') && !serialized.includes('must-hide'), 'inspect_api must redact sensitive fields');

  const failedInspection = await rpc(base, 'tools/call', {
    name: 'inspect_api',
    arguments: { endpoint: 'reports', query: { page: 1, size: 1 } },
  });
  const failedInspectionData = toolData(failedInspection);
  assert(failedInspectionData.ok === false && failedInspectionData.status === 503, 'inspect_api must preserve backend 5xx diagnostics');

  const smoke = await rpc(base, 'tools/call', {
    name: 'run_smoke_test',
    arguments: { endpoints: ['factory', 'products', 'notifications'] },
  });
  const smokeData = toolData(smoke);
  assert(smokeData.ok === true && smokeData.total === 3 && smokeData.failed === 0, 'run_smoke_test must pass');

  for (const [name, args] of [
    ['search_system_logs', { kind: 'operations', module: 'MCP', page: 1, size: 1 }],
    ['inspect_online_sessions', { page: 1, size: 1 }],
    ['inspect_cache', {}],
  ]) {
    const result = await rpc(base, 'tools/call', { name, arguments: args });
    assert(toolData(result).ok === true, `${name} must pass`);
  }

  const deniedPath = await rpc(base, 'tools/call', {
    name: 'inspect_api',
    arguments: { endpoint: 'products', query: { arbitrary: 'nope' } },
  });
  assert(deniedPath.result?.isError === true, 'unknown query parameters must be rejected');

  expireCacheToken = true;
  const refreshed = await rpc(base, 'tools/call', { name: 'inspect_cache', arguments: {} });
  assert(toolData(refreshed).ok === true, 'parallel cache requests must recover from expired tokens');
  assert(loginCount === 2, 'staggered 401 responses must share one token refresh');

  const redirected = toolData(await rpc(base, 'tools/call', { name: 'inspect_api', arguments: { endpoint: 'products', query: { name: 'redirect' } } }));
  assert(redirected.status === 302 && redirectTargetCount === 0, 'backend redirects must not escape the endpoint allowlist');
  const oversized = await rpc(base, 'tools/call', { name: 'inspect_api', arguments: { endpoint: 'products', query: { name: 'oversized' } } });
  assert(toolData(oversized).error?.code === 'BACKEND_RESPONSE_TOO_LARGE', 'oversized responses must fail with a bounded diagnostic');
  const nonJson = await rpc(base, 'tools/call', { name: 'inspect_api', arguments: { endpoint: 'products', query: { name: 'non-json' } } });
  assert(!JSON.stringify(nonJson).includes('must-hide'), 'non-JSON upstream errors must not leak unredacted secrets');
  const timedOut = await rpc(base, 'tools/call', { name: 'inspect_api', arguments: { endpoint: 'products', query: { name: 'timeout' } } });
  assert(toolData(timedOut).error?.code === 'BACKEND_TIMEOUT', 'backend timeouts must be reported');
  const recovered = await rpc(base, 'tools/call', { name: 'inspect_api', arguments: { endpoint: 'products' } });
  assert(toolData(recovered).ok === true, 'a timeout must not poison subsequent requests');
  assert(backendMethods.every(([method, pathname]) => method === 'GET' || (method === 'POST' && pathname === '/api/auth/login')), 'only sign-in may write to the backend');
  console.log('MCP protocol smoke passed: discovery, auth, Host/Origin, diagnostics, redaction, allowlist, parallel refresh, redirects, response limits, timeout recovery');
} finally {
  mcp.kill('SIGTERM');
  backend.close();
  await Promise.race([
    once(mcp, 'close'),
    new Promise((resolve) => setTimeout(resolve, 1000)),
  ]);
  if (mcp.exitCode === null) mcp.kill('SIGKILL');
}

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

function rawRequest(url, options) {
  return new Promise((resolve, reject) => {
    const request = http.request(url, { method: options.method, headers: options.headers }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => resolve({ status: response.statusCode, body: Buffer.concat(chunks).toString() }));
    });
    request.on('error', reject);
    request.end(options.body);
  });
}

async function waitForHealth(child, base, getOutput) {
  // Cold module loading on Windows/OneDrive can exceed the old four-second limit.
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${base}/healthz`, { signal: AbortSignal.timeout(1000) });
      if (response.ok) return;
    } catch {
      // The child process may still be binding its port.
    }
    if (child.exitCode !== null) throw new Error(`MCP server exited early: ${getOutput()}`);
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`MCP server did not start: ${getOutput()}`);
}

async function freePort() {
  const probe = net.createServer();
  await new Promise((resolve) => probe.listen(0, '127.0.0.1', resolve));
  const port = probe.address().port;
  await new Promise((resolve) => probe.close(resolve));
  return port;
}

async function rpc(base, method, params) {
  const response = await fetch(`${base}/mcp`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream',
    },
    body: JSON.stringify({ jsonrpc: '2.0', id: Math.random().toString(36).slice(2), method, params }),
  });
  const body = await response.text();
  assert(response.status === 200, `${method} returned HTTP ${response.status}: ${body}`);
  return JSON.parse(body);
}

function toolData(response) {
  const content = response.result?.structuredContent;
  if (content) return content;
  const text = response.result?.content?.find((item) => item.type === 'text')?.text;
  return text ? JSON.parse(text) : {};
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

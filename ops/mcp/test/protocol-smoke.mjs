import http from 'node:http';
import { once } from 'node:events';
import { spawn } from 'node:child_process';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const key = 'protocol-test-mcp-key';

const backend = http.createServer(async (req, res) => {
  const requestUrl = new URL(req.url || '/', 'http://127.0.0.1');
  if (req.method === 'POST' && requestUrl.pathname === '/api/auth/login') {
    return send(res, 200, { token: 'mock-backend-token' });
  }
  if (requestUrl.pathname === '/actuator/health/readiness' || requestUrl.pathname === '/actuator/health/liveness') {
    return send(res, 200, { status: 'UP' });
  }
  if (!req.headers.authorization) return send(res, 401, { message: 'missing auth' });
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
    MCP_RATE_LIMIT_PER_MINUTE: '1000',
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
  console.log('MCP protocol smoke passed: initialize, discovery, auth, diagnostics, smoke, logs, sessions, cache, redaction, allowlist');
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

async function waitForHealth(child, base, getOutput) {
  for (let attempt = 0; attempt < 80; attempt += 1) {
    try {
      const response = await fetch(`${base}/healthz`);
      if (response.ok) return;
    } catch {
      // The child process may still be binding its port.
    }
    if (child.exitCode !== null) throw new Error(`MCP server exited early: ${getOutput()}`);
    await new Promise((resolve) => setTimeout(resolve, 50));
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

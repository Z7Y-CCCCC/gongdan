import http from 'node:http';
import { randomUUID } from 'node:crypto';
import { timingSafeEqual } from 'node:crypto';
import { URL } from 'node:url';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { z } from 'zod';

const VERSION = '1.0.0';
const DEFAULT_PORT = 9300;
const DEFAULT_BACKEND_URL = 'http://127.0.0.1:8080';
const MAX_BODY_BYTES = 256 * 1024;
const MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
const DEFAULT_TIMEOUT_MS = 10_000;
const DEFAULT_RATE_LIMIT = 60;
const MCP_TOOL_COUNT = 7;

const READ_ONLY_ANNOTATIONS = {
  readOnlyHint: true,
  destructiveHint: false,
  idempotentHint: true,
  openWorldHint: false,
};

const API_ENDPOINTS = Object.freeze({
  factory: { path: '/api/factory', label: 'Current factory profile', query: [] },
  users: { path: '/api/users', label: 'Factory users', query: [] },
  departments: { path: '/api/departments', label: 'Departments', query: ['code', 'name', 'page', 'size'] },
  units: { path: '/api/units', label: 'Units', query: ['name', 'page', 'size'] },
  warehouses: { path: '/api/warehouses', label: 'Warehouses', query: ['code', 'name', 'page', 'size'] },
  suppliers: { path: '/api/suppliers', label: 'Suppliers', query: ['code', 'name', 'page', 'size'] },
  customers: { path: '/api/customers', label: 'Customers', query: ['code', 'name', 'page', 'size'] },
  products: { path: '/api/products', label: 'Products', query: ['code', 'name', 'spec', 'page', 'size'] },
  product_operation_logs: { path: '/api/products/operation-logs', label: 'Product operation logs', query: ['page', 'size'] },
  product_categories: { path: '/api/products/categories', label: 'Product categories', query: [] },
  boms: { path: '/api/boms', label: 'Bills of materials', query: ['code', 'productName', 'page', 'size'] },
  processes: { path: '/api/processes', label: 'Processes', query: ['code', 'name', 'page', 'size'] },
  process_routes: { path: '/api/process-routes', label: 'Process routes', query: ['code', 'name', 'page', 'size'] },
  defect_items: { path: '/api/defect-items', label: 'Defect items', query: ['code', 'name', 'page', 'size'] },
  custom_fields: { path: '/api/custom-fields', label: 'Custom fields', query: ['scope'] },
  work_order_categories: { path: '/api/work-orders/categories', label: 'Work-order categories', query: [] },
  work_orders: { path: '/api/work-orders', label: 'Work orders', query: ['code', 'productName', 'page', 'size'] },
  sales_orders: { path: '/api/sales-orders', label: 'Sales orders', query: ['code', 'customerName', 'status', 'page', 'size'] },
  inbound_receipts: { path: '/api/inbound-receipts', label: 'Inbound receipts', query: ['code', 'inboundType', 'startTime', 'endTime', 'page', 'size'] },
  outbound_receipts: { path: '/api/outbound-receipts', label: 'Outbound receipts', query: ['code', 'outboundType', 'startTime', 'endTime', 'page', 'size'] },
  inventory_details: { path: '/api/inventory-details', label: 'Inventory details', query: ['receiptCode', 'receiptType', 'sourceType', 'remark', 'productCode', 'productName', 'productSpec', 'productAttribute', 'startTime', 'endTime', 'page', 'size'] },
  reports: { path: '/api/reports', label: 'Production reports', query: ['status', 'workOrderCode', 'reporterName', 'period', 'page', 'size'] },
  advanced_records: { path: '/api/advanced-records', label: 'Advanced operation records', query: ['moduleName', 'status', 'page', 'size'] },
  dashboard_process_cards: { path: '/api/home-dashboard/process-cards', label: 'Dashboard process cards', query: ['range', 'includeNotStarted'] },
  dashboard_work_orders: { path: '/api/home-dashboard/work-orders', label: 'Dashboard work orders', query: ['status'] },
  dashboard_performance: { path: '/api/home-dashboard/performance', label: 'Dashboard performance', query: ['range'] },
  dashboard_recent_reports: { path: '/api/home-dashboard/reports/recent', label: 'Recent dashboard reports', query: ['since', 'limit'] },
  notifications: { path: '/api/notifications', label: 'Notifications', query: ['scope', 'unreadOnly', 'eventType', 'priority', 'keyword', 'page', 'size'] },
  notification_unread_count: { path: '/api/notifications/unread-count', label: 'Unread notification count', query: [] },
  chat_contacts: { path: '/api/internal-chat/contacts', label: 'Internal chat contacts', query: [] },
  mobile_work_orders: { path: '/api/mobile/work-orders', label: 'Mobile work orders', query: ['code', 'productName', 'page', 'size'] },
  mobile_reports: { path: '/api/mobile/reports', label: 'Mobile reports', query: ['status', 'period', 'page', 'size'] },
  mobile_field_events: { path: '/api/mobile/field-events', label: 'Mobile field events', query: ['status', 'page', 'size'] },
  plugins: { path: '/api/platform/plugins', label: 'Installed plugins', query: [] },
  plugin_runtime_manifest: { path: '/api/platform/plugins/runtime-manifest', label: 'Plugin runtime manifest', query: ['role'] },
  import_logs: { path: '/api/system-logs/imports', label: 'Import audit logs', query: ['module', 'page', 'size'] },
  operation_logs: { path: '/api/system-logs/operations', label: 'Operation audit logs', query: ['module', 'page', 'size'] },
  online_sessions: { path: '/api/monitor/online', label: 'Online sessions', query: ['ipAddress', 'loginName', 'page', 'size'] },
  cache_metrics: { path: '/api/monitor/cache', label: 'Cache metrics', query: [] },
  cache_names: { path: '/api/monitor/cache/names', label: 'Cache names', query: [] },
  cache_recoveries: { path: '/api/monitor/cache/recovery', label: 'Cache recovery records', query: [] },
});

const DEFAULT_SMOKE_ENDPOINTS = [
  'factory',
  'departments',
  'products',
  'work_orders',
  'inbound_receipts',
  'outbound_receipts',
  'inventory_details',
  'dashboard_process_cards',
  'dashboard_performance',
  'notifications',
  'notification_unread_count',
  'chat_contacts',
  'plugins',
  'operation_logs',
  'cache_metrics',
  'cache_names',
];

class FixedWindowRateLimiter {
  constructor(limit) {
    this.limit = limit;
    this.windowMs = 60_000;
    this.entries = new Map();
    this.cleanupTimer = setInterval(() => this.cleanup(), this.windowMs);
    this.cleanupTimer.unref();
  }

  allow(key) {
    const now = Date.now();
    const existing = this.entries.get(key);
    if (!existing || now - existing.startedAt >= this.windowMs) {
      this.entries.set(key, { startedAt: now, count: 1 });
      return true;
    }
    existing.count += 1;
    return existing.count <= this.limit;
  }

  cleanup() {
    const cutoff = Date.now() - this.windowMs;
    for (const [key, entry] of this.entries) {
      if (entry.startedAt < cutoff) this.entries.delete(key);
    }
  }
}

const config = loadConfig();
const rateLimiter = new FixedWindowRateLimiter(config.rateLimit);
let backendToken = config.backendToken || null;
let backendTokenPromise = null;

function loadConfig() {
  const nodeEnv = process.env.NODE_ENV || 'development';
  const apiKey = clean(process.env.MCP_API_KEY);
  const allowInsecureLocal = parseBoolean(
    process.env.MCP_ALLOW_INSECURE_LOCAL,
    nodeEnv !== 'production',
  );
  if (nodeEnv === 'production' && (isPlaceholderSecret(apiKey) || apiKey.length < 24)) {
    throw new Error('MCP_API_KEY must be a real random secret with at least 24 characters when NODE_ENV=production');
  }

  const backendUrl = clean(process.env.MCP_BACKEND_URL) || DEFAULT_BACKEND_URL;
  let parsedBackendUrl;
  try {
    parsedBackendUrl = new URL(backendUrl);
  } catch {
    throw new Error('MCP_BACKEND_URL must be a valid URL');
  }
  if (!['http:', 'https:'].includes(parsedBackendUrl.protocol)) {
    throw new Error('MCP_BACKEND_URL must use http or https');
  }

  const port = parseInteger(process.env.MCP_PORT, DEFAULT_PORT, 0, 65_535);
  const host = clean(process.env.MCP_HOST) || (nodeEnv === 'production' ? '127.0.0.1' : '127.0.0.1');
  const allowedHosts = csv(process.env.MCP_ALLOWED_HOSTS);
  const allowedOrigins = csv(process.env.MCP_ALLOWED_ORIGINS);
  const backendToken = clean(process.env.MCP_BACKEND_TOKEN);
  const backendPassword = clean(process.env.MCP_BACKEND_PASSWORD);

  const backendCredentialConfigured = !isPlaceholderSecret(backendToken)
    || (clean(process.env.MCP_BACKEND_FACTORY_CODE)
      && clean(process.env.MCP_BACKEND_USERNAME)
      && !isPlaceholderSecret(backendPassword));
  if (nodeEnv === 'production' && !backendCredentialConfigured) {
    throw new Error('Configure a real MCP_BACKEND_TOKEN or a dedicated backend factory/user/password when NODE_ENV=production');
  }
  if (!backendCredentialConfigured) {
    console.warn('[mcp] backend credentials are not configured; health and tool discovery work, backend diagnostics will return configuration errors');
  }

  return {
    nodeEnv,
    apiKey,
    allowInsecureLocal,
    host,
    port,
    backendUrl: parsedBackendUrl.toString().replace(/\/$/, ''),
    backendFactoryCode: clean(process.env.MCP_BACKEND_FACTORY_CODE) || 'HHGD',
    backendUsername: clean(process.env.MCP_BACKEND_USERNAME) || 'admin',
    backendPassword,
    backendToken,
    timeoutMs: parseInteger(process.env.MCP_BACKEND_TIMEOUT_MS, DEFAULT_TIMEOUT_MS, 250, 30_000),
    rateLimit: parseInteger(process.env.MCP_RATE_LIMIT_PER_MINUTE, DEFAULT_RATE_LIMIT, 1, 10_000),
    allowedHosts,
    allowedOrigins,
  };
}

function isPlaceholderSecret(value) {
  if (!value) return true;
  return /^(change-|replace-with|<.*>|example|local-|test-)/i.test(value.trim());
}

function clean(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : '';
}

function csv(value) {
  return clean(value).split(',').map((item) => item.trim()).filter(Boolean);
}

function parseBoolean(value, fallback) {
  if (value === undefined || value === null || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
}

function parseInteger(value, fallback, min, max) {
  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback;
}

function isLoopback(address) {
  if (!address) return false;
  return address === '127.0.0.1' || address === '::1' || address === '::ffff:127.0.0.1';
}

function requestId(req) {
  const supplied = clean(req.headers['x-request-id']);
  return supplied && supplied.length <= 80 ? supplied : randomUUID();
}

function json(res, status, payload, headers = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    ...headers,
  });
  res.end(body);
}

function errorPayload(message, code = 'MCP_REQUEST_FAILED') {
  return {
    ok: false,
    error: { code, message },
  };
}

function textResult(data, isError = false) {
  return {
    content: [{ type: 'text', text: JSON.stringify(data) }],
    structuredContent: data,
    ...(isError ? { isError: true } : {}),
  };
}

function safeError(error, fallback = 'The diagnostic request failed') {
  if (error?.code === 'MCP_CONFIGURATION') return error.message;
  if (error?.code === 'BACKEND_TIMEOUT') return 'The backend did not respond before the diagnostic timeout';
  if (error?.code === 'BACKEND_UNAUTHORIZED') return 'The configured backend diagnostic identity is not authorized or has expired';
  if (error?.code === 'BACKEND_HTTP') return `The backend returned HTTP ${error.status}`;
  return fallback;
}

function toolFailure(error, fallback) {
  return textResult(errorPayload(safeError(error, fallback), error?.code || 'MCP_REQUEST_FAILED'), true);
}

function registerTools(server) {
  server.registerTool(
    'system_health',
    {
      title: 'System health',
      description: 'Use this when you need a quick liveness and readiness check for the work-order backend.',
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async () => {
      try {
        const started = Date.now();
        const readiness = await backendRequest('/actuator/health/readiness', { auth: false, allowHttpErrors: true });
        const liveness = await backendRequest('/actuator/health/liveness', { auth: false, allowHttpErrors: true });
        return textResult({
          ok: readiness.status === 200 && liveness.status === 200,
          checkedAt: new Date().toISOString(),
          durationMs: Date.now() - started,
          backend: {
            baseUrl: config.backendUrl,
            readiness: summarizeHttpResponse(readiness),
            liveness: summarizeHttpResponse(liveness),
          },
        });
      } catch (error) {
        return toolFailure(error, 'The backend health check failed');
      }
    },
  );

  server.registerTool(
    'list_diagnostic_endpoints',
    {
      title: 'List diagnostic endpoints',
      description: 'Use this before interface debugging to discover the read-only backend endpoint keys allowed by this MCP gateway.',
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async () => textResult({
      ok: true,
      endpoints: Object.entries(API_ENDPOINTS).map(([key, definition]) => ({
        key,
        label: definition.label,
        method: 'GET',
        path: definition.path,
        query: definition.query,
      })),
      writeAccess: false,
    }),
  );

  server.registerTool(
    'inspect_api',
    {
      title: 'Inspect a backend API',
      description: 'Use this to debug one known read-only backend API and inspect its sanitized response, HTTP status, and latency.',
      inputSchema: {
        endpoint: z.enum(Object.keys(API_ENDPOINTS)),
        query: z.record(z.string(), z.union([z.string(), z.number(), z.boolean()])).optional(),
      },
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async ({ endpoint, query }) => {
      try {
        const definition = requireEndpoint(endpoint);
        const params = validateQuery(definition, query || {});
        const response = await backendRequest(definition.path, { query: params, allowHttpErrors: true });
        return textResult({
          ok: response.status >= 200 && response.status < 300,
          endpoint,
          method: 'GET',
          path: definition.path,
          query: params,
          ...summarizeHttpResponse(response, true),
        });
      } catch (error) {
        return toolFailure(error, 'The backend API inspection failed');
      }
    },
  );

  server.registerTool(
    'run_smoke_test',
    {
      title: 'Run read-only smoke test',
      description: 'Use this after a deployment or suspected incident to probe a bounded set of important read-only APIs and report failures with latency.',
      inputSchema: {
        endpoints: z.array(z.enum(Object.keys(API_ENDPOINTS))).max(30).optional(),
      },
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async ({ endpoints }) => {
      const selected = endpoints?.length ? [...new Set(endpoints)] : DEFAULT_SMOKE_ENDPOINTS;
      const results = [];
      for (const endpoint of selected) {
        const definition = requireEndpoint(endpoint);
        const query = smokeQuery(endpoint);
        const started = Date.now();
        try {
          const response = await backendRequest(definition.path, { query, allowHttpErrors: true });
          results.push({
            endpoint,
            method: 'GET',
            path: definition.path,
            ok: response.status >= 200 && response.status < 300,
            status: response.status,
            durationMs: Date.now() - started,
          });
        } catch (error) {
          results.push({
            endpoint,
            method: 'GET',
            path: definition.path,
            ok: false,
            status: error.status || 0,
            durationMs: Date.now() - started,
            error: safeError(error),
          });
        }
      }
      return textResult({
        ok: results.every((item) => item.ok),
        checkedAt: new Date().toISOString(),
        total: results.length,
        passed: results.filter((item) => item.ok).length,
        failed: results.filter((item) => !item.ok).length,
        results,
      });
    },
  );

  server.registerTool(
    'search_system_logs',
    {
      title: 'Search system logs',
      description: 'Use this when you need recent import or operation audit records for fault localization; results are paginated and sanitized.',
      inputSchema: {
        kind: z.enum(['operations', 'imports']).default('operations'),
        module: z.string().trim().min(1).max(80),
        page: z.number().int().min(1).max(1000).optional(),
        size: z.number().int().min(1).max(50).optional(),
      },
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async ({ kind, module, page, size }) => {
      try {
        const endpoint = kind === 'imports' ? API_ENDPOINTS.import_logs : API_ENDPOINTS.operation_logs;
        const response = await backendRequest(endpoint.path, {
          query: { module, page: page || 1, size: size || 20 },
          allowHttpErrors: true,
        });
        return textResult({
          ok: response.status >= 200 && response.status < 300,
          kind,
          module,
          ...summarizeHttpResponse(response, true),
        });
      } catch (error) {
        return toolFailure(error, 'The system log query failed');
      }
    },
  );

  server.registerTool(
    'inspect_online_sessions',
    {
      title: 'Inspect online sessions',
      description: 'Use this when you need to diagnose active sessions, device metadata, or a suspicious login for the current factory.',
      inputSchema: {
        loginName: z.string().trim().max(80).optional(),
        ipAddress: z.string().trim().max(80).optional(),
        page: z.number().int().min(1).max(1000).optional(),
        size: z.number().int().min(1).max(50).optional(),
      },
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async ({ loginName, ipAddress, page, size }) => {
      try {
        const response = await backendRequest(API_ENDPOINTS.online_sessions.path, {
          query: { loginName, ipAddress, page: page || 1, size: size || 20 },
          allowHttpErrors: true,
        });
        return textResult({
          ok: response.status >= 200 && response.status < 300,
          ...summarizeHttpResponse(response, true),
        });
      } catch (error) {
        return toolFailure(error, 'The online session query failed');
      }
    },
  );

  server.registerTool(
    'inspect_cache',
    {
      title: 'Inspect cache status',
      description: 'Use this when diagnosing stale data, cache pressure, or recent cache recovery records without exposing cache values or keys.',
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async () => {
      try {
        const [metrics, names, recoveries] = await Promise.all([
          backendRequest(API_ENDPOINTS.cache_metrics.path, { allowHttpErrors: true }),
          backendRequest(API_ENDPOINTS.cache_names.path, { allowHttpErrors: true }),
          backendRequest(API_ENDPOINTS.cache_recoveries.path, { allowHttpErrors: true }),
        ]);
        return textResult({
          ok: [metrics, names, recoveries].every((item) => item.status >= 200 && item.status < 300),
          metrics: summarizeHttpResponse(metrics, true),
          names: summarizeHttpResponse(names, true),
          recoveries: summarizeHttpResponse(recoveries, true),
        });
      } catch (error) {
        return toolFailure(error, 'The cache inspection failed');
      }
    },
  );
}

function requireEndpoint(endpoint) {
  const definition = API_ENDPOINTS[endpoint];
  if (!definition) {
    const error = new Error('The requested endpoint is not on the diagnostic allowlist');
    error.code = 'MCP_ENDPOINT_NOT_ALLOWED';
    throw error;
  }
  return definition;
}

function validateQuery(definition, query) {
  const output = {};
  for (const [key, value] of Object.entries(query)) {
    if (!definition.query.includes(key)) {
      const error = new Error(`Query parameter ${key} is not allowed for this endpoint`);
      error.code = 'MCP_QUERY_NOT_ALLOWED';
      throw error;
    }
    if (value === undefined || value === null || value === '') continue;
    const stringValue = String(value);
    if (stringValue.length > 160) {
      const error = new Error(`Query parameter ${key} is too long`);
      error.code = 'MCP_QUERY_TOO_LARGE';
      throw error;
    }
    output[key] = stringValue;
  }
  return output;
}

function smokeQuery(endpoint) {
  const defaults = {
    users: { page: 1, size: 1 },
    departments: { page: 1, size: 1 },
    units: { page: 1, size: 1 },
    warehouses: { page: 1, size: 1 },
    suppliers: { page: 1, size: 1 },
    customers: { page: 1, size: 1 },
    products: { page: 1, size: 1 },
    processes: { page: 1, size: 1 },
    process_routes: { page: 1, size: 1 },
    work_orders: { page: 1, size: 1 },
    sales_orders: { page: 1, size: 1 },
    inbound_receipts: { page: 1, size: 1 },
    outbound_receipts: { page: 1, size: 1 },
    inventory_details: { page: 1, size: 1 },
    reports: { page: 1, size: 1 },
    advanced_records: { moduleName: 'SOP', page: 1, size: 1 },
    dashboard_process_cards: { range: 'TODAY', includeNotStarted: false },
    dashboard_work_orders: { status: 'in_progress' },
    dashboard_performance: { range: 'TODAY' },
    dashboard_recent_reports: { limit: 1 },
    notifications: { page: 1, size: 1 },
    import_logs: { module: 'MCP', page: 1, size: 1 },
    operation_logs: { module: 'MCP', page: 1, size: 1 },
    online_sessions: { page: 1, size: 1 },
  };
  return defaults[endpoint] || {};
}

async function backendRequest(path, options = {}) {
  const url = new URL(path, `${config.backendUrl}/`);
  for (const [key, value] of Object.entries(options.query || {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  }
  const auth = options.auth === false ? null : await getBackendToken();
  const result = await fetchWithTimeout(url, auth);
  if (result.status === 401 && options.auth !== false && config.backendPassword && !config.backendToken && !options.retried) {
    // A late 401 for an older token must not invalidate a token another request
    // has already refreshed (the backend allows only one session per identity).
    await getBackendToken(auth);
    return backendRequest(path, { ...options, retried: true });
  }
  if (result.status === 401) {
    const error = new Error('The backend rejected the diagnostic identity');
    error.code = 'BACKEND_UNAUTHORIZED';
    error.status = result.status;
    throw error;
  }
  if (result.status >= 500 && !options.allowHttpErrors) {
    const error = new Error(`The backend returned HTTP ${result.status}`);
    error.code = 'BACKEND_HTTP';
    error.status = result.status;
    throw error;
  }
  return result;
}

async function getBackendToken(rejectedToken = null) {
  if (config.backendToken) return config.backendToken;
  if (!config.backendPassword) {
    const error = new Error('Configure MCP_BACKEND_TOKEN or MCP_BACKEND_FACTORY_CODE, MCP_BACKEND_USERNAME and MCP_BACKEND_PASSWORD');
    error.code = 'MCP_CONFIGURATION';
    throw error;
  }
  if (rejectedToken && backendToken === rejectedToken) backendToken = null;
  if (backendTokenPromise) return backendTokenPromise;
  if (backendToken) return backendToken;
  backendTokenPromise = (async () => {
    const url = new URL('/api/auth/login', `${config.backendUrl}/`);
    const response = await fetchWithTimeout(url, null, {
      method: 'POST',
      body: JSON.stringify({
        factoryCode: config.backendFactoryCode,
        username: config.backendUsername,
        password: config.backendPassword,
      }),
    });
    if (response.status < 200 || response.status >= 300 || !response.body?.token) {
      const error = new Error('The MCP backend identity could not sign in');
      error.code = 'BACKEND_UNAUTHORIZED';
      error.status = response.status;
      throw error;
    }
    backendToken = response.body.token;
    return backendToken;
  })();
  try {
    return await backendTokenPromise;
  } finally {
    backendTokenPromise = null;
  }
}

async function fetchWithTimeout(url, token, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.timeoutMs);
  try {
    const response = await fetch(url, {
      method: options.method || 'GET',
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: options.body,
      signal: controller.signal,
      // Never follow a diagnostic GET or credential POST outside its allowlist.
      redirect: 'manual',
    });
    const chunks = [];
    let size = 0;
    if (response.body) {
      const reader = response.body.getReader();
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          size += value.byteLength;
          if (size > MAX_RESPONSE_BYTES) {
            await reader.cancel();
            const error = new Error('The backend response exceeded the diagnostic response limit');
            error.code = 'BACKEND_RESPONSE_TOO_LARGE';
            throw error;
          }
          chunks.push(value);
        }
      } finally {
        reader.releaseLock();
      }
    }
    const text = Buffer.concat(chunks, size).toString('utf8');
    let body = null;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        // Arbitrary HTML/text cannot be reliably redacted by JSON field names.
        body = { format: 'non-json', omitted: true };
      }
    }
    return { status: response.status, headers: response.headers, body };
  } catch (error) {
    if (error.name === 'AbortError') {
      const timeout = new Error('The backend request timed out');
      timeout.code = 'BACKEND_TIMEOUT';
      throw timeout;
    }
    if (error.code?.startsWith('BACKEND_')) throw error;
    const network = new Error('The backend is unavailable');
    network.code = 'BACKEND_UNAVAILABLE';
    throw network;
  } finally {
    clearTimeout(timer);
  }
}

function summarizeHttpResponse(response, includeBody = false) {
  const result = { status: response.status };
  if (includeBody) result.body = sanitize(response.body);
  else if (response.body && typeof response.body === 'object') result.bodyKeys = Object.keys(response.body).slice(0, 30);
  return result;
}

function sanitize(value, key = '') {
  if (Array.isArray(value)) return value.slice(0, 100).map((item) => sanitize(item));
  if (!value || typeof value !== 'object') return value;
  const output = {};
  for (const [childKey, childValue] of Object.entries(value)) {
    if (isSensitiveKey(childKey) || isSensitiveKey(key)) {
      output[childKey] = '[REDACTED]';
    } else {
      output[childKey] = sanitize(childValue, childKey);
    }
  }
  return output;
}

function isSensitiveKey(key) {
  return /pass(word)?|token|secret|authorization|cookie|accesskey|access_key|privatekey|private_key|phone|mobile|smscode|sms_code/i.test(String(key));
}

async function readBody(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      const error = new Error('Request body is too large');
      error.code = 'MCP_BODY_TOO_LARGE';
      throw error;
    }
    chunks.push(chunk);
  }
  if (!chunks.length) return undefined;
  const raw = Buffer.concat(chunks).toString('utf8');
  try {
    return JSON.parse(raw);
  } catch {
    const error = new Error('Request body must be valid JSON');
    error.code = 'MCP_INVALID_JSON';
    throw error;
  }
}

function authorize(req) {
  if (config.apiKey) {
    const authorization = clean(req.headers.authorization);
    const supplied = authorization.startsWith('Bearer ')
      ? authorization.slice('Bearer '.length).trim()
      : clean(req.headers['x-mcp-api-key']);
    if (!supplied || !sameSecret(supplied, config.apiKey)) return false;
    return true;
  }
  return config.allowInsecureLocal && isLoopback(req.socket.remoteAddress);
}

function sameSecret(left, right) {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer);
}

function healthPayload() {
  return {
    ok: true,
    service: 'heihu-workorder-mcp',
    version: VERSION,
    transport: 'Streamable HTTP',
    readOnly: true,
    backendConfigured: Boolean(config.backendToken || config.backendPassword),
    authentication: config.apiKey ? 'api-key' : 'loopback-only-development-mode',
    tools: MCP_TOOL_COUNT,
    checkedAt: new Date().toISOString(),
  };
}

async function handleMcp(req, res, body) {
  const server = new McpServer({ name: 'heihu-workorder-diagnostics', version: VERSION });
  registerTools(server);
  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
    enableJsonResponse: true,
    // SDK allowlists are ignored unless this flag is explicitly enabled.
    enableDnsRebindingProtection: true,
    ...(config.allowedHosts.length ? { allowedHosts: config.allowedHosts } : {}),
    ...(config.allowedOrigins.length ? { allowedOrigins: config.allowedOrigins } : {}),
  });
  try {
    await server.connect(transport);
    await transport.handleRequest(req, res, body);
  } finally {
    await server.close().catch(() => {});
  }
}

const server = http.createServer(async (req, res) => {
  const id = requestId(req);
  res.setHeader('X-Request-ID', id);
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    if (url.pathname === '/healthz' && req.method === 'GET') {
      json(res, 200, healthPayload());
      return;
    }
    if (url.pathname !== '/mcp') {
      json(res, 404, errorPayload('Not found', 'MCP_NOT_FOUND'));
      return;
    }
    if (!authorize(req)) {
      json(res, 401, errorPayload('A valid MCP API key is required', 'MCP_UNAUTHORIZED'), { 'WWW-Authenticate': 'Bearer' });
      return;
    }
    const rateKey = req.socket.remoteAddress || 'unknown';
    if (!rateLimiter.allow(rateKey)) {
      json(res, 429, errorPayload('MCP rate limit exceeded', 'MCP_RATE_LIMITED'), { 'Retry-After': '60' });
      return;
    }
    if (req.method !== 'POST') {
      json(res, 405, errorPayload('Only POST /mcp is enabled for this stateless diagnostic server', 'MCP_METHOD_NOT_ALLOWED'), { Allow: 'POST' });
      return;
    }
    if (req.headers['content-type'] && !String(req.headers['content-type']).toLowerCase().includes('application/json')) {
      json(res, 415, errorPayload('MCP requests must use application/json', 'MCP_UNSUPPORTED_MEDIA_TYPE'));
      return;
    }
    const body = await readBody(req);
    await handleMcp(req, res, body);
  } catch (error) {
    if (!res.headersSent) {
      const status = error.code === 'MCP_BODY_TOO_LARGE' ? 413
        : error.code === 'MCP_INVALID_JSON' ? 400 : 500;
      json(res, status, errorPayload(
        status === 500 ? 'The MCP request could not be processed' : error.message,
        error.code || 'MCP_INTERNAL_ERROR',
      ));
    } else if (!res.writableEnded) {
      res.end();
    }
    console.error(`[mcp] request ${id} failed: ${error.code || error.name || 'Error'}`);
  }
});

server.listen(config.port, config.host, () => {
  const address = server.address();
  const boundPort = typeof address === 'object' && address ? address.port : config.port;
  console.log(`[mcp] listening on http://${config.host}:${boundPort}/mcp`);
  console.log(`[mcp] backend=${config.backendUrl} environment=${config.nodeEnv} readOnly=true`);
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));

import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const DEFAULT_PLUGIN_ORIGINS = 'http://localhost:9101,http://localhost:9102'
const marker = '# __PLUGIN_FRAME_SOURCE_MAP__'

export function parsePluginOrigins(value = DEFAULT_PLUGIN_ORIGINS) {
  if (typeof value !== 'string' || value.length > 4096) throw new Error('Plugin origin list must be a string of at most 4096 characters')
  if (!value.trim()) return []
  const entries = value.split(',')
  if (entries.length > 32) throw new Error('At most 32 plugin origins may be configured')
  return [...new Set(entries.map((entry) => {
    const origin = entry.trim()
    // Accept origins, not paths, CSP keywords, wildcard hosts, credentials or
    // Nginx syntax. Canonicalize ports, IP literals and case using the URL parser.
    if (!/^https?:\/\/(?:\[[0-9a-f:.]+\]|[a-z0-9.-]+)(?::[0-9]+)?$/i.test(origin)) {
      throw new Error('Each plugin entry must be an explicit HTTP(S) origin without paths, wildcards or credentials')
    }
    let url
    try { url = new URL(origin) } catch { throw new Error('Invalid plugin origin') }
    if (url.port === '0') throw new Error('Plugin origin port must be between 1 and 65535')
    if (!url.hostname.startsWith('[')) {
      const host = url.hostname.replace(/\.$/, '')
      if (host.length > 253 || host.split('.').some(label => !/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i.test(label))) {
        throw new Error('Invalid plugin host name')
      }
    }
    return url.origin
  }))]
}

export function frameSourceTable(origins) {
  const urls = origins.map(origin => new URL(origin))
  const authoritiesFor = (url) => {
    const port = url.port || (url.protocol === 'https:' ? '443' : '80')
    return ['80', '443'].includes(port)
      ? [url.hostname, `${url.hostname}:80`, `${url.hostname}:443`]
      : [url.host]
  }
  const authorities = new Set()
  for (const url of urls) {
    for (const authority of authoritiesFor(url)) authorities.add(authority)
  }
  const table = new Map()
  for (const authority of authorities) {
    table.set(authority, origins.filter((origin, index) => {
      const url = urls[index]
      // Exclude same authority irrespective of scheme. CSP permits HTTP source
      // upgrades to HTTPS, and reverse proxies may terminate TLS before Nginx.
      return !authoritiesFor(url).includes(authority)
    }))
  }
  return table
}

export function frameSourcesForHost(origins, host) {
  const sources = frameSourceTable(origins).get(String(host).toLowerCase()) ?? origins
  return sources.length ? sources.join(' ') : "'none'"
}

export function renderNginx(
  template,
  configuredOrigins = DEFAULT_PLUGIN_ORIGINS
) {
  if (template.split(marker).length !== 2) throw new Error('Nginx template must contain exactly one plugin frame-source marker')
  const origins = parsePluginOrigins(configuredOrigins)
  const quoted = sources => JSON.stringify(sources.length ? sources.join(' ') : "'none'")
  // Even valid single-label DNS names can be Nginx map keywords. Escape them as
  // literal keys, not directives, before the config parser processes them.
  const quotedHost = host => JSON.stringify(/^(default|include|hostnames|volatile)$/.test(host) ? `\\${host}` : host)
  const lines = [
    '# Generated from APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS. Rebuild after changing it.',
    'map_hash_bucket_size 512;',
    'map $http_host $plugin_frame_src {',
    `    default ${quoted(origins)};`,
    ...[...frameSourceTable(origins)].map(([host, sources]) => `    ${quotedHost(host)} ${quoted(sources)};`),
    '}'
  ]
  return template.replace(marker, lines.join('\n'))
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [input, output] = process.argv.slice(2)
  if (!input || !output) throw new Error('Usage: node render-nginx.mjs <template.conf> <output.conf|--stdout>')
  const rendered = renderNginx(
    readFileSync(input, 'utf8'),
    process.env.APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS ?? DEFAULT_PLUGIN_ORIGINS
  )
  if (output === '--stdout') process.stdout.write(rendered)
  else writeFileSync(output, rendered, 'utf8')
}

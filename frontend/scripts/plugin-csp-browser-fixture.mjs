// Browser regression fixture for the generated production CSP. All four HTTP
// servers bind ephemeral loopback ports; no real backend/plugin is contacted.
import http from 'node:http'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { frameSourcesForHost, parsePluginOrigins, renderNginx } from './render-nginx.mjs'

const servers = []
const requests = { quality: 0, inspection: 0, untrusted: 0, same: 0 }
let mainOrigin = ''
async function listen(server) {
  servers.push(server)
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  return `http://127.0.0.1:${server.address().port}`
}
function html(res, body, policy) {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', ...(policy ? { 'Content-Security-Policy': policy } : {}) })
  res.end(body)
}
function child(name) {
  return http.createServer((req, res) => {
    if (req.url === '/ready.js') {
      res.writeHead(200, { 'Content-Type': 'text/javascript' })
      return res.end(`parent.postMessage({type:'plugin-csp-ready',name:${JSON.stringify(name)}},${JSON.stringify(mainOrigin)});`)
    }
    requests[name] += 1
    html(res, `<!doctype html><html><body><h2>${name} plugin loaded</h2><script src="/ready.js"></script></body></html>`)
  })
}
const quality = await listen(child('quality'))
const inspection = await listen(child('inspection'))
const untrusted = await listen(child('untrusted'))
let sources, desktopPolicy, mobilePolicy
const main = http.createServer((req, res) => {
  if (req.url === '/state') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    return res.end(JSON.stringify({ requests, configuredOrigins: sources, desktopPolicy, mobilePolicy }))
  }
  if (req.url === '/same-frame') {
    requests.same += 1
    return html(res, '<!doctype html><html><body>Same-origin frame must never be requested</body></html>')
  }
  if (req.url === '/observer.js') {
    res.writeHead(200, { 'Content-Type': 'text/javascript' })
    return res.end(`
      const mobile = location.pathname.startsWith('/mobile/');
      const origins = ${JSON.stringify({ quality, inspection, untrusted, same: mainOrigin })};
      window.pluginCspResult = { page: mobile ? 'mobile' : 'desktop', loaded: [], violations: [] };
      function show() { const output = document.getElementById('results'); if (output) output.textContent = JSON.stringify(window.pluginCspResult); }
      addEventListener('message', event => {
        const data = event.data;
        if (!data || data.type !== 'plugin-csp-ready') return;
        const frame = document.getElementById(data.name);
        if (!frame || frame.contentWindow !== event.source || event.origin !== origins[data.name]) return;
        window.pluginCspResult.loaded.push(data.name); show();
      });
      addEventListener('securitypolicyviolation', event => {
        if (event.effectiveDirective === 'frame-src') window.pluginCspResult.violations.push(event.blockedURI);
        show();
      });
      addEventListener('DOMContentLoaded', show);
      window.assertPluginCsp = async () => {
        const result = window.pluginCspResult;
        const expected = mobile ? [] : ['inspection', 'quality'];
        if (JSON.stringify([...result.loaded].sort()) !== JSON.stringify(expected)) throw Error('Unexpected loaded frames: ' + JSON.stringify(result));
        const rejected = mobile ? Object.values(origins) : [origins.untrusted, origins.same];
        if (!rejected.every(origin => result.violations.some(uri => uri.startsWith(origin)))) throw Error('Missing frame-src rejection: ' + JSON.stringify(result));
        const state = await (await fetch('/state')).json();
        if (state.requests.untrusted || state.requests.same) throw Error('Blocked iframe reached an HTTP server');
        return { passed: true, ...result, requests: state.requests };
      };
    `)
  }
  const mobile = req.url.startsWith('/mobile/')
  const policy = (mobile ? mobilePolicy : desktopPolicy).replaceAll('$plugin_frame_src', frameSourcesForHost(sources, req.headers.host))
  const sandbox = 'allow-forms allow-modals allow-popups allow-same-origin allow-scripts'
  html(res, `<!doctype html><html><head><title>Plugin CSP regression</title><script src="/observer.js"></script></head><body>
    <h1>${mobile ? 'Mobile: all plugin frames blocked' : 'Desktop: only two isolated plugins allowed'}</h1><output id="results">Waiting for frame checks</output>
    <iframe id="quality" title="Quality plugin" sandbox="${sandbox}" src="${quality}/"></iframe>
    <iframe id="inspection" title="Inspection plugin" sandbox="${sandbox}" src="${inspection}/"></iframe>
    <iframe id="untrusted" title="Unlisted plugin (must be blocked)" sandbox="${sandbox}" src="${untrusted}/"></iframe>
    <iframe id="same" title="Main origin (must be blocked)" sandbox="${sandbox}" src="${mainOrigin}/same-frame"></iframe>
    </body></html>`, policy)
})
mainOrigin = await listen(main)
// Include the main origin intentionally: the generated per-Host map must remove
// it even if an operator accidentally includes it in the backend allowlist.
sources = parsePluginOrigins([quality, inspection, mainOrigin].join(','))
const template = readFileSync(fileURLToPath(new URL('../nginx.conf', import.meta.url)), 'utf8')
const rendered = renderNginx(template, sources.join(','))
desktopPolicy = rendered.match(/add_header Content-Security-Policy "([^"]+)" always;/)[1]
const mobileBlock = rendered.match(/location \/mobile\/ \{([\s\S]*?)\n\s*\}/)[1]
mobilePolicy = mobileBlock.match(/add_header Content-Security-Policy "([^"]+)" always;/)[1]
console.log(`Plugin CSP browser fixture: ${mainOrigin}/ (also test ${mainOrigin}/mobile/)`)
console.log('After each navigation, evaluate: await window.assertPluginCsp()')

function close() {
  for (const server of servers) { server.closeAllConnections(); server.close() }
}
process.on('SIGINT', close)
process.on('SIGTERM', close)

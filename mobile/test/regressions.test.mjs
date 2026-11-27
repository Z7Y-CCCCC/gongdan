import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import vm from 'node:vm'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(import.meta.url)
const Vue = require('vue')
const defaultUser = { id: 7, name: '工人甲', username: 'worker', role: 'PRODUCTION', roleLabel: '生产人员', departmentName: '装配' }

function dashboardData(overrides = {}) {
  return {
    workOrderTotal: 0, totalPlanned: 0, totalGood: 0, activeOrderCount: 0,
    reportTotal: 0, pendingReportCount: 0, todayReportCount: 0, totalDefect: 0,
    todayGood: 0, todayDefect: 0, todayPending: 0, recentOrders: [], rankings: [], ...overrides
  }
}

function taskOrder(id, tasks = [{ sequenceNo: 1, processName: '装配', plannedQuantity: 100, completedQuantity: 0, enabled: true, reportPermission: 'ALL' }]) {
  return { id, code: `QA-${id}`, productName: '产品', status: 'EXECUTING', plannedQuantity: 100, tasks }
}

function deferred() {
  let resolve, reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function harness({ browser = false, services, mocks = {}, uniH5, urlApi, storage: initialStorage = {}, now = () => Date.now() } = {}) {
  const storage = new Map(Object.entries({ token: 'old-token', user: defaultUser, ...initialStorage }))
  const requests = [], uploads = [], downloads = [], choices = [], scans = [], events = []
  const hooks = { mounted: [], unmounted: [], activated: [], deactivated: [], hide: [], show: [], load: [] }
  const timers = new Map()
  const currentPages = []
  let timerId = 0
  const clone = value => value === undefined ? undefined : JSON.parse(JSON.stringify(value))
  const uni = {
    getStorageSync: key => clone(storage.get(key)),
    setStorageSync: (key, value) => storage.set(key, clone(value)),
    removeStorageSync: key => storage.delete(key),
    request: options => requests.push(options),
    uploadFile: options => uploads.push(options),
    downloadFile: options => downloads.push(options),
    chooseImage: options => choices.push(options),
    scanCode: options => scans.push(options),
    previewImage: options => events.push(['previewImage', options]),
    showToast: options => events.push(['toast', options]),
    showModal: options => events.push(['modal', options]),
    showLoading: options => events.push(['loading', options]),
    hideLoading: () => events.push(['hideLoading']),
    reLaunch: options => events.push(['reLaunch', options]),
    navigateTo: options => events.push(['navigateTo', options]),
    navigateBack: options => events.push(['navigateBack', options]),
    switchTab: options => events.push(['switchTab', options]),
    login: options => options.success({ code: 'wx-test-code' })
  }
  const lifecycleVue = {
    ...Vue,
    onMounted: callback => hooks.mounted.push(callback),
    onBeforeUnmount: callback => hooks.unmounted.push(callback),
    onActivated: callback => hooks.activated.push(callback),
    onDeactivated: callback => hooks.deactivated.push(callback)
  }
  const uniApp = {
    onLoad: callback => hooks.load.push(callback),
    onShow: callback => hooks.show.push(callback),
    onHide: callback => hooks.hide.push(callback),
    onLaunch: callback => hooks.mounted.push(callback)
  }
  const windowListeners = new Map()
  const window = browser ? {
    location: { hostname: 'workorder.example.test', hash: '#/pages/bind/index' },
    addEventListener(type, callback) {
      if (!windowListeners.has(type)) windowListeners.set(type, new Set())
      windowListeners.get(type).add(callback)
    },
    removeEventListener(type, callback) { windowListeners.get(type)?.delete(callback) }
  } : undefined
  const cache = new Map()
  function load(relativePath) {
    const filename = path.resolve(root, relativePath)
    if (services && filename === path.join(root, 'src/services/api.ts')) return services
    if (mocks[path.relative(root, filename).replaceAll('\\', '/')]) return mocks[path.relative(root, filename).replaceAll('\\', '/')]
    if (cache.has(filename)) return cache.get(filename).exports
    let source = readFileSync(filename, 'utf8')
    if (filename.endsWith('.vue')) {
      source = compileScript(parse(source, { filename }).descriptor, { id: filename }).content
    }
    source = source.replaceAll('import.meta.env', '__env')
    const output = ts.transpileModule(source, {
      compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, esModuleInterop: true }
    }).outputText
    const module = { exports: {} }
    cache.set(filename, module)
    const localRequire = specifier => {
      if (specifier.endsWith('.css')) return {}
      if (specifier === 'vue') return lifecycleVue
      if (specifier === '@dcloudio/uni-app') return uniApp
      if (specifier === '@dcloudio/uni-h5' && uniH5) return uniH5
      if (specifier.startsWith('.')) {
        const resolved = path.resolve(path.dirname(filename), specifier)
        return load(path.extname(resolved) ? resolved : `${resolved}.ts`)
      }
      return require(specifier)
    }
    vm.runInNewContext(output, {
      module, exports: module.exports, require: localRequire,
      uni, window, console, Error, URL: urlApi, URLSearchParams: browser ? URLSearchParams : undefined,
      getCurrentPages: () => currentPages, __env: { VITE_WECHAT_MOCK_ENABLED: 'false' },
      Date: class extends Date {
        constructor(...args) { super(...(args.length ? args : [now()])) }
        static now() { return now() }
      },
      setTimeout: callback => { timers.set(++timerId, callback); return timerId },
      clearTimeout: id => timers.delete(id)
    }, { filename })
    return module.exports
  }
  return {
    storage, uni, requests, uploads, downloads, choices, scans, events, hooks, timers, window, load,
    setCurrentPages(pages) { currentPages.splice(0, currentPages.length, ...pages) },
    hashRoute(hash) {
      window.location.hash = hash
      for (const callback of windowListeners.get('hashchange') ?? []) callback()
    },
    async page(filename, options = {}, mount = true, props = {}) {
      const component = load(filename).default
      const bindings = component.setup(props, { expose() {}, emit: (...args) => events.push(['emit', ...args]) })
      hooks.load.forEach(callback => callback(options))
      if (mount) await Promise.all(hooks.mounted.map(callback => callback()))
      return bindings
    },
    async mount() { await Promise.all(hooks.mounted.map(callback => callback())) },
    unmount() { hooks.unmounted.forEach(callback => callback()) },
    flushTimers() {
      const pending = [...timers.values()]
      timers.clear()
      pending.forEach(callback => callback())
    }
  }
}

test('work-order browse API requests just the specified page and safely carries literal filters', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const response = api.fetchWorkOrderPage({ page: 1, size: 20, keyword: '零件 & _%', status: 'PAUSED' })
  const query = new URL(h.requests[0].url, 'https://fixture.invalid').searchParams
  assert.deepEqual(Object.fromEntries(query), { page: '1', size: '20', keyword: '零件 & _%', status: 'PAUSED' })
  h.requests[0].success({ statusCode: 200, data: { items: [{ id: 1 }], total: 8000 } })
  assert.equal((await response).total, 8000)
  assert.equal(h.requests.length, 1)
})

test('mobile work-order page keeps results when loading more fails and retries that same page once', async () => {
  const calls = []
  const append = deferred()
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchWorkOrderPage: filters => {
      calls.push(filters)
      if (filters.page === 1) return Promise.resolve({ items: Array.from({ length: 20 }, (_, index) => ({ id: index + 1 })), total: 22 })
      if (calls.length === 2) return append.promise
      return Promise.resolve({ items: [{ id: 20 }, { id: 22 }], total: 22 })
    }
  } })
  const page = await h.page('src/pages/work-orders/index.vue')
  assert.equal(calls.length, 1)
  assert.equal(page.orders.value.length, 20)
  assert.equal(page.hasMore.value, true)
  const loading = page.loadMore()
  page.loadMore()
  assert.equal(calls.length, 2)
  append.reject(new Error('网络超时'))
  await loading
  assert.equal(page.orders.value.length, 20)
  assert.equal(page.loadError.value, '网络超时')
  await page.retryPage()
  assert.deepEqual(calls.map(call => call.page), [1, 2, 2])
  assert.equal(page.orders.value.length, 21)
  assert.equal(page.orders.value[20].id, 22)
  assert.equal(page.hasMore.value, false)
})

test('mobile search and status changes replace in-flight results without displaying a stale page', async () => {
  const calls = []
  const pending = [deferred(), deferred(), deferred()]
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchWorkOrderPage: filters => {
      calls.push(filters)
      return pending[calls.length - 1].promise
    }
  } })
  const page = await h.page('src/pages/work-orders/index.vue', {}, false)
  const old = page.loadOrders()
  page.keyword.value = '  PART  '
  const searching = page.doSearch()
  const filtered = page.selectTab('EXECUTING')
  assert.equal(calls.length, 3)
  assert.equal(calls[2].keyword, 'PART')
  assert.equal(calls[2].status, 'EXECUTING')
  pending[2].resolve({ items: [{ id: 8001 }], total: 1 })
  await filtered
  pending[1].reject(new Error('过时请求失败'))
  pending[0].resolve({ items: [{ id: 1 }], total: 10000 })
  await Promise.all([old, searching])
  assert.equal(page.orders.value[0].id, 8001)
  assert.equal(page.orderTotal.value, 1)
  assert.equal(page.loadError.value, '')
})

test('a cached H5 work-order list loads after password login returns to its deep link', async () => {
  let calls = 0
  let authenticated = false
  const h = harness({ browser: true, services: {
    ensureLogin: () => authenticated,
    fetchWorkOrderPage: () => { calls++; return Promise.resolve({ items: [], total: 0 }) }
  } })
  await h.page('src/pages/work-orders/index.vue')
  assert.equal(calls, 0)
  authenticated = true
  h.hashRoute('#/pages/bind/index?redirect=/pages/work-orders/index')
  assert.equal(calls, 0)
  h.hashRoute('#/pages/work-orders/index?from=login')
  assert.equal(calls, 1)
  h.unmount()
  h.hashRoute('#/pages/work-orders/index')
  assert.equal(calls, 1)
})

test('password login and WeChat binding 401 do not invalidate a different existing session', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  for (const login of [
    () => api.passwordLogin({ factoryCode: 'HHGD', username: 'worker', password: 'wrong' }),
    () => api.bindAccount({ openidTicket: 'ticket', factoryCode: 'HHGD', username: 'worker', password: 'wrong' })
  ]) {
    const request = login()
    const pending = h.requests.at(-1)
    assert.equal(pending.header.Authorization, undefined)
    pending.success({ statusCode: 401, data: { message: '密码错误' } })
    await assert.rejects(request, /密码错误/)
  }
  assert.equal(h.storage.get('token'), 'old-token')
  assert.equal(h.timers.size, 0)
})

test('H5 password login calls the mobile credential endpoint, not the management login', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const pending = api.passwordLogin({ factoryCode: 'HHGD', username: 'worker', password: 'test-only' })
  assert.equal(h.requests[0].url, '/api/mobile/auth/password-login')
  assert.equal(h.requests[0].header.Authorization, undefined)
  h.requests[0].success({ statusCode: 200, data: { token: 'worker-token', user: defaultUser } })
  assert.equal((await pending).token, 'worker-token')
})

test('simultaneous 401 responses redirect once; a later login cancels the redirect', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const a = api.request('/mobile/work-orders')
  const b = api.request('/mobile/reports')
  const rejected = Promise.allSettled([a, b])
  h.requests.forEach(request => request.success({ statusCode: 401, data: {} }))
  await rejected
  assert.equal(h.storage.get('token'), undefined)
  assert.equal(h.timers.size, 1)
  assert.equal(h.events.filter(([event]) => event === 'toast').length, 1)
  api.saveSession({ token: 'new-token', user: defaultUser })
  h.flushTimers()
  assert.equal(h.events.filter(([event]) => event === 'reLaunch').length, 0)
})

test('late failures and successes from an old session cannot replace a new session', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const failed = api.request('/mobile/work-orders')
  const succeeded = api.request('/mobile/reports')
  const rejected = Promise.allSettled([failed, succeeded])
  api.saveSession({ token: 'new-token', user: { ...defaultUser, id: 8 } })
  h.requests[0].success({ statusCode: 401, data: {} })
  h.requests[1].success({ statusCode: 200, data: { items: ['old-private-record'] } })
  assert.ok((await rejected).every(result => result.status === 'rejected'))
  assert.equal(h.storage.get('token'), 'new-token')
  assert.equal(h.timers.size, 0)
})

test('upload errors preserve backend messages and expire authentication consistently', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const upload = api.uploadReportImage('/tmp/test.png')
  assert.equal(h.uploads[0].timeout, 30000)
  h.uploads[0].success({ statusCode: 401, data: JSON.stringify({ message: '登录已过期' }) })
  await assert.rejects(upload, /登录已过期/)
  assert.equal(h.storage.get('token'), undefined)
  assert.equal(h.timers.size, 1)
})

test('restored attachment download uses the authenticated API, not an unauthenticated image URL', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const download = api.downloadReportImage(9)
  assert.equal(h.downloads[0].url, '/api/mobile/reports/images/9/file')
  assert.equal(h.downloads[0].header.Authorization, 'Bearer old-token')
  h.downloads[0].success({ statusCode: 200, tempFilePath: 'wxfile://downloaded-image' })
  assert.equal(await download, 'wxfile://downloaded-image')
})

test('Mini Program report queries do not require browser URLSearchParams and include pages after 500', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  h.uni.request = options => {
    h.requests.push(options)
    const query = new URL(options.url, 'https://test.invalid').searchParams
    const page = Number(query.get('page'))
    const size = Number(query.get('size'))
    const count = Math.min(size, 550 - (page - 1) * size)
    options.success({ statusCode: 200, data: { items: Array.from({ length: count }, (_, i) => ({ id: (page - 1) * size + i + 1 })), total: 550 } })
  }
  const result = await api.fetchReports('', 'today')
  assert.equal(result.items.length, 550)
  assert.equal(result.total, 550)
  assert.equal(result.truncated, false)
  assert.equal(h.requests.length, 6)
  assert.match(h.requests[0].url, /period=today/)
})

test('large lists have a bounded page count and explicitly report truncation', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  h.uni.request = options => {
    h.requests.push(options)
    const page = Number(new URL(options.url, 'https://test.invalid').searchParams.get('page'))
    options.success({ statusCode: 200, data: { items: Array.from({ length: 100 }, (_, i) => ({ id: (page - 1) * 100 + i })), total: 999999 } })
  }
  const result = await api.fetchWorkOrders()
  assert.equal(h.requests.length, 50)
  assert.equal(result.items.length, 5000)
  assert.equal(result.truncated, true)
  assert.equal(result.total, 999999)
})

test('login redirects preserve inner query escaping and use switchTab for tab pages', () => {
  const h = harness()
  const nav = h.load('src/utils/navigation.ts')
  const route = '/pages/report-form/index?processName=A%26B%25&orderId=9'
  assert.equal(nav.loginRedirect(encodeURIComponent(`#${route}`)), route)
  assert.equal(nav.loginRedirect(route), route)
  assert.equal(nav.loginRedirect('https://untrusted.example.test'), '')
  assert.equal(nav.loginRedirect('/pages/bind/index'), '')
  assert.doesNotThrow(() => nav.loginRedirect('%E0%A4'))
  nav.goAfterLogin('/pages/report-list/index')
  assert.equal(h.events.at(-1)[0], 'switchTab')
  nav.goAfterLogin(route)
  assert.equal(h.events.at(-1)[0], 'reLaunch')
})

test('H5 internal navigation uses uni router APIs for lifecycle and preserves encoded non-tab queries', async () => {
  const h = harness({ browser: true })
  const nav = h.load('src/utils/navigation.ts')
  const originalHash = h.window.location.hash
  nav.goAfterLogin('/pages/board/index')
  assert.equal(h.events.at(-1)[0], 'switchTab')
  assert.equal(h.events.at(-1)[1].url, '/pages/board/index')
  const query = '/pages/report-form/index?processName=A%26B%25&orderId=42'
  nav.navigatePage(query)
  assert.equal(h.events.at(-1)[0], 'navigateTo')
  assert.equal(h.events.at(-1)[1].url, query)
  nav.navigatePage(query, true)
  assert.equal(h.events.at(-1)[0], 'reLaunch')
  const tabBar = await h.page('src/components/MobileTabBar.vue', {}, false)
  tabBar.open('/pages/tasks/index')
  assert.equal(h.events.at(-1)[0], 'switchTab')
  assert.equal(h.window.location.hash, originalHash)
})

test('duplicate pre-ready login navigation queues one transition and accepts a later completed navigation', () => {
  const h = harness({ browser: true })
  const nav = h.load('src/utils/navigation.ts')
  nav.navigatePage('/pages/bind/index?redirect=%2Fpages%2Fboard%2Findex', true)
  nav.navigatePage('/pages/bind/index?redirect=%2Fpages%2Fboard%2Findex', true)
  assert.equal(h.events.filter(([kind]) => kind === 'reLaunch').length, 1)
  h.events[0][1].complete()
  nav.navigatePage('/pages/bind/index?redirect=%2Fpages%2Fboard%2Findex', true)
  assert.equal(h.events.filter(([kind]) => kind === 'reLaunch').length, 2)
})

test('H5 teleported tab bar is removed when its cached page deactivates and restored only on activation', async () => {
  const h = harness({ browser: true })
  h.hashRoute('#/pages/board/index')
  const bar = await h.page('src/components/MobileTabBar.vue', {}, false, { current: 'board' })
  assert.equal(bar.visible.value, true)
  h.hooks.deactivated.forEach(callback => callback())
  assert.equal(bar.visible.value, false)
  h.hooks.activated.forEach(callback => callback())
  assert.equal(bar.visible.value, true)
  h.hashRoute('#/pages/tasks/index')
  h.hooks.mounted.forEach(callback => callback())
  assert.equal(bar.visible.value, false)
  h.hooks.activated.forEach(callback => callback())
  assert.equal(bar.visible.value, false)
})

test('manual H5 route registration wraps each view as a real uni page with query and lifecycle initialization', async () => {
  const initialized = []
  const PageComponent = { name: 'UniPageRuntime' }
  const query = { id: '42', code: 'A&B%' }
  const app = { $route: { query } }
  const h = harness({ uniH5: {
    setupPage: component => { initialized.push(component); return component },
    PageComponent,
    getApp: () => app
  } })
  const { routes } = h.load('src/h5-routes.ts')
  const detail = routes.find(route => route.path === '/pages/work-order-detail/index')
  const render = detail.component.setup()
  const pageWrapper = render()
  assert.equal(pageWrapper.type, PageComponent)
  const pageView = pageWrapper.children.page()[0]
  assert.equal(pageView.props.__pageQuery.id, '42')
  assert.equal(pageView.props.__pageQuery.code, 'A&B%')
  assert.deepEqual(Object.keys(pageView.props).sort(), ['__pageQuery', 'ref'])
  app.$route.query = { id: '84', code: '新的工单' }
  const nextView = render().children.page()[0]
  assert.equal(nextView.props.__pageQuery.id, '84')
  assert.equal(nextView.props.__pageQuery.code, '新的工单')
  assert.equal(typeof detail.loader, 'function')
  await detail.loader()
  assert.equal(initialized.length, 1)
  assert.equal(routes.find(route => route.path === '/pages/board/index').meta.isTabBar, true)
})

test('report values reject negative/fractional/overflow quantities and reversed/invalid dates', () => {
  const { validateReportValues, updateDateTime } = harness().load('src/utils/report.ts')
  const form = { reportQuantity: '10', goodQuantity: '8', defectQuantity: '2', startTime: '2026-09-11 08:30:00', endTime: '2026-09-11 09:30:00' }
  assert.equal(validateReportValues(form, '1', '0'), '')
  for (const changes of [
    { goodQuantity: '-1', defectQuantity: '11' },
    { reportQuantity: '1.5', goodQuantity: '1.5', defectQuantity: '0' },
    { reportQuantity: '2147483648', goodQuantity: '2147483648', defectQuantity: '0' },
    { endTime: '2026-09-11 08:29:00' }, { startTime: '2026-02-31 08:30:00' }
  ]) assert.notEqual(validateReportValues({ ...form, ...changes }, '1', '0'), '')
  assert.notEqual(validateReportValues(form, '-1', '0'), '')
  assert.notEqual(validateReportValues(form, '1', '60'), '')
  assert.equal(updateDateTime(form.startTime, 'date', '2026-09-12'), '2026-09-12 08:30:00')
  assert.equal(updateDateTime(form.startTime, 'time', '10:15'), '2026-09-11 10:15:00')
})

test('H5 bootstrap uses an external base-aware script compatible with production CSP', () => {
  const entry = readFileSync(path.join(root, 'index.html'), 'utf8')
  assert.match(entry, /src="%BASE_URL%uni-bootstrap\.js"/)
  assert.doesNotMatch(entry, /<script\s*>/)
  const globals = {}
  vm.runInNewContext(readFileSync(path.join(root, 'public/uni-bootstrap.js'), 'utf8'), globals)
  assert.equal(globals.__UNI_FEATURE_PAGES__, true)
  assert.equal(globals.__UNI_FEATURE_ROUTER_MODE__, 'hash')
  assert.equal(globals.__uniConfig.router.base, '/')
})

test('security overrides retain the fixed uni/Vue toolchain and compatible i18n/Babel APIs', () => {
  for (const name of ['@dcloudio/uni-app', '@dcloudio/uni-components', '@dcloudio/uni-mp-weixin', '@dcloudio/vite-plugin-uni']) {
    assert.equal(require(`${name}/package.json`).version, '3.0.0-4020920240930001')
  }
  assert.equal(Vue.version, '3.4.34')
  const i18n = require('@intlify/core-base')
  i18n.registerMessageCompiler(i18n.compileToFunction)
  const context = i18n.createCoreContext({ locale: 'zh', messages: { zh: { count: '数量 {value}' } } })
  assert.equal(i18n.translate(context, 'count', { value: 2 }), '数量 2')
  const babel = require('@babel/core')
  const result = babel.transformSync('const quantity: number = 1', { configFile: false, babelrc: false, plugins: ['@babel/plugin-transform-typescript'] })
  assert.match(result.code, /const quantity = 1/)
})

test('patched JPEG and ZIP dependencies preserve the compiler-facing buffer APIs', async () => {
  const jpeg = require('jpeg-js')
  const Jimp = require('jimp')
  const encoded = jpeg.encode({ data: Buffer.alloc(16, 255), width: 2, height: 2 }, 75).data
  const decoded = await Jimp.read(encoded)
  assert.equal(decoded.bitmap.width, 2)
  assert.equal(decoded.bitmap.height, 2)
  const AdmZip = require('adm-zip')
  const archive = new AdmZip()
  archive.addFile('test.txt', Buffer.from('compatibility'))
  assert.equal(new AdmZip(archive.toBuffer()).readAsText('test.txt'), 'compatibility')
})

test('task display permission excludes disabled tasks and accepts role/department/assignee rules', () => {
  const { canReportTask } = harness().load('src/utils/report-permission.ts')
  assert.equal(canReportTask({ enabled: false, reportPermission: 'ALL' }, defaultUser), false)
  assert.equal(canReportTask({ reportPermission: ' all ' }, defaultUser), true)
  assert.equal(canReportTask({ reportPermission: '装配' }, defaultUser), true)
  assert.equal(canReportTask({ reportPermission: 'PRODUCTION' }, defaultUser), true)
  assert.equal(canReportTask({ reportPermission: '管理员', assignee: 'worker' }, defaultUser), true)
  assert.equal(canReportTask({ reportPermission: '管理员' }, defaultUser), false)
  assert.equal(canReportTask({ reportPermission: 'ALL' }, undefined), false)
})

test('drafts are scoped by account, flush on leaving, and cannot revive after clear/session change', async () => {
  const h = harness({ storage: { 'draft': { note: 'legacy-other-user' }, 'draft:user:global:8': { note: 'other-user' } } })
  const form = Vue.reactive({ note: '' })
  const { usePageDraft } = h.load('src/composables/usePageDraft.ts')
  const draft = usePageDraft({ key: 'draft', source: () => ({ ...form }), restore: saved => Object.assign(form, saved) })
  await h.mount()
  assert.equal(form.note, '')
  form.note = 'new-unsaved-edit'
  h.hooks.hide.forEach(callback => callback())
  assert.equal(h.storage.get('draft:user:global:7').note, 'new-unsaved-edit')
  form.note = 'submitted-edit'
  draft.clearDraft()
  h.flushTimers()
  assert.equal(h.storage.has('draft:user:global:7'), false)
  form.note = 'old-session-edit'
  h.storage.set('token', 'different-session')
  h.unmount()
  assert.equal(h.storage.has('draft:user:global:7'), false)
})

test('an unauthenticated cached page neither restores nor saves a stale account draft, then rebinds after login', async () => {
  const h = harness({ storage: { token: '', 'draft:user:global:7': { note: '已登录账号的草稿' } } })
  const form = Vue.reactive({ note: '' })
  const { usePageDraft } = h.load('src/composables/usePageDraft.ts')
  usePageDraft({ key: 'draft', source: () => ({ ...form }), restore: saved => Object.assign(form, saved) })
  await h.mount()
  assert.equal(form.note, '')
  form.note = '未登录输入'
  h.flushTimers()
  h.hooks.hide.forEach(callback => callback())
  assert.equal(h.storage.get('draft:user:global:7').note, '已登录账号的草稿')
  h.storage.set('token', 'new-login')
  h.hooks.show.forEach(callback => callback())
  assert.equal(form.note, '已登录账号的草稿')
  form.note = '登录后编辑'
  h.flushTimers()
  assert.equal(h.storage.get('draft:user:global:7').note, '登录后编辑')
})

test('cached-page account switches discard pending old edits and restore only the selected account draft', async () => {
  const h = harness({ storage: { 'draft:user:global:7': { note: '甲的已保存草稿' } } })
  const form = Vue.reactive({ note: '' })
  const { usePageDraft } = h.load('src/composables/usePageDraft.ts')
  usePageDraft({ key: 'draft', source: () => ({ ...form }), restore: saved => Object.assign(form, saved) })
  await h.mount()
  form.note = '甲的待保存修改'
  h.storage.set('token', 'user-eight-token')
  h.storage.set('user', { id: 8, name: '乙' })
  h.hooks.show.forEach(callback => callback())
  h.flushTimers()
  assert.equal(form.note, '')
  assert.equal(h.storage.get('draft:user:global:7').note, '甲的已保存草稿')
  assert.equal(h.storage.has('draft:user:global:8'), false)
  form.note = '乙的新草稿'
  h.flushTimers()
  assert.equal(h.storage.get('draft:user:global:8').note, '乙的新草稿')
  h.storage.set('token', 'user-seven-token')
  h.storage.set('user', defaultUser)
  h.hooks.show.forEach(callback => callback())
  assert.equal(form.note, '甲的已保存草稿')
  assert.equal(h.storage.get('draft:user:global:8').note, '乙的新草稿')
})

test('draft writes and clears compare the account and factory as well as the session token', async () => {
  const h = harness({ storage: { user: { ...defaultUser, factoryId: 1 }, 'draft:user:1:7': { note: '工厂一草稿' } } })
  const form = Vue.reactive({ note: '' })
  const { usePageDraft } = h.load('src/composables/usePageDraft.ts')
  const draft = usePageDraft({ key: 'draft', source: () => ({ ...form }), restore: saved => Object.assign(form, saved) })
  await h.mount()
  form.note = '尚未保存'
  h.storage.set('user', { ...defaultUser, factoryId: 2 })
  h.flushTimers()
  draft.clearDraft()
  assert.equal(h.storage.get('draft:user:1:7').note, '工厂一草稿')
  assert.equal(h.storage.has('draft:user:2:7'), false)
  h.hooks.show.forEach(callback => callback())
  assert.equal(form.note, '')
  form.note = '工厂二草稿'
  h.hooks.hide.forEach(callback => callback())
  assert.equal(h.storage.get('draft:user:2:7').note, '工厂二草稿')
})

test('logout clears the visible cached form without overwriting its saved draft or creating anonymous data', async () => {
  const h = harness({ storage: { 'draft:user:global:7': { note: '保留的个人草稿' } } })
  const form = Vue.reactive({ note: '' })
  const { usePageDraft } = h.load('src/composables/usePageDraft.ts')
  usePageDraft({ key: 'draft', source: () => ({ ...form }), restore: saved => Object.assign(form, saved) })
  await h.mount()
  form.note = '退出前未保存'
  h.storage.delete('token')
  h.storage.delete('user')
  h.hooks.show.forEach(callback => callback())
  assert.equal(form.note, '')
  form.note = '匿名输入'
  h.flushTimers()
  h.unmount()
  assert.equal(h.storage.get('draft:user:global:7').note, '保留的个人草稿')
  assert.equal(h.storage.has('draft'), false)
})

test('device-scoped account-name drafts remain usable without login and do not reset on a token change', async () => {
  const h = harness({ storage: { token: '', account: { username: 'saved-name' } } })
  const form = Vue.reactive({ username: '' })
  const { usePageDraft } = h.load('src/composables/usePageDraft.ts')
  usePageDraft({ key: 'account', scope: 'device', source: () => ({ ...form }), restore: saved => Object.assign(form, saved) })
  await h.mount()
  assert.equal(form.username, 'saved-name')
  form.username = 'edited-name'
  h.storage.set('token', 'new-token')
  h.hooks.show.forEach(callback => callback())
  assert.equal(form.username, 'edited-name')
  h.flushTimers()
  assert.equal(h.storage.get('account').username, 'edited-name')
})

function reportServices(overrides = {}) {
  return {
    ensureLogin: () => true,
    fetchWorkOrder: async () => ({ id: 42, status: 'EXECUTING', plannedQuantity: 100, tasks: [{ sequenceNo: 1, processName: '实际工序', plannedQuantity: 100, enabled: true }] }),
    fetchWorkOrderReporters: async () => [defaultUser],
    createReport: async () => ({ id: 1 }),
    uploadReportImage: async () => ({ id: 3, accessPath: '/api/mobile/reports/images/3/file' }),
    downloadReportImage: async () => 'wxfile://restored',
    ...overrides
  }
}

function fillValidReport(page) {
  Object.assign(page.form, { reportQuantity: '10', goodQuantity: '10', defectQuantity: '0', startTime: '2026-09-11 08:00:00', endTime: '2026-09-11 09:00:00' })
}

test('report form uses server process/reporter data and ignores forged URL permission/name', async () => {
  let received
  const h = harness({ services: reportServices({ fetchWorkOrderReporters: async (...args) => { received = args; return [defaultUser] } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1', processName: 'URL伪造工序', reportPermission: 'unrelated' })
  assert.deepEqual(received, [42, 1])
  assert.equal(page.form.processName, '实际工序')
  assert.equal(page.selectedReporterId.value, 7)
  page.changeH5Time('startTime', { target: { value: '2026-09-11T10:15' } })
  assert.equal(page.form.startTime, '2026-09-11 10:15:00')
})

test('failed reporter lookup cannot silently fall back to an unverified current user', async () => {
  const h = harness({ services: reportServices({ fetchWorkOrderReporters: async () => { throw new Error('无权报工') } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  assert.equal(page.reporterList.value.length, 0)
  assert.equal(page.selectedReporterId.value, 0)
  assert.equal(page.reporterLoading.value, false)
})

test('rapid report submissions issue exactly one write and wait for an explicit records action', async () => {
  const write = deferred()
  let count = 0
  const h = harness({ services: reportServices({ createReport: () => { count += 1; return write.promise } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  const first = page.handleSubmit(false)
  await page.handleSubmit(false)
  await page.handleSubmit(true)
  assert.equal(count, 1)
  assert.equal(page.submitPhase.value, 'submitting')
  assert.equal(page.submitStatusLabel.value, '正在提交报工…')
  write.resolve({ id: 1 })
  await first
  assert.equal(page.submitPhase.value, 'success')
  assert.equal(page.submitStatusLabel.value, '提交成功')
  h.flushTimers()
  assert.ok(!h.events.some(([kind]) => kind === 'switchTab'))
  await page.handleSubmit(false)
  assert.equal(count, 1)
  assert.equal(page.loading.value, true)
  page.goToReportRecords(true)
  assert.equal(h.events.at(-1)[1].url, '/pages/report-list/index')
})

test('definite report rejection keeps the retry intention and draft; retry submits the same data once', async () => {
  const writes = []
  const h = harness({ services: reportServices({ createReport: async data => {
    writes.push(data)
    if (writes.length === 1) throw Object.assign(new Error('报工暂未通过校验'), { status: 422 })
    return { id: 19 }
  } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  page.form.remark = '失败后保留内容'
  await page.handleSubmit(true)
  assert.equal(page.submitPhase.value, 'failed')
  assert.equal(page.retryContinues.value, true)
  assert.equal(page.submitError.value, '报工暂未通过校验')
  assert.equal(page.form.remark, '失败后保留内容')
  assert.equal(page.submitDisabled.value, false)
  await page.handleSubmit(page.retryContinues.value)
  assert.equal(writes.length, 2)
  assert.deepEqual(writes[0], writes[1])
  assert.equal(page.submitPhase.value, 'success')
  assert.equal(page.completedSubmissionContinues.value, true)
  assert.equal(page.form.remark, '')
})

test('unconfirmed report delivery survives reopening and does not silently resubmit', async () => {
  const key = 'gongdan:draft:report:42:1:user:global:7'
  const order = { orderId: '42', processNo: '1' }
  let writes = 0
  const services = reportServices({ createReport: async () => {
    writes++
    if (writes === 1) throw new Error('连接已断开')
    return { id: 21 }
  } })
  const h = harness({ services })
  const page = await h.page('src/pages/report-form/index.vue', order)
  fillValidReport(page)
  page.form.remark = '检查已生成记录'
  await page.handleSubmit(false)
  assert.equal(page.submitPhase.value, 'uncertain')
  assert.equal(page.formLocked.value, true)
  assert.equal(h.storage.get(key).reportNeedsReview, true)
  page.goToReportRecords(false)
  assert.equal(h.events.at(-1)[1].url, '/pages/report-list/index')
  const review = h.load('src/utils/report-flow.ts').consumeReportFilterOnReturn()
  assert.equal(review.status, '')
  assert.equal(review.review.workOrderId, 42)
  assert.equal(review.review.processName, '实际工序')
  assert.equal(review.review.quantity, 10)
  assert.ok(review.review.startedAt)

  const reopened = harness({ services, storage: { [key]: h.storage.get(key) } })
  const reopenedPage = await reopened.page('src/pages/report-form/index.vue', order)
  assert.equal(reopenedPage.submitPhase.value, 'uncertain')
  assert.equal(reopenedPage.form.remark, '检查已生成记录')
  await reopenedPage.handleSubmit(false)
  assert.equal(writes, 1)
  reopenedPage.confirmNotSubmitted()
  assert.equal(reopenedPage.submitPhase.value, 'failed')
  assert.equal(reopenedPage.submitDisabled.value, false)
  await reopenedPage.handleSubmit(false)
  assert.equal(writes, 2)
  assert.equal(reopenedPage.submitPhase.value, 'success')
  assert.equal(reopened.storage.has(key), false)
})

test('a malformed successful response is not treated as a committed report', async () => {
  const h = harness({ services: reportServices({ createReport: async () => ({}) }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  await page.handleSubmit(false)
  assert.equal(page.submitPhase.value, 'uncertain')
  assert.equal(page.form.reportQuantity, '10')
})

test('manually confirming an existing record resolves the blocked draft without writing again or claiming pending', async () => {
  const key = 'gongdan:draft:report:42:1:user:global:7'
  let writes = 0
  const h = harness({ services: reportServices({ createReport: async () => {
    writes++
    throw Object.assign(new Error('网关未返回报工结果'), { status: 502 })
  } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  await page.handleSubmit(false)
  assert.equal(h.storage.get(key).reportNeedsReview, true)
  page.confirmExistingReport()
  assert.equal(page.submitStatusLabel.value, '记录已核对')
  await page.handleSubmit(false)
  assert.equal(writes, 1)
  assert.equal(h.storage.has(key), false)
  page.goToReportRecords(true)
  assert.equal(h.load('src/utils/report-flow.ts').consumeReportFilterOnReturn().status, '')
})

test('records review opens all statuses for the original work order and allows clearing the scope', async () => {
  const reads = []
  const h = harness({ services: { ensureLogin: () => true, fetchReportPage: async query => {
    reads.push(query)
    return { items: [], total: 0 }
  } } })
  const page = await h.page('src/pages/report-list/index.vue', {}, false)
  page.statusFilter.value = 'REJECTED'
  page.period.value = 'lastMonth'
  h.load('src/utils/report-flow.ts').showReportsForReview({ workOrderId: 42, processName: '装配', quantity: 10, startedAt: '2026-11-27 09:33:11' })
  await page.loadOnReturn()
  assert.equal(reads[0].workOrderId, 42)
  assert.equal(reads[0].status, undefined)
  assert.equal(reads[0].period, '')
  assert.equal(page.reportReview.value.processName, '装配')
  await page.clearReportReview()
  assert.equal(reads[1].workOrderId, undefined)
})

test('report submission waits for image uploads and multiple picker clicks are serialized', async () => {
  const upload = deferred()
  let writes = 0
  const h = harness({ services: reportServices({ uploadReportImage: () => upload.promise, createReport: async () => { writes += 1; return { id: 1 } } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  page.chooseImage()
  page.chooseImage()
  assert.equal(h.choices.length, 1)
  const chosen = h.choices[0].success({ tempFilePaths: ['/tmp/selected.png'] })
  await page.handleSubmit(true)
  assert.equal(writes, 0)
  upload.resolve({ id: 3, accessPath: '/api/mobile/reports/images/3/file' })
  await chosen
  assert.equal(page.uploadedImages.value.length, 1)
  await page.handleSubmit(true)
  assert.equal(writes, 1)
  assert.equal(page.loading.value, false)
  assert.equal(page.form.reportQuantity, '')
})

test('production H5 login calls password auth directly and never fabricates a WeChat ticket', async () => {
  let wxCalls = 0, credentials
  const h = harness({ browser: true, services: {
    passwordLogin: async data => { credentials = data; return { token: 'new-token', user: defaultUser } },
    wechatLogin: async () => { wxCalls += 1; throw new Error('browser has no WeChat code') },
    bindAccount: async () => { throw new Error('H5 must not use binding') },
    saveSession: () => {}
  } })
  const page = await h.page('src/pages/bind/index.vue')
  assert.equal(wxCalls, 0)
  Object.assign(page.form, { factoryCode: ' HHGD ', username: ' worker ', password: ' secret ' })
  await page.handleBind()
  assert.equal(credentials.factoryCode, 'HHGD')
  assert.equal(credentials.username, 'worker')
  assert.equal(credentials.password, ' secret ')
  assert.equal(page.form.password, '')
})

test('report history applies status and period on the server and discards stale responses', async () => {
  const first = deferred(), second = deferred()
  const calls = []
  const h = harness({ services: { fetchReportPage: options => { calls.push(options); return calls.length === 1 ? first.promise : second.promise }, ensureLogin: () => true } })
  const page = await h.page('src/pages/report-list/index.vue', {}, false)
  page.statusFilter.value = 'PENDING'
  page.period.value = 'today'
  const a = page.loadReports()
  page.period.value = 'lastMonth'
  const b = page.loadReports()
  second.resolve({ items: [{ id: 3, status: 'PENDING' }], total: 1 })
  await b
  first.resolve({ items: [{ id: 1, status: 'REJECTED' }], total: 1 })
  await a
  assert.ok(calls.every(options => options.status === 'PENDING' && options.includeSubmitted === true && options.page === 1 && options.size === 20))
  assert.deepEqual(calls.map(options => options.period), ['today', 'lastMonth'])
  assert.equal(page.filteredReports.value.length, 1)
  assert.equal(page.reports.value[0].id, 3)
})

test('rejected records can open a fresh report for their original order and process', async () => {
  const h = harness()
  const page = await h.page('src/pages/report-list/index.vue', {}, false)
  const rejected = { id: 1, status: 'REJECTED', workOrderId: 42, processSequenceNo: 3, processName: '装配 & 检验', reporterId: 9 }
  page.reports.value = [rejected, { ...rejected, id: 2, status: 'APPROVED' }]
  page.statusFilter.value = 'REJECTED'
  assert.equal(page.filteredReports.value.length, 1)
  page.retryReport(page.reports.value[1])
  assert.equal(h.events.length, 0)
  page.retryReport(rejected)
  const [event, { url }] = h.events[0]
  assert.equal(event, 'navigateTo')
  const target = new URL(url, 'https://test.invalid')
  assert.equal(target.pathname, '/pages/report-form/index')
  assert.equal(target.searchParams.get('orderId'), '42')
  assert.equal(target.searchParams.get('processNo'), '3')
  assert.equal(target.searchParams.get('processName'), rejected.processName)
  assert.equal(target.searchParams.get('reporterId'), '9')
  assert.equal(target.searchParams.get('correction'), '1')
})

test('a correction cannot be submitted against a replaced process or without permission', async () => {
  const h = harness({ services: reportServices() })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1', correction: '1', processName: '已替换的工序' })
  assert.match(page.loadError.value, /工序已变更/)
  await page.handleSubmit(false)
  page.chooseImage()
  assert.equal(h.choices.length, 0)

  const h2 = harness({ services: reportServices({ fetchWorkOrderReporters: async () => [{ ...defaultUser, id: 8 }] }) })
  const unauthorized = await h2.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  assert.match(unauthorized.loadError.value, /没有该工序的报工权限/)
})

test('work order history can be requested without downloading unrelated reports', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const result = api.fetchReports('', '', { workOrderId: 42, includeSubmitted: true })
  const request = h.requests[0]
  const query = new URL(request.url, 'https://test.invalid').searchParams
  assert.equal(query.get('workOrderId'), '42')
  assert.equal(query.get('includeSubmitted'), 'true')
  request.success({ statusCode: 200, data: { items: [], total: 0 } })
  await result
})

test('returning from a successful correction reveals pending reports even from the rejected tab', async () => {
  const h = harness({ services: { ensureLogin: () => true, fetchReportPage: async () => ({ items: [], total: 0 }) } })
  const page = await h.page('src/pages/report-list/index.vue', {}, false)
  const flow = h.load('src/utils/report-flow.ts')
  page.statusFilter.value = 'REJECTED'
  page.period.value = 'lastMonth'
  flow.showPendingReportsOnReturn()
  await page.loadOnReturn()
  assert.equal(page.statusFilter.value, 'PENDING')
  assert.equal(page.period.value, '')
  flow.showPendingReportsOnReturn()
  h.storage.set('token', 'different-session')
  assert.equal(flow.consumeReportFilterOnReturn(), undefined)
})

test('field event double clicks create one event and JSON null payloads remain renderable', async () => {
  let writes = 0
  const write = deferred()
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchFieldEvents: async () => ({ items: [], total: 0 }),
    createFieldEvent: () => { writes += 1; return write.promise }
  } })
  const page = await h.page('src/pages/field-events/index.vue')
  page.form.title = '测试现场事件'
  const first = page.submitEvent()
  await page.submitEvent()
  assert.equal(writes, 1)
  write.resolve({ id: 1 })
  await first
  assert.equal(Object.keys(page.payloadOf({ payloadJson: 'null' })).length, 0)
  assert.equal(Object.keys(page.payloadOf({ payloadJson: '[]' })).length, 0)
})

test('a field event submission refresh supersedes a GET started before the write', async () => {
  const initial = deferred()
  let reads = 0
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchFieldEvents: () => ++reads === 1 ? initial.promise : Promise.resolve({ items: [{ id: 2, title: '新事件' }], total: 1 }),
    createFieldEvent: async () => ({ id: 2 })
  } })
  const page = await h.page('src/pages/field-events/index.vue', {}, false)
  const initialLoad = h.mount()
  page.form.title = '新事件'
  await page.submitEvent()
  assert.equal(reads, 2)
  assert.equal(page.events.value[0].id, 2)
  assert.equal(page.loading.value, false)
  initial.resolve({ items: [{ id: 1, title: '旧事件' }], total: 1 })
  await initialLoad
  assert.equal(page.events.value[0].id, 2)
})

test('an obsolete field event failure cannot replace the successful refresh with an error', async () => {
  const initial = deferred()
  let reads = 0
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchFieldEvents: () => ++reads === 1 ? initial.promise : Promise.resolve({ items: [{ id: 2 }], total: 1 }),
    createFieldEvent: async () => ({ id: 2 })
  } })
  const page = await h.page('src/pages/field-events/index.vue', {}, false)
  const initialLoad = h.mount()
  page.form.title = '新事件'
  await page.submitEvent()
  initial.reject(new Error('过时请求失败'))
  await initialLoad
  assert.equal(page.errorMessage.value, '')
  assert.equal(page.events.value[0].id, 2)
  assert.equal(h.events.some(([type, event]) => type === 'toast' && event.title === '过时请求失败'), false)
})

test('field events load when a cached native page becomes visible after login', async () => {
  let loggedIn = false
  let reads = 0
  const h = harness({ services: {
    ensureLogin: () => loggedIn,
    fetchFieldEvents: async () => { reads += 1; return { items: [{ id: 7 }], total: 1 } }
  } })
  const page = await h.page('src/pages/field-events/index.vue')
  assert.equal(reads, 0)
  loggedIn = true
  await Promise.all(h.hooks.show.map(callback => callback()))
  assert.equal(reads, 1)
  assert.equal(page.events.value[0].id, 7)
})

test('field event list failures expose a retry without deleting the successful records', async () => {
  let fail = false
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchFieldEvents: async () => {
      if (fail) throw new Error('临时断网')
      return { items: [{ id: 7 }], total: 1 }
    }
  } })
  const page = await h.page('src/pages/field-events/index.vue')
  fail = true
  await page.loadEvents()
  assert.equal(page.errorMessage.value, '临时断网')
  assert.equal(page.events.value[0].id, 7)
  fail = false
  await page.loadEvents()
  assert.equal(page.errorMessage.value, '')
})

test('board refresh requests the new local day and keeps server aggregate status counts', async () => {
  let clock = new Date(2026, 10, 24, 23, 59).getTime()
  const days = []
  const h = harness({ now: () => clock, services: {
    ensureLogin: () => true,
    fetchDashboard: async day => {
      days.push(day)
      return dashboardData({ activeOrderCount: 1, todayReportCount: day === '2026-11-24' ? 1 : 2 })
    }
  } })
  const page = await h.page('src/pages/board/index.vue')
  assert.equal(page.todayReportCount.value, 1)
  assert.equal(page.activeOrderCount.value, 1)
  assert.equal(page.statusLabel('CANCELLED'), '已取消')
  clock = new Date(2026, 10, 25, 0, 1).getTime()
  await page.loadBoard()
  assert.equal(page.todayKey.value, '2026-11-25')
  assert.equal(page.todayReportCount.value, 2)
  assert.deepEqual(days, ['2026-11-24', '2026-11-25'])
})

test('board filters only recent rows, persists the selection, and keeps the aggregate available on same-day refresh failure', async () => {
  let fail = false, reads = 0
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchDashboard: async () => {
      reads++
      if (fail) throw new Error('暂不可用')
      return dashboardData({ workOrderTotal: 50, activeOrderCount: 43, recentOrders: [
        taskOrder(3), { ...taskOrder(2), status: 'IN_PROGRESS' }, { ...taskOrder(1), status: 'PAUSED' }
      ] })
    }
  } })
  const page = await h.page('src/pages/board/index.vue')
  assert.equal(page.visibleOrderRows.value.length, 3)
  assert.equal(page.filterChanged.value, false)
  page.setOrderFilter('executing')
  assert.deepEqual(Array.from(page.visibleOrderRows.value, item => item.id), [3, 2])
  assert.equal(page.filterChanged.value, true)
  page.setOrderFilter('other')
  assert.deepEqual(Array.from(page.visibleOrderRows.value, item => item.id), [1])
  assert.equal(reads, 1)
  assert.equal(page.activeOrderCount.value, 43)
  assert.equal(page.dashboard.value.workOrderTotal, 50)
  fail = true
  await page.loadBoard()
  assert.equal(page.errorMessage.value, '暂不可用')
  assert.equal(page.orderFilter.value, 'other')
  assert.deepEqual(Array.from(page.visibleOrderRows.value, item => item.id), [1])
  h.hooks.hide.forEach(callback => callback())
  assert.equal(h.storage.get('gongdan:draft:board-filter:user:global:7').filter, 'other')
  fail = false
  await Promise.all(h.hooks.show.map(callback => callback()))
  assert.equal(page.orderFilter.value, 'other')
  assert.equal(page.errorMessage.value, '')
})

test('home refresh advances its day and reads the current logged-in account', async () => {
  let clock = new Date(2026, 10, 24, 23, 59).getTime()
  const h = harness({ now: () => clock, services: {
    ensureLogin: () => true,
    fetchDashboard: async day => dashboardData({ todayGood: day === '2026-11-24' ? 3 : 7 })
  } })
  const page = await h.page('src/pages/home/index.vue')
  assert.equal(page.todayGood.value, 3)
  clock = new Date(2026, 10, 25, 0, 1).getTime()
  h.storage.set('user', { id: 8, name: '工人乙' })
  await Promise.all(h.hooks.show.map(callback => callback()))
  assert.equal(page.todayGood.value, 7)
  assert.equal(page.currentDate.value, '2026年11月25日')
  assert.equal(page.userInfo.value.name, '工人乙')
})

test('home and board retain an explicit failed-load state until a successful retry', async () => {
  for (const [filename, method, errorField] of [
    ['home', 'loadStats', 'statsError'], ['board', 'loadBoard', 'errorMessage']
  ]) {
    let fail = true
    const h = harness({ services: {
      ensureLogin: () => true,
      fetchDashboard: async () => {
        if (fail) throw new Error('统计暂时不可用')
        return dashboardData()
      }
    } })
    const page = await h.page(`src/pages/${filename}/index.vue`)
    assert.equal(page[errorField].value, '统计暂时不可用')
    fail = false
    await page[method]()
    assert.equal(page[errorField].value, '')
  }
})

test('home and board consume full large aggregates with one request instead of downloading thousands of rows', async () => {
  const aggregate = dashboardData({
    workOrderTotal: 20000, totalPlanned: 200000, totalGood: 170000, activeOrderCount: 8000,
    reportTotal: 18000, pendingReportCount: 13000, todayReportCount: 7300, totalDefect: 4000,
    todayGood: 9001, todayDefect: 999, todayPending: 12345,
    recentOrders: [taskOrder(1)], rankings: [{ name: '装配', value: 95000 }, { name: '包装', value: 70000 }]
  })
  for (const filename of ['home', 'board']) {
    const h = harness({ now: () => new Date(2026, 10, 24, 10).getTime() })
    const page = await h.page(`src/pages/${filename}/index.vue`, {}, false)
    const mounting = h.mount()
    assert.equal(h.requests.length, 1)
    const endpoint = new URL(h.requests[0].url, 'https://fixture.invalid')
    assert.equal(endpoint.pathname, '/api/mobile/dashboard')
    assert.equal(endpoint.searchParams.get('day'), '2026-11-24')
    h.requests[0].success({ statusCode: 200, data: aggregate })
    await mounting
    assert.equal(h.requests.length, 1)
    if (filename === 'home') {
      assert.equal(page.todayGood.value, 9001)
      assert.equal(page.todayPending.value, 12345)
      assert.equal(page.todayPassRate.value, 90)
    } else {
      assert.equal(page.totalPlanned.value, 200000)
      assert.equal(page.totalGood.value, 170000)
      assert.equal(page.achievementRate.value, 85)
      assert.equal(page.activeOrderCount.value, 8000)
      assert.equal(page.todayReportCount.value, 7300)
      assert.equal(page.pendingReportCount.value, 13000)
      assert.equal(page.totalDefect.value, 4000)
      assert.equal(page.orderRows.value.length, 1)
      assert.equal(page.rankings.value[0].value, 95000)
    }
  }
})

test('dashboard API rejects malformed statistics rather than treating missing metrics as zero', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const response = api.fetchDashboard('2026-11-24')
  h.requests[0].success({ statusCode: 200, data: { items: [], total: 0 } })
  await assert.rejects(response, /统计数据返回格式异常/)
})

test('dashboard pages reject incomplete or unsafe aggregates without displaying them as genuine zero statistics', async () => {
  for (const [data, message] of [[{}, /返回格式异常/], [dashboardData({ totalGood: Number.MAX_SAFE_INTEGER + 1 }), /超出安全范围/]]) {
    const h = harness({ services: { ensureLogin: () => true, fetchDashboard: async () => data } })
    const page = await h.page('src/pages/board/index.vue')
    assert.equal(page.dashboard.value, null)
    assert.match(page.errorMessage.value, message)
    assert.equal(page.loading.value, false)
  }
})

test('a new day supersedes the prior-day in-flight dashboard request while same-day lifecycle calls stay deduplicated', async () => {
  let clock = new Date(2026, 10, 24, 23, 59).getTime()
  const previousDay = deferred()
  const days = []
  const h = harness({ now: () => clock, services: {
    ensureLogin: () => true,
    fetchDashboard: day => {
      days.push(day)
      return day === '2026-11-24' ? previousDay.promise : Promise.resolve(dashboardData({ todayReportCount: 2 }))
    }
  } })
  const page = await h.page('src/pages/board/index.vue', {}, false)
  const oldLoad = h.mount()
  await page.loadBoard()
  assert.equal(days.length, 1)
  clock = new Date(2026, 10, 25, 0, 1).getTime()
  await page.loadBoard()
  previousDay.resolve(dashboardData({ todayReportCount: 1 }))
  await oldLoad
  assert.deepEqual(days, ['2026-11-24', '2026-11-25'])
  assert.equal(page.todayReportCount.value, 2)
  assert.equal(page.todayKey.value, '2026-11-25')
})

test('new-session dashboard loads can supersede in-flight old-session successes and failures', async () => {
  for (const lateStatus of [200, 500]) {
    const h = harness()
    const page = await h.page('src/pages/board/index.vue', {}, false)
    const oldLoad = h.mount()
    h.storage.set('token', 'new-token')
    h.storage.set('user', { id: 8, name: '乙' })
    const newLoad = page.loadBoard()
    assert.equal(h.requests.length, 2)
    h.requests[1].success({ statusCode: 200, data: dashboardData({ todayReportCount: 7000 }) })
    await newLoad
    h.requests[0].success({ statusCode: lateStatus, data: lateStatus === 200 ? dashboardData({ todayReportCount: 1 }) : { message: '旧账号错误' } })
    await oldLoad
    assert.equal(page.todayReportCount.value, 7000)
    assert.equal(page.errorMessage.value, '')
    assert.equal(page.loading.value, false)
  }
})

test('returning to a dashboard supersedes the pre-hide request without accepting its stale snapshot', async () => {
  const h = harness()
  const page = await h.page('src/pages/home/index.vue', {}, false)
  const oldLoad = h.mount()
  h.hooks.hide.forEach(callback => callback())
  const newLoad = Promise.all(h.hooks.show.map(callback => callback()))
  assert.equal(h.requests.length, 2)
  h.requests[1].success({ statusCode: 200, data: dashboardData({ todayGood: 9000 }) })
  await newLoad
  h.requests[0].success({ statusCode: 200, data: dashboardData({ todayGood: 1 }) })
  await oldLoad
  assert.equal(page.todayGood.value, 9000)
})

test('task browsing API sends a single reportable-only page and literal search without a conflicting status', async () => {
  const h = harness()
  const api = h.load('src/services/api.ts')
  const response = api.fetchWorkOrderPage({ page: 2, size: 20, keyword: '后页 & _%', reportableOnly: true })
  const endpoint = new URL(h.requests[0].url, 'https://fixture.invalid')
  assert.equal(endpoint.searchParams.get('page'), '2')
  assert.equal(endpoint.searchParams.get('size'), '20')
  assert.equal(endpoint.searchParams.get('keyword'), '后页 & _%')
  assert.equal(endpoint.searchParams.get('reportableOnly'), 'true')
  assert.equal(endpoint.searchParams.has('status'), false)
  h.requests[0].success({ statusCode: 200, data: { items: [], total: 0 } })
  await response
})

test('tasks load only 20 orders initially and preserve records when the next page fails, then retry the same page once', async () => {
  const calls = []
  const failedPage = deferred()
  let failed = false
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchWorkOrderPage: filters => {
      calls.push(filters)
      if (filters.page === 2 && !failed) { failed = true; return failedPage.promise }
      const count = filters.page === 3 ? 2 : 20
      return Promise.resolve({ items: Array.from({ length: count }, (_, index) => taskOrder((filters.page - 1) * 20 + index + 1)), total: 42 })
    }
  } })
  const page = await h.page('src/pages/tasks/index.vue')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].size, 20)
  assert.equal(calls[0].reportableOnly, true)
  assert.equal(page.tasks.value.length, 20)
  assert.equal(page.orderTotal.value, 42)
  const load = page.loadMore()
  await page.loadMore()
  assert.equal(calls.length, 2)
  failedPage.reject(new Error('第二页断网'))
  await load
  assert.equal(page.tasks.value.length, 20)
  assert.equal(page.loadedOrders.value, 20)
  assert.equal(page.errorMessage.value, '第二页断网')
  await page.retryPage()
  assert.equal(calls[2].page, 2)
  assert.equal(page.tasks.value.length, 40)
  await page.loadMore()
  assert.equal(page.tasks.value.length, 42)
  assert.equal(page.hasMore.value, false)
})

test('an empty filtered task page is not treated as the end and task sequence numbers survive filtering', async () => {
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchWorkOrderPage: async ({ page }) => page === 1
      ? { items: Array.from({ length: 20 }, (_, index) => taskOrder(index + 1, [{ processName: '管理专用', enabled: true, reportPermission: 'ADMIN', plannedQuantity: 100 }])), total: 21 }
      : { items: [taskOrder(21, [{ processName: '禁用', enabled: false }, { processName: '可报工', enabled: true, reportPermission: 'ALL', plannedQuantity: 100 }])], total: 21 }
  } })
  const page = await h.page('src/pages/tasks/index.vue')
  assert.equal(page.tasks.value.length, 0)
  assert.equal(page.loadedOrders.value, 20)
  assert.equal(page.hasMore.value, true)
  await page.loadMore()
  assert.equal(page.tasks.value.length, 1)
  assert.equal(page.tasks.value[0].sequenceNo, 2)
  assert.equal(page.hasMore.value, false)
})

test('new task searches and sessions discard old pages without losing the latest results', async () => {
  const old = deferred()
  const calls = []
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchWorkOrderPage: filters => {
      calls.push(filters)
      return filters.keyword === '' ? old.promise : Promise.resolve({ items: [taskOrder(99)], total: 1 })
    }
  } })
  const page = await h.page('src/pages/tasks/index.vue', {}, false)
  const mounting = h.mount()
  page.keyword.value = '后页零件'
  h.storage.set('token', 'new-account-token')
  const latest = page.loadTasks()
  await latest
  old.resolve({ items: [taskOrder(1)], total: 8000 })
  await mounting
  assert.equal(calls.length, 2)
  assert.equal(calls[1].keyword, '后页零件')
  assert.equal(page.tasks.value[0].workOrderId, 99)
  assert.equal(page.orderTotal.value, 1)
  assert.equal(page.errorMessage.value, '')
})

test('task reporting permission checks cannot navigate under a session that changed during the lookup', async () => {
  const permission = deferred()
  const h = harness({ services: {
    ensureLogin: () => true,
    fetchWorkOrderPage: async () => ({ items: [taskOrder(1)], total: 1 }),
    fetchWorkOrderReporters: () => permission.promise
  } })
  const page = await h.page('src/pages/tasks/index.vue')
  const opening = page.handleTask(page.tasks.value[0])
  h.storage.set('token', 'new-token')
  h.storage.set('user', { id: 8 })
  permission.resolve([{ id: 8 }])
  await opening
  assert.equal(h.events.some(([type]) => type === 'navigateTo'), false)
  assert.equal(h.events.some(([type, event]) => type === 'toast' && /登录状态已变更/.test(event.title)), true)
})

function qrBitmap(text) {
  const QRCode = require('qrcode-terminal/vendor/QRCode')
  const qr = new QRCode(-1, 1)
  qr.addData(text); qr.make()
  const size = qr.getModuleCount(), scale = 6, border = 4
  const width = (size + border * 2) * scale
  const data = new Uint8ClampedArray(width * width * 4).fill(255)
  for (let row = 0; row < size; row++) for (let col = 0; col < size; col++) if (qr.isDark(row, col)) {
    for (let y = 0; y < scale; y++) for (let x = 0; x < scale; x++) {
      const offset = (((row + border) * scale + y) * width + (col + border) * scale + x) * 4
      data[offset] = data[offset + 1] = data[offset + 2] = 0
    }
  }
  return { width, height: width, data }
}

test('real QR pixels decode a work-order link locally, and blank or oversized images fail', async () => {
  const h = harness()
  const { decodeQrPixels } = h.load('src/utils/qr-image.ts')
  const text = 'https://fixture.invalid/mobile/#/pages/work-order-detail/index?id=42'
  assert.equal(await decodeQrPixels(qrBitmap(text)), text)
  await assert.rejects(decodeQrPixels({ width: 40, height: 40, data: new Uint8ClampedArray(40 * 40 * 4).fill(255) }), /未识别到二维码/)
  await assert.rejects(decodeQrPixels({ width: 2048, height: 2048, data: new Uint8ClampedArray(0) }), /尺寸/)
})

test('scan parsing accepts internal work orders but never opens a foreign route or unsafe ID', () => {
  const h = harness()
  const { scannedWorkOrderRoute } = h.load('src/utils/scan-target.ts')
  assert.equal(scannedWorkOrderRoute('42'), '/pages/work-order-detail/index?id=42')
  assert.equal(scannedWorkOrderRoute('QA-042'), '/pages/work-order-detail/index?code=QA-042')
  assert.equal(scannedWorkOrderRoute('00042', true), '/pages/work-order-detail/index?code=00042')
  assert.equal(scannedWorkOrderRoute('https://example.test/mobile/#/pages/work-order-detail/index?code=A%20%26%20B'), '/pages/work-order-detail/index?code=A%20%26%20B')
  for (const invalid of ['', '0', '9007199254740992', 'https://example.test/delete?id=42', 'javascript://open', '/pages/home/index?id=42', '#/pages/work-order-detail/index?code=%E0%A4']) assert.throws(() => scannedWorkOrderRoute(invalid))
})

test('H5 scan opens the real scan dialog and WeChat scan resolves into safe local navigation', async () => {
  const browser = harness({ browser: true })
  const home = await browser.page('src/pages/home/index.vue', {}, false)
  home.handleScan()
  assert.equal(home.showScan.value, true)
  assert.equal(browser.scans.length, 0)
  const mini = harness()
  const orders = await mini.page('src/pages/work-orders/index.vue', {}, false)
  orders.handleScan()
  mini.scans[0].success({ result: 'https://example.test/mobile/#/pages/work-order-detail/index?id=42' })
  assert.equal(mini.events.at(-1)[1].url, '/pages/work-order-detail/index?id=42')
  mini.scans[0].success({ result: 'https://example.test/not-a-work-order?id=42' })
  assert.equal(mini.events.at(-1)[0], 'toast')
})

test('logout has explicit action labels and removes the session before returning to manual login', async () => {
  const h = harness(), page = await h.page('src/pages/home/index.vue', {}, false)
  page.handleLogout()
  const confirmation = h.events.at(-1)[1]
  assert.equal(confirmation.cancelText, '取消')
  assert.equal(confirmation.confirmText, '退出')
  const loggingOut = confirmation.success({ confirm: true })
  assert.equal(h.requests[0].method, 'POST')
  assert.ok(h.requests[0].url.endsWith('/auth/logout'))
  h.requests[0].success({ statusCode: 200, data: {} })
  await loggingOut
  assert.equal(h.storage.has('token'), false)
  assert.equal(h.storage.has('user'), false)
  assert.equal(h.events.at(-1)[1].url, '/pages/bind/index?manual=1')
})

test('invalid upload IDs and missing downloaded paths cannot become attached photos', async () => {
  const h = harness(), api = h.load('src/services/api.ts')
  const upload = api.uploadReportImage('local-photo')
  h.uploads[0].success({ statusCode: 200, data: JSON.stringify({ accessPath: '/image' }) })
  await assert.rejects(upload, /上传结果/)
  const download = api.downloadReportImage(1)
  h.downloads[0].success({ statusCode: 200 })
  await assert.rejects(download, /无法加载/)
})

test('report attachment previews use authenticated downloads once and allow recovery from failure', async () => {
  const h = harness()
  const page = await h.page('src/components/ReportImages.vue', {}, false, { images: [{ id: 11 }] })
  const first = page.preview()
  await page.preview()
  assert.equal(h.downloads.length, 1)
  assert.equal(h.downloads[0].header.Authorization, 'Bearer old-token')
  h.downloads[0].success({ statusCode: 403 })
  await first
  assert.match(page.error.value, /无法加载/)
  const retry = page.preview()
  h.downloads[1].success({ statusCode: 200, tempFilePath: 'local-file://photo' })
  await retry
  assert.equal(page.error.value, '')
  assert.equal(h.events.at(-1)[0], 'previewImage')
  assert.equal(h.events.at(-1)[1].urls[0], 'local-file://photo')
})

test('a late attachment preview cannot open under another account or after unmount', async () => {
  const h = harness(), page = await h.page('src/components/ReportImages.vue', {}, false, { images: [{ id: 11 }] })
  const pending = page.preview()
  h.storage.set('token', 'new-token')
  h.downloads[0].success({ statusCode: 200, tempFilePath: 'private-old-file' })
  await pending
  assert.ok(h.events.every(([name]) => name !== 'previewImage'))
  const next = page.preview()
  h.unmount()
  h.downloads[1].success({ statusCode: 200, tempFilePath: 'private-file' })
  await next
  assert.ok(h.events.every(([name]) => name !== 'previewImage'))
})

test('H5 attachment preview uses its own image dialog instead of the incompatible native popup', async () => {
  const h = harness({ browser: true }), page = await h.page('src/components/ReportImages.vue', {}, false, { images: [{ id: 11 }] })
  const opened = page.preview()
  h.downloads[0].success({ statusCode: 200, tempFilePath: 'blob:https://fixture.invalid/photo' })
  await opened
  assert.equal(page.showPreview.value, true)
  assert.equal(page.previewUrls.value[0], 'blob:https://fixture.invalid/photo')
  assert.ok(h.events.every(([name]) => name !== 'previewImage'))
  page.trapPreview({ key: 'Escape', preventDefault() {} })
  assert.equal(page.showPreview.value, false)
})

test('shared mobile icon glyphs inherit the semantic theme color instead of freezing the light-theme color', () => {
  const source = readFileSync(path.join(root, 'src/components/IconButton.vue'), 'utf8')
  assert.match(source, /:color="primary \? '#ffffff' : 'inherit'"/)
  assert.match(source, /color:\s*var\(--mobile-muted,\s*#41635a\)/)
})

test('a failed photo batch revokes both acquired and later successful Blob URLs', async () => {
  const early = deferred(), failure = deferred(), late = deferred(), revoked = []
  const h = harness({ browser: true, urlApi: { revokeObjectURL: url => revoked.push(url) }, services: {
    ensureLogin: () => true,
    downloadReportImage: id => ({ 11: early, 12: failure, 13: late })[id].promise
  } })
  const page = await h.page('src/components/ReportImages.vue', {}, true, { images: [{ id: 11 }, { id: 12 }, { id: 13 }] })
  const pending = page.preview()
  early.resolve('blob:https://fixture.invalid/early-photo')
  await Promise.resolve()
  failure.reject(new Error('one photo failed'))
  await pending
  late.resolve('blob:https://fixture.invalid/late-photo')
  await late.promise
  await Promise.resolve()
  assert.equal(page.showPreview.value, false)
  assert.equal(page.busy.value, false)
  assert.equal(page.error.value, 'one photo failed')
  assert.deepEqual(revoked.sort(), ['blob:https://fixture.invalid/early-photo', 'blob:https://fixture.invalid/late-photo'])
  h.unmount()
})

test('late files from a failed photo batch cannot replace or release a successful retry', async () => {
  const failure = deferred(), late = deferred(), revoked = [], counts = new Map()
  const h = harness({ browser: true, urlApi: { revokeObjectURL: url => revoked.push(url) }, services: {
    ensureLogin: () => true,
    downloadReportImage: id => {
      counts.set(id, (counts.get(id) || 0) + 1)
      return counts.get(id) === 1 ? (id === 11 ? failure.promise : late.promise)
        : Promise.resolve(`blob:https://fixture.invalid/current-${id}`)
    }
  } })
  const page = await h.page('src/components/ReportImages.vue', {}, true, { images: [{ id: 11 }, { id: 12 }] })
  const pending = page.preview()
  failure.reject(new Error('retry this batch'))
  await pending
  await page.preview()
  late.resolve('blob:https://fixture.invalid/former-late-photo')
  await late.promise
  await Promise.resolve()
  assert.equal(page.showPreview.value, true)
  assert.equal(page.busy.value, false)
  assert.equal(page.error.value, '')
  assert.deepEqual([...page.previewUrls.value], ['blob:https://fixture.invalid/current-11', 'blob:https://fixture.invalid/current-12'])
  assert.deepEqual(revoked, ['blob:https://fixture.invalid/former-late-photo'])
  h.unmount()
})

test('an API download discarded after an account change releases its generated Blob URL', async () => {
  const revoked = []
  const h = harness({ browser: true, urlApi: { revokeObjectURL: url => revoked.push(url) } })
  const api = h.load('src/services/api.ts')
  const pending = api.downloadReportImage(11)
  h.storage.set('token', 'new-account-token')
  h.downloads[0].success({ statusCode: 200, tempFilePath: 'blob:https://fixture.invalid/former-account' })
  await assert.rejects(pending, /无法加载/)
  assert.deepEqual(revoked, ['blob:https://fixture.invalid/former-account'])
  assert.equal(h.storage.get('token'), 'new-account-token')
  assert.equal(h.timers.size, 0)
})

test('failed report context can retry without losing entered values or bypassing permissions', async () => {
  let failed = true
  const services = reportServices({ fetchWorkOrder: async () => {
    if (failed) throw new Error('工序网络故障')
    return { id: 42, status: 'EXECUTING', plannedQuantity: 100, unitName: '公斤', tasks: [{ sequenceNo: 1, processName: '已恢复工序', enabled: true }] }
  } })
  const h = harness({ services }), page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  assert.match(page.loadError.value, /网络故障/)
  page.form.reportQuantity = '12'; page.form.remark = '保留输入'
  failed = false
  await page.loadReportContext()
  assert.equal(page.loadError.value, '')
  assert.equal(page.form.reportQuantity, '12')
  assert.equal(page.form.remark, '保留输入')
  assert.equal(page.form.processName, '已恢复工序')
  assert.equal(page.productUnit.value, '公斤')
  assert.equal(page.selectedReporterId.value, defaultUser.id)
  services.fetchWorkOrder = async () => ({ id: 42 })
  await page.loadReportContext()
  assert.match(page.loadError.value, /返回格式异常/)
  assert.equal(page.form.reportQuantity, '12')
})

test('report context ignores late lookup values after a newer retry', async () => {
  const old = deferred(), fresh = deferred(); let calls = 0
  const h = harness({ services: reportServices({ fetchWorkOrder: () => ++calls === 1 ? old.promise : fresh.promise }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' }, false)
  const mounted = h.mount()
  const retry = page.loadReportContext()
  fresh.resolve({ id: 42, status: 'EXECUTING', tasks: [{ sequenceNo: 1, processName: '新工序', completedQuantity: 11 }] })
  await retry
  old.resolve({ id: 42, status: 'EXECUTING', tasks: [{ sequenceNo: 1, processName: '旧工序', completedQuantity: 99 }] })
  await mounted
  assert.equal(page.completedQty.value, 11)
  assert.equal(page.form.processName, '新工序')
})

test('report history can address pages beyond the former 5000-record ceiling with literal filters', async () => {
  const h = harness(), api = h.load('src/services/api.ts')
  const pending = api.fetchReportPage({ page: 251, size: 20, status: 'REJECTED', period: 'lastMonth', includeSubmitted: true })
  const query = new URL(h.requests[0].url, 'https://fixture.invalid').searchParams
  assert.equal(query.get('page'), '251'); assert.equal(query.get('status'), 'REJECTED'); assert.equal(query.get('includeSubmitted'), 'true')
  h.requests[0].success({ statusCode: 200, data: { items: [{ id: 5001 }], total: 6000 } })
  assert.equal((await pending).items[0].id, 5001)
  assert.equal(h.requests.length, 1)
})

test('report history retains earlier pages and retries the failed next page rather than resetting', async () => {
  const calls = []
  const h = harness({ services: { ensureLogin: () => true, fetchReportPage: async options => {
    calls.push(options)
    if (calls.length === 2) throw new Error('下一页网络故障')
    return { items: options.page === 1 ? Array.from({ length: 20 }, (_, index) => ({ id: index + 1, status: 'PENDING' })) : [{ id: 21, status: 'PENDING' }], total: 21 }
  } } })
  const page = await h.page('src/pages/report-list/index.vue')
  await page.loadMore()
  assert.equal(page.reports.value.length, 20); assert.match(page.loadError.value, /网络故障/)
  await page.retryPage()
  assert.deepEqual(calls.map(options => options.page), [1, 2, 2])
  assert.equal(page.reports.value.length, 21); assert.equal(page.hasMore.value, false)
})

test('a failed same-query refresh keeps cached history until a successful retry replaces it', async () => {
  let calls = 0
  const h = harness({ services: { ensureLogin: () => true, fetchReportPage: async () => {
    if (++calls === 2) throw new Error('刷新失败')
    return { items: [{ id: calls, status: 'PENDING' }], total: 1 }
  } } })
  const page = await h.page('src/pages/report-list/index.vue')
  await page.loadReports()
  assert.equal(page.reports.value[0].id, 1); assert.equal(page.reportTotal.value, 1)
  await page.retryPage()
  assert.equal(page.reports.value[0].id, 3); assert.equal(page.reports.value.length, 1)
})

test('work-order load errors are actionable and a valid retry restores its actual data', async () => {
  let failed = true
  const h = harness({ services: { ensureLogin: () => true, fetchWorkOrder: async () => {
    if (failed) throw new Error('工单网络故障')
    return { id: 42, code: '真实工单', unitName: '米', tasks: [] }
  }, fetchReportPage: async () => ({ items: [], total: 0 }) } })
  const page = await h.page('src/pages/work-order-detail/index.vue', { id: '42' })
  assert.match(page.loadError.value, /网络故障/)
  failed = false
  await page.loadData()
  assert.equal(page.order.value.code, '真实工单'); assert.equal(page.loadError.value, '')
  assert.equal(page.productUnit.value, '米')
})

test('work-order history has paged recovery without turning failures into zero records', async () => {
  const calls = []
  const h = harness({ services: { ensureLogin: () => true, fetchWorkOrder: async () => ({ id: 42, code: 'WO42', tasks: [] }), fetchReportPage: async options => {
    calls.push(options)
    if (calls.length === 2) throw new Error('履历网络故障')
    return { items: options.page === 1 ? Array.from({ length: 20 }, (_, index) => ({ id: index + 1, status: 'PENDING' })) : [{ id: 21, status: 'PENDING' }], total: 21 }
  } } })
  const page = await h.page('src/pages/work-order-detail/index.vue', { id: '42' })
  await page.loadHistoryMore()
  assert.equal(page.reports.value.length, 20); assert.equal(page.reportTotal.value, 21)
  assert.match(page.historyError.value, /网络故障/)
  await page.retryHistory()
  assert.deepEqual(calls.map(options => options.page), [1, 2, 2])
  assert.ok(calls.every(options => options.workOrderId === 42 && options.includeSubmitted === true))
  assert.equal(page.loadedReports.value, 21); assert.equal(page.historyHasMore.value, false)
})

test('round2: a camera selection from a departed report page cannot upload under another account', async () => {
  let uploads = 0
  const h = harness({ services: reportServices({ uploadReportImage: async () => { uploads++; return { id: 3 } } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  page.chooseImage()
  h.storage.set('token', 'new-session')
  h.storage.set('user', { ...defaultUser, id: 8, factoryId: 2 })
  await h.choices[0].success({ tempFilePaths: ['/tmp/old-account-photo.png'] })
  assert.equal(uploads, 0)
  assert.equal(page.uploadedImages.value.length, 0)
})

test('round2: an in-flight photo cannot enter the draft after the report page unmounts', async () => {
  const upload = deferred()
  const h = harness({ services: reportServices({ uploadReportImage: () => upload.promise }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  page.chooseImage()
  const selected = h.choices[0].success({ tempFilePaths: ['/tmp/photo.png'] })
  h.unmount()
  upload.resolve({ id: 3, accessPath: '/api/mobile/reports/images/3/file' })
  await selected
  assert.equal(page.uploadedImages.value.length, 0)
  assert.equal(page.uploading.value, false)
})

test('round2: a committed report from an unmounted page cannot erase a newer draft or navigate later', async () => {
  const write = deferred()
  const h = harness({ services: reportServices({ createReport: () => write.promise }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  const pending = page.handleSubmit(false)
  h.unmount()
  const draftKey = 'gongdan:draft:report:42:1:user:global:7'
  h.storage.set(draftKey, { form: { remark: 'a newer page draft' } })
  write.resolve({ id: 1 })
  await pending
  h.flushTimers()
  assert.equal(h.storage.get(draftKey)?.form.remark, 'a newer page draft')
  assert.equal(h.events.filter(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)).length, 0)
  assert.equal(h.events.filter(([kind, options]) => kind === 'toast' && options.title === '提交成功').length, 0)
})

test('round2: submit-and-continue refreshes real process totals before enabling another submission', async () => {
  let reads = 0
  const h = harness({ services: reportServices({ fetchWorkOrder: async () => ({
    id: 42, status: 'EXECUTING', tasks: [{ sequenceNo: 1, processName: '实际工序', plannedQuantity: 100,
      completedQuantity: ++reads === 1 ? 0 : 10, defectQuantity: 2, enabled: true }]
  }) }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  await page.handleSubmit(true)
  assert.equal(reads, 2)
  assert.equal(page.completedQty.value, 10)
  assert.equal(page.defectQtyDisplay.value, 2)
  assert.equal(page.loading.value, false)
})

test('round2: a paused order loaded through a direct report URL cannot enable submit', async () => {
  let writes = 0
  const h = harness({ services: reportServices({ fetchWorkOrder: async () => ({
    id: 42, status: 'PAUSED', tasks: [{ sequenceNo: 1, processName: '实际工序', enabled: true }]
  }), createReport: async () => { writes++; return { id: 1 } } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  await page.handleSubmit(false)
  assert.match(page.loadError.value, /不可报工|暂停/)
  assert.equal(writes, 0)
})

test('round2: leaving password login before its reply never restores a session or schedules a redirect', async () => {
  const reply = deferred()
  let saved = 0
  const h = harness({ browser: true, services: {
    passwordLogin: () => reply.promise, saveSession: () => { saved++ },
    bindAccount: async () => { throw new Error('wrong endpoint') }, wechatLogin: async () => { throw new Error('wrong platform') }
  } })
  const page = await h.page('src/pages/bind/index.vue')
  Object.assign(page.form, { factoryCode: 'TEST', username: 'worker', password: 'password' })
  const login = page.handleBind()
  h.unmount()
  reply.resolve({ token: 'reply-token', user: defaultUser })
  await login
  h.flushTimers()
  assert.equal(saved, 0)
  assert.equal(h.events.filter(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)).length, 0)
})

test('round2: a late password login cannot replace a session selected while the request was pending', async () => {
  const reply = deferred()
  let saved = 0
  const h = harness({ browser: true, services: {
    passwordLogin: () => reply.promise, saveSession: () => { saved++ },
    bindAccount: async () => { throw new Error('wrong endpoint') }, wechatLogin: async () => { throw new Error('wrong platform') }
  } })
  const page = await h.page('src/pages/bind/index.vue')
  Object.assign(page.form, { factoryCode: 'TEST', username: 'worker', password: 'password' })
  const login = page.handleBind()
  h.storage.set('token', 'selected-session')
  reply.resolve({ token: 'reply-token', user: defaultUser })
  await login
  assert.equal(saved, 0)
  assert.equal(h.storage.get('token'), 'selected-session')
})

test('round2: a new session can refresh field events while the previous session GET is unresolved', async () => {
  const old = deferred()
  let calls = 0
  const h = harness({ services: { ensureLogin: () => true, fetchFieldEvents: () => ++calls === 1 ? old.promise : Promise.resolve({ items: [{ id: 2, title: 'new factory' }] }) } })
  const page = await h.page('src/pages/field-events/index.vue', {}, false)
  const mounted = h.mount()
  h.storage.set('token', 'new-session')
  h.storage.set('user', { ...defaultUser, factoryId: 2 })
  await page.loadEvents()
  assert.equal(calls, 2)
  old.resolve({ items: [{ id: 1, title: 'old factory' }] })
  await mounted
  assert.equal(page.events.value[0].title, 'new factory')
})

test('round2: a departed field-event POST cannot clear a newer draft or refresh its former page', async () => {
  const write = deferred()
  let reads = 0
  const h = harness({ services: { ensureLogin: () => true, fetchFieldEvents: async () => { reads++; return { items: [] } }, createFieldEvent: () => write.promise } })
  const page = await h.page('src/pages/field-events/index.vue')
  page.form.title = 'old submitted event'
  const submitting = page.submitEvent()
  h.unmount()
  const draftKey = 'gongdan:draft:field-event:user:global:7'
  h.storage.set(draftKey, { title: 'new draft' })
  write.resolve({ id: 1 })
  await submitting
  assert.equal(h.storage.get(draftKey)?.title, 'new draft')
  assert.equal(reads, 1)
})

test('round2: an image-selected QR from a previous account never navigates under a new account', async () => {
  const decoded = deferred()
  const h = harness({ browser: true, services: { ensureLogin: () => true }, mocks: {
    'src/utils/qr-image.ts': { QR_MAX_FILE_BYTES: 10485760, readQrImage: () => decoded.promise }
  } })
  const page = await h.page('src/components/ScanWorkOrderDialog.vue', {}, true, { modelValue: true })
  page.choose('camera')
  const selected = h.choices[0].success({ tempFilePaths: ['/tmp/qr.png'] })
  h.storage.set('token', 'new-account-session')
  decoded.resolve('http://localhost:8080/mobile/#/pages/work-order-detail/index?id=42')
  await selected
  assert.equal(h.events.filter(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)).length, 0)
})

test('round2: a valid same-account image QR closes the dialog and opens the actual internal work order', async () => {
  const h = harness({ browser: true, services: { ensureLogin: () => true }, mocks: {
    'src/utils/qr-image.ts': { QR_MAX_FILE_BYTES: 10485760, readQrImage: async () => 'https://fixture.invalid/mobile/#/pages/work-order-detail/index?id=42' }
  } })
  const page = await h.page('src/components/ScanWorkOrderDialog.vue', {}, true, { modelValue: true })
  page.choose('album')
  await h.choices[0].success({ tempFilePaths: ['/tmp/qr.png'] })
  assert.equal(page.busy.value, false)
  assert.equal(page.error.value, '')
  assert.equal(h.events.find(([kind]) => kind === 'emit')?.[1], 'update:modelValue')
  assert.equal(h.events.find(([kind]) => kind === 'emit')?.[2], false)
  assert.equal(h.events.find(([kind]) => kind === 'navigateTo')?.[1].url, '/pages/work-order-detail/index?id=42')
})

test('round2: a native scan callback from a prior account cannot redirect a newly selected session', async () => {
  const h = harness(), page = await h.page('src/pages/home/index.vue', {}, false)
  page.handleScan()
  assert.equal(h.scans.length, 1)
  h.storage.set('token', 'new-account-session')
  h.scans[0].success({ result: 'https://fixture.invalid/mobile/#/pages/work-order-detail/index?id=42' })
  assert.equal(h.events.some(([kind]) => kind === 'navigateTo'), false)
})

test('round2: a departed home native scan callback never redirects or displays an old failure', async () => {
  const h = harness(), page = await h.page('src/pages/home/index.vue', {}, false)
  page.handleScan()
  h.unmount()
  h.scans[0].success({ result: '42' })
  h.scans[0].fail({ errMsg: 'scan failed' })
  assert.equal(h.events.some(([kind]) => kind === 'navigateTo' || kind === 'toast'), false)
})

test('round2: an older logout confirmation cannot clear the session chosen after it opened', async () => {
  const h = harness(), page = await h.page('src/pages/home/index.vue', {}, false)
  page.handleLogout()
  h.storage.set('token', 'selected-account-token')
  h.storage.set('user', { ...defaultUser, id: 8 })
  h.events.find(([kind]) => kind === 'modal')[1].success({ confirm: true })
  assert.equal(h.storage.get('token'), 'selected-account-token')
  assert.equal(h.storage.get('user').id, 8)
  assert.equal(h.events.some(([kind]) => kind === 'reLaunch'), false)
})

test('round2: task permission responses from a departed page cannot navigate after the user leaves', async () => {
  const permission = deferred()
  const h = harness({ services: { ensureLogin: () => true, fetchWorkOrderPage: async () => ({ items: [taskOrder(1)], total: 1 }), fetchWorkOrderReporters: () => permission.promise } })
  const page = await h.page('src/pages/tasks/index.vue')
  const opening = page.handleTask(page.tasks.value[0])
  h.unmount()
  permission.resolve([defaultUser])
  await opening
  assert.equal(h.events.some(([kind]) => kind === 'navigateTo'), false)
})

test('round2: detail permission cannot use same-number user IDs from another factory or a departed page', async () => {
  for (const changeSession of [true, false]) {
    const permission = deferred()
    const h = harness({ services: { ensureLogin: () => true, fetchWorkOrder: async () => taskOrder(42), fetchReportPage: async () => ({ items: [], total: 0 }), fetchWorkOrderReporters: () => permission.promise } })
    const page = await h.page('src/pages/work-order-detail/index.vue', { id: '42' })
    const opening = page.handleReport(page.order.value.tasks[0])
    if (changeSession) { h.storage.set('token', 'new-account-session'); h.storage.set('user', { ...defaultUser, factoryId: 2 }) }
    else h.unmount()
    permission.resolve([defaultUser])
    await opening
    assert.equal(h.events.some(([kind]) => kind === 'navigateTo'), false)
  }
})

test('round2: report submit-and-continue waits for its context refresh and cannot write twice while it is pending', async () => {
  const fresh = deferred()
  let reads = 0, writes = 0
  const h = harness({ services: reportServices({ fetchWorkOrder: () => ++reads === 1 ? Promise.resolve(taskOrder(42)) : fresh.promise, createReport: async () => { writes++; return { id: 1 } } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  const first = page.handleSubmit(true)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(reads, 2)
  assert.equal(page.loading.value, true)
  assert.equal(page.reporterLoading.value, true)
  fillValidReport(page)
  await page.handleSubmit(true)
  assert.equal(writes, 1)
  fresh.resolve(taskOrder(42))
  await first
  assert.equal(page.loading.value, false)
})

test('round2: a committed continue submission with a failed reread remains committed and blocks a duplicate retry', async () => {
  let reads = 0, writes = 0
  const h = harness({ services: reportServices({ fetchWorkOrder: async () => {
    if (++reads === 2) throw new Error('已提交，但工序刷新失败')
    return taskOrder(42)
  }, createReport: async () => { writes++; return { id: 1 } } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  await page.handleSubmit(true)
  assert.match(page.loadError.value, /工序刷新失败/)
  assert.equal(page.form.reportQuantity, '')
  assert.equal(page.loading.value, false)
  await page.handleSubmit(true)
  assert.equal(writes, 1)
  await page.loadReportContext()
  assert.equal(page.loadError.value, '')
  assert.equal(writes, 1)
})

test('round2: only the three actual backend reportable states permit a direct form submission', async () => {
  for (const status of ['NOT_STARTED', 'EXECUTING', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'FINISHED', 'CLOSED', 'CANCELED', undefined]) {
    let writes = 0
    const h = harness({ services: reportServices({ fetchWorkOrder: async () => ({ ...taskOrder(42), status }), createReport: async () => { writes++; return { id: 1 } } }) })
    const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
    fillValidReport(page)
    await page.handleSubmit(false)
    assert.equal(writes, ['NOT_STARTED', 'EXECUTING', 'IN_PROGRESS'].includes(status) ? 1 : 0, String(status))
  }
})

test('round2: a report page context loaded for a former account never submits or starts a new image selection', async () => {
  let writes = 0
  const h = harness({ services: reportServices({ createReport: async () => { writes++; return { id: 1 } } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  h.storage.set('token', 'new-account-session')
  page.chooseImage()
  await page.handleSubmit(false)
  assert.equal(h.choices.length, 0)
  assert.equal(writes, 0)
  assert.match(page.loadError.value, /登录状态已变更/)
})

test('round2: multiple report uploads stop after an account change and no stale upload error is shown', async () => {
  const first = deferred()
  let calls = 0
  const h = harness({ services: reportServices({ uploadReportImage: () => { calls++; return first.promise } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  page.chooseImage()
  const selected = h.choices[0].success({ tempFilePaths: ['/tmp/one.png', '/tmp/two.png'] })
  h.storage.set('token', 'new-account-session')
  first.reject(new Error('an old photo error'))
  await selected
  assert.equal(calls, 1)
  assert.equal(page.uploadedImages.value.length, 0)
  assert.equal(page.uploading.value, false)
  assert.equal(h.events.some(([kind, options]) => kind === 'toast' && options.title === 'an old photo error'), false)
})

test('round2: a login redirect timer never runs under a session chosen after the login succeeds', async () => {
  const h = harness({ browser: true, services: { passwordLogin: async () => ({ token: 'signed-in-session', user: defaultUser }), saveSession: result => { h.storage.set('token', result.token); h.storage.set('user', result.user) } } })
  const page = await h.page('src/pages/bind/index.vue')
  Object.assign(page.form, { factoryCode: 'TEST', username: 'worker', password: 'password' })
  await page.handleBind()
  assert.equal(h.storage.get('token'), 'signed-in-session')
  h.storage.set('token', 'later-account-session')
  h.flushTimers()
  assert.equal(h.events.some(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)), false)
})

test('round2: a late WeChat login cannot restore a departed page or replace a selected session', async () => {
  for (const changeSession of [true, false]) {
    const reply = deferred()
    let saved = 0
    const h = harness({ services: { wechatLogin: () => reply.promise, saveSession: () => { saved++ } } })
    const page = await h.page('src/pages/bind/index.vue', {}, false)
    const login = page.tryWechatLogin()
    await new Promise(resolve => setImmediate(resolve))
    if (changeSession) h.storage.set('token', 'selected-account-session')
    else h.unmount()
    reply.resolve({ token: 'reply-token', user: defaultUser })
    await login
    assert.equal(saved, 0)
    assert.equal(h.events.some(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)), false)
  }
})

test('round2: an unmounted draft owner cannot clear a replacement page draft with the same account key', async () => {
  const h = harness()
  const form = Vue.reactive({ title: 'old draft' })
  const { clearDraft } = h.load('src/composables/usePageDraft.ts').usePageDraft({ key: 'round2-draft', source: () => ({ ...form }), restore: () => {} })
  await h.mount()
  h.unmount()
  h.storage.set('round2-draft:user:global:7', { title: 'new page draft' })
  clearDraft()
  assert.equal(h.storage.get('round2-draft:user:global:7').title, 'new page draft')
})

test('round2: a merely hidden report POST cannot erase a newer draft, announce success, or redirect on its old page', async () => {
  const write = deferred()
  const h = harness({ browser: true, services: reportServices({ createReport: () => write.promise }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  const pending = page.handleSubmit(false)
  h.hooks.hide.forEach(callback => callback())
  const draftKey = 'gongdan:draft:report:42:1:user:global:7'
  h.storage.set(draftKey, { form: { remark: 'a newer visible page draft' } })
  write.resolve({ id: 1 })
  await pending
  h.flushTimers()
  assert.equal(h.storage.get(draftKey).form.remark, 'a newer visible page draft')
  assert.equal(h.events.some(([kind, options]) => kind === 'toast' && options.title === '提交成功'), false)
  assert.equal(h.events.some(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)), false)
  assert.equal(page.submitPhase.value, 'uncertain')
  assert.match(page.loadError.value, /页面已切换/)
})

test('round2: a hidden login reply neither saves the session nor clears or redirects the current page', async () => {
  const reply = deferred()
  let saved = 0
  const h = harness({ browser: true, services: { passwordLogin: () => reply.promise, saveSession: () => { saved++ } } })
  const page = await h.page('src/pages/bind/index.vue')
  Object.assign(page.form, { factoryCode: 'TEST', username: 'worker', password: 'password' })
  const login = page.handleBind()
  h.hooks.hide.forEach(callback => callback())
  reply.resolve({ token: 'reply-token', user: defaultUser })
  await login
  h.flushTimers()
  assert.equal(saved, 0)
  assert.equal(page.form.password, '')
  assert.equal(h.events.some(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)), false)
})

test('round2: a native camera overlay hide preserves a same-page same-account photo selection', async () => {
  const h = harness({ services: reportServices() })
  const currentPage = { route: 'pages/report-form/index' }
  h.setCurrentPages([currentPage])
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  page.chooseImage()
  h.hooks.hide.forEach(callback => callback())
  await h.choices[0].success({ tempFilePaths: ['/tmp/native-camera.png'] })
  assert.equal(page.uploadedImages.value.length, 1)
  assert.equal(page.uploadedImages.value[0].previewPath, '/tmp/native-camera.png')
  assert.equal(page.uploading.value, false)
})

test('round2: native scanner overlays can hide the same page but navigating to a different uni page invalidates them', async () => {
  for (const navigateAway of [true, false]) {
    const h = harness(), currentPage = { route: 'pages/home/index' }
    h.setCurrentPages([currentPage])
    const page = await h.page('src/pages/home/index.vue', {}, false)
    page.handleScan()
    h.hooks.hide.forEach(callback => callback())
    if (navigateAway) h.setCurrentPages([currentPage, { route: 'pages/field-events/index' }])
    h.scans[0].success({ result: 'https://fixture.invalid/mobile/#/pages/work-order-detail/index?id=42' })
    assert.equal(h.events.some(([kind]) => kind === 'navigateTo'), !navigateAway)
  }
})

test('round2: leaving and returning to an H5 route cannot revive its older media picker callback', async () => {
  const h = harness({ browser: true, services: reportServices() })
  h.window.location.hash = '#/pages/report-form/index?orderId=42&processNo=1'
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  page.chooseImage()
  h.hooks.hide.forEach(callback => callback())
  h.hashRoute('#/pages/work-order-detail/index?id=42')
  h.hashRoute('#/pages/report-form/index?orderId=42&processNo=1')
  await h.choices[0].success({ tempFilePaths: ['/tmp/stale-photo.png'] })
  assert.equal(page.uploadedImages.value.length, 0)
  assert.equal(page.uploading.value, false)
})

test('round2: a field-event POST on a hidden but mounted page does not clear a new draft or refresh the old view', async () => {
  const write = deferred()
  let reads = 0
  const h = harness({ services: { ensureLogin: () => true, fetchFieldEvents: async () => { reads++; return { items: [] } }, createFieldEvent: () => write.promise } })
  const page = await h.page('src/pages/field-events/index.vue')
  page.form.title = 'submitted while visible'
  const pending = page.submitEvent()
  h.hooks.hide.forEach(callback => callback())
  h.storage.set('gongdan:draft:field-event:user:global:7', { title: 'new visible page draft' })
  write.resolve({ id: 1 })
  await pending
  assert.equal(reads, 1)
  assert.equal(h.storage.get('gongdan:draft:field-event:user:global:7').title, 'new visible page draft')
  assert.equal(h.events.some(([kind, options]) => kind === 'toast' && options.title === '已上报'), false)
})

test('round2: merely hidden task and detail permission lookups cannot open the former report form', async () => {
  for (const filename of ['tasks', 'work-order-detail']) {
    const permission = deferred()
    const h = harness({ services: {
      ensureLogin: () => true, fetchWorkOrderPage: async () => ({ items: [taskOrder(42)], total: 1 }),
      fetchWorkOrder: async () => taskOrder(42), fetchReportPage: async () => ({ items: [], total: 0 }),
      fetchWorkOrderReporters: () => permission.promise
    } })
    const page = await h.page(`src/pages/${filename}/index.vue`, { id: '42' })
    const pending = filename === 'tasks' ? page.handleTask(page.tasks.value[0]) : page.handleReport(page.order.value.tasks[0])
    h.hooks.hide.forEach(callback => callback())
    permission.resolve([defaultUser])
    await pending
    assert.equal(h.events.some(([kind]) => kind === 'navigateTo'), false)
  }
})

test('MSEC-001: untrusted work-order identifiers cannot change an authenticated API path', async () => {
  const h = harness(), api = h.load('src/services/api.ts')
  const request = api.fetchWorkOrder('../reports')
  assert.equal(h.requests.length, 0)
  await assert.rejects(request, /工单编号/)
})

test('MSEC-002: a hidden but mounted photo owner cannot open its late authenticated download over another page', async () => {
  const download = deferred()
  const h = harness({ browser: true, services: { ensureLogin: () => true, downloadReportImage: () => download.promise } })
  const props = Vue.reactive({ images: [{ id: 11 }], active: true })
  const page = await h.page('src/components/ReportImages.vue', {}, true, props)
  const pending = page.preview()
  props.active = false
  await Vue.nextTick()
  download.resolve('blob:https://fixture.invalid/private-photo')
  await pending
  assert.equal(page.showPreview.value, false)
  assert.equal(page.previewUrls.value.length, 0)
})

test('MSEC-002: leaving the owner page closes an already open teleported photo gallery', async () => {
  const h = harness({ browser: true, services: { ensureLogin: () => true, downloadReportImage: async () => 'blob:https://fixture.invalid/private-photo' } })
  const props = Vue.reactive({ images: [{ id: 11 }], active: true })
  const page = await h.page('src/components/ReportImages.vue', {}, true, props)
  await page.preview()
  assert.equal(page.showPreview.value, true)
  props.active = false
  await Vue.nextTick()
  assert.equal(page.showPreview.value, false)
})

test('MUX-001: concurrent report photo buttons can own only one preview overlay', async () => {
  const first = deferred(), second = deferred()
  const h = harness({ browser: true, services: { ensureLogin: () => true, downloadReportImage: id => id === 11 ? first.promise : second.promise } })
  const oldPage = await h.page('src/components/ReportImages.vue', {}, true, { images: [{ id: 11 }], active: true })
  const newPage = await h.page('src/components/ReportImages.vue', {}, true, { images: [{ id: 12 }], active: true })
  const oldPreview = oldPage.preview()
  const newPreview = newPage.preview()
  second.resolve('blob:https://fixture.invalid/current-photo')
  await newPreview
  first.resolve('blob:https://fixture.invalid/former-photo')
  await oldPreview
  assert.equal(newPage.showPreview.value, true)
  assert.equal(oldPage.showPreview.value, false)
})

test('MSEC-003: a partially failed login persistence cannot leave a new token with the former user', () => {
  const h = harness(), api = h.load('src/services/api.ts')
  h.uni.setStorageSync = (key, value) => {
    if (key === 'user') throw new Error('Storage quota failure')
    h.storage.set(key, value)
  }
  assert.throws(() => api.saveSession({ token: 'new-session', user: { ...defaultUser, id: 8 } }))
  assert.equal(h.storage.has('token'), false)
  assert.equal(h.storage.has('user'), false)
})

test('MSEC-003: malformed session responses are rejected before replacing existing storage', () => {
  const h = harness(), api = h.load('src/services/api.ts')
  assert.throws(() => api.saveSession({ token: { token: 'not-a-string' }, user: { id: 0 } }), /登录响应/)
  assert.equal(h.storage.get('token'), 'old-token')
  assert.equal(h.storage.get('user').id, defaultUser.id)
})

test('MSEC-004: corrupt or prototype-bearing field-event drafts restore only known string fields', async () => {
  const malicious = JSON.parse('{"title":{},"station":42,"description":"safe text","eventType":"unknown","__proto__":{"injected":true}}')
  const h = harness({ storage: { 'gongdan:draft:field-event:user:global:7': malicious }, services: { ensureLogin: () => true, fetchFieldEvents: async () => ({ items: [] }) } })
  const page = await h.page('src/pages/field-events/index.vue')
  assert.equal(page.form.title, '')
  assert.equal(page.form.station, '')
  assert.equal(page.form.description, 'safe text')
  assert.equal(page.form.eventType, '异常')
  assert.equal(page.form.injected, undefined)
  await page.submitEvent()
  assert.match(page.submitError.value, /事件标题/)
})

test('MSEC-004: a malformed device account draft cannot poison login field types', async () => {
  const h = harness({ browser: true, storage: { 'gongdan:draft:login-account': { factoryCode: {}, username: [] } } })
  const page = await h.page('src/pages/bind/index.vue')
  assert.equal(page.form.factoryCode, '')
  assert.equal(page.form.username, '')
  await page.handleBind()
  assert.match(page.loginError.value, /填写完整信息/)
})

test('MSEC-001: all resource-ID rejection paths stop before any authenticated request while normal IDs work', async () => {
  const h = harness(), api = h.load('src/services/api.ts')
  for (const id of [0, -1, NaN, Infinity, '1/../reports', '%2e%2e', 'not-a-resource-id', '9007199254740992']) {
    await assert.rejects(api.fetchWorkOrder(id), /工单编号/)
    await assert.rejects(api.fetchWorkOrderReporters(id, 1), /工单编号/)
  }
  await assert.rejects(api.fetchWorkOrderReporters(42, 0), /工序编号/)
  await assert.rejects(api.downloadReportImage(0), /照片编号/)
  assert.equal(h.requests.length + h.downloads.length, 0)
  const request = api.fetchWorkOrder('00042')
  assert.ok(h.requests[0].url.endsWith('/mobile/work-orders/42'))
  h.requests[0].success({ statusCode: 200, data: { id: 42 } })
  assert.equal((await request).id, 42)
})

test('MSEC-003: a valid session still persists both values and resolves an expired redirect timer', () => {
  const h = harness(), api = h.load('src/services/api.ts')
  api.saveSession({ token: 'valid-session', user: defaultUser })
  assert.equal(h.storage.get('token'), 'valid-session')
  assert.equal(h.storage.get('user').id, defaultUser.id)
  api.clearSession()
  assert.equal(h.storage.has('token'), false)
  assert.equal(h.storage.has('user'), false)
})

test('MUX-002: H5 messages invoke only their own pending decision, dismiss on hide, and preserve native fallback', () => {
  const h = harness({ browser: true })
  const messages = h.load('src/composables/usePageMessage.ts').usePageMessage()
  const choices = []
  messages.open({ title: '旧决定', content: '内容', showCancel: true }, value => choices.push(['old', value]))
  messages.open({ title: '新决定', content: '内容', showCancel: true }, value => choices.push(['new', value]))
  assert.equal(messages.message.visible, true)
  assert.equal(messages.message.title, '新决定')
  assert.deepEqual(choices, [['old', false]])
  h.hooks.hide.forEach(callback => callback())
  assert.equal(messages.message.visible, false)
  assert.deepEqual(choices, [['old', false], ['new', false]])
  const native = harness(), pageMessages = native.load('src/composables/usePageMessage.ts').usePageMessage()
  let accepted = false
  pageMessages.open({ title: '原生', content: '原生内容' }, result => { accepted = result })
  native.events[0][1].success({ confirm: true })
  assert.equal(accepted, true)
})

test('MUX-003: navigation arrows move keyboard focus without silently changing business tabs', async () => {
  const h = harness(), page = await h.page('src/components/MobileTabBar.vue', {}, false, { current: 'home' })
  let focused = -1, prevented = 0
  const controls = Array.from({ length: 5 }, (_, index) => ({ focus: () => { focused = index } }))
  const event = key => ({ key, preventDefault: () => { prevented++ }, currentTarget: { parentElement: { querySelectorAll: () => controls } } })
  page.moveFocus(event('ArrowRight'), 2)
  assert.equal(focused, 3)
  page.moveFocus(event('ArrowLeft'), 0)
  assert.equal(focused, 4)
  page.moveFocus(event('Home'), 4)
  assert.equal(focused, 0)
  page.moveFocus(event('End'), 0)
  assert.equal(focused, 4)
  assert.equal(prevented, 4)
  assert.equal(h.events.length, 0)
})

test('MUX-004: failed report validation and backend saves leave persistent feedback and preserve the draft', async () => {
  const h = harness({ services: reportServices({ createReport: async () => { throw Object.assign(new Error('保存失败，可重试'), { status: 400 }) } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  await page.handleSubmit(false)
  assert.match(page.submitError.value, /报工数/)
  fillValidReport(page)
  page.form.remark = 'keep me'
  await page.handleSubmit(false)
  assert.equal(page.submitError.value, '保存失败，可重试')
  assert.equal(page.form.remark, 'keep me')
  assert.equal(page.loading.value, false)
})

test('MUX-004: field-event save feedback remains separate from list errors and allows an unchanged retry', async () => {
  let fail = true, writes = 0
  const h = harness({ services: { ensureLogin: () => true, fetchFieldEvents: async () => ({ items: [] }), createFieldEvent: async () => { writes++; if (fail) throw Error('现场保存失败'); return { id: 1 } } } })
  const page = await h.page('src/pages/field-events/index.vue')
  page.form.title = 'retain title'
  await page.submitEvent()
  assert.equal(page.submitError.value, '现场保存失败')
  assert.equal(page.errorMessage.value, '')
  assert.equal(page.form.title, 'retain title')
  fail = false
  await page.submitEvent()
  assert.equal(writes, 2)
  assert.equal(page.form.title, '')
  assert.equal(page.submitError.value, '')
})

test('MSEC-002: changing the photo list cancels the former authenticated preview without blocking a fresh request', async () => {
  const old = deferred()
  const h = harness({ browser: true, services: { ensureLogin: () => true, downloadReportImage: id => id === 11 ? old.promise : Promise.resolve('blob:https://fixture.invalid/current') } })
  const props = Vue.reactive({ images: [{ id: 11 }], active: true })
  const page = await h.page('src/components/ReportImages.vue', {}, true, props)
  const pending = page.preview()
  props.images = [{ id: 12 }]
  await Vue.nextTick()
  await page.preview()
  old.resolve('blob:https://fixture.invalid/stale')
  await pending
  assert.equal(page.showPreview.value, true)
  assert.equal(page.previewUrls.value[0], 'blob:https://fixture.invalid/current')
})

test('MSEC-001: malformed report-form deep links cannot be silently truncated to a different valid order', async () => {
  let reads = 0, writes = 0
  const h = harness({ services: reportServices({ fetchWorkOrder: async () => { reads++; return taskOrder(42) }, createReport: async () => { writes++; return { id: 1 } } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42not-an-id', processNo: '1' })
  fillValidReport(page)
  await page.handleSubmit(false)
  assert.equal(reads, 0)
  assert.equal(writes, 0)
  assert.ok(page.loadError.value)
})

test('MSEC-005: logout failures keep the session, prevent duplicate posts, and permit an explicit retry', async () => {
  const write = deferred()
  let calls = 0
  const h = harness({ browser: true, services: { logoutSession: () => ++calls === 1 ? write.promise : Promise.resolve(), clearSession: () => { h.storage.delete('token'); h.storage.delete('user') } } })
  const page = await h.page('src/pages/home/index.vue', {}, false)
  page.handleLogout()
  const pending = page.pageMessage.decide(true)
  page.handleLogout()
  assert.equal(calls, 1)
  assert.equal(page.logoutPending.value, true)
  write.reject(Object.assign(Error('服务暂时不可用'), { status: 503 }))
  await pending
  assert.equal(h.storage.get('token'), 'old-token')
  assert.match(page.logoutError.value, /登录仍保留/)
  assert.equal(page.logoutPending.value, false)
  await page.retryLogout()
  assert.equal(calls, 2)
  assert.equal(h.storage.has('token'), false)
})

test('MSEC-005: a late logout reply cannot clear a newer login or navigate a hidden page', async () => {
  for (const changeSession of [true, false]) {
    const write = deferred()
    const h = harness({ browser: true, services: { logoutSession: () => write.promise, clearSession: () => { h.storage.delete('token'); h.storage.delete('user') } } })
    const page = await h.page('src/pages/home/index.vue', {}, false)
    page.handleLogout()
    const pending = page.pageMessage.decide(true)
    if (changeSession) { h.storage.set('token', 'new-login'); h.storage.set('user', { ...defaultUser, id: 8 }) }
    else h.hooks.hide.forEach(callback => callback())
    write.resolve()
    await pending
    assert.equal(h.storage.get('token'), changeSession ? 'new-login' : 'old-token')
    assert.equal(h.events.some(([kind]) => ['navigateTo', 'switchTab', 'reLaunch'].includes(kind)), false)
  }
})

test('MSEC-005: an expired own logout clears the client but an old 401 cannot erase a different new session', async () => {
  for (const changeSession of [true, false]) {
    const write = deferred()
    const h = harness({ browser: true, services: { logoutSession: () => write.promise, clearSession: () => { h.storage.delete('token'); h.storage.delete('user') } } })
    const page = await h.page('src/pages/home/index.vue', {}, false)
    page.handleLogout()
    const pending = page.pageMessage.decide(true)
    if (changeSession) { h.storage.set('token', 'new-login'); h.storage.set('user', { ...defaultUser, id: 8 }) }
    write.reject(Object.assign(Error('会话已失效'), { status: 401 }))
    await pending
    assert.equal(h.storage.has('token'), changeSession)
    if (changeSession) assert.equal(h.storage.get('token'), 'new-login')
  }
})

test('MUX-004: a temporary attachment download failure preserves the draft and verified reporter until retry', async () => {
  const draftKey = 'gongdan:draft:report:42:1:user:global:7'
  const downloads = [], writes = []
  let failDownload = true
  const h = harness({ storage: { [draftKey]: {
    form: { reportQuantity: '10', goodQuantity: '10', defectQuantity: '0', startTime: '2026-09-11 08:00:00', endTime: '2026-09-11 09:00:00', remark: 'keep saved attachments' },
    selectedReporterId: 7,
    uploadedImages: [{ id: 11 }, { id: 12 }]
  } }, services: reportServices({
    downloadReportImage: async id => {
      downloads.push(id)
      if (id === 12 && failDownload) throw Object.assign(Error('图片暂时无法加载，请重试'), { status: 503 })
      return `wxfile://restored-${id}`
    },
    createReport: async payload => { writes.push(payload); return { id: 1 } }
  }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  assert.match(page.loadError.value, /无法加载/)
  assert.deepEqual([...page.uploadedImages.value].map(image => image.id), [11, 12])
  assert.equal(page.uploadedImages.value[0].previewPath, 'wxfile://restored-11')
  assert.equal(page.uploadedImages.value[1].previewPath, undefined)
  assert.equal(page.selectedReporterId.value, 7)
  assert.equal(page.selectedReporterName.value, defaultUser.name)
  assert.equal(page.form.remark, 'keep saved attachments')
  h.flushTimers()
  assert.deepEqual(h.storage.get(draftKey).uploadedImages.map(image => image.id), [11, 12])
  await page.handleSubmit(true)
  assert.equal(writes.length, 0)
  failDownload = false
  await page.loadReportContext()
  assert.equal(page.loadError.value, '')
  assert.deepEqual(downloads, [11, 12, 12])
  await page.handleSubmit(true)
  assert.equal(writes.length, 1)
  assert.deepEqual([...writes[0].imageIds], [11, 12])
})

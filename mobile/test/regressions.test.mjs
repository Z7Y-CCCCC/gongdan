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

function deferred() {
  let resolve, reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function harness({ browser = false, services, storage: initialStorage = {} } = {}) {
  const storage = new Map(Object.entries({ token: 'old-token', user: defaultUser, ...initialStorage }))
  const requests = [], uploads = [], downloads = [], choices = [], events = []
  const hooks = { mounted: [], unmounted: [], hide: [], show: [], load: [] }
  const timers = new Map()
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
    onBeforeUnmount: callback => hooks.unmounted.push(callback)
  }
  const uniApp = {
    onLoad: callback => hooks.load.push(callback),
    onShow: callback => hooks.show.push(callback),
    onHide: callback => hooks.hide.push(callback),
    onLaunch: callback => hooks.mounted.push(callback)
  }
  const window = browser ? {
    location: { hostname: 'workorder.example.test', hash: '#/pages/bind/index' },
    addEventListener() {}, removeEventListener() {}
  } : undefined
  const cache = new Map()
  function load(relativePath) {
    const filename = path.resolve(root, relativePath)
    if (services && filename === path.join(root, 'src/services/api.ts')) return services
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
      if (specifier === 'vue') return lifecycleVue
      if (specifier === '@dcloudio/uni-app') return uniApp
      if (specifier.startsWith('.')) {
        const resolved = path.resolve(path.dirname(filename), specifier)
        return load(path.extname(resolved) ? resolved : `${resolved}.ts`)
      }
      return require(specifier)
    }
    vm.runInNewContext(output, {
      module, exports: module.exports, require: localRequire,
      uni, window, console, URLSearchParams: browser ? URLSearchParams : undefined,
      getCurrentPages: () => [], __env: { VITE_WECHAT_MOCK_ENABLED: 'false' },
      setTimeout: callback => { timers.set(++timerId, callback); return timerId },
      clearTimeout: id => timers.delete(id)
    }, { filename })
    return module.exports
  }
  return {
    storage, uni, requests, uploads, downloads, choices, events, hooks, timers, window, load,
    async page(filename, options = {}, mount = true) {
      const component = load(filename).default
      const bindings = component.setup({}, { expose() {} })
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

function reportServices(overrides = {}) {
  return {
    ensureLogin: () => true,
    fetchWorkOrder: async () => ({ id: 42, plannedQuantity: 100, tasks: [{ sequenceNo: 1, processName: '实际工序', plannedQuantity: 100, enabled: true }] }),
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

test('rapid report submissions issue exactly one write, including during post-success navigation', async () => {
  const write = deferred()
  let count = 0
  const h = harness({ services: reportServices({ createReport: () => { count += 1; return write.promise } }) })
  const page = await h.page('src/pages/report-form/index.vue', { orderId: '42', processNo: '1' })
  fillValidReport(page)
  const first = page.handleSubmit(false)
  await page.handleSubmit(false)
  assert.equal(count, 1)
  write.resolve({ id: 1 })
  await first
  assert.equal(h.events.filter(([name]) => name === 'hideLoading').length, 1)
  assert.ok(h.events.findIndex(([name]) => name === 'hideLoading') < h.events.findIndex(([name]) => name === 'toast'))
  await page.handleSubmit(false)
  assert.equal(count, 1)
  assert.equal(page.loading.value, true)
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

test('report filter changes keep all-status counters and discard stale period responses', async () => {
  const first = deferred(), second = deferred()
  const calls = []
  const h = harness({ services: { fetchReports: (...args) => { calls.push(args); return calls.length === 1 ? first.promise : second.promise }, ensureLogin: () => true } })
  const page = await h.page('src/pages/report-list/index.vue', {}, false)
  page.statusFilter.value = 'PENDING'
  page.period.value = 'today'
  const a = page.loadReports()
  page.period.value = 'lastMonth'
  const b = page.loadReports()
  second.resolve({ items: [{ id: 2, status: 'APPROVED' }, { id: 3, status: 'PENDING' }], total: 2 })
  await b
  first.resolve({ items: [{ id: 1, status: 'REJECTED' }], total: 1 })
  await a
  assert.ok(calls.every(([status]) => status === ''))
  assert.equal(page.approvedCount.value, 1)
  assert.equal(page.pendingCount.value, 1)
  assert.equal(page.filteredReports.value.length, 1)
  assert.equal(page.reports.value[0].id, 2)
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

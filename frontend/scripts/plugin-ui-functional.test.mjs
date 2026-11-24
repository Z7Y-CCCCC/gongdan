import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const parentOrigin = 'https://app.example.test'
const flush = async () => { for (let i = 0; i < 3; i++) await new Promise(setImmediate) }

const plugins = [
  {
    key: 'equipment-inspection', field: 'equipmentCode', state: 'result',
    values: ['正常', '需维护', '停机'], labels: ['Normal', 'Maintenance needed', 'Stopped'],
    listPath: '/api/plugin/equipment-inspection/inspections', saved: '点检记录已保存。',
    record: { equipmentCode: '正常', result: '需维护', remark: '备注', createdAt: '2026-11-24T10:00:00' },
  },
  {
    key: 'quality-management', field: 'issue', state: 'status',
    values: ['待处理', '处理中', '已关闭'], labels: ['Pending', 'In progress', 'Closed'],
    listPath: '/api/plugin/quality-management/records', saved: '质量问题已保存。',
    record: { issue: '待处理', status: '处理中', remark: '备注', createdAt: '2026-11-24T10:00:00' },
  },
]

/** Run the shipped HTML and locale script; no replacement implementation or real network. */
function createUi(t, plugin, { locale = 'zh-CN' } = {}) {
  const directory = path.join(root, 'plugins', plugin.key, 'src/main/resources/static')
  const html = readFileSync(path.join(directory, 'index.html'), 'utf8')
  const dom = new JSDOM(html, {
    url: `https://${plugin.key}.example.test/?lang=${locale}`,
    runScripts: 'outside-only',
  })
  t.after(() => dom.window.close())
  const { window } = dom
  const messages = []
  const parent = {
    postMessage(data, targetOrigin) {
      messages.push({ data: JSON.parse(JSON.stringify(data)), targetOrigin })
    },
  }
  Object.defineProperty(window, 'parent', { value: parent, configurable: true })
  window.eval(readFileSync(path.join(directory, 'ui-i18n.js'), 'utf8'))
  for (const script of window.document.querySelectorAll('script:not([src])')) window.eval(script.textContent)

  function emit(data, overrides = {}) {
    window.dispatchEvent(new window.MessageEvent('message', {
      source: parent,
      origin: parentOrigin,
      data: { pluginKey: plugin.key, ...data },
      ...overrides,
    }))
  }
  function context(overrides = {}, event = {}) {
    emit({ type: 'platform-context', locale, theme: 'light', context: {}, ...overrides }, event)
  }
  function respond(message, body, ok = true, overrides = {}, event = {}) {
    assert.ok(message, 'expected a pending bridge request')
    emit({ type: 'plugin-api-response', requestId: message.data.requestId, ok, body, ...overrides }, event)
  }
  function requests(method, requestPath) {
    return messages.filter(({ data }) => data.type === 'plugin-api-request'
      && (!method || data.method === method) && (!requestPath || data.path === requestPath))
  }
  function ping() { return requests('GET', `/api/plugin/${plugin.key}/ping`).at(-1) }
  const pingBody = { version: '1.0.0', factoryId: 7, role: '管理员', config: { inspectionIntervalDays: 2 } }
  async function connect(records = []) {
    context()
    respond(ping(), pingBody)
    await flush()
    respond(requests('GET', plugin.listPath).at(-1), records)
    await flush()
    assert.equal(window.document.getElementById('submit').disabled, false)
  }
  function fill() {
    window.document.querySelector(`[name="${plugin.field}"]`).value = '正常 — user entry'
    window.document.querySelector(`[name="${plugin.state}"]`).value = plugin.values[1]
    window.document.querySelector('[name="remark"]').value = '备注 — keep this note'
  }
  function submit() {
    const event = new window.Event('submit', { bubbles: true, cancelable: true })
    window.document.getElementById('form').dispatchEvent(event)
    return event
  }
  return {
    window, messages, parent, emit, context, respond, requests, ping, pingBody,
    connect, fill, submit,
    get: id => window.document.getElementById(id),
  }
}

for (const plugin of plugins) {
  test(`${plugin.key}: safe handshake, exact request path and explicit target origin`, async t => {
    const ui = createUi(t, plugin)
    assert.deepEqual(ui.messages[0], { data: { type: 'plugin-ready', pluginKey: plugin.key }, targetOrigin: '*' })
    assert.equal(ui.get('submit').disabled, true)
    await ui.connect([plugin.record])
    assert.equal(ui.messages.slice(1).every(message => message.targetOrigin === parentOrigin), true)
    assert.equal(new Set(ui.requests().map(message => message.data.requestId)).size, 2)
    assert.equal(ui.get('retry').disabled, false)
    assert.equal(ui.get('records').querySelectorAll('.record').length, 1)
  })

  test(`${plugin.key}: ping failure blocks save and the refresh button recovers`, async t => {
    const ui = createUi(t, plugin)
    ui.context()
    ui.respond(ui.ping(), { message: 'offline for test' }, false)
    await flush()
    assert.equal(ui.get('submit').disabled, true)
    assert.equal(ui.get('retry').disabled, false)
    assert.equal(ui.get('notice').textContent, 'offline for test')
    ui.fill()
    ui.submit()
    assert.equal(ui.requests('POST').length, 0)
    ui.get('retry').click()
    assert.equal(ui.requests('GET', `/api/plugin/${plugin.key}/ping`).length, 2)
    ui.respond(ui.ping(), ui.pingBody)
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), [])
    await flush()
    assert.equal(ui.get('submit').disabled, false)
    assert.equal(ui.get('notice').hidden, true)
  })

  test(`${plugin.key}: repeated context coalesces connection and a timed-out ping can be retried`, async t => {
    const ui = createUi(t, plugin)
    const timeouts = new Map()
    let nextTimer = 0
    ui.window.setTimeout = callback => { const id = ++nextTimer; timeouts.set(id, callback); return id }
    ui.window.clearTimeout = id => timeouts.delete(id)
    ui.context()
    ui.context()
    ui.context()
    assert.equal(ui.requests('GET', `/api/plugin/${plugin.key}/ping`).length, 1)
    assert.equal(ui.get('retry').disabled, true)
    const timeout = timeouts.values().next().value
    timeouts.clear()
    timeout()
    await flush()
    assert.equal(ui.get('notice').textContent, '插件请求超时')
    assert.equal(ui.get('submit').disabled, true)
    assert.equal(ui.get('retry').disabled, false)
    // A late success for the timed-out request must not revive it.
    ui.respond(ui.ping(), ui.pingBody)
    await flush()
    assert.equal(ui.requests('GET', plugin.listPath).length, 0)
    ui.get('retry').click()
    ui.respond(ui.ping(), ui.pingBody)
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), [])
    await flush()
    assert.equal(ui.get('submit').disabled, false)
    assert.equal(timeouts.size, 0, 'resolved bridge requests clear their deadlines')
  })

  test(`${plugin.key}: initial record load failure is visible and refresh retries`, async t => {
    const ui = createUi(t, plugin)
    ui.context()
    ui.respond(ui.ping(), ui.pingBody)
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), { message: 'list offline' }, false)
    await flush()
    assert.equal(ui.get('submit').disabled, true)
    assert.equal(ui.get('notice').textContent, 'list offline')
    ui.get('retry').click()
    ui.respond(ui.ping(), ui.pingBody)
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), [plugin.record])
    await flush()
    assert.equal(ui.get('submit').disabled, false)
    assert.equal(ui.get('records').querySelectorAll('.record').length, 1)
  })

  test(`${plugin.key}: deferred save captures the form and coalesces double submissions`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    ui.fill()
    const event = ui.submit()
    assert.equal(event.currentTarget, null, 'event currentTarget is cleared after dispatch, before the awaited reply')
    ui.submit()
    assert.equal(ui.requests('POST', plugin.listPath).length, 1)
    assert.equal(ui.get('submit').disabled, true)
    const post = ui.requests('POST', plugin.listPath)[0]
    assert.deepEqual(post.data.body, {
      [plugin.field]: '正常 — user entry', [plugin.state]: plugin.values[1], remark: '备注 — keep this note',
    })
    ui.respond(post, { id: 1 })
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), [plugin.record])
    await flush()
    assert.equal(ui.get('submit').disabled, false)
    assert.equal(ui.get('notice').textContent, plugin.saved)
    assert.equal(ui.window.document.querySelector(`[name="${plugin.field}"]`).value, '')
    assert.equal(ui.get('records').querySelectorAll('.record').length, 1)
  })

  test(`${plugin.key}: failed save preserves inputs and supports a same-form retry`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect([plugin.record])
    ui.fill()
    ui.submit()
    ui.respond(ui.requests('POST')[0], { message: 'write rejected' }, false)
    await flush()
    assert.equal(ui.get('notice').textContent, 'write rejected')
    assert.equal(ui.get('submit').disabled, false)
    assert.equal(ui.window.document.querySelector(`[name="${plugin.field}"]`).value, '正常 — user entry')
    assert.equal(ui.window.document.querySelector(`[name="${plugin.state}"]`).value, plugin.values[1])
    assert.equal(ui.window.document.querySelector('[name="remark"]').value, '备注 — keep this note')
    assert.equal(ui.get('records').querySelectorAll('.record').length, 1)
    ui.submit()
    assert.equal(ui.requests('POST').length, 2)
    ui.respond(ui.requests('POST')[1], { id: 2 })
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), [])
    await flush()
    assert.equal(ui.get('notice').textContent, plugin.saved)
  })

  test(`${plugin.key}: a committed save is not presented as a failed write when list refresh fails`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect([plugin.record])
    ui.fill()
    ui.submit()
    ui.respond(ui.requests('POST')[0], { id: 2 })
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), { message: 'list reload unavailable' }, false)
    await flush()
    assert.match(ui.get('notice').textContent, /已保存/, 'the user must know the POST was committed')
    assert.match(ui.get('notice').textContent, /list reload unavailable/)
    assert.equal(ui.get('records').querySelectorAll('.record').length, 1, 'retain the previous record view')
    assert.equal(ui.get('submit').disabled, false)
  })

  test(`${plugin.key}: writable fields do not lose a new draft when an older save completes`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    ui.fill()
    ui.submit()
    const fields = Array.from(ui.get('form').querySelectorAll('input,select,textarea'))
    const title = ui.window.document.querySelector(`[name="${plugin.field}"]`)
    const note = ui.window.document.querySelector('[name="remark"]')
    const fieldsLocked = fields.every(field => field.matches(':disabled'))
    if (!fieldsLocked) {
      // Native disabled fields are an acceptable alternative to preserving edits.
      assert.equal(title.matches(':disabled'), false)
      assert.equal(note.matches(':disabled'), false)
      title.value = 'new draft entered while saving'
      note.value = 'do not erase this next note'
    }
    ui.respond(ui.requests('POST')[0], { id: 1 })
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), [plugin.record])
    await flush()
    if (fieldsLocked) {
      assert.equal(title.value, '')
      assert.equal(fields.some(field => field.matches(':disabled')), false, 'unlock fields after save')
    } else {
      assert.equal(title.value, 'new draft entered while saving')
      assert.equal(note.value, 'do not erase this next note')
    }
  })

  test(`${plugin.key}: saving restores pre-existing field disabled states after a rejected write`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    ui.fill()
    const note = ui.window.document.querySelector('[name="remark"]')
    note.disabled = true
    ui.submit()
    assert.equal(ui.get('form').getAttribute('aria-busy'), 'true')
    assert.equal(ui.get('retry').disabled, true)
    assert.equal(ui.requests('POST')[0].data.body.remark, undefined)
    ui.respond(ui.requests('POST')[0], { message: 'write rejected' }, false)
    await flush()
    assert.equal(ui.get('form').getAttribute('aria-busy'), 'false')
    assert.equal(note.disabled, true, 'do not enable fields disabled before saving')
    assert.equal(ui.window.document.querySelector(`[name="${plugin.field}"]`).disabled, false)
    assert.equal(ui.window.document.querySelector(`[name="${plugin.state}"]`).disabled, false)
    assert.equal(ui.get('retry').disabled, false)
  })

  test(`${plugin.key}: locale changes during a pending save preserve locked values and business payload`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    ui.fill()
    ui.submit()
    ui.context({ locale: 'en-US' })
    assert.equal(ui.get('submit').textContent, 'Saving...')
    assert.equal(ui.window.document.querySelector(`[name="${plugin.state}"]`).value, plugin.values[1])
    assert.equal(ui.window.document.querySelector(`[name="${plugin.field}"]`).value, '正常 — user entry')
    assert.equal(ui.window.document.querySelector(`[name="${plugin.field}"]`).disabled, true)
    assert.equal(ui.requests('POST')[0].data.body[plugin.state], plugin.values[1])
    ui.respond(ui.requests('POST')[0], { message: 'write rejected' }, false)
    await flush()
    assert.equal(ui.get('submit').textContent, plugin.key === 'equipment-inspection' ? 'Save inspection' : 'Save issue')
    assert.equal(ui.window.document.querySelector(`[name="${plugin.field}"]`).value, '正常 — user entry')
  })

  test(`${plugin.key}: an overlapping refresh failure cannot be undone by save completion`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect([plugin.record])
    ui.fill()
    ui.submit()
    const post = ui.requests('POST')[0]
    ui.get('retry').click()
    // Implementations may block refresh while saving; if accepted, failed health must stay authoritative.
    const pings = ui.requests('GET', `/api/plugin/${plugin.key}/ping`)
    if (pings.length === 1) {
      assert.equal(ui.get('submit').disabled, true)
      ui.respond(post, { message: 'write rejected' }, false)
      await flush()
      assert.equal(ui.get('submit').disabled, false)
      return
    }
    ui.respond(pings.at(-1), { message: 'connection lost during save' }, false)
    await flush()
    assert.equal(ui.get('submit').disabled, true)
    ui.respond(post, { message: 'write rejected' }, false)
    await flush()
    assert.equal(ui.get('submit').disabled, true, 'save completion must not enable an unhealthy plugin')
    ui.submit()
    assert.equal(ui.requests('POST').length, 1)
  })

  test(`${plugin.key}: required-field validation cannot issue an empty POST`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    ui.submit()
    assert.equal(ui.requests('POST').length, 0)
    assert.equal(ui.get('submit').disabled, false)
  })

  test(`${plugin.key}: English switching keeps business values, input and record names intact`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect([plugin.record])
    ui.fill()
    ui.context({ locale: 'en-US' })
    const select = ui.window.document.querySelector(`[name="${plugin.state}"]`)
    assert.equal(ui.window.document.documentElement.lang, 'en-US')
    assert.deepEqual(Array.from(select.options, option => option.value), plugin.values)
    assert.deepEqual(Array.from(select.options, option => option.textContent), plugin.labels)
    assert.equal(select.value, plugin.values[1])
    assert.equal(ui.window.document.querySelector(`[name="${plugin.field}"]`).value, '正常 — user entry')
    assert.equal(ui.window.document.querySelector('[name="remark"]').value, '备注 — keep this note')
    assert.equal(ui.get('records').querySelector('strong').textContent, plugin.record[plugin.field])
    assert.match(ui.get('records').querySelector('.muted').textContent, /备注/)
    assert.equal(ui.get('records').querySelector('.tag').textContent, plugin.labels[1])
    assert.equal(ui.get('retry').getAttribute('aria-label'), 'Refresh')
    ui.submit()
    assert.equal(ui.requests('POST')[0].data.body[plugin.state], plugin.values[1])
    ui.respond(ui.requests('POST')[0], { message: 'test rejection' }, false)
    await flush()
    ui.context({ locale: 'zh-CN' })
    assert.deepEqual(Array.from(select.options, option => option.textContent), plugin.values)
    assert.equal(ui.get('retry').getAttribute('aria-label'), '刷新')
    assert.equal(select.value, plugin.values[1])
  })

  test(`${plugin.key}: an English-first load still posts the original state value`, async t => {
    const ui = createUi(t, plugin, { locale: 'en-US' })
    await ui.connect()
    ui.fill()
    ui.submit()
    assert.equal(ui.requests('POST')[0].data.body[plugin.state], plugin.values[1])
    ui.respond(ui.requests('POST')[0], { message: 'test rejection' }, false)
    await flush()
  })

  test(`${plugin.key}: empty-note UI fallback is translated without translating business notes`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect([{ ...plugin.record, remark: '' }, { ...plugin.record, remark: '备注' }])
    assert.match(ui.get('records').textContent, /无备注/)
    ui.context({ locale: 'en-US' })
    assert.match(ui.get('records').textContent, /No notes/)
    assert.match(ui.get('records').textContent, /备注/, 'a nonempty business remark is not UI copy')
    ui.context({ locale: 'zh-CN' })
    assert.match(ui.get('records').textContent, /无备注/)
    assert.doesNotMatch(ui.get('records').textContent, /No notes/)
  })

  test(`${plugin.key}: committed-save warning can change language without losing its error`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    ui.fill()
    ui.submit()
    ui.respond(ui.requests('POST')[0], { id: 1 })
    await flush()
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), { message: '插件请求超时' }, false)
    await flush()
    ui.context({ locale: 'en-US' })
    assert.equal(ui.get('notice').textContent, 'Saved. List refresh failed: Plugin request timed out')
    ui.context({ locale: 'zh-CN' })
    assert.equal(ui.get('notice').textContent, '已保存，列表刷新失败：插件请求超时')
  })

  test(`${plugin.key}: theme context changes actual surfaces and native color scheme`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    const { window } = ui
    assert.equal(window.getComputedStyle(window.document.body).backgroundColor, 'rgb(244, 247, 248)')
    assert.equal(window.getComputedStyle(window.document.querySelector('section')).backgroundColor, 'rgb(255, 255, 255)')
    assert.equal(window.getComputedStyle(window.document.documentElement).colorScheme, 'light')
    ui.context({ theme: 'dark' })
    assert.equal(window.getComputedStyle(window.document.body).backgroundColor,
      plugin.key === 'equipment-inspection' ? 'rgb(16, 22, 26)' : 'rgb(16, 19, 24)')
    assert.equal(window.getComputedStyle(window.document.querySelector('section')).backgroundColor,
      plugin.key === 'equipment-inspection' ? 'rgb(23, 30, 35)' : 'rgb(21, 26, 33)')
    assert.equal(window.getComputedStyle(window.document.documentElement).colorScheme, 'dark')
    ui.context({ theme: 'light' })
    assert.equal(window.getComputedStyle(window.document.body).backgroundColor, 'rgb(244, 247, 248)')
    assert.equal(window.getComputedStyle(window.document.documentElement).colorScheme, 'light')
  })

  test(`${plugin.key}: foreign window, wrong plugin and non-http context messages are ignored`, async t => {
    const ui = createUi(t, plugin)
    const baseline = ui.messages.length
    ui.context({}, { source: {} })
    ui.context({ pluginKey: 'another-plugin' })
    ui.context({}, { origin: 'null' })
    ui.context({}, { origin: 'file://app.example.test' })
    ui.context({}, { origin: 'data:text/html,test' })
    await flush()
    assert.equal(ui.messages.length, baseline)
    assert.equal(ui.get('submit').disabled, true)
    await ui.connect()
  })

  test(`${plugin.key}: forged responses cannot resolve a real pending request`, async t => {
    const ui = createUi(t, plugin)
    ui.context()
    const request = ui.ping()
    ui.respond(request, ui.pingBody, true, {}, { source: {} })
    ui.respond(request, ui.pingBody, true, {}, { origin: 'https://forged.example.test' })
    ui.respond(request, ui.pingBody, true, { pluginKey: 'another-plugin' })
    ui.respond(request, ui.pingBody, true, { requestId: 'invented-id' })
    ui.respond(request, ui.pingBody, true, { type: 'not-a-response' })
    await flush()
    assert.equal(ui.requests('GET', plugin.listPath).length, 0)
    assert.equal(ui.get('submit').disabled, true)
    ui.respond(request, ui.pingBody)
    await flush()
    assert.equal(ui.requests('GET', plugin.listPath).length, 1)
    ui.respond(ui.requests('GET', plugin.listPath).at(-1), [])
    await flush()
    assert.equal(ui.get('submit').disabled, false)
    // A replay after completion must have no side effects.
    ui.respond(request, { message: 'replayed failure' }, false)
    await flush()
    assert.equal(ui.get('submit').disabled, false)
    assert.notEqual(ui.get('notice').textContent, 'replayed failure')
  })

  test(`${plugin.key}: the established parent origin cannot be replaced by another context`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect()
    ui.context({ locale: 'en-US' }, { origin: 'https://forged.example.test' })
    assert.equal(ui.window.document.documentElement.lang, 'zh-CN')
    ui.fill()
    ui.submit()
    const post = ui.requests('POST')[0]
    assert.equal(post.targetOrigin, parentOrigin)
    ui.respond(post, { message: 'test rejection' }, false)
    await flush()
  })

  test(`${plugin.key}: record text is escaped rather than treated as HTML`, async t => {
    const ui = createUi(t, plugin)
    await ui.connect([{
      ...plugin.record,
      [plugin.field]: '<img src=x onerror="window.injected=true">',
      remark: '<script>window.injected=true</script>',
      createdAt: '<svg onload="window.injected=true">',
    }])
    assert.equal(ui.get('records').querySelectorAll('img,script,svg').length, 0)
    assert.equal(ui.get('records').querySelector('strong').textContent, '<img src=x onerror="window.injected=true">')
    assert.equal(ui.window.injected, undefined)
  })
}

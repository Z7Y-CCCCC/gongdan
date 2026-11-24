import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import { compileTemplate, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { localizeVueSource, uiI18nTransform } from './ui-i18n-transform.mjs'

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(import.meta.url)
const sourceFiles = directory => readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
  ? sourceFiles(path.join(directory, entry.name)) : entry.name.endsWith('.vue') ? [path.join(directory, entry.name)] : [])
const vueFiles = sourceFiles(path.join(frontend, 'src'))
const normalize = value => value.replace(/\s+/g, ' ').trim()
const catalogs = ['en-support', 'en-pages', 'en-shell', 'en-errors'].map(name => ({
  name, messages: JSON.parse(readFileSync(path.join(frontend, 'src/i18n/locales', name + '.json'), 'utf8'))
}))
const english = Object.fromEntries(Object.entries(Object.assign({}, ...catalogs.map(catalog => catalog.messages)))
  .map(([key, value]) => [normalize(key), value]))

// Exercise the real TypeScript translation functions without producing temporary
// files or adding a second, test-only implementation of the translation logic.
const storage = new Map()
const sandbox = vm.createContext({
  console, Intl, Date,
  localStorage: {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value))
  }
})
const modules = new Map()
function loadTypeScript(filename) {
  const absolute = path.resolve(filename)
  if (modules.has(absolute)) return modules.get(absolute)
  const exports = {}
  modules.set(absolute, exports)
  const compiled = ts.transpileModule(readFileSync(absolute, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true }
  }).outputText
  const load = specifier => {
    if (!specifier.startsWith('.')) return require(specifier)
    const resolved = path.resolve(path.dirname(absolute), specifier)
    return resolved.endsWith('.json') ? JSON.parse(readFileSync(resolved, 'utf8')) : loadTypeScript(resolved + '.ts')
  }
  vm.runInContext(`(function(require, exports) {\n${compiled}\n})`, sandbox, { filename: absolute })(load, exports)
  return exports
}
const i18n = loadTypeScript(path.join(frontend, 'src/i18n/index.ts'))

function transformed(template) {
  return localizeVueSource(`<template>${template}</template>`, 'contract.vue')
}
function render(template, context = {}, locale = 'en-US') {
  i18n.setUiLocale(locale)
  const source = parse(transformed(template)).descriptor.template.content
  const compiled = compileTemplate({ source, id: 'locale-contract', filename: 'contract.vue' })
  assert.deepEqual(compiled.errors, [])
  const code = ts.transpileModule(compiled.code, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const exports = {}
  new Function('require', 'exports', code)(require, exports)
  return exports.render({ $t: i18n.t, $date: i18n.formatUiDate, ...context }, [])
}
function nodes(vnode, predicate) {
  if (!vnode || typeof vnode !== 'object') return []
  const children = Array.isArray(vnode.children) ? vnode.children.flatMap(child => nodes(child, predicate)) : []
  return [...(predicate(vnode) ? [vnode] : []), ...children]
}
function textContent(vnode) {
  if (typeof vnode === 'string') return vnode
  if (!vnode || typeof vnode !== 'object') return ''
  return Array.isArray(vnode.children) ? vnode.children.map(textContent).join('') : String(vnode.children ?? '')
}

test('the Vite hook runs before Vue, transforms only project SFCs and leaves script-only files alone', () => {
  const plugin = uiI18nTransform()
  assert.equal(plugin.enforce, 'pre')
  assert.equal(plugin.transform('<template>保存</template>', 'library.js'), null)
  assert.equal(plugin.transform('<template>保存</template>', '/node_modules/library.vue'), null)
  assert.equal(plugin.transform('<template>保存</template>', 'page.vue?vue&type=template'), null)
  const script = '<script setup>const label = "保存"</script>'
  assert.equal(plugin.transform(script, 'page.vue').code, script)
  assert.match(plugin.transform('<template>保存</template>', 'page.vue').code, /\$t\(/)
})

test('the transform rejects malformed SFCs instead of silently generating broken UI', () => {
  assert.throws(() => localizeVueSource('<template><button>保存</template>', 'broken.vue'))
})

test('static trusted text and accessible attributes translate to English', () => {
  const root = render('<button title="保存" aria-label="保存"><span>保存</span></button>')
  assert.equal(root.props.title, english['保存'])
  assert.equal(root.props['aria-label'], english['保存'])
  assert.equal(textContent(root), english['保存'])
  assert.equal(textContent(render('<button>保存</button>', {}, 'zh-CN')), '保存')
})

test('implicit option values retain Chinese machine values while their UI labels translate', () => {
  for (const locale of ['zh-CN', 'en-US']) {
    const option = nodes(render('<select><option>自制</option><option>外购</option></select>',
      {}, locale), node => node.type === 'option')
    assert.deepEqual(option.map(node => node.props.value), ['自制', '外购'])
    assert.deepEqual(option.map(textContent), locale === 'zh-CN' ? ['自制', '外购'] : [english['自制'], english['外购']])
  }
})

test('explicit static and bound option values are never replaced with labels', () => {
  const option = nodes(render('<select><option value="IN_HOUSE">自制</option><option :value="code">外购</option></select>',
    { code: 'PURCHASED' }), node => node.type === 'option')
  assert.deepEqual(option.map(node => node.props.value), ['IN_HOUSE', 'PURCHASED'])
  assert.deepEqual(option.map(textContent), [english['自制'], english['外购']])
})

test('dynamic implicit options retain authored names and the exact original value', () => {
  const option = render('<option>{{ item.name }}</option>', { item: { name: '保存' } })
  assert.equal(option.props.value, '保存')
  assert.equal(textContent(option), '保存')
})

test('mixed option content keeps the untranslated original wire value', () => {
  const option = render('<option>工序 {{ row.name }}</option>', { row: { name: '保存' } })
  assert.equal(option.props.value, '工序 保存')
  assert.equal(textContent(option), i18n.t('工序 {0}', ['保存']))
})

test('table headers retain stable Chinese identifiers for column settings', () => {
  const root = render('<table><thead><tr><th>操作</th><th data-ui-label="wire-column">工序</th></tr></thead></table>')
  const headers = nodes(root, node => node.type === 'th')
  assert.equal(headers[0].props['data-ui-label'], '操作')
  assert.equal(textContent(headers[0]), english['操作'])
  assert.equal(headers[1].props['data-ui-label'], 'wire-column')
})

test('dynamic table identifiers use the authored title rather than a translated rendering', () => {
  const header = render('<th>{{ row.title }}</th>', { row: { title: '保存' } })
  assert.equal(header.props['data-ui-label'], '保存')
  assert.equal(textContent(header), '保存')
})

test('input wire values, v-model expressions, click handlers and script source remain untouched', () => {
  const script = '<script setup>const CSV_HEADERS = ["产品名称", "数量"]; const data = "保存"</script>'
  const source = `<template><input v-model="record.title" :value="record.name" placeholder="请输入密码" /><button @click="save('保存')">保存</button></template>${script}`
  const output = localizeVueSource(source, 'contract.vue')
  assert.ok(output.endsWith(script))
  assert.match(output, /v-model="record\.title"/)
  assert.match(output, /:value="record\.name"/)
  assert.match(output, /@click="save\('保存'\)"/)
  assert.match(output, /:placeholder="\$t\(/)
})

test('authored record names, titles, descriptions, messages and display names are not translated', () => {
  const fields = ['record.title', 'item.name', 'row.content', 'entry.description', 'order.remark', 'displayName']
  const context = { record: { title: '保存' }, item: { name: '保存' }, row: { content: '保存' },
    entry: { description: '保存' }, order: { remark: '保存' }, displayName: '保存' }
  for (const field of fields) {
    assert.equal(textContent(render(`<h2>{{ ${field} }}</h2>`, context)), '保存', field)
    assert.equal(render(`<button :title="${field}">OK</button>`, context).props.title, '保存', field)
  }
})

test('logical and nullish UI fallbacks translate only the fallback, never the authored value', () => {
  for (const operator of ['||', '??']) {
    const template = `<h2>{{ row.title ${operator} '插件' }}</h2>`
    assert.equal(textContent(render(template, { row: { title: '保存' } })), '保存', operator)
    assert.equal(textContent(render(template, { row: { title: null } })), english['插件'], operator)
    const pluginTemplate = `<h2>{{ selectedRuntimePlugin?.name ${operator} '插件' }}</h2>`
    assert.equal(textContent(render(pluginTemplate, { selectedRuntimePlugin: { name: '保存' } })), '保存', operator)
    assert.equal(textContent(render(pluginTemplate, { selectedRuntimePlugin: null })), english['插件'], operator)
  }
})

test('explicit chat-preview fallback localizes only UI copy, preserving an authored message matching a catalog key', () => {
  const template = '<p>{{ contact.lastMessage || $t(\'开始一段新的沟通\') }}</p>'
  assert.equal(textContent(render(template, { contact: { lastMessage: '保存' } })), '保存')
  assert.equal(textContent(render(template, { contact: { lastMessage: '' } })), english['开始一段新的沟通'])
})

test('condition predicates remain machine comparisons while both UI branches translate', () => {
  const template = '<button :title="state === \'保存\' ? \'编辑\' : \'删除\'">{{ state === \'保存\' ? \'编辑\' : \'删除\' }}</button>'
  const source = transformed(template)
  assert.match(source, /state === '保存'/)
  for (const [state, expected] of [['保存', '编辑'], ['other', '删除']]) {
    const root = render(template, { state })
    assert.equal(root.props.title, english[expected])
    assert.equal(textContent(root), english[expected])
  }
})

test('Chinese UI concatenations use numbered patterns without translating authored parameters', () => {
  const collected = []
  const source = localizeVueSource('<template><p>{{ "新建" + row.name }}</p></template>', 'contract.vue',
    (key, filename, kind) => collected.push({ key, filename, kind }))
  assert.ok(collected.some(item => item.key === '新建{0}' && item.kind === 'pattern'))
  assert.match(source, /\$t\("新建\{0\\u007d", \[row\.name\]\)/)
  assert.equal(textContent(render('<p>{{ "新建" + row.name }}</p>', { row: { name: '保存' } })),
    i18n.t('新建{0}', ['保存']))
})

test('mixed text interpolation preserves customer values and interpolates only trusted patterns', () => {
  const root = render('<p>新建{{ row.name }}</p>', { row: { name: '<script>保存</script>' } })
  assert.equal(textContent(root), i18n.t('新建{0}', ['<script>保存</script>']))
  assert.equal(nodes(root, node => node.type === 'script').length, 0)
})

test('template-string UI labels translate static metadata parameters but preserve authored record titles', () => {
  const template = '<button :aria-label="`关闭 ${tab.label}`">OK</button>'
  assert.equal(render(template, { tab: { label: '工单' } }).props['aria-label'], `Close ${english['工单']}`)
  const authoredTemplate = '<button :aria-label="`关闭 ${record.title}`">OK</button>'
  assert.equal(render(authoredTemplate, { record: { title: '保存' } }).props['aria-label'], 'Close 保存')
})

test('actual script-generated average-cycle formatter localizes its unit instead of relying on name heuristics', () => {
  const file = path.join(frontend, 'src/components/PerformanceSection.vue')
  const descriptor = parse(readFileSync(file, 'utf8'), { filename: file }).descriptor
  const tree = ts.createSourceFile('performance.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const formatter = tree.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'formatValue')
  assert.ok(formatter)
  const code = ts.transpileModule(`(${formatter.getText(tree)})`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  const data = { value: { averageCycleDays: 0, defectRatePercent: 2.5 } }
  const hasError = { value: false }
  const formatValue = new Function('t', 'data', 'hasError', `return ${code}`)(i18n.t, data, hasError)
  i18n.setUiLocale('en-US')
  assert.equal(formatValue({ key: 'averageCycleDays' }), '0.0 days')
  assert.equal(formatValue({ key: 'defectRatePercent', suffix: '%' }), '2.50%')
  i18n.setUiLocale('zh-CN')
  assert.equal(formatValue({ key: 'averageCycleDays' }), '0.0天')
  hasError.value = true
  assert.equal(formatValue({ key: 'averageCycleDays' }), '--')
})

test('macro closing braces do not prematurely terminate Vue moustaches', () => {
  const template = '<p>${工单.{{ fieldName }}}</p>'
  const output = transformed(template)
  assert.match(output, /\\u007d/)
  assert.equal(textContent(render(template, { fieldName: '产品名称' })), '${工单.产品名称}')
})

test('escaped quotes, ampersands, angle brackets and brace strings produce valid attributes', () => {
  const root = render('<button title="复制 &quot;工单&quot; &amp; &lt;字段&gt; }">保存</button>')
  assert.equal(root.props.title, '复制 "工单" & <字段> }')
})

test('explicit $t calls are not wrapped a second time', () => {
  const source = transformed('<button :title="$t(\'保存\')">{{ $t(\'保存\') }}</button>')
  assert.equal((source.match(/\$t\(/g) || []).length, 2)
  assert.equal(textContent(render('<button>{{ $t(\'保存\') }}</button>')), english['保存'])
})

test('localizing an already-localized template does not change expressions or add duplicate identifiers', () => {
  const output = transformed('<table><tr><th>操作</th><td><select><option>自制</option></select></td></tr></table>')
  assert.equal(localizeVueSource(output, 'contract.vue'), output)
})

test('date rendering uses the configured timezone without rewriting input values', () => {
  const source = transformed('<div><span>{{ row.createdAt }}</span><input :value="row.createdAt" /></div>')
  assert.match(source, /\$date\(row\.createdAt\)/)
  assert.match(source, /:value="row\.createdAt"/)
  const root = render('<div><span>{{ row.createdAt }}</span><input :value="row.createdAt" /></div>',
    { row: { createdAt: '2026-11-24T09:30:00' } })
  assert.equal(textContent(nodes(root, node => node.type === 'span')[0]), '2026-11-24 09:30')
  assert.equal(nodes(root, node => node.type === 'input')[0].props.value, '2026-11-24T09:30:00')
})

test('the real translation function keeps unknown business strings and non-string values unchanged', () => {
  i18n.setUiLocale('en-US')
  const data = { name: '客户原文', value: 1 }
  assert.equal(i18n.t(data), data)
  assert.equal(i18n.t(42), 42)
  assert.equal(i18n.t(null), null)
  assert.equal(i18n.t('客户原文  A\nB'), '客户原文  A\nB')
  assert.equal(i18n.t('新密码不能为空；客户原文'), '新密码不能为空；客户原文')
})

test('known compound errors translate only when all original segments are recognized', () => {
  i18n.setUiLocale('en-US')
  assert.equal(i18n.t('新密码不能为空；两次输入的密码不一致'),
    i18n.t('新密码不能为空') + '; ' + i18n.t('两次输入的密码不一致'))
})

test('the real translation function preserves account-detail newlines and authored parameter values', () => {
  i18n.setUiLocale('en-US')
  const value = i18n.t('账号：{0}\n姓名：{1}\n角色：{2}\n部门：{3}\n密码：{4}',
    ['qa', '保存', 'ADMIN', '制造', 'fixture-not-a-credential'])
  assert.ok(value.includes('Name: 保存'))
  assert.equal((value.match(/\n/g) || []).length, 4)
  assert.ok(value.includes('制造'))
  assert.ok(value.includes('fixture-not-a-credential'))
})

test('locale switching persists only supported locales and can return to Chinese', () => {
  assert.equal(i18n.setUiLocale('en-US'), true)
  assert.equal(storage.get('heihu_locale'), 'en-US')
  assert.equal(i18n.setUiLocale('invalid'), false)
  assert.equal(storage.get('heihu_locale'), 'en-US')
  assert.equal(i18n.setUiLocale('zh-CN'), true)
  assert.equal(i18n.t('保存'), '保存')
  assert.equal(i18n.t('新建{0}', ['保存']), '新建保存')
})

for (const { name, messages } of catalogs) {
  test(`${name}: every translation is nonempty and preserves all numbered placeholders`, () => {
    const placeholders = value => [...new Set(value.match(/\{\d+\}/g) || [])].sort()
    for (const [key, value] of Object.entries(messages)) {
      assert.equal(typeof value, 'string', key)
      assert.ok(value.trim(), `${name}: ${key}`)
      assert.deepEqual(placeholders(value), placeholders(key), `${name}: ${key}`)
    }
  })
}

test('all recorded template, UI-attribute and conditional keys have English catalog coverage', () => {
  const missing = []
  for (const file of vueFiles) {
    localizeVueSource(readFileSync(file, 'utf8'), file, (key, filename, kind) => {
      if (!english[normalize(key)]) missing.push({ key, filename: path.relative(frontend, filename), kind })
    })
  }
  assert.deepEqual(missing, [])
})

for (const file of vueFiles) {
  const relative = path.relative(frontend, file).replaceAll('\\', '/')
  test(`${relative}: localized SFC parses and its template compiles`, () => {
    const output = localizeVueSource(readFileSync(file, 'utf8'), file)
    const parsed = parse(output, { filename: file })
    assert.deepEqual(parsed.errors, [])
    if (!parsed.descriptor.template) return
    const compiled = compileTemplate({ source: parsed.descriptor.template.content, filename: file, id: relative })
    assert.deepEqual(compiled.errors, [])
  })
}

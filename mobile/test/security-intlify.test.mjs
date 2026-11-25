import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { after, test } from 'node:test'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { parseFragment } from 'parse5'

const require = createRequire(import.meta.url)
const mobile = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const vendor = path.join(mobile, 'vendor/uni-intlify-core')
const provenance = JSON.parse(fs.readFileSync(path.join(vendor, 'PROVENANCE.json'), 'utf8'))
const lock = JSON.parse(fs.readFileSync(path.join(mobile, 'package-lock.json'), 'utf8'))
const cjs = require('@intlify/core-base')
const esm = await import('@intlify/core-base')
const shared = require('@intlify/shared')
const temporaryDirectories = []
const hash = value => createHash('sha256').update(value).digest('hex')

function context(core, messages, options = {}) {
  // Use the actual fixed upstream parser/compiler/fallback implementation.
  core.registerMessageCompiler(core.compile)
  core.registerMessageResolver(core.resolveValue)
  core.registerLocaleFallbacker(core.fallbackWithLocaleChain)
  return core.createCoreContext({
    locale: 'en', fallbackLocale: 'en', messages: { en: messages },
    escapeParameter: true, warnHtmlMessage: false, missingWarn: false,
    fallbackWarn: false, ...options
  })
}

function quietly(operation) {
  const warn = console.warn
  console.warn = () => {}
  try { return operation() } finally { console.warn = warn }
}

function elements(fragment) {
  const result = []
  function walk(node) {
    if (node.tagName) result.push(node)
    for (const child of node.childNodes || []) walk(child)
    if (node.content) walk(node.content)
  }
  walk(parseFragment(fragment))
  return result
}

function attributes(node) {
  return Object.fromEntries(node.attrs.map(attribute => [attribute.name.toLowerCase(), attribute.value]))
}

after(() => {
  for (const directory of temporaryDirectories) {
    const target = path.resolve(directory)
    assert.equal(path.dirname(target), vendor)
    assert.ok(path.basename(target).startsWith('.intlify-test-'))
    fs.rmSync(target, { recursive: true, force: true })
  }
})

test('facade is a disclosed local package backed by exact official fixed distributions', () => {
  const manifest = require('@intlify/core-base/package.json')
  assert.equal(manifest.name, '@gongdan/uni-intlify-core-compat')
  assert.equal(manifest.version, '1.0.0')
  assert.equal(manifest.private, true)
  assert.equal(provenance.facade.name, manifest.name)
  assert.equal(provenance.localName, manifest.name)
  assert.equal(manifest.dependencies['intlify-core-modern'], 'npm:@intlify/core-base@11.4.23')
  assert.equal(cjs.VERSION, '11.4.23')
  assert.equal(esm.VERSION, '11.4.23')
  assert.match(fs.readFileSync(path.join(vendor, 'UPSTREAM_LICENSE'), 'utf8'), /MIT License/)
  for (const metadata of provenance.packages) {
    const alias = metadata.name === '@intlify/core-base' ? 'intlify-core-modern' : metadata.name
    const directory = path.dirname(require.resolve(`${alias}/package.json`))
    const installed = JSON.parse(fs.readFileSync(path.join(directory, 'package.json'), 'utf8'))
    assert.equal(installed.name, metadata.name)
    assert.equal(installed.version, '11.4.23')
    const edges = Object.entries(lock.packages).filter(([entry, info]) =>
      (entry === `node_modules/${alias}` || entry.endsWith(`/node_modules/${alias}`)) && info.version === metadata.version)
    assert.ok(edges.length, `missing official fixed lock entry: ${alias}`)
    for (const [entry, info] of edges) {
      assert.equal(info.integrity, metadata.integrity, entry)
      assert.equal(info.resolved, metadata.tarball, entry)
    }
    for (const [filename, expected] of Object.entries(metadata.distHashes)) {
      assert.equal(hash(fs.readFileSync(path.join(directory, 'dist', filename))), expected, `${alias}/${filename}`)
    }
  }
  assert.deepEqual(provenance.upstreamFixes.map(fix => [fix.ghsa, fix.firstFixed]), [
    ['GHSA-83rm-6c8r-g728', '11.4.14'], ['GHSA-8rc2-428q-7ww3', '11.4.14']
  ])
  // A facade must delegate rather than retain a renamed vulnerable translator.
  const modern = require('intlify-core-modern')
  for (const name of ['translate', 'compile', 'createCoreContext', 'createMessageContext', 'resolveValue']) {
    assert.equal(cjs[name], modern[name], name)
  }
})

for (const [distribution, core] of [['CommonJS', cjs], ['ESM', esm]]) {
  test(`${distribution}: list and named HTML are both escaped in the same real translation`, () => {
    const inputList = '<img src=x onerror=__local_intlify_marker__>'
    const inputNamed = '<b data-intlify-marker="1">named</b>'
    const list = Object.freeze([inputList])
    const named = Object.freeze({ name: inputNamed })
    const ctx = context(core, { mixed: 'list {0}; named {name}' })
    const result = quietly(() => core.translate(ctx, 'mixed', list, Object.freeze({ named })))
    assert.equal(result, `list ${shared.escapeHtml(inputList)}; named ${shared.escapeHtml(inputNamed)}`)
    assert.deepEqual(list, [inputList])
    assert.deepEqual(named, { name: inputNamed })
    const parsed = parseFragment(result)
    assert.equal(parsed.childNodes.length, 1)
    assert.equal(parsed.childNodes[0].nodeName, '#text')
    assert.equal(parsed.childNodes[0].value, `list ${inputList}; named ${inputNamed}`)
    assert.deepEqual(elements(result), [])
  })

  test(`${distribution}: frozen named values remain unchanged and are not escaped twice on reuse`, () => {
    const named = Object.freeze({ name: '<span title="\'&">same</span>' })
    const ctx = context(core, { named: 'hello {name}', mixed: '{0} / {name}' })
    const expected = `hello ${shared.escapeHtml(named.name)}`
    for (let index = 0; index < 3; index++) {
      assert.equal(quietly(() => core.translate(ctx, 'named', named)), expected)
      assert.equal(quietly(() => core.translate(ctx, 'mixed', ['ok'], { named })), `ok / ${shared.escapeHtml(named.name)}`)
      assert.equal(named.name, '<span title="\'&">same</span>')
    }
  })

  test(`${distribution}: srcdoc itself is neutralized after real interpolation and HTML5 parsing`, () => {
    // These are inert parser fixtures: no browser or event handler is executed.
    const inner = '<svg onload="__local_intlify_marker__"></svg>'
    for (const [key, message] of Object.entries({
      double: '<iframe title="frame" srcdoc="{document}"></iframe>',
      single: "<iframe title='frame' SrCdOc='{document}'></iframe>",
      bare: '<iframe title="frame" srcdoc={document}></iframe>'
    })) {
      const ctx = context(core, { [key]: message })
      const result = quietly(() => core.translate(ctx, key, { document: inner }))
      const frame = elements(result).find(node => node.tagName === 'iframe')
      assert.ok(frame, result)
      const attrs = attributes(frame)
      assert.equal(attrs.title, 'frame')
      assert.equal(Object.hasOwn(attrs, 'srcdoc'), false, result)
      assert.ok(Object.keys(attrs).some(name => name.includes('&#115;')), result)
      assert.equal(elements(result).some(node => node.tagName === 'svg'), false)
    }
  })

  test(`${distribution}: quote-shaped values do not create event attributes`, () => {
    const ctx = context(core, {
      double: '<div title="{value}">content</div>',
      single: "<div title='{value}'>content</div>"
    })
    for (const [key, value] of [
      ['double', '" onfocus="__local_intlify_marker__" data-marker="'],
      ['single', "' onfocus='__local_intlify_marker__' data-marker='"]
    ]) {
      const result = quietly(() => core.translate(ctx, key, Object.freeze({ value })))
      const nodes = elements(result)
      assert.equal(nodes.length, 1)
      assert.deepEqual(attributes(nodes[0]), { title: value })
      assert.equal(nodes[0].childNodes[0].value, 'content')
    }
  })

  test(`${distribution}: normal strings, plural numbers and explicit escaping options preserve their contracts`, () => {
    const ctx = context(core, {
      greeting: 'Hello {name}', list: '{0} / {1}', plural: 'no items | one item | {n} items',
      literal: '<strong>{name}</strong>'
    })
    assert.equal(core.translate(ctx, 'greeting', { name: 'Ethan' }), 'Hello Ethan')
    assert.equal(core.translate(ctx, 'list', [7, '件']), '7 / 件')
    assert.equal(core.translate(ctx, 'plural', 2), '2 items')
    assert.equal(quietly(() => core.translate(ctx, 'literal', { name: '<b>plain</b>' })),
      `<strong>${shared.escapeHtml('<b>plain</b>')}</strong>`)
    // Escaping is an explicit upstream option, not a claim that every message is safe HTML.
    assert.equal(core.translate(ctx, 'greeting', { name: '<b>trusted</b>' }, { escapeParameter: false }), 'Hello <b>trusted</b>')
  })
}

test('flat dictionary bridge preserves dotted keys, scalar collisions, ASTs and own-property boundaries', () => {
  const nested = { 'section.hello': 'Hello', section: { existing: 'keep' }, scalar: 'value', 'scalar.child': 'literal' }
  assert.equal(cjs.handleFlatJson(nested), nested)
  assert.equal(nested.section.hello, 'Hello')
  assert.equal(nested.section.existing, 'keep')
  assert.equal(Object.hasOwn(nested, 'section.hello'), false)
  assert.equal(nested.scalar, 'value')
  assert.equal(nested['scalar.child'], 'literal')
  const compiler = require('@intlify/message-compiler')
  const ast = compiler.baseCompile('Hello {name}', { jit: true, location: false }).ast
  assert.equal(cjs.isMessageAST(ast), true)
  const snapshot = JSON.stringify(ast)
  assert.equal(cjs.handleFlatJson(ast), ast)
  const astCollision = { ast, 'ast.type': 'keep literal' }
  cjs.handleFlatJson(astCollision)
  assert.equal(JSON.stringify(ast), snapshot)
  assert.equal(astCollision['ast.type'], 'keep literal')
  const ancestor = { section: { original: 'unchanged' } }
  const inherited = Object.assign(Object.create(ancestor), { 'section.safe': 'own' })
  cjs.handleFlatJson(inherited)
  assert.equal(Object.hasOwn(inherited, 'section'), true)
  assert.equal(inherited.section.safe, 'own')
  assert.deepEqual(ancestor.section, { original: 'unchanged' })
  for (const key of ['__proto__.intlifyMarker', 'constructor.prototype.intlifyMarker', 'safe.prototype.intlifyMarker']) {
    assert.throws(() => cjs.handleFlatJson(JSON.parse(JSON.stringify({ [key]: 'inert' }))), /Unsafe flat locale key/)
  }
  assert.equal({}.intlifyMarker, undefined)
})

test('legacy compileToFunction and getLocaleChain use the same fixed official implementations', () => {
  for (const core of [cjs, esm]) {
    assert.equal(core.compileToFunction, core.compile)
    assert.equal(core.getLocaleChain, core.fallbackWithLocaleChain)
    const ctx = context(core, { greeting: 'Hello {name}' }, { locale: 'en-US', fallbackLocale: 'en' })
    assert.deepEqual(core.getLocaleChain(ctx, 'en', 'en-US'), ['en-US', 'en'])
    assert.equal(core.translate(ctx, 'greeting', { name: 'Ethan' }), 'Hello Ethan')
  }
})

function uniRuntimeSources() {
  return Object.keys(lock.packages)
    .filter(entry => entry === 'node_modules/@dcloudio/uni-cli-shared' || entry.endsWith('/node_modules/@dcloudio/uni-cli-shared'))
    .map(entry => path.join(mobile, entry, 'lib/vue-i18n/dist/vue-i18n.runtime.esm-bundler.js'))
}

test('every installed uni Vue-I18n importer has all real core/shared/devtools symbols', () => {
  const sources = uniRuntimeSources()
  assert.ok(sources.length, 'no actual uni runtime found')
  const namespaces = { '@intlify/core-base': esm, '@intlify/shared': shared, '@intlify/vue-devtools': require('@intlify/vue-devtools') }
  for (const filename of sources) {
    const text = fs.readFileSync(filename, 'utf8')
    for (const [name, namespace] of Object.entries(namespaces)) {
      const expression = new RegExp(`import \\{([^}]+)\\} from ['"]${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}['"]`)
      const found = expression.exec(text)
      assert.ok(found, `${filename}: missing ${name} import inventory`)
      for (const entry of found[1].split(',')) {
        const exported = entry.trim().split(/\s+as\s+/)[0]
        assert.ok(Object.hasOwn(namespace, exported), `${filename}: missing ${name}.${exported}`)
      }
    }
  }
})

test('unmodified vendored uni Vue-I18n actually translates in composer and legacy modes', async () => {
  const source = fs.readFileSync(uniRuntimeSources()[0], 'utf8')
  // This pinned uni runtime deliberately does not compile raw strings. Its
  // verified custom compiler returns the source literally; real interpolation
  // uses catalogs precompiled at build time. Do not enable a second compiler.
  assert.match(source, /messageCompiler: function compileToFunction\(source\)\s*\{\s*return \(ctx\) => \{\s*return ctx\.normalize\(\[source\]\);/)
  const directory = fs.mkdtempSync(path.join(vendor, '.intlify-test-'))
  temporaryDirectories.push(directory)
  const filename = path.join(directory, 'uni-runtime.mjs')
  // Only the filename extension changes for native Node ESM; library bytes do not.
  fs.writeFileSync(filename, source)
  assert.equal(hash(fs.readFileSync(filename)), hash(source))
  const runtime = await import(pathToFileURL(filename).href)
  esm.registerMessageCompiler(esm.compile)
  esm.registerMessageResolver(esm.resolveValue)
  esm.registerLocaleFallbacker(esm.fallbackWithLocaleChain)
  for (const legacy of [false, true]) {
    const i18n = runtime.createI18n({
      legacy, locale: 'en-US', fallbackLocale: 'en', flatJson: true,
      escapeParameter: true, escapeParameterHtml: true,
      warnHtmlMessage: false, warnHtmlInMessage: 'off', missingWarn: false, fallbackWarn: false,
      messages: { en: {
        'section.hello': esm.compile('Hello {name}', { warnHtmlMessage: false }),
        mixed: esm.compile('{0} / {name}', { warnHtmlMessage: false }),
        listOnly: esm.compile('List {0}', { warnHtmlMessage: false }),
        raw: 'Literal {name}', section: { existing: 'keep' }
      } }
    })
    const translator = i18n.global
    assert.equal(quietly(() => translator.t('section.hello', { name: 'Ethan' })), 'Hello Ethan')
    assert.equal(quietly(() => translator.t('raw', { name: 'Ethan' })), 'Literal {name}')
    assert.equal(translator.te('section.hello', 'en'), true)
    assert.equal(translator.tm('section').existing, 'keep')
    const named = Object.freeze({ name: '<b>named</b>' })
    assert.equal(quietly(() => translator.t('section.hello', named)), `Hello ${shared.escapeHtml(named.name)}`)
    assert.equal(quietly(() => translator.t('listOnly', ['<i>list</i>'])), `List ${shared.escapeHtml('<i>list</i>')}`)
    // v9's legacy public t accepts a list OR named values, not a v11 options
    // object. Its underlying actual composer supports the joint options path.
    const composer = legacy ? translator.__composer : translator
    const result = quietly(() => composer.t('mixed', ['<i>list</i>'], { named }))
    assert.equal(result, `${shared.escapeHtml('<i>list</i>')} / ${shared.escapeHtml(named.name)}`)
    assert.deepEqual(named, { name: '<b>named</b>' })
  }
})

test('independent uni-i18n formatter retains ordinary locale, named and list behavior', () => {
  const uni = require('@dcloudio/uni-i18n')
  const i18n = uni.initVueI18n('en', {
    en: { hello: 'Hello {name}', list: '{0} / {1}' },
    'zh-Hans': { hello: '你好 {name}', list: '{0} / {1}' }
  }, 'en')
  assert.equal(i18n.t('hello', { name: 'Ethan' }), 'Hello Ethan')
  assert.equal(i18n.t('list', [7, '件']), '7 / 件')
  i18n.setLocale('zh-Hans')
  assert.equal(i18n.t('hello', { name: 'Ethan' }), '你好 Ethan')
  assert.equal(i18n.getLocale(), 'zh-Hans')
  // This separate formatter is not advertised as an HTML sanitizer.
  assert.equal(i18n.f('工单 {code}', { code: 'QA-042' }), '工单 QA-042')
})

test('production CommonJS facade still uses fixed named/list and srcdoc behavior', () => {
  const probe = `
    const assert = require('node:assert/strict');
    const c = require('@intlify/core-base');
    const s = require('@intlify/shared');
    c.registerMessageCompiler(c.compile); c.registerMessageResolver(c.resolveValue);
    c.registerLocaleFallbacker(c.fallbackWithLocaleChain);
    const context = c.createCoreContext({locale:'en', fallbackLocale:'en', escapeParameter:true,
      warnHtmlMessage:false, missingWarn:false, fallbackWarn:false,
      messages:{en:{mixed:'{0} / {name}', frame:'<iframe srcdoc="{document}"></iframe>'}}});
    const named=Object.freeze({name:'<b>named</b>'});
    assert.equal(c.translate(context,'mixed',['<i>list</i>'],{named}), s.escapeHtml('<i>list</i>')+' / '+s.escapeHtml(named.name));
    const frame=c.translate(context,'frame',{document:'<svg onload=__local_intlify_marker__></svg>'});
    assert.ok(frame.includes('&#115;rcdoc='));
    assert.equal(named.name,'<b>named</b>');
    assert.equal(c.VERSION,'11.4.23');
    process.stdout.write(JSON.stringify({version:c.VERSION,production:process.env.NODE_ENV,passed:true}));
  `
  const result = spawnSync(process.execPath, ['-e', probe], {
    cwd: mobile, encoding: 'utf8', env: { ...process.env, NODE_ENV: 'production' }, timeout: 30000
  })
  assert.equal(result.error, undefined)
  assert.equal(result.status, 0, result.stderr || result.stdout)
  assert.deepEqual(JSON.parse(result.stdout), { version: '11.4.23', production: 'production', passed: true })
})

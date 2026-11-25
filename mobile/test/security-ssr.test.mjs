import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import vm from 'node:vm'
import { after, test } from 'node:test'
import { parseFragment } from 'parse5'

const require = createRequire(import.meta.url)
const mobile = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const rendererDir = path.join(mobile, 'vendor/uni-server-renderer')
const sharedDir = path.join(mobile, 'vendor/uni-vue-shared')
const provenance = {
  renderer: JSON.parse(fs.readFileSync(path.join(rendererDir, 'PROVENANCE.json'), 'utf8')),
  shared: JSON.parse(fs.readFileSync(path.join(sharedDir, 'PROVENANCE.json'), 'utf8')),
}
const OLD_RE = '/[>/="\'\\u0009\\u000a\\u000c\\u0020]/'
const FIXED_RE = '/[>/="\'\\u0009\\u000a\\u000c\\u000d\\u0020]/'
const SHARED_ALIAS = '@vue/shared'
const attackKey = 'x\rautofocus\ronfocus'
const inertHandler = '__local_ssr_regression_marker__'
const temporaryDirs = []
const hash = value => createHash('sha256').update(value).digest('hex')

function source(group, relative, original = false) {
  const dir = group === 'shared' ? sharedDir : rendererDir
  let text = fs.readFileSync(path.join(dir, relative), 'utf8')
  const patch = provenance[group].patches.find(item => item.path === relative)
  if (patch) {
    assert.equal(hash(text), patch.patchedSha256, `Patched file changed: ${relative}`)
    if (original) {
      if (patch.kind.startsWith('official-add-CR-blacklist')) {
        assert.equal(text.split(FIXED_RE).length - 1, 1)
        text = text.replace(FIXED_RE, OLD_RE)
      } else throw new Error(`Unexpected non-security patch: ${patch.kind}`)
      assert.equal(hash(text), patch.originalSha256, `Not exact upstream bytes: ${relative}`)
    }
  }
  if (original || !patch) assert.equal(hash(text), provenance[group].upstreamFileSha256[relative])
  return text
}

function publishedCommonJs(text, shared) {
  const module = { exports: {} }
  const load = name => name === SHARED_ALIAS ? shared : require(name)
  // Verified official library code, not replacement helpers or untrusted input.
  const compiled = vm.runInThisContext(`(function(exports, require, module, __filename, __dirname) {\n${text}\n})`)
  compiled(module.exports, load, module, path.join(rendererDir, 'verified-upstream.cjs'), rendererDir)
  return module.exports
}

function originalCommonJs(relative = 'dist/server-renderer.cjs.js') {
  const originalShared = publishedCommonJs(source('shared', 'dist/shared.cjs.js', true))
  return publishedCommonJs(source('renderer', relative, true), originalShared)
}

function htmlAttributes(output, tag = 'div') {
  const element = parseFragment(`<${tag}${output}>body</${tag}>`).childNodes.find(node => node.tagName === tag)
  assert.ok(element)
  return Object.fromEntries(element.attrs.map(attribute => [attribute.name, attribute.value]))
}

async function quiet(operation) {
  const saved = { warn: console.warn, error: console.error }
  console.warn = () => {}
  console.error = () => {}
  try { return await operation() } finally { Object.assign(console, saved) }
}

async function esmModule(group, relative, original = false, sharedModule) {
  const dir = fs.mkdtempSync(path.join(rendererDir, '.ssr-esm-'))
  temporaryDirs.push(dir)
  let text = source(group, relative, original)
  if (sharedModule) {
    // Native Node needs .mjs for the bundler-format file. Library code is unchanged
    // except redirecting its dependency to the verified original shared module.
    fs.writeFileSync(path.join(dir, 'original-shared.mjs'), sharedModule)
    assert.ok(text.includes("'@vue/shared'"), 'baseline renderer must import its original shared dependency')
    text = text.replaceAll("'@vue/shared'", "'./original-shared.mjs'")
  }
  const target = path.join(dir, 'distribution.mjs')
  fs.writeFileSync(target, text)
  return import(pathToFileURL(target).href)
}

after(() => {
  for (const directory of temporaryDirs) {
    const target = path.resolve(directory)
    assert.equal(path.dirname(target), rendererDir)
    assert.ok(path.basename(target).startsWith('.ssr-esm-'))
    fs.rmSync(target, { recursive: true, force: true })
  }
})

test('private forks disclose official integrity, narrow patch, license and custom version', () => {
  const expected = {
    shared: 'sha512-x5LmiRLpRsd9KTjAB8MPKf0CDPMcuItjP0gbNqFCIgL1I8iYp4zglhj9w9FPCdIbHG2M91RVeIbArFfFTz9I3A==',
    renderer: 'sha512-GeyEUfMVRZMD/mZcNONEqg7MiU10QQ1DB3O/Qr6+8uXpbwdlmVgQ5Qs1/ZUAFX1X2UUtqMoGrDRbxdWfOJFT7Q==',
  }
  for (const group of ['shared', 'renderer']) {
    const metadata = provenance[group]
    const dir = group === 'shared' ? sharedDir : rendererDir
    assert.equal(metadata.upstreamVersion, '3.4.34')
    assert.equal(metadata.localVersion, '3.4.34-gongdan.1')
    assert.equal(metadata.tarballIntegrity, expected[group])
    assert.equal(metadata.officialFixCommit, 'a2b40db9a83b36ed9da3a16403cf8f040262d73f')
    assert.deepEqual(metadata.officialFixedVersions, ['3.5.42', '3.6.0-rc.6'])
    assert.equal(hash(fs.readFileSync(path.join(dir, 'LICENSE'))), metadata.licenseSha256)
    const pkg = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'))
    assert.equal(pkg.private, true)
    assert.equal(pkg.name, metadata.localName)
    assert.equal(pkg.version, metadata.localVersion)
    assert.equal(pkg.gongdanSecurityBackport.notAnOfficialVueSecurityRelease, true)
    assert.equal(metadata.patches.length, group === 'shared' ? 3 : 2)
    for (const patch of metadata.patches) {
      assert.ok(patch.kind.startsWith('official-add-CR-blacklist'))
      assert.notEqual(patch.originalSha256, patch.patchedSha256, 'unchanged imports are not security patches')
    }
    if (group === 'renderer') assert.deepEqual(metadata.integration.peerDependencies, pkg.peerDependencies)
    for (const relative of Object.keys(metadata.upstreamFileSha256)) {
      if (relative.endsWith('.js') || relative.endsWith('.d.ts')) source(group, relative, true)
    }
  }
})

test('unmodified published renderer really creates separate CR-injected HTML5 attributes', () => {
  const original = originalCommonJs()
  const output = original.ssrRenderAttrs({ id: 'safe', [attackKey]: inertHandler })
  assert.ok(output.includes(attackKey))
  const attributes = htmlAttributes(output)
  assert.equal(attributes.id, 'safe')
  assert.equal(attributes.x, '')
  assert.equal(attributes.autofocus, '')
  assert.equal(attributes.onfocus, inertHandler)
})

test('installed aliases and root-managed compiler peers all use the disclosed patched shared', () => {
  const rendererPackage = require('@vue/server-renderer/package.json')
  const sharedPackage = require(`${SHARED_ALIAS}/package.json`)
  assert.equal(rendererPackage.name, '@gongdan/uni-server-renderer')
  assert.equal(rendererPackage.version, '3.4.34-gongdan.1')
  assert.equal(rendererPackage.dependencies, undefined)
  assert.deepEqual(rendererPackage.peerDependencies, {
    vue: '3.4.34', '@vue/compiler-ssr': '3.4.34', '@vue/shared': '3.4.34-gongdan.1'
  })
  assert.equal(sharedPackage.name, '@gongdan/uni-vue-shared')
  assert.equal(sharedPackage.version, '3.4.34-gongdan.1')
  assert.equal(require('vue').version, '3.4.34')
  const rendererRequire = createRequire(require.resolve('@vue/server-renderer'))
  const compilerRequire = createRequire(require.resolve('@vue/compiler-ssr/package.json'))
  assert.equal(rendererRequire('@vue/compiler-ssr/package.json').version, '3.4.34')
  for (const consumer of [require, rendererRequire, compilerRequire]) {
    assert.equal(consumer(`${SHARED_ALIAS}/package.json`).name, '@gongdan/uni-vue-shared')
    assert.equal(consumer(`${SHARED_ALIAS}/package.json`).version, '3.4.34-gongdan.1')
    assert.equal(fs.realpathSync(consumer.resolve(SHARED_ALIAS)), fs.realpathSync(require.resolve(SHARED_ALIAS)))
    assert.equal(consumer(SHARED_ALIAS).isSSRSafeAttrName(attackKey), false)
  }
})

for (const distribution of ['dist/server-renderer.cjs.js', 'dist/server-renderer.cjs.prod.js']) {
  test(`${distribution} rejects CR through real helpers without changing exports`, async () => {
    const patched = require(path.join(rendererDir, distribution))
    const original = originalCommonJs(distribution)
    assert.deepEqual(Object.keys(patched).sort(), Object.keys(original).sort())
    await quiet(() => {
      const output = patched.ssrRenderAttrs({ id: 'safe', [attackKey]: inertHandler })
      assert.equal(output, ' id="safe"')
      assert.deepEqual(htmlAttributes(output), { id: 'safe' })
      assert.equal(patched.ssrRenderDynamicAttr(attackKey, inertHandler), '')
      assert.equal(patched.ssrRenderDynamicAttr('foo\rbar', 'text'), '')
    })
  })
}

test('all shared distributions preserve exports and reject all HTML ASCII whitespace', async () => {
  const original = publishedCommonJs(source('shared', 'dist/shared.cjs.js', true))
  const distributions = [require(path.join(sharedDir, 'dist/shared.cjs.js')), require(path.join(sharedDir, 'dist/shared.cjs.prod.js')),
    await esmModule('shared', 'dist/shared.esm-bundler.js')]
  await quiet(() => {
    for (const shared of distributions) {
      assert.deepEqual(Object.keys(shared).sort(), Object.keys(original).sort())
      assert.equal(shared.isSSRSafeAttrName('data-business-field'), true)
      assert.equal(shared.isSSRSafeAttrName('中文字段'), true)
      for (const character of ['\t', '\n', '\f', '\r', ' ']) assert.equal(shared.isSSRSafeAttrName(`x${character}name`), false)
    }
  })
})

test('SSR preserves ordinary data, boolean, SVG, class/style and escaped values', () => {
  const renderer = require('@vue/server-renderer')
  const original = originalCommonJs()
  const valid = { id: 'record-1', title: '<x>&"quote', 'data-code': '=1+1', disabled: true, hidden: false,
    class: { selected: true, skipped: false }, style: { color: 'red' } }
  assert.equal(renderer.ssrRenderAttrs(valid), original.ssrRenderAttrs(valid))
  const attrs = htmlAttributes(renderer.ssrRenderAttrs(valid))
  assert.equal(attrs.title, valid.title)
  assert.equal(attrs['data-code'], '=1+1')
  assert.equal(attrs.disabled, '')
  assert.equal(Object.hasOwn(attrs, 'hidden'), false)
  assert.equal(attrs.class, 'selected')
  assert.equal(renderer.ssrRenderAttrs({ viewBox: '0 0 10 10' }, 'svg'), original.ssrRenderAttrs({ viewBox: '0 0 10 10' }, 'svg'))
  // ssrRenderAttr is the upstream literal-key helper; preserve its value contract.
  assert.equal(renderer.ssrRenderAttr('title', valid.title), original.ssrRenderAttr('title', valid.title))
})

test('renderToString rejects CR for both Vue and current bundled uni VNodes', async () => {
  const renderer = require('@vue/server-renderer')
  const original = originalCommonJs()
  const Vue = require('vue')
  const Uni = require('@dcloudio/uni-h5-vue')
  assert.equal(Uni.version, '3.4.21')
  await quiet(async () => {
    for (const runtime of [Vue, Uni]) {
      const app = () => runtime.createSSRApp({ render: () => runtime.h('div', { id: 'safe', [attackKey]: inertHandler }, 'record') })
      const baseline = await original.renderToString(app())
      const unsafeElement = parseFragment(baseline).childNodes.find(node => node.tagName === 'div')
      assert.ok(unsafeElement)
      assert.equal(Object.fromEntries(unsafeElement.attrs.map(attr => [attr.name, attr.value])).onfocus, inertHandler)
      const output = await renderer.renderToString(app())
      assert.equal(output, '<div id="safe">record</div>')
      assert.equal(output.includes('onfocus'), false)
    }
  })
})

test('ESM-bundler distribution uses fixed helpers and retains exports', async () => {
  const patched = await esmModule('renderer', 'dist/server-renderer.esm-bundler.js')
  const original = await esmModule('renderer', 'dist/server-renderer.esm-bundler.js', true,
    source('shared', 'dist/shared.esm-bundler.js', true))
  assert.deepEqual(Object.keys(patched).sort(), Object.keys(original).sort())
  const baseline = original.ssrRenderAttrs({ id: 'safe', [attackKey]: inertHandler })
  assert.ok(baseline.includes(attackKey), 'the ESM baseline must really use the verified vulnerable original shared')
  assert.equal(htmlAttributes(baseline).onfocus, inertHandler)
  await quiet(() => assert.equal(patched.ssrRenderAttrs({ id: 'safe', [attackKey]: inertHandler }), ' id="safe"'))
})

for (const distribution of ['dist/server-renderer.esm-browser.js', 'dist/server-renderer.esm-browser.prod.js']) {
test(`${distribution} embedded shared is actually patched, not only renamed`, async () => {
  const original = await esmModule('renderer', distribution, true)
  const patched = await esmModule('renderer', distribution)
  assert.deepEqual(Object.keys(patched).sort(), Object.keys(original).sort())
  assert.equal(htmlAttributes(original.ssrRenderAttrs({ [attackKey]: inertHandler })).onfocus, inertHandler)
  await quiet(() => assert.equal(patched.ssrRenderAttrs({ id: 'safe', [attackKey]: inertHandler }), ' id="safe"'))
})
}

test('uni SSR forwards to the alias; client bundles are not falsely labeled upgraded', () => {
  assert.strictEqual(require('@dcloudio/uni-h5-vue/server-renderer'), require('@vue/server-renderer'))
  const uniDir = path.dirname(require.resolve('@dcloudio/uni-h5-vue/package.json'))
  for (const relative of ['dist/vue.runtime.cjs.js', 'dist/vue.runtime.esm.js', 'dist-x/vue.runtime.cjs.js', 'dist-x/vue.runtime.esm.js']) {
    const body = fs.readFileSync(path.join(uniDir, relative), 'utf8')
    assert.ok(body.includes('v3.4.21'))
    assert.equal(body.includes('unsafeAttrCharRE'), false)
    assert.equal(body.includes('isSSRSafeAttrName'), false)
  }
})

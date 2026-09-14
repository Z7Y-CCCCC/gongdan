import assert from 'node:assert/strict'
import { test } from 'node:test'
import path from 'node:path'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(import.meta.url)

test('all embedded Vite/esbuild/compiler plugins use the verified patched versions', () => {
  const expected = { vite: '6.4.3', esbuild: '0.25.12', '@vitejs/plugin-vue': '5.2.4', '@vitejs/plugin-vue-jsx': '4.2.0', '@vitejs/plugin-legacy': '6.1.1' }
  const manifest = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'))
  // npm reloads actual edges using the already-overridden version. The range
  // must include both uni's 3.4.21 request and 3.4.34 target, but exclude 3.5.
  assert.equal(manifest.overrides['@vue/shared@~3.4.0'], '3.4.34')
  assert.equal(manifest.overrides['@vue/compiler-sfc@~3.4.0'], '3.4.34')
  const lock = JSON.parse(readFileSync(path.join(root, 'package-lock.json'), 'utf8'))
  for (const [name, version] of Object.entries(expected)) {
    const found = Object.entries(lock.packages).filter(([entry]) => entry === `node_modules/${name}` || entry.endsWith(`/node_modules/${name}`))
    assert.ok(found.length > 0, `missing ${name}`)
    for (const [entry, metadata] of found) assert.equal(metadata.version, version, entry)
  }
  const uniRequire = createRequire(require.resolve('@dcloudio/vite-plugin-uni/package.json'))
  assert.equal(uniRequire('vite/package.json').version, expected.vite)
  const sharedRequire = createRequire(uniRequire.resolve('@dcloudio/uni-cli-shared/package.json'))
  assert.equal(sharedRequire('esbuild/package.json').version, expected.esbuild)
  assert.equal(sharedRequire('@vue/shared/package.json').version, '3.4.34')
  assert.equal(sharedRequire('@vue/compiler-sfc/package.json').version, '3.4.34')
  for (const name of ['@dcloudio/uni-app', '@dcloudio/uni-components', '@dcloudio/uni-mp-weixin', '@dcloudio/vite-plugin-uni']) {
    assert.equal(require(`${name}/package.json`).version, '3.0.0-4020920240930001')
  }
  assert.equal(require('vue/package.json').version, '3.4.34')
  // uni-h5-vue vendors its own runtime; package overrides do not rewrite it.
  const h5Runtime = readFileSync(require.resolve('@dcloudio/uni-h5-vue/dist/vue.runtime.esm.js'), 'utf8')
  assert.match(h5Runtime, /const version = "3\.4\.21"/)
  // Modern type/JSX tools already use their own 3.5 compiler helpers. Do not
  // force their private APIs down to 3.4 while pinning the uni/runtime branch.
  const languageRequire = createRequire(require.resolve('@vue/language-core/package.json'))
  assert.equal(languageRequire('@vue/compiler-dom/package.json').version, '3.5.34')
  assert.equal(languageRequire('@vue/shared/package.json').version, '3.5.34')
  const jsxRequire = createRequire(require.resolve('@vue/babel-plugin-jsx/package.json'))
  const jsxTypesRequire = createRequire(jsxRequire.resolve('@vue/babel-plugin-resolve-type/package.json'))
  assert.equal(jsxTypesRequire('@vue/compiler-sfc/package.json').version, '3.5.34')
})

test('H5 remains manually bootstrapped instead of enabling a second legacy compiler pipeline', () => {
  const manifest = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'))
  assert.equal(manifest.dependencies['@dcloudio/uni-h5'], undefined)
  const entry = readFileSync(path.join(root, 'src/main.ts'), 'utf8')
  assert.match(entry, /createSSRApp\(setupApp\(App\)\)/)
  assert.match(entry, /app\.mount\('#app'\)/)
})

test('compiler Express query parsing uses patched qs without bracket/comma and isBuffer failures', () => {
  const uniRequire = createRequire(require.resolve('@dcloudio/vite-plugin-uni/package.json'))
  const expressRequire = createRequire(uniRequire.resolve('express/package.json'))
  const qs = expressRequire('qs')
  assert.equal(expressRequire('qs/package.json').version, '6.16.0')
  assert.deepEqual(qs.parse('filter[status]=PENDING&ids[]=1&ids[]=2'), { filter: { status: 'PENDING' }, ids: ['1', '2'] })
  assert.throws(() => qs.parse('ids[]=1,2,3', { comma: true, arrayLimit: 2, throwOnLimitExceeded: true }), /Array limit exceeded/)
  const untrusted = qs.parse('constructor[isBuffer]=not-a-function&value=1', { allowPrototypes: true })
  assert.doesNotThrow(() => qs.stringify(untrusted))
})

test('local compiler adapters resolve inside the checkout and are never mislabeled upstream releases', () => {
  const expected = { 'adm-zip': '@gongdan/uni-zip-adapter', 'file-type': '@gongdan/uni-file-type-adapter' }
  const lock = JSON.parse(readFileSync(path.join(root, 'package-lock.json'), 'utf8'))
  for (const [alias, name] of Object.entries(expected)) {
    assert.equal(require(`${alias}/package.json`).name, name)
    assert.equal(require(`${alias}/package.json`).version, '1.0.0')
    const link = lock.packages[`node_modules/${alias}`]
    assert.equal(link.link, true)
    assert.ok(link.resolved.startsWith('vendor/'))
    assert.equal(lock.packages[link.resolved].name, name)
    assert.ok(!Object.keys(lock.packages).some(entry => entry.startsWith('node_modules/') && entry.includes(`/vendor/${path.basename(link.resolved)}`)), 'no broken parent-relative file override links')
  }
})

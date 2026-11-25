import assert from 'node:assert/strict'
import { test } from 'node:test'
import path from 'node:path'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(import.meta.url)

test('all embedded Vite/esbuild/compiler plugins use the verified patched versions', () => {
  const expected = { vite: '6.4.6', esbuild: '0.25.12', '@vitejs/plugin-vue': '5.2.4', '@vitejs/plugin-vue-jsx': '4.2.0', '@vitejs/plugin-legacy': '6.1.1' }
  const manifest = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'))
  // Patch only uni's compatible 3.4 branch, including the compiler's shared
  // predicate. Do not downgrade modern tools' separate 3.5 private APIs.
  assert.equal(manifest.overrides['@vue/shared@~3.4.0'], '$@vue/shared')
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
  assert.equal(sharedRequire('@vue/shared/package.json').name, '@gongdan/uni-vue-shared')
  assert.equal(sharedRequire('@vue/shared/package.json').version, '3.4.34-gongdan.1')
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
  assert.equal(languageRequire('@vue/compiler-dom/package.json').version, '3.5.46')
  assert.equal(languageRequire('@vue/shared/package.json').version, '3.5.46')
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

test('auto-import uses the bounded glob matcher without changing its named API contract', () => {
  const uniRequire = createRequire(require.resolve('@dcloudio/vite-plugin-uni/package.json'))
  const sharedRequire = createRequire(uniRequire.resolve('@dcloudio/uni-cli-shared/package.json'))
  const autoImportRequire = createRequire(sharedRequire.resolve('unplugin-auto-import/package.json'))
  const manifest = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'))
  assert.equal(manifest.overrides['unplugin-auto-import']?.minimatch, '11.0.5')
  assert.equal(autoImportRequire('minimatch/package.json').version, '11.0.5')
  // uni's pinned auto-import plug-in calls only the public named minimatch API.
  // Keep directory discovery equivalent while removing GHSA-g3hv-75wg-m249.
  const { minimatch } = autoImportRequire('minimatch')
  assert.equal(minimatch('src/composables/usePageMessage.ts', 'src/composables/**/*.ts'), true)
  assert.equal(minimatch('src/utils/navigation.ts', 'src/{utils,composables}/*.{ts,js}'), true)
  assert.equal(minimatch('src/pages/home/index.vue', 'src/composables/**/*.ts'), false)
  assert.equal(minimatch('src/composables/.hidden.ts', 'src/composables/**/*.ts'), false)
  assert.equal(minimatch('src/composables/.hidden.ts', 'src/composables/**/*.ts', { dot: true }), true)
})

test('compiler Express query parsing uses patched qs without bracket/comma and isBuffer failures', () => {
  const uniRequire = createRequire(require.resolve('@dcloudio/vite-plugin-uni/package.json'))
  const expressRequire = createRequire(uniRequire.resolve('express/package.json'))
  const qs = expressRequire('qs')
  assert.equal(expressRequire('qs/package.json').version, '6.17.1')
  assert.equal(expressRequire('body-parser/package.json').version, '1.20.9')
  assert.equal(expressRequire('proxy-addr/package.json').version, '2.0.8')
  assert.deepEqual(qs.parse('filter[status]=PENDING&ids[]=1&ids[]=2'), { filter: { status: 'PENDING' }, ids: ['1', '2'] })
  assert.throws(() => qs.parse('ids[]=1,2,3', { comma: true, arrayLimit: 2, throwOnLimitExceeded: true }), /Array limit exceeded/)
  const untrusted = qs.parse('constructor[isBuffer]=not-a-function&value=1', { allowPrototypes: true })
  assert.doesNotThrow(() => qs.stringify(untrusted))
})

test('local compatibility packages resolve inside the checkout and are never mislabeled upstream releases', () => {
  const expected = {
    'adm-zip': { name: '@gongdan/uni-zip-adapter', version: '1.0.0' },
    'file-type': { name: '@gongdan/uni-file-type-adapter', version: '1.0.0' },
    '@intlify/core-base': { name: '@gongdan/uni-intlify-core-compat', version: '1.0.0' },
    '@vue/server-renderer': { name: '@gongdan/uni-server-renderer', version: '3.4.34-gongdan.1' },
    '@vue/shared': { name: '@gongdan/uni-vue-shared', version: '3.4.34-gongdan.1' }
  }
  const lock = JSON.parse(readFileSync(path.join(root, 'package-lock.json'), 'utf8'))
  for (const [alias, { name, version }] of Object.entries(expected)) {
    assert.equal(require(`${alias}/package.json`).name, name)
    assert.equal(require(`${alias}/package.json`).version, version)
    const link = lock.packages[`node_modules/${alias}`]
    assert.equal(link.link, true)
    assert.ok(link.resolved.startsWith('vendor/'))
    assert.equal(lock.packages[link.resolved].name, name)
    assert.ok(!Object.keys(lock.packages).some(entry => entry.startsWith('node_modules/') && entry.includes(`/vendor/${path.basename(link.resolved)}`)), 'no broken parent-relative file override links')
  }
})

import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

const mobile = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repo = path.dirname(mobile)
const read = file => readFileSync(path.join(repo, file), 'utf8').replace(/^\uFEFF/, '')

test('fresh checkout and Docker contexts include only the reviewed vendored distributions', () => {
  const gitIgnore = read('.gitignore').split(/\r?\n/)
  const dockerIgnore = read('deploy/docker/Dockerfile.dockerignore').split(/\r?\n/)
  const attributes = read('.gitattributes').split(/\r?\n/)
  for (const name of ['uni-vue-shared', 'uni-server-renderer', 'uni-intlify-core']) {
    assert.ok(attributes.includes(`mobile/vendor/${name}/** -text`),
      'Windows auto-CRLF must not change the verified source/license bytes')
  }
  assert.ok(gitIgnore.includes('dist/'), 'application build outputs stay ignored')
  assert.ok(dockerIgnore.includes('**/dist/'), 'generated outputs stay out of Docker context')
  for (const name of ['uni-vue-shared', 'uni-server-renderer']) {
    const directory = `mobile/vendor/${name}/dist/`
    for (const [rules, deny] of [[gitIgnore, 'dist/'], [dockerIgnore, '**/dist/']]) {
      assert.ok(rules.indexOf(`!${directory}`) > rules.indexOf(deny), directory)
      assert.ok(rules.indexOf(`!${directory}**`) > rules.indexOf(deny), directory)
    }
    const root = path.join(mobile, 'vendor', name)
    const manifest = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'))
    for (const entry of [manifest.main, manifest.module, manifest.types]) {
      const target = path.resolve(root, entry)
      const relative = path.relative(root, target)
      assert.ok(!relative.startsWith('..') && !path.isAbsolute(relative), entry)
      assert.ok(existsSync(target), `required vendored entry is missing: ${name}/${entry}`)
    }
    assert.ok(existsSync(path.join(root, 'LICENSE')))
    assert.ok(existsSync(path.join(root, 'PROVENANCE.json')))
  }
  const dockerfile = read('deploy/docker/Dockerfile')
  const stage = dockerfile.split('AS mobile-build')[1].split(/^FROM /m)[0]
  assert.ok(stage.indexOf('COPY mobile/vendor/ ./vendor/') < stage.indexOf('RUN npm ci'),
    'file dependencies must be present before the clean install')
})

test('CI gates both npm dependency graphs without omitting build-time dependencies', () => {
  const workflow = read('.github/workflows/ci.yml')
  for (const [name, next] of [['frontend-build', 'backend-build'], ['mobile-build', 'plugin-tests']]) {
    const job = workflow.split(`  ${name}:`)[1].split(`  ${next}:`)[0]
    assert.match(job, /npm audit --audit-level=low --registry=https:\/\/registry\.npmjs\.org/)
    assert.doesNotMatch(job, /npm audit[^\r\n]*(?:--omit|--production|\|\|\s*true)/)
    assert.match(job, /npm ci/)
    assert.match(job, /npm test/)
  }
})

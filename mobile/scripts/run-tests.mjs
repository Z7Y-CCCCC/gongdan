import { readdirSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
// Expand explicitly for Windows and Node 20; never auto-run the long-lived
// browser-fixture.mjs server just because it lives beside the regression tests.
const tests = readdirSync(path.join(root, 'test'))
  .filter(name => name.endsWith('.test.mjs'))
  .sort()
  .map(name => path.join(root, 'test', name))
if (!tests.length) throw new Error('No mobile regression test files found')
const result = spawnSync(process.execPath, ['--test', ...process.argv.slice(2), ...tests], { cwd: root, stdio: 'inherit' })
if (result.error) throw result.error
process.exit(result.status ?? 1)

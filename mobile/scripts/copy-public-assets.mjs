import { cpSync, existsSync, mkdirSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = resolve(fileURLToPath(new URL('..', import.meta.url)))
const publicDir = resolve(rootDir, 'public')
const outputDir = resolve(rootDir, 'dist/build/h5')

if (existsSync(publicDir)) {
  mkdirSync(outputDir, { recursive: true })
  for (const entry of readdirSync(publicDir)) {
    cpSync(resolve(publicDir, entry), resolve(outputDir, entry), { recursive: true })
  }
}

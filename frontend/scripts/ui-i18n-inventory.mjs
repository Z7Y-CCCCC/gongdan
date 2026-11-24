import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@vue/compiler-sfc'
import { baseParse, NodeTypes } from '@vue/compiler-dom'
import ts from 'typescript'
import { localizeVueSource } from './ui-i18n-transform.mjs'

const src = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src')
const chinese = /\p{Script=Han}/u
const result = new Map()
const files = dir => readdirSync(dir, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
  ? files(path.join(dir, entry.name)) : entry.name.endsWith('.vue') ? [path.join(dir, entry.name)] : [])
function add(value, file, kind) {
  const text = value.trim()
  if (!chinese.test(text)) return
  const occurrences = result.get(text) || []
  occurrences.push({ file, kind })
  result.set(text, occurrences)
}
function htmlKeys(html, file) {
  try {
    const walk = node => {
      if (node.tag === 'style' || node.tag === 'script') return
      if (node.type === NodeTypes.TEXT) add(node.content, file, 'print')
      for (const child of node.children || []) walk(child)
    }
    walk(baseParse(html))
  } catch { /* Non-HTML strings remain script data, not translatable markup. */ }
}
for (const file of files(src)) {
  const name = path.relative(src, file).replaceAll('\\', '/')
  const { descriptor } = parse(readFileSync(file, 'utf8'))
  localizeVueSource(readFileSync(file, 'utf8'), name, (value, unused, kind) => add(value, name, kind))
  const code = descriptor.scriptSetup || descriptor.script
  if (code) {
    const ast = ts.createSourceFile(name + '.ts', code.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
    const walk = node => {
      if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
        if (/<\/?[a-z][\w:-]*(?:\s|>)/i.test(node.text)) htmlKeys(node.text, name)
        else add(node.text, name, 'script')
      }
      if (ts.isTemplateExpression(node)) {
        const pattern = node.templateSpans.reduce((text, span, index) => text + `{${index}}` + span.literal.text, node.head.text)
        if (/<\/?[a-z][\w:-]*(?:\s|>)/i.test(pattern)) htmlKeys(pattern, name)
        else add(pattern, name, 'pattern')
      }
      ts.forEachChild(node, walk)
    }
    walk(ast)
  }
}
const inventory = [...result].map(([source, occurrences]) => ({ source, occurrences }))
if (process.argv[2]) writeFileSync(process.argv[2], JSON.stringify(inventory, null, 2), 'utf8')
console.log(JSON.stringify({ files: files(src).length, unique: inventory.length, template: inventory.filter(item => item.occurrences.some(occurrence => occurrence.kind !== 'script')).length, script: inventory.filter(item => item.occurrences.some(occurrence => occurrence.kind === 'script')).length }))

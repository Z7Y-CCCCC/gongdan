import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@vue/compiler-sfc'
import { baseParse, NodeTypes } from '@vue/compiler-dom'

const defaultRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src')
function files(dir) {
  return readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name)).flatMap(entry => {
    const name = path.join(dir, entry.name)
    return entry.isDirectory() ? files(name) : name.endsWith('.vue') ? [name] : []
  })
}
function literalExpression(expression) {
  const value = expression?.trim() || ''
  const string = value.match(/^(['"])([\s\S]*)\1$/)
  if (string && !string[2].includes(string[1])) return string[2]
  if (/^(?:null|undefined|false|true)$/.test(value)) return ''
  if (/^\d+$/.test(value)) return value
  // An expression is evidence of a potential label, not proof it is non-empty at runtime.
  return value ? '{dynamic}' : ''
}
function ariaHidden(node) {
  return node.props?.some(prop =>
    (prop.type === NodeTypes.ATTRIBUTE && prop.name === 'aria-hidden' && prop.value?.content.toLowerCase() === 'true') ||
    (prop.type === NodeTypes.DIRECTIVE && prop.name === 'bind' && prop.arg?.content === 'aria-hidden' && prop.exp?.content.trim() === 'true'))
}
function labelText(node) {
  if (node.type === NodeTypes.TEXT) return node.content.trim()
  if (node.type === NodeTypes.INTERPOLATION) return literalExpression(node.content.content)
  if (node.tag === 'svg' || node.tag?.replaceAll('-', '').toLowerCase() === 'uiicon' || ariaHidden(node)) return ''
  return (node.children || []).map(labelText).join(' ').trim()
}
function hasMeaningfulText(value) {
  const text = value.trim()
  // Single letters commonly used as close glyphs do not describe the action.
  return !/^[xX]$/.test(text) && /[\p{L}\p{N}]/u.test(text)
}
function named(node) {
  return node.props.some(prop =>
    (prop.type === NodeTypes.ATTRIBUTE && ['aria-label', 'aria-labelledby', 'title'].includes(prop.name) && hasMeaningfulText(prop.value?.content || '')) ||
    (prop.type === NodeTypes.DIRECTIVE && prop.name === 'bind' && ['aria-label', 'aria-labelledby', 'title'].includes(prop.arg?.content) && hasMeaningfulText(literalExpression(prop.exp?.content))))
}

/** Structural contract, not a browser AccName implementation or accessibility certification.
 * SVG/UiIcon content and aria-hidden descendants do not count as a button label.
 * Dynamic expressions and aria-labelledby references still need runtime verification.
 */
export function auditTemplate(template, { file = '<template>', lineOffset = 0 } = {}) {
  const ast = baseParse(template)
  const missing = []
  let buttons = 0
  function walk(node, hiddenAncestor = false) {
    const hidden = hiddenAncestor || ariaHidden(node)
    if (node.tag === 'button') {
      buttons++
      if (hidden || (!hasMeaningfulText(labelText(node)) && !named(node))) {
        missing.push(`${file}:${node.loc.start.line + lineOffset}`)
      }
    }
    for (const child of node.children || []) walk(child, hidden)
  }
  walk(ast)
  return { buttons, missing }
}

export function auditIconControls(rootDirectory = defaultRoot) {
  const root = path.resolve(rootDirectory)
  const missing = []
  let buttons = 0
  for (const file of files(root)) {
    const source = readFileSync(file, 'utf8')
    const { descriptor, errors } = parse(source)
    if (errors.length) throw new SyntaxError(`${file}: ${errors.map(String).join('; ')}`)
    if (!descriptor.template) continue
    const result = auditTemplate(descriptor.template.content, {
      file: path.relative(root, file).replaceAll('\\', '/'),
      lineOffset: descriptor.template.loc.start.line - 1
    })
    buttons += result.buttons
    missing.push(...result.missing)
  }
  return { buttons, missing }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = auditIconControls()
  console.log(JSON.stringify(result, null, 2))
  if (process.argv.includes('--check') && result.missing.length) process.exitCode = 1
}

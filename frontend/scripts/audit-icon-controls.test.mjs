import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { compileStyle } from '@vue/compiler-sfc'
import ts from 'typescript'
import { auditIconControls, auditTemplate } from './audit-icon-controls.mjs'

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

test('named SVG and UiIcon buttons pass without counting their graphic contents', () => {
  const result = auditTemplate('<button aria-label="删除"><svg><title>garbage</title></svg></button><button title="刷新"><UiIcon name="refresh" /></button>')
  assert.equal(result.buttons, 2)
  assert.deepEqual(result.missing, [])
})

test('SVG title/text and UiIcon children do not replace a button accessible name', () => {
  const result = auditTemplate('<button><svg><title>删除</title><text>Delete</text></svg></button><button><UiIcon>刷新</UiIcon></button>')
  assert.equal(result.buttons, 2)
  assert.equal(result.missing.length, 2)
})

test('symbol-only and close-glyph controls require meaningful names', () => {
  for (const symbol of ['×', 'x', 'X', '+', '−', '…', '⚙', '&lt;', '&gt;']) {
    assert.equal(auditTemplate(`<button>${symbol}</button>`).missing.length, 1, symbol)
  }
  assert.deepEqual(auditTemplate('<button aria-label="关闭">×</button>').missing, [])
})

test('ordinary visible text, nested text, and page numbers remain valid labels', () => {
  const result = auditTemplate('<button>删除工单</button><button><span>Retry</span></button><button>12</button>')
  assert.equal(result.buttons, 3)
  assert.deepEqual(result.missing, [])
})

test('aria-hidden text cannot supply a label, while named buttons may hide their icons', () => {
  assert.equal(auditTemplate('<button><span aria-hidden="true">删除</span><svg /></button>').missing.length, 1)
  assert.equal(auditTemplate('<button><span :aria-hidden="true">删除</span></button>').missing.length, 1)
  assert.deepEqual(auditTemplate('<button aria-label="删除"><svg aria-hidden="true" /></button>').missing, [])
})

test('a focusable button hidden from assistive technology is flagged even with a name', () => {
  assert.equal(auditTemplate('<button aria-hidden="true" aria-label="删除"><svg /></button>').missing.length, 1)
  assert.equal(auditTemplate('<div aria-hidden="true"><button>删除</button></div>').missing.length, 1)
})

test('dynamic labels are recognized structurally without executing their expressions', () => {
  const result = auditTemplate('<button :aria-label="label"><svg /></button><button>{{ label }}</button><button :title="active ? \'暂停\' : \'播放\'"><svg /></button>')
  assert.equal(result.buttons, 3)
  assert.deepEqual(result.missing, [])
})

test('statically empty dynamic labels, empty names, and punctuation names are rejected', () => {
  for (const attrs of ['aria-label=" "', ':aria-label="\'\'"', ':title="null"', 'title="×"']) {
    assert.equal(auditTemplate(`<button ${attrs}><svg /></button>`).missing.length, 1, attrs)
  }
})

test('literal interpolation is distinguished from dynamic text and empty strings', () => {
  assert.deepEqual(auditTemplate('<button>{{ \'删除\' }}</button><button>{{ 0 }}</button>').missing, [])
  assert.equal(auditTemplate('<button>{{ \'\' }}</button><button>{{ \'×\' }}</button>').missing.length, 2)
})

test('comments do not turn an unnamed icon control into a text control', () => {
  assert.equal(auditTemplate('<button><!-- 删除 --><svg /></button>').missing.length, 1)
  assert.deepEqual(auditTemplate('<!-- <button><svg /></button> -->'), { buttons: 0, missing: [] })
})

test('template audits return actionable source locations and reject malformed markup', () => {
  assert.deepEqual(auditTemplate('\n<button><svg /></button>', { file: 'fixture.vue', lineOffset: 7 }),
    { buttons: 1, missing: ['fixture.vue:9'] })
  assert.throws(() => auditTemplate('<button><svg></button>'), SyntaxError)
})

test('SFC scanning uses template line offsets and skips files with no template', () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'ui-contract-'))
  try {
    writeFileSync(path.join(directory, 'bad.vue'), '<script setup>const x = 1</script>\n<template>\n<button><svg /></button>\n</template>')
    writeFileSync(path.join(directory, 'good.vue'), '<template><button aria-label="关闭"><svg /></button></template>')
    writeFileSync(path.join(directory, 'helper.vue'), '<script>export default {}</script>')
    assert.deepEqual(auditIconControls(directory), { buttons: 2, missing: ['bad.vue:3'] })
  } finally {
    assert.equal(path.dirname(path.resolve(directory)), path.resolve(os.tmpdir()))
    assert.match(path.basename(directory), /^ui-contract-/)
    rmSync(directory, { recursive: true, force: true })
  }
})

const css = readFileSync(path.join(frontend, 'src/styles.css'), 'utf8')
const compiled = compileStyle({ source: css, filename: 'styles.css', id: 'ui-contract', scoped: false })
assert.deepEqual(compiled.errors, [])
const stylesheet = compiled.rawResult.root
function lastRule(container, selector) {
  const rules = []
  container.walkRules(rule => {
    if (rule.selector.split(',').map(value => value.trim()).includes(selector)) rules.push(rule)
  })
  assert.ok(rules.length, `Missing CSS rule: ${selector}`)
  return rules.at(-1)
}
function declaration(rule, property) {
  return rule.nodes.filter(node => node.type === 'decl' && node.prop === property).at(-1)
}
function media(pattern) {
  const rules = []
  stylesheet.walkAtRules('media', rule => { if (pattern.test(rule.params)) rules.push(rule) })
  assert.ok(rules.length, `Missing media contract: ${pattern}`)
  return rules.at(-1)
}

test('actual stylesheet source gives keyboard buttons a nonzero visible focus outline', () => {
  const rule = lastRule(stylesheet, 'button:focus-visible')
  assert.match(declaration(rule, 'outline').value, /^2px\s+(?!none\b)/)
  assert.equal(declaration(rule, 'outline-offset').value, '2px')
})

test('actual coarse-pointer rules enlarge icon and labeled actions rather than only the glyph', () => {
  const coarse = media(/pointer\s*:\s*coarse/)
  const icons = lastRule(coarse, '.ui-icon-button.ui-icon-button')
  assert.equal(declaration(icons, 'width').value, '44px')
  assert.equal(declaration(icons, 'min-width').value, '44px')
  assert.equal(declaration(icons, 'min-height').value, '44px')
  assert.equal(declaration(lastRule(coarse, '.ui-action-button'), 'min-height').value, '44px')
})

test('actual reduced-motion override disables both animation and transition with important priority', () => {
  const reduced = media(/prefers-reduced-motion\s*:\s*reduce/)
  const rule = lastRule(reduced, '*')
  for (const property of ['animation', 'transition']) {
    assert.equal(declaration(rule, property).value, 'none')
    assert.equal(declaration(rule, property).important, true)
  }
  assert.equal(declaration(rule, 'scroll-behavior').value, 'auto')
  assert.equal(declaration(rule, 'scroll-behavior').important, true)
})

test('entry source mounts Vue without adding a global innerHTML/MutationObserver header decorator', () => {
  const entry = ts.createSourceFile('main.ts', readFileSync(path.join(frontend, 'src/main.ts'), 'utf8'),
    ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  let vueMount = false
  const forbidden = []
  function visit(node) {
    if (ts.isPropertyAccessExpression(node) && node.name.text === 'innerHTML') forbidden.push('innerHTML')
    if (ts.isIdentifier(node) && node.text === 'MutationObserver') forbidden.push('MutationObserver')
    if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression) && node.expression.name.text === 'mount' &&
        node.arguments.some(arg => ts.isStringLiteral(arg) && arg.text === '#app')) vueMount = true
    ts.forEachChild(node, visit)
  }
  visit(entry)
  assert.equal(vueMount, true)
  assert.deepEqual(forbidden, [])
})

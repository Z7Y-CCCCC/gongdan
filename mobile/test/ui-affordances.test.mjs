import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { parse } from '@vue/compiler-sfc'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const views = [
  ...readdirSync(path.join(root, 'src/pages')).map(page => `src/pages/${page}/index.vue`),
  ...readdirSync(path.join(root, 'src/components')).filter(name => name.endsWith('.vue')).map(name => `src/components/${name}`)
]
const read = file => readFileSync(path.join(root, file), 'utf8')
function property(node, name) {
  return node.props?.find(prop => prop.type === 6 && prop.name === name
    || prop.type === 7 && prop.name === 'bind' && prop.arg?.content === name)
}
function userText(node) {
  if (node.type === 2) return node.content.trim().length > 0
  if (node.type === 5) return true
  if (node.tag === 'uni-icons') return false
  return (node.children || []).some(userText)
}
function walk(node, visit) {
  visit(node)
  for (const child of node.children || []) walk(child, visit)
}

test('mobile icon actions are named semantic buttons and use known uni-icons glyphs', () => {
  const font = readFileSync(path.join(root, 'node_modules/@dcloudio/uni-ui/lib/uni-icons/uniicons_file_vue.js'), 'utf8')
  const known = new Set([...font.matchAll(/"font_class"\s*:\s*"([^"]+)"/g)].map(match => match[1]))
  for (const file of views) {
    const parsed = parse(read(file))
    assert.equal(parsed.errors.length, 0, file)
    walk(parsed.descriptor.template.ast, node => {
      if (node.tag === 'IconButton') {
        const label = property(node, 'label')
        assert.ok(label && (label.value?.content.trim() || label.exp?.content.trim()), `${file}: icon action must have a label`)
        const icon = property(node, 'icon')
        if (icon?.type === 6) assert.ok(known.has(icon.value.content), `${file}: unknown icon ${icon.value.content}`)
      }
      if (node.tag === 'uni-icons') {
        const icon = property(node, 'type')
        if (icon?.type === 6) assert.ok(known.has(icon.value.content), `${file}: unknown icon ${icon.value.content}`)
      }
      if (node.tag === 'button' && !userText(node)) assert.ok(property(node, 'aria-label'), `${file}: unnamed icon-only button`)
      if (['view', 'text'].includes(node.tag) && node.props?.some(prop => prop.type === 7 && prop.name === 'on' && prop.arg?.content === 'click')
          && !userText(node) && node.children?.some(child => child.tag === 'uni-icons')) {
        assert.fail(`${file}: icon action must not be an inaccessible view`)
      }
    })
  }
})

test('shared mobile actions have 44px touch targets, visible focus and disabled/busy semantics', () => {
  const icon = read('src/components/IconButton.vue')
  assert.match(icon, /<button\b/)
  assert.match(icon, /:aria-label="label"/)
  assert.match(icon, /:title="label"/)
  assert.match(icon, /:disabled="disabled \|\| busy"/)
  assert.match(icon, /:aria-busy="busy"/)
  assert.match(icon, /min-width:\s*44px/)
  assert.match(icon, /min-height:\s*44px/)
  const app = read('src/App.vue')
  assert.match(app, /button:focus-visible/)
  assert.match(app, /outline:\s*2px solid/)
  assert.match(app, /min-height:\s*44px/)
  assert.match(read('src/pages/work-order-detail/index.vue'), /\.btn\.primary\s*\{[^}]*background:\s*#00856b;[^}]*color:\s*#fff/)
})

test('mobile motion is interaction-only, bounded and honors reduced motion', () => {
  const app = read('src/App.vue')
  assert.match(app, /--motion-fast:\s*140ms/)
  assert.match(app, /--motion-change:\s*180ms/)
  assert.match(app, /--motion-base:\s*220ms/)
  assert.match(app, /prefers-reduced-motion:\s*reduce/)
  assert.match(app, /animation-duration:\s*1ms !important/)
  assert.match(app, /transition-duration:\s*1ms !important/)
  assert.doesNotMatch(app, /mobile-page-in/)
  for (const file of [...views, 'src/App.vue']) {
    for (const style of parse(read(file)).descriptor.styles) {
      assert.doesNotMatch(style.content, /(?:animation|transition)[^;]*\binfinite\b/, file)
      assert.doesNotMatch(style.content, /transition[^;]*0\.3s/, file)
    }
  }
})

test('compact mobile UI keeps partial-data scope, rejection reasons and non-color status labels', () => {
  assert.match(read('src/components/ListLimitNotice.vue'), /loaded.*total/)
  assert.match(read('src/components/ListLimitNotice.vue'), /仅统计已加载记录/)
  assert.match(read('src/pages/tasks/index.vue'), /已查.*loadedOrders.*orderTotal/)
  assert.match(read('src/pages/board/index.vue'), /指标全量/)
  assert.match(read('src/pages/report-list/index.vue'), /驳回原因/)
  assert.match(read('src/pages/report-list/index.vue'), /已通过.*已驳回.*待审批/)
  const bar = read('src/components/MobileTabBar.vue')
  assert.match(bar, /<text>\{\{ item.label \}\}<\/text>/)
  assert.match(bar, /:aria-selected=/)
  assert.match(bar, /onDeactivated/)
  assert.match(bar, /to="body"/)
})

test('H5 list frames provide real bounded scroll regions so pagination is not covered by the dock', () => {
  const app = read('src/App.vue')
  assert.match(app, /\.task-list.*\.order-list.*\.list-content.*\.process-list.*overflow-y:\s*auto/)
  assert.match(app, /\.status-tabs.*overflow-x:\s*auto/)
  for (const file of ['tasks', 'work-orders', 'report-list', 'work-order-detail']) {
    const styles = parse(read(`src/pages/${file}/index.vue`)).descriptor.styles.map(style => style.content).join('\n')
    assert.match(styles, /min-height:\s*0/, file)
  }
})

test('mobile reporter dialog exposes its purpose, Escape dismissal and focus containment', () => {
  const form = read('src/pages/report-form/index.vue')
  assert.match(form, /role="dialog" aria-modal="true" aria-label="选择生产人员"/)
  assert.match(form, /@keydown="trapPickerFocus"/)
  assert.match(form, /event.key === 'Escape'/)
  assert.match(form, /event.key !== 'Tab'/)
  assert.match(form, /pickerReturnFocus\?\.focus/)
})

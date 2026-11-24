import { parse } from '@vue/compiler-sfc'
import { baseParse, NodeTypes } from '@vue/compiler-dom'
import ts from 'typescript'

const han = /\p{Script=Han}/u
const attributes = new Set(['title', 'aria-label', 'placeholder', 'alt', 'label'])
const authored = /(?:\b(?:name|code|spec|remark|content|lastMessage|senderName|receiverName|fileName|originalFileName|username|phone|address|password|token|url|sql|value|fieldName|productName|materialName|customerName|supplierName|warehouseName|departmentName|operatorName|reporterName|assignee|createdAt|updatedAt)\b)$/i
const uiValue = /(?:label|title|message|error|notice|hint|description|display|status|priority|role|attribute|summary|totalText|formatDate|formatTime|relativeTime)/i
const authoredRecord = /(?:\b(?:record|item|row|entry|doc|document|order|product|contact|message)\??\.(?:title|description|content|name|remark)\b|\bdisplayName\b)/
const escapeAttribute = value => value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;')

export function localizeVueSource(source, filename = 'component.vue', record = () => {}) {
  const { descriptor, errors } = parse(source, { filename })
  if (errors.length) throw errors[0]
  if (!descriptor.template) return source
  const offset = descriptor.template.loc.start.offset
  const ast = baseParse(descriptor.template.content)
  const edits = []
  const add = (node, text) => edits.push({ start: offset + node.loc.start.offset, end: offset + node.loc.end.offset, text })
  const key = text => text.replace(/\s+/g, ' ').trim()
  // Vue closes moustaches at }} even inside a string; keep macro/JSON keys safe.
  const quoted = text => JSON.stringify(text).replaceAll('}', '\\u007d')
  function displayExpression(expression, force = false) {
    if (expression.includes('$t(')) return expression
    if (authoredRecord.test(expression) && !han.test(expression)) return expression
    const tree = ts.createSourceFile('value.ts', `const value = (${expression})`, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
    let node = tree.statements[0]?.declarationList?.declarations[0]?.initializer
    while (node && ts.isParenthesizedExpression(node)) node = node.expression
    if (node && ts.isCallExpression(node) && /^(?:formatDate|formatDateTime|normalizeDate)$/.test(node.expression.getText(tree)) && node.arguments.length === 1) return `$date(${node.arguments[0].getText(tree)}, ${node.expression.getText(tree) === 'formatDateTime'})`
    if (node && ts.isPropertyAccessExpression(node) && /^(?:createdAt|updatedAt|loginAt|lastSeenAt|lastReportTime|readAt|handledAt|resolvedAt|archivedAt)$/.test(node.name.text)) return `$date(${expression})`
    if (node && ts.isConditionalExpression(node)) return `(${node.condition.getText(tree)} ? ${displayExpression(node.whenTrue.getText(tree), true)} : ${displayExpression(node.whenFalse.getText(tree), true)})`
    if (node && ts.isBinaryExpression(node) && [ts.SyntaxKind.BarBarToken, ts.SyntaxKind.QuestionQuestionToken].includes(node.operatorToken.kind)) return `(${displayExpression(node.left.getText(tree), true)} ${node.operatorToken.getText(tree)} ${displayExpression(node.right.getText(tree), true)})`
    if (node && ts.isTemplateExpression(node)) {
      const pattern = node.templateSpans.reduce((text, span, index) => text + `{${index}}` + span.literal.text, node.head.text)
      if (han.test(pattern)) {
        record(key(pattern), filename, 'pattern')
        return `$t(${quoted(key(pattern))}, [${node.templateSpans.map(span => displayExpression(span.expression.getText(tree))).join(', ')}])`
      }
    }
    if (node && ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.PlusToken && han.test(node.getText(tree))) {
      const parts = []
      const flatten = value => {
        if (ts.isBinaryExpression(value) && value.operatorToken.kind === ts.SyntaxKind.PlusToken && han.test(value.getText(tree))) {
          flatten(value.left); flatten(value.right)
        } else parts.push(value)
      }
      flatten(node)
      const values = []
      const pattern = parts.map(part => ts.isStringLiteral(part) || ts.isNoSubstitutionTemplateLiteral(part) ? part.text : `{${values.push(part.getText(tree)) - 1}}`).join('')
      if (han.test(pattern)) {
        record(key(pattern), filename, 'pattern')
        return `$t(${quoted(key(pattern))}, [${values.map(value => displayExpression(value)).join(', ')}])`
      }
    }
    return !authored.test(expression.trim()) && (force || han.test(expression) || uiValue.test(expression)) ? `$t((${expression}))` : expression
  }
  function collectExpression(expression) {
    const tree = ts.createSourceFile('expression.ts', `const value = (${expression})`, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
    const visit = node => {
      if ((ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) && han.test(node.text)) record(key(node.text), filename, 'expression')
      if (ts.isTemplateExpression(node)) {
        const pattern = node.templateSpans.reduce((text, span, index) => text + `{${index}}` + span.literal.text, node.head.text)
        if (han.test(pattern)) record(key(pattern), filename, 'pattern')
      }
      ts.forEachChild(node, visit)
    }
    visit(tree)
  }
  function walk(node) {
    if (['option', 'th'].includes(node.tag)) {
      const parts = []
      const collect = child => {
        if (child.type === NodeTypes.TEXT) parts.push({ text: child.content })
        else if (child.type === NodeTypes.INTERPOLATION) parts.push({ expression: child.content.content })
        else if (child.tag !== 'svg' && child.tag !== 'UiIcon') (child.children || []).forEach(collect)
      }
      ;(node.children || []).forEach(collect)
      const text = parts.map(part => part.text || '').join('').trim()
      const expressions = parts.filter(part => part.expression)
      const value = expressions.length ? `[${parts.map(part => part.expression ? `String((${part.expression}))` : quoted(part.text)).join(',')}].join('')` : quoted(text)
      const attribute = node.tag === 'option' ? 'value' : 'data-ui-label'
      const exists = node.props.some(prop => prop.name === attribute || prop.name === 'bind' && prop.arg?.content === attribute)
      if (!exists && parts.length) {
        const start = offset + node.loc.start.offset + node.tag.length + 1
        edits.push({ start, end: start, text: ` :${attribute}="${escapeAttribute(value)}"` })
      }
    }
    for (const prop of node.props || []) {
      if (prop.type === NodeTypes.ATTRIBUTE && attributes.has(prop.name) && han.test(prop.value?.content || '')) {
        const text = key(prop.value.content)
        record(text, filename, prop.name)
        add(prop, `:${prop.name}="${escapeAttribute(`$t(${quoted(text)})`)}"`)
      } else if (prop.type === NodeTypes.DIRECTIVE && prop.name === 'bind' && attributes.has(prop.arg?.content) && prop.exp?.content && !prop.exp.content.includes('$t(')) {
        collectExpression(prop.exp.content)
        add(prop, `:${prop.arg.content}="${escapeAttribute(displayExpression(prop.exp.content, true))}"`)
      }
    }
    const children = node.children || []
    for (let index = 0; index < children.length;) {
      const child = children[index]
      if (child.type !== NodeTypes.TEXT && child.type !== NodeTypes.INTERPOLATION) {
        walk(child)
        index++
        continue
      }
      const run = []
      while (index < children.length && [NodeTypes.TEXT, NodeTypes.INTERPOLATION].includes(children[index].type)) run.push(children[index++])
      const values = []
      const pattern = key(run.map(item => item.type === NodeTypes.TEXT ? item.content : `{${values.push(item.content.content) - 1}}`).join(''))
      if (han.test(pattern)) {
        record(pattern, filename, 'template')
        values.forEach(collectExpression)
        edits.push({ start: offset + run[0].loc.start.offset, end: offset + run[run.length - 1].loc.end.offset,
          text: `{{ $t(${quoted(pattern)}${values.length ? `, [${values.map(displayExpression).join(', ')}]` : ''}) }}` })
      } else {
        for (const item of run) {
          if (item.type !== NodeTypes.INTERPOLATION || item.content.content.includes('$t(')) continue
          const expression = item.content.content.trim()
          collectExpression(expression)
          if (han.test(expression) || (!authored.test(expression) && (uiValue.test(expression) || ['button', 'option', 'h1', 'h2', 'h3', 'h4', 'dt'].includes(node.tag))) || /\b(?:createdAt|updatedAt|loginAt|lastSeenAt|lastReportTime)\b/.test(expression)) {
            add(item, `{{ ${displayExpression(expression, true)} }}`)
          }
        }
      }
    }
  }
  walk(ast)
  return edits.sort((a, b) => b.start - a.start).reduce((text, edit) => text.slice(0, edit.start) + edit.text + text.slice(edit.end), source)
}

export function uiI18nTransform() {
  return {
    name: 'task-ui-localization',
    enforce: 'pre',
    transform(source, id) {
      if (!id.endsWith('.vue') || id.includes('node_modules')) return null
      return { code: localizeVueSource(source, id), map: null }
    }
  }
}

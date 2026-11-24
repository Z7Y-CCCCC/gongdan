/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, describe, expect, it } from 'vitest'
import { currentReportPath, reportHasUnsavedChanges, reportLocaleUrl } from '../reportLocale'

const { JSDOM } = createRequire(import.meta.url)('jsdom') as { JSDOM: new (...args: any[]) => { window: any } }
const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const backendResources = resolve(frontend, '../backend/src/main/resources')
const bootstrap = readFileSync(resolve(frontend, 'public/report-locale-bootstrap.js'), 'utf8')
const documents: InstanceType<typeof JSDOM>[] = []
function nativeDocument(html = '', query = '?ui_locale=en-US', page = 'designer') {
  const dom = new JSDOM(html, { url: `https://reports.test/ureport/${page}${query}`, runScripts: 'outside-only' })
  documents.push(dom)
  return dom
}
afterEach(() => { documents.splice(0).forEach(dom => dom.window.close()) })

describe('native report locale integration', () => {
  it('keeps frontend and direct-backend bootstrap assets byte-for-byte identical', () => {
    expect(readFileSync(resolve(backendResources, 'static/report-locale-bootstrap.js'), 'utf8')).toBe(bootstrap)
  })

  it.each([
    ['designer.html', '2f5099909faea3f80da5c771016e6b9a2d3bd1a70536a726b0a056a4d4eba50f'],
    ['html-preview.html', '130c8d7e7f4f041fdefa98286d407afec88a595a56cf9c412cc67e7764a79909'],
    ['searchform.html', 'df15b44aa45a42c1d7ea08425f7653399772589cda0325069eecd43ba73b0cee']
  ])('preserves native %s template byte content except documented UI integration', (name, hash) => {
    const template = readFileSync(resolve(backendResources, 'ureport-html', name), 'utf8')
    const integration = '\n<script src="${contextPath}/report-locale-bootstrap.js"></script>'
    expect(template.indexOf(integration)).toBeGreaterThan(template.indexOf('venderjs/jquery.min.js'))
    expect(template.indexOf(integration)).toBeLessThan(template.indexOf('bundle.js'))
    const original = template.replace(integration, '').replace(' id="_workorder_report_toolbar"', '')
    expect(createHash('sha256').update(original, 'utf8').digest('hex')).toBe(hash)
  })

  it('sets only the report iframe navigator and honors an explicit validated query before storage', () => {
    const parent = new JSDOM('<iframe></iframe>', { url: 'https://reports.test/ureport/designer?ui_locale=en-US', runScripts: 'outside-only' })
    documents.push(parent)
    Object.defineProperty(parent.window.navigator, 'language', { configurable: true, value: 'fr-FR' })
    parent.window.localStorage.setItem('heihu_locale', 'zh-CN')
    const originalLanguage = parent.window.navigator.language
    const frame = parent.window.document.querySelector('iframe')!.contentWindow!
    // A real iframe starts at about:blank; the report preference still comes from the same origin.
    frame.localStorage.setItem('heihu_locale', 'en-US')
    frame.eval(bootstrap)
    expect(frame.navigator.language).toBe('en-US')
    expect(parent.window.navigator.language).toBe(originalLanguage)

    const report = nativeDocument()
    report.window.localStorage.setItem('heihu_locale', 'zh-CN')
    report.window.eval(bootstrap)
    expect(report.window.navigator.language).toBe('en-US')
    expect(report.window.localStorage.getItem('heihu_locale')).toBe('en-US')
    expect(report.window.document.documentElement.lang).toBe('en-US')
  })

  it('ignores invalid locale query values and never persists tokens or external URLs', () => {
    const report = nativeDocument('', '?ui_locale=https%3A%2F%2Fevil.test%2FTOKEN')
    report.window.localStorage.setItem('heihu_locale', 'zh-CN')
    report.window.eval(bootstrap)
    expect(report.window.navigator.language).toBe('zh-CN')
    expect(report.window.localStorage.length).toBe(1)
    expect(report.window.localStorage.getItem('heihu_locale')).toBe('zh-CN')
  })

  it('supplements the native missing Undo label without changing other native translations', () => {
    const report = nativeDocument()
    report.window.eval(bootstrap)
    const dictionary = { tools: { undo: { noUndo: 'Nothing to undo.', redo: 'Undo' }, save: { save: 'Save' } } }
    ;(report.window as any).i18n = dictionary
    expect((report.window as any).i18n.tools.undo.undo).toBe('Undo')
    expect((report.window as any).i18n.tools.undo.redo).toBe('Undo')
    expect((report.window as any).i18n.tools.save.save).toBe('Save')
  })

  it('handles the native bundle building its editor after the outer jQuery ready queue', async () => {
    const report = nativeDocument()
    report.window.eval(bootstrap)
    report.window.document.dispatchEvent(new report.window.Event('DOMContentLoaded'))
    await new Promise(resolve => setTimeout(resolve, 10))
    // Native bundle construction starts later. Its named globals notify only
    // the owned UI scope, without observing any business/report document body.
    ;(report.window as any).i18n = { tools: { undo: { redo: 'Undo' } } }
    const builder = { customBusinessData: '用户自定义值' }
    ;(report.window as any).formBuilder = builder
    report.window.document.body.innerHTML = '<div class="ud-toolbar"><button title="Undo"></button><div style="float:right">报表:真实模板</div></div><div id="pb_component_container_palette">文本框</div><div id="pb_component_property_palette"><label>标题</label></div><div id="_ureport_table">文本框</div>'
    await new Promise(resolve => setTimeout(resolve, 10))
    expect(report.window.document.querySelector('.ud-toolbar button')!.getAttribute('aria-label')).toBe('Undo')
    expect(report.window.document.querySelector('.ud-toolbar > div')!.textContent).toBe('Report: 真实模板')
    expect(report.window.document.getElementById('pb_component_container_palette')!.textContent).toBe('Text input')
    expect(report.window.document.getElementById('_ureport_table')!.textContent).toBe('文本框')
    expect((report.window as any).formBuilder).toBe(builder)
    expect(builder.customBusinessData).toBe('用户自定义值')
  })

  it('waits for the template AJAX before localizing both native toolbar scopes', async () => {
    const report = nativeDocument('<script src="/ureport/res/ureport-asserts/js/designer.bundle.js"></script>')
    report.window.eval(bootstrap)
    ;(report.window as any).i18n = { tools: { undo: { redo: 'Undo' } } }
    report.window.document.dispatchEvent(new report.window.Event('DOMContentLoaded'))
    await new Promise(resolve => setTimeout(resolve, 30))
    // The initial report fetch resolves later than both jQuery ready queues.
    report.window.document.body.innerHTML = '<div class="ud-toolbar"><button title="Add Database Connection"></button></div><div class="ud-toolbar top-toolbar"><button title="Undo"></button><div style="float:right">报表:真实模板</div></div><div id="_ureport_table">用户自定义值</div>'
    await new Promise(resolve => setTimeout(resolve, 150))
    expect(report.window.document.querySelector('.ud-toolbar button')!.getAttribute('aria-label')).toBe('Add Database Connection')
    expect(report.window.document.querySelector('.top-toolbar button')!.getAttribute('aria-label')).toBe('Undo')
    expect(report.window.document.querySelector('.top-toolbar > div')!.textContent).toBe('Report: 真实模板')
    expect(report.window.document.getElementById('_ureport_table')!.textContent).toBe('用户自定义值')
  })

  it.each([
    ['p', '设计中报表-ureport', 'en-US', 'Draft report - UReport'],
    ['file:设计中报表-ureport', '设计中报表-ureport', 'en-US', '设计中报表-ureport'],
    ['p', '用户自定义报表-ureport', 'en-US', '用户自定义报表-ureport'],
    ['p', '设计中报表-ureport（客户版）', 'en-US', '设计中报表-ureport（客户版）'],
    ['p', '设计中报表-ureport', 'zh-CN', '设计中报表-ureport']
  ])('translates only the exact native draft preview title (%s / %s / %s)', async (file, title, locale, expected) => {
    const report = nativeDocument('', `?_u=${encodeURIComponent(file)}&ui_locale=${locale}`, 'preview')
    report.window.document.title = title
    const data = { reportTitle: title, cells: [{ value: '设计中报表-ureport' }] }
    ;(report.window as any).__current_report_def = data
    report.window.eval(bootstrap)
    report.window.document.dispatchEvent(new report.window.Event('DOMContentLoaded'))
    await new Promise(resolve => setTimeout(resolve, 10))
    expect(report.window.document.title).toBe(expected)
    expect(data.reportTitle).toBe(title)
    expect(data.cells[0].value).toBe('设计中报表-ureport')
  })

  it('uses singular/plural only for the owned page count word, never business report text', () => {
    const report = nativeDocument('<div id="_workorder_report_toolbar"><span id="totalPageLabel">1</span>页<select><option>1</option></select></div><div id="_ureport_table">Total 1 pages 用户数据</div>', '?_u=p&ui_locale=en-US', 'preview')
    report.window.eval(bootstrap)
    ;(report.window as any).buildPaging = () => {}
    ;(report.window as any).buildPaging()
    const doc = report.window.document
    expect(doc.getElementById('totalPageLabel')!.nextSibling!.nodeValue!.trim()).toBe('page')
    doc.getElementById('totalPageLabel')!.textContent = '2'
    ;(report.window as any).buildPaging()
    expect(doc.getElementById('totalPageLabel')!.nextSibling!.nodeValue!.trim()).toBe('pages')
    expect(doc.querySelector('select option')!.textContent).toBe('1')
    expect(doc.getElementById('_ureport_table')!.textContent).toBe('Total 1 pages 用户数据')
  })

  it('localizes only owned preview/palette UI and preserves business cells, inputs and query options', async () => {
    const report = nativeDocument(`<div id="_workorder_report_toolbar"><button title="导出EXCEL">预览</button><span id="pageLinkContainer"></span></div>
      <div id="_ureport_table">预览<button title="导出EXCEL">业务数据</button></div>
      <div class="pb-palette"><ul class="nav-tabs"><li>组件</li></ul></div>
      <div id="pb_component_container_palette">文本框</div>
      <div id="pb_component_property_palette"><label>显示名称</label><input value="预览"><select><option>文本框</option></select>
        <div class="form-group" id="native-fixed-style"><label>按钮风格</label><select><option value="btn-default">默认</option></select></div>
        <div class="form-group" id="business-query-option"><label>绑定的查询参数</label><select><option value="NAME">默认</option></select></div>
      </div>
      <div class="pb-canvas"><label>显示名称</label>文本框</div>
      <div class="ud-toolbar"><button title="Font">宋体</button><div style="float:right">报表:模板预览.ureport.xml</div></div>
      <hr class="ureport-right-hr-for-print" title="打印线">`)
    const reportDef = { cells: [{ value: '预览', fontFamily: '宋体' }], searchForm: { components: [{ label: '文本框', value: '默认' }] }, datasources: [{ name: '数据集' }] }
    ;(report.window as any).__current_report_def = reportDef
    const serializedDefinition = JSON.stringify(reportDef)
    report.window.eval(bootstrap)
    let calls = 0
    ;(report.window as any).buildPaging = (value: string) => {
      calls++
      report.window.document.getElementById('pageLinkContainer')!.innerHTML = `<button>${value}</button>`
      return 42
    }
    expect((report.window as any).buildPaging('下一页')).toBe(42)
    expect(calls).toBe(1)
    report.window.document.dispatchEvent(new report.window.Event('DOMContentLoaded'))
    await new Promise(resolve => setTimeout(resolve, 10))
    const doc = report.window.document
    expect(doc.querySelector('#_workorder_report_toolbar button')!.getAttribute('title')).toBe('Export Excel')
    expect(doc.getElementById('pageLinkContainer')!.textContent).toBe('Next')
    expect(doc.querySelector('#pb_component_property_palette label')!.textContent).toBe('Label')
    expect(doc.querySelector('.pb-palette .nav-tabs')!.textContent).toBe('Components')
    expect(doc.querySelector('#pb_component_property_palette input')!.getAttribute('value')).toBe('预览')
    expect(doc.querySelector('#pb_component_property_palette option')!.textContent).toBe('文本框')
    expect(doc.querySelector('#native-fixed-style option')!.textContent).toBe('Default')
    expect(doc.querySelector('#native-fixed-style option')!.getAttribute('value')).toBe('btn-default')
    expect(doc.querySelector('#business-query-option option')!.textContent).toBe('默认')
    expect(doc.querySelector('.pb-canvas')!.textContent).toBe('显示名称文本框')
    expect(doc.getElementById('_ureport_table')!.textContent).toBe('预览业务数据')
    expect(doc.querySelector('#_ureport_table button')!.getAttribute('title')).toBe('导出EXCEL')
    expect(doc.querySelector('.ud-toolbar > div')!.textContent).toBe('Report: 模板预览.ureport.xml')
    expect(doc.querySelector('.ud-toolbar > button')!.textContent).toBe('宋体')
    expect(doc.querySelector('.ud-toolbar > button')!.getAttribute('aria-label')).toBe('Font')
    expect(doc.querySelector('.ureport-right-hr-for-print')!.getAttribute('title')).toBe('Print boundary')
    expect(JSON.stringify((report.window as any).__current_report_def)).toBe(serializedDefinition)
    const newLabel = doc.createElement('label'); newLabel.textContent = '日期格式'
    doc.getElementById('pb_component_property_palette')!.appendChild(newLabel)
    await new Promise(resolve => setTimeout(resolve, 10))
    expect(newLabel.textContent).toBe('Date format')
  })

  it('preserves native query parameters when adding the strict locale', () => {
    const path = reportLocaleUrl('/ureport/designer?_u=file%3ATEMPLATE&_i=2#cell', 'en-US', 123)
    const parsed = new URL(path, window.location.href)
    expect(parsed.searchParams.get('_u')).toBe('file:TEMPLATE')
    expect(parsed.searchParams.get('_i')).toBe('2')
    expect(parsed.searchParams.get('ui_locale')).toBe('en-US')
    expect(parsed.searchParams.get('_t')).toBe('123')
    expect(parsed.hash).toBe('#cell')
  })

  it('does not normalize raw native filenames, spaces, escaped values or repeated business parameters', () => {
    const raw = '_u=file%3AMy%20Template&_i=2&NAME=First%20value&NAME=Second+value&escaped=%2520'
    const path = reportLocaleUrl(`/ureport/designer?${raw}&ui_locale=zh-CN&_t=OLD`, 'en-US', 123)
    expect(path).toBe(`/ureport/designer?${raw}&ui_locale=en-US&_t=123`)
  })

  it('reads native dirty state and only preserves the same native designer URL', () => {
    const report = nativeDocument('<button id="__save_btn" class="disabled"></button>', '?_u=file%3ATEMPLATE')
    const frame = { contentDocument: report.window.document, contentWindow: report.window } as unknown as HTMLIFrameElement
    expect(reportHasUnsavedChanges(frame)).toBe(false)
    report.window.document.getElementById('__save_btn')!.classList.remove('disabled')
    expect(reportHasUnsavedChanges(frame)).toBe(true)
    expect(currentReportPath(frame, 'https://reports.test/ureport/designer')).toContain('_u=file%3ATEMPLATE')
    expect(currentReportPath(frame, 'https://other.test/ureport/designer')).toBe('')
    expect(currentReportPath(frame, 'https://reports.test/ureport/preview')).toBe('')
  })
})

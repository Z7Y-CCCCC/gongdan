/* UReport 2.2.9 integration. This trusted script runs before the native bundles. */
;(function () {
  'use strict'
  // Native report documents are same-origin, but never change the parent navigator.
  var locale = 'zh-CN'
  try { locale = localStorage.getItem('heihu_locale') === 'en-US' ? 'en-US' : 'zh-CN' } catch (_) {}
  var requestedLocale = new URL(window.location.href).searchParams.get('ui_locale')
  if (requestedLocale === 'en-US' || requestedLocale === 'zh-CN') {
    locale = requestedLocale
    // Native preview windows inherit only this origin's locale, never app tokens.
    try { localStorage.setItem('heihu_locale', locale) } catch (_) {}
  }
  try { Object.defineProperty(window.navigator, 'language', { configurable: true, get: function () { return locale } }) } catch (_) {}
  document.documentElement.lang = locale
  document.documentElement.setAttribute('data-workorder-report-locale', locale)

  // 2.2.9's English toolbar dictionary accidentally calls the Undo key "redo".
  // Supplement only the missing UI label; keep all native actions/data intact.
  var nativeI18n
  Object.defineProperty(window, 'i18n', {
    configurable: true,
    get: function () { return nativeI18n },
    set: function (value) {
      if (locale === 'en-US' && value && value.tools && value.tools.undo && !value.tools.undo.undo) {
        value.tools.undo.undo = value.tools.undo.redo || 'Undo'
      }
      nativeI18n = value
      // The bundle owns a second jQuery ready queue. Wait until its synchronous
      // native editor construction finishes instead of racing the outer queue.
      scheduleReady()
    }
  })
  var nativeFormBuilder
  Object.defineProperty(window, 'formBuilder', {
    configurable: true,
    get: function () { return nativeFormBuilder },
    set: function (value) { nativeFormBuilder = value; scheduleReady() }
  })

  var messages = {
    '在线打印': 'Print', 'PDF在线打印': 'Print PDF', 'PDF在线预览打印': 'Preview and print PDF',
    '导出PDF': 'Export PDF', '导出WORD': 'Export Word', '导出EXCEL': 'Export Excel',
    '分页导出EXCEL': 'Export paged Excel', '分页分Sheet导出EXCEL': 'Export Excel with page sheets',
    '预览': 'Preview', '分页预览': 'Paged preview', '上一页': 'Previous', '下一页': 'Next',
    '共': 'Total ', '页': ' pages', '组件': 'Components', '属性': 'Properties',
    '一列布局': 'One column', '两列布局': 'Two columns', '三列布局': 'Three columns',
    '四列布局': 'Four columns', '自定义列布局': 'Custom columns', '文本框': 'Text input',
    '日期选择框': 'Date picker', '单选框': 'Radio group', '复选框': 'Checkbox group',
    '单选列表': 'Select', '提交按钮': 'Submit button', '重置按钮': 'Reset button',
    '选项换行显示': 'Wrap options', '绑定的查询参数': 'Query parameter', '显示名称': 'Label',
    '标签位置': 'Label position', '选项设置': 'Options', '布局': 'Layout', '显示边框': 'Show border',
    '边框宽度': 'Border width', '边框颜色': 'Border color', '日期格式': 'Date format',
    '对齐方式': 'Alignment', '按钮类型': 'Button type', '按钮风格': 'Button style',
    '数据源类型': 'Data source type', '数据集': 'Dataset', '标签字段': 'Label field',
    '值字段': 'Value field', '值': 'Value', '操作': 'Action', '名称': 'Name', '类型': 'Type',
    '保存': 'Save', '保存成功!': 'Saved', '删除': 'Delete', '删除选中组件': 'Delete selected component',
    '清空': 'Clear', '页面属性': 'Page properties', '表单位置': 'Form position',
    '标题': 'Label', '标题位置': 'Label position', '按钮标题': 'Button label',
    '显示边线': 'Show border', '边线宽度': 'Border width', '边线颜色': 'Border color',
    '数据来源': 'Data source', '固定值选项': 'Fixed options', '显示值字段名': 'Label field',
    '实际值字段名': 'Value field', '查询表单位置': 'Query form position',
    '删除选中的元素': 'Delete selected component', '请先选择一个组件': 'Select a component first',
    '请输入列信息': 'Enter column sizes', '单位': 'Unit', '是': 'Yes', '否': 'No',
    '左边': 'Left', '上边': 'Top', '左对齐': 'Left', '右对齐': 'Right',
    '固定值': 'Fixed values', '基本': 'Primary', '默认': 'Default', '成功': 'Success',
    '信息': 'Info', '警告': 'Warning', '危险': 'Danger', '链接': 'Link',
    '预览工具栏之上': 'Above the preview toolbar', '预览工具栏之下': 'Below the preview toolbar',
    '边线宽度(单位px)': 'Border width (px)'
  }
  function translateText(root) {
    if (locale !== 'en-US' || !root) return
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
    var node
    while ((node = walker.nextNode())) {
      if (node.parentElement && node.parentElement.closest('input, textarea, [contenteditable], script, style')) continue
      var source = node.nodeValue.trim()
      if (Object.prototype.hasOwnProperty.call(messages, source)) node.nodeValue = node.nodeValue.replace(source, messages[source])
    }
  }
  function localizePreview() {
    if (locale !== 'en-US') return
    var url = new URL(window.location.href)
    if (url.pathname.endsWith('/ureport/preview') && url.searchParams.get('_u') === 'p' && document.title === '设计中报表-ureport') {
      document.title = 'Draft report - UReport'
    }
    var toolbar = document.getElementById('_workorder_report_toolbar')
    if (!toolbar) return
    translateText(toolbar)
    var total = toolbar.querySelector('#totalPageLabel')
    var pageWord = total && total.nextSibling
    var count = total && Number(total.textContent.trim())
    if (pageWord && pageWord.nodeType === 3 && Number.isSafeInteger(count) && count > 0 && /^\s*pages?\s*$/.test(pageWord.nodeValue)) {
      pageWord.nodeValue = pageWord.nodeValue.replace(/pages?/, count === 1 ? 'page' : 'pages')
    }
    toolbar.querySelectorAll('[title]').forEach(function (button) {
      var source = button.getAttribute('title')
      if (Object.prototype.hasOwnProperty.call(messages, source)) {
        button.setAttribute('title', messages[source])
        button.setAttribute('aria-label', messages[source])
      }
    })
  }
  // Paging builds new controls. Wrap that named native function, not report cells.
  var nativeBuildPaging
  Object.defineProperty(window, 'buildPaging', {
    configurable: true,
    get: function () { return nativeBuildPaging },
    set: function (value) {
      nativeBuildPaging = typeof value === 'function' ? function () {
        var result = value.apply(this, arguments)
        localizePreview()
        return result
      } : value
    }
  })
  var observers = []
  var observedRoots = new WeakSet()
  var readyTimer
  var readyAttempts = 0
  function scheduleReady() {
    if (readyTimer !== undefined) return
    readyTimer = setTimeout(checkReady, 0)
  }
  function checkReady() {
    readyTimer = undefined
    ready()
    // Native designer creates controls only after its initial template AJAX.
    // A bounded lookup of known UI roots is not a report/body DOM observer.
    var waitsForDesigner = document.querySelector('script[src$="/designer.bundle.js"]') && !document.querySelector('.ud-toolbar.top-toolbar')
    var waitsForSearchForm = document.querySelector('script[src$="/searchform.bundle.js"]') && !document.getElementById('pb_component_container_palette')
    if ((waitsForDesigner || waitsForSearchForm) && readyAttempts++ < 200) readyTimer = setTimeout(checkReady, 100)
  }
  function localizeDesignerControls() {
    document.querySelectorAll('.ud-toolbar').forEach(function (toolbar) {
      if (locale === 'en-US') toolbar.querySelectorAll(':scope > div').forEach(function (info) {
        // Native FileInfo has no class. Match its exact owned placement and only
        // replace the fixed prefix; the actual template filename stays intact.
        if (info.style.cssFloat !== 'right' || !info.firstChild || info.firstChild.nodeType !== 3) return
        var value = info.firstChild.nodeValue
        if (value.indexOf('报表:') === 0) info.firstChild.nodeValue = 'Report: ' + value.slice(3)
      })
      toolbar.querySelectorAll('button[title], a[title]').forEach(function (button) {
        var label = button.getAttribute('title')
        if (label) button.setAttribute('aria-label', label)
      })
    })
    if (locale === 'en-US') document.querySelectorAll('.ureport-right-hr-for-print[title="打印线"]').forEach(function (line) {
      line.setAttribute('title', 'Print boundary')
      line.setAttribute('aria-label', 'Print boundary')
    })
  }
  function localizeSearchControls() {
    // Never translate the form canvas, field values or datasource option names.
    document.querySelectorAll('.pb-toolbar, .pb-palette > .nav-tabs, #pb_component_container_palette').forEach(translateText)
    var palette = document.getElementById('pb_component_property_palette')
    if (!palette) return
    palette.querySelectorAll('label').forEach(translateText)
    // Only these native fixed-option controls are translated. Query parameters,
    // datasets and user-supplied field/option names are explicitly not included.
    var fixedOptionLabels = ['Label position', 'Button style', 'Alignment', 'Data source', 'Query form position', 'Wrap options', 'Show border']
    palette.querySelectorAll('.form-group').forEach(function (group) {
      var label = group.querySelector('label')
      if (!label || fixedOptionLabels.indexOf(label.textContent.trim()) < 0) return
      group.querySelectorAll('select > option').forEach(translateText)
    })
  }
  function ready() {
    localizePreview()
    localizeDesignerControls()
    localizeSearchControls()
    if (locale !== 'en-US') return
    // The observer is limited to native property/toolbar UI, never report content.
    document.querySelectorAll('.pb-toolbar, #pb_component_property_palette').forEach(function (root) {
      if (observedRoots.has(root)) return
      observedRoots.add(root)
      var observer = new MutationObserver(localizeSearchControls)
      observer.observe(root, { subtree: true, childList: true, characterData: true })
      observers.push(observer)
    })
    document.querySelectorAll('.ud-toolbar').forEach(function (toolbar) {
      if (observedRoots.has(toolbar)) return
      observedRoots.add(toolbar)
      var observer = new MutationObserver(localizeDesignerControls)
      observer.observe(toolbar, { subtree: true, childList: true, characterData: true })
      observers.push(observer)
    })
  }
  if (window.jQuery) window.jQuery(document).ready(scheduleReady)
  else document.addEventListener('DOMContentLoaded', scheduleReady, { once: true })
  window.addEventListener('pagehide', function () { clearTimeout(readyTimer); observers.forEach(function (observer) { observer.disconnect() }) }, { once: true })
})()

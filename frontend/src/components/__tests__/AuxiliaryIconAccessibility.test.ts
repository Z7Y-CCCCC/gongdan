import { describe, expect, it } from 'vitest'
import { parse } from '@vue/compiler-sfc'
import { baseParse } from '@vue/compiler-dom'
import iconSource from '../UiIcon.vue?raw'

const sources = import.meta.glob<string>([
  '../AdvancedOperationsPage.vue', '../BaseDataPage.vue', '../CacheListPage.vue', '../CacheMonitorPage.vue',
  '../CustomReceiptFieldsPage.vue', '../CustomWorkOrderFieldsPage.vue', '../DepartmentManagementPage.vue',
  '../ExternalDataSourcePage.vue', '../FactorySettingsPage.vue', '../InternalChatPage.vue',
  '../NotificationCenterPage.vue', '../OnlineUsersPage.vue', '../PluginCenterPage.vue', '../PluginRuntimePage.vue',
  '../SuperAdminAuthCodesPage.vue', '../UserManagementPage.vue', '../UReportPage.vue', '../LoginPage.vue', '../BindFactoryPage.vue'
], { query: '?raw', import: 'default', eager: true })

function walk(node: any, visit: (node: any) => void) {
  visit(node)
  for (const child of node.children || []) walk(child, visit)
}
function readableText(node: any): string {
  if (node.type === 2) return node.content.trim()
  if (node.type === 5) return '{{dynamic text}}'
  return (node.children || []).map(readableText).join('')
}
function attribute(node: any, name: string) {
  return node.props.find((property: any) => (property.type === 6 && property.name === name)
    || (property.type === 7 && property.name === 'bind' && property.arg?.content === name))
}

describe('辅助页图标动作的可访问名称', () => {
  it.each(Object.entries(sources))('%s 的纯图标按钮保留可见提示和读屏名称', (file, source) => {
    const result = parse(source)
    expect(result.errors, file).toEqual([])
    const ast = baseParse(result.descriptor.template!.content)
    walk(ast, node => {
      if (node.tag !== 'button' || readableText(node) || !/<UiIcon|<svg/.test(node.loc.source)) return
      for (const name of ['title', 'aria-label']) {
        const value = attribute(node, name)
        expect(value?.value?.content || value?.exp?.content, `${file}: ${name}: ${node.loc.source}`).toBeTruthy()
        if (value?.value?.content) expect(/[\p{L}\p{N}]/u.test(value.value.content), `${file}: ${name}`).toBe(true)
      }
      expect(attribute(node, 'class')?.value?.content, file).toContain('ui-icon-button')
      expect(node.loc.source, file).toContain('<UiIcon')
    })
  })

  it('使用现有图标字典，不退化成未知图标', () => {
    const names = new Set(Array.from(iconSource.matchAll(/^\s*(?:'([^']+)'|([\w-]+)):\s*'/gm), match => match[1] || match[2]))
    for (const [file, source] of Object.entries(sources)) {
      const template = parse(source).descriptor.template!.content
      for (const match of template.matchAll(/<UiIcon\s+name="([^"]+)"/g)) {
        expect(names.has(match[1]), `${file}: ${match[1]}`).toBe(true)
      }
    }
  })

  it('非通用动作图标保留极简动作名，避免猜测含义', () => {
    const labels = new Set(['测试连接', '表与字段', '记录', '健康检查', '导入日志', '批量编辑', '恢复'])
    for (const [file, source] of Object.entries(sources)) {
      const ast = baseParse(parse(source).descriptor.template!.content)
      walk(ast, node => {
        if (node.tag !== 'button' || !labels.has(attribute(node, 'aria-label')?.value?.content)) return
        expect(readableText(node), file).toBeTruthy()
      })
    }
  })

  it('登录仅保留品牌和登录表单，模式切换可用键盘操作', () => {
    const template = parse(sources['../LoginPage.vue']).descriptor.template!.content
    expect(template).not.toContain('login-hero')
    expect(template).not.toContain('hero-description')
    expect(template).toContain('<h1>数字工单通</h1>')
    const ast = baseParse(template)
    const switches: any[] = []
    walk(ast, node => {
      const click = node.props?.find((property: any) => property.type === 7 && property.name === 'on' && property.arg?.content === 'click')
      if (click?.exp?.content.startsWith('loginTab =') || click?.exp?.content.startsWith('phoneLoginMode =')) switches.push(node)
    })
    expect(switches).toHaveLength(4)
    expect(switches.every(node => node.tag === 'button' && attribute(node, 'type')?.value?.content === 'button')).toBe(true)
  })
})

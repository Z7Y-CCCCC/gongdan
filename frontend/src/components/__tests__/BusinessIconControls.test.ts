import { parse } from '@vue/compiler-sfc'
import { baseParse, NodeTypes, type ElementNode, type RootNode, type TemplateChildNode } from '@vue/compiler-dom'
import { describe, expect, it } from 'vitest'

const pages = ['BomPage', 'CustomerPage', 'SupplierPage', 'WarehousePage', 'WorkOrderPage', 'SalesOrderPage',
  'InboundReceiptPage', 'OutboundReceiptPage', 'InventoryBalancePage', 'InventoryDetailPage', 'InventorySummaryPage',
  'ReportApprovalPage', 'ProductDefinitionPage', 'TaskPage', 'ReportCenterPage', 'DashboardPage', 'ProcessTaskSection']
const sources = import.meta.glob<string>('../*.vue', { query: '?raw', import: 'default', eager: true })

function source(name: string) {
  return sources[`../${name}.vue`]
}
function elements(node: RootNode | TemplateChildNode): ElementNode[] {
  if (node.type !== NodeTypes.ROOT && node.type !== NodeTypes.ELEMENT) return []
  return [...(node.type === NodeTypes.ELEMENT ? [node] : []), ...node.children.flatMap(elements)]
}
function property(node: ElementNode, name: string) {
  return node.props.find(prop => prop.type === NodeTypes.ATTRIBUTE ? prop.name === name :
    prop.name === 'bind' && prop.arg?.type === NodeTypes.SIMPLE_EXPRESSION && prop.arg.content === name)
}

describe('compact business icon controls', () => {
  it.each(pages)('%s preserves labeled, non-empty icon buttons', name => {
    const parsed = parse(source(name))
    expect(parsed.errors).toEqual([])
    const buttons = elements(baseParse(parsed.descriptor.template!.content)).filter(node =>
      node.tag === 'button' && node.props.some(prop => prop.type === NodeTypes.ATTRIBUTE && prop.name === 'class' &&
        prop.value?.content.split(/\s+/).includes('ui-icon-button')))
    expect(buttons.length).toBeGreaterThan(0)
    for (const button of buttons) {
      expect(property(button, 'title'), `${name}: hover label`).toBeDefined()
      expect(property(button, 'aria-label'), `${name}: accessible label`).toBeDefined()
      expect(button.children.some(node => node.type === NodeTypes.ELEMENT), `${name}: icon`).toBe(true)
      expect(button.children.filter(node => node.type === NodeTypes.TEXT).map(node => node.content).join('').trim()).toBe('')
    }
  })

  it('retains submission feedback, required rejection reasons, and the visible truncation notice', () => {
    expect(source('BomPage')).toContain("saving ? '保存中...' : '保存'")
    expect(source('WorkOrderPage')).toContain("uploadingFieldId === field.id ? '上传中...' : '上传'")
    expect(source('ReportApprovalPage')).toContain('decisionPending || !rejectRemark.trim()')
    expect(source('WorkOrderProgressSection')).toContain('仅显示前500条')
    expect(source('ReportCenterPage')).not.toContain('汇总当前工厂的工单产出、报工审批和人员产出。')
  })
})

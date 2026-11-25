import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import ExcelJS from 'exceljs'
import WorkOrderPage from '../WorkOrderPage.vue'

const api = vi.hoisted(() => Object.fromEntries(['fetchWorkOrders', 'fetchProducts', 'fetchProcesses', 'fetchProcessRoutes',
  'fetchCustomFields', 'fetchWorkOrderCategories', 'fetchBoms', 'fetchUserPreference', 'fetchSystemImportLogs',
  'fetchSystemOperationLogs'].map(name => [name, vi.fn()])))
const output = vi.hoisted(() => ({ saveAs: vi.fn(), bytes: [] as any[] }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
vi.mock('file-saver', () => ({ saveAs: output.saveAs }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,fixture') } }))
vi.mock('exceljs', async original => {
  const module = await original<typeof import('exceljs')>()
  const base = (module as unknown as { default?: typeof module }).default || module
  class Workbook extends base.Workbook {
    constructor() {
      super()
      const write = this.xlsx.writeBuffer.bind(this.xlsx)
      this.xlsx.writeBuffer = async options => {
        const bytes = await write(options)
        output.bytes.push(bytes)
        return bytes
      }
    }
  }
  return { ...module, default: { ...base, Workbook } }
})
const wrappers: VueWrapper<any>[] = []
const original = '业务 _x0041_ / _x000A_ / _x005F_x0041_\rCR\r\nCRLF\nLF 😀 =1+1'
async function page(template: string, name: any = original) {
  const wrapper = shallowMount(WorkOrderPage, { props: { token: 'fixture-one' } })
  wrappers.push(wrapper); await flushPromises()
  const vm = wrapper.vm as any
  vm.workOrders = [{ id: 1, code: 'WO-1', productId: 1, productCode: '=1+1', productName: name,
    plannedQuantity: 12, goodQuantity: 2, defectQuantity: 0, status: 'EXECUTING', priority: 'HIGH',
    tasks: [{ sequenceNo: 1, processName: original, plannedQuantity: 12, completedQuantity: 0 }],
    materials: [{ materialCode: '=1+1', materialName: original, materialSpec: original, quantity: 1_234_567_890_123_456 }],
    customValues: [{ fieldId: 99, fieldName: original, value: original }] }]
  vm.customFields = [{ id: 99, name: original, scope: 'workorder', fieldType: 'text' }]
  vm.selectedIds = [1]; vm.selectedExcelTpl = template; vm.showPrintModal = true
  return { wrapper, vm }
}
beforeEach(() => {
  vi.resetAllMocks(); output.bytes.length = 0
  for (const mock of Object.values(api)) mock.mockResolvedValue({ items: [], total: 0 })
  api.fetchWorkOrderCategories.mockResolvedValue([])
  api.fetchUserPreference.mockResolvedValue({ value: null })
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()) })

describe('the actual printed XLSX serializer, not only in-memory cells', () => {
  it.each(['flow', 'list', 'qr', 'materials'])('preserves original business text and text types in %s', async template => {
    const { vm } = await page(template)
    await vm.doExcelPrint('download')
    expect(output.saveAs).toHaveBeenCalledTimes(1)
    expect(output.bytes).toHaveLength(1)
    const reopened = new ExcelJS.Workbook()
    await reopened.xlsx.load(output.bytes[0])
    const values: unknown[] = []
    for (const sheet of reopened.worksheets) sheet.eachRow(row => row.eachCell(cell => {
      values.push(cell.value)
      expect(cell.formula).toBeUndefined()
      expect(cell.hyperlink).toBeUndefined()
    }))
    expect(values).toContain(original)
    if (template !== 'qr') expect(values).toContain('=1+1')
    if (template === 'materials' || template === 'flow') expect(values).toContain('1234567890123456')
    expect(vm.showPrintModal).toBe(false)
  })
  it.each(['a'.repeat(32_768), '\u0000原文', '\ud800', { formula: '1+1', result: 2 }])('rejects unrepresentable/active cell input #%# without a partial download', async name => {
    const { wrapper, vm } = await page('list', name)
    await vm.doExcelPrint('download'); await flushPromises()
    expect(output.saveAs).not.toHaveBeenCalled()
    expect(output.bytes).toHaveLength(0)
    expect(vm.showPrintModal).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('生成 Excel 失败')
  })
})

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import WorkOrderPage from '../WorkOrderPage.vue'
import { setUiLocale } from '../../i18n'

const api = vi.hoisted(() => Object.fromEntries(['fetchUnits', 'fetchDefects', 'fetchDepartments', 'fetchProcesses',
  'fetchProcessRoutes', 'fetchSystemImportLogs', 'fetchWorkOrders', 'fetchProducts', 'fetchCustomFields',
  'fetchWorkOrderCategories', 'fetchBoms', 'fetchUserPreference'].map(name => [name, vi.fn()])))
const output = vi.hoisted(() => ({ saveAs: vi.fn(), writeBuffer: vi.fn(), qr: vi.fn(), workbooks: [] as any[] }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
vi.mock('file-saver', () => ({ saveAs: output.saveAs }))
vi.mock('qrcode', () => ({ default: { toDataURL: output.qr } }))
vi.mock('exceljs', async original => {
  const module = await original<typeof import('exceljs')>()
  const base = (module as unknown as { default?: typeof module }).default || module
  class Workbook extends base.Workbook {
    constructor() {
      super()
      this.xlsx.writeBuffer = output.writeBuffer
      output.workbooks.push(this)
    }
  }
  return { ...module, default: { ...base, Workbook } }
})

function deferred<T = any>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const order = (id = 1) => ({ id, code: `WO-${id}`, productId: 1, productCode: 'P1', productName: `原产品-${id}`,
  productSpec: '', plannedQuantity: 12, goodQuantity: 2, defectQuantity: 0, status: 'EXECUTING', priority: 'HIGH',
  tasks: [], materials: [], customValues: [{ fieldId: 99, fieldName: '原字段', value: '原值' }] })
const wrappers: VueWrapper<any>[] = []
async function page() {
  const wrapper = shallowMount(WorkOrderPage, { props: { token: 'fixture-one' } })
  wrappers.push(wrapper)
  await flushPromises()
  const vm = wrapper.vm as any
  vm.workOrders = [order(1), order(2)]
  vm.selectedIds = [1]
  vm.selectedExcelTpl = 'list'
  vm.customFields = [{ id: 99, name: '原字段', scope: 'workorder', fieldType: 'text' }]
  await flushPromises()
  output.qr.mockClear()
  return wrapper
}
// Dependency bootstrap is setup, not part of the pending-operation deadline.
beforeAll(async () => { await import('exceljs') })
beforeEach(() => {
  vi.resetAllMocks()
  output.workbooks.length = 0
  for (const method of Object.values(api)) method.mockResolvedValue({ items: [], total: 0 })
  api.fetchWorkOrderCategories.mockResolvedValue([])
  api.fetchUserPreference.mockResolvedValue({ value: null })
  output.writeBuffer.mockResolvedValue(new Uint8Array([1, 2, 3]))
  output.qr.mockResolvedValue('data:image/png;base64,fixture')
  setUiLocale('zh-CN')
})
afterEach(() => {
  wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('work-order printing pending-operation and context boundaries', () => {
  it('sends one Excel generation while pending, locks print controls, and permits retry without losing the modal', async () => {
    const wrapper = await page(), vm = wrapper.vm as any
    vm.showPrintModal = true
    const pending = deferred()
    output.writeBuffer.mockReturnValueOnce(pending.promise)
    const first = vm.doExcelPrint('download'), second = vm.doExcelPrint('wps')
    await vi.waitFor(() => expect(output.writeBuffer).toHaveBeenCalledTimes(1), { timeout: 5000 })
    expect(vm.exporting).toBe(true)
    expect(wrapper.get('button[aria-label="下载打印"]').attributes('disabled')).toBeDefined()
    pending.reject(new Error('生成暂时失败'))
    await Promise.all([first, second]); await flushPromises()
    expect(vm.exporting).toBe(false)
    expect(vm.showPrintModal).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('生成暂时失败')
    await vm.doExcelPrint('download')
    expect(output.writeBuffer).toHaveBeenCalledTimes(2)
    expect(output.saveAs).toHaveBeenCalledTimes(1)
    expect(vm.showPrintModal).toBe(false)
  })

  it.each(['session', 'unmount'])('suppresses a completed Excel download after %s changes', async change => {
    const wrapper = await page(), vm = wrapper.vm as any
    vm.showPrintModal = true
    const pending = deferred()
    output.writeBuffer.mockReturnValueOnce(pending.promise)
    const first = vm.doExcelPrint('download')
    await vi.waitFor(() => expect(output.writeBuffer).toHaveBeenCalledTimes(1), { timeout: 5000 })
    if (change === 'session') {
      await wrapper.setProps({ token: 'fixture-two' })
      vm.showPrintModal = true // a new-session dialog must not be closed by the old task
    } else wrapper.unmount()
    pending.resolve(new Uint8Array([1, 2, 3]))
    await first; await flushPromises()
    expect(output.saveAs).not.toHaveBeenCalled()
    expect(wrapper.emitted('notice')).toBeUndefined()
    if (change === 'session') expect(vm.showPrintModal).toBe(true)
  })

  it('captures the template, custom headers and nested business values before asynchronous imports', async () => {
    const wrapper = await page(), vm = wrapper.vm as any
    const first = vm.doExcelPrint('download')
    vm.selectedExcelTpl = 'qr'
    vm.customFields[0].name = '新字段'
    vm.workOrders[0].productName = '新产品'
    vm.workOrders[0].customValues[0].value = '新值'
    await first
    const sheet = output.workbooks[0].worksheets[0]
    const values: any[] = []
    sheet.eachRow((row: any) => row.eachCell((cell: any) => values.push(cell.value)))
    expect(values).toContain('原产品-1')
    expect(values).toContain('原字段')
    expect(values).toContain('原值')
    expect(values).not.toContain('新字段')
    expect(values).not.toContain('新产品')
  })

  it('prepares the print dialog once and does not open it for a departed account', async () => {
    const wrapper = await page(), vm = wrapper.vm as any
    const pending = deferred()
    output.qr.mockReturnValue(pending.promise)
    const first = vm.openPrint(), second = vm.openPrint()
    await flushPromises()
    expect(output.qr).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ token: 'fixture-two' })
    pending.resolve('data:image/png;base64,old-account')
    await Promise.all([first, second]); await flushPromises()
    expect(vm.showPrintModal).toBe(false)
    expect(vm.qrDataUrls).toEqual({})
    expect(vm.printPreparing).toBe(false)
  })

  it('keeps generated QR images bound to the latest selected orders', async () => {
    const wrapper = await page(), vm = wrapper.vm as any
    const old = deferred()
    output.qr.mockReturnValueOnce(old.promise)
    const first = vm.generateAllQrs()
    output.qr.mockResolvedValueOnce('data:image/png;base64,order-two')
    vm.selectedIds = [2]
    await flushPromises()
    old.resolve('data:image/png;base64,order-one')
    await first; await flushPromises()
    expect(vm.qrDataUrls).toEqual({ 2: 'data:image/png;base64,order-two' })
  })

  it('does not schedule duplicate native print calls', async () => {
    const wrapper = await page(), vm = wrapper.vm as any
    vi.useFakeTimers()
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    vm.doPrint(); vm.doPrint()
    vi.advanceTimersByTime(100)
    expect(print).toHaveBeenCalledTimes(1)
  })

  it.each(['session', 'unmount'])('cancels delayed native printing after %s changes', async change => {
    const wrapper = await page(), vm = wrapper.vm as any
    vi.useFakeTimers()
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    vm.doPrint()
    if (change === 'session') await wrapper.setProps({ token: 'fixture-two' })
    else wrapper.unmount()
    vi.advanceTimersByTime(100)
    expect(print).not.toHaveBeenCalled()
  })
})

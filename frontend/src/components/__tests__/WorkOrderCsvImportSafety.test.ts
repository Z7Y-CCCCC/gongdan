import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, KeepAlive } from 'vue'
import WorkOrderPage from '../WorkOrderPage.vue'

const api = vi.hoisted(() => Object.fromEntries(['fetchWorkOrders', 'fetchProducts', 'fetchProcesses', 'fetchProcessRoutes',
  'fetchCustomFields', 'fetchWorkOrderCategories', 'fetchBoms', 'fetchUserPreference', 'fetchSystemImportLogs',
  'fetchSystemOperationLogs', 'createWorkOrder', 'createSystemImportLog'].map(name => [name, vi.fn()])))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,fixture') } }))
const NativeFileReader = FileReader
const readers: TestReader[] = [], pickers: HTMLInputElement[] = [], wrappers: VueWrapper<any>[] = []
let autoRead = true
class TestReader {
  static LOADING = 1
  readyState = 0
  onload?: (event: any) => unknown
  onerror?: () => unknown
  onabort?: () => unknown
  error: Error | null = null
  text = ''
  finished = Promise.resolve<unknown>(undefined)
  abort = vi.fn(() => { this.readyState = 2; this.onabort?.() })
  readAsText(file: File & { fixtureText: string }) {
    this.text = file.fixtureText; this.readyState = 1; readers.push(this)
    if (autoRead) queueMicrotask(() => this.deliver())
  }
  deliver() {
    this.readyState = 2
    this.finished = Promise.resolve(this.onload?.({ target: { result: this.text } }))
    return this.finished
  }
}
function deferred<T = any>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(yes => { resolve = yes })
  return { promise, resolve }
}
const product = { id: 1, code: 'P1', name: '原产品', unitName: '件', inventoryQty: 0 }
const basicCsv = '工单编号,产品编号,计划数\nWO-1,P1,5\nWO-2,P1,6'
async function page(cached = false) {
  const props = { token: 'fixture-one' }
  if (!cached) {
    const wrapper = shallowMount(WorkOrderPage, { props })
    wrappers.push(wrapper); await flushPromises()
    return { wrapper, host: wrapper }
  }
  const Host = defineComponent({ props: { show: Boolean }, render() {
    return h(KeepAlive, null, { default: () => this.show ? h(WorkOrderPage, props) : null })
  } })
  const host = mount(Host, { props: { show: true }, global: { stubs: { UiIcon: true, TableConfigButton: true,
    DeleteConfirmDialog: true, SystemLogsDialog: true, ColumnSettingsPanel: true } } })
  wrappers.push(host); await flushPromises()
  return { wrapper: host.getComponent(WorkOrderPage), host }
}
async function choose(vm: any, csv: string) {
  vm.handleImport()
  const input = pickers[pickers.length - 1]
  const file = Object.assign(new File([csv], 'fixture.csv', { type: 'text/csv' }), { fixtureText: csv })
  Object.defineProperty(input, 'files', { configurable: true, value: [file] })
  input.dispatchEvent(new Event('change'))
  await flushPromises()
  return input
}
async function blobText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new NativeFileReader()
    reader.onload = () => resolve(new TextDecoder('utf-8', { ignoreBOM: true }).decode(reader.result as ArrayBuffer))
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(blob)
  })
}
beforeEach(() => {
  vi.resetAllMocks(); readers.length = 0; pickers.length = 0; autoRead = true
  for (const [name, mock] of Object.entries(api)) mock.mockResolvedValue(name.startsWith('fetch') ? { items: [], total: 0 } : {})
  api.fetchProducts.mockResolvedValue({ items: [product], total: 1 })
  api.fetchWorkOrderCategories.mockResolvedValue([])
  api.fetchUserPreference.mockResolvedValue({ value: null })
  vi.stubGlobal('FileReader', TestReader)
  vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(function (this: HTMLInputElement) { pickers.push(this) })
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('work-order raw CSV import complete parsing and captured session', () => {
  it('roundtrips quoted CRLF, comma, quotes, task/material/custom text and formula-looking business prefixes', async () => {
    const { wrapper } = await page(), vm = wrapper.vm as any
    const order = { id: 1, code: '=WO("1")', productId: 1, productCode: 'P1', productName: '原产品',
      plannedQuantity: 12, goodQuantity: 2, defectQuantity: 0, status: 'EXECUTING', priority: 'HIGH',
      remark: '含,逗号与"引号"\r\n第二行 =1+1', tasks: [{ processName: '工序\n名称', plannedQuantity: 12 }],
      materials: [{ materialName: '物料\n名称', quantity: 2 }], customValues: [{ fieldId: 99, fieldName: '补充', value: '+原文\n下一行' }] }
    vm.workOrders = [order]; vm.selectedIds = [1]
    vm.customFields = [{ id: 99, name: '补充', fieldType: 'text', scope: 'workorder' }]
    const create = vi.fn().mockReturnValue('blob:roundtrip')
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: vi.fn() }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vm.handleExport()
    const csv = await blobText(create.mock.calls[0][0])
    await choose(vm, csv); await readers[0].finished
    expect(api.createWorkOrder).toHaveBeenCalledTimes(1)
    const [token, payload] = api.createWorkOrder.mock.calls[0]
    expect(token).toBe('fixture-one')
    expect(payload.code).toBe(order.code); expect(payload.remark).toBe(order.remark)
    expect(payload.tasks[0].processName).toBe('工序\n名称')
    expect(payload.materials[0]).toMatchObject({ materialName: '物料\n名称', quantity: 2 })
    expect(payload.customValues[0].value).toBe('+原文\n下一行')
  })

  it('rejects an invalid final CSV record before writing a valid prefix or an import log', async () => {
    const { wrapper } = await page(), vm = wrapper.vm as any
    await choose(vm, '工单编号,产品编号,计划数\nWO-1,P1,5\n"WO-2,P1,6')
    await readers[0].finished
    expect(api.createWorkOrder).not.toHaveBeenCalled()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(wrapper.get('.import-error[role="alert"]').text()).toContain('引号未闭合')
    expect(vm.importing).toBe(false)
  })

  it.each(['session', 'deactivate', 'unmount'])('stops subsequent row writes and late completion UI after %s changes', async change => {
    const { wrapper, host } = await page(change === 'deactivate'), vm = wrapper.vm as any
    const pending = deferred(); api.createWorkOrder.mockReturnValueOnce(pending.promise)
    await choose(vm, basicCsv)
    expect(api.createWorkOrder).toHaveBeenCalledTimes(1)
    expect(vm.importing).toBe(true)
    if (change === 'session') await wrapper.setProps({ token: 'fixture-two' })
    else if (change === 'deactivate') await host.setProps({ show: false })
    else host.unmount()
    pending.resolve({ id: 1 }); await readers[0].finished; await flushPromises()
    expect(api.createWorkOrder).toHaveBeenCalledTimes(1)
    expect(api.createWorkOrder.mock.calls[0][0]).toBe('fixture-one')
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect((wrapper.emitted('notice') || []).flat().join(' ')).not.toContain('导入完成')
    expect(vm.importing).toBe(false)
  })

  it('invalidates a pending FileReader and does not import its delayed contents into a new account', async () => {
    const { wrapper } = await page(), vm = wrapper.vm as any
    autoRead = false
    await choose(vm, basicCsv)
    await wrapper.setProps({ token: 'fixture-two' })
    expect(readers[0].abort).toHaveBeenCalledTimes(1)
    await readers[0].deliver(); await flushPromises()
    expect(api.createWorkOrder).not.toHaveBeenCalled()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
  })

  it('opens one picker and reads one file while pending, then unlocks the real import control', async () => {
    const { wrapper } = await page(), vm = wrapper.vm as any
    vm.handleImport(); vm.handleImport()
    expect(pickers).toHaveLength(1)
    const pending = deferred(); api.createWorkOrder.mockReturnValueOnce(pending.promise)
    const input = pickers[0], file = Object.assign(new File([''], 'fixture.csv'), { fixtureText: '产品编号,计划数\nP1,5' })
    Object.defineProperty(input, 'files', { configurable: true, value: [file] })
    input.dispatchEvent(new Event('change')); input.dispatchEvent(new Event('change'))
    vm.handleImport(); await flushPromises()
    expect(readers).toHaveLength(1); expect(api.createWorkOrder).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[aria-label="导入工单"]').attributes('disabled')).toBeDefined()
    pending.resolve({ id: 1 }); await readers[0].finished; await flushPromises()
    expect(vm.importing).toBe(false)
    expect(wrapper.get('button[aria-label="导入工单"]').attributes('disabled')).toBeUndefined()
    vm.handleImport(); expect(pickers).toHaveLength(2)
  })

  it('reports real partial business failures without changing existing product/status/priority mappings', async () => {
    const { wrapper } = await page(), vm = wrapper.vm as any
    api.createWorkOrder.mockResolvedValueOnce({ id: 1 }).mockRejectedValueOnce(new Error('业务行异常'))
    await choose(vm, '工单编号,产品编号,计划数,状态,优先级\nWO-1,P1,5,运行中,高\nWO-2,MISSING,6,已暂停,加急\nWO-3,P1,7,未开始,低')
    await readers[0].finished
    expect(api.createWorkOrder).toHaveBeenCalledTimes(2)
    expect(api.createWorkOrder.mock.calls[0][1]).toMatchObject({ productId: 1, plannedQuantity: 5, status: 'EXECUTING', priority: 'HIGH' })
    expect(api.createWorkOrder.mock.calls[1][1]).toMatchObject({ plannedQuantity: 7, status: 'NOT_STARTED', priority: 'LOW' })
    expect(api.createSystemImportLog).toHaveBeenCalledWith('fixture-one', expect.objectContaining({
      totalCount: 3, successCount: 1, failCount: 2, status: '部分成功', errors: expect.stringContaining('业务行异常')
    }))
    expect((wrapper.emitted('notice') || []).flat().join(' ')).toContain('导入完成：成功 1 条，失败 2 条')
  })

  it('keeps a file-reading error visible and allows selecting a new file', async () => {
    const { wrapper } = await page(), vm = wrapper.vm as any
    autoRead = false; await choose(vm, basicCsv)
    readers[0].error = new Error('文件读取失败'); readers[0].onerror?.()
    await flushPromises()
    expect(wrapper.get('.import-error[role="alert"]').text()).toContain('文件读取失败')
    expect(vm.importing).toBe(false)
    vm.handleImport(); expect(pickers).toHaveLength(2)
  })
})

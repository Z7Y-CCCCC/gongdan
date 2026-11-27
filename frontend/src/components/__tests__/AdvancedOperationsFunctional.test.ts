import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import AdvancedOperationsPage from '../AdvancedOperationsPage.vue'

const mocks = vi.hoisted(() => ({ fetchAdvancedRecords: vi.fn(), fetchReports: vi.fn(), fetchWorkOrders: vi.fn(),
  createAdvancedRecord: vi.fn(), updateAdvancedRecord: vi.fn(), deleteAdvancedRecord: vi.fn(), sendAdvancedMessageTest: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...mocks }))
const record = (moduleName = 'SOP', id = 1, payload = {}) => ({ id, moduleName, title: `${moduleName}-${id}`,
  businessKey: '', status: 'ACTIVE', payloadJson: JSON.stringify(payload) })
let wrapper: any
function deferred() {
  let resolve!: (value?: any) => void
  let reject!: (reason?: any) => void
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function page() {
  wrapper = shallowMount(AdvancedOperationsPage, { props: { token: 'token-one' } })
  await flushPromises()
  return wrapper.vm as any
}

describe('advanced operations real functional paths', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.fetchAdvancedRecords.mockImplementation((_token, params) => Promise.resolve({ items: [record(params.moduleName)], total: 1 }))
    mocks.fetchReports.mockResolvedValue({ items: [], total: 0 })
    mocks.fetchWorkOrders.mockResolvedValue({ items: [], total: 0 })
    mocks.createAdvancedRecord.mockResolvedValue({})
    mocks.updateAdvancedRecord.mockResolvedValue({})
    mocks.deleteAdvancedRecord.mockResolvedValue({})
  })
  afterEach(() => wrapper?.unmount())

  it('lands on the requested section and announces genuine selection changes', async () => {
    wrapper = shallowMount(AdvancedOperationsPage, { props: { token: 'token-one', section: 'GANTT' } })
    await flushPromises()
    expect(wrapper.vm.activeTab).toBe('GANTT')
    expect(mocks.fetchAdvancedRecords).not.toHaveBeenCalled()
    expect(wrapper.get('button[aria-pressed="true"]').text()).toContain('排产甘特图')
    await wrapper.setProps({ section: 'FIELD_EVENT' })
    await flushPromises()
    expect(mocks.fetchAdvancedRecords).toHaveBeenLastCalledWith('token-one', expect.objectContaining({ moduleName: 'FIELD_EVENT' }))
    expect(wrapper.emitted('section-change')).toEqual([['FIELD_EVENT']])
  })

  it('does not overwrite the new module with a late previous-tab response', async () => {
    const old = deferred()
    mocks.fetchAdvancedRecords.mockReturnValueOnce(old.promise)
    const vm = await page()
    await vm.switchTab('QUALITY')
    old.resolve({ items: [record('SOP')], total: 1 })
    await flushPromises()
    expect(vm.records[0].moduleName).toBe('QUALITY')
    expect(vm.loading).toBe(false)
  })

  it('does not restore old account records after the token changes', async () => {
    const old = deferred()
    mocks.fetchAdvancedRecords.mockReturnValueOnce(old.promise)
    const vm = await page()
    mocks.fetchAdvancedRecords.mockResolvedValueOnce({ items: [record('SOP', 2)], total: 1 })
    await wrapper.setProps({ token: 'token-two' })
    await flushPromises()
    old.resolve({ items: [record('SOP', 1)], total: 1 })
    await flushPromises()
    expect(vm.records.map((entry: any) => entry.id)).toEqual([2])
  })

  it('loads all rule/report pages and respects the payroll effective month', async () => {
    const vm = await page()
    vm.payrollMonth = '2026-11'
    mocks.fetchAdvancedRecords.mockResolvedValueOnce({ items: [record('PAYROLL_RULE', 1,
      { processName: '加工', effectiveMonth: '2026-11', unitPrice: 2, defectPenalty: 1 })], total: 2 })
      .mockResolvedValueOnce({ items: [record('PAYROLL_RULE', 2,
        { processName: '加工', effectiveMonth: '2026-12', unitPrice: 9, defectPenalty: 1 })], total: 2 })
    mocks.fetchReports.mockResolvedValueOnce({ items: [{ id: 1, status: 'APPROVED', reporterName: '甲', processName: '加工',
      goodQuantity: 10, defectQuantity: 1, createdAt: '2026-11-02' }], total: 2 })
      .mockResolvedValueOnce({ items: [{ id: 2, status: 'APPROVED', reporterName: '甲', processName: '加工',
        goodQuantity: 20, defectQuantity: 0, createdAt: '2026-10-02' }], total: 2 })
    await vm.switchTab('PAYROLL')
    expect(vm.records).toHaveLength(2)
    expect(vm.reports).toHaveLength(2)
    expect(vm.payrollRows).toEqual([{ name: '甲', goodQuantity: 10, defectQuantity: 1, processCount: 1, amount: 19 }])
    expect(mocks.fetchReports.mock.calls.map(call => call[1].page)).toEqual([1, 2])
  })

  it('loads later work-order pages for the Gantt chart', async () => {
    const vm = await page()
    const order = (id: number) => ({ id, code: `WO-${id}`, productName: '产品', plannedQuantity: 10,
      planStart: '2026-11-02', planEnd: '2026-11-03' })
    mocks.fetchWorkOrders.mockResolvedValueOnce({ items: [order(1)], total: 2 }).mockResolvedValueOnce({ items: [order(2)], total: 2 })
    await vm.switchTab('GANTT')
    expect(vm.ganttItems.map((entry: any) => entry.id)).toEqual([1, 2])
  })

  it('locks duplicate saves and keeps the form draft after failure', async () => {
    const vm = await page()
    vm.openCreate()
    vm.form.title = 'SOP draft'
    const pending = deferred()
    mocks.createAdvancedRecord.mockReturnValueOnce(pending.promise)
    const first = vm.saveRecord()
    const second = vm.saveRecord()
    expect(mocks.createAdvancedRecord).toHaveBeenCalledTimes(1)
    pending.reject(new Error('保存服务不可用'))
    await Promise.all([first, second])
    expect(vm.showModal).toBe(true)
    expect(vm.form.title).toBe('SOP draft')
    expect(vm.saveError).toBe('保存服务不可用')
    expect(vm.saving).toBe(false)
  })

  it('serializes a row status change without duplicate requests', async () => {
    const vm = await page()
    const entry = vm.records[0]
    const pending = deferred()
    mocks.updateAdvancedRecord.mockReturnValueOnce(pending.promise)
    const first = vm.changeStatus(entry, 'ARCHIVED')
    const second = vm.changeStatus(entry, 'ARCHIVED')
    expect(mocks.updateAdvancedRecord).toHaveBeenCalledTimes(1)
    expect(vm.pendingRecords.has(entry.id)).toBe(true)
    pending.resolve()
    await Promise.all([first, second])
    expect(vm.pendingRecords.has(entry.id)).toBe(false)
  })

  it('uses the real send-test endpoint and shows a receiver failure rather than simulated success', async () => {
    const vm = await page()
    await vm.switchTab('MESSAGE')
    const entry = vm.records[0]
    const failed = { ...entry, status: 'FAILED' }
    const pending = deferred()
    mocks.sendAdvancedMessageTest.mockReturnValueOnce(pending.promise)
    const first = vm.sendTest(entry)
    const second = vm.sendTest(entry)
    expect(mocks.sendAdvancedMessageTest).toHaveBeenCalledExactlyOnceWith('token-one', entry.id)
    mocks.fetchAdvancedRecords.mockResolvedValueOnce({ items: [failed], total: 1 })
    pending.resolve({ success: false, message: '接收端返回 503', httpStatus: 503, record: failed })
    await Promise.all([first, second])
    expect(mocks.updateAdvancedRecord).not.toHaveBeenCalled()
    expect(vm.records[0].status).toBe('FAILED')
    expect(wrapper.emitted('notice')).toContainEqual(['接收端返回 503'])
  })

  it('keeps the previous record status when dispatch validation rejects the request', async () => {
    const vm = await page()
    await vm.switchTab('MESSAGE')
    const entry = vm.records[0]
    mocks.sendAdvancedMessageTest.mockRejectedValueOnce(new Error('未配置服务器推送凭据'))
    await vm.sendTest(entry)
    expect(vm.records[0].status).toBe('ACTIVE')
    expect(vm.pendingRecords.size).toBe(0)
    expect(wrapper.emitted('notice')).toContainEqual(['未配置服务器推送凭据'])
  })

  it('provides a retryable load error instead of a false empty module', async () => {
    mocks.fetchAdvancedRecords.mockRejectedValueOnce(new Error('网络故障'))
    const vm = await page()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络故障')
    expect(wrapper.find('.table-container').exists()).toBe(false)
    await wrapper.get('button[aria-label="重试加载高级运营"]').trigger('click')
    await flushPromises()
    expect(vm.loadError).toBe('')
    expect(wrapper.find('.table-container').exists()).toBe(true)
  })
})

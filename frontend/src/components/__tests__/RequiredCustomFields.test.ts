import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import WorkOrderPage from '../WorkOrderPage.vue'
import InboundReceiptPage from '../InboundReceiptPage.vue'
import OutboundReceiptPage from '../OutboundReceiptPage.vue'
import { setUiLocale } from '../../i18n'
const mocks = vi.hoisted(() => Object.fromEntries(['fetchWorkOrders', 'fetchProducts', 'fetchProcesses', 'fetchProcessRoutes',
  'fetchCustomFields', 'fetchWorkOrderCategories', 'fetchBoms', 'fetchUserPreference', 'fetchInboundReceipts', 'fetchOutboundReceipts',
  'createWorkOrder', 'createInboundReceipt', 'createOutboundReceipt'].map(name => [name, vi.fn()])))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...mocks }))
const field = (scope: string, id: number) => ({ id, scope, name: id === 101 ? '批次' : '明细批次', fieldType: 'text', required: true,
  allowUserOptions: false, defaultValues: [], options: [], sortOrder: 0 })
let wrapper: any
beforeEach(() => {
  vi.resetAllMocks()
  for (const [name, mock] of Object.entries(mocks)) mock.mockResolvedValue(name.startsWith('fetch') ? { items: [], total: 0 } : {})
  mocks.fetchProducts.mockResolvedValue({ items: [{ id: 1, name: '测试产品', code: 'P1', unitId: 1, inventoryQty: 10 }], total: 1 })
  mocks.fetchWorkOrderCategories.mockResolvedValue([])
  mocks.fetchUserPreference.mockResolvedValue({ value: null })
  mocks.fetchCustomFields.mockImplementation((_token, scope) => Promise.resolve({ items: [field(scope, scope === 'receipt_detail' ? 201 : 101)] }))
})
afterEach(() => { wrapper?.unmount(); setUiLocale('zh-CN') })
describe('business forms honor required custom fields before posting', () => {
  it('prints real attribution and units, never synthetic people or timestamps', async () => {
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'fixture-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.getFieldValue({ id: 1, code: 'WO-1' }, '创建人')).toBe('-')
    expect(vm.getFieldValue({ id: 1 }, '创建时间')).toBe('-')
    expect(vm.getFieldValue({ id: 1 }, '更新时间')).toBe('-')
    expect(vm.getFieldValue({ id: 1, unitName: '米' }, '单位')).toBe('米')
    expect(vm.getFieldValue({ id: 1, code: 'WO-1' }, '工单外链')).toContain('/#/pages/work-order-detail/index?id=1&code=WO-1')
  })
  it('preserves printed business attribution and units in English mode', async () => {
    setUiLocale('en-US')
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'fixture-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.getFieldValue({ creator: '真实员工' }, '创建人')).toBe('真实员工')
    expect(vm.getFieldValue({ unitName: '米' }, '单位')).toBe('米')
    expect(vm.getFieldValue({ code: '业务编号1' }, '工单编号')).toBe('业务编号1')
  })
  it('keeps the work-order form on custom fields when a required text is blank', async () => {
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'fixture-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openCreate()
    vm.form.productId = 1
    vm.form.plannedQuantity = 2
    await vm.saveOrder()
    expect(mocks.createWorkOrder).not.toHaveBeenCalled()
    expect(vm.activeTab).toBe('custom')
    expect(wrapper.emitted('notice')).toContainEqual(['请填写必填字段「批次」'])
  })
  it.each([
    { name: '入库', component: InboundReceiptPage, method: 'handleSave', type: 'inboundType', time: 'inboundTime', write: 'createInboundReceipt' },
    { name: '出库', component: OutboundReceiptPage, method: 'submitCreate', type: 'outboundType', time: 'outboundTime', write: 'createOutboundReceipt' }
  ])('$name validates both header and detail values', async ({ component, method, type, time, write }) => {
    wrapper = shallowMount(component, { props: { token: 'fixture-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.createForm[type] = '生产'
    vm.createForm[time] = '2026-11-24T10:00'
    vm.createForm.customValues = [{ fieldId: 101, fieldName: '批次', value: '' }]
    vm.formItems = [{ productId: 1, productName: '产品', quantity: 1, customValues: [{ fieldId: 201, fieldName: '明细批次', value: 'D1' }] }]
    await vm[method]()
    expect(mocks[write]).not.toHaveBeenCalled()
    expect(wrapper.emitted('notice')).toContainEqual(['请填写必填字段「批次」'])
    vm.createForm.customValues[0].value = 'H1'
    vm.formItems[0].customValues[0].value = ''
    await vm[method]()
    expect(mocks[write]).not.toHaveBeenCalled()
    expect(wrapper.emitted('notice')).toContainEqual(['请填写必填字段「明细批次」'])
  })
})

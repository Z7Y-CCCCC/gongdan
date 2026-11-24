import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import CustomReceiptFieldsPage from '../CustomReceiptFieldsPage.vue'
import CustomWorkOrderFieldsPage from '../CustomWorkOrderFieldsPage.vue'
import DeleteConfirmDialog from '../DeleteConfirmDialog.vue'

const mocks = vi.hoisted(() => ({
  fetchCustomFields: vi.fn(), createCustomField: vi.fn(), updateCustomField: vi.fn(), deleteCustomField: vi.fn(),
  fetchSystemImportLogs: vi.fn(), createSystemImportLog: vi.fn()
}))
vi.mock('../../api', () => mocks)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const field = { id: 1, scope: 'receipt', name: '批次', fieldType: 'text', options: [], defaultValues: [], sortOrder: 0 }

describe.each([
  { label: '单据', component: CustomReceiptFieldsPage, extraProps: { scope: 'receipt', scopeLabel: '出入库单' } },
  { label: '工单', component: CustomWorkOrderFieldsPage, extraProps: {} }
])('$label自定义字段闭环', ({ component, extraProps }) => {
  let wrapper: VueWrapper<any>
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.fetchCustomFields.mockResolvedValue({ items: [field], total: 1 })
    mocks.fetchSystemImportLogs.mockResolvedValue({ items: [], total: 0 })
  })
  afterEach(() => wrapper?.unmount())
  async function mountPage() {
    wrapper = shallowMount(component, { props: { token: 'test-token', ...extraProps } })
    await flushPromises()
    return wrapper.vm as any
  }

  it('shows an initial load error and allows a successful retry', async () => {
    mocks.fetchCustomFields.mockRejectedValueOnce(new Error('字段接口暂不可用'))
    const vm = await mountPage()
    expect(wrapper.emitted('notice')).toContainEqual(['字段接口暂不可用'])
    expect(wrapper.find('[role="alert"]').text()).toContain('字段接口暂不可用')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(vm.fields).toHaveLength(1)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('locks duplicate saves, keeps the failed form, and allows retry', async () => {
    const vm = await mountPage()
    vm.openCreate()
    vm.form.name = '新批次'
    vm.form.fieldType = 'text'
    const pending = deferred<any>()
    mocks.createCustomField.mockReturnValueOnce(pending.promise).mockResolvedValueOnce(field)
    const first = vm.saveField()
    const second = vm.saveField()
    expect(mocks.createCustomField).toHaveBeenCalledTimes(1)
    pending.reject(new Error('字段名称已存在'))
    await Promise.all([first, second])
    expect(vm.view).toBe('form')
    expect(vm.form.name).toBe('新批次')
    expect(vm.saving).toBe(false)
    expect(wrapper.emitted('notice')).toContainEqual(['字段名称已存在'])
    await vm.saveField()
    expect(mocks.createCustomField).toHaveBeenCalledTimes(2)
    expect(vm.view).toBe('list')
  })

  it('reports delete failure, closes the confirmation, and preserves the original field', async () => {
    const vm = await mountPage()
    mocks.deleteCustomField.mockRejectedValueOnce(new Error('字段仍被引用'))
    const deletion = vm.removeField(field)
    wrapper.getComponent(DeleteConfirmDialog).vm.$emit('confirm')
    await deletion
    expect(wrapper.emitted('notice')).toContainEqual(['字段仍被引用'])
    expect(vm.visible).toBe(false)
    expect(vm.loading).toBe(false)
    expect(vm.fields).toHaveLength(1)
  })

  it('ignores an obsolete field-list response', async () => {
    const vm = await mountPage()
    const old = deferred<any>()
    mocks.fetchCustomFields.mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ items: [{ ...field, id: 2 }], total: 1 })
    const first = vm.loadFields()
    await vm.loadFields()
    old.resolve({ items: [field], total: 1 })
    await first
    expect(vm.fields.map((item: any) => item.id)).toEqual([2])
  })
})

it('does not offer receipt-field switches that the API cannot persist or enforce', async () => {
  mocks.fetchCustomFields.mockResolvedValue({ items: [], total: 0 })
  mocks.fetchSystemImportLogs.mockResolvedValue({ items: [], total: 0 })
  const wrapper = shallowMount(CustomReceiptFieldsPage, {
    props: { token: 'test-token', scope: 'receipt', scopeLabel: '出入库单' }
  })
  await flushPromises()
  ;(wrapper.vm as any).openCreate()
  ;(wrapper.vm as any).form.fieldType = 'text'
  await flushPromises()
  expect(wrapper.text()).not.toContain('允许扫码输入')
  expect(wrapper.text()).not.toContain('可修改扫码结果')
  expect(wrapper.text()).not.toContain('只读')
  expect(wrapper.text()).not.toContain('多行')
  wrapper.unmount()
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import CustomerPage from '../CustomerPage.vue'
import SupplierPage from '../SupplierPage.vue'
import WarehousePage from '../WarehousePage.vue'
import DeleteConfirmDialog from '../DeleteConfirmDialog.vue'

const api = vi.hoisted(() => ({
  fetchCustomers: vi.fn(), createCustomer: vi.fn(), deleteCustomer: vi.fn(),
  fetchSuppliers: vi.fn(), createSupplier: vi.fn(), deleteSupplier: vi.fn(),
  fetchWarehouses: vi.fn(), createWarehouse: vi.fn(), deleteWarehouse: vi.fn()
}))
vi.mock('../../api', async (original) => ({
  ...await original<typeof import('../../api')>(), ...api
}))

const row = (id: number, name: string) => ({ id, code: `CODE-${id}`, name, enabled: true })
type Result = { items: ReturnType<typeof row>[]; total: number }

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe.each([
  { label: '客户', component: CustomerPage, fetch: api.fetchCustomers, create: api.createCustomer, remove: api.deleteCustomer },
  { label: '供应商', component: SupplierPage, fetch: api.fetchSuppliers, create: api.createSupplier, remove: api.deleteSupplier },
  { label: '仓库', component: WarehousePage, fetch: api.fetchWarehouses, create: api.createWarehouse, remove: api.deleteWarehouse },
])('$label列表和操作', ({ label, component, fetch, create, remove }) => {
  let wrapper: VueWrapper<any>

  beforeEach(() => {
    vi.resetAllMocks()
    fetch.mockResolvedValue({ items: [row(1, '首页条目')], total: 601 })
    wrapper = shallowMount(component, { props: { token: 'test-token' } })
  })

  afterEach(() => { wrapper.unmount() })

  it('按页加载，翻页保留已提交的筛选，重新搜索回到首页', async () => {
    await flushPromises()
    expect(fetch).toHaveBeenLastCalledWith('test-token', { page: 1, size: 50 })
    expect(wrapper.get('.page-info').text()).toBe('第 1 / 13 页')
    await wrapper.get('.filters input').setValue(' CODE-9 ')
    fetch.mockResolvedValueOnce({ items: [row(51, '第二页条目')], total: 601 })
    await wrapper.get('.page-controls button:last-child').trigger('click')
    await flushPromises()
    expect(fetch).toHaveBeenLastCalledWith('test-token', { page: 2, size: 50 })
    expect(wrapper.get('tbody tr td').text()).toBe('51')

    await wrapper.get('.query-btn').trigger('click')
    await flushPromises()
    expect(fetch).toHaveBeenLastCalledWith('test-token', { code: 'CODE-9', name: undefined, page: 1, size: 50 })
  })

  it('忽略过时搜索，最新请求失败时显示可重试的错误', async () => {
    await flushPromises()
    const old = deferred<Result>()
    fetch.mockReturnValueOnce(old.promise)
    await wrapper.get('.filters input').setValue('旧条件')
    await wrapper.get('.query-btn').trigger('click')
    fetch.mockRejectedValueOnce(new Error('当前筛选失败'))
    await wrapper.get('.filters input').setValue('新条件')
    await wrapper.get('.query-btn').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('当前筛选失败')
    old.resolve({ items: [row(2, '过时结果')], total: 1 })
    await flushPromises()
    expect(wrapper.text()).not.toContain('过时结果')

    fetch.mockResolvedValueOnce({ items: [row(3, '重试成功')], total: 1 })
    await wrapper.get('tbody button').trigger('click')
    await flushPromises()
    expect(fetch).toHaveBeenLastCalledWith('test-token', { code: '新条件', name: undefined, page: 1, size: 50 })
    expect(wrapper.text()).toContain('重试成功')
  })

  it('保存挂起时只发送一次创建，失败后保留输入供重试', async () => {
    await flushPromises()
    const pending = deferred<ReturnType<typeof row>>()
    create.mockReturnValueOnce(pending.promise)
    await wrapper.get('.toolbar .primary-btn').trigger('click')
    await wrapper.get(`.modal-body input[placeholder="请输入${label}名称"]`).setValue('新条目')
    const submit = wrapper.get('.modal-footer .primary-btn')
    await Promise.all([submit.trigger('click'), submit.trigger('click')])
    expect(create).toHaveBeenCalledTimes(1)
    expect(submit.attributes('disabled')).toBeDefined()
    pending.reject(new Error('保存暂时失败'))
    await flushPromises()
    expect(wrapper.emitted('notice')).toContainEqual(['保存暂时失败'])
    expect((wrapper.get(`.modal-body input[placeholder="请输入${label}名称"]`).element as HTMLInputElement).value).toBe('新条目')
    expect(submit.attributes('disabled')).toBeUndefined()

    create.mockResolvedValueOnce(row(100, '新条目'))
    fetch.mockResolvedValueOnce({ items: [row(100, '新条目')], total: 1 })
    await submit.trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-backdrop').exists()).toBe(false)
    expect(wrapper.get('tbody').text()).toContain('新条目')
    expect(wrapper.emitted('notice')).toContainEqual([`${label}已创建`])
  })

  it('删除期间禁用确认，成功关闭弹窗并退回有效页', async () => {
    await flushPromises()
    fetch.mockResolvedValueOnce({ items: [row(51, '末页条目')], total: 51 })
    await wrapper.get('.page-controls button:last-child').trigger('click')
    await flushPromises()
    await wrapper.get('tbody .danger').trigger('click')
    const dialog = wrapper.getComponent(DeleteConfirmDialog)
    expect(dialog.props('visible')).toBe(true)
    const pending = deferred<void>()
    remove.mockReturnValueOnce(pending.promise)
    dialog.vm.$emit('confirm')
    dialog.vm.$emit('confirm')
    await flushPromises()
    expect(dialog.props('loading')).toBe(true)
    expect(remove).toHaveBeenCalledExactlyOnceWith('test-token', 51)
    fetch.mockResolvedValueOnce({ items: [], total: 50 })
      .mockResolvedValueOnce({ items: [row(1, '首页条目')], total: 50 })
    pending.resolve()
    await flushPromises()
    expect(dialog.props('visible')).toBe(false)
    expect(wrapper.get('.page-info').text()).toBe('第 1 / 1 页')
    expect(wrapper.text()).toContain('首页条目')
    expect(wrapper.emitted('notice')).toContainEqual([`${label}已删除`])
  })
})

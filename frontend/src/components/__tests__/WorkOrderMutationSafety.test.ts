import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, KeepAlive, ref } from 'vue'
import WorkOrderPage from '../WorkOrderPage.vue'

const api = vi.hoisted(() => Object.fromEntries(['fetchUnits', 'fetchDefects', 'fetchDepartments', 'fetchProcesses',
  'fetchProcessRoutes', 'fetchSystemImportLogs', 'fetchSystemOperationLogs', 'fetchWorkOrders', 'fetchProducts',
  'fetchCustomFields', 'fetchWorkOrderCategories', 'fetchBoms', 'fetchUserPreference', 'deleteWorkOrder',
  'updateWorkOrder', 'createWorkOrder', 'createSystemOperationLog'].map(name => [name, vi.fn()])))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,fixture') } }))
const wrappers: VueWrapper<any>[] = []
function deferred() {
  let resolve!: (value?: any) => void
  const promise = new Promise<any>(yes => { resolve = yes })
  return { promise, resolve }
}
const order = (id: number) => ({ id, code: `WO-${id}`, productId: 1, productCode: 'P1', productName: '产品',
  plannedQuantity: 12, goodQuantity: 1, defectQuantity: 0, status: 'EXECUTING', priority: 'HIGH', version: 1,
  tasks: [{ sequenceNo: 1, processName: `工序-${id}`, plannedQuantity: 12 }], materials: [], customValues: [] })
async function page(change: string) {
  const visible = ref(true), token = ref('actor-A')
  const Other = defineComponent({ setup: () => () => h('div', 'other page') })
  const Host = defineComponent({ setup: () => () => h(KeepAlive, {}, {
    default: () => visible.value ? h(WorkOrderPage, { token: token.value }) : h(Other)
  }) })
  const wrapper = change === 'deactivate' ? shallowMount(Host, { global: { stubs: { WorkOrderPage: false, KeepAlive: false } } })
    : shallowMount(WorkOrderPage, { props: { token: token.value } })
  wrappers.push(wrapper)
  await flushPromises()
  const child = change === 'deactivate' ? wrapper.findComponent(WorkOrderPage) : wrapper
  const vm = child.vm as any
  vm.workOrders = [order(1), order(2)]; vm.selectedIds = [1, 2]
  await flushPromises()
  const leave = async () => {
    if (change === 'token') await wrapper.setProps({ token: 'actor-B' })
    else if (change === 'deactivate') { visible.value = false; await flushPromises() }
    else wrapper.unmount()
  }
  return { wrapper, child, vm, leave }
}
beforeEach(() => {
  vi.resetAllMocks()
  for (const method of Object.values(api)) method.mockResolvedValue({ items: [], total: 0 })
  api.fetchWorkOrderCategories.mockResolvedValue([])
  api.fetchUserPreference.mockResolvedValue({ value: null })
  api.fetchProducts.mockResolvedValue({ items: [{ id: 1, code: 'P1', name: '产品', unitName: '件' }], total: 1 })
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.unstubAllGlobals() })

describe('work-order writes belong to their starting page and session', () => {
  it('safely disposes without a pending reader when an adapter omits FileReader state constants', async () => {
    const { wrapper } = await page('token')
    vi.stubGlobal('FileReader', class {})
    expect(() => wrapper.unmount()).not.toThrow()
  })
  it.each(['token', 'deactivate', 'unmount'])('cancels pending bulk-delete confirmation after %s', async change => {
    const { vm, leave } = await page(change)
    const operation = vm.handleDeleteSelected()
    expect(vm.deleteVisible).toBe(true)
    await leave()
    vm.deleteHandleConfirm()
    await operation
    expect(api.deleteWorkOrder).not.toHaveBeenCalled()
    expect(vm.deleteVisible).toBe(false)
  })

  it.each(['token', 'deactivate', 'unmount'])('stops the remaining delete loop after %s', async change => {
    const { child, vm, leave } = await page(change), pending = deferred()
    api.deleteWorkOrder.mockReturnValueOnce(pending.promise)
    const operation = vm.handleDeleteSelected(); vm.deleteHandleConfirm(); await flushPromises()
    expect(api.deleteWorkOrder).toHaveBeenCalledExactlyOnceWith('actor-A', 1)
    await leave(); pending.resolve(); await operation; await flushPromises()
    expect(api.deleteWorkOrder).toHaveBeenCalledTimes(1)
    expect(child.emitted('notice')).toBeUndefined()
  })

  it.each(['token', 'deactivate', 'unmount'])('stops the remaining status loop and late notices after %s', async change => {
    const { child, vm, leave } = await page(change), pending = deferred()
    api.updateWorkOrder.mockReturnValueOnce(pending.promise)
    const operation = vm.handleBulkStatus('PAUSED'); await flushPromises()
    expect(api.updateWorkOrder).toHaveBeenCalledTimes(1)
    expect(api.updateWorkOrder.mock.calls[0][0]).toBe('actor-A')
    await leave(); pending.resolve(); await operation; await flushPromises()
    expect(api.updateWorkOrder).toHaveBeenCalledTimes(1)
    expect(api.createSystemOperationLog).not.toHaveBeenCalled()
    expect(child.emitted('notice')).toBeUndefined()
  })

  it('snapshots every status payload before the first write awaits', async () => {
    const { vm } = await page('token'), pending = deferred()
    api.updateWorkOrder.mockReturnValueOnce(pending.promise)
    const operation = vm.handleBulkStatus('PAUSED'); await flushPromises()
    vm.workOrders[1].tasks[0].processName = '随后编辑'
    vm.workOrders[1].version = 9
    pending.resolve(); await operation
    expect(api.updateWorkOrder.mock.calls[1][2].tasks[0].processName).toBe('工序-2')
    expect(api.updateWorkOrder.mock.calls[1][2].version).toBe(1)
  })

  it('does not accept a stale single-row deletion confirmation under another token', async () => {
    const { vm, leave } = await page('token')
    const operation = vm.removeOrder(order(1)); await leave(); vm.deleteHandleConfirm(); await operation
    expect(api.deleteWorkOrder).not.toHaveBeenCalled()
  })
})

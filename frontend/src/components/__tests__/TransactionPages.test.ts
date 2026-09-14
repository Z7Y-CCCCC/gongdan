import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import WorkOrderPage from '../WorkOrderPage.vue'
import ProductDefinitionPage from '../ProductDefinitionPage.vue'
import InboundReceiptPage from '../InboundReceiptPage.vue'
import OutboundReceiptPage from '../OutboundReceiptPage.vue'
import InventoryBalancePage from '../InventoryBalancePage.vue'
import InventorySummaryPage from '../InventorySummaryPage.vue'
import BomPage from '../BomPage.vue'
import SalesOrderPage from '../SalesOrderPage.vue'
import TaskPage from '../TaskPage.vue'
import ReportCenterPage from '../ReportCenterPage.vue'
import ReportApprovalPage from '../ReportApprovalPage.vue'

const mocks = vi.hoisted(() => ({
  fetchWorkOrders: vi.fn(), fetchProducts: vi.fn(), fetchProcesses: vi.fn(), fetchUnits: vi.fn(),
  fetchProcessRoutes: vi.fn(), fetchCustomFields: vi.fn(), fetchWorkOrderCategories: vi.fn(),
  fetchProductCategories: vi.fn(), fetchBoms: vi.fn(), fetchUserPreference: vi.fn(),
  fetchSystemImportLogs: vi.fn(), fetchSystemOperationLogs: vi.fn(), createSystemOperationLog: vi.fn(),
  fetchInboundReceipts: vi.fn(), fetchOutboundReceipts: vi.fn(), fetchInventoryDetails: vi.fn(),
  createWorkOrder: vi.fn(), updateWorkOrder: vi.fn(), createProduct: vi.fn(), updateProduct: vi.fn(),
  createInboundReceipt: vi.fn(), createOutboundReceipt: vi.fn(), fetchSalesOrders: vi.fn(),
  createSalesOrder: vi.fn(), createBom: vi.fn(), fetchReports: vi.fn()
}))
vi.mock('../../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api')>(), ...mocks
}))

const product = {
  id: 1, code: 'P1', name: '产品', unitId: 1, unitName: '件', spec: '', attribute: '自制',
  inventoryQty: 20, keyPart: false, createdAt: '2026-09-01', version: 7
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe('transaction page regressions', () => {
  let wrapper: VueWrapper<any> | undefined
  beforeEach(() => {
    vi.resetAllMocks()
    for (const name of ['fetchWorkOrders', 'fetchProducts', 'fetchProcesses', 'fetchUnits', 'fetchProcessRoutes',
      'fetchCustomFields', 'fetchBoms', 'fetchSystemImportLogs', 'fetchSystemOperationLogs',
      'fetchInboundReceipts', 'fetchOutboundReceipts', 'fetchInventoryDetails', 'fetchSalesOrders', 'fetchReports'] as const) {
      mocks[name].mockResolvedValue({ items: [], total: 0 })
    }
    mocks.fetchWorkOrderCategories.mockResolvedValue([])
    mocks.fetchProductCategories.mockResolvedValue([])
    mocks.fetchUserPreference.mockResolvedValue(null)
    mocks.fetchProducts.mockResolvedValue({ items: [product], total: 1 })
    mocks.createSystemOperationLog.mockResolvedValue(undefined)
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('sends only one work order creation while save is pending', async () => {
    const pending = deferred<any>()
    mocks.createWorkOrder.mockReturnValue(pending.promise)
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openCreate()
    vm.form.productId = 1
    vm.form.plannedQuantity = 5
    const firstSave = vm.saveOrder()
    await vm.saveOrder()
    expect(mocks.createWorkOrder).toHaveBeenCalledTimes(1)
    expect(vm.saving).toBe(true)
    pending.resolve({ id: 1, code: 'WO1' })
    await firstSave
    expect(vm.saving).toBe(false)
  })

  it('reports initial work-order load failures instead of leaking unhandled rejections', async () => {
    mocks.fetchWorkOrders.mockRejectedValue(new Error('工单接口暂不可用'))
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'test-token' } })
    await flushPromises()
    expect(wrapper.emitted('notice')).toContainEqual(['工单接口暂不可用'])
  })

  it.each([
    { permissions: ['张三', '李四', 'ADMIN'], expected: '张三,李四,ADMIN' },
    { permissions: [], expected: '所有人' }
  ])('retains configured permissions when generating product-route tasks: $expected', async ({ permissions, expected }) => {
    mocks.fetchProducts.mockResolvedValue({ items: [{ ...product, routeId: 10 }], total: 1 })
    mocks.fetchProcesses.mockResolvedValue({ items: [{ id: 20, code: 'CUT', name: '切割', reportPermission: permissions }], total: 1 })
    mocks.fetchProcessRoutes.mockResolvedValue({ items: [{ id: 10, name: '工艺路线', steps: [{ processId: 20 }] }], total: 1 })
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'test-token' } })
    await flushPromises()
    const tasks = (wrapper.vm as any).buildTasks(1)
    expect(tasks).toHaveLength(1)
    expect(tasks[0].reportPermission).toBe(expected)
  })

  it('retains all permissions for manually added process and route tasks', async () => {
    const process = { id: 20, code: 'CUT', name: '切割', reportPermission: ['张三', 'ADMIN'] }
    mocks.fetchProcesses.mockResolvedValue({ items: [process], total: 1 })
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.addTaskFromProcess(process)
    vm.addTaskFromRoute({ steps: [{ processId: 20 }] })
    expect(vm.form.tasks.map((task: any) => task.reportPermission)).toEqual(['张三,ADMIN', '张三,ADMIN'])
  })

  it('preserves category and expected version during a bulk status update and reports partial failure', async () => {
    const orders = [
      { id: 1, code: 'WO1', productId: 1, categoryId: 9, version: 3, plannedQuantity: 2, tasks: [] },
      { id: 2, code: 'WO2', productId: 1, categoryId: 9, version: 4, plannedQuantity: 2, tasks: [] }
    ]
    mocks.fetchWorkOrders.mockResolvedValue({ items: orders, total: 2 })
    mocks.updateWorkOrder.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('版本冲突'))
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.selectedIds = [1, 2]
    await vm.handleBulkStatus('EXECUTING')
    expect(mocks.updateWorkOrder.mock.calls[0][2]).toMatchObject({ categoryId: 9, version: 3, status: 'EXECUTING' })
    expect(wrapper.emitted('notice')).toContainEqual(['已更新 1 条工单；版本冲突'])
    expect(vm.saving).toBe(false)
  })

  it('includes equivalent persisted status spellings in cards and filters', async () => {
    mocks.fetchWorkOrders.mockResolvedValue({ items: [
      { id: 1, code: 'WO1', status: 'IN_PROGRESS', tasks: [] },
      { id: 2, code: 'WO2', status: 'CANCELLED', tasks: [] }
    ], total: 2 })
    wrapper = shallowMount(WorkOrderPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.statusCards.find((card: any) => card.key === 'EXECUTING').count).toBe(1)
    expect(vm.statusCards.find((card: any) => card.key === 'CANCELED').count).toBe(1)
    vm.statusFilter = 'EXECUTING'
    expect(vm.filteredOrders.map((order: any) => order.id)).toEqual([1])
    vm.statusFilter = 'CANCELED'
    expect(vm.filteredOrders.map((order: any) => order.id)).toEqual([2])
  })

  it('includes the version and omits unchanged inventory on a product edit', async () => {
    const pending = deferred<any>()
    mocks.updateProduct.mockReturnValue(pending.promise)
    wrapper = shallowMount(ProductDefinitionPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openEdit(product)
    vm.form.name = '新名称'
    const firstSave = vm.saveProduct()
    await vm.saveProduct()
    expect(mocks.updateProduct).toHaveBeenCalledTimes(1)
    const payload = mocks.updateProduct.mock.calls[0][2]
    expect(payload.version).toBe(7)
    expect(payload.inventoryQty).toBeUndefined()
    pending.reject(new Error('产品已被其他用户修改，请刷新后重试'))
    await firstSave
    expect(vm.saving).toBe(false)
    expect(vm.view).toBe('form')
    expect(wrapper.emitted('notice')).toContainEqual(['产品已被其他用户修改，请刷新后重试'])
  })

  it('does not include an existing product version when copying to a new product', async () => {
    mocks.createProduct.mockResolvedValue({ ...product, id: 2 })
    wrapper = shallowMount(ProductDefinitionPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.copyProduct(product)
    await vm.saveProduct()
    expect(mocks.createProduct.mock.calls[0][1].version).toBeUndefined()
  })

  it('blocks an inbound quantity edited to zero or a fractional number before saving', async () => {
    wrapper = shallowMount(InboundReceiptPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.goToCreate()
    for (const quantity of [0, -1, 1.5, Number.NaN]) {
      vm.formItems = [{ productId: 1, quantity, customValues: [] }]
      await vm.handleSave()
    }
    expect(mocks.createInboundReceipt).not.toHaveBeenCalled()
  })

  it('keeps inbound save locked even when an overlapping list refresh finishes', async () => {
    const pending = deferred<any>()
    mocks.createInboundReceipt.mockReturnValue(pending.promise)
    wrapper = shallowMount(InboundReceiptPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.goToCreate()
    vm.formItems = [{ productId: 1, quantity: 2, customValues: [] }]
    const firstSave = vm.handleSave()
    await vm.loadReceipts()
    await vm.handleSave()
    expect(mocks.createInboundReceipt).toHaveBeenCalledTimes(1)
    expect(vm.saving).toBe(true)
    pending.resolve({ id: 1 })
    await firstSave
    expect(vm.saving).toBe(false)
  })

  it('sends only one outbound save and lets the server decide against its current stock', async () => {
    const pending = deferred<any>()
    mocks.createOutboundReceipt.mockReturnValue(pending.promise)
    wrapper = shallowMount(OutboundReceiptPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.handleCreate()
    vm.formItems = [{ productId: 1, productName: '产品', inventoryQty: 0, quantity: 2, customValues: [] }]
    const firstSave = vm.submitCreate()
    await vm.submitCreate()
    expect(mocks.createOutboundReceipt).toHaveBeenCalledTimes(1)
    pending.resolve({ id: 1 })
    await firstSave
    expect(vm.saving).toBe(false)
  })

  it('ignores an older inventory search response that arrives after a newer one', async () => {
    const old = deferred<any>()
    const latest = deferred<any>()
    mocks.fetchProducts.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    wrapper = shallowMount(InventoryBalancePage, { props: { token: 'test-token' } })
    const vm = wrapper.vm as any
    vm.filterForm.name = '最新产品'
    const request = vm.loadData()
    latest.resolve({ items: [{ ...product, id: 2, name: '最新产品' }], total: 1 })
    await request
    old.resolve({ items: [product], total: 1 })
    await flushPromises()
    expect(vm.products.map((item: any) => item.name)).toEqual(['最新产品'])
  })

  it('summarizes every inventory page and does not merge products with delimiter-containing names', async () => {
    mocks.fetchInventoryDetails
      .mockResolvedValueOnce({ items: [{ productCode: 'A|B', productName: 'C', changeQty: 3, sourceType: 'IN' }], total: 2 })
      .mockResolvedValueOnce({ items: [{ productCode: 'A', productName: 'B|C', changeQty: -1, sourceType: 'OUT' }], total: 2 })
    wrapper = shallowMount(InventorySummaryPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(mocks.fetchInventoryDetails).toHaveBeenCalledTimes(2)
    expect(vm.rows).toHaveLength(2)
    expect(vm.totals).toEqual({ inboundQty: 3, outboundQty: 1, netQty: 2, transactionCount: 2 })
  })

  it('submits a sales order only once while the first save is pending', async () => {
    const pending = deferred<any>()
    mocks.createSalesOrder.mockReturnValue(pending.promise)
    wrapper = shallowMount(SalesOrderPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openCreate()
    vm.form.customerName = '客户'
    vm.form.items = [{ productId: 1, quantity: 1, remark: '' }]
    const first = vm.saveOrder()
    await vm.saveOrder()
    expect(mocks.createSalesOrder).toHaveBeenCalledTimes(1)
    pending.resolve({ id: 1 })
    await first
    expect(vm.saving).toBe(false)
  })

  it('keeps a BOM save locked independently of list refreshes', async () => {
    const pending = deferred<any>()
    mocks.createBom.mockReturnValue(pending.promise)
    wrapper = shallowMount(BomPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openCreate()
    vm.form.productId = 1
    vm.form.items = [{ productId: 2, quantity: 1, scrapRate: 0, remark: '' }]
    const first = vm.saveBom()
    await vm.loadData()
    await vm.saveBom()
    expect(mocks.createBom).toHaveBeenCalledTimes(1)
    pending.resolve({ id: 1 })
    await first
    expect(vm.saving).toBe(false)
  })

  it('includes work-order tasks beyond the first page', async () => {
    const order = (id: number) => ({ id, code: `WO${id}`, productId: 1, plannedQuantity: 2,
      status: 'NOT_STARTED', tasks: [{ processName: '加工', plannedQuantity: 2 }] })
    mocks.fetchWorkOrders
      .mockResolvedValueOnce({ items: [order(1)], total: 2 })
      .mockResolvedValueOnce({ items: [order(2)], total: 2 })
    wrapper = shallowMount(TaskPage, { props: { token: 'test-token' } })
    await flushPromises()
    expect((wrapper.vm as any).summary.total).toBe(2)
    expect(mocks.fetchWorkOrders).toHaveBeenCalledTimes(2)
  })

  it('does not truncate production report totals at the first page', async () => {
    mocks.fetchWorkOrders
      .mockResolvedValueOnce({ items: [{ id: 1, productName: '甲', plannedQuantity: 2 }], total: 2 })
      .mockResolvedValueOnce({ items: [{ id: 2, productName: '乙', plannedQuantity: 3 }], total: 2 })
    mocks.fetchReports
      .mockResolvedValueOnce({ items: [{ id: 1, status: 'APPROVED' }], total: 2 })
      .mockResolvedValueOnce({ items: [{ id: 2, status: 'PENDING' }], total: 2 })
    wrapper = shallowMount(ReportCenterPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.totalPlanned).toBe(5)
    expect(vm.pendingReports).toBe(1)
    expect(vm.reports).toHaveLength(2)
  })

  it('includes pending approvals on later pages while preserving search filters', async () => {
    mocks.fetchReports
      .mockResolvedValueOnce({ items: [{ id: 1, status: 'APPROVED', images: [] }], total: 2 })
      .mockResolvedValueOnce({ items: [{ id: 2, status: 'PENDING', images: [] }], total: 2 })
    wrapper = shallowMount(ReportApprovalPage, { props: { token: 'test-token' } })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.reports.map((report: any) => report.id)).toEqual([2])
    expect(vm.tabs.find((tab: any) => tab.key === 'PENDING').count).toBe(1)
    expect(mocks.fetchReports.mock.calls[1][1].page).toBe(2)
  })
})

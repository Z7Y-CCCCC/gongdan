import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, KeepAlive } from 'vue'
import WorkOrderPage from '../WorkOrderPage.vue'
import ProductDefinitionPage from '../ProductDefinitionPage.vue'
import BaseDataPage from '../BaseDataPage.vue'
import InventoryDetailPage from '../InventoryDetailPage.vue'
import InventorySummaryPage from '../InventorySummaryPage.vue'
import InventoryBalancePage from '../InventoryBalancePage.vue'
import UserManagementPage from '../UserManagementPage.vue'
import { setUiLocale } from '../../i18n'

const api = vi.hoisted(() => Object.fromEntries(['fetchWorkOrders', 'fetchProducts', 'fetchProcesses', 'fetchUnits',
  'fetchDefects', 'fetchDepartments', 'fetchUsers', 'fetchProcessRoutes', 'fetchCustomFields', 'fetchBoms',
  'fetchInventoryDetails', 'fetchSystemImportLogs', 'fetchSystemOperationLogs', 'fetchUserPreference',
  'fetchProductCategories', 'fetchWorkOrderCategories', 'fetchProductsExportBlob', 'fetchUsersExportBlob',
  'exportUsersCsv'].map(name => [name, vi.fn()])))
const files = vi.hoisted(() => ({ downloadXlsx: vi.fn(), create: vi.fn(), revoke: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
vi.mock('../../utils/spreadsheet', () => ({ downloadXlsx: files.downloadXlsx }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,fixture') } }))

function deferred<T = any>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function csvBlob(csv: string) { return Object.assign(new Blob([csv], { type: 'text/csv' }), { text: async () => csv }) }
async function readBlob(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(new TextDecoder('utf-8', { ignoreBOM: true }).decode(reader.result as ArrayBuffer))
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(blob)
  })
}
const csvEscape = (value: unknown) => /[,"\r\n]/.test(String(value ?? ''))
  ? `"${String(value ?? '').replace(/"/g, '""')}"` : String(value ?? '')
const quoted = (row: unknown[]) => row.map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(',')
const user = { id: 1, username: '001', name: '=1+1', role: 'ADMIN', roleLabel: '管理员', enabled: true, virtualUser: false }
const order = { id: 1, code: '=WO("1")', productId: 1, productCode: '001', productName: '=1+1', productSpec: '  原规格  ',
  plannedQuantity: 12, goodQuantity: 2, defectQuantity: 0, status: 'EXECUTING', priority: 'HIGH',
  tasks: [{ id: 11, processName: '+工序', processCode: '0001', sequenceNo: 1, plannedQuantity: 12,
    completedQuantity: -2, defectQuantity: 0, approvedQuantity: 0, unapprovedQuantity: -2 }],
  materials: [{ materialCode: '@物料', materialName: '保存', materialSpec: '', quantity: -3 }],
  customValues: [{ fieldId: 99, fieldName: '保存', value: '=SUM(A1:A2)' }] }
const product = { id: 1, code: '001', name: '=1+1', unitName: '@单位', spec: '  原规格  ', attribute: '自制',
  routeName: '+路线', supplierName: '-供应商', inventoryQty: -3, createdAt: '2026-11-24' }
const detail = { id: 1, receiptCode: '=单据', productCode: '001', productName: '=1+1', productSpec: '  原规格  ',
  productAttribute: '自制', unitName: '@单位', receiptQty: 3, changeQty: -3, receiptType: '普通出库',
  sourceType: 'OUT', operationTime: '2026-11-24 10:00:00', remark: '原文,"换行\n保存' }
const summary = { key: 'fixture', productCode: '001', productName: '=1+1', productSpec: '  原规格  ',
  productAttribute: '自制', unitName: '@单位', inboundQty: 2, outboundQty: 5, netQty: -3,
  transactionCount: 2, lastOperationTime: '2026-11-24 10:00:00' }
const balanceCsv = '\uFEFF库存数量,单位,产品名称,产品编号,产品规格,工艺路线\r\n-3,@单位,=1+1,001,  原规格  ,+路线\r\n4,件,完整第二行,002,,\r\n5,件,完整第三行,003,,\r\n'
const userCsv = '账号,姓名,角色,电话\n' + Array.from({ length: 12 }, (_, index) =>
  `${String(index + 1).padStart(3, '0')},${index ? `完整用户${index + 1}` : '=1+1'},ADMIN,013800000000`).join('\n')
const workOrderHeaders = ['工单编号', '产品编号', '产品名称', '产品规格', '状态', '优先级', '计划数', '良品数',
  '不良品数', '不良率', '工单进度', '工艺路线', '分类', '计划开始', '计划结束', '关联订单号', '客户名称',
  '备注', '创建人', '创建时间', '更新时间', '工序1-名称', '工序1-编号', '工序1-序号', '工序1-计划数',
  '工序1-完成数', '工序1-不良数', '工序1-已审数', '工序1-未审数', '工序1-负责人', '工序1-截止时间',
  '工序1-报工权限', '工序1-最后报工', '工序1-启用', '物料1-编号', '物料1-名称', '物料1-规格', '物料1-数量', '保存']
const workOrderValues = [order.code, '001', '=1+1', '  原规格  ', '执行中', '高', 12, 2, 0, '', '', '', '', '', '',
  '', '', '', '', '', '', '+工序', '0001', 1, 12, -2, 0, 0, -2, '', '', '', '', '是', '@物料', '保存', '', -3, '=SUM(A1:A2)']

type Scenario = { name: string; component: any; props?: Record<string, unknown>; prepare: (vm: any) => void;
  exportKind?: string; csvMethod?: string; csv?: string; verify: (matrix: any[][]) => void }
const scenarios: Scenario[] = [
  { name: '工单', component: WorkOrderPage, prepare: vm => {
    vm.workOrders = [order]; vm.selectedIds = [1]
    vm.customFields = [{ id: 99, name: '保存', scope: 'workorder', fieldType: 'text' }]
  }, csvMethod: 'handleExport', csv: '\uFEFF' + [workOrderHeaders, workOrderValues].map(row => row.map(csvEscape).join(',')).join('\n'),
  verify: rows => {
    expect(rows[0][rows[0].length - 1]).toBe('保存'); expect(rows[1][2]).toBe('=1+1'); expect(rows[1][3]).toBe('  原规格  ')
    expect(rows[1][rows[1].length - 1]).toBe('=SUM(A1:A2)'); expect(rows[1][25]).toBe(-2); expect(rows[1][37]).toBe(-3)
  } },
  { name: '产品', component: ProductDefinitionPage, prepare: vm => { vm.products = [product] },
    exportKind: 'handleExportProducts', csvMethod: 'handleExportProducts',
    csv: '\uFEFF产品编号,产品名称,库存单位,产品规格,产品属性,工艺路线,默认供应商,库存数量\n"001","=1+1","@单位","  原规格  ","自制","+路线","-供应商",-3',
    verify: rows => { expect(rows[1]).toEqual(['001', '=1+1', '@单位', '  原规格  ', '自制', '+路线', '-供应商', -3]) } },
  { name: '单位', component: BaseDataPage, props: { activeBasedataMenu: 'unit', users: [] },
    prepare: vm => { vm.units = [{ id: 1, name: '=1+1', remark: '  原备注  ' }] }, exportKind: 'handleExportUnits', csvMethod: 'handleExportUnits',
    csv: '\uFEFF单位名称,备注\n"=1+1","  原备注  "', verify: rows => { expect(rows[1]).toEqual(['=1+1', '  原备注  ']) } },
  { name: '不良项', component: BaseDataPage, props: { activeBasedataMenu: 'defect', users: [] },
    prepare: vm => { vm.defects = [{ id: 1, code: '001', name: '=1+1' }] }, exportKind: 'handleExportDefects', csvMethod: 'handleExportDefects',
    csv: '\uFEFF不良品项编号,不良品项名称\n"001","=1+1"', verify: rows => { expect(rows[1]).toEqual(['001', '=1+1']) } },
  { name: '工序', component: BaseDataPage, props: { activeBasedataMenu: 'process', users: [] }, prepare: vm => {
    vm.processes = [{ id: 1, code: '001', name: '=1+1', reportPermission: ['all', 'dept-1'], reportRatio: 2,
      taskPunchIn: true, goodQuantity: '', machineName: '', feedCount: '', correspondingMachine: '', requirements: '  原要求  ' }]
  }, exportKind: 'handleExportProcesses', csvMethod: 'handleExportProcesses',
    csv: '\uFEFF工序编号,工序名称,报工权限,报工数配比,任务打卡,良品数量,工序名称机械,投料数,对应机台,要求\n"001","=1+1","all,dept-1",2,"开启","","","","","  原要求  "',
    verify: rows => { expect(rows[1][1]).toBe('=1+1'); expect(rows[1][2]).toBe('all,dept-1'); expect(rows[1][3]).toBe(2); expect(rows[1][9]).toBe('  原要求  ') } },
  { name: '路线', component: BaseDataPage, props: { activeBasedataMenu: 'route', users: [] }, prepare: vm => {
    vm.processes = [{ id: 1, code: 'P1', name: '保存' }]
    vm.routes = [{ id: 1, code: '001', name: '=1+1', steps: [{ id: 1, type: 'process', processId: 1 }], creator: '@创建人', createdAt: '2026-11-24' }]
  }, exportKind: 'handleExportRoutes', csvMethod: 'handleExportRoutes',
    csv: '\uFEFF工艺路线编号,工艺路线名称,工序列表,创建人,创建时间\n"001","=1+1","保存","@创建人","2026-11-24"',
    verify: rows => { expect(rows[1]).toEqual(['001', '=1+1', '保存', '@创建人', '2026-11-24']) } },
  { name: '库存明细', component: InventoryDetailPage, prepare: vm => { vm.items = [detail]; vm.total = 51 }, csvMethod: 'exportCSV',
    csv: '\uFEFF序号,单据编号,产品编号,产品名称,产品规格,产品属性,库存单位,单据数量,库存变更数量,单据类型,来源,收发时间,备注\n'
      + quoted([1, '=单据', '001', '=1+1', '  原规格  ', '自制', '@单位', 3, -3, '普通出库', '出库单', '2026-11-24 10:00:00', detail.remark]),
    verify: rows => { expect(rows).toHaveLength(2); expect(rows[1][3]).toBe('=1+1'); expect(rows[1][8]).toBe(-3); expect(rows[1][12]).toBe(detail.remark) } },
  { name: '库存汇总', component: InventorySummaryPage, prepare: vm => { vm.rows = [summary]; vm.sourceTotal = 51 }, csvMethod: 'exportCSV',
    csv: '\uFEFF产品编号,产品名称,规格,属性,单位,入库数量,出库数量,净变动,收发次数,最后收发时间\n'
      + quoted(['001', '=1+1', '  原规格  ', '自制', '@单位', 2, 5, -3, 2, '2026-11-24 10:00:00']),
    verify: rows => { expect(rows).toHaveLength(2); expect(rows[1][1]).toBe('=1+1'); expect(rows[1][7]).toBe(-3) } },
  { name: '库存余额', component: InventoryBalancePage, prepare: vm => {
    vm.products = [product]; vm.totalProducts = 3
    Object.assign(vm.filterForm, { code: '00', name: '', spec: '  原规格  ' })
  }, csvMethod: 'handleExport', csv: balanceCsv,
    verify: rows => { expect(rows).toHaveLength(4); expect(rows[1]).toEqual([-3, '@单位', '=1+1', '001', '  原规格  ', '+路线']); expect(rows[3][2]).toBe('完整第三行') } },
  { name: '用户', component: UserManagementPage, props: { users: [user], departments: [], currentUser: user }, prepare: () => {}, csvMethod: 'handleExport',
    verify: rows => { expect(rows).toHaveLength(13); expect(rows[1][1]).toBe('=1+1'); expect(rows[1][0]).toBe('001'); expect(rows[1][3]).toBe('013800000000'); expect(rows.flat().join(' ')).not.toMatch(/password|hash|密码哈希/i) } }
]

const wrappers: VueWrapper<any>[] = []
async function page(scenario: Scenario) {
  const wrapper = shallowMount(scenario.component, { props: { token: 'fixture-one', ...scenario.props } })
  wrappers.push(wrapper); await flushPromises()
  scenario.prepare(wrapper.vm); await flushPromises()
  return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  for (const method of Object.values(api)) method.mockResolvedValue({ items: [], total: 0 })
  api.fetchProductCategories.mockResolvedValue([]); api.fetchWorkOrderCategories.mockResolvedValue([])
  api.fetchUserPreference.mockResolvedValue({ value: null })
  api.fetchProductsExportBlob.mockResolvedValue(csvBlob(balanceCsv))
  api.fetchUsersExportBlob.mockResolvedValue(csvBlob(userCsv))
  api.exportUsersCsv.mockResolvedValue(undefined)
  files.downloadXlsx.mockResolvedValue(true)
  files.create.mockReturnValue('blob:raw-backup')
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: files.create, revokeObjectURL: files.revoke }))
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  setUiLocale('zh-CN')
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe.each(scenarios)('$name default safe spreadsheet and explicit raw backup', scenario => {
  it('defaults the visible action to XLSX, preserves literal business values and states the real export/backup scope', async () => {
    const wrapper = await page(scenario)
    expect(wrapper.findAll('button[aria-label="导出Excel"]').length).toBeGreaterThan(0)
    expect(wrapper.findAll('button[aria-label="CSV备份"]').length).toBeGreaterThan(0)
    expect(wrapper.get('.export-scope-note').text()).toMatch(/已加载|导出范围/)
    expect(wrapper.get('.export-scope-note').text()).toContain('不宜直接用Excel打开')
    await wrapper.get('button[aria-label="导出Excel"]').trigger('click'); await flushPromises()
    expect(files.downloadXlsx).toHaveBeenCalledTimes(1)
    const [fileName, , matrix, current] = files.downloadXlsx.mock.calls[0]
    expect(fileName).toMatch(/\.xlsx$/); expect(current()).toBe(true)
    scenario.verify(matrix)
    expect(files.create).not.toHaveBeenCalled(); expect(api.exportUsersCsv).not.toHaveBeenCalled()
    if (scenario.name === '用户') expect(api.fetchUsersExportBlob).toHaveBeenCalledWith('fixture-one')
    if (scenario.name === '库存余额') expect(api.fetchProductsExportBlob).toHaveBeenCalledWith('fixture-one', { code: '00', name: '', spec: '  原规格  ' })
  })

  it('locks XLSX and raw CSV together during a pending file, then permits retry on an honest error', async () => {
    const wrapper = await page(scenario), vm = wrapper.vm as any
    const pending = deferred(); files.downloadXlsx.mockReturnValueOnce(pending.promise)
    const first = vm.exportXlsx(scenario.exportKind), second = vm.exportXlsx(scenario.exportKind)
    await flushPromises()
    await vm[scenario.csvMethod!]()
    expect(files.downloadXlsx).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[aria-label="导出Excel"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[aria-label="CSV备份"]').attributes('disabled')).toBeDefined()
    expect(files.create).not.toHaveBeenCalled(); expect(api.exportUsersCsv).not.toHaveBeenCalled()
    pending.reject(new Error('文件生成暂时失败'))
    await Promise.all([first, second]); await flushPromises()
    expect(vm.exporting).toBe(false)
    expect([...(wrapper.emitted('notice') || []), ...(wrapper.emitted('error') || [])].flat()).toContain('文件生成暂时失败')
    expect(wrapper.get('.export-error[role="alert"]').text()).toContain('文件生成暂时失败')
    await vm.exportXlsx(scenario.exportKind)
    expect(files.downloadXlsx).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.export-error').exists()).toBe(false)
  })

  it.each(['session', 'unmount'])('invalidates the helper save guard after %s changes without claiming a download', async change => {
    const wrapper = await page(scenario), vm = wrapper.vm as any
    const pending = deferred(); files.downloadXlsx.mockReturnValueOnce(pending.promise)
    const first = vm.exportXlsx(scenario.exportKind)
    await flushPromises()
    const current = files.downloadXlsx.mock.calls[0][3]
    expect(current()).toBe(true)
    if (change === 'session') await wrapper.setProps({ token: 'fixture-two' })
    else wrapper.unmount()
    expect(current()).toBe(false)
    pending.resolve(false); await first; await flushPromises()
    expect(wrapper.emitted('notice')).toBeUndefined()
    expect(wrapper.emitted('error')).toBeUndefined()
  })

  it('cancels a pending download when its cached workspace is deactivated', async () => {
    const Host = defineComponent({ props: { show: Boolean }, render() {
      return h(KeepAlive, null, { default: () => this.show ? h(scenario.component, { token: 'fixture-one', ...scenario.props }) : null })
    } })
    const host = mount(Host, { props: { show: true }, global: { stubs: { UiIcon: true, TableConfigButton: true,
      DeleteConfirmDialog: true, SystemLogsDialog: true, ColumnSettingsPanel: true } } })
    wrappers.push(host); await flushPromises()
    const wrapper = host.getComponent(scenario.component), vm = wrapper.vm as any
    scenario.prepare(vm); await flushPromises()
    const pending = deferred(); files.downloadXlsx.mockReturnValueOnce(pending.promise)
    const first = vm.exportXlsx(scenario.exportKind)
    await flushPromises()
    const current = files.downloadXlsx.mock.calls[0][3]
    expect(current()).toBe(true)
    await host.setProps({ show: false })
    expect(current()).toBe(false)
    pending.resolve(false); await first
    await host.setProps({ show: true }); await flushPromises()
    expect(vm.exporting).toBe(false)
    expect(wrapper.emitted('notice')).toBeUndefined()
  })
})

describe.each(scenarios.filter(scenario => scenario.csv))('$name original CSV backup compatibility', scenario => {
  it('retains exactly the original bytes and column order without sanitizing business prefixes', async () => {
    const wrapper = await page(scenario), vm = wrapper.vm as any
    await vm[scenario.csvMethod!]()
    expect(files.downloadXlsx).not.toHaveBeenCalled()
    expect(files.create).toHaveBeenCalledTimes(1)
    const blob = files.create.mock.calls[0][0]
    expect(await readBlob(blob)).toBe(scenario.csv)
    expect(files.revoke).toHaveBeenCalledWith('blob:raw-backup')
    if (scenario.name === '库存余额') expect(blob).toBe(await api.fetchProductsExportBlob.mock.results[0].value)
  })
})

describe('remote full-data export boundaries', () => {
  it.each(['session', 'unmount'])('does not pass a late remote user CSV to XLSX after %s changes', async change => {
    const scenario = scenarios.find(item => item.name === '用户')!, wrapper = await page(scenario), vm = wrapper.vm as any
    const pending = deferred(); api.fetchUsersExportBlob.mockReturnValueOnce(pending.promise)
    const first = vm.exportXlsx()
    if (change === 'session') await wrapper.setProps({ token: 'fixture-two' })
    else wrapper.unmount()
    pending.resolve(csvBlob(userCsv)); await first
    expect(files.downloadXlsx).not.toHaveBeenCalled()
    expect(wrapper.emitted('notice')).toBeUndefined()
  })

  it('passes an account/disposal guard to the legacy hash-inclusive user CSV helper', async () => {
    const scenario = scenarios.find(item => item.name === '用户')!, wrapper = await page(scenario), vm = wrapper.vm as any
    const pending = deferred(); api.exportUsersCsv.mockReturnValueOnce(pending.promise)
    const first = vm.handleExport(), second = vm.handleExport()
    expect(api.exportUsersCsv).toHaveBeenCalledTimes(1)
    const [token, current] = api.exportUsersCsv.mock.calls[0]
    expect(token).toBe('fixture-one'); expect(current()).toBe(true)
    await wrapper.setProps({ token: 'fixture-two' })
    expect(current()).toBe(false)
    pending.resolve(undefined); await Promise.all([first, second])
    expect(wrapper.emitted('notice')).toBeUndefined()
  })

  it('only converts the explicitly known balance quantity column, leaving unusual/large quantities and all codes as text', async () => {
    const scenario = scenarios.find(item => item.name === '库存余额')!, wrapper = await page(scenario), vm = wrapper.vm as any
    const values = ['-3', '0.125', '123456789012345', '1234567890123456', ' 3 ', '001', '=1+1', '+3', '3e2']
    api.fetchProductsExportBlob.mockResolvedValueOnce(csvBlob('库存数量,产品编号\n' + values.map(value => `${value},001`).join('\n')))
    await vm.exportXlsx()
    const matrix = files.downloadXlsx.mock.calls[0][2]
    expect(matrix.slice(1).map((row: any[]) => row[0])).toEqual([-3, 0.125, 123456789012345, ...values.slice(3)])
    expect(matrix.slice(1).every((row: any[]) => row[1] === '001')).toBe(true)
  })

  it('translates authored XLSX headers without translating custom field names or raw CSV machine headers', async () => {
    setUiLocale('en-US')
    const scenario = scenarios.find(item => item.name === '工单')!, wrapper = await page(scenario), vm = wrapper.vm as any
    await vm.exportXlsx()
    const matrix = files.downloadXlsx.mock.calls[0][2]
    expect(matrix[0][0]).toBe('Work order code'); expect(matrix[0][matrix[0].length - 1]).toBe('保存')
    expect(matrix[1][4]).toBe('In progress'); expect(matrix[1][matrix[1].length - 1]).toBe('=SUM(A1:A2)')
    await vm.handleExport()
    expect(await readBlob(files.create.mock.calls[0][0])).toBe(scenario.csv)
  })
})

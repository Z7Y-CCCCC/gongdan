import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import ExcelJS from 'exceljs'
import BaseDataPage from '../BaseDataPage.vue'
import UserManagementPage from '../UserManagementPage.vue'
import WorkOrderPage from '../WorkOrderPage.vue'
import { setUiLocale, setUiTimezone } from '../../i18n'
import { printSheetName } from '../../utils/printHtml'
const mocks = vi.hoisted(() => Object.fromEntries(['fetchUnits', 'fetchDefects', 'fetchDepartments', 'fetchProcesses',
  'fetchProcessRoutes', 'fetchSystemImportLogs', 'fetchWorkOrders', 'fetchProducts', 'fetchCustomFields',
  'fetchWorkOrderCategories', 'fetchBoms', 'fetchUserPreference'].map(name => [name, vi.fn()])))
const output = vi.hoisted(() => ({ saveAs: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...mocks }))
vi.mock('file-saver', () => ({ saveAs: output.saveAs }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,') } }))
const wrappers: any[] = []
function windowFixture() {
  const target = { document: { write: vi.fn(), close: vi.fn() }, focus: vi.fn(), print: vi.fn() }
  vi.spyOn(window, 'open').mockReturnValue(target as unknown as Window)
  return target
}
function printed(target: ReturnType<typeof windowFixture>) {
  return new DOMParser().parseFromString(target.document.write.mock.calls[0][0], 'text/html')
}
async function page(component: any, props: Record<string, unknown> = {}) {
  const wrapper = shallowMount(component, { props: { token: 'fixture-token', ...props } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
async function arrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(blob)
  })
}
const order = { id: 1, code: '工单编号/:*?TEST', productId: 1, productCode: '产品编号', productName: '产品名称', productSpec: '产品规格',
  plannedQuantity: 12, goodQuantity: 2, defectQuantity: 0, status: 'EXECUTING', priority: 'HIGH', routeName: '工艺路线',
  categoryName: '分类', customerName: '客户名称', remark: '备注', creator: '创建人', createdAt: '2026-11-24 10:00:00',
  tasks: [{ id: 11, processName: '工序名称', plannedQuantity: 12, completedQuantity: 2 }],
  materials: [{ materialCode: '物料编号', materialName: '物料名称', materialSpec: '物料规格', quantity: 3 }],
  customValues: [{ fieldId: 99, fieldName: '状态', value: '预览' }] }
const user = { id: 1, username: '账号<svg onload=alert(1)>', name: '姓名</td><script>alert(1)</script>', role: 'ADMIN', roleLabel: '管理员',
  phone: '13800000000', departmentId: 1, departmentName: '部门&产品', enabled: true, virtualUser: true }
beforeEach(() => {
  vi.resetAllMocks()
  for (const mock of Object.values(mocks)) mock.mockResolvedValue({ items: [], total: 0 })
  mocks.fetchWorkOrderCategories.mockResolvedValue([])
  mocks.fetchUserPreference.mockResolvedValue({ value: null })
  mocks.fetchDepartments.mockResolvedValue({ items: [{ id: 1, name: '部门&产品', code: 'D1' }], total: 1 })
  setUiLocale('en-US'); setUiTimezone('Asia/Shanghai')
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('localized ordinary printing preserves business data', () => {
  it('prints process labels in English, resolves permission names, and escapes business HTML', async () => {
    const target = windowFixture()
    const wrapper = await page(BaseDataPage, { activeBasedataMenu: 'process', users: [user] })
    const process = { id: 1, code: '工序编号', name: '工序名称<svg>', reportPermission: ['all', 'dept-1', 'emp-1'], reportRatio: 2,
      defectItemIds: [], taskPunchIn: true, goodQuantity: '良品数量字段', machineName: '机械/机台', feedCount: '10',
      correspondingMachine: '对应机台', requirements: '要求<script>', collectData: [{ type: '类型', name: '名称<img>' }] }
    const snapshot = JSON.stringify(process)
    ;(wrapper.vm as any).printProcess(process)
    const doc = printed(target)
    expect(doc.documentElement.lang).toBe('en-US')
    expect(doc.querySelector('h1')!.textContent).toBe('Process information card')
    expect(doc.querySelector('.meta')!.textContent).toMatch(/Printed at \d{4}-\d{2}-\d{2}/)
    expect(Array.from(doc.querySelectorAll('th')).map(th => th.textContent).join(' ')).not.toMatch(/[\u3400-\u9fff]/)
    expect(doc.body.textContent).toContain('工序名称<svg>')
    expect(doc.body.textContent).toContain('Everyone、部门&产品、' + user.name)
    expect(doc.body.textContent).toContain('名称<img>')
    expect(doc.querySelectorAll('script, img, svg').length).toBe(0)
    expect(target.print).toHaveBeenCalledTimes(1)
    expect(JSON.stringify(process)).toBe(snapshot)
  })

  it('prints user details with translated enum/UI labels while escaping all personal fields', async () => {
    const target = windowFixture()
    const wrapper = await page(UserManagementPage, { users: [user], departments: [], currentUser: user })
    ;(wrapper.vm as any).handlePrintUser(user)
    const doc = printed(target)
    expect(doc.documentElement.lang).toBe('en-US')
    expect(doc.querySelector('h1')!.textContent).toBe('Work Order Hub — User details')
    expect(doc.querySelector('title')!.textContent).toContain(user.name)
    expect(doc.body.textContent).toContain(user.name)
    expect(doc.body.textContent).toContain(user.username)
    expect(doc.body.textContent).toContain(user.departmentName)
    expect(doc.body.textContent).toContain('Administrator')
    expect(doc.body.textContent).toContain('Desktop and mobile')
    expect(doc.body.textContent).toContain('Enabled')
    expect(doc.body.textContent).toContain('Yes')
    expect(doc.querySelectorAll('script, svg').length).toBe(0)
    expect(target.print).toHaveBeenCalledTimes(1)
  })

  it('prints the selected user list without translating business names or adding inline scripts', async () => {
    const target = windowFixture()
    const second = { ...user, id: 2, name: '不打印的姓名', enabled: false, role: 'PRODUCTION', roleLabel: '生产人员' }
    const wrapper = await page(UserManagementPage, { users: [user, second], departments: [], currentUser: user })
    const vm = wrapper.vm as any
    vm.filterForm.status = '全部'; vm.selectedUserIds = [1]
    vm.handlePrintTemplate()
    const doc = printed(target)
    expect(doc.documentElement.lang).toBe('en-US')
    expect(doc.querySelector('h1')!.textContent).toBe('Work Order Hub — Users')
    expect(doc.querySelector('.info')!.textContent).toContain('1 people')
    expect(Array.from(doc.querySelectorAll('th')).map(th => th.textContent).join(' ')).not.toMatch(/[\u3400-\u9fff]/)
    expect(doc.querySelectorAll('tbody tr')).toHaveLength(1)
    expect(doc.body.textContent).toContain(user.name)
    expect(doc.body.textContent).not.toContain(second.name)
    expect(doc.querySelectorAll('script, svg')).toHaveLength(0)
    expect(target.print).toHaveBeenCalledTimes(1)
  })

  it('keeps Chinese print UI available without changing user data', async () => {
    setUiLocale('zh-CN')
    const target = windowFixture()
    const wrapper = await page(UserManagementPage, { users: [user], departments: [], currentUser: user })
    ;(wrapper.vm as any).handlePrintUser(user)
    const doc = printed(target)
    expect(doc.documentElement.lang).toBe('zh-CN')
    expect(doc.querySelector('h1')!.textContent).toBe('小工单用户信息')
    expect(doc.body.textContent).toContain(user.name)
    expect(doc.body.textContent).toContain('电脑端 + 手机端')
  })

  it('reports blocked print windows instead of pretending a document was printed', async () => {
    vi.spyOn(window, 'open').mockReturnValue(null)
    const wrapper = await page(UserManagementPage, { users: [user], departments: [], currentUser: user })
    ;(wrapper.vm as any).handlePrintUser(user)
    await flushPromises()
    expect(wrapper.emitted('error')?.[0]?.[0]).toMatch(/browser|pop/i)
  })

  it.each(['flow', 'list', 'qr', 'materials'])('writes a real localized %s workbook while keeping business cells/custom headers intact', async template => {
    const wrapper = await page(WorkOrderPage)
    const vm = wrapper.vm as any
    vm.workOrders = [order]; vm.selectedIds = [1]; vm.selectedExcelTpl = template
    vm.customFields = [{ id: 99, name: '状态', scope: 'workorder', fieldType: 'text' }]
    const before = JSON.stringify(order)
    await vm.doExcelPrint('download')
    expect(output.saveAs).toHaveBeenCalledTimes(1)
    const [blob, name] = output.saveAs.mock.calls[0]
    expect(name).not.toMatch(/[\u3400-\u9fff]/)
    const workbook = new ExcelJS.Workbook()
    await workbook.xlsx.load(await arrayBuffer(blob) as any)
    const sheet = workbook.worksheets[0]
    expect(sheet.name.length).toBeLessThanOrEqual(31)
    expect(sheet.name).not.toMatch(/[\\/*?:\[\]]/)
    const values: unknown[] = []
    sheet.eachRow(row => row.eachCell(cell => { values.push(cell.value) }))
    expect(values).toContain(order.code)
    expect(values).toContain(order.productName)
    if (template === 'flow' || template === 'list') {
      expect(values).toContain('状态') // user-defined header intentionally stays Chinese
      expect(values).toContain('预览') // business value looks like a UI key but is not translated
      expect(values).toContain('In progress')
    }
    if (template === 'qr') {
      expect(String(sheet.getCell('C2').value)).toContain('/#/pages/work-order-detail/index?id=1&code=')
      expect(String(sheet.getCell('C2').value)).not.toContain('/workorder/')
    }
    if (template === 'materials' || template === 'flow') expect(values).toContain('物料名称')
    expect(JSON.stringify(order)).toBe(before)
  })

  it('leaves CSV import/export machine headers in the original format in English mode', async () => {
    const create = vi.fn().mockReturnValue('blob:csv-fixture')
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: vi.fn() }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = await page(WorkOrderPage)
    const vm = wrapper.vm as any
    vm.workOrders = [order]; vm.selectedIds = [1]
    vm.handleExport()
    const data = new TextDecoder().decode(await arrayBuffer(create.mock.calls[0][0]))
    expect(data.replace(/^\uFEFF/, '')).toMatch(/^工单编号,产品编号,产品名称/)
    expect(data).toContain(order.productName)
    expect(data).not.toMatch(/^Work order/)
  })

  it('keeps sanitized long/colliding spreadsheet tab names unique without changing business codes', () => {
    const used = new Set<string>()
    const first = printSheetName('Work-order traveler', 'A/?' + 'LONG'.repeat(20), used)
    const second = printSheetName('Work-order traveler', 'A/?' + 'LONG'.repeat(20) + 'OTHER', used)
    expect(first).not.toMatch(/[\\/*?:\[\]]/)
    expect(second).not.toBe(first)
    expect(first.length).toBeLessThanOrEqual(31)
    expect(second.length).toBeLessThanOrEqual(31)
  })
})

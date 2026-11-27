import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { KeepAlive, h } from 'vue'
import BaseDataPage from '../BaseDataPage.vue'
import WorkOrderPage from '../WorkOrderPage.vue'
import { createWorkspaceLeaveGuards, workspaceLeaveGuardsKey } from '../../utils/workspaceLeaveGuards'

const api = vi.hoisted(() => ({
  fetchWorkOrders: vi.fn(), fetchWorkOrderCategories: vi.fn(), fetchProducts: vi.fn(),
  fetchProcesses: vi.fn(), fetchProcessRoutes: vi.fn(), fetchCustomFields: vi.fn(),
  fetchBoms: vi.fn(), fetchUserPreference: vi.fn(), fetchUnits: vi.fn(),
  fetchDefects: vi.fn(), fetchDepartments: vi.fn(), fetchSystemImportLogs: vi.fn(),
  fetchSystemOperationLogs: vi.fn(), createSystemOperationLog: vi.fn(),
  createWorkOrder: vi.fn(), createUnit: vi.fn(), updateUnit: vi.fn(),
}))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))

const unit = { id: 1, name: '箱', remark: '已有备注', createdAt: '2026-08-01' }
const product = { id: 7, code: 'PT-7', name: '测试产品', inventoryQty: 1, unitName: '箱' }
const order = { id: 1, code: 'WO-1', plannedQuantity: 1, categoryId: 3, productId: product.id,
  productName: product.name, productCode: product.code, status: 'NOT_STARTED', priority: 'NORMAL', version: 1, tasks: [] }
let wrapper: VueWrapper | undefined

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (value: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function key(value: string, shiftKey = false) {
  document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: value, shiftKey, bubbles: true, cancelable: true }))
}
function button(root: HTMLElement, text: string) {
  return [...root.querySelectorAll('button')].find(element => element.textContent?.trim() === text)!
}
function confirmDialog() {
  return document.body.querySelector<HTMLElement>('.delete-confirm-backdrop [tabindex="-1"]')!
}

beforeEach(() => {
  vi.resetAllMocks()
  for (const fn of Object.values(api)) fn.mockResolvedValue({ items: [], total: 0 })
  api.fetchWorkOrders.mockResolvedValue({ items: [order], total: 1 })
  api.fetchProducts.mockResolvedValue({ items: [product], total: 1 })
  api.fetchUnits.mockResolvedValue({ items: [unit], total: 1 })
  api.fetchWorkOrderCategories.mockResolvedValue([{ id: 3, name: '测试分类' }])
  api.fetchUserPreference.mockResolvedValue(null)
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  vi.restoreAllMocks()
})

async function orders(guards?: ReturnType<typeof createWorkspaceLeaveGuards>) {
  wrapper = mount(WorkOrderPage, { attachTo: document.body, props: { token: 'test-account' },
    global: { stubs: { TableConfigButton: true }, provide: { [workspaceLeaveGuardsKey]: guards ?? null } } })
  await flushPromises()
  return wrapper
}
async function units() {
  wrapper = mount(BaseDataPage, { attachTo: document.body,
    props: { token: 'test-account', activeBasedataMenu: 'unit', users: [] } })
  await flushPromises()
  return wrapper
}

describe('desktop business form dialogs', () => {
  it('protects the actual work-order workspace from tab closure, logout, and browser unload', async () => {
    const guards = createWorkspaceLeaveGuards()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const page = await orders(guards)
    const id = 'production-workorder'
    await page.get('[aria-label="创建工单"]').trigger('click')
    const input = page.get<HTMLInputElement>('input[placeholder*="自动生成"]')
    expect(guards.hasUnsavedChanges()).toBe(false)
    await input.setValue('KEEP-THIS-DRAFT')
    expect(guards.hasUnsavedChanges()).toBe(true)
    expect(guards.canLeave(['home'], 'close')).toBe(true)
    expect(guards.canLeave([id], 'close')).toBe(false)
    expect(page.emitted('error')).toContainEqual(['工单有未保存的修改，请返回工单表单保存或放弃。'])
    expect(confirm).not.toHaveBeenCalled()
    expect(guards.canLeave(undefined, 'logout')).toBe(false)
    expect(guards.canLeave(undefined, 'logout')).toBe(true)
    expect(input.element.value).toBe('KEEP-THIS-DRAFT')
    page.unmount()
    wrapper = undefined
    expect(guards.hasUnsavedChanges()).toBe(false)
  })

  it.each(['order', 'unit'] as const)('keeps the %s draft in flight during ordinary KeepAlive tab changes', async kind => {
    const guards = createWorkspaceLeaveGuards()
    const pending = deferred<any>()
    const apiSave = kind === 'order' ? api.createWorkOrder : api.createUnit
    apiSave.mockReturnValueOnce(pending.promise)
    const id = kind === 'order' ? 'production-workorder' : 'basedata-unit'
    const pageComponent = kind === 'order' ? WorkOrderPage : BaseDataPage
    const props = kind === 'order' ? { token: 'test-account' } : { token: 'test-account', activeBasedataMenu: 'unit', users: [] }
    wrapper = mount({ props: { show: Boolean }, setup: (host: { show: boolean }) => () => h(KeepAlive, null, {
      default: () => host.show ? h(pageComponent, props) : h('section', 'Other workspace'),
    }) }, { attachTo: document.body, props: { show: true }, global: { provide: { [workspaceLeaveGuardsKey]: guards } } })
    const host = wrapper
    await flushPromises()
    const page = host.findComponent(pageComponent)
    const open = kind === 'order' ? page.get('[aria-label="创建工单"]') : page.findAll('button').find(item => item.text() === '创建单位')!
    await open.trigger('click')
    const modal = page.get('[role="dialog"]')
    if (kind === 'order') {
      await modal.get('select').setValue(String(product.id))
      await modal.get('input[type="number"]').setValue('5')
      button(modal.element as HTMLElement, '保存工单').click()
    } else {
      await modal.get('input[placeholder="请输入单位名称"]').setValue('唯一单位')
      await modal.get('form').trigger('submit')
    }
    expect(apiSave).toHaveBeenCalledTimes(1)
    await host.setProps({ show: false })
    expect(guards.hasUnsavedChanges([id])).toBe(true)
    expect(guards.canLeave([id], 'close')).toBe(false)
    expect(guards.canLeave(undefined, 'logout')).toBe(false)
    expect(page.emitted('error')).toContainEqual([kind === 'order' ? '工单仍在保存或上传，请稍候。' : '单位仍在保存，请稍候。'])
    await host.setProps({ show: true })
    expect(page.get('[role="dialog"]').attributes('aria-busy')).toBe('true')
    if (kind === 'order') button(page.get('[role="dialog"]').element as HTMLElement, '保存中...').click()
    else await page.get('[role="dialog"] form').trigger('submit')
    expect(apiSave).toHaveBeenCalledTimes(1)
    await host.setProps({ show: false })
    pending.resolve(kind === 'order' ? order : unit)
    await flushPromises()
    expect(guards.canLeave([id], 'close')).toBe(true)
    await host.setProps({ show: true })
    expect(page.find('[role="dialog"]').exists()).toBe(false)
  })
  it('focuses the work-order form, owns Tab, and dismisses an unchanged order with Escape', async () => {
    const page = await orders()
    const opener = page.get<HTMLButtonElement>('[aria-label="创建工单"]')
    opener.element.focus()
    await opener.trigger('click')
    await flushPromises()
    const dialog = page.get<HTMLElement>('.workorder-modal[role="dialog"]')
    const close = dialog.get('button[aria-label="关闭"]')
    expect(document.getElementById(dialog.attributes('aria-labelledby')!)?.textContent).toBe('创建工单')
    expect(document.activeElement).toBe(close.element)
    key('Tab', true)
    expect(document.activeElement).toBe(button(dialog.element, '保存工单'))
    key('Tab')
    expect(document.activeElement).toBe(close.element)
    key('Escape')
    await flushPromises()
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(confirmDialog()).toBeNull()
    expect(document.activeElement).toBe(opener.element)
  })

  it.each(['Escape', '取消', '关闭'])('protects the work-order draft when closing via %s', async closeVia => {
    const page = await orders()
    const opener = page.get<HTMLButtonElement>('[aria-label="创建工单"]')
    opener.element.focus()
    await opener.trigger('click')
    await flushPromises()
    const dialog = page.get<HTMLElement>('.workorder-modal[role="dialog"]')
    const input = dialog.get<HTMLInputElement>('input[placeholder*="自动生成"]')
    await input.setValue('ONLY-IN-DRAFT')
    input.element.focus()
    const close = () => closeVia === 'Escape' ? key('Escape') : closeVia === '取消'
      ? button(dialog.element, '取消').click()
      : dialog.get<HTMLButtonElement>('[aria-label="关闭"]').element.click()
    close()
    await flushPromises()
    expect(confirmDialog().textContent).toContain('放弃未保存的修改？')
    expect(confirmDialog().contains(document.activeElement)).toBe(true)
    key('Escape')
    await flushPromises()
    expect(confirmDialog()).toBeNull()
    expect(input.element.value).toBe('ONLY-IN-DRAFT')
    expect(document.activeElement).toBe(input.element)
    close()
    await flushPromises()
    button(confirmDialog(), '放弃修改').click()
    await flushPromises()
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(opener.element)
    await opener.trigger('click')
    expect(page.get<HTMLInputElement>('input[placeholder*="自动生成"]').element.value).toBe('')
  })

  it('only dismisses a nested task picker on the first Escape', async () => {
    const page = await orders()
    await page.get('[aria-label="创建工单"]').trigger('click')
    await flushPromises()
    const dialog = page.get<HTMLElement>('.workorder-modal[role="dialog"]')
    button(dialog.element, '生产任务').click()
    await flushPromises()
    const trigger = dialog.get<HTMLButtonElement>('button[aria-label="添加生产任务"]')
    trigger.element.focus()
    await trigger.trigger('click')
    await flushPromises()
    const picker = page.get<HTMLElement>('.task-selector-modal[role="dialog"]')
    expect(picker.element.contains(document.activeElement)).toBe(true)
    key('Escape')
    await flushPromises()
    expect(page.find('.task-selector-modal').exists()).toBe(false)
    expect(dialog.element.isConnected).toBe(true)
    expect(document.activeElement).toBe(trigger.element)
  })

  it('keeps the work-order draft on a write failure and prevents duplicate writes while pending', async () => {
    const page = await orders()
    const pending = deferred<object>()
    api.createWorkOrder.mockReturnValueOnce(pending.promise)
    await page.get('[aria-label="创建工单"]').trigger('click')
    const dialog = page.get<HTMLElement>('.workorder-modal[role="dialog"]')
    await dialog.get('select').setValue(String(product.id))
    await dialog.get('input[type="number"]').setValue('9')
    const save = button(dialog.element, '保存工单')
    save.click()
    save.click()
    await flushPromises()
    expect(api.createWorkOrder).toHaveBeenCalledTimes(1)
    expect(dialog.attributes('aria-busy')).toBe('true')
    expect(dialog.get('[role="status"]').text()).toBe('保存中...')
    expect(save.disabled).toBe(true)
    key('Escape')
    await flushPromises()
    expect(confirmDialog()).toBeNull()
    pending.reject(new Error('测试：暂时无法保存'))
    await flushPromises()
    expect(dialog.get('[role="alert"]').text()).toBe('测试：暂时无法保存')
    expect(dialog.get<HTMLInputElement>('input[type="number"]').element.value).toBe('9')
    expect(save.disabled).toBe(false)
  })

  it('starts a clean create after leaving a categorized order edit', async () => {
    const page = await orders()
    await page.get('[aria-label="编辑工单"]').trigger('click')
    await flushPromises()
    key('Escape')
    await flushPromises()
    expect(confirmDialog()).toBeNull()
    await page.get('[aria-label="创建工单"]').trigger('click')
    const category = page.findAll('label').find(label => label.text().startsWith('所属分类'))!
    expect(category.get<HTMLSelectElement>('select').element.value).toBe('')
  })

  it('shows useful save feedback, locks a unit submission, and retries without clearing the input', async () => {
    const page = await units()
    const pending = deferred<typeof unit>()
    api.createUnit.mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ ...unit, id: 2, name: '托盘' })
    const opener = page.findAll('button').find(item => item.text() === '创建单位')!
    opener.element.focus()
    await opener.trigger('click')
    await flushPromises()
    const dialog = page.get<HTMLElement>('[role="dialog"]')
    const name = dialog.get<HTMLInputElement>('input[placeholder="请输入单位名称"]')
    expect(document.activeElement).toBe(name.element)
    await name.setValue('托盘')
    const form = dialog.get('form')
    await Promise.all([form.trigger('submit'), form.trigger('submit')])
    expect(api.createUnit).toHaveBeenCalledTimes(1)
    expect(dialog.attributes('aria-busy')).toBe('true')
    expect(dialog.get('[role="status"]').text()).toBe('保存中...')
    expect(dialog.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    key('Escape')
    await flushPromises()
    expect(dialog.element.isConnected).toBe(true)
    pending.reject(new Error('测试：单位未保存'))
    await flushPromises()
    expect(dialog.get('[role="alert"]').text()).toBe('测试：单位未保存')
    expect(name.element.value).toBe('托盘')
    await form.trigger('submit')
    await flushPromises()
    expect(api.createUnit).toHaveBeenCalledTimes(2)
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(opener.element)
  })

  it('does not apply an old unit edit after the session changes', async () => {
    const page = await units()
    const pending = deferred<typeof unit>()
    api.updateUnit.mockReturnValueOnce(pending.promise)
    await page.get('tbody button[aria-label="编辑"]').trigger('click')
    await page.get('form.create-form').trigger('submit')
    expect(api.updateUnit).toHaveBeenCalledTimes(1)
    await page.setProps({ token: 'next-session' })
    pending.resolve({ ...unit, name: '上一账户的改动' })
    await flushPromises()
    expect(page.find('[role="dialog"]').exists()).toBe(false)
    expect(page.text()).not.toContain('上一账户的改动')
  })
})

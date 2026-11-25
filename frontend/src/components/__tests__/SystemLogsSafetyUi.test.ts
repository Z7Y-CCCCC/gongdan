import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import SystemLogsDialog from '../SystemLogsDialog.vue'
import { setUiLocale } from '../../i18n'

const api = vi.hoisted(() => ({ fetchSystemImportLogs: vi.fn(), fetchSystemOperationLogs: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
const wrappers: VueWrapper<any>[] = []
function deferred<T = any>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const log = (fileName = '用户导入.csv') => ({ id: 1, fileName, totalCount: 5, successCount: 4, failCount: 1,
  errors: '第5行：账号已存在', status: '部分成功', operatorName: '保存', createdAt: '2026-11-24T10:00:00' })
async function page(visible = false) {
  const wrapper = shallowMount(SystemLogsDialog, { props: { visible, token: 'fixture-one', module: 'USER', type: 'import', title: '导入日志' }, attachTo: document.body })
  wrappers.push(wrapper); await flushPromises(); return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  api.fetchSystemImportLogs.mockResolvedValue({ items: [log()], total: 31 })
  api.fetchSystemOperationLogs.mockResolvedValue({ items: [{ id: 2, operatorName: '保存', message: '新的工单操作', createdAt: '2026-11-24T10:00:00' }], total: 1 })
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()) })

describe('business log context, safe text and honest read failures', () => {
  it('loads exactly once when mounted visible, and exposes a named keyboard dialog', async () => {
    const wrapper = await page(true)
    expect(api.fetchSystemImportLogs).toHaveBeenCalledExactlyOnceWith('fixture-one', 'USER', 1, 15)
    expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    expect(wrapper.text()).toContain('用户导入.csv')
  })
  it('never claims there are no logs when the log request failed; retry restores records', async () => {
    api.fetchSystemImportLogs.mockRejectedValueOnce(new Error('日志服务暂时不可用'))
    const wrapper = await page(); await wrapper.setProps({ visible: true }); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('日志服务暂时不可用')
    expect(wrapper.text()).not.toContain('暂无导入记录')
    await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('用户导入.csv')
  })
  it('does not mix an old import result into the next module/operation log', async () => {
    const wrapper = await page()
    const old = deferred(); api.fetchSystemImportLogs.mockReturnValueOnce(old.promise)
    await wrapper.setProps({ visible: true })
    await wrapper.setProps({ module: 'WORK_ORDER', type: 'operation', title: '操作记录' }); await flushPromises()
    old.resolve({ items: [log('旧的用户日志.csv')], total: 31 }); await flushPromises()
    expect(wrapper.text()).toContain('新的工单操作')
    expect(wrapper.text()).not.toContain('旧的用户日志.csv')
    expect(api.fetchSystemOperationLogs).toHaveBeenCalledExactlyOnceWith('fixture-one', 'WORK_ORDER', 1, 15)
  })
  it('does not restore hidden data after closing or reuse another token response', async () => {
    const wrapper = await page()
    const old = deferred(); api.fetchSystemImportLogs.mockReturnValueOnce(old.promise)
    await wrapper.setProps({ visible: true })
    await wrapper.setProps({ visible: false, token: 'fixture-two' })
    old.resolve({ items: [log('旧会话.csv')], total: 31 }); await flushPromises()
    expect((wrapper.vm as any).logs).toEqual([])
    await wrapper.setProps({ visible: true }); await flushPromises()
    expect(api.fetchSystemImportLogs).toHaveBeenLastCalledWith('fixture-two', 'USER', 1, 15)
    expect(wrapper.text()).not.toContain('旧会话.csv')
  })
  it('retains the prior successful page on a failed next page and retries that precise page', async () => {
    const wrapper = await page(); await wrapper.setProps({ visible: true }); await flushPromises()
    api.fetchSystemImportLogs.mockRejectedValueOnce(new Error('下一页失败'))
    await wrapper.get('button[aria-label="下一页"]').trigger('click'); await flushPromises()
    expect((wrapper.vm as any).currentPage).toBe(1)
    expect(wrapper.text()).toContain('用户导入.csv')
    api.fetchSystemImportLogs.mockResolvedValueOnce({ items: [log('第二页.csv')], total: 31 })
    await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
    expect(api.fetchSystemImportLogs).toHaveBeenLastCalledWith('fixture-one', 'USER', 2, 15)
    expect((wrapper.vm as any).currentPage).toBe(2)
    expect(wrapper.text()).toContain('第二页.csv')
  })
  it('renders attacker-shaped file/operator/error text without interpreting HTML, and preserves business names in English', async () => {
    const attack = '<img src=x onerror="window.__audit=1">'
    api.fetchSystemImportLogs.mockResolvedValueOnce({ items: [{ ...log(attack), errors: attack, operatorName: '保存' }], total: 1 })
    setUiLocale('en-US')
    const wrapper = await page(); await wrapper.setProps({ visible: true }); await flushPromises()
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain(attack)
    expect(wrapper.text()).toContain('保存')
  })
})

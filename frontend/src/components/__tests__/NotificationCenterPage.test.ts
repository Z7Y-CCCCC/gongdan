import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import NotificationCenterPage from '../NotificationCenterPage.vue'
import { setUiLocale } from '../../i18n'

const mocks = vi.hoisted(() => ({
  fetchNotifications: vi.fn(), markNotificationRead: vi.fn(), markNotificationUnread: vi.fn(),
  archiveNotification: vi.fn(), markAllNotificationsRead: vi.fn()
}))
vi.mock('../../api', () => mocks)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const notification = (id = 1) => ({
  id, title: `通知${id}`, content: '报工待审批', eventType: 'REPORT_PENDING_APPROVAL',
  priority: 'NORMAL', state: 'UNREAD', unread: true, createdAt: '2026-11-24T10:00:00'
})
const result = (items = [notification()], total = items.length, unreadCount = total) => ({ items, total, unreadCount })

describe('notification center regressions', () => {
  let wrapper: VueWrapper<any>
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.fetchNotifications.mockResolvedValue(result())
    mocks.markNotificationRead.mockResolvedValue({})
    mocks.markNotificationUnread.mockResolvedValue({})
    mocks.archiveNotification.mockResolvedValue({})
    mocks.markAllNotificationsRead.mockResolvedValue({})
  })
  afterEach(() => wrapper?.unmount())
  async function mountPage() {
    wrapper = shallowMount(NotificationCenterPage, { props: { token: 'test-token' } })
    await flushPromises()
    return wrapper.vm as any
  }

  it('names the search field and announces which range is selected', async () => {
    await mountPage()
    expect(wrapper.get('input[type="search"]').attributes('aria-label')).toBe('搜索通知标题或内容')
    const range = wrapper.get('[role="group"][aria-label="通知范围"]')
    expect(range.get('[aria-pressed="true"]').text()).toBe('当前通知')
    await range.findAll('button')[3].trigger('click')
    expect(range.get('[aria-pressed="true"]').text()).toBe('已归档')
  })

  it('does not claim zero records after a failed refresh, and recovers through retry', async () => {
    mocks.fetchNotifications.mockResolvedValueOnce(result([notification()], 21))
    const vm = await mountPage()
    vm.searchKeyword = '保留筛选'
    mocks.fetchNotifications.mockRejectedValueOnce(new Error('网络故障'))
    await vm.load()
    expect(vm.items.map((item: any) => item.id)).toEqual([1])
    expect(vm.total).toBe(21)
    expect(vm.searchKeyword).toBe('保留筛选')
    expect(wrapper.find('.notification-center-summary').exists()).toBe(false)
    expect(wrapper.find('.notification-center-pagination').exists()).toBe(false)
    expect(wrapper.text()).toContain('网络故障')
    mocks.fetchNotifications.mockResolvedValueOnce(result([notification(2)], 1))
    await wrapper.get('button[aria-label="重试"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('.notification-center-summary').text()).toContain('共 1 条记录')
    expect(vm.items.map((item: any) => item.id)).toEqual([2])
  })

  it('ignores a late response from an older search', async () => {
    const old = deferred<any>()
    mocks.fetchNotifications.mockReturnValueOnce(old.promise)
    const vm = await mountPage()
    mocks.fetchNotifications.mockResolvedValueOnce(result([notification(2)]))
    vm.searchKeyword = '新筛选'
    vm.search()
    await flushPromises()
    old.resolve(result([notification(1)]))
    await flushPromises()
    expect(vm.items.map((item: any) => item.id)).toEqual([2])
    expect(vm.loading).toBe(false)
  })

  it('ignores a late error from an older search', async () => {
    const old = deferred<any>()
    mocks.fetchNotifications.mockReturnValueOnce(old.promise)
    const vm = await mountPage()
    vm.searchKeyword = '新筛选'
    vm.search()
    await flushPromises()
    old.reject(new Error('旧请求失败'))
    await flushPromises()
    expect(vm.error).toBe('')
    expect(vm.items).toHaveLength(1)
  })

  it('resets the page when notification filters change without duplicate requests', async () => {
    mocks.fetchNotifications.mockResolvedValue(result([notification()], 60))
    const vm = await mountPage()
    vm.nextPage()
    await flushPromises()
    expect(vm.page).toBe(2)
    mocks.fetchNotifications.mockClear()
    vm.eventType = 'REPORT_REJECTED'
    await flushPromises()
    expect(vm.page).toBe(1)
    expect(mocks.fetchNotifications).toHaveBeenCalledTimes(1)
    expect(mocks.fetchNotifications.mock.calls[0][1]).toMatchObject({ page: 1, eventType: 'REPORT_REJECTED' })
  })

  it('sends one read mutation and reloads authoritative counts and unread-filter contents', async () => {
    const vm = await mountPage()
    vm.unreadOnly = true
    await flushPromises()
    const item = vm.items[0]
    const pending = deferred<any>()
    mocks.markNotificationRead.mockReturnValue(pending.promise)
    mocks.fetchNotifications.mockResolvedValue(result([], 0, 0))
    const first = vm.toggleRead(item)
    const second = vm.toggleRead(item)
    await flushPromises()
    expect(mocks.markNotificationRead).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[aria-label="标记为已读"]').attributes('disabled')).toBeDefined()
    pending.resolve({})
    await Promise.all([first, second])
    expect(vm.items).toEqual([])
    expect(vm.total).toBe(0)
    expect(vm.unreadCount).toBe(0)
  })

  it('revalidates an archived page and moves back when the last page becomes empty', async () => {
    mocks.fetchNotifications.mockResolvedValue(result([notification()], 21))
    const vm = await mountPage()
    vm.nextPage()
    await flushPromises()
    const item = vm.items[0]
    mocks.fetchNotifications.mockResolvedValueOnce(result([], 20, 20))
      .mockResolvedValueOnce(result([notification(2)], 20, 20))
    await vm.archiveItem(item)
    await flushPromises()
    expect(vm.page).toBe(1)
    expect(vm.items.map((item: any) => item.id)).toEqual([2])
    expect(vm.total).toBe(20)
  })

  it('serializes mark-all-read with item changes and avoids duplicate submissions', async () => {
    const vm = await mountPage()
    const pending = deferred<any>()
    mocks.markAllNotificationsRead.mockReturnValue(pending.promise)
    mocks.fetchNotifications.mockResolvedValue(result([], 0, 0))
    const first = vm.markAllRead()
    const second = vm.markAllRead()
    await vm.toggleRead(vm.items[0])
    expect(mocks.markAllNotificationsRead).toHaveBeenCalledTimes(1)
    expect(mocks.markNotificationRead).not.toHaveBeenCalled()
    pending.resolve({})
    await Promise.all([first, second])
    expect(vm.unreadCount).toBe(0)
  })

  it('keeps navigation available after a read mutation failure and permits retry', async () => {
    const vm = await mountPage()
    const item = vm.items[0]
    mocks.markNotificationRead.mockRejectedValueOnce(new Error('状态更新失败'))
    await vm.openItem(item)
    expect(wrapper.emitted('open')).toEqual([[item]])
    expect(item.unread).toBe(true)
    await vm.toggleRead(item)
    expect(mocks.markNotificationRead).toHaveBeenCalledTimes(2)
  })

  it('passes the updated read state to navigation instead of causing a second parent read', async () => {
    const vm = await mountPage()
    const updated = { ...notification(), unread: false, state: 'READ' }
    mocks.markNotificationRead.mockResolvedValue(updated)
    mocks.fetchNotifications.mockResolvedValue(result([updated], 1, 0))
    await vm.openItem(vm.items[0])
    expect(wrapper.emitted('open')).toEqual([[updated]])
    expect(wrapper.emitted('unread-count')).toContainEqual([0])
  })

  it('clears the old account data and ignores pending results when the token is removed', async () => {
    const pending = deferred<any>()
    mocks.fetchNotifications.mockReturnValueOnce(pending.promise)
    const vm = await mountPage()
    await wrapper.setProps({ token: '' })
    pending.resolve(result())
    await flushPromises()
    expect(vm.items).toEqual([])
    expect(vm.total).toBe(0)
    expect(vm.loading).toBe(false)
  })

  it('localizes system notification display reactively without altering custom contents or stored records', async () => {
    const system = { ...notification(), title: '今日报工', eventType: 'REPORT_SUBMITTED', content: '保存 提交 2 良品 / 1 不良' }
    const custom = { ...system, id: 2, eventType: 'CUSTOM_MESSAGE' }
    mocks.fetchNotifications.mockResolvedValue(result([system, custom]))
    setUiLocale('en-US')
    const vm = await mountPage()
    expect(wrapper.findAll('.notification-center-item-title strong').map(node => node.text())).toEqual(["Today's reports", '今日报工'])
    expect(wrapper.findAll('.notification-center-item-content').map(node => node.text())).toEqual([
      '保存 submitted 2 good / 1 defective', custom.content
    ])
    setUiLocale('zh-CN')
    await flushPromises()
    expect(wrapper.findAll('.notification-center-item-title strong').map(node => node.text())).toEqual(['今日报工', '今日报工'])
    expect(wrapper.findAll('.notification-center-item-content').map(node => node.text())).toEqual([system.content, custom.content])
    expect(vm.items).toEqual([system, custom])
    expect(mocks.fetchNotifications).toHaveBeenCalledTimes(1)
  })
})

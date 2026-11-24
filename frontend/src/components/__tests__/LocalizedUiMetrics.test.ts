import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import { setUiLocale, setUiTimezone, t } from '../../i18n'
import PerformanceSection from '../PerformanceSection.vue'
import CacheMonitorPage from '../CacheMonitorPage.vue'
import InternalChatPage from '../InternalChatPage.vue'

const mocks = vi.hoisted(() => ({
  fetchHomePerformance: vi.fn(), fetchCacheMetrics: vi.fn(), fetchInternalChatContacts: vi.fn(),
  fetchInternalChatMessages: vi.fn(), markInternalChatRead: vi.fn(), sendInternalChatMessage: vi.fn()
}))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...mocks }))

const performance = { plannedQuantity: 10, completedQuantity: 8, workOrderCount: 2, defectRatePercent: 2.5,
  averageCycleDays: 3.25, overdueUnfinishedCount: 1 }
const metricLabels = ['Redis 版本', '缓存类型', '运行模式', '端口', '客户端数', '运行时间（天）',
  '使用内存', '内存配置', 'AOF 是否开启', 'RDB 状态', 'Key 数量', '网络入口', '网络出口']
const contact = { id: 1, name: '保存', username: 'qa', roleLabel: '管理员', departmentName: '制造',
  unreadCount: 0, online: false, lastSeenAt: '2026-11-24T11:58:00Z', lastLoginAt: '2026-11-24T11:00:00Z',
  lastMessage: '保存', lastMessageAt: '2026-11-24T11:59:00Z' }
const wrappers: VueWrapper[] = []
async function page(component: any) {
  const wrapper = shallowMount(component, { props: { token: 'fixture-token', ...(component === InternalChatPage ? { currentUser: null } : {}) } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.fetchHomePerformance.mockResolvedValue(performance)
  mocks.fetchCacheMetrics.mockResolvedValue({ basicInfo: { redisVersion: '7.2', cacheType: 'Redis缓存', mode: 'standalone',
    clients: '2', uptimeDays: '3', usedMemory: '64MB', aofEnabled: '是', status: '连接正常', recoveryWindow: '30分钟' },
    commandStats: [], memoryInfo: { usedBytes: 64, maxBytes: 128, usedPercent: 50, usedHuman: '64B', maxHuman: '128B' } })
  mocks.fetchInternalChatContacts.mockResolvedValue([{ ...contact }])
  mocks.fetchInternalChatMessages.mockResolvedValue([{ id: 1, senderId: 1, senderName: '保存', receiverId: 2,
    content: '保存', read: false, createdAt: '2026-11-24T11:59:00Z' }])
  mocks.markInternalChatRead.mockResolvedValue(undefined)
  mocks.sendInternalChatMessage.mockResolvedValue({ id: 2 })
  vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-11-24T12:00:00Z').getTime())
})
afterEach(() => {
  wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
  setUiLocale('zh-CN')
  setUiTimezone('Asia/Shanghai')
})

describe('script-generated trusted UI formatting', () => {
  it('translates average-cycle days with numeric spacing while preserving percentages and quantities', async () => {
    setUiLocale('en-US')
    const wrapper = await page(PerformanceSection)
    expect(wrapper.findAll('.kpi-value').map(node => node.text())).toEqual(['10', '8', '2', '2.50%', '3.3 days', '1'])
    expect(wrapper.text()).not.toContain('天')
  })

  it('changes displayed units immediately when locale changes without refetching data', async () => {
    const wrapper = await page(PerformanceSection)
    expect(wrapper.findAll('.kpi-value')[4].text()).toBe('3.3天')
    setUiLocale('en-US')
    await flushPromises()
    expect(wrapper.findAll('.kpi-value')[4].text()).toBe('3.3 days')
    setUiLocale('zh-CN')
    await flushPromises()
    expect(wrapper.findAll('.kpi-value')[4].text()).toBe('3.3天')
    expect(mocks.fetchHomePerformance).toHaveBeenCalledTimes(1)
  })

  it('renders zero-day values truthfully and does not attach units to failed KPIs', async () => {
    setUiLocale('en-US')
    mocks.fetchHomePerformance.mockResolvedValueOnce({ ...performance, averageCycleDays: 0 })
    const wrapper = await page(PerformanceSection)
    expect(wrapper.findAll('.kpi-value')[4].text()).toBe('0.0 days')
    mocks.fetchHomePerformance.mockRejectedValueOnce(new Error('offline'))
    await (wrapper.vm as any).loadPerformance()
    await flushPromises()
    expect(wrapper.findAll('.kpi-value').map(node => node.text())).toEqual(Array(6).fill('--'))
  })

  it('translates all fixed cache-metric labels without changing raw identifiers or numeric data', async () => {
    setUiLocale('en-US')
    const wrapper = await page(CacheMonitorPage)
    expect(wrapper.findAll('.metric-item > span').map(node => node.text())).toEqual(metricLabels.map(label => t(label)))
    expect(wrapper.findAll('.metric-item > strong').map(node => node.text())).toContain('7.2')
    expect(wrapper.findAll('.metric-item > strong').map(node => node.text())).toContain('standalone')
    expect(wrapper.findAll('.metric-item > strong').map(node => node.text())).toContain('64MB')
    setUiLocale('zh-CN')
    await flushPromises()
    expect(wrapper.findAll('.metric-item > span').map(node => node.text())).toEqual(metricLabels)
    expect(mocks.fetchCacheMetrics).toHaveBeenCalledTimes(1)
  })

  it('translates nested chat presence and elapsed UI but leaves names, departments and message text intact', async () => {
    setUiLocale('en-US')
    const wrapper = await page(InternalChatPage)
    expect(wrapper.get('.internal-chat-peer-status').text()).toContain('Offline · Active 2 minutes ago')
    expect(wrapper.get('.internal-chat-peer-status').attributes('title')).toContain('Offline · Active 2 minutes ago; last sign-in')
    expect(wrapper.get('.internal-chat-peer-title h2').text()).toBe('保存')
    expect(wrapper.get('.internal-chat-peer-title p').text()).toContain('制造')
    expect(wrapper.get('.internal-chat-bubble').text()).toBe('保存')
    expect(wrapper.get('.internal-chat-contact-preview').text()).toBe('保存')
    setUiLocale('zh-CN')
    await flushPromises()
    expect(wrapper.get('.internal-chat-peer-status').text()).toContain('离线 · 2分钟前活跃')
    expect(wrapper.get('.internal-chat-bubble').text()).toBe('保存')
  })

  it('localizes every elapsed-time range and online-device tooltip with no leftover Chinese UI fragments', async () => {
    setUiLocale('en-US')
    const wrapper = await page(InternalChatPage)
    const vm = wrapper.vm as any
    const now = Date.now()
    expect(vm.formatElapsed()).toBe('No activity history')
    expect(vm.formatElapsed(new Date(now - 5000).toISOString())).toBe('Active just now')
    expect(vm.formatElapsed(new Date(now - 30000).toISOString())).toBe('Active 30 seconds ago')
    expect(vm.formatElapsed(new Date(now - 120000).toISOString())).toBe('Active 2 minutes ago')
    expect(vm.formatElapsed(new Date(now - 7200000).toISOString())).toBe('Active 2 hours ago')
    expect(vm.formatElapsed(new Date(now - 172800000).toISOString())).toBe('Active 2 days ago')
    expect(vm.presenceTitle({ ...contact, online: true, onlineDeviceCount: 2 })).toBe('Online · 2 devices; click to view device details')
    expect(vm.presenceLabel({ ...contact, online: false, lastSeenAt: undefined })).toBe('No sign-in history')
  })

  it('interprets offset-free chat timestamps consistently and honors the selected display timezone', async () => {
    const wrapper = await page(InternalChatPage)
    const vm = wrapper.vm as any
    expect(vm.formatElapsed('2026-11-24T12:00:00')).toBe('8小时前活跃')
    expect(vm.formatTime('2026-11-24T12:00:00', true)).toBe('11-24 12:00')
    setUiTimezone('America/New_York')
    expect(vm.formatTime('2026-11-24T12:00:00', true)).toBe('11-23 23:00')
    expect(vm.formatTime('2026-11-24T04:00:00Z', true)).toBe('11-23 23:00')
    expect(vm.formatTime('fixture invalid date', true)).toBe('fixture invalid date')
  })
})

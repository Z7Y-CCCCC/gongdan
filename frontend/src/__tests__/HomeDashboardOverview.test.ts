import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HomeDashboardPage from '../views/HomeDashboardPage.vue'

const api = vi.hoisted(() => ({ reports: vi.fn(), orders: vi.fn() }))
vi.mock('../api', async (load) => ({
  ...await load<typeof import('../api')>(), fetchReports: api.reports, fetchHomeWorkOrders: api.orders
}))

describe('home overview counts', () => {
  beforeEach(() => {
    api.reports.mockReset().mockResolvedValue({ items: [{}], total: 7 })
    api.orders.mockReset().mockResolvedValue({ items: Array.from({ length: 500 }, () => ({})) })
  })

  it('uses the approval total, caps the preview count honestly, and accepts a real overdue zero', async () => {
    const page = shallowMount(HomeDashboardPage, { props: { token: 'test' } })
    try {
      await flushPromises()
      expect(api.reports).toHaveBeenCalledWith('test', { status: 'PENDING', page: 1, size: 1 })
      expect(page.get('.home-metric--amber strong').text()).toBe('7')
      expect(page.get('.home-metric--green strong').text()).toBe('至少 500')
      expect(page.get('.home-metric--red strong').text()).toBe('—')
      page.getComponent({ name: 'PerformanceSection' }).vm.$emit('overdue-count', 0, false)
      await flushPromises()
      expect(page.get('.home-metric--red strong').text()).toBe('0')
    } finally { page.unmount() }
  })

  it('does not display stale or false-zero counts when a session changes or a request fails', async () => {
    let resolveOld: (value: { items: unknown[]; total: number }) => void = () => {}
    api.reports.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
      .mockRejectedValueOnce(new Error('offline'))
    api.orders.mockResolvedValue({ items: [] })
    const page = shallowMount(HomeDashboardPage, { props: { token: 'old' } })
    try {
      await page.setProps({ token: 'new' })
      resolveOld({ items: [], total: 30 })
      await flushPromises()
      expect(page.get('.home-metric--amber strong').text()).toBe('—')
      expect(page.get('.home-metric--amber .home-metric-error').text()).toBe('加载失败')
      expect(page.get('.home-metric--green strong').text()).toBe('0')
    } finally { page.unmount() }
  })
})

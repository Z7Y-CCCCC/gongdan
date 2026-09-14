import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import WorkOrderProgressSection from '../WorkOrderProgressSection.vue'

const fetchOrders = vi.hoisted(() => vi.fn())
vi.mock('../../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api')>(), fetchHomeWorkOrders: fetchOrders
}))

function orders(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    id: index + 1, code: `WO-${index + 1}`, productCode: 'P1', productName: '产品',
    productSpec: '', status: 'NOT_STARTED', statusLabel: '未开始'
  }))
}

describe('home work-order display limit', () => {
  let wrapper: VueWrapper<any> | undefined
  beforeEach(() => { fetchOrders.mockReset() })
  afterEach(() => { wrapper?.unmount() })

  it.each([{ count: 499, warning: false }, { count: 500, warning: true }])
    ('shows an explicit limit notice only when the result reaches the limit ($count rows)', async ({ count, warning }) => {
      fetchOrders.mockResolvedValue({ items: orders(count) })
      wrapper = shallowMount(WorkOrderProgressSection, { props: { token: 'test-token' } })
      await flushPromises()
      expect(wrapper.text().includes('最多显示500条，完整工单请到工单列表')).toBe(warning)
      expect(fetchOrders).toHaveBeenCalledTimes(1)
    })

  it('removes the limit notice when the next tab returns fewer results', async () => {
    fetchOrders.mockResolvedValueOnce({ items: orders(500) }).mockResolvedValueOnce({ items: orders(1) })
    wrapper = shallowMount(WorkOrderProgressSection, { props: { token: 'test-token' } })
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    ;(wrapper.vm as any).switchTab('in_progress')
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(fetchOrders).toHaveBeenCalledTimes(2)
  })
})

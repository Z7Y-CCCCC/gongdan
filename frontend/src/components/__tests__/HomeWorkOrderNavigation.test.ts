import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import HomeDashboardPage from '../../views/HomeDashboardPage.vue'
import WorkOrderProgressSection from '../WorkOrderProgressSection.vue'
import LiveBroadcastSection from '../LiveBroadcastSection.vue'
import HomeWorkOrderDrawer from '../HomeWorkOrderDrawer.vue'

describe('首页工单详情抽屉', () => {
  it('opens the drawer with the exact ID from progress or live broadcast, without changing workspace', async () => {
    const wrapper = shallowMount(HomeDashboardPage, { props: { token: 'test-token' } })
    try {
      const drawer = wrapper.getComponent(HomeWorkOrderDrawer)
      wrapper.getComponent(WorkOrderProgressSection).vm.$emit('view', 5001)
      await nextTick()
      expect(drawer.props('orderId')).toBe(5001)
      drawer.vm.$emit('dismiss')
      await nextTick()
      expect(drawer.props('orderId')).toBeNull()
      wrapper.getComponent(LiveBroadcastSection).vm.$emit('view-work-order', 42)
      await nextTick()
      expect(drawer.props('orderId')).toBe(42)
      expect(wrapper.emitted('navigate')).toBeUndefined()
    } finally { wrapper.unmount() }
  })
})

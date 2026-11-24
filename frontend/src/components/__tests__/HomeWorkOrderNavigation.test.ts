import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import HomeDashboardPage from '../../views/HomeDashboardPage.vue'
import WorkOrderProgressSection from '../WorkOrderProgressSection.vue'
import LiveBroadcastSection from '../LiveBroadcastSection.vue'

describe('首页真实工单导航事件', () => {
  it('forwards work-order progress and production broadcast actions with their real IDs', () => {
    const wrapper = shallowMount(HomeDashboardPage, { props: { token: 'test-token' } })
    wrapper.getComponent(WorkOrderProgressSection).vm.$emit('view', 5001)
    wrapper.getComponent(LiveBroadcastSection).vm.$emit('view-work-order', 42)
    expect(wrapper.emitted('view-work-order')).toEqual([[5001], [42]])
    wrapper.unmount()
  })
})

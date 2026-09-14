import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, KeepAlive, ref } from 'vue'
import LiveBroadcastSection from '../LiveBroadcastSection.vue'

const fetchReports = vi.hoisted(() => vi.fn())
vi.mock('../../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api')>(), fetchHomeRecentReports: fetchReports
}))

describe('broadcast polling lifecycle', () => {
  let wrapper: VueWrapper<any> | undefined
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    fetchReports.mockReset().mockResolvedValue({ items: [] })
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
  })

  it('stops polling while kept alive in an inactive tab and resumes when activated', async () => {
    const visible = ref(true)
    wrapper = mount(defineComponent({
      setup: () => () => h(KeepAlive, null, {
        default: () => visible.value ? h(LiveBroadcastSection, { token: 'test-token', active: true }) : null
      })
    }))
    await flushPromises()
    expect(fetchReports).toHaveBeenCalledTimes(1)
    visible.value = false
    await flushPromises()
    await vi.advanceTimersByTimeAsync(60000)
    expect(fetchReports).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
    visible.value = true
    await flushPromises()
    expect(fetchReports).toHaveBeenCalledTimes(2)
    expect(vi.getTimerCount()).toBe(1)
  })

  it('does not resurrect polling when the initial request finishes after unmount', async () => {
    let resolve!: (value: { items: [] }) => void
    fetchReports.mockReturnValueOnce(new Promise(done => { resolve = done }))
    wrapper = shallowMount(LiveBroadcastSection, { props: { token: 'test-token', active: true } })
    wrapper.unmount()
    wrapper = undefined
    resolve({ items: [] })
    await flushPromises()
    expect(vi.getTimerCount()).toBe(0)
  })
})

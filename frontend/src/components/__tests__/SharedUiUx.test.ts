import { afterEach, describe, expect, it } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import WorkspaceTabs from '../WorkspaceTabs.vue'
import LoadingProgressOverlay from '../LoadingProgressOverlay.vue'
import UiIcon from '../UiIcon.vue'
import ColumnSettingsPanel from '../ColumnSettingsPanel.vue'

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('Shared task-first UI', () => {
  it('keeps at least one table column and cancels unsaved settings with Escape', async () => {
    const columns = [{ key: 'name', label: '名称', width: 120, visible: true, fixed: 'none' as const }]
    const wrapper = mount(ColumnSettingsPanel, { props: { modelValue: columns, defaults: columns }, attachTo: document.body })
    wrappers.push(wrapper)
    await flushPromises()
    await wrapper.get('input[type="checkbox"]').setValue(false)
    expect(wrapper.get('button[aria-label="应用列设置"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('至少显示一列')
    expect(columns[0].visible).toBe(true)
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
    document.dispatchEvent(event)
    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })
  it('renders column settings in the same decorative SVG family without an icon font', () => {
    const wrapper = mount(UiIcon, { props: { name: 'table-columns', size: 18 } })
    wrappers.push(wrapper)
    const icon = wrapper.get('svg')
    expect(icon.attributes('aria-hidden')).toBe('true')
    expect(icon.attributes('focusable')).toBe('false')
    expect(icon.attributes('width')).toBe('18')
    expect(icon.find('rect').exists()).toBe(true)
    expect(wrapper.find('span').exists()).toBe(false)
  })
  it('keeps loading feedback and progress without a wall of stage explanations', () => {
    const wrapper = mount(LoadingProgressOverlay, { props: {
      visible: true, progress: 37, stage: '加载工单',
      stages: [{ key: 'orders', label: '工单', detail: '重复的详细解释', status: 'loading' }]
    } })
    wrappers.push(wrapper)
    expect(wrapper.text()).toContain('加载工单')
    expect(wrapper.text()).toContain('37%')
    expect(wrapper.text()).not.toContain('重复的详细解释')
    expect(wrapper.text()).not.toContain('请保持页面打开')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('37')
  })

  it('retains the actionable error and an accessible retry control', async () => {
    const wrapper = mount(LoadingProgressOverlay, { props: {
      visible: true, progress: 150, stage: '', stages: [], error: '工单请求超时'
    } })
    wrappers.push(wrapper)
    expect(wrapper.text()).toContain('工单请求超时')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
    const button = wrapper.get('button[aria-label="重试"]')
    expect(button.attributes('title')).toBe('重试')
    await button.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('supports roving keyboard tab selection without selecting on a child close action', async () => {
    const tabs = [
      { id: 'home', label: '主页', pinned: true, closable: false },
      { id: 'orders', label: '工单', pinned: false, closable: true }
    ]
    const wrapper = mount(WorkspaceTabs, { props: { tabs, activeId: 'home' }, attachTo: document.body })
    wrappers.push(wrapper)
    const items = wrapper.findAll('[role="tab"]')
    expect(items.map(item => item.attributes('tabindex'))).toEqual(['0', '-1'])
    await items[0].trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(wrapper.emitted('select')).toEqual([[tabs[1]]])
    expect(document.activeElement).toBe(items[1].element)
    await wrapper.get('button[aria-label="关闭 工单"]').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('select')).toHaveLength(1)
    await wrapper.get('button[aria-label="关闭 工单"]').trigger('click')
    expect(wrapper.emitted('close')).toEqual([[tabs[1]]])
  })

})

import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import ColumnSettingsPanel, { type ColumnSetting } from '../ColumnSettingsPanel.vue'
import DeleteConfirmDialog from '../DeleteConfirmDialog.vue'

const selector = '.workspace-dialog-motion-backdrop'
let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('workspace dialog motion', () => {
  it('restores opener focus before a leaving confirmation disappears and can open it again', async () => {
    const Host = defineComponent({
      setup() {
        const visible = ref(false)
        return () => h('div', [
          h('button', { onClick: () => { visible.value = true } }, 'Open'),
          h(DeleteConfirmDialog, { visible: visible.value,
            'onUpdate:visible': (value) => { visible.value = value } })
        ])
      },
    })
    wrapper = mount(Host, { attachTo: document.body, global: { stubs: { transition: false } } })
    const opener = wrapper.get('button')
    opener.element.focus()
    await opener.trigger('click')
    await nextTick()

    const opening = document.querySelector(selector)
    expect(opening?.classList.contains('workspace-dialog-motion-enter-active')).toBe(true)
    expect(opening?.querySelector('.workspace-dialog-motion-panel')).not.toBeNull()
    expect(document.activeElement).toBe(opening?.querySelector('.btn--ghost'))

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await nextTick()
    await nextTick()
    expect(opening?.hasAttribute('inert')).toBe(true)
    expect(document.activeElement).toBe(opener.element)

    await opener.trigger('click')
    await nextTick()
    const reopened = document.querySelector(selector)
    expect(reopened).not.toBe(opening)
    expect(reopened?.hasAttribute('inert')).toBe(false)
    expect(document.activeElement).toBe(reopened?.querySelector('.btn--ghost'))
  })

  it('ends keyboard ownership as fields animate out and resets the draft on re-open', async () => {
    const columns: ColumnSetting[] = [{ key: 'name', label: '名称', width: 120, visible: true, fixed: 'none' }]
    const Host = defineComponent({
      setup() {
        const visible = ref(false)
        return () => h('div', [
          h('button', { onClick: () => { visible.value = true } }, 'Open fields'),
          h(ColumnSettingsPanel, {
            visible: visible.value,
            modelValue: columns,
            defaults: columns,
            onCancel: () => { visible.value = false },
          }),
        ])
      },
    })
    wrapper = mount(Host, { attachTo: document.body, global: { stubs: { transition: false } } })
    const opener = wrapper.get('button')
    opener.element.focus()
    expect(wrapper.find(selector).exists()).toBe(false)
    await opener.trigger('click')
    await nextTick()

    const opening = wrapper.get('.column-settings-overlay')
    expect(opening.classes()).toContain('workspace-dialog-motion-enter-active')
    expect(document.activeElement).toBe(opening.get('.column-settings-close').element)
    const field = opening.get('input[type="number"]')
    await field.setValue('220')
    expect(columns[0].width).toBe(120)

    await opening.get('.column-settings-close').trigger('keydown', { key: 'Escape' })
    await nextTick()
    expect(opening.attributes('inert')).toBe('')
    expect(opening.classes()).toContain('workspace-dialog-motion-leave-active')
    expect(document.activeElement).toBe(opener.element)

    await opener.trigger('click')
    await nextTick()
    const reopened = wrapper.get('.column-settings-overlay')
    expect(reopened.element).not.toBe(opening.element)
    expect(reopened.attributes('inert')).toBeUndefined()
    expect(reopened.get<HTMLInputElement>('input[type="number"]').element.value).toBe('120')
    expect(document.activeElement).toBe(reopened.get('.column-settings-close').element)
  })
})

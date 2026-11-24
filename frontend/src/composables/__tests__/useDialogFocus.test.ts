import { defineComponent, h, KeepAlive, nextTick, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useDialogFocus } from '../useDialogFocus'

const wrappers: ReturnType<typeof mount>[] = []
const outside: HTMLElement[] = []
afterEach(() => {
  wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount())
  outside.splice(0).forEach(element => element.remove())
})

const Dialog = defineComponent({
  props: { visible: Boolean, disabled: Boolean, dismiss: { type: Function, required: true } },
  setup(props) {
    const root = ref<HTMLElement | null>(null)
    useDialogFocus(root, () => props.visible, () => props.dismiss())
    return () => props.visible ? h('section', { ref: root, tabindex: -1, role: 'dialog' }, [
      h('button', { disabled: true }, 'Unavailable'),
      h('div', { style: 'display:none' }, [h('button', 'Hidden')]),
      h('button', { disabled: props.disabled, 'data-testid': 'first' }, 'First'),
      h('button', { disabled: props.disabled, 'data-testid': 'last' }, 'Last')
    ]) : null
  }
})

function opener() {
  const button = document.createElement('button')
  document.body.append(button)
  outside.push(button)
  button.focus()
  return button
}

async function dialog(visible = true, disabled = false) {
  const dismiss = vi.fn()
  const wrapper = mount(Dialog, { props: { visible, disabled, dismiss }, attachTo: document.body })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, dismiss }
}

function key(key: string, shiftKey = false) {
  const event = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true })
  document.activeElement!.dispatchEvent(event)
  return event
}

describe('modal keyboard focus', () => {
  it('releases keyboard ownership while a KeepAlive page is inactive, and resumes on return', async () => {
    opener()
    const dismiss = vi.fn()
    const Other = defineComponent({ setup: () => () => h('p', 'Other page') })
    const Host = defineComponent({
      props: { show: Boolean },
      setup: props => () => h(KeepAlive, null, {
        default: () => props.show ? h(Dialog, { key: 'dialog', visible: true, dismiss }) : h(Other, { key: 'other' })
      })
    })
    const wrapper = mount(Host, { props: { show: true }, attachTo: document.body })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').element.contains(document.activeElement)).toBe(true)
    await wrapper.setProps({ show: false })
    await flushPromises()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(key('Tab').defaultPrevented).toBe(false)
    key('Escape')
    expect(dismiss).not.toHaveBeenCalled()
    await wrapper.setProps({ show: true })
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').element.contains(document.activeElement)).toBe(true)
    key('Escape')
    expect(dismiss).toHaveBeenCalledTimes(1)
  })

  it('restores into the parent when both dialogs open before the first focus tick', async () => {
    opener()
    const parent = mount(Dialog, { props: { visible: true, dismiss: vi.fn() }, attachTo: document.body })
    const child = mount(Dialog, { props: { visible: true, dismiss: vi.fn() }, attachTo: document.body })
    wrappers.push(parent, child)
    await flushPromises()
    await child.setProps({ visible: false })
    await flushPromises()
    expect(parent.get('[role="dialog"]').element.contains(document.activeElement)).toBe(true)
  })

  it.each(['hidden', 'inert'])('does not restore focus to an opener that is now %s', async attribute => {
    const trigger = opener()
    const { wrapper } = await dialog()
    trigger.setAttribute(attribute, '')
    const focus = vi.spyOn(trigger, 'focus')
    await wrapper.setProps({ visible: false })
    await flushPromises()
    expect(focus).not.toHaveBeenCalled()
    focus.mockRestore()
  })
  it('focuses only available controls, loops both directions and restores the opener', async () => {
    const trigger = opener()
    const { wrapper, dismiss } = await dialog(false)
    expect(document.activeElement).toBe(trigger)
    await wrapper.setProps({ visible: true })
    await flushPromises()
    const first = wrapper.get('[data-testid="first"]').element as HTMLElement
    const last = wrapper.get('[data-testid="last"]').element as HTMLElement
    expect(document.activeElement).toBe(first)
    expect(key('Tab', true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(last)
    expect(key('Tab').defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(first)
    expect(key('Tab').defaultPrevented).toBe(false)
    key('Escape')
    expect(dismiss).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ visible: false })
    await flushPromises()
    expect(document.activeElement).toBe(trigger)
  })

  it('only the upper dialog handles Escape and restores focus within its parent', async () => {
    const trigger = opener()
    const parent = await dialog()
    const innerTrigger = parent.wrapper.get('[data-testid="last"]').element as HTMLElement
    innerTrigger.focus()
    const child = await dialog()
    key('Escape')
    expect(child.dismiss).toHaveBeenCalledTimes(1)
    expect(parent.dismiss).not.toHaveBeenCalled()
    child.wrapper.unmount()
    wrappers.pop()
    expect(document.activeElement).toBe(innerTrigger)
    key('Escape')
    expect(parent.dismiss).toHaveBeenCalledTimes(1)
    parent.wrapper.unmount()
    wrappers.pop()
    expect(document.activeElement).toBe(trigger)
    key('Escape')
    expect(parent.dismiss).toHaveBeenCalledTimes(1)
  })

  it('keeps focus on the dialog when all actions are unavailable', async () => {
    opener()
    const { wrapper } = await dialog(true, true)
    expect(document.activeElement).toBe(wrapper.get('[role="dialog"]').element)
    expect(key('Tab').defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(wrapper.get('[role="dialog"]').element)
  })

  it('recovers the focus loop when focus was programmatically moved outside', async () => {
    const trigger = opener()
    const { wrapper } = await dialog()
    trigger.focus()
    key('Tab')
    expect(document.activeElement).toBe(wrapper.get('[data-testid="first"]').element)
    trigger.focus()
    key('Tab', true)
    expect(document.activeElement).toBe(wrapper.get('[data-testid="last"]').element)
  })

  it('does not steal focus with a stale queued open after an immediate close', async () => {
    const trigger = opener()
    const { wrapper } = await dialog(false)
    await wrapper.setProps({ visible: true })
    await wrapper.setProps({ visible: false })
    await nextTick()
    await flushPromises()
    expect(document.activeElement).toBe(trigger)
  })
})

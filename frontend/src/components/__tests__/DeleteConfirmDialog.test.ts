import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import DeleteConfirmDialog from '../DeleteConfirmDialog.vue'

describe('DeleteConfirmDialog', () => {
  let wrapper: ReturnType<typeof mount> | null = null

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    // Clean up any teleported content
    document.body.innerHTML = ''
  })

  describe('Rendering when visible is false', () => {
    it('renders nothing when visible is false', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: false },
        attachTo: document.body
      })
      expect(document.body.querySelector('.delete-confirm-backdrop')).toBeNull()
      expect(document.body.querySelector('[role="dialog"]')).toBeNull()
    })
  })

  describe('Rendering when visible is true', () => {
    it('renders the dialog with correct structure, role, and aria attributes', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除此项？' },
        attachTo: document.body
      })
      const dialog = document.body.querySelector('[role="dialog"]')
      expect(dialog).not.toBeNull()
      expect(dialog?.getAttribute('aria-modal')).toBe('true')
      expect(dialog?.getAttribute('aria-labelledby')).toBeTruthy()

      // Verify the aria-labelledby points to the title element
      const labelledById = dialog?.getAttribute('aria-labelledby')
      const titleEl = document.body.querySelector(`#${labelledById}`)
      expect(titleEl).not.toBeNull()
      expect(titleEl?.tagName.toLowerCase()).toBe('h2')
    })

    it('renders the backdrop overlay', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除此项？' },
        attachTo: document.body
      })
      expect(document.body.querySelector('.delete-confirm-backdrop__overlay')).not.toBeNull()
    })

    it('renders the dialog section with cancel and confirm buttons', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除此项？' },
        attachTo: document.body
      })
      const cancelBtn = document.body.querySelector('.btn--ghost')
      const confirmBtn = document.body.querySelector('.btn--danger')
      const closeBtn = document.body.querySelector('.close-btn')
      expect(closeBtn).not.toBeNull()
      expect(cancelBtn?.textContent?.trim()).toBe('取消')
      expect(confirmBtn?.textContent?.trim()).toContain('确定')
    })
  })

  describe('Default title', () => {
    it('displays default title "删除确认" when title prop is not provided', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除此项？' },
        attachTo: document.body
      })
      const titleEl = document.body.querySelector('.delete-confirm-dialog__title')
      expect(titleEl?.textContent).toBe('删除确认')
    })
  })

  describe('Custom title and message display', () => {
    it('displays custom title when provided', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, title: '自定义标题', message: '自定义消息' },
        attachTo: document.body
      })
      const titleEl = document.body.querySelector('.delete-confirm-dialog__title')
      expect(titleEl?.textContent).toBe('自定义标题')
    })

    it('displays custom message when provided', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, title: '删除确认', message: '确认删除「产品A」？' },
        attachTo: document.body
      })
      const messageEl = document.body.querySelector('.delete-confirm-dialog__message')
      expect(messageEl?.textContent).toBe('确认删除「产品A」？')
    })
  })

  describe('Confirm button emits confirm event', () => {
    it('emits confirm event when confirm button is clicked', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？' },
        attachTo: document.body
      })
      const confirmBtn = document.body.querySelector('.btn--danger') as HTMLButtonElement
      confirmBtn.click()
      await nextTick()
      expect(wrapper.emitted('confirm')).toBeTruthy()
      expect(wrapper.emitted('confirm')!.length).toBe(1)
    })
  })

  describe('Cancel button emits update:visible with false', () => {
    it('emits update:visible with false when cancel button is clicked', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？' },
        attachTo: document.body
      })
      const cancelBtn = document.body.querySelector('.btn--ghost') as HTMLButtonElement
      cancelBtn.click()
      await nextTick()
      expect(wrapper.emitted('update:visible')).toBeTruthy()
      expect(wrapper.emitted('update:visible')![0]).toEqual([false])
    })
  })

  describe('Backdrop click emits update:visible with false', () => {
    it('emits update:visible with false when backdrop overlay is clicked', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？' },
        attachTo: document.body
      })
      const overlay = document.body.querySelector('.delete-confirm-backdrop__overlay') as HTMLElement
      overlay.click()
      await nextTick()
      expect(wrapper.emitted('update:visible')).toBeTruthy()
      expect(wrapper.emitted('update:visible')![0]).toEqual([false])
    })
  })

  describe('Escape key emits update:visible with false', () => {
    it('emits update:visible with false when Escape key is pressed', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？' },
        attachTo: document.body
      })
      const event = new KeyboardEvent('keydown', { key: 'Escape' })
      document.dispatchEvent(event)
      await nextTick()
      expect(wrapper.emitted('update:visible')).toBeTruthy()
      expect(wrapper.emitted('update:visible')![0]).toEqual([false])
    })
  })

  describe('Loading state disables all interactions', () => {
    it('disables confirm button when loading is true', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？', loading: true },
        attachTo: document.body
      })
      const confirmBtn = document.body.querySelector('.btn--danger') as HTMLButtonElement
      expect(confirmBtn.disabled).toBe(true)
    })

    it('disables cancel button when loading is true', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？', loading: true },
        attachTo: document.body
      })
      const cancelBtn = document.body.querySelector('.btn--ghost') as HTMLButtonElement
      expect(cancelBtn.disabled).toBe(true)
    })

    it('does not emit confirm when confirm button is clicked during loading', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？', loading: true },
        attachTo: document.body
      })
      const confirmBtn = document.body.querySelector('.btn--danger') as HTMLButtonElement
      confirmBtn.click()
      await nextTick()
      expect(wrapper.emitted('confirm')).toBeFalsy()
    })

    it('does not emit update:visible when cancel button is clicked during loading', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？', loading: true },
        attachTo: document.body
      })
      const cancelBtn = document.body.querySelector('.btn--ghost') as HTMLButtonElement
      cancelBtn.click()
      await nextTick()
      expect(wrapper.emitted('update:visible')).toBeFalsy()
    })

    it('does not emit update:visible when backdrop is clicked during loading', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？', loading: true },
        attachTo: document.body
      })
      const overlay = document.body.querySelector('.delete-confirm-backdrop__overlay') as HTMLElement
      overlay.click()
      await nextTick()
      expect(wrapper.emitted('update:visible')).toBeFalsy()
    })

    it('does not emit update:visible when Escape key is pressed during loading', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？', loading: true },
        attachTo: document.body
      })
      const event = new KeyboardEvent('keydown', { key: 'Escape' })
      document.dispatchEvent(event)
      await nextTick()
      expect(wrapper.emitted('update:visible')).toBeFalsy()
    })

    it('shows spinner on confirm button when loading is true', () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: true, message: '确认删除？', loading: true },
        attachTo: document.body
      })
      const spinner = document.body.querySelector('.spinner')
      expect(spinner).not.toBeNull()
    })
  })

  describe('Focus management', () => {
    it('moves focus to cancel button when dialog opens', async () => {
      wrapper = mount(DeleteConfirmDialog, {
        props: { visible: false, message: '确认删除？' },
        attachTo: document.body
      })
      await wrapper.setProps({ visible: true })
      await nextTick()
      // Allow the nextTick inside the watcher to complete
      await nextTick()
      const cancelBtn = document.body.querySelector('.btn--ghost') as HTMLButtonElement
      expect(document.activeElement).toBe(cancelBtn)
    })
  })
})

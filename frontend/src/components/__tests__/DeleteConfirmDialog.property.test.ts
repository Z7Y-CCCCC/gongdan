import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import fc from 'fast-check'
import DeleteConfirmDialog from '../DeleteConfirmDialog.vue'

describe('DeleteConfirmDialog - Property 1: Visibility toggle correctness', () => {
  /**
   * Property 1: Visibility toggle correctness
   *
   * For any boolean value of `visible`, the DeleteConfirmDialog component
   * renders modal content when true and renders nothing when false.
   *
   * **Validates: Requirements 1.2**
   */
  it('renders dialog content when visible=true and nothing when visible=false', () => {
    fc.assert(
      fc.property(fc.boolean(), (visible) => {
        const wrapper = mount(DeleteConfirmDialog, {
          props: { visible },
          global: {
            stubs: {
              Teleport: true,
              Transition: false
            }
          }
        })

        if (visible) {
          expect(wrapper.find('.delete-confirm-dialog').exists()).toBe(true)
        } else {
          expect(wrapper.find('.delete-confirm-dialog').exists()).toBe(false)
        }

        wrapper.unmount()
      }),
      { numRuns: 100 }
    )
  })
})

describe('DeleteConfirmDialog - Property 2: Props rendering round-trip', () => {
  /**
   * Property 2: Props rendering round-trip
   *
   * For any valid title string (1-50 characters, non-empty) and any valid message
   * string (1-200 characters), when passed as props to a visible DeleteConfirmDialog,
   * the rendered output SHALL contain the exact title text in the heading element
   * and the exact message text in the body paragraph.
   *
   * **Validates: Requirements 1.3, 1.4**
   */
  it('rendered heading contains exact title and message paragraph contains exact message', () => {
    // Generate strings with at least one non-whitespace character and no
    // leading/trailing whitespace, since DOM text rendering normalizes whitespace.
    const titleArb = fc
      .string({ minLength: 1, maxLength: 50 })
      .map((s) => s.trim())
      .filter((s) => s.length > 0 && s.length <= 50)
    const messageArb = fc
      .string({ minLength: 1, maxLength: 200 })
      .map((s) => s.trim())
      .filter((s) => s.length > 0 && s.length <= 200)

    fc.assert(
      fc.property(titleArb, messageArb, (title, message) => {
        const wrapper = mount(DeleteConfirmDialog, {
          props: {
            visible: true,
            title,
            message
          },
          global: {
            stubs: {
              Teleport: true,
              Transition: false
            }
          }
        })

        const headingEl = wrapper.find('.delete-confirm-dialog__title')
        expect(headingEl.exists()).toBe(true)
        expect(headingEl.text()).toBe(title)

        const messageEl = wrapper.find('.delete-confirm-dialog__message')
        expect(messageEl.exists()).toBe(true)
        expect(messageEl.text()).toBe(message)

        wrapper.unmount()
      }),
      { numRuns: 100 }
    )
  })
})

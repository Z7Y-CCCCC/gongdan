import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { useDeleteConfirm } from '../useDeleteConfirm'

/**
 * Property 3: Composable confirm state setup
 *
 * For any valid DeleteConfirmOptions object (with a non-empty message and optional title),
 * calling `confirm(options)` SHALL set `visible` to `true`, `title` to the provided title
 * (or "删除确认" if omitted), and `message` to the provided message, and SHALL return a
 * Promise that resolves to `true` when `handleConfirm()` is called or `false` when
 * `handleCancel()` is called.
 *
 * **Validates: Requirements 9.2, 9.3**
 */
describe('useDeleteConfirm - Property 3: Composable confirm state setup', () => {
  const messageArb = fc.string({ minLength: 1, maxLength: 200 })
  const titleArb = fc.option(fc.string({ minLength: 1, maxLength: 50 }), { nil: undefined })

  it('confirm(options) sets visible, title, and message correctly for any valid options', () => {
    fc.assert(
      fc.property(messageArb, titleArb, (message, title) => {
        const { visible, title: titleRef, message: messageRef, confirm } = useDeleteConfirm()

        const options = title !== undefined ? { message, title } : { message }
        confirm(options)

        expect(visible.value).toBe(true)
        expect(titleRef.value).toBe(title ?? '删除确认')
        expect(messageRef.value).toBe(message)
      }),
      { numRuns: 100 }
    )
  })

  it('promise resolves true when handleConfirm() is called', async () => {
    await fc.assert(
      fc.asyncProperty(messageArb, titleArb, async (message, title) => {
        const { confirm, handleConfirm } = useDeleteConfirm()

        const options = title !== undefined ? { message, title } : { message }
        const promise = confirm(options)

        handleConfirm()

        const result = await promise
        expect(result).toBe(true)
      }),
      { numRuns: 100 }
    )
  })

  it('promise resolves false when handleCancel() is called', async () => {
    await fc.assert(
      fc.asyncProperty(messageArb, titleArb, async (message, title) => {
        const { confirm, handleCancel } = useDeleteConfirm()

        const options = title !== undefined ? { message, title } : { message }
        const promise = confirm(options)

        handleCancel()

        const result = await promise
        expect(result).toBe(false)
      }),
      { numRuns: 100 }
    )
  })
})

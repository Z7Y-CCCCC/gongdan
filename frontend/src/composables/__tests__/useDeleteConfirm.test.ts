import { describe, it, expect } from 'vitest'
import { useDeleteConfirm } from '../useDeleteConfirm'

describe('useDeleteConfirm', () => {
  it('confirm() sets visible, title, and message correctly with default title', () => {
    const { visible, title, message, confirm } = useDeleteConfirm()

    confirm({ message: 'test' })

    expect(visible.value).toBe(true)
    expect(title.value).toBe('删除确认')
    expect(message.value).toBe('test')
  })

  it('confirm() sets custom title when provided', () => {
    const { title, confirm } = useDeleteConfirm()

    confirm({ message: 'test', title: '自定义' })

    expect(title.value).toBe('自定义')
  })

  it('promise resolves true on handleConfirm', async () => {
    const { confirm, handleConfirm } = useDeleteConfirm()

    const promise = confirm({ message: 'test' })
    handleConfirm()

    const result = await promise
    expect(result).toBe(true)
  })

  it('promise resolves false on handleCancel and sets visible to false', async () => {
    const { visible, confirm, handleCancel } = useDeleteConfirm()

    const promise = confirm({ message: 'test' })
    expect(visible.value).toBe(true)

    handleCancel()

    const result = await promise
    expect(result).toBe(false)
    expect(visible.value).toBe(false)
  })

  it('concurrent calls resolve previous promise with false', async () => {
    const { visible, title, message, confirm } = useDeleteConfirm()

    const firstPromise = confirm({ message: 'first' })
    const secondPromise = confirm({ message: 'second' })

    // First promise should resolve false immediately
    const firstResult = await firstPromise
    expect(firstResult).toBe(false)

    // Second dialog state should be applied
    expect(visible.value).toBe(true)
    expect(message.value).toBe('second')
    expect(title.value).toBe('删除确认')

    // Second promise is still pending (not yet resolved)
    // We don't await it here to avoid hanging
    void secondPromise
  })

  it('default title is "删除确认" when title is not provided', () => {
    const { title, confirm } = useDeleteConfirm()

    confirm({ message: '确认删除此项？' })

    expect(title.value).toBe('删除确认')
  })
})

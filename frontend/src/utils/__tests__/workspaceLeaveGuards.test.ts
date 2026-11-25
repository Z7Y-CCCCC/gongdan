import { describe, expect, it, vi } from 'vitest'
import { createWorkspaceLeaveGuards } from '../workspaceLeaveGuards'

describe('per-session workspace departure guards', () => {
  it('asks only dirty selected workspaces and deduplicates close-all targets', () => {
    const registry = createWorkspaceLeaveGuards()
    const dirty = vi.fn(() => false), clean = vi.fn(() => false)
    registry.register('analytics', { isDirty: () => true, confirmLeave: dirty })
    registry.register('home', { isDirty: () => false, confirmLeave: clean })
    expect(registry.canLeave(['home'], 'close')).toBe(true)
    expect(registry.canLeave(['analytics', 'analytics'], 'close')).toBe(false)
    expect(dirty).toHaveBeenCalledExactlyOnceWith('close')
    expect(clean).not.toHaveBeenCalled()
  })

  it('does not let the disposer of an old page remove the replacement registration', () => {
    const registry = createWorkspaceLeaveGuards()
    const disposeOld = registry.register('analytics', { isDirty: () => false, confirmLeave: () => true })
    const disposeNew = registry.register('analytics', { isDirty: () => true, confirmLeave: () => false })
    disposeOld()
    expect(registry.hasUnsavedChanges()).toBe(true)
    disposeNew()
    expect(registry.hasUnsavedChanges()).toBe(false)
  })

  it('suppresses only an already accepted synchronous change and always restores the gate', () => {
    const registry = createWorkspaceLeaveGuards()
    const confirm = vi.fn(() => true)
    registry.register('analytics', { isDirty: () => true, confirmLeave: confirm })
    expect(registry.canLeave(undefined, 'close')).toBe(true)
    registry.withAcceptedChange(() => expect(registry.canLeave(undefined, 'navigate')).toBe(true))
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(() => registry.withAcceptedChange(() => { throw new Error('failed change') })).toThrow()
    registry.canLeave(undefined, 'navigate')
    expect(confirm).toHaveBeenCalledTimes(2)
  })

  it('clears every old-session registration before a new login mounts its pages', () => {
    const registry = createWorkspaceLeaveGuards()
    registry.register('analytics', { isDirty: () => true, confirmLeave: () => false })
    registry.clear()
    expect(registry.hasUnsavedChanges()).toBe(false)
    expect(registry.canLeave(undefined, 'logout')).toBe(true)
  })

  it('does not silently discard when the native draft state cannot be inspected', () => {
    const registry = createWorkspaceLeaveGuards()
    const confirm = vi.fn(() => false)
    registry.register('analytics', { isDirty: () => { throw new Error('unavailable frame') }, confirmLeave: confirm })
    expect(registry.hasUnsavedChanges()).toBe(true)
    expect(registry.canLeave(undefined, 'navigate')).toBe(false)
    expect(confirm).toHaveBeenCalledExactlyOnceWith('navigate')
  })
})

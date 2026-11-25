import type { InjectionKey } from 'vue'

export type WorkspaceLeaveReason = 'navigate' | 'close' | 'logout'
export interface WorkspaceLeaveGuard {
  isDirty: () => boolean
  confirmLeave: (reason: WorkspaceLeaveReason) => boolean
}

/** Per-shell registry: cached pages never share departure state with another login. */
export function createWorkspaceLeaveGuards() {
  const guards = new Map<string, WorkspaceLeaveGuard>()
  let acceptedChangeDepth = 0
  const selected = (ids?: readonly string[]) => ids ? [...new Set(ids)].map(id => guards.get(id)).filter(Boolean) as WorkspaceLeaveGuard[] : [...guards.values()]
  const isDirty = (guard: WorkspaceLeaveGuard) => {
    try { return guard.isDirty() } catch { return true }
  }
  return {
    register(id: string, guard: WorkspaceLeaveGuard) {
      guards.set(id, guard)
      return () => { if (guards.get(id) === guard) guards.delete(id) }
    },
    hasUnsavedChanges(ids?: readonly string[]) { return selected(ids).some(isDirty) },
    canLeave(ids: readonly string[] | undefined, reason: WorkspaceLeaveReason) {
      if (acceptedChangeDepth) return true
      return selected(ids).every(guard => {
        if (!isDirty(guard)) return true
        try { return guard.confirmLeave(reason) } catch { return false }
      })
    },
    withAcceptedChange<T>(change: () => T): T {
      acceptedChangeDepth++
      try { return change() } finally { acceptedChangeDepth-- }
    },
    clear() { guards.clear() }
  }
}

export type WorkspaceLeaveGuards = ReturnType<typeof createWorkspaceLeaveGuards>
export const workspaceLeaveGuardsKey: InjectionKey<WorkspaceLeaveGuards> = Symbol('workspace-leave-guards')

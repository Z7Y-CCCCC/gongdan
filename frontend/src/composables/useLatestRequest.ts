import { onScopeDispose } from 'vue'

/** Only the newest request in a component may update its data or loading state. */
export function useLatestRequest() {
  let sequence = 0
  onScopeDispose(() => { sequence += 1 })
  return function beginRequest() {
    const requestId = ++sequence
    return () => requestId === sequence
  }
}

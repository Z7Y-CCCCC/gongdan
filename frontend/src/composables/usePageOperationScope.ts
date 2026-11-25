import { onActivated, onBeforeUnmount, onDeactivated, watch } from 'vue'

/** A page's mutations must not continue under a newer login or after leaving it. */
export function usePageOperationScope(getToken: () => string, onInvalidate: () => void) {
  let generation = 0, active = true, disposed = false
  const invalidate = () => { generation++; onInvalidate() }
  watch(getToken, invalidate, { flush: 'sync' })
  onActivated(() => { active = true })
  onDeactivated(() => { active = false; invalidate() })
  onBeforeUnmount(() => { disposed = true; active = false; invalidate() })
  return {
    capture() {
      const token = getToken(), capturedGeneration = generation
      return {
        token,
        isCurrent: () => Boolean(token) && !disposed && active
          && getToken() === token && generation === capturedGeneration
      }
    }
  }
}

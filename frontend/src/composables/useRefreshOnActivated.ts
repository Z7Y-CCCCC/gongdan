import { onActivated } from 'vue'

/** onMounted handles the initial load; refresh again when a cached page reopens. */
export function useRefreshOnActivated(refresh: () => unknown) {
  let firstActivation = true
  onActivated(() => {
    if (!firstActivation) void refresh()
    firstActivation = false
  })
}

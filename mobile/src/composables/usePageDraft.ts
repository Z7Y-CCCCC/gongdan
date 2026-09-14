import { onBeforeUnmount, onMounted, watch, type WatchSource } from 'vue'
import { onHide } from '@dcloudio/uni-app'

type DraftOptions<T> = {
  key: string | (() => string)
  source: WatchSource<T>
  restore: (draft: T) => void
  debounceMs?: number
  scope?: 'user' | 'device'
}

function resolveKey(key: DraftOptions<unknown>['key']) {
  return typeof key === 'function' ? key() : key
}

export function usePageDraft<T>({ key, source, restore, debounceMs = 180, scope = 'user' }: DraftOptions<T>) {
  let hydrated = false
  let timer: ReturnType<typeof setTimeout> | null = null
  let storageKey = ''
  let sessionToken = ''
  let pending: T | undefined

  function flush() {
    if (timer) clearTimeout(timer)
    timer = null
    if (pending === undefined) return
    try {
      if (storageKey && (scope === 'device' || uni.getStorageSync('token') === sessionToken)) {
        uni.setStorageSync(storageKey, pending)
      }
    } catch {
      // Storage may be full or unavailable; do not crash form navigation.
    }
    pending = undefined
  }

  onMounted(() => {
    try {
      const baseKey = resolveKey(key)
      const user = uni.getStorageSync('user')
      sessionToken = uni.getStorageSync('token') || ''
      storageKey = scope === 'device' ? baseKey
        : user?.id && baseKey ? `${baseKey}:user:${user.factoryId || 'global'}:${user.id}` : ''
      if (storageKey) {
        const saved = uni.getStorageSync(storageKey)
        if (saved && typeof saved === 'object') restore(saved as T)
      }
    } catch { /* A corrupt draft must not prevent the page from opening. */ }
    hydrated = true
  })

  watch(source, (value) => {
    if (!hydrated) return
    if (timer) clearTimeout(timer)
    // Snapshot now rather than retaining mutable form references across pages.
    try { pending = JSON.parse(JSON.stringify(value)) } catch { pending = undefined }
    timer = setTimeout(flush, debounceMs)
  }, { deep: true, flush: 'sync' })

  onHide(flush)
  onBeforeUnmount(() => {
    flush()
    hydrated = false
  })

  return {
    clearDraft() {
      if (timer) clearTimeout(timer)
      timer = null
      pending = undefined
      try { if (storageKey) uni.removeStorageSync(storageKey) } catch { /* best effort */ }
    }
  }
}

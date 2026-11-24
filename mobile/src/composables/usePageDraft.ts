import { onBeforeUnmount, onMounted, watch, type WatchSource } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'

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

function snapshot<T>(value: T): T | undefined {
  try { return JSON.parse(JSON.stringify(value)) } catch { return undefined }
}

export function usePageDraft<T>({ key, source, restore, debounceMs = 180, scope = 'user' }: DraftOptions<T>) {
  let hydrated = false
  let timer: ReturnType<typeof setTimeout> | null = null
  let storageKey = ''
  let sessionToken = ''
  let pending: T | undefined
  // Account-bound defaults must be captured before a page's mount hook fills
  // reporter/account fields. Never reuse the old user's live form as defaults.
  const initialDraft = snapshot(typeof source === 'function' ? source() : source.value)

  function currentBinding() {
    const baseKey = resolveKey(key)
    if (scope === 'device') return { key: baseKey, token: '' }
    const user = uni.getStorageSync('user')
    const currentToken = uni.getStorageSync('token') || ''
    const userKey = currentToken && user?.id && baseKey
      ? `${baseKey}:user:${user.factoryId || 'global'}:${user.id}` : ''
    return { key: userKey, token: currentToken }
  }

  function matchesBinding() {
    try {
      const current = currentBinding()
      return Boolean(storageKey) && current.key === storageKey && current.token === sessionToken
    } catch { return false }
  }

  function discardPending() {
    if (timer) clearTimeout(timer)
    timer = null
    pending = undefined
  }

  function flush() {
    if (timer) clearTimeout(timer)
    timer = null
    if (pending === undefined) return
    try {
      if (matchesBinding()) {
        uni.setStorageSync(storageKey, pending)
      }
    } catch {
      // Storage may be full or unavailable; do not crash form navigation.
    }
    pending = undefined
  }

  function hydrate(initial = false) {
    try {
      const current = currentBinding()
      if (!initial && current.key === storageKey && current.token === sessionToken) return
      hydrated = false
      discardPending()
      storageKey = current.key
      sessionToken = current.token
      if (!initial && initialDraft !== undefined) restore(snapshot(initialDraft) as T)
      if (storageKey) {
        const saved = uni.getStorageSync(storageKey)
        if (saved && typeof saved === 'object') restore(saved as T)
      }
    } catch { /* A corrupt draft must not prevent the page from opening. */ }
    finally { hydrated = true }
  }

  onMounted(() => hydrate(true))
  onShow(() => { if (hydrated) hydrate() })

  watch(source, (value) => {
    if (!hydrated || !matchesBinding()) {
      discardPending()
      return
    }
    if (timer) clearTimeout(timer)
    // Snapshot now rather than retaining mutable form references across pages.
    pending = snapshot(value)
    timer = setTimeout(flush, debounceMs)
  }, { deep: true, flush: 'sync' })

  onHide(flush)
  onBeforeUnmount(() => {
    flush()
    hydrated = false
  })

  return {
    clearDraft() {
      discardPending()
      try { if (matchesBinding()) uni.removeStorageSync(storageKey) } catch { /* best effort */ }
    }
  }
}

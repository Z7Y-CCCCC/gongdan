import { onMounted, watch, type WatchSource } from 'vue'

type DraftOptions<T> = {
  key: string | (() => string)
  source: WatchSource<T>
  restore: (draft: T) => void
  debounceMs?: number
}

function resolveKey(key: DraftOptions<unknown>['key']) {
  return typeof key === 'function' ? key() : key
}

export function usePageDraft<T>({ key, source, restore, debounceMs = 180 }: DraftOptions<T>) {
  let hydrated = false
  let timer: ReturnType<typeof setTimeout> | null = null

  onMounted(() => {
    const storageKey = resolveKey(key)
    if (storageKey) {
      const saved = uni.getStorageSync(storageKey)
      if (saved && typeof saved === 'object') {
        restore(saved as T)
      }
    }
    hydrated = true
  })

  watch(source, (value) => {
    if (!hydrated) return
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      const storageKey = resolveKey(key)
      if (storageKey) uni.setStorageSync(storageKey, value)
    }, debounceMs)
  }, { deep: true })

  return {
    clearDraft() {
      if (timer) clearTimeout(timer)
      timer = null
      const storageKey = resolveKey(key)
      if (storageKey) uni.removeStorageSync(storageKey)
    }
  }
}

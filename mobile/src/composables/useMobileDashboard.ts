import { onBeforeUnmount, ref } from 'vue'
import { onHide } from '@dcloudio/uni-app'
import { ensureLogin, fetchDashboard } from '../services/api'
import type { MobileDashboard } from '../types/api'
import { localDateKey } from '../utils/local-date'
import { mobileSessionKey } from '../utils/session-key'
import { validateMobileDashboard } from '../utils/dashboard'

export function useMobileDashboard() {
  const dashboard = ref<MobileDashboard | null>(null)
  const loading = ref(false)
  const errorMessage = ref('')
  const day = ref(localDateKey())
  let sequence = 0
  let activeSession = ''

  async function load() {
    const session = mobileSessionKey()
    const currentDay = localDateKey()
    if (loading.value && activeSession === session && day.value === currentDay) return
    if (!ensureLogin()) {
      sequence += 1
      dashboard.value = null
      loading.value = false
      errorMessage.value = ''
      return
    }
    const requestId = ++sequence
    activeSession = session
    loading.value = true
    errorMessage.value = ''
    dashboard.value = null
    day.value = currentDay
    try {
      const result = await fetchDashboard(day.value)
      if (requestId !== sequence || mobileSessionKey() !== session) return
      dashboard.value = validateMobileDashboard(result)
    } catch (error: any) {
      if (requestId !== sequence || mobileSessionKey() !== session) return
      errorMessage.value = error?.message || '统计加载失败，请重试'
      uni.showToast({ title: errorMessage.value, icon: 'none' })
    } finally {
      if (requestId === sequence) {
        loading.value = false
        if (mobileSessionKey() !== session) {
          dashboard.value = null
          errorMessage.value = '登录状态已变更，请刷新'
        }
      }
    }
  }

  function invalidate() {
    sequence += 1
    loading.value = false
    dashboard.value = null
  }
  onHide(invalidate)
  onBeforeUnmount(invalidate)
  return { dashboard, loading, errorMessage, day, load }
}

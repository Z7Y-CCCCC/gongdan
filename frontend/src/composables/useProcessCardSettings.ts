/**
 * 工序任务卡片设置 composable
 *
 * 加载并管理用户的工序卡片偏好设置。
 * - 使用 GET /api/user/preferences/home_dashboard.process_cards 加载偏好
 * - 偏好不存在时（404），按工序创建时间升序取前 4 个，分配蓝灰黄绿预设颜色
 * - 偏好中的 processId 在工序列表中不存在时跳过
 * - 不主动写回偏好（懒持久化），仅在用户保存设置时 PUT
 *
 * Requirements: 6.6, 6.7, 6.8
 */
import { ref } from 'vue'
import {
  ApiError,
  fetchUserPreference,
  setUserPreference,
  type ProcessInfo
} from '../api'

const PREFERENCE_KEY = 'home_dashboard.process_cards'

/** 预设颜色：蓝、灰、黄、绿 */
const PRESET_COLORS = ['#3B82F6', '#9CA3AF', '#FACC15', '#10B981']

export interface ProcessCardSettingItem {
  processId: number
  color: string
  visible: boolean
}

export interface ProcessCardSettings {
  version: number
  items: ProcessCardSettingItem[]
}

export function useProcessCardSettings() {
  const settings = ref<ProcessCardSettings>({ version: 1, items: [] })
  const loading = ref(false)

  /**
   * 加载用户偏好设置
   * @param token - 用户认证 token
   * @param processes - 当前可用的工序列表（用于验证 processId 和生成默认值）
   */
  async function loadSettings(token: string, processes: ProcessInfo[]) {
    loading.value = true
    try {
      const pref = await fetchUserPreference(token, PREFERENCE_KEY)
      const parsed = JSON.parse(pref.value) as ProcessCardSettings

      // 构建当前工序 ID 集合，用于过滤不存在的 processId
      const validProcessIds = new Set(processes.map(p => p.id))

      // 过滤掉工序列表中不存在的 processId
      const filteredItems = parsed.items.filter(
        item => validProcessIds.has(item.processId)
      )

      settings.value = {
        version: parsed.version || 1,
        items: filteredItems
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        // 偏好不存在，生成默认设置
        settings.value = generateDefaultSettings(processes)
      } else {
        // 其他错误重新抛出，由调用方处理
        throw error
      }
    } finally {
      loading.value = false
    }
  }

  /**
   * 保存设置到后端
   * @param token - 用户认证 token
   * @param newSettings - 要保存的新设置
   */
  async function saveSettings(token: string, newSettings: ProcessCardSettings) {
    const value = JSON.stringify(newSettings)
    await setUserPreference(token, PREFERENCE_KEY, value)
    settings.value = newSettings
  }

  return {
    settings,
    loading,
    loadSettings,
    saveSettings
  }
}

/**
 * 偏好不存在时生成默认设置：
 * 按工序创建时间（ID）升序取前 4 个，分配蓝灰黄绿预设颜色，全部 visible=true
 */
function generateDefaultSettings(processes: ProcessInfo[]): ProcessCardSettings {
  // 按 ID 升序排列（ID 递增即创建时间升序）
  const sorted = [...processes].sort((a, b) => a.id - b.id)
  const top4 = sorted.slice(0, 4)

  const items: ProcessCardSettingItem[] = top4.map((process, index) => ({
    processId: process.id,
    color: PRESET_COLORS[index % PRESET_COLORS.length],
    visible: true
  }))

  return {
    version: 1,
    items
  }
}

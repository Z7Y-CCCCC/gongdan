/**
 * 主页仪表盘全局错误处理 composable
 *
 * 处理逻辑：
 * - 任一接口返回 401 时调用 useAuth().logout() 清除 token 并跳转登录页
 * - 任一接口返回 403 时隐藏所有数据区块，尝试显示"无权限访问主页"提示
 *
 * Requirements: 12.1, 12.2
 */
import { ref } from 'vue'
import { ApiError } from '../api'
import { useAuth } from './useAuth'

export function useHomeDashboardErrorHandler() {
  const { logout } = useAuth()

  /** 当为 true 时，所有数据区块应被隐藏 */
  const forbidden = ref(false)

  /** 403 时的提示文字 */
  const forbiddenMessage = ref('')

  /**
   * 处理 API 调用中的错误。
   * 如果是 401，清除 token 并跳转登录页。
   * 如果是 403，设置 forbidden 标志隐藏所有数据区块。
   *
   * @param error - 捕获到的错误对象
   * @returns true 表示错误已被全局处理（401/403），调用方无需再处理；
   *          false 表示非 401/403 错误，调用方应自行处理
   */
  function handleApiError(error: unknown): boolean {
    if (!(error instanceof ApiError)) {
      return false
    }

    if (error.status === 401) {
      // 清除本地登录态并跳转登录页
      // useAuth().logout() 清除 localStorage 中的 token 和 user
      logout()
      // 强制刷新页面以确保 App.vue 重新从 localStorage 读取状态（为空），显示登录页
      window.location.reload()
      return true
    }

    if (error.status === 403) {
      // 隐藏所有数据区块为强制行为
      forbidden.value = true
      // 尝试显示提示文字（提示文字不可显示时不影响数据区块已被隐藏的状态）
      try {
        forbiddenMessage.value = '无权限访问主页'
      } catch {
        // 提示文字设置失败时静默忽略
      }
      return true
    }

    return false
  }

  /**
   * 包装一个异步 API 调用，自动处理 401/403 错误。
   * 如果是 401/403，返回 null；否则重新抛出错误供调用方处理。
   *
   * @param apiCall - 返回 Promise 的 API 调用函数
   * @returns API 调用的结果，或在 401/403 时返回 null
   */
  async function withErrorHandling<T>(apiCall: () => Promise<T>): Promise<T | null> {
    try {
      return await apiCall()
    } catch (error) {
      if (handleApiError(error)) {
        return null
      }
      throw error
    }
  }

  return {
    forbidden,
    forbiddenMessage,
    handleApiError,
    withErrorHandling
  }
}

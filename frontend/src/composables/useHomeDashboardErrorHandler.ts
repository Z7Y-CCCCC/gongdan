/**
 * 主页仪表盘全局错误处理 composable
 *
 * 处理逻辑：
 * - 401 由 API 层按请求 token 统一清理会话；组件不重复退出或刷新页面
 * - 任一接口返回 403 时隐藏所有数据区块，尝试显示"无权限访问主页"提示
 *
 * Requirements: 12.1, 12.2
 */
import { getCurrentInstance, inject, provide, ref, type InjectionKey, type Ref } from 'vue'
import { ApiError } from '../api'

const dashboardErrorsKey: InjectionKey<{ forbidden: Ref<boolean>; forbiddenMessage: Ref<string> }> = Symbol('dashboard-errors')

export function useHomeDashboardErrorHandler() {
  const inherited = getCurrentInstance() ? inject(dashboardErrorsKey, null) : null
  const { forbidden, forbiddenMessage } = inherited || { forbidden: ref(false), forbiddenMessage: ref('') }
  if (!inherited && getCurrentInstance()) provide(dashboardErrorsKey, { forbidden, forbiddenMessage })

  /**
   * 处理 API 调用中的错误。
   * 如果是 401，API 层已按请求所属会话处理，无需再次退出或重载。
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

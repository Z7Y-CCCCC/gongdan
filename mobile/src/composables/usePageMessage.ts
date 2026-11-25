import { onBeforeUnmount, reactive } from 'vue'
import { onHide } from '@dcloudio/uni-app'

type Options = { title: string; content: string; confirmText?: string; cancelText?: string; showCancel?: boolean }

export function usePageMessage() {
  const message = reactive({ visible: false, title: '', content: '', confirmText: '关闭', cancelText: '' })
  let sequence = 0
  let decision: ((accepted: boolean) => void | Promise<void>) | undefined
  function decide(accepted: boolean, request = sequence) {
    if (request !== sequence) return
    sequence++
    message.visible = false
    const callback = decision
    decision = undefined
    return callback?.(accepted)
  }
  function open(options: Options, callback?: (accepted: boolean) => void | Promise<void>) {
    decide(false)
    const request = ++sequence
    decision = callback
    if (typeof window === 'undefined') {
      uni.showModal({ ...options, confirmText: options.confirmText || '关闭',
        success: result => decide(Boolean(result.confirm), request), fail: () => decide(false, request) })
    } else {
      Object.assign(message, { ...options, visible: true, confirmText: options.confirmText || '关闭',
        cancelText: options.showCancel ? options.cancelText || '取消' : '' })
    }
  }
  onHide(() => decide(false))
  onBeforeUnmount(() => decide(false))
  return { message, open, decide }
}

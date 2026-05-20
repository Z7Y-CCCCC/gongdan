import { ref } from 'vue'
import type { Ref } from 'vue'

export interface DeleteConfirmOptions {
  message: string
  title?: string
}

export interface UseDeleteConfirmReturn {
  visible: Ref<boolean>
  title: Ref<string>
  message: Ref<string>
  loading: Ref<boolean>
  confirm: (options: DeleteConfirmOptions) => Promise<boolean>
  handleConfirm: () => void
  handleCancel: () => void
}

export function useDeleteConfirm(): UseDeleteConfirmReturn {
  const visible = ref(false)
  const title = ref('删除确认')
  const message = ref('')
  const loading = ref(false)

  let pendingResolve: ((value: boolean) => void) | null = null

  function confirm(options: DeleteConfirmOptions): Promise<boolean> {
    // If called while previous dialog is visible, resolve previous promise with false
    if (pendingResolve) {
      pendingResolve(false)
      pendingResolve = null
    }

    title.value = options.title ?? '删除确认'
    message.value = options.message
    loading.value = false
    visible.value = true

    return new Promise<boolean>((resolve) => {
      pendingResolve = resolve
    })
  }

  function handleConfirm(): void {
    if (pendingResolve) {
      pendingResolve(true)
      pendingResolve = null
    }
  }

  function handleCancel(): void {
    if (pendingResolve) {
      pendingResolve(false)
      pendingResolve = null
    }
    visible.value = false
  }

  return {
    visible,
    title,
    message,
    loading,
    confirm,
    handleConfirm,
    handleCancel
  }
}

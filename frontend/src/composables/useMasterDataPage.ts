import { computed, onMounted, onScopeDispose, reactive, ref, shallowRef } from 'vue'
import { useDeleteConfirm } from './useDeleteConfirm'
import { useLatestRequest } from './useLatestRequest'
import { useRefreshOnActivated } from './useRefreshOnActivated'

interface ListQuery {
  code?: string
  name?: string
  page: number
  size: number
}

/** Shared list and mutation lifecycle for customer, supplier and warehouse settings. */
export function useMasterDataPage<T extends { id: number; name: string }>(options: {
  label: string
  list: (query: ListQuery) => Promise<{ items: T[]; total: number }>
  remove: (id: number) => Promise<void>
  notice: (message: string) => void
}) {
  const pageSize = 50
  const filter = reactive({ code: '', name: '' })
  const items = shallowRef<T[]>([])
  const total = ref(0)
  const page = ref(1)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
  const loading = ref(false)
  const loadError = ref('')
  const saving = ref(false)
  const deletion = useDeleteConfirm()
  const beginLoad = useLatestRequest()
  let attemptedPage = 1
  let appliedFilter: Pick<ListQuery, 'code' | 'name'> = {}
  let disposed = false

  async function load(targetPage = page.value) {
    if (disposed) return
    const current = beginLoad()
    const criteria = appliedFilter
    attemptedPage = targetPage
    loading.value = true
    loadError.value = ''
    try {
      for (;;) {
        const result = await options.list({ ...criteria, page: targetPage, size: pageSize })
        if (!current()) return
        const lastPage = Math.max(1, Math.ceil(result.total / pageSize))
        if (result.total > 0 && targetPage > lastPage) {
          // Deleting the final row on a page can move the last page back.
          targetPage = lastPage
          attemptedPage = targetPage
          continue
        }
        items.value = result.items
        total.value = result.total
        page.value = result.total === 0 ? 1 : targetPage
        return
      }
    } catch (e) {
      if (!current()) return
      loadError.value = e instanceof Error ? e.message : `${options.label}列表加载失败`
    } finally {
      if (current()) loading.value = false
    }
  }

  function search() {
    appliedFilter = {
      code: filter.code.trim() || undefined,
      name: filter.name.trim() || undefined
    }
    return load(1)
  }

  function reset() {
    filter.code = ''
    filter.name = ''
    return search()
  }

  function goPage(nextPage: number) {
    if (!loading.value && nextPage >= 1 && nextPage <= totalPages.value && nextPage !== page.value) {
      return load(nextPage)
    }
  }

  async function save(action: () => Promise<unknown>, editing: boolean, done: () => void) {
    if (saving.value || deletion.visible.value) return
    saving.value = true
    try {
      await action()
      if (disposed) return
      options.notice(`${options.label}${editing ? '已更新' : '已创建'}`)
      done()
      await load(editing ? page.value : 1)
    } catch (e) {
      if (!disposed) options.notice(e instanceof Error ? e.message : '保存失败')
    } finally {
      saving.value = false
    }
  }

  async function remove(item: T) {
    if (saving.value || deletion.visible.value) return
    const accepted = await deletion.confirm({ message: `确认删除${options.label}「${item.name}」？` })
    if (!accepted || disposed) return
    deletion.loading.value = true
    try {
      await options.remove(item.id)
      if (disposed) return
      options.notice(`${options.label}已删除`)
      await load()
    } catch (e) {
      if (!disposed) options.notice(e instanceof Error ? e.message : '删除失败')
    } finally {
      deletion.loading.value = false
      deletion.handleCancel()
    }
  }

  onMounted(() => { void load() })
  useRefreshOnActivated(() => load())
  onScopeDispose(() => {
    disposed = true
    deletion.handleCancel()
  })

  return {
    filter, items, total, page, pageSize, totalPages, loading, loadError, saving,
    deletion, search, reset, goPage, retry: () => load(attemptedPage), save, remove
  }
}

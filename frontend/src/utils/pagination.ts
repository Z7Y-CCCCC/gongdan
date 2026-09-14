export interface PageResult<T> {
  items: T[]
  total: number
}

/** Load every page for selectors and client-side aggregates, without truncating totals. */
export async function fetchAllPages<T>(
  fetchPage: (params: { page: number; size: number }) => Promise<PageResult<T>>,
  size = 500,
  isCurrent: () => boolean = () => true
): Promise<PageResult<T>> {
  if (!Number.isInteger(size) || size < 1) throw new Error('分页大小必须为正整数')
  const items: T[] = []
  let total = 0
  for (let page = 1; ; page += 1) {
    if (page > 200) throw new Error('分页请求超过安全上限，请缩小筛选范围后重试')
    if (!isCurrent()) throw new Error('请求已失效')
    const result = await fetchPage({ page, size })
    if (!isCurrent()) throw new Error('请求已失效')
    if (!Array.isArray(result.items) || !Number.isSafeInteger(result.total) || result.total < 0) {
      throw new Error('分页数据格式不正确')
    }
    if (result.total > 10000 || items.length + result.items.length > 10000) {
      throw new Error('匹配记录超过 10000 条，请缩小筛选范围或分批查询')
    }
    items.push(...result.items)
    total = result.total
    if (items.length >= total) return { items, total }
    if (result.items.length === 0) throw new Error('分页数据不完整，请刷新后重试')
  }
}

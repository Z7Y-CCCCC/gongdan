import { describe, expect, it, vi } from 'vitest'
import { fetchAllPages } from '../pagination'

describe('fetchAllPages', () => {
  it('loads all pages even when the server caps the requested page size', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce({ items: [1, 2], total: 5 })
      .mockResolvedValueOnce({ items: [3, 4], total: 5 })
      .mockResolvedValueOnce({ items: [5], total: 5 })
    expect(await fetchAllPages(fetchPage, 500)).toEqual({ items: [1, 2, 3, 4, 5], total: 5 })
    expect(fetchPage.mock.calls.map(([params]) => params.page)).toEqual([1, 2, 3])
  })

  it('accepts an empty result without requesting additional pages', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ items: [], total: 0 })
    expect(await fetchAllPages(fetchPage)).toEqual({ items: [], total: 0 })
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('rejects incomplete pages instead of silently returning incorrect totals', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce({ items: [1], total: 3 })
      .mockResolvedValueOnce({ items: [], total: 3 })
    await expect(fetchAllPages(fetchPage)).rejects.toThrow('分页数据不完整')
  })

  it('stops superseded requests before loading more pages', async () => {
    let current = true
    const fetchPage = vi.fn(async () => {
      current = false
      return { items: [1], total: 3 }
    })
    await expect(fetchAllPages(fetchPage, 500, () => current)).rejects.toThrow('请求已失效')
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('fails explicitly instead of returning partial results beyond the safe client-side limit', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ items: [1], total: 10001 })
    await expect(fetchAllPages(fetchPage)).rejects.toThrow('匹配记录超过 10000 条')
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })
})

import { afterEach, expect, it, vi } from 'vitest'
import { sendAdvancedMessageTest } from '../api'

afterEach(() => vi.unstubAllGlobals())
it('posts a test dispatch to the backend instead of editing the record to simulate delivery', async () => {
  const reply = { success: true, message: '已发送', httpStatus: 200, record: { id: 42, moduleName: 'MESSAGE', status: 'SENT' } }
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(reply), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  vi.stubGlobal('fetch', fetch)
  expect(await sendAdvancedMessageTest('fixture-token', 42)).toEqual(reply)
  expect(fetch).toHaveBeenCalledTimes(1)
  const [url, options] = fetch.mock.calls[0]
  expect(url).toContain('/advanced-records/42/send-test')
  expect(options.method).toBe('POST')
  expect(new Headers(options.headers).get('Authorization')).toBe('Bearer fixture-token')
  expect(options.body).toBeUndefined()
})

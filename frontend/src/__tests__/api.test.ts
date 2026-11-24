import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

const user = {
  id: 1, username: 'admin', name: '管理员', role: 'ADMIN' as const,
  roleLabel: '管理员', virtualUser: false, enabled: true, createdAt: '2026-09-01'
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('API session and upload handling', () => {
  let api: typeof import('../api')
  let auth: ReturnType<typeof import('../composables/useAuth')['useAuth']>

  beforeAll(async () => {
    // Warm the modules once in a suite hook. A timed-out test must not leave a late
    // import running login/fetch setup against the next test's mocks.
    vi.stubEnv('VITE_API_BASE_URL', 'https://factory.example.test/backend/api/')
    api = await import('../api')
    auth = (await import('../composables/useAuth')).useAuth()
  })
  beforeEach(() => {
    auth.logout()
    localStorage.clear()
    document.getElementById('kicked-out-overlay')?.remove()
    vi.stubGlobal('fetch', vi.fn())
  })
  afterEach(() => {
    auth.logout()
    vi.unstubAllGlobals()
  })
  afterAll(() => { vi.unstubAllEnvs() })

  function createSession(token = 'current-token') {
    auth.persistSession({ token, user, isSuperAdmin: false })
    return auth
  }

  it('retrieves a single real work order using the read-only authenticated endpoint', async () => {
    createSession()
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { id: 42, code: 'WO-42' }))
    expect(await api.fetchWorkOrder('current-token', 42)).toMatchObject({ id: 42, code: 'WO-42' })
    const [rawUrl, options] = vi.mocked(fetch).mock.calls[0]
    const url = new URL(String(rawUrl))
    expect(url.origin + url.pathname).toBe('https://factory.example.test/backend/api/work-orders/42')
    expect(url.searchParams.has('_t')).toBe(true)
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer current-token')
    expect(options?.method || 'GET').toBe('GET')
  })

  it('clears the active session for an expired authenticated JSON response', async () => {
    const session = createSession()
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { message: '登录已过期' }))
    await expect(api.fetchUsers('current-token')).rejects.toMatchObject({ status: 401, message: '登录已过期' })
    expect(session.isLoggedIn.value).toBe(false)
    expect(localStorage.getItem('heihu_token')).toBeNull()
    expect(document.getElementById('kicked-out-overlay')).toBeNull()
  })

  it('never logs out an existing session when an unauthenticated login fails', async () => {
    const session = createSession()
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { message: '用户名或密码错误' }))
    await expect(api.login({ factoryCode: 'F2', username: 'other', password: 'bad' })).rejects.toMatchObject({ status: 401 })
    expect(session.token.value).toBe('current-token')
  })

  it.each([200, 401])('rejects a delayed %s response without overwriting or clearing a newer session', async (status) => {
    const session = createSession('old-token')
    let resolve!: (response: Response) => void
    vi.mocked(fetch).mockReturnValue(new Promise<Response>(done => { resolve = done }))
    const pending = api.fetchUsers('old-token')
    session.persistSession({ token: 'new-token', user, isSuperAdmin: false })
    resolve(jsonResponse(status, status === 200 ? [user] : { message: '您的账号已在其他地方登录，您已被挤下线' }))
    await expect(pending).rejects.toMatchObject({ status: 409 })
    expect(session.token.value).toBe('new-token')
    expect(document.getElementById('kicked-out-overlay')).toBeNull()
  })

  it('treats image authorization errors like JSON requests without falsely showing a kicked-out message', async () => {
    const session = createSession()
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { message: '登录已过期' }))
    await expect(api.fetchReportImageBlob('current-token', 7)).rejects.toMatchObject({ status: 401 })
    expect(session.isLoggedIn.value).toBe(false)
    expect(document.getElementById('kicked-out-overlay')).toBeNull()
  })

  it('also rejects a stale JSON body when the session changes after response headers arrive', async () => {
    const session = createSession('old-token')
    let resolve!: (text: string) => void
    const response = jsonResponse(200, [])
    vi.spyOn(response, 'text').mockReturnValue(new Promise(done => { resolve = done }))
    vi.mocked(fetch).mockResolvedValue(response)
    const pending = api.fetchUsers('old-token')
    await flushPromises()
    session.persistSession({ token: 'new-token', user, isSuperAdmin: false })
    resolve(JSON.stringify([user]))
    await expect(pending).rejects.toMatchObject({ status: 409 })
    expect(session.token.value).toBe('new-token')
  })

  it('uses the configured API base for multipart uploads without a JSON Content-Type', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { accessPath: '/api/custom-field-attachments/1/file' }))
    const data = new FormData()
    data.append('file', new File(['sample'], 'sample.txt'))
    await api.uploadCustomFieldAttachment('upload-token', data)
    const [url, options] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('https://factory.example.test/backend/api/custom-field-attachments')
    expect(new Headers(options?.headers).has('Content-Type')).toBe(false)
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer upload-token')
    expect(options?.body).toBe(data)
  })

  it('uses the configured API base and encoded filters for inventory exports', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('csv'))
    await api.fetchProductsExportBlob('export-token', { name: 'A&B' })
    const [url, options] = vi.mocked(fetch).mock.calls[0]
    expect(String(url)).toMatch(/^https:\/\/factory\.example\.test\/backend\/api\/products\/export\?/)
    expect(new URL(String(url)).searchParams.get('name')).toBe('A&B')
    expect(new Headers(options?.headers).get('Authorization')).toBe('Bearer export-token')
  })
})

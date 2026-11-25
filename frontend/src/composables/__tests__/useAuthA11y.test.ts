import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { handleUnauthorizedSession, useAuth } from '../useAuth'

const auth = useAuth()
const user = { id: 1, username: 'fixture', name: 'Fixture', role: 'ADMIN' as const, roleLabel: '管理员',
  virtualUser: false, enabled: true, createdAt: '2026-11-24' }
beforeEach(() => {
  auth.logout()
  document.body.innerHTML = '<div id="app"><main class="login-page"><input autocomplete="username" /></main></div>'
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
  auth.persistSession({ token: 'fixture-token', user, isSuperAdmin: false })
})
afterEach(() => { auth.logout(); document.body.innerHTML = ''; vi.unstubAllGlobals() })

describe('forced-signout dialog respects login and keyboard boundaries', () => {
  it('moves focus into a named modal, traps Tab and returns to the next logical login field', async () => {
    handleUnauthorizedSession('fixture-token', '您的账号已在其他地方登录，您已被挤下线')
    await Promise.resolve()
    const dialog = document.getElementById('kicked-out-overlay')!
    expect(auth.token.value).toBe('')
    expect(dialog.getAttribute('role')).toBe('alertdialog')
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)?.innerText).toBe('下线通知')
    expect(document.getElementById(dialog.getAttribute('aria-describedby')!)?.innerText).toContain('其他地方登录')
    expect(document.getElementById('app')!.inert).toBe(true)
    const button = dialog.querySelector('button')!
    expect(document.activeElement).toBe(button)
    const tab = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true })
    button.dispatchEvent(tab)
    expect(tab.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(button)
    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(document.getElementById('kicked-out-overlay')).toBeNull()
    expect(document.getElementById('app')!.inert).toBe(false)
    expect(document.activeElement).toBe(document.querySelector('input'))
  })

  it('cleans up the old modal when a new login arrives without leaving the app inert', async () => {
    handleUnauthorizedSession('fixture-token', '您的账号已在其他地方登录，您已被挤下线')
    auth.persistSession({ token: 'new-token', user, isSuperAdmin: false })
    await Promise.resolve()
    expect(document.getElementById('kicked-out-overlay')).toBeNull()
    expect(document.getElementById('app')!.inert).toBe(false)
    expect(auth.token.value).toBe('new-token')
  })

  it('honors reduced motion and does not install a delayed reload or an extra confirmation', () => {
    handleUnauthorizedSession('fixture-token', '您的账号已在其他地方登录，您已被挤下线')
    const dialog = document.getElementById('kicked-out-overlay')!
    expect(dialog.style.transition).toBe('none')
    expect(dialog.style.opacity).toBe('1')
    expect((dialog.firstElementChild as HTMLElement).style.transform).toBe('none')
    dialog.querySelector('button')!.click()
    expect(document.getElementById('kicked-out-overlay')).toBeNull()
    expect(auth.token.value).toBe('')
  })
})

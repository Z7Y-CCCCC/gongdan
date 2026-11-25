import { ref, computed } from 'vue'
import { t } from '../i18n'
import type { UserInfo, LoginResponse } from '../api'

function readStoredUser(): UserInfo | null {
  const raw = localStorage.getItem('heihu_user')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

const token = ref(localStorage.getItem('heihu_token') || '')
const currentUser = ref<UserInfo | null>(readStoredUser())
const currentFactoryCode = ref(localStorage.getItem('heihu_factory_code') || 'HHGD')
const isSuperAdmin = ref(localStorage.getItem('heihu_is_super_admin') === 'true')

const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))
let dismissKickedOverlay: ((restoreLoginFocus?: boolean) => void) | undefined

function persistSession(response: LoginResponse) {
  isKickedAlerted = false
  dismissKickedOverlay?.(false)
  document.getElementById('kicked-out-overlay')?.remove()
  token.value = response.token
  currentUser.value = response.user
  isSuperAdmin.value = response.isSuperAdmin ?? false
  localStorage.setItem('heihu_token', response.token)
  localStorage.setItem('heihu_user', JSON.stringify(response.user))
  localStorage.setItem('heihu_is_super_admin', String(response.isSuperAdmin ?? false))
}

function logout() {
  dismissKickedOverlay?.(false)
  token.value = ''
  currentUser.value = null
  isSuperAdmin.value = false
  localStorage.removeItem('heihu_token')
  localStorage.removeItem('heihu_user')
  localStorage.removeItem('heihu_is_super_admin')
}

let isKickedAlerted = false

// A delayed response from a previous login must never clear a newer session.
export function handleUnauthorizedSession(requestToken: string, message: string) {
  if (!requestToken || requestToken !== token.value) return
  if (message === '您的账号已在其他地方登录，您已被挤下线') {
    handleKickedOut()
  } else {
    logout()
  }
}

export function handleKickedOut() {
  if (isKickedAlerted) return
  isKickedAlerted = true
  
  logout()

  // The expired workspace is no longer an appropriate focus-return target.
  const overlay = document.createElement('div')
  overlay.id = 'kicked-out-overlay'
  overlay.setAttribute('role', 'alertdialog')
  overlay.setAttribute('aria-modal', 'true')
  overlay.setAttribute('aria-labelledby', 'kicked-session-title')
  overlay.setAttribute('aria-describedby', 'kicked-session-description')
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  Object.assign(overlay.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    width: '100vw',
    height: '100vh',
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: '999999',
    opacity: '0',
    transition: reducedMotion ? 'none' : 'opacity 0.14s ease'
  })

  const card = document.createElement('div')
  Object.assign(card.style, {
    background: 'var(--surface-raised, #fff)',
    padding: '24px',
    borderRadius: '6px',
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
    width: '360px',
    maxWidth: 'calc(100vw - 32px)',
    boxSizing: 'border-box',
    textAlign: 'center',
    transform: reducedMotion ? 'none' : 'translateY(7px)',
    transition: reducedMotion ? 'none' : 'transform 0.14s ease',
    fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
  })

  // Static application icon only; no server or business markup is inserted here.
  const iconWrapper = document.createElement('div')
  Object.assign(iconWrapper.style, {
    width: '44px',
    height: '44px',
    borderRadius: '7px',
    background: 'var(--status-warning-bg, #fff9e6)',
    color: '#d97706',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    margin: '0 auto 16px'
  })
  iconWrapper.innerHTML = `<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="color: #ff9800"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>`
  iconWrapper.setAttribute('aria-hidden', 'true')

  // 标题
  const title = document.createElement('h3')
  title.id = 'kicked-session-title'
  title.innerText = t('下线通知')
  Object.assign(title.style, {
    margin: '0 0 12px',
    fontSize: '18px',
    fontWeight: '600',
    color: 'var(--text-primary, #1e293b)'
  })

  // 详情信息
  const content = document.createElement('p')
  content.id = 'kicked-session-description'
  content.innerText = t('您的账号已在其他地方登录，您已被挤下线。')
  Object.assign(content.style, {
    margin: '0 0 20px',
    fontSize: '15px',
    color: 'var(--text-secondary, #64748b)',
    lineHeight: '1.6'
  })

  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'kicked-session-button'
  button.innerText = t('重新登录')
  Object.assign(button.style, {
    background: '#007a60',
    color: '#ffffff',
    border: 'none',
    padding: '12px 32px',
    borderRadius: '7px',
    fontSize: '15px',
    fontWeight: '600',
    cursor: 'pointer',
    transition: reducedMotion ? 'none' : 'background 0.14s ease',
    minHeight: '44px',
    width: '100%'
  })

  button.onmouseover = () => {
    button.style.background = '#006b53'
  }
  button.onmouseout = () => {
    button.style.background = '#007a60'
  }

  card.appendChild(iconWrapper)
  card.appendChild(title)
  card.appendChild(content)
  card.appendChild(button)
  overlay.appendChild(card)
  document.body.appendChild(overlay)
  const appRoot = document.getElementById('app')
  const previouslyInert = appRoot?.inert ?? false
  if (appRoot) appRoot.inert = true
  const dismiss = (restoreLoginFocus = true) => {
    overlay.remove()
    if (appRoot) appRoot.inert = previouslyInert
    if (dismissKickedOverlay === dismiss) dismissKickedOverlay = undefined
    if (restoreLoginFocus) document.querySelector<HTMLInputElement>('.login-page input[autocomplete="username"], .login-page input')?.focus()
  }
  dismissKickedOverlay = dismiss
  button.onclick = () => dismiss()
  overlay.addEventListener('keydown', event => {
    if (event.key === 'Tab') { event.preventDefault(); event.stopPropagation(); button.focus() }
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); dismiss() }
  })
  queueMicrotask(() => { if (overlay.isConnected) button.focus() })
  const reveal = () => {
    if (!overlay.isConnected) return
    overlay.style.opacity = '1'
    card.style.transform = 'none'
  }
  if (reducedMotion) reveal()
  else requestAnimationFrame(reveal)
}

function setFactoryCode(code: string) {
  currentFactoryCode.value = code
  localStorage.setItem('heihu_factory_code', code)
}

export function useAuth() {
  return {
    token,
    currentUser,
    currentFactoryCode,
    isSuperAdmin,
    isLoggedIn,
    persistSession,
    logout,
    setFactoryCode,
    handleKickedOut
  }
}

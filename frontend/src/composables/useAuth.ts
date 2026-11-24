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

function persistSession(response: LoginResponse) {
  isKickedAlerted = false
  document.getElementById('kicked-out-overlay')?.remove()
  token.value = response.token
  currentUser.value = response.user
  isSuperAdmin.value = response.isSuperAdmin ?? false
  localStorage.setItem('heihu_token', response.token)
  localStorage.setItem('heihu_user', JSON.stringify(response.user))
  localStorage.setItem('heihu_is_super_admin', String(response.isSuperAdmin ?? false))
}

function logout() {
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

  // 动态构建高颜值磨砂玻璃拟物化自定义下线通知弹窗
  const overlay = document.createElement('div')
  overlay.id = 'kicked-out-overlay'
  Object.assign(overlay.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    width: '100vw',
    height: '100vh',
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
    backdropFilter: 'blur(12px)',
    webkitBackdropFilter: 'blur(12px)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: '999999',
    opacity: '0',
    transition: 'opacity 0.4s cubic-bezier(0.16, 1, 0.3, 1)'
  })

  const card = document.createElement('div')
  Object.assign(card.style, {
    background: '#ffffff',
    padding: '36px 32px',
    borderRadius: '24px',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
    width: '420px',
    maxWidth: '90%',
    textAlign: 'center',
    transform: 'scale(0.9) translateY(20px)',
    transition: 'transform 0.4s cubic-bezier(0.16, 1, 0.3, 1)',
    fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
  })

  // 头部精致警告图标
  const iconWrapper = document.createElement('div')
  Object.assign(iconWrapper.style, {
    width: '64px',
    height: '64px',
    borderRadius: '50%',
    background: '#fff9e6',
    color: '#d97706',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    margin: '0 auto 20px'
  })
  iconWrapper.innerHTML = `<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="color: #ff9800"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>`

  // 标题
  const title = document.createElement('h3')
  title.innerText = t('下线通知')
  Object.assign(title.style, {
    margin: '0 0 12px',
    fontSize: '20px',
    fontWeight: '600',
    color: '#1e293b'
  })

  // 详情信息
  const content = document.createElement('p')
  content.innerText = t('您的账号已在其他地方登录，您已被挤下线。')
  Object.assign(content.style, {
    margin: '0 0 28px',
    fontSize: '15px',
    color: '#64748b',
    lineHeight: '1.6'
  })

  // 精致的渐变色交互按钮
  const button = document.createElement('button')
  button.innerText = t('重新登录')
  Object.assign(button.style, {
    background: 'linear-gradient(135deg, #00b889 0%, #00966f 100%)',
    color: '#ffffff',
    border: 'none',
    padding: '12px 32px',
    borderRadius: '12px',
    fontSize: '15px',
    fontWeight: '600',
    cursor: 'pointer',
    boxShadow: '0 4px 12px rgba(0, 184, 137, 0.25)',
    transition: 'all 0.2s ease',
    outline: 'none',
    width: '100%'
  })

  // 按钮交互微动效
  button.onmouseover = () => {
    button.style.transform = 'translateY(-2px)'
    button.style.boxShadow = '0 6px 16px rgba(0, 184, 137, 0.35)'
  }
  button.onmouseout = () => {
    button.style.transform = 'none'
    button.style.boxShadow = '0 4px 12px rgba(0, 184, 137, 0.25)'
  }
  button.onmousedown = () => {
    button.style.transform = 'translateY(1px) scale(0.98)'
  }
  button.onmouseup = () => {
    button.style.transform = 'translateY(-2px) scale(1)'
  }

  button.onclick = () => {
    // 渐出动效后执行页面重载
    overlay.style.opacity = '0'
    card.style.transform = 'scale(0.9) translateY(20px)'
    setTimeout(() => {
      overlay.remove()
      window.location.reload()
    }, 300)
  }

  card.appendChild(iconWrapper)
  card.appendChild(title)
  card.appendChild(content)
  card.appendChild(button)
  overlay.appendChild(card)
  document.body.appendChild(overlay)

  // 触发优雅的上升渐入动画
  requestAnimationFrame(() => {
    overlay.style.opacity = '1'
    card.style.transform = 'scale(1) translateY(0)'
  })
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

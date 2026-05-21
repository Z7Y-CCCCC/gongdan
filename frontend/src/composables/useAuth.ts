import { ref, computed } from 'vue'
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
    setFactoryCode
  }
}

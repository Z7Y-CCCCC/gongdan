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

const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))

function persistSession(response: LoginResponse) {
  token.value = response.token
  currentUser.value = response.user
  localStorage.setItem('heihu_token', response.token)
  localStorage.setItem('heihu_user', JSON.stringify(response.user))
}

function logout() {
  token.value = ''
  currentUser.value = null
  localStorage.removeItem('heihu_token')
  localStorage.removeItem('heihu_user')
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
    isLoggedIn,
    persistSession,
    logout,
    setFactoryCode
  }
}

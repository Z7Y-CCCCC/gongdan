/**
 * 全局共享状态 composable
 * 提供所有子组件共用的：token、loading、错误消息、确认弹窗、操作提示等
 */
import { computed, reactive, ref, watch } from 'vue'
import type { UserInfo, DepartmentInfo } from '../api'

// ===== 全局单例状态 =====
const token = ref(localStorage.getItem('heihu_token') || '')
const currentUser = ref<UserInfo | null>(readStoredUser())
const loading = ref(false)
const errorMessage = ref('')
let errorTimer: ReturnType<typeof setTimeout> | null = null

const actionMessage = ref('')
let actionTimer: ReturnType<typeof setTimeout> | null = null

const confirmState = reactive({
  show: false,
  title: '',
  message: '',
  action: () => {}
})

const currentFactoryCode = ref(localStorage.getItem('heihu_factory_code') || 'HHGD')

// 全局共享的 users 和 departments（多个页面需要访问）
const users = ref<UserInfo[]>([])
const departments = ref<DepartmentInfo[]>([])

function readStoredUser() {
  const raw = localStorage.getItem('heihu_user')
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

function showAction(message: string) {
  actionMessage.value = message
}

function showError(message: string) {
  errorMessage.value = message
}

const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))

export function useSharedState() {
  // 设置自动清除定时器（只在首次调用时设置）
  if (!errorTimer) {
    watch(errorMessage, (v) => {
      if (errorTimer) clearTimeout(errorTimer)
      if (v) errorTimer = setTimeout(() => { errorMessage.value = '' }, 5000)
    })
  }
  if (!actionTimer) {
    watch(actionMessage, (v) => {
      if (actionTimer) clearTimeout(actionTimer)
      if (v) actionTimer = setTimeout(() => { actionMessage.value = '' }, 3000)
    })
  }

  return {
    token,
    currentUser,
    loading,
    errorMessage,
    actionMessage,
    confirmState,
    currentFactoryCode,
    users,
    departments,
    isLoggedIn,
    showAction,
    showError,
    readStoredUser
  }
}

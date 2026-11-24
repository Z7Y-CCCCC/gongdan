<template>
  <view class="container">
    <view class="header">
      <view class="brand-row">
        <view class="logo-box"><uni-icons type="compose" size="24" color="#00a982"  aria-hidden="true" /></view>
        <view class="brand-copy">
          <text class="title">黑湖小工单</text>
        </view>
      </view>
      <text class="login-title">登录</text>
    </view>

    <view class="form">
      <view class="input-group">
        <text class="label">工厂代码</text>
        <input class="input" v-model="form.factoryCode" placeholder="请输入工厂代码" placeholder-style="color:#ccc" />
      </view>

      <view class="input-group">
        <text class="label">用户名</text>
        <input class="input" v-model="form.username" placeholder="请输入工号/手机号" placeholder-style="color:#ccc" />
      </view>

      <view class="input-group">
        <text class="label">密码</text>
        <input class="input" v-model="form.password" :type="isH5 ? 'password' : 'text'" password placeholder="请输入登录密码" placeholder-style="color:#ccc" />
      </view>

      <button class="btn-submit" :loading="loading" :aria-busy="loading" :disabled="loading" @click="handleBind">登录</button>
      <button v-if="!isH5" class="wechat-login" :disabled="loading" @click="tryWechatLogin">微信登录</button>
    </view>

    <view class="footer">
      <text class="footer-text">没有账号？联系管理员</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount, onMounted } from 'vue'
import { bindAccount, passwordLogin, saveSession, wechatLogin } from '../../services/api'
import { usePageDraft } from '../../composables/usePageDraft'
import { goAfterLogin as navigateAfterLogin, loginRedirect } from '../../utils/navigation'
import { mobileSessionKey } from '../../utils/session-key'
import { onHide } from '@dcloudio/uni-app'
import { useMobilePageActivity } from '../../composables/useMobilePageActivity'

const pageActivity = useMobilePageActivity()

const loading = ref(false)
const redirectUrl = ref('')
const isH5 = typeof window !== 'undefined'
let manualLogin = false
let navigationTimer: ReturnType<typeof setTimeout> | undefined
let pageAlive = true
onHide(() => { if (navigationTimer) clearTimeout(navigationTimer) })
onBeforeUnmount(() => { pageAlive = false; if (navigationTimer) clearTimeout(navigationTimer) })
const configuredWechatMock = import.meta.env.VITE_WECHAT_MOCK_ENABLED
const wechatMockEnabled = configuredWechatMock === 'true'
const form = reactive({
  factoryCode: '',
  username: '',
  password: '',
  openidTicket: ''
})

const { clearDraft: clearAccountDraft } = usePageDraft({
  key: 'gongdan:draft:login-account',
  scope: 'device',
  source: () => ({ factoryCode: form.factoryCode, username: form.username }),
  restore: (draft) => {
    form.factoryCode = draft.factoryCode || ''
    form.username = draft.username || ''
  }
})

onMounted(() => {
  resolveRedirect()
  // A normal browser cannot obtain a Mini Program jscode2session code.
  if (!isH5 && !manualLogin) tryWechatLogin()
})

function resolveRedirect() {
  try {
    const pages = getCurrentPages()
    const currentPage = pages[pages.length - 1] as any
    const options = currentPage?.$page?.options || currentPage?.options || {}
    redirectUrl.value = loginRedirect(options.redirect)
    manualLogin = options.manual === '1'
  } catch { /* H5 routes may not yet have a page instance. */ }
  if (typeof window !== 'undefined' && window.location) {
    const hashStr = window.location.hash || ''
    const qIdx = hashStr.indexOf('?')
    if (qIdx >= 0) {
      const hashParams = new URLSearchParams(hashStr.substring(qIdx))
      const r = hashParams.get('redirect')
      if (r) redirectUrl.value = loginRedirect(r)
      manualLogin = hashParams.get('manual') === '1'
    }
  }
}

function getMockOpenid() {
  let mockOpenid = uni.getStorageSync('mockOpenid')
  if (!mockOpenid) {
    mockOpenid = 'MOCK_OPENID_LOCAL_' + Math.random().toString(36).slice(2)
    uni.setStorageSync('mockOpenid', mockOpenid)
  }
  return mockOpenid
}

async function getWxCode() {
  if (typeof window !== 'undefined') return ''
  return new Promise<string>((resolve) => {
    uni.login({
      provider: 'weixin',
      success: (res) => resolve(res.code || ''),
      fail: () => resolve('')
    })
  })
}

async function tryWechatLogin() {
  if (!pageAlive || loading.value) return
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture(true)
  const current = () => pageAlive && isPageCurrent() && session === mobileSessionKey()
  loading.value = true
  try {
    const code = await getWxCode()
    if (!current()) return
    const res = await wechatLogin(wechatMockEnabled ? {
      code,
      mockOpenid: getMockOpenid()
    } : { code })
    if (!current()) return
    if (res.bindRequired) {
      form.openidTicket = res.openidTicket || ''
      return
    }
    saveSession(res)
    goAfterLogin()
  } catch (e: any) {
    if (!current()) return
    form.openidTicket = ''
    uni.showToast({ title: e.message || '微信登录失败，请重试', icon: 'none' })
  } finally {
    if (pageAlive) loading.value = false
  }
}

function goAfterLogin() {
  navigateAfterLogin(redirectUrl.value)
}

const handleBind = async () => {
  if (!pageAlive || loading.value) return
  if (!form.factoryCode.trim() || !form.username.trim() || !form.password) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }
  if (!isH5 && !form.openidTicket) {
    uni.showToast({ title: '请先点击微信登录获取绑定凭证', icon: 'none' })
    return
  }

  loading.value = true
  let signedIn = false
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture()
  const current = () => pageAlive && isPageCurrent() && session === mobileSessionKey()
  try {
    const credentials = {
      factoryCode: form.factoryCode.trim(),
      username: form.username.trim(),
      password: form.password
    }
    const res = isH5 ? await passwordLogin(credentials)
      : await bindAccount({ ...credentials, openidTicket: form.openidTicket })
    if (!current()) return
    saveSession(res)
    signedIn = true
    const signedInSession = mobileSessionKey()
    form.password = ''
    clearAccountDraft()
    uni.showToast({ title: '登录成功', icon: 'success' })
    
    navigationTimer = setTimeout(() => {
      if (pageAlive && isPageCurrent() && signedInSession === mobileSessionKey()) goAfterLogin()
    }, 1500)
  } catch (e: any) {
    if (!current()) return
    uni.showModal({
      title: '登录失败',
      content: e.message || '请检查工厂代码或账号密码是否正确',
      confirmText: '关闭',
      showCancel: false
    })
  } finally {
    if (pageAlive && !signedIn) loading.value = false
  }
}
</script>

<style scoped>
.container {
  padding: 48rpx 36rpx 32rpx;
  background-color: #f6f7f7;
  min-height: 100vh;
  box-sizing: border-box;
}

.header {
  margin-bottom: 28rpx;
  display: flex;
  flex-direction: column;
}

.brand-row {
  display: flex;
  align-items: center;
  margin-bottom: 70rpx;
}

.logo-box {
  width: 72rpx;
  height: 72rpx;
  border-radius: 14rpx;
  background: #e5f7f2;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.brand-copy {
  display: flex;
  flex-direction: column;
}

.title {
  font-size: 34rpx;
  font-weight: 700;
  color: #202926;
}

.subtitle {
  font-size: 22rpx;
  color: #64756d;
  margin-top: 4rpx;
}

.login-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #202926;
}

.form {
  background: white;
  padding: 34rpx;
  border-radius: 16rpx;
  border: 1rpx solid #e8eceb;
}

.input-group {
  margin-bottom: 30rpx;
}

.label {
  font-size: 28rpx;
  color: #586360;
  margin-bottom: 12rpx;
  display: block;
}

.input {
  width: 100%;
  display: block;
  height: 82rpx;
  background: #f7f9f8;
  border-radius: 10rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  border: 1rpx solid #dfe5e3;
  box-sizing: border-box;
}

.btn-submit {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #00856b;
  color: white;
  border-radius: 12rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 30rpx;
  font-weight: 600;
  margin-top: 10rpx;
  border: none;
}

.btn-submit::after {
  border: none;
}

.btn-submit[disabled] {
  opacity: 0.72;
}

.wechat-login {
  margin-top: 24rpx;
  background: transparent;
  color: #00856b;
  font-size: 24rpx;
}

.footer {
  margin-top: 48rpx;
  text-align: center;
}

.footer-text {
  font-size: 24rpx;
  color: #9aa4a1;
}
</style>

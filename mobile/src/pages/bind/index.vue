<template>
  <view class="container">
    <view class="header">
      <view class="brand-row">
        <view class="logo-box"><uni-icons type="compose" size="24" color="#00a982" /></view>
        <view class="brand-copy">
          <text class="title">黑湖小工单</text>
          <text class="subtitle">生产现场移动工作台</text>
        </view>
      </view>
      <text class="login-title">登录工厂账号</text>
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
        <input class="input" v-model="form.password" password placeholder="请输入登录密码" placeholder-style="color:#ccc" />
      </view>

      <button class="btn-submit" :loading="loading" @click="handleBind">登录</button>
    </view>

    <view class="footer">
      <text class="footer-text">如果您没有账号，请联系车间管理员</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { bindAccount, saveSession, wechatLogin } from '../../services/api'
import { usePageDraft } from '../../composables/usePageDraft'

const loading = ref(false)
const redirectUrl = ref('')
function isLocalH5() {
  return typeof window !== 'undefined' && window.location && ['127.0.0.1', 'localhost'].includes(window.location.hostname)
}
const configuredWechatMock = import.meta.env.VITE_WECHAT_MOCK_ENABLED
const wechatMockEnabled = configuredWechatMock === 'true' || (!configuredWechatMock && isLocalH5())
const form = reactive({
  factoryCode: '',
  username: '',
  password: '',
  openidTicket: ''
})

const { clearDraft: clearAccountDraft } = usePageDraft({
  key: 'gongdan:draft:login-account',
  source: () => ({ factoryCode: form.factoryCode, username: form.username }),
  restore: (draft) => {
    form.factoryCode = draft.factoryCode || ''
    form.username = draft.username || ''
  }
})

onMounted(() => {
  resolveRedirect()
  tryWechatLogin()
})

function resolveRedirect() {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  if (currentPage?.$page?.options?.redirect) {
    redirectUrl.value = decodeURIComponent(currentPage.$page.options.redirect)
  }
  if (typeof window !== 'undefined' && window.location) {
    const hashStr = window.location.hash || ''
    const qIdx = hashStr.indexOf('?')
    if (qIdx >= 0) {
      const hashParams = new URLSearchParams(hashStr.substring(qIdx))
      const r = hashParams.get('redirect')
      if (r) redirectUrl.value = decodeURIComponent(r)
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
  loading.value = true
  try {
    const code = await getWxCode()
    const res = await wechatLogin(wechatMockEnabled ? {
      code,
      mockOpenid: getMockOpenid()
    } : { code })
    if (res.bindRequired) {
      form.openidTicket = res.openidTicket || ''
      return
    }
    saveSession(res)
    goAfterLogin()
  } catch (e) {
    if (wechatMockEnabled) {
      form.openidTicket = getMockOpenid().replace('MOCK_OPENID_', 'MOCK_TICKET_')
    }
  } finally {
    loading.value = false
  }
}

function goAfterLogin() {
  if (redirectUrl.value) {
    if (typeof window !== 'undefined' && redirectUrl.value.startsWith('#')) {
      window.location.hash = redirectUrl.value.substring(1)
      window.location.reload()
      return
    }
    if (redirectUrl.value.startsWith('/pages/')) {
      uni.navigateTo({ url: redirectUrl.value })
      return
    }
  }
  if (typeof window !== 'undefined' && window.location) {
    window.location.hash = '#/pages/home/index'
    return
  }
  uni.switchTab({ url: '/pages/home/index' })
}

const handleBind = async () => {
  if (!form.factoryCode || !form.username || !form.password) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }

  loading.value = true
  try {
    const res = await bindAccount({
      openidTicket: form.openidTicket,
      factoryCode: form.factoryCode,
      username: form.username,
      password: form.password
    })
    
    saveSession(res)
    clearAccountDraft()
    uni.showToast({ title: '登录成功', icon: 'success' })
    
    setTimeout(() => {
      goAfterLogin()
    }, 1500)
  } catch (e: any) {
    uni.showModal({
      title: '登录失败',
      content: e.message || '请检查工厂代码或账号密码是否正确',
      showCancel: false
    })
  } finally {
    loading.value = false
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
  color: #8a9693;
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
  background-color: #00b889;
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

.footer {
  margin-top: 48rpx;
  text-align: center;
}

.footer-text {
  font-size: 24rpx;
  color: #9aa4a1;
}
</style>

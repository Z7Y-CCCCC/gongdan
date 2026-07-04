<template>
  <view class="container">
    <view class="header">
      <view class="logo-icon">🏭</view>
      <text class="title">黑湖小工单 - 移动报工</text>
      <text class="subtitle">绑定您的工厂账号以开始工作</text>
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

      <button class="btn-submit" :loading="loading" @tap="handleBind">立即登录</button>
    </view>

    <view class="footer">
      <text class="footer-text">如果您没有账号，请联系车间管理员</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { bindAccount, saveSession, wechatLogin } from '../../services/api'

const loading = ref(false)
const redirectUrl = ref('')
const wechatMockEnabled = import.meta.env.VITE_WECHAT_MOCK_ENABLED === 'true'
const form = reactive({
  factoryCode: '',
  username: '',
  password: '',
  openidTicket: ''
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
  padding: 60rpx 40rpx;
  background-color: #f6f7f7;
  min-height: 100vh;
}

.header {
  margin-bottom: 80rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-icon {
  font-size: 120rpx;
  margin-bottom: 30rpx;
}

.title {
  font-size: 40rpx;
  font-weight: bold;
  color: #333;
}

.subtitle {
  font-size: 26rpx;
  color: #999;
  margin-top: 10rpx;
}

.form {
  background: white;
  padding: 40rpx;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.05);
}

.input-group {
  margin-bottom: 40rpx;
}

.label {
  font-size: 28rpx;
  color: #666;
  margin-bottom: 15rpx;
  display: block;
}

.input {
  height: 90rpx;
  background: #f9f9f9;
  border-radius: 10rpx;
  padding: 0 30rpx;
  font-size: 30rpx;
  border: 1px solid #eee;
}

.btn-submit {
  background-color: #00b889;
  color: white;
  border-radius: 45rpx;
  height: 90rpx;
  line-height: 90rpx;
  font-size: 32rpx;
  font-weight: bold;
  margin-top: 20rpx;
}

.btn-submit::after {
  border: none;
}

.footer {
  margin-top: 100rpx;
  text-align: center;
}

.footer-text {
  font-size: 24rpx;
  color: #ccc;
}
</style>

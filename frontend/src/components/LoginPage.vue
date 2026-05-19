<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  login,
  phonePasswordLogin,
  phoneSmsLogin,
  sendSmsCode,
  selectFactory,
  type PhoneLoginResult,
  type FactoryOptionItem
} from '../api'

const emit = defineEmits<{
  (e: 'login-success', payload: { token: string; user: any; factoryCode?: string }): void
}>()

const loading = ref(false)
const errorMessage = ref('')

const loginForm = reactive({
  factoryCode: 'HHGD',
  username: 'admin',
  password: ''
})

// Phone login state
const loginTab = ref<'phone' | 'factory'>('phone')
const phoneLoginMode = ref<'password' | 'sms'>('password')
const phoneForm = reactive({
  phone: '',
  password: '',
  code: ''
})
const smsCountdown = ref(0)
let smsTimer: ReturnType<typeof setInterval> | null = null
const phoneError = ref('')
const showFactorySelect = ref(false)
const factoryList = ref<FactoryOptionItem[]>([])
const pendingPhoneCredential = reactive({
  phone: '',
  loginType: '' as 'password' | 'sms',
  credential: ''
})

const isPhoneValid = computed(() => /^1\d{10}$/.test(phoneForm.phone))

async function handleLogin() {
  errorMessage.value = ''
  loading.value = true
  try {
    const response = await login(loginForm)
    emit('login-success', {
      token: response.token,
      user: response.user,
      factoryCode: loginForm.factoryCode
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}

// Phone login functions
async function handleSendSms() {
  if (!isPhoneValid.value) {
    phoneError.value = '请输入正确的手机号'
    return
  }
  if (smsCountdown.value > 0) return
  try {
    await sendSmsCode({ phone: phoneForm.phone })
    smsCountdown.value = 60
    smsTimer = setInterval(() => {
      smsCountdown.value--
      if (smsCountdown.value <= 0) {
        if (smsTimer) clearInterval(smsTimer)
        smsTimer = null
      }
    }, 1000)
  } catch (e) {
    phoneError.value = e instanceof Error ? e.message : '发送验证码失败'
  }
}

async function handlePhoneLogin() {
  phoneError.value = ''
  if (!isPhoneValid.value) {
    phoneError.value = '请输入正确的手机号'
    return
  }
  loading.value = true
  try {
    let result: PhoneLoginResult
    if (phoneLoginMode.value === 'password') {
      if (!phoneForm.password) {
        phoneError.value = '请输入密码'
        loading.value = false
        return
      }
      result = await phonePasswordLogin({ phone: phoneForm.phone, password: phoneForm.password })
      pendingPhoneCredential.phone = phoneForm.phone
      pendingPhoneCredential.loginType = 'password'
      pendingPhoneCredential.credential = phoneForm.password
    } else {
      if (!phoneForm.code) {
        phoneError.value = '请输入验证码'
        loading.value = false
        return
      }
      result = await phoneSmsLogin({ phone: phoneForm.phone, code: phoneForm.code })
      pendingPhoneCredential.phone = phoneForm.phone
      pendingPhoneCredential.loginType = 'sms'
      pendingPhoneCredential.credential = phoneForm.code
    }
    handlePhoneLoginResult(result)
  } catch (e) {
    phoneError.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}

function handlePhoneLoginResult(result: PhoneLoginResult) {
  if (result.selectRequired && result.factories) {
    factoryList.value = result.factories
    showFactorySelect.value = true
  } else if (result.token && result.user) {
    emit('login-success', { token: result.token, user: result.user })
  }
}

async function handleSelectFactory(factoryId: number) {
  loading.value = true
  phoneError.value = ''
  try {
    const result = await selectFactory({
      phone: pendingPhoneCredential.phone,
      factoryId,
      loginType: pendingPhoneCredential.loginType as 'password' | 'sms',
      credential: pendingPhoneCredential.credential
    })
    if (result.token && result.user) {
      const factory = factoryList.value.find(f => f.factoryId === factoryId)
      emit('login-success', {
        token: result.token,
        user: result.user,
        factoryCode: factory?.factoryCode
      })
      showFactorySelect.value = false
    }
  } catch (e) {
    phoneError.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-hero">
      <div class="brand">
        <div class="logo-mark">工</div>
        <span>黑湖小工单</span>
      </div>
      <div class="hero-copy">
        <h1>随时随地，管理车间现场</h1>
        <div class="feature-grid">
          <div class="feature-item">
            <span class="feature-icon">01</span>
            <div>
              <strong>扫码接单，移动作业</strong>
              <p>一线工人手机扫码，无纸化生产</p>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">02</span>
            <div>
              <strong>计时计件薪资实时核算</strong>
              <p>解放财务劳动力，绩效薪资实时核算</p>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">03</span>
            <div>
              <strong>工厂全局大屏监控</strong>
              <p>一站式数据管理驱动生产</p>
            </div>
          </div>
        </div>
      </div>
    </section>
    <section class="login-panel">
      <div class="login-box">
        <h2>欢迎登录</h2>
        <p class="login-sub">黑湖小工单管理系统</p>

        <!-- Tab switcher -->
        <div class="login-tabs">
          <span class="login-tab" :class="{ active: loginTab === 'phone' }" @click="loginTab = 'phone'">手机号登录</span>
          <span class="login-tab" :class="{ active: loginTab === 'factory' }" @click="loginTab = 'factory'">工厂账号登录</span>
        </div>

        <!-- Factory select overlay -->
        <div v-if="showFactorySelect" class="factory-select-panel">
          <h3>请选择工厂</h3>
          <div class="factory-list">
            <div v-for="f in factoryList" :key="f.factoryId" class="factory-item" @click="handleSelectFactory(f.factoryId)">
              <span class="factory-name-label">{{ f.factoryName }}</span>
              <span class="factory-code-label">{{ f.factoryCode }}</span>
            </div>
          </div>
          <button class="text-btn" type="button" @click="showFactorySelect = false">返回</button>
        </div>

        <!-- Phone login form -->
        <form v-if="loginTab === 'phone' && !showFactorySelect" class="login-form" @submit.prevent="handlePhoneLogin">
          <div class="phone-mode-switch">
            <span :class="{ active: phoneLoginMode === 'password' }" @click="phoneLoginMode = 'password'">密码登录</span>
            <span class="mode-divider">|</span>
            <span :class="{ active: phoneLoginMode === 'sms' }" @click="phoneLoginMode = 'sms'">验证码登录</span>
          </div>
          <label>
            <span>手机号</span>
            <input v-model="phoneForm.phone" autocomplete="tel" placeholder="请输入手机号" maxlength="11" />
          </label>
          <label v-if="phoneLoginMode === 'password'">
            <span>密码</span>
            <input v-model="phoneForm.password" autocomplete="current-password" placeholder="请输入密码" type="password" />
          </label>
          <label v-else class="sms-label">
            <span>验证码</span>
            <div class="sms-input-row">
              <input v-model="phoneForm.code" placeholder="请输入验证码" maxlength="6" />
              <button type="button" class="sms-btn" :disabled="smsCountdown > 0 || !isPhoneValid" @click="handleSendSms">
                {{ smsCountdown > 0 ? smsCountdown + 's后重新获取' : '获取验证码' }}
              </button>
            </div>
          </label>
          <p v-if="phoneError" class="form-error">{{ phoneError }}</p>
          <button class="primary-btn login-btn" :disabled="loading" type="submit">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <!-- Factory account login form (existing) -->
        <form v-if="loginTab === 'factory' && !showFactorySelect" class="login-form" @submit.prevent="handleLogin">
          <label>
            <span>工厂代码</span>
            <input v-model="loginForm.factoryCode" autocomplete="organization" placeholder="请输入工厂代码" />
          </label>
          <label>
            <span>用户名</span>
            <input v-model="loginForm.username" autocomplete="username" placeholder="请输入用户名" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" autocomplete="current-password" placeholder="请输入密码" type="password" />
          </label>
          <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
          <button class="primary-btn login-btn" :disabled="loading" type="submit">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>

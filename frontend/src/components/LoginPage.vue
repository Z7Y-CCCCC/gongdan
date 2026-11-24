<script setup lang="ts">
import UiIcon from './UiIcon.vue'
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
import BindFactoryPage from './BindFactoryPage.vue'

const emit = defineEmits<{
  (e: 'login-success', payload: { token: string; user: any; factoryCode?: string }): void
}>()

const loading = ref(false)
const errorMessage = ref('')

const loginForm = reactive({
  factoryCode: '',
  username: '',
  password: ''
})
const showLoginPassword = ref(false)

// Phone login state
const loginTab = ref<'phone' | 'factory'>('factory')
const rememberMe = ref(false)

// Restore saved login identifiers only. Never persist the password in browser storage.
try {
  const saved = localStorage.getItem('gongdan_remember_me')
  if (saved) {
    const parsed = JSON.parse(saved)
    if (parsed.remember) {
      rememberMe.value = true
      loginForm.factoryCode = parsed.factoryCode || ''
      loginForm.username = parsed.username || ''
      if (parsed.password) {
        localStorage.setItem('gongdan_remember_me', JSON.stringify({
          remember: true,
          factoryCode: loginForm.factoryCode,
          username: loginForm.username
        }))
      }
    }
  }
} catch (e) {
  console.error('Failed to read rememberMe from localStorage:', e)
}
const phoneLoginMode = ref<'password' | 'sms'>('password')
const phoneForm = reactive({
  phone: '',
  password: '',
  code: ''
})
const showPhonePassword = ref(false)
const smsCountdown = ref(0)
let smsTimer: ReturnType<typeof setInterval> | null = null
const phoneError = ref('')
const showFactorySelect = ref(false)
const showBindFactory = ref(false)
const bindingPhone = ref('')
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
    if (rememberMe.value) {
      localStorage.setItem('gongdan_remember_me', JSON.stringify({
        remember: true,
        factoryCode: loginForm.factoryCode,
        username: loginForm.username
      }))
    } else {
      localStorage.removeItem('gongdan_remember_me')
    }
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
  if (result.bindingRequired && result.phone) {
    bindingPhone.value = result.phone
    showBindFactory.value = true
  } else if (result.selectRequired && result.factories) {
    factoryList.value = result.factories
    showFactorySelect.value = true
  } else if (result.token && result.user) {
    emit('login-success', { token: result.token, user: result.user })
  }
}

function handleBindBack() {
  showBindFactory.value = false
  bindingPhone.value = ''
}

function handleBound(payload: { token: string; user: any }) {
  emit('login-success', payload)
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
    <section class="login-panel">
      <div class="login-box">
        <div class="brand">
          <span class="brand-mark"><img class="logo-mark" src="/logo.jpg" alt="数字工单通" /></span>
          <div class="brand-name"><h1>数字工单通</h1></div>
        </div>

        <!-- Tab switcher -->
        <div class="login-tabs">
          <button class="login-tab" type="button" :class="{ active: loginTab === 'factory' }" :aria-pressed="loginTab === 'factory'" @click="loginTab = 'factory'">工厂账号登录</button>
          <button class="login-tab" type="button" :class="{ active: loginTab === 'phone' }" :aria-pressed="loginTab === 'phone'" @click="loginTab = 'phone'">手机号登录</button>
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
          <button class="text-btn ui-icon-button" type="button" @click="showFactorySelect = false" title="返回" aria-label="返回"><UiIcon name="arrowLeft" /></button>
        </div>

        <!-- Bind factory page -->
        <BindFactoryPage
          v-if="showBindFactory"
          :phone="bindingPhone"
          @back="handleBindBack"
          @bound="handleBound"
        />

        <!-- Phone login form -->
        <form v-if="loginTab === 'phone' && !showFactorySelect && !showBindFactory" class="login-form" @submit.prevent="handlePhoneLogin">
          <div class="phone-mode-switch">
            <button type="button" :class="{ active: phoneLoginMode === 'password' }" :aria-pressed="phoneLoginMode === 'password'" @click="phoneLoginMode = 'password'">密码登录</button>
            <button type="button" :class="{ active: phoneLoginMode === 'sms' }" :aria-pressed="phoneLoginMode === 'sms'" @click="phoneLoginMode = 'sms'">验证码登录</button>
          </div>
          <label>
            <span>手机号</span>
            <input v-model="phoneForm.phone" autocomplete="tel" placeholder="请输入手机号" maxlength="11" />
          </label>
          <label v-if="phoneLoginMode === 'password'">
            <span>密码</span>
            <div class="password-input-wrap">
              <input v-model="phoneForm.password" autocomplete="current-password" placeholder="请输入密码" :type="showPhonePassword ? 'text' : 'password'" />
              <button
                class="password-toggle ui-icon-button"
                type="button"
                :aria-label="showPhonePassword ? '隐藏密码' : '显示密码'"
                :title="showPhonePassword ? '隐藏密码' : '显示密码'"
                @click="showPhonePassword = !showPhonePassword"
              ><UiIcon name="eye" :size="18" /></button>
            </div>
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
        <form v-if="loginTab === 'factory' && !showFactorySelect && !showBindFactory" class="login-form" @submit.prevent="handleLogin">
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
            <div class="password-input-wrap">
              <input v-model="loginForm.password" autocomplete="current-password" placeholder="请输入密码" :type="showLoginPassword ? 'text' : 'password'" />
              <button
                class="password-toggle ui-icon-button"
                type="button"
                :aria-label="showLoginPassword ? '隐藏密码' : '显示密码'"
                :title="showLoginPassword ? '隐藏密码' : '显示密码'"
                @click="showLoginPassword = !showLoginPassword"
              ><UiIcon name="eye" :size="18" /></button>
            </div>
          </label>
          <div class="remember-me-row">
            <label class="remember-me-label">
              <input type="checkbox" v-model="rememberMe" />
              <span>记住账号</span>
            </label>
          </div>
          <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
          <button class="primary-btn login-btn" :disabled="loading" type="submit">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>
        <p v-if="!showFactorySelect && !showBindFactory" class="login-help">账号由工厂管理员统一创建与维护</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  grid-template-columns: minmax(0, 440px);
  justify-content: center;
  align-content: center;
  gap: 0;
  padding: 32px 20px;
  background: var(--canvas, #f5f7f6);
}
.login-page::before, .login-page::after { display: none; }
.login-panel {
  justify-self: center;
  padding: 32px;
  border-radius: 12px;
  border: 1px solid var(--line, #dfe7e4);
  background: var(--surface, #fff);
  box-shadow: 0 12px 40px rgb(0 0 0 / 8%);
  backdrop-filter: none;
}
.brand { margin-bottom: 24px; gap: 12px; }
.brand-mark { width: 42px; height: 42px; border: 0; box-shadow: none; }
.brand-name h1 { margin: 0; font-size: 22px; color: var(--ink, #20302c); background: none; -webkit-text-fill-color: currentColor; text-shadow: none; }
.phone-mode-switch button { border: 0; padding: 0; background: none; color: #6b7280; font: inherit; cursor: pointer; }
.phone-mode-switch button.active { color: var(--accent, #059669); font-weight: 600; }
@media (max-width: 480px) {
  .login-page { padding: 16px; }
  .login-panel { padding: 24px; }
}
</style>

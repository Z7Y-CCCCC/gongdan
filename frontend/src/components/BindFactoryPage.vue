<script setup lang="ts">
import { reactive, ref } from 'vue'
import { bindFactory, type BindFactoryResponse } from '../api'

const props = defineProps<{
  phone: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'bound', payload: { token: string; user: any }): void
}>()

const form = reactive({
  factoryCode: '',
  authorizationCode: ''
})

const loading = ref(false)
const errorMessage = ref('')
const bindSuccess = ref(false)
const defaultPassword = ref('')

async function handleSubmit() {
  errorMessage.value = ''

  if (!form.factoryCode.trim()) {
    errorMessage.value = '请输入工厂代码'
    return
  }
  if (!form.authorizationCode.trim()) {
    errorMessage.value = '请输入授权码'
    return
  }

  loading.value = true
  try {
    const result: BindFactoryResponse = await bindFactory({
      phone: props.phone,
      factoryCode: form.factoryCode.trim(),
      authorizationCode: form.authorizationCode.trim()
    })
    defaultPassword.value = result.defaultPassword
    bindSuccess.value = true
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '绑定失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

function handleGoLogin() {
  emit('back')
}
</script>

<template>
  <div class="bind-factory-container">
    <!-- Success state -->
    <div v-if="bindSuccess" class="bind-success">
      <div class="success-icon">✓</div>
      <h3>绑定成功</h3>
      <p class="success-hint">您的账号已成功绑定工厂，以下是您的默认登录密码：</p>
      <div class="default-password">{{ defaultPassword }}</div>
      <p class="password-tip">请牢记此密码，后续可使用手机号 + 密码登录</p>
      <button class="primary-btn" @click="handleGoLogin">返回登录</button>
    </div>

    <!-- Bind form -->
    <div v-else>
      <h3 class="bind-title">绑定工厂</h3>
      <p class="bind-hint">手机号 {{ phone }} 尚未绑定工厂，请输入工厂代码和授权码完成绑定</p>
      <form class="bind-form" @submit.prevent="handleSubmit">
        <label>
          <span>工厂代码</span>
          <input
            v-model="form.factoryCode"
            placeholder="请输入工厂代码"
            autocomplete="off"
          />
        </label>
        <label>
          <span>授权码</span>
          <input
            v-model="form.authorizationCode"
            placeholder="请输入授权码"
            autocomplete="off"
          />
        </label>
        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <button class="primary-btn login-btn" :disabled="loading" type="submit">
          {{ loading ? '绑定中...' : '确认绑定' }}
        </button>
        <button class="text-btn" type="button" @click="handleGoLogin">返回登录</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.bind-factory-container {
  width: 100%;
}

.bind-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--ink);
}

.bind-hint {
  font-size: 14px;
  color: var(--muted);
  margin: 0 0 24px;
  line-height: 1.5;
}

.bind-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.bind-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  color: var(--ink);
}

.bind-form label span {
  font-weight: 500;
}

.bind-form input {
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;
}

.bind-form input:focus {
  border-color: var(--accent);
}

.form-error {
  color: #e53e3e;
  font-size: 13px;
  margin: 0;
}

.primary-btn {
  width: 100%;
  padding: 12px;
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.primary-btn:hover:not(:disabled) {
  background: var(--accent-dark);
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.text-btn {
  background: none;
  border: none;
  color: var(--muted);
  font-size: 14px;
  cursor: pointer;
  padding: 8px;
  text-align: center;
}

.text-btn:hover {
  color: var(--accent);
}

.bind-success {
  text-align: center;
  padding: 16px 0;
}

.success-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  background: var(--accent);
  color: #fff;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 28px;
  font-weight: bold;
}

.bind-success h3 {
  font-size: 20px;
  margin: 0 0 8px;
  color: var(--ink);
}

.success-hint {
  font-size: 14px;
  color: var(--muted);
  margin: 0 0 16px;
}

.default-password {
  font-size: 28px;
  font-weight: 700;
  color: var(--accent);
  background: #f0faf7;
  border: 2px dashed var(--accent);
  border-radius: 10px;
  padding: 16px 24px;
  margin: 0 auto 12px;
  display: inline-block;
  letter-spacing: 4px;
}

.password-tip {
  font-size: 13px;
  color: var(--muted);
  margin: 0 0 24px;
}
</style>

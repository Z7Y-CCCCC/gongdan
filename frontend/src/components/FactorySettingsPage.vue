<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { fetchCurrentFactory, updateCurrentFactory, type CurrentFactoryInfo } from '../api'
import UiIcon from './UiIcon.vue'

const props = defineProps<{
  token: string
}>()

const emit = defineEmits<{
  (e: 'notice', message: string): void
  (e: 'error', message: string): void
}>()

const loading = ref(false)
const saving = ref(false)
const factory = ref<CurrentFactoryInfo | null>(null)
const form = reactive({ name: '' })

async function loadFactory() {
  loading.value = true
  try {
    const data = await fetchCurrentFactory(props.token)
    factory.value = data
    form.name = data.name
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '加载工厂信息失败')
  } finally {
    loading.value = false
  }
}

async function saveFactory() {
  const name = form.name.trim()
  if (!name) {
    emit('error', '请填写工厂名称')
    return
  }
  saving.value = true
  try {
    const data = await updateCurrentFactory(props.token, { name })
    factory.value = data
    form.name = data.name
    emit('notice', '工厂信息已保存')
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '保存工厂信息失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadFactory)
</script>

<template>
  <section class="content factory-settings-page">
    <div class="factory-header">
      <div>
        <h2>工厂设置</h2>
        <p>维护当前租户的基础信息。工厂代码用于登录和租户隔离，暂不支持在前台修改。</p>
      </div>
      <button class="ghost-btn" type="button" :disabled="loading" @click="loadFactory"><UiIcon name="refresh" />刷新</button>
    </div>

    <div v-if="loading && !factory" class="factory-empty">加载中...</div>

    <div v-else class="factory-panel">
      <div class="factory-summary">
        <div class="summary-item">
          <span>工厂代码</span>
          <strong>{{ factory?.code || '-' }}</strong>
        </div>
        <div class="summary-item">
          <span>状态</span>
          <strong>{{ factory?.enabled ? '启用中' : '停用中' }}</strong>
        </div>
        <div class="summary-item">
          <span>创建时间</span>
          <strong>{{ factory?.createdAt || '-' }}</strong>
        </div>
      </div>

      <form class="factory-form" @submit.prevent="saveFactory">
        <label>
          <span>工厂名称</span>
          <input v-model="form.name" maxlength="128" placeholder="请输入工厂名称" />
        </label>
        <div class="form-actions">
          <button class="primary-btn" type="submit" :disabled="saving">
            <UiIcon name="save" />
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.factory-settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.factory-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.factory-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #1f2d2a;
}

.factory-header p {
  margin: 0;
  color: #64716d;
  font-size: 13px;
}

.factory-panel {
  border: 1px solid #e4ece8;
  border-radius: 8px;
  background: #fff;
  padding: 18px;
}

.factory-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.summary-item {
  border: 1px solid #edf2ef;
  border-radius: 6px;
  padding: 12px;
  background: #f8faf9;
}

.summary-item span {
  display: block;
  margin-bottom: 6px;
  color: #71807a;
  font-size: 12px;
}

.summary-item strong {
  color: #1f2d2a;
  font-size: 15px;
  font-weight: 600;
  word-break: break-all;
}

.factory-form {
  max-width: 520px;
}

.factory-form label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #33413d;
  font-size: 13px;
}

.factory-form input {
  height: 36px;
  border: 1px solid #d9e3de;
  border-radius: 6px;
  padding: 0 10px;
  font-size: 14px;
}

.form-actions {
  margin-top: 16px;
}

.factory-empty {
  padding: 36px;
  text-align: center;
  color: #7a8782;
}

@media (max-width: 760px) {
  .factory-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .factory-summary {
    grid-template-columns: 1fr;
  }
}
</style>

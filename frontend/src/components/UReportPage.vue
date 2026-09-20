<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ApiError, establishUReportSession } from '../api'

const props = defineProps<{ token: string }>()

const iframeSrc = ref('')
const loading = ref(true)
const frameLoading = ref(false)
const errorMessage = ref('')
let disposed = false

async function load() {
  loading.value = true
  frameLoading.value = false
  iframeSrc.value = ''
  errorMessage.value = ''
  try {
    const session = await establishUReportSession(props.token)
    if (disposed) return
    iframeSrc.value = `${session.path}?_t=${Date.now()}`
    frameLoading.value = true
  } catch (error) {
    if (disposed) return
    errorMessage.value = error instanceof ApiError ? error.message : '报表设计器启动失败，请稍后重试'
  } finally {
    if (!disposed) loading.value = false
  }
}

function onFrameLoaded() {
  frameLoading.value = false
}

onMounted(load)
onBeforeUnmount(() => {
  disposed = true
  iframeSrc.value = ''
})
</script>

<template>
  <section class="content ureport-page">
    <div class="ureport-header">
      <div>
        <h2>高级报表</h2>
        <p>UReport2 已嵌入当前系统，报表设计、预览和导出都运行在现有 Java 服务内。</p>
      </div>
      <button class="ghost-btn" type="button" :disabled="loading || frameLoading" @click="load">刷新</button>
    </div>

    <div v-if="loading" class="ureport-state">
      <strong>正在启动报表设计器</strong>
      <span>使用当前管理员登录态建立安全会话…</span>
    </div>

    <div v-else-if="errorMessage" class="ureport-state ureport-state-error">
      <strong>{{ errorMessage }}</strong>
      <span>请确认当前账号具有管理员权限，或联系系统管理员。</span>
      <button class="primary-btn" type="button" @click="load">重新加载</button>
    </div>

    <iframe
      v-else-if="iframeSrc"
      class="ureport-frame"
      :src="iframeSrc"
      title="UReport2 高级报表"
      @load="onFrameLoaded"
    ></iframe>
  </section>
</template>

<style scoped>
.ureport-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 144px);
}

.ureport-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.ureport-header h2 {
  margin: 0 0 6px;
  color: #1f2d2a;
  font-size: 20px;
}

.ureport-header p {
  margin: 0;
  color: #64716d;
  font-size: 13px;
}

.ureport-state {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  padding: 32px;
  border: 1px dashed #cbdad4;
  border-radius: 12px;
  background: #fbfdfc;
  color: #64716d;
  text-align: center;
}

.ureport-state strong {
  color: #1f2d2a;
  font-size: 18px;
}

.ureport-state-error {
  border-color: #f0c5c5;
  background: #fffafa;
}

.ureport-state-error strong {
  color: #a53c3c;
}

.ureport-frame {
  flex: 1;
  width: 100%;
  min-height: 760px;
  border: 1px solid #dfe9e4;
  border-radius: 12px;
  background: #fff;
}

:global(.dark) .ureport-header h2,
:global(.dark) .ureport-state strong {
  color: #e6f2ed;
}

:global(.dark) .ureport-header p,
:global(.dark) .ureport-state {
  color: #96a5ad;
}

:global(.dark) .ureport-state {
  border-color: #344641;
  background: #15201e;
}

:global(.dark) .ureport-state-error {
  border-color: #6c3e3e;
  background: #251919;
}

:global(.dark) .ureport-frame {
  border-color: #344641;
  background: #101917;
}
</style>

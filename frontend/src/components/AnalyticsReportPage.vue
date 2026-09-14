<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { embedDashboard } from '@superset-ui/embedded-sdk'
import { ApiError, fetchAnalyticsConfig, fetchAnalyticsGuestToken, type AnalyticsConfig } from '../api'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()

const mountPoint = ref<HTMLElement | null>(null)
const config = ref<AnalyticsConfig | null>(null)
const loading = ref(true)
const embedding = ref(false)
const errorMessage = ref('')
let disposed = false

async function load() {
  loading.value = true
  embedding.value = false
  errorMessage.value = ''
  config.value = null
  if (mountPoint.value) mountPoint.value.replaceChildren()

  try {
    const nextConfig = await fetchAnalyticsConfig(props.token)
    if (disposed) return
    config.value = nextConfig
    if (!nextConfig.enabled) return
    if (!nextConfig.supersetDomain || !nextConfig.dashboardId) {
      errorMessage.value = '分析报表配置不完整，请联系系统管理员'
      return
    }

    await nextTick()
    if (disposed || !mountPoint.value) return
    embedding.value = true
    await embedDashboard({
      id: nextConfig.dashboardId,
      supersetDomain: nextConfig.supersetDomain,
      mountPoint: mountPoint.value,
      fetchGuestToken: async () => {
        const result = await fetchAnalyticsGuestToken(props.token)
        return result.token
      },
      dashboardUiConfig: {
        hideTitle: true,
        hideTab: true,
        filters: { expanded: false },
        urlParams: { standalone: 3 }
      }
    })
  } catch (error) {
    if (disposed) return
    errorMessage.value = error instanceof ApiError
      ? error.message
      : '分析报表加载失败，请检查 Superset 服务和嵌入配置'
  } finally {
    if (!disposed) {
      loading.value = false
      embedding.value = false
    }
  }
}

function retry() {
  void load()
}

onMounted(load)
onBeforeUnmount(() => {
  disposed = true
  mountPoint.value?.replaceChildren()
})
</script>

<template>
  <section class="content analytics-report-page">
    <div class="analytics-header">
      <div>
        <h2>分析报表</h2>
        <p>基于 Apache Superset 的工厂级生产分析，数据范围由当前登录工厂自动隔离。</p>
      </div>
      <button class="ghost-btn" type="button" :disabled="loading || embedding" @click="retry">刷新</button>
    </div>

    <div v-if="loading" class="analytics-state">
      <strong>正在连接分析报表</strong>
      <span>获取安全嵌入凭证…</span>
    </div>

    <div v-else-if="errorMessage" class="analytics-state analytics-state-error">
      <strong>{{ errorMessage }}</strong>
      <span>请确认 Superset 地址、服务账号、Dashboard ID 和允许嵌入域名配置正确。</span>
      <button class="primary-btn" type="button" @click="retry">重新加载</button>
    </div>

    <div v-else-if="config && !config.enabled" class="analytics-state analytics-state-disabled">
      <strong>分析报表尚未启用</strong>
      <span>当前系统仍使用内置生产报表。配置 Superset 后，这里会显示可交互的分析看板。</span>
    </div>

    <div v-else ref="mountPoint" class="analytics-embed" aria-label="Superset 分析报表"></div>
  </section>
</template>

<style scoped>
.analytics-report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 144px);
}

.analytics-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.analytics-header h2 {
  margin: 0 0 6px;
  color: #1f2d2a;
  font-size: 20px;
}

.analytics-header p {
  margin: 0;
  color: #64716d;
  font-size: 13px;
}

.analytics-state {
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

.analytics-state strong {
  color: #1f2d2a;
  font-size: 18px;
}

.analytics-state-error {
  border-color: #f0c5c5;
  background: #fffafa;
}

.analytics-state-error strong {
  color: #a53c3c;
}

.analytics-state-disabled {
  border-style: solid;
}

.analytics-embed {
  flex: 1;
  min-height: 720px;
  overflow: hidden;
  border: 1px solid #dfe9e4;
  border-radius: 12px;
  background: #fff;
}

.analytics-embed :deep(iframe) {
  display: block;
  width: 100%;
  min-height: 720px;
  border: 0;
}

:global(.dark) .analytics-header h2,
:global(.dark) .analytics-state strong {
  color: #e6f2ed;
}

:global(.dark) .analytics-header p,
:global(.dark) .analytics-state {
  color: #96a5ad;
}

:global(.dark) .analytics-state {
  border-color: #344641;
  background: #15201e;
}

:global(.dark) .analytics-state-error {
  border-color: #6c3e3e;
  background: #251919;
}

:global(.dark) .analytics-embed {
  border-color: #344641;
  background: #101917;
}
</style>

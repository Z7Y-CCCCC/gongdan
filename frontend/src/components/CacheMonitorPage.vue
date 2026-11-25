<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchCacheMetrics, type MonitorCacheMetricsInfo } from '../api'
import UiIcon from './UiIcon.vue'
import { t } from '../i18n'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ notice: [message: string] }>()
const loading = ref(false)
const metrics = ref<MonitorCacheMetricsInfo | null>(null)
const errorMessage = ref('')
const beginLoad = useLatestRequest()
const statusText = computed(() => loading.value ? t('加载中') : errorMessage.value ? t('加载失败') : t(metrics.value?.basicInfo.status || '未获取数据'))

const basicItems = [
  ['redisVersion', 'Redis 版本'], ['cacheType', '缓存类型'], ['mode', '运行模式'], ['port', '端口'], ['clients', '客户端数'],
  ['uptimeDays', '运行时间（天）'], ['usedMemory', '使用内存'], ['memoryConfig', '内存配置'],
  ['aofEnabled', 'AOF 是否开启'], ['rdbStatus', 'RDB 状态'], ['keyCount', 'Key 数量'],
  ['networkInput', '网络入口'], ['networkOutput', '网络出口']
]

const basicInfo = computed(() => metrics.value?.basicInfo || {})
const commandStats = computed(() => metrics.value?.commandStats || [])
const memoryInfo = computed(() => metrics.value?.memoryInfo || { usedBytes: 0, maxBytes: 0, usedPercent: 0, usedHuman: '0B', maxHuman: '0B' })
const commandTotal = computed(() => commandStats.value.reduce((total, item) => total + item.count, 0))
const pieColors = ['#1689ed', '#18ad85', '#f39a3d', '#8d6de8', '#e35d6a', '#27a9a0', '#d2a52e', '#5f7cdb', '#d468b0', '#63a65d', '#e87843', '#7b8794']
const commandPieGradient = computed(() => {
  if (!commandTotal.value) return 'conic-gradient(#edf2f0 0deg 360deg)'
  let start = 0
  const segments = commandStats.value.map((item, index) => {
    const end = start + item.count / commandTotal.value * 360
    const segment = `${pieColors[index % pieColors.length]} ${start}deg ${end}deg`
    start = end
    return segment
  })
  return `conic-gradient(${segments.join(', ')})`
})

function commandPercent(count: number) {
  return commandTotal.value ? Math.round(count / commandTotal.value * 1000) / 10 : 0
}

function commandColor(index: number) {
  return pieColors[index % pieColors.length]
}

function displayValue(key: string) {
  return basicInfo.value[key] || '-'
}

async function load() {
  const token = props.token, latest = beginLoad()
  if (!token) return
  const current = () => latest() && props.token === token
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchCacheMetrics(token)
    if (current()) metrics.value = result
  } catch (error) {
    if (current()) { errorMessage.value = error instanceof Error ? error.message : '缓存监控加载失败'; emit('notice', errorMessage.value) }
  } finally {
    if (current()) loading.value = false
  }
}

onMounted(load)
useRefreshOnActivated(load)
watch(() => props.token, () => { beginLoad(); metrics.value = null; errorMessage.value = ''; loading.value = false; void load() }, { flush: 'sync' })
</script>

<template>
  <section class="content monitor-page">
    <div class="monitor-page-header">
      <div>
        <h1>缓存监控</h1>
      </div>
      <div class="monitor-header-actions">
        <span class="monitor-status" :class="{ fallback: !!errorMessage || (basicInfo.status && !basicInfo.status.includes('正常')) }">
          <i></i>{{ statusText }}
        </span>
        <button class="toolbar-btn ui-icon-button" type="button" title="刷新缓存监控" @click="load" aria-label="刷新缓存监控"><UiIcon name="refresh" /></button>
      </div>
    </div>

    <p v-if="errorMessage" role="alert">{{ errorMessage }} <button class="ui-icon-button" type="button" @click="load" title="重试" aria-label="重试"><UiIcon name="refresh" /></button></p>
    <p v-if="loading && !metrics" role="status">加载中...</p>
    <div v-if="metrics" class="monitor-metrics-grid" :aria-busy="loading">
      <section class="monitor-panel monitor-basic-panel">
        <div class="monitor-panel-head"><div><UiIcon name="layers" :size="16" /><strong>基本信息</strong></div><small>连接状态实时读取</small></div>
        <div v-if="loading" class="monitor-panel-loading">加载中...</div>
        <div v-else class="metric-grid">
          <div v-for="item in basicItems" :key="item[0]" class="metric-item">
            <span>{{ $t(item[1]) }}</span><strong>{{ displayValue(item[0]) }}</strong>
          </div>
        </div>
      </section>

      <section class="monitor-panel memory-panel">
        <div class="monitor-panel-head"><div><UiIcon name="activity" :size="16" /><strong>内存使用</strong></div><small>{{ memoryInfo.usedPercent }}%</small></div>
        <div class="memory-summary"><strong>{{ memoryInfo.usedHuman }}</strong><span>/ {{ memoryInfo.maxHuman }}</span></div>
        <div class="metric-progress"><span :style="{ width: `${Math.min(100, Math.max(0, memoryInfo.usedPercent))}%` }"></span></div>
        <div class="memory-foot"><span>已使用</span><span>最大可用</span></div>
        <div class="monitor-note">删除缓存仅影响缓存数据，不会删除业务数据库记录。恢复期限以清理结果为准。</div>
      </section>
    </div>

    <section v-if="metrics" class="monitor-panel command-panel">
      <div class="monitor-panel-head"><div><UiIcon name="list" :size="16" /><strong>命令统计</strong></div><small>调用次数 Top 12</small></div>
      <div v-if="commandStats.length === 0" class="monitor-panel-loading">暂无命令统计</div>
      <div v-else class="command-chart">
        <div class="command-pie" :style="{ background: commandPieGradient }" role="img" aria-label="Redis 命令调用占比">
          <div class="command-pie-hole"><strong>{{ commandTotal.toLocaleString() }}</strong><span>总调用</span></div>
        </div>
        <div class="command-legend">
          <div v-for="(item, index) in commandStats" :key="item.name" class="command-legend-row">
            <div class="command-legend-name"><i :style="{ backgroundColor: commandColor(index) }"></i><span>{{ item.name }}</span></div>
            <div class="command-legend-value"><strong>{{ item.count.toLocaleString() }}</strong><small>{{ commandPercent(item.count) }}%</small></div>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
type LoadingStageStatus = 'pending' | 'loading' | 'done' | 'error'

interface LoadingStage {
  key: string
  label: string
  detail?: string
  status: LoadingStageStatus
}

const props = withDefaults(defineProps<{
  visible: boolean
  progress: number
  stage: string
  stages: LoadingStage[]
  error?: string
}>(), {
  error: ''
})

const emit = defineEmits<{
  retry: []
}>()

function clampProgress(value: number) {
  return Math.min(100, Math.max(0, Math.round(value)))
}

function statusLabel(status: LoadingStageStatus) {
  if (status === 'done') return '完成'
  if (status === 'loading') return '进行中'
  if (status === 'error') return '失败'
  return '等待'
}

function completedCount() {
  return props.stages.filter(stage => stage.status === 'done').length
}
</script>

<template>
  <Transition name="workspace-loading">
    <div
      v-if="visible"
      class="workspace-loading-overlay"
      role="status"
      aria-live="polite"
      aria-label="系统加载进度"
    >
      <section class="workspace-loading-card" :class="{ 'is-error': error }">
        <div class="workspace-loading-heading">
          <div class="workspace-loading-mark" aria-hidden="true">
            <span class="workspace-loading-ring workspace-loading-ring-one"></span>
            <span class="workspace-loading-ring workspace-loading-ring-two"></span>
            <span class="workspace-loading-core"></span>
          </div>
          <div class="workspace-loading-title-group">
            <strong>正在加载工作区</strong>
            <span>{{ stage || '准备启动' }}</span>
          </div>
          <strong class="workspace-loading-percent">{{ clampProgress(progress) }}%</strong>
        </div>

        <div class="workspace-loading-progress" aria-hidden="true">
          <span :style="{ width: `${clampProgress(progress)}%` }"></span>
        </div>

        <div class="workspace-loading-summary">
          <span>{{ completedCount() }} / {{ stages.length }} 个阶段完成</span>
          <span>{{ error ? '加载暂停' : '请保持页面打开' }}</span>
        </div>

        <ol class="workspace-loading-stages">
          <li v-for="item in stages" :key="item.key" :class="`is-${item.status}`">
            <span class="workspace-loading-status" aria-hidden="true">
              <span v-if="item.status === 'done'" class="workspace-loading-check">✓</span>
              <span v-else-if="item.status === 'error'" class="workspace-loading-cross">!</span>
              <span v-else-if="item.status === 'loading'" class="workspace-loading-dot"></span>
              <span v-else class="workspace-loading-pending"></span>
            </span>
            <span class="workspace-loading-stage-copy">
              <span>{{ item.label }}</span>
              <small v-if="item.detail">{{ item.detail }}</small>
            </span>
            <em>{{ statusLabel(item.status) }}</em>
          </li>
        </ol>

        <div v-if="error" class="workspace-loading-error">
          <span class="workspace-loading-error-icon">!</span>
          <div>
            <strong>加载没有完成</strong>
            <p>{{ error }}</p>
          </div>
          <button type="button" class="workspace-loading-retry" @click="emit('retry')">重新加载</button>
        </div>
      </section>
    </div>
  </Transition>
</template>

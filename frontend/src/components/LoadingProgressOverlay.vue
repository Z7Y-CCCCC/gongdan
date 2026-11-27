<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
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
  focusOnError?: boolean
}>(), {
  error: ''
})

const emit = defineEmits<{
  retry: []
}>()
const retryButton = ref<HTMLButtonElement | null>(null)

watch(retryButton, element => {
  if (!element || !props.focusOnError) return
  nextTick(() => {
    if (document.activeElement === document.body) retryButton.value?.focus({ preventScroll: true })
  })
})

function clampProgress(value: number) {
  return Math.min(100, Math.max(0, Math.round(value)))
}

</script>

<template>
  <Transition name="workspace-loading" appear>
    <div
      v-if="visible"
      class="workspace-loading-overlay"
      role="status"
      aria-live="polite"
      aria-label="系统加载进度"
    >
      <section class="workspace-loading-card" :class="{ 'is-error': error }">
        <div class="workspace-loading-heading">
          <span v-if="!error" class="workspace-loading-spinner" aria-hidden="true"></span>
          <UiIcon v-else name="warning" :size="22" />
          <div class="workspace-loading-title-group">
            <strong>{{ error ? '加载失败' : (stage || '加载中') }}</strong>
          </div>
          <strong class="workspace-loading-percent">{{ clampProgress(progress) }}%</strong>
        </div>

        <div class="workspace-loading-progress" role="progressbar" aria-label="加载进度" :aria-valuenow="clampProgress(progress)" aria-valuemin="0" aria-valuemax="100">
          <span :style="{ width: `${clampProgress(progress)}%` }"></span>
        </div>

        <div v-if="error" class="workspace-loading-error" role="alert">
          <div>
            <p>{{ error }}</p>
          </div>
          <button ref="retryButton" type="button" class="workspace-loading-retry ui-icon-button" title="重试" aria-label="重试" @click="emit('retry')"><UiIcon name="refresh" :size="18" /></button>
        </div>
      </section>
    </div>
  </Transition>
</template>

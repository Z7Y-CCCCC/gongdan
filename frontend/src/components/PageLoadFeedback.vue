<script setup lang="ts">
import UiIcon from './UiIcon.vue'

defineProps<{ loading: boolean; error: string; hasData: boolean }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading || error" class="page-load-feedback" :class="{ 'has-error': !loading && error }">
    <p v-if="loading" role="status">{{ hasData ? '正在刷新数据…' : '加载中...' }}</p>
    <template v-else>
      <p role="alert"><UiIcon name="warning" /> <span>{{ error }}<small v-if="hasData">显示上次成功加载的数据。</small></span></p>
      <button class="ghost-btn ui-action-button" type="button" @click="$emit('retry')"><UiIcon name="refresh" />重试</button>
    </template>
  </div>
</template>

<style scoped>
.page-load-feedback {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  padding: 12px;
  border: 1px solid var(--border-default);
  border-radius: 7px;
  background: var(--surface-subtle);
  color: var(--text-primary);
  overflow-wrap: anywhere;
}
.page-load-feedback.has-error { background: var(--status-danger-bg); }
.page-load-feedback p { display: flex; align-items: flex-start; gap: 8px; margin: 0; font-size: 14px; min-width: 0; }
.page-load-feedback .ui-icon { flex-shrink: 0; margin-top: 2px; }
.page-load-feedback small { display: block; margin-top: 3px; color: var(--text-secondary); }
</style>

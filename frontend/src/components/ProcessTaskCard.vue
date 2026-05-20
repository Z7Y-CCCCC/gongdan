<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  processName: string
  color: string
  taskCount: number
  plannedQuantity: number
  goodQuantity: number
  defectQuantity: number
}>()

const progressPercent = computed(() =>
  props.plannedQuantity > 0
    ? Math.round(props.goodQuantity / props.plannedQuantity * 100)
    : 0
)

const progressBarWidth = computed(() =>
  Math.min(progressPercent.value, 100) + '%'
)
</script>

<template>
  <div class="process-task-card">
    <!-- Top-left: process name badge -->
    <div class="card-header">
      <span class="process-badge" :style="{ backgroundColor: color }">
        {{ processName }}
      </span>
    </div>

    <!-- Main body: large number + side metrics -->
    <div class="card-body">
      <div class="card-main">
        <span class="task-count">{{ taskCount }}</span>
        <span class="task-label">生产任务数</span>
      </div>
      <div class="card-metrics">
        <div class="metric-row">
          <span class="metric-label">计划数</span>
          <span class="metric-value">{{ plannedQuantity }}</span>
        </div>
        <div class="metric-row">
          <span class="metric-label">良品数</span>
          <span class="metric-value">{{ goodQuantity }}</span>
        </div>
        <div class="metric-row">
          <span class="metric-label">不良品数</span>
          <span class="metric-value">{{ defectQuantity }}</span>
        </div>
      </div>
    </div>

    <!-- Bottom: progress text + bar -->
    <div class="card-footer">
      <div class="progress-info">
        <span class="progress-label">进度</span>
        <span class="progress-percent">{{ progressPercent }}%</span>
      </div>
      <div class="progress-bar-track">
        <div
          class="progress-bar-fill"
          :style="{ width: progressBarWidth, backgroundColor: color }"
        ></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.process-task-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  min-width: 240px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Header: process name badge */
.card-header {
  display: flex;
  align-items: flex-start;
}

.process-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}

/* Body: main number + side metrics */
.card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.task-count {
  font-size: 36px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.2;
}

.task-label {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.card-metrics {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.metric-label {
  color: #999;
  white-space: nowrap;
}

.metric-value {
  color: #333;
  font-weight: 500;
}

/* Footer: progress */
.card-footer {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.progress-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.progress-label {
  font-size: 12px;
  color: #999;
}

.progress-percent {
  font-size: 12px;
  color: #333;
  font-weight: 500;
}

.progress-bar-track {
  width: 100%;
  height: 6px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}
</style>

<script setup lang="ts">
/**
 * HomeDashboardPage - 主页仪表盘
 *
 * 管理员登录后默认展示的主页，包含工序任务卡片区、工单进度报表、绩效指标、生产实时播报四个区块。
 * 通过 provide/inject 将 homeRange 共享给 ProcessTaskSection 和 PerformanceSection。
 *
 * Requirements: 2.1-2.4, 11.1-11.3
 */
import { provide, ref } from 'vue'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'
import type { HomeRange } from '../api'
import ProcessTaskSection from '../components/ProcessTaskSection.vue'
import WorkOrderProgressSection from '../components/WorkOrderProgressSection.vue'
import PerformanceSection from '../components/PerformanceSection.vue'
import LiveBroadcastSection from '../components/LiveBroadcastSection.vue'

const props = defineProps<{
  token: string
}>()
const emit = defineEmits<{ 'view-work-order': [workOrderId: number] }>()

const { forbidden, forbiddenMessage } = useHomeDashboardErrorHandler()

// Time range shared with child components (ProcessTaskSection writes, PerformanceSection reads)
const range = ref<HomeRange>('today')
provide('homeRange', range)

// Handle "view work order" events from child sections
function handleViewWorkOrder(workOrderId: number) {
  emit('view-work-order', workOrderId)
}
</script>

<template>
  <div class="home-dashboard" data-home-view="task-first">
    <!-- Top Bar -->
    <header class="home-topbar">
      <div class="home-topbar-left">
        <h1 class="home-topbar-title">主页</h1>
      </div>
    </header>

    <!-- 403 Forbidden state -->
    <div v-if="forbidden" class="home-forbidden" role="alert">
      <p class="home-forbidden-text">{{ forbiddenMessage || '无权限访问主页' }}</p>
    </div>

    <!-- Main Content Area -->
    <div v-else class="home-content">
      <!-- Top: Process Task Section (full width) -->
      <section class="home-section home-section-process">
        <ProcessTaskSection :token="props.token" />
      </section>

      <!-- Bottom: responsive grid layout -->
      <div class="home-bottom-grid">
        <!-- Bottom Left: Work Order Progress Section -->
        <section class="home-section home-section-workorder">
          <WorkOrderProgressSection
            :token="props.token"
            @view="handleViewWorkOrder"
          />
        </section>

        <!-- Bottom Right: Performance + Live Broadcast stacked -->
        <div class="home-right-column">
          <section class="home-section home-section-performance">
            <PerformanceSection :token="props.token" />
          </section>

          <section class="home-section home-section-broadcast">
            <LiveBroadcastSection
              :token="props.token"
              :active="true"
              @view-work-order="handleViewWorkOrder"
            />
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-dashboard {
  min-height: 100%;
  color: var(--text-primary, #1e2724);
  background-color: var(--page-bg, #f3f5f6);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  line-height: 1.45;
  display: flex;
  flex-direction: column;
}

/* Top Bar */
.home-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--surface-raised, #fff);
  border-bottom: 1px solid var(--border-default, #dde5e2);
}

.home-topbar-left {
  display: flex;
  align-items: center;
}

.home-topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1e2724);
  margin: 0;
}

/* Forbidden state */
.home-forbidden {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.home-forbidden-text {
  font-size: 14px;
  color: var(--text-muted, #879390);
}

/* Main Content */
.home-content {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Section cards */
.home-section {
  min-width: 0;
  background: var(--surface-raised, #fff);
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

/* Process Task Section - full width top */
.home-section-process {
  width: 100%;
}

/* Bottom Grid: responsive layout */
.home-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  min-width: 0;
  gap: 16px;
  flex: 1;
}

/* Right column stacks performance + broadcast */
.home-right-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

/* Page-local theme bridge: preserve child behavior and configured process colors. */
.home-dashboard :deep(button),
.home-dashboard :deep(input),
.home-dashboard :deep(select),
.home-dashboard :deep(textarea),
.home-dashboard :deep(table),
.home-dashboard :deep(th),
.home-dashboard :deep(td) { font-family: inherit !important; }

.home-dashboard :deep(.process-task-title),
.home-dashboard :deep(.work-order-progress__title),
.home-dashboard :deep(.performance-title),
.home-dashboard :deep(.live-broadcast__title),
.home-dashboard :deep(.task-count),
.home-dashboard :deep(.metric-value),
.home-dashboard :deep(.progress-percent),
.home-dashboard :deep(.kpi-value) { color: var(--text-primary) !important; }

.home-dashboard :deep(.process-task-checkbox-label),
.home-dashboard :deep(.work-order-progress__limit-note),
.home-dashboard :deep(.kpi-label),
.home-dashboard :deep(.metric-label),
.home-dashboard :deep(.task-label),
.home-dashboard :deep(.progress-label),
.home-dashboard :deep(.live-broadcast__text),
.home-dashboard :deep(.live-broadcast__time) { color: var(--text-secondary) !important; }

.home-dashboard :deep(.process-task-card) {
  background: var(--surface-raised) !important;
  border-color: var(--border-default) !important;
}
.home-dashboard :deep(.kpi-tile) { background: var(--surface-subtle) !important; }
.home-dashboard :deep(.kpi-value--green) { color: var(--accent-dark) !important; }
.home-dashboard :deep(.kpi-value--red) { color: var(--status-danger-text) !important; }

.home-dashboard :deep(.process-task-settings-btn),
.home-dashboard :deep(.process-task-refresh-btn),
.home-dashboard :deep(.range-tab),
.home-dashboard :deep(.work-order-progress__tab) {
  color: var(--text-secondary) !important;
  background: var(--control-bg) !important;
  border-color: var(--control-border) !important;
}
.home-dashboard :deep(.process-task-settings-btn:hover),
.home-dashboard :deep(.process-task-refresh-btn:hover),
.home-dashboard :deep(.range-tab:hover),
.home-dashboard :deep(.work-order-progress__tab:hover) {
  color: var(--accent-dark) !important;
  background: var(--surface-hover) !important;
  border-color: var(--accent) !important;
}
.home-dashboard :deep(.range-tab--active),
.home-dashboard :deep(.range-tab--active:hover),
.home-dashboard :deep(.work-order-progress__tab--active),
.home-dashboard :deep(.work-order-progress__tab--active:hover) {
  color: #fff !important;
  background: var(--action-bg) !important;
  border-color: var(--action-bg) !important;
}
.home-dashboard :deep(.sort-arrows path.active) { fill: var(--accent-dark); }
.home-dashboard :deep(.work-order-progress__view-link),
.home-dashboard :deep(.live-broadcast__link) {
  color: var(--accent-dark) !important;
  text-decoration: underline;
}

/* Override the legacy global important table polish without affecting other pages. */
.home-dashboard.home-dashboard :deep(.work-order-progress__table-wrapper) {
  background: var(--surface-raised) !important;
  border-color: var(--table-line) !important;
}
.home-dashboard.home-dashboard :deep(.work-order-progress__table th) {
  color: var(--text-secondary) !important;
  background: var(--table-head-bg) !important;
  border-color: var(--table-line) !important;
}
.home-dashboard.home-dashboard :deep(.work-order-progress__table td) {
  color: var(--table-text) !important;
  background: var(--table-row-bg) !important;
  border-color: var(--table-line) !important;
}
.home-dashboard.home-dashboard :deep(.work-order-progress__table tbody tr:hover td) {
  background: var(--table-row-hover) !important;
}

/* Allow for the sidebar and card padding before retaining the wide-screen two-column layout. */
@media (max-width: 1439px) {
  .home-bottom-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-right-column {
    flex-direction: column;
  }
}
</style>

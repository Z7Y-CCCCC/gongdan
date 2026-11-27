<script setup lang="ts">
/**
 * HomeDashboardPage - 主页仪表盘
 *
 * 管理员登录后默认展示的主页，包含工序任务卡片区、工单进度报表、绩效指标、生产实时播报四个区块。
 * 通过 provide/inject 将 homeRange 共享给 ProcessTaskSection 和 PerformanceSection。
 *
 * Requirements: 2.1-2.4, 11.1-11.3
 */
import { onDeactivated, onMounted, provide, ref, watch } from 'vue'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { fetchHomeWorkOrders, fetchReports, type HomeRange, type WorkOrderStatusTab } from '../api'
import { t } from '../i18n'
import UiIcon from '../components/UiIcon.vue'
import ProcessTaskSection from '../components/ProcessTaskSection.vue'
import WorkOrderProgressSection from '../components/WorkOrderProgressSection.vue'
import PerformanceSection from '../components/PerformanceSection.vue'
import LiveBroadcastSection from '../components/LiveBroadcastSection.vue'
import HomeWorkOrderDrawer from '../components/HomeWorkOrderDrawer.vue'

const props = defineProps<{
  token: string
}>()
const emit = defineEmits<{ navigate: [target: 'workorder' | 'workbench'] }>()

const { forbidden, forbiddenMessage } = useHomeDashboardErrorHandler()

// Time range shared with child components (ProcessTaskSection writes, PerformanceSection reads)
const range = ref<HomeRange>('today')
provide('homeRange', range)

type Metric = { value: number | null; failed: boolean; capped?: boolean }
const blank = (): Metric => ({ value: null, failed: false })
const approvals = ref<Metric>(blank())
const inProgress = ref<Metric>(blank())
const overdue = ref<Metric>(blank())
const refreshing = ref(false)
const refreshKey = ref(0)
const selectedOrder = ref<number | null>(null)
const progressSection = ref<InstanceType<typeof WorkOrderProgressSection> | null>(null)
const beginSummary = useLatestRequest()
const PREVIEW_LIMIT = 500

function readCount(value: number, capped = false): Metric {
  return Number.isSafeInteger(value) && value >= 0 ? { value, failed: false, capped } : { value: null, failed: true }
}

async function refreshSummary() {
  const current = beginSummary()
  const token = props.token
  approvals.value = blank()
  inProgress.value = blank()
  refreshing.value = Boolean(token)
  if (!token) return
  const applicable = () => current() && props.token === token
  // Approval has an exact paginated total. The existing progress preview is capped.
  const update = async (target: typeof approvals, request: () => Promise<Metric>) => {
    try {
      const metric = await request()
      if (applicable()) target.value = metric
    } catch {
      if (applicable()) target.value = { value: null, failed: true }
    }
  }
  await Promise.all([
    update(approvals, async () => readCount((await fetchReports(token, { status: 'PENDING', page: 1, size: 1 })).total)),
    update(inProgress, async () => {
      const { items } = await fetchHomeWorkOrders(token, 'in_progress')
      if (!Array.isArray(items)) return readCount(NaN)
      return readCount(Math.min(items.length, PREVIEW_LIMIT), items.length >= PREVIEW_LIMIT)
    })
  ])
  if (applicable()) refreshing.value = false
}

function reloadOverview() {
  refreshKey.value++
  void refreshSummary()
}

function updateOverdue(value: number | null, failed: boolean) {
  overdue.value = value === null ? { value: null, failed } : readCount(value)
}

function showProgress(tab: WorkOrderStatusTab) {
  progressSection.value?.selectAndFocus(tab)
}

function handleViewWorkOrder(workOrderId: number) {
  if (Number.isSafeInteger(workOrderId) && workOrderId > 0) selectedOrder.value = workOrderId
}

onMounted(refreshSummary)
useRefreshOnActivated(refreshSummary)
onDeactivated(() => { selectedOrder.value = null })
watch(() => props.token, () => {
  selectedOrder.value = null
  overdue.value = blank()
  void refreshSummary()
}, { flush: 'sync' })
</script>

<template>
  <div class="home-dashboard" data-home-view="task-first">
    <div v-if="forbidden" class="home-forbidden" role="alert">
      <p class="home-forbidden-text">{{ forbiddenMessage || '无权限访问主页' }}</p>
    </div>

    <div v-else class="home-content">
      <header class="home-topbar">
        <h1 class="home-topbar-title">生产总览</h1>
        <div class="home-topbar-actions">
          <button type="button" class="home-list-button" @click="emit('navigate', 'workorder')">
            <UiIcon name="list" :size="16" />工单列表
          </button>
          <button type="button" class="ui-icon-button" :disabled="refreshing" title="刷新概览" aria-label="刷新概览" @click="reloadOverview">
            <UiIcon name="refresh" :size="17" />
          </button>
        </div>
      </header>

      <section class="home-queue" aria-label="生产状态">
        <button class="home-metric home-metric--amber" type="button" @click="emit('navigate', 'workbench')">
          <span class="home-metric-icon"><UiIcon name="clock" :size="20" /></span>
          <span class="home-metric-content"><span class="home-metric-label">待审批报工</span><strong>{{ approvals.value ?? '—' }}</strong></span>
          <span v-if="approvals.failed" class="home-metric-error">加载失败</span>
          <UiIcon v-else class="home-metric-arrow" name="arrowRight" :size="17" />
        </button>
        <button class="home-metric home-metric--green" type="button" @click="showProgress('in_progress')">
          <span class="home-metric-icon"><UiIcon name="activity" :size="20" /></span>
          <span class="home-metric-content"><span class="home-metric-label">执行中工单</span><strong>{{ inProgress.capped ? t('至少 {0}', [String(inProgress.value)]) : (inProgress.value ?? '—') }}</strong></span>
          <span v-if="inProgress.failed" class="home-metric-error">加载失败</span>
          <UiIcon v-else class="home-metric-arrow" name="arrowRight" :size="17" />
        </button>
        <button class="home-metric home-metric--red" type="button" @click="showProgress('overdue_unfinished')">
          <span class="home-metric-icon"><UiIcon name="warning" :size="20" /></span>
          <span class="home-metric-content"><span class="home-metric-label">逾期未完工</span><strong>{{ overdue.value ?? '—' }}</strong></span>
          <span v-if="overdue.failed" class="home-metric-error">加载失败</span>
          <UiIcon v-else class="home-metric-arrow" name="arrowRight" :size="17" />
        </button>
      </section>

      <div class="home-bottom-grid">
        <section class="home-section home-section-workorder">
          <WorkOrderProgressSection
            ref="progressSection"
            :token="props.token"
            @view="handleViewWorkOrder"
          />
        </section>

        <div class="home-right-column">
          <section class="home-section home-section-performance">
            <PerformanceSection :token="props.token" :refresh-key="refreshKey" @overdue-count="updateOverdue" />
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

      <section class="home-section home-section-process">
        <ProcessTaskSection :token="props.token" />
      </section>

      <HomeWorkOrderDrawer :token="props.token" :order-id="selectedOrder" @dismiss="selectedOrder = null" />
    </div>
  </div>
</template>

<style scoped>
.home-dashboard {
  container-type: inline-size;
  min-height: 100%;
  color: var(--text-primary, #1e2724);
  background-color: var(--page-bg, #f3f5f6);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  line-height: 1.45;
  display: flex;
  flex-direction: column;
}

.home-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.home-topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.home-list-button, .home-topbar-actions .ui-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  border: 1px solid var(--control-border);
  border-radius: 7px;
  background: var(--control-bg);
  color: var(--text-primary);
  cursor: pointer;
}
.home-list-button { padding: 0 12px; font-weight: 600; }
.home-list-button:hover, .home-topbar-actions .ui-icon-button:hover:not(:disabled) { background: var(--surface-hover); }
.home-topbar-actions .ui-icon-button:disabled { opacity: .55; cursor: wait; }
.home-dashboard button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

.home-queue {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.home-metric {
  --metric-ink: #925018;
  --metric-tint: #fff0dc;
  --metric-signal: #dc922e;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 99px;
  padding: 14px 16px;
  border: 1px solid var(--border-default);
  border-inline-start: 3px solid var(--metric-signal);
  border-radius: 10px;
  background: var(--surface-raised);
  background: color-mix(in srgb, var(--surface-raised) 94%, var(--metric-tint));
  color: var(--text-primary);
  text-align: start;
  cursor: pointer;
  transition: translate var(--motion-fast, 140ms) ease, background-color var(--motion-fast, 140ms) ease, box-shadow var(--motion-fast, 140ms) ease;
}
.home-metric--green { --metric-ink: #08765b; --metric-tint: #d9f7ec; --metric-signal: #2ab98d; }
.home-metric--red { --metric-ink: #a43037; --metric-tint: #ffe7e9; --metric-signal: #df6a73; }
.home-metric:hover { translate: 0 -2px; box-shadow: 0 5px 14px #061c1817; background: var(--surface-hover); }
.home-metric:active { translate: 0 0; }
.home-metric-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: var(--metric-tint);
  color: var(--metric-ink);
}
.home-metric-content { min-width: 0; display: grid; gap: 3px; }
.home-metric-label { color: var(--text-secondary); font-size: 13px; font-weight: 600; }
.home-metric strong { font-size: clamp(22px, 3cqi, 30px); line-height: 1.12; font-weight: 700; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.home-metric-error { color: #a43037; font-size: 12px; }
.home-metric-arrow { color: var(--text-secondary); }
:global(.dark-theme) .home-metric { background: var(--surface-raised); }
:global(.dark-theme) .home-metric:hover { background: var(--surface-hover); }
:global(.dark-theme) .home-metric-error { color: var(--status-danger-text); }
:global(.dark-theme) .home-metric-icon { background: color-mix(in srgb, var(--metric-signal) 22%, var(--surface-raised)); color: color-mix(in srgb, var(--metric-signal) 40%, white); }

.home-topbar-title {
  font-size: clamp(23px, 2.9cqi, 32px);
  line-height: 1.15;
  font-weight: 720;
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
  padding: clamp(12px, 2.1cqi, 28px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Section cards */
.home-section {
  min-width: 0;
  background: var(--surface-raised, #fff);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  padding: 14px;
}

/* Process Task Section - full width top */
.home-section-process {
  width: 100%;
}

/* Bottom Grid: responsive layout */
.home-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(245px, 27%);
  min-width: 0;
  gap: 16px;
  align-items: start;
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
}

.home-dashboard :deep(.work-order-progress) { padding: 0; background: transparent; }
.home-dashboard :deep(.work-order-progress__title) { font-size: 18px; }
.home-dashboard :deep(.work-order-progress__table-wrapper) { border-radius: 7px; }

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

@container (max-width: 900px) {
  .home-bottom-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-right-column {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}
@container (max-width: 610px) {
  .home-queue, .home-right-column { grid-template-columns: minmax(0, 1fr); }
  .home-queue { gap: 8px; }
  .home-metric { min-height: 65px; padding: 8px 12px; }
  .home-metric-content { grid-template-columns: minmax(0, 1fr) auto; align-items: center; }
  .home-metric-label { font-size: 14px; }
  .home-section { padding: 10px; }
  .home-list-button, .home-topbar-actions .ui-icon-button { min-height: 44px; }
}
@media (prefers-reduced-motion: reduce) {
  .home-metric, .home-metric:hover { translate: none; transition: none; }
}
</style>

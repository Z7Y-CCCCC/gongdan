<script setup lang="ts">
import { computed, onDeactivated, ref, watch } from 'vue'
import { fetchWorkOrder, type WorkOrderInfo } from '../api'
import { useLatestRequest } from '../composables/useLatestRequest'
import { t } from '../i18n'
import { formatUiDate } from '../i18n/date'
import UiIcon from './UiIcon.vue'

const props = defineProps<{ token: string; orderId: number | null }>()
const emit = defineEmits<{ dismiss: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const order = ref<WorkOrderInfo | null>(null)
const loading = ref(false)
const failed = ref(false)
const beginLoad = useLatestRequest()

const statusNames: Record<string, string> = {
  NOT_STARTED: '未开始', EXECUTING: '执行中', IN_PROGRESS: '执行中',
  PAUSED: '已暂停', COMPLETED: '已完成', FINISHED: '已结束',
  CLOSED: '已关闭', CANCELED: '已取消', CANCELLED: '已取消'
}
const progress = computed(() => {
  const value = order.value?.progressPercent
  return typeof value === 'number' && Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : null
})

async function loadOrder() {
  const current = beginLoad()
  const { orderId, token } = props
  order.value = null
  failed.value = false
  loading.value = Boolean(orderId && token)
  if (!orderId || !token) return
  const applicable = () => current() && props.orderId === orderId && props.token === token
  try {
    const response = await fetchWorkOrder(token, orderId)
    if (!applicable()) return
    if (response?.id !== orderId) failed.value = true
    else order.value = response
  } catch {
    if (applicable()) failed.value = true
  } finally {
    if (applicable()) loading.value = false
  }
}

watch(() => [props.orderId, props.token] as const, ([id, token]) => {
  void loadOrder()
  const element = dialog.value
  if (!element) return
  if (id && token && !element.open && element.isConnected) element.showModal()
  else if ((!id || !token) && element.open) element.close()
}, { flush: 'post' })

onDeactivated(() => {
  beginLoad()
  if (dialog.value?.open) dialog.value.close()
})

let pressedBackdrop = false
function onPointerDown(event: PointerEvent) { pressedBackdrop = event.target === dialog.value }
function onClick(event: MouseEvent) {
  if (pressedBackdrop && event.target === dialog.value) emit('dismiss')
  pressedBackdrop = false
}
</script>

<template>
  <dialog ref="dialog" class="home-order-drawer" aria-label="工单详情"
    @cancel.prevent="emit('dismiss')" @pointerdown="onPointerDown" @click="onClick">
    <div class="home-order-drawer-body">
      <header class="drawer-header">
        <div class="drawer-heading">
          <span v-if="order" class="drawer-caption">工单详情</span>
          <h2>{{ order?.code || '工单详情' }}</h2>
        </div>
        <button class="ui-icon-button drawer-dismiss" type="button" title="关闭工单详情" aria-label="关闭工单详情" autofocus
          @click="emit('dismiss')"><UiIcon name="close" :size="20" /></button>
      </header>
      <div class="drawer-main" :aria-busy="loading">
        <p v-if="loading" class="drawer-state" role="status">加载中…</p>
        <div v-else-if="failed" class="drawer-state" role="alert">
          <span>工单加载失败，请重试</span>
          <button class="drawer-retry" type="button" @click="loadOrder"><UiIcon name="refresh" />重试</button>
        </div>
        <template v-else-if="order">
          <div class="drawer-product">
            <div><h3>{{ order.productName || '—' }}</h3><p v-if="order.productCode || order.productSpec">{{ [order.productCode, order.productSpec].filter(Boolean).join(' · ') }}</p></div>
            <span class="drawer-status">{{ t(statusNames[order.status] || order.status || '—') }}</span>
          </div>
          <div class="drawer-progress">
            <div><span id="home-order-progress-label">工单进度</span><strong>{{ progress === null ? '—' : progress + '%' }}</strong></div>
            <div v-if="progress !== null" class="drawer-progress-track" role="progressbar" aria-labelledby="home-order-progress-label" :aria-valuenow="progress">
              <span :style="{ '--work-order-progress': progress + '%' }"></span>
            </div>
          </div>
          <dl class="drawer-quantities">
            <div><dt>计划数</dt><dd>{{ order.plannedQuantity ?? '—' }}</dd></div>
            <div><dt>良品数</dt><dd>{{ order.goodQuantity ?? '—' }}</dd></div>
            <div><dt>不良数</dt><dd>{{ order.defectQuantity ?? '—' }}</dd></div>
          </dl>
          <dl class="drawer-meta">
            <div><dt>计划开始</dt><dd>{{ formatUiDate(order.planStart) || '—' }}</dd></div>
            <div><dt>计划结束</dt><dd>{{ formatUiDate(order.planEnd) || '—' }}</dd></div>
            <div v-if="order.customerName"><dt>客户</dt><dd>{{ order.customerName }}</dd></div>
            <div v-if="order.remark"><dt>备注</dt><dd>{{ order.remark }}</dd></div>
          </dl>
        </template>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.home-order-drawer {
  position: fixed;
  inset: 0 0 0 auto;
  margin: 0;
  box-sizing: border-box;
  width: min(480px, 100vw);
  max-width: none;
  height: 100dvh;
  max-height: none;
  border: 0;
  border-inline-start: 1px solid var(--border-default);
  background: var(--surface-raised);
  color: var(--text-primary);
  box-shadow: -12px 0 40px #081c1b2b;
  padding: 0;
  outline: none;
  font: inherit;
  translate: 100% 0;
  transition: translate 220ms cubic-bezier(.2, .8, .2, 1), display 220ms allow-discrete, overlay 220ms allow-discrete;
}
.home-order-drawer[open] { translate: 0 0; }
.home-order-drawer::backdrop {
  background: transparent;
  transition: background-color 220ms ease, display 220ms allow-discrete, overlay 220ms allow-discrete;
}
.home-order-drawer[open]::backdrop { background: #091d1a72; }
@starting-style {
  .home-order-drawer[open] { translate: 100% 0; }
  .home-order-drawer[open]::backdrop { background: transparent; }
}
.home-order-drawer-body { display: flex; flex-direction: column; min-height: 100%; }
.drawer-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 24px; border-bottom: 1px solid var(--border-default); }
.drawer-heading { min-width: 0; overflow-wrap: anywhere; }
.drawer-caption { color: var(--text-secondary); font-size: 13px; }
.drawer-heading h2, .drawer-product h3 { margin: 4px 0 0; font-size: 22px; }
.drawer-dismiss {
  appearance: none;
  display: inline-grid;
  place-items: center;
  width: 44px;
  min-width: 44px;
  min-height: 44px;
  padding: 0;
  border: 1px solid var(--control-border);
  border-radius: 8px;
  background: var(--control-bg);
  color: var(--text-primary);
  cursor: pointer;
}
.drawer-dismiss:hover { background: var(--surface-hover); }
.drawer-main { display: grid; gap: 20px; padding: 22px 24px; }
.drawer-state, .drawer-product, .drawer-progress > div:first-child { display: flex; align-items: center; gap: 12px; justify-content: space-between; min-width: 0; }
.drawer-state { margin: 0; }
.drawer-retry { display: inline-flex; align-items: center; gap: 5px; min-height: 36px; border: 1px solid var(--control-border); border-radius: 6px; color: var(--text-primary); background: var(--control-bg); cursor: pointer; }
.drawer-product { flex-wrap: wrap; overflow-wrap: anywhere; }
.drawer-product h3 { font-size: 19px; }
.drawer-product p { margin: 4px 0 0; color: var(--text-secondary); }
.drawer-status { padding: 4px 8px; border-radius: 6px; background: var(--surface-subtle); color: var(--text-secondary); }
.drawer-progress { display: grid; gap: 8px; }
.drawer-progress strong { color: var(--accent-dark); font-variant-numeric: tabular-nums; }
.drawer-progress-track { height: 8px; background: var(--surface-hover); border-radius: 10px; overflow: hidden; }
.drawer-progress-track span {
  display: block; width: var(--work-order-progress); height: 100%; border-radius: inherit;
  background: var(--accent, #00b889); animation: home-progress-fill 230ms cubic-bezier(.2,.8,.2,1) both;
  transform-origin: left; transition: width 230ms ease;
}
@keyframes home-progress-fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
.drawer-quantities { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1px; background: var(--border-default); border: 1px solid var(--border-default); border-radius: 9px; overflow: hidden; margin: 0; }
.drawer-quantities > div { min-width: 0; padding: 12px 10px; background: var(--surface-subtle); }
.drawer-quantities dd { margin: 4px 0 0; font-size: 21px; font-weight: 650; overflow-wrap: anywhere; }
.drawer-meta { margin: 0; }
.drawer-meta > div { display: grid; grid-template-columns: minmax(70px, 28%) minmax(0, 1fr); gap: 8px; padding: 11px 0; border-bottom: 1px solid var(--border-default); }
.drawer-main dt { color: var(--text-secondary); font-size: 13px; }
.drawer-meta dd { margin: 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.home-order-drawer button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
@media (max-width: 560px) {
  .home-order-drawer { inset: auto 0 0; width: 100%; height: auto; min-height: 55dvh; max-height: 92dvh; border-inline-start: none; border-radius: 14px 14px 0 0; translate: 0 100%; }
  @starting-style { .home-order-drawer[open] { translate: 0 100%; } }
  .drawer-header, .drawer-main { padding: 16px; }
}
@media (prefers-reduced-motion: reduce) {
  .home-order-drawer, .home-order-drawer::backdrop, .drawer-progress-track span { transition: none; animation: none; }
}
</style>

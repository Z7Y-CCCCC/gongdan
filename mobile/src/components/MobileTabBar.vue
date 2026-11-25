<template>
  <!-- #ifdef H5 -->
  <Teleport v-if="visible" to="body">
  <!-- #endif -->
  <view class="mobile-tabbar" role="tablist" aria-label="主导航">
    <button
      v-for="(item, index) in items"
      :key="item.key"
      class="tab-item"
      :class="{ active: current === item.key }"
      role="tab" :aria-label="item.label" :aria-selected="current === item.key"
      :tabindex="current === item.key ? 0 : -1"
      @keydown="moveFocus($event, index)"
      @click="open(item.path)"
    >
      <uni-icons :type="item.icon" size="21" :color="current === item.key ? '#00a982' : '#64756d'" aria-hidden="true" />
      <text>{{ item.label }}</text>
    </button>
  </view>
  <!-- #ifdef H5 -->
  </Teleport>
  <!-- #endif -->
</template>

<script setup lang="ts">
import { navigatePage } from '../utils/navigation'
// #ifdef H5
import { onActivated, onDeactivated, onMounted, ref } from 'vue'
// #endif
const props = defineProps<{ current: string }>()

const items = [
  { key: 'orders', label: '工单', icon: 'compose', path: '/pages/work-orders/index' },
  { key: 'tasks', label: '任务', icon: 'list', path: '/pages/tasks/index' },
  { key: 'home', label: '报工', icon: 'plus-filled', path: '/pages/home/index' },
  { key: 'reports', label: '记录', icon: 'bars', path: '/pages/report-list/index' },
  { key: 'board', label: '看板', icon: 'map', path: '/pages/board/index' }
]

// #ifdef H5
function isCurrentTab() {
  if (typeof window === 'undefined') return true
  const item = items.find(item => item.key === props.current)
  return Boolean(item && window.location.hash.split('?')[0] === `#${item.path}`)
}
const visible = ref(isCurrentTab())
// Keep the bar outside the transformed uni page, but never leave a cached
// or late-mounted inactive page's teleported bar in the document body.
onMounted(() => { visible.value = isCurrentTab() })
onActivated(() => { visible.value = isCurrentTab() })
onDeactivated(() => { visible.value = false })
// #endif

function open(path: string) {
  navigatePage(path)
}
function moveFocus(event: KeyboardEvent, index: number) {
  let target: number
  if (event.key === 'ArrowRight') target = (index + 1) % items.length
  else if (event.key === 'ArrowLeft') target = (index + items.length - 1) % items.length
  else if (event.key === 'Home') target = 0
  else if (event.key === 'End') target = items.length - 1
  else return
  event.preventDefault()
  const buttons = (event.currentTarget as HTMLElement)?.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')
  buttons?.[target]?.focus()
}
</script>

<style scoped>
.mobile-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  height: calc(104rpx + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  background: var(--mobile-surface, #fff);
  border-top: 1rpx solid var(--mobile-border, #e8eceb);
  box-sizing: border-box;
}

.tab-item {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  color: var(--mobile-muted, #64756d);
  font-size: 20rpx;
  position: relative;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  background: transparent;
  border-radius: 0;
  line-height: 1.2;
}

.tab-item.active {
  color: var(--mobile-link, #00856b);
}

.tab-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 36rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: #00a982;
  opacity: 0;
  transform: translateX(-50%) scaleX(0.4);
  transition: opacity 180ms ease, transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.tab-item.active::before {
  opacity: 1;
  transform: translateX(-50%) scaleX(1);
}
</style>

<script setup lang="ts">
/**
 * HomeDashboardPage - 主页仪表盘
 *
 * 管理员登录后默认展示的主页，包含工序任务卡片区、工单进度报表、绩效指标、生产实时播报四个区块。
 * 通过 provide/inject 将 homeRange 共享给 ProcessTaskSection 和 PerformanceSection。
 *
 * Requirements: 2.1-2.4, 11.1-11.3
 */
import { provide, ref, computed } from 'vue'
import { useAuth } from '../composables/useAuth'
import { useHomeDashboardErrorHandler } from '../composables/useHomeDashboardErrorHandler'
import type { HomeRange } from '../api'
import ProcessTaskSection from '../components/ProcessTaskSection.vue'
import WorkOrderProgressSection from '../components/WorkOrderProgressSection.vue'
import PerformanceSection from '../components/PerformanceSection.vue'
import LiveBroadcastSection from '../components/LiveBroadcastSection.vue'

const props = defineProps<{
  token: string
}>()

const { currentUser } = useAuth()
const { forbidden, forbiddenMessage } = useHomeDashboardErrorHandler()

// Time range shared with child components (ProcessTaskSection writes, PerformanceSection reads)
const range = ref<HomeRange>('today')
provide('homeRange', range)

// Display name: use name field, fallback to username if name is empty/null
const displayName = computed(() => {
  if (!currentUser.value) return ''
  const name = currentUser.value.name
  if (name && name.trim()) return name
  return currentUser.value.username
})

// Avatar error handling: hide entire user area if avatar fails to load
const avatarError = ref(false)
function onAvatarError() {
  avatarError.value = true
}

// Handle "view work order" events from child sections
function handleViewWorkOrder(workOrderId: number) {
  // Navigate to work order detail page
  // In the current architecture, this would switch activeMainMenu to 'production'
  // and open the work order detail.
  void workOrderId
}
</script>

<template>
  <div class="home-dashboard">
    <!-- Top Bar -->
    <header class="home-topbar">
      <div class="home-topbar-left">
        <h1 class="home-topbar-title">主页</h1>
      </div>
      <div v-if="currentUser && !avatarError" class="home-topbar-right">
        <img
          src="/avatar-placeholder.svg"
          alt="用户头像"
          class="home-topbar-avatar"
          @error="onAvatarError"
        />
        <span class="home-topbar-username">{{ displayName }}</span>
      </div>
    </header>

    <!-- 403 Forbidden state -->
    <div v-if="forbidden" class="home-forbidden">
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
  background-color: #f5f5f5;
  display: flex;
  flex-direction: column;
}

/* Top Bar */
.home-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
}

.home-topbar-left {
  display: flex;
  align-items: center;
}

.home-topbar-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.home-topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.home-topbar-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.home-topbar-username {
  font-size: 14px;
  color: #333;
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
  font-size: 16px;
  color: #999;
}

/* Main Content */
.home-content {
  flex: 1;
  min-width: 0;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Section cards */
.home-section {
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
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
  gap: 20px;
  flex: 1;
}

/* Right column stacks performance + broadcast */
.home-right-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 20px;
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

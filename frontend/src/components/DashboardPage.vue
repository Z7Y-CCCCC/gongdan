<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { fetchWorkOrders, fetchReports } from '../api'
import { useAuth } from '../composables/useAuth'

const { token } = useAuth()

const emit = defineEmits<{
  (e: 'exit'): void
}>()

// ======== 车间看板数据 ========
const isDashboardFullscreen = ref(false)
const dashboardTime = ref(new Date().toLocaleString('zh-CN'))
const currentMonth = ref(new Date().getFullYear() + '.' + (new Date().getMonth() + 1))

setInterval(() => { dashboardTime.value = new Date().toLocaleString('zh-CN') }, 60000)

function toggleDashboardFullscreen() {
  const el = document.querySelector('.dashboard-fullscreen') as HTMLElement
  if (!el) return
  if (!isDashboardFullscreen.value) {
    el.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
  isDashboardFullscreen.value = !isDashboardFullscreen.value
}

const exitDashboard = () => {
  if (isDashboardFullscreen.value) {
    document.exitFullscreen?.()
    isDashboardFullscreen.value = false
  }
  emit('exit')
}

const dashboardKpi = reactive({
  planCount: 0,
  delayedOrders: 0,
  delayedTasks: 0,
  todayDone: 0,
  todayTasks: 0,
  todayOutput: 0,
  defectRate: 0
})

const barData = ref<Array<{ day: number; done: number; output: number; defect: number }>>([])

const perfData = ref<Array<{ name: string; value: number }>>([])
const perfMax = computed(() => Math.max(1, ...perfData.value.map(p => p.value)))

const defectTrend = ref<Array<{ day: number; count: number; rate: number }>>([])

const pieData = ref<Array<{ name: string; color: string; count: number; pct: string }>>([])

const scrollMessages = ref<Array<{ time: string; text: string }>>([])

const dashboardReports = ref<any[]>([])

const displayReports = computed(() => {
  if (dashboardReports.value && dashboardReports.value.length > 0) {
    return dashboardReports.value.slice(0, 5)
  }
  return []
})

const tableData = ref<Array<{ code: string; product: string; qty: number; customer: string; urgent: boolean; progress: number }>>([])

const taskProgressData = ref<Array<{ process: string; taskCount: number; planned: number; good: number; defect: number; remaining: number }>>([])

function workOrderProgress(wo: any) {
  if (typeof wo.progressPercent === 'number') return Math.max(0, Math.min(100, Math.round(wo.progressPercent)))
  const planned = Number(wo.plannedQuantity || 0)
  if (planned <= 0) return 0
  return Math.max(0, Math.min(100, Math.round(((wo.goodQuantity || 0) + (wo.defectQuantity || 0)) / planned * 100)))
}

function isClosedStatus(status: string | undefined) {
  return ['COMPLETED', 'CANCELED', 'CANCELLED', '已完成', '已取消'].includes(status || '')
}

const loadDashboardData = async () => {
  try {
    const t = token.value
    if (!t) return

    // 1. 获取真实工单数据
    const woResponse = await fetchWorkOrders(t, { page: 1, size: 50 })
    const realWorkOrders = woResponse.items || []
    
    // 更新工单概览表格
    tableData.value = realWorkOrders.map(wo => ({
      code: wo.code,
      product: wo.productName || '未知产品',
      qty: wo.plannedQuantity || 0,
      customer: wo.categoryName || '-',
      urgent: wo.priority === 'URGENT',
      progress: workOrderProgress(wo)
    }))

    // 简单的统计聚合
    const totalPlan = realWorkOrders.reduce((sum, wo) => sum + (wo.plannedQuantity || 0), 0)
    dashboardKpi.planCount = totalPlan
    dashboardKpi.todayTasks = realWorkOrders.filter(wo => !isClosedStatus(wo.status)).length
    dashboardKpi.delayedOrders = realWorkOrders.filter(wo =>
      Boolean(wo.planEnd) && !isClosedStatus(wo.status) && new Date(wo.planEnd as string).getTime() < Date.now()
    ).length

    // 2. 获取报工记录，聚合任务执行进度
    const reportRes = await fetchReports(t, { page: 1, size: 200 })
    const reports = reportRes.items || []
    dashboardReports.value = reports.map(r => ({
      ...r,
      createdAt: r.createdAt ? r.createdAt.replace('T', ' ').substring(5, 16) : ''
    }))

    const today = new Date().toISOString().slice(0, 10)
    const todayReports = reports.filter(r => (r.createdAt || '').startsWith(today))
    const totalGood = reports.reduce((sum, r) => sum + (r.goodQuantity || 0), 0)
    const totalDefect = reports.reduce((sum, r) => sum + (r.defectQuantity || 0), 0)
    dashboardKpi.todayDone = todayReports.length
    dashboardKpi.todayOutput = todayReports.reduce((sum, r) => sum + (r.goodQuantity || 0), 0)
    dashboardKpi.defectRate = totalGood + totalDefect > 0 ? Number((totalDefect / (totalGood + totalDefect) * 100).toFixed(2)) : 0

    const dailyMap = new Map<string, { day: number; done: number; output: number; defect: number }>()
    const reporterMap = new Map<string, number>()
    const defectMap = new Map<string, number>()
    reports.forEach(r => {
      const dateKey = (r.createdAt || '').slice(0, 10)
      if (dateKey) {
        const day = Number(dateKey.slice(8, 10))
        const item = dailyMap.get(dateKey) || { day, done: 0, output: 0, defect: 0 }
        item.done += 1
        item.output += (r.goodQuantity || 0)
        item.defect += (r.defectQuantity || 0)
        dailyMap.set(dateKey, item)
      }
      reporterMap.set(r.reporterName || '未知员工', (reporterMap.get(r.reporterName || '未知员工') || 0) + (r.goodQuantity || 0))
      const defectName = r.defectItemName || (r.defectQuantity > 0 ? '未分类不良' : '')
      if (defectName) defectMap.set(defectName, (defectMap.get(defectName) || 0) + (r.defectQuantity || 0))
    })
    barData.value = Array.from(dailyMap.entries()).sort(([a], [b]) => a.localeCompare(b)).slice(-10).map(([, item]) => item)
    perfData.value = Array.from(reporterMap.entries())
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 5)
    const totalDefectItems = Array.from(defectMap.values()).reduce((sum, count) => sum + count, 0)
    const colors = ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#6b7280']
    pieData.value = Array.from(defectMap.entries()).map(([name, count], index) => ({
      name,
      color: colors[index % colors.length],
      count,
      pct: totalDefectItems > 0 ? (count / totalDefectItems * 100).toFixed(2) + '%' : '0.00%'
    }))
    defectTrend.value = barData.value.map(item => ({
      day: item.day,
      count: item.defect,
      rate: item.output + item.defect > 0 ? Number((item.defect / (item.output + item.defect) * 100).toFixed(2)) : 0
    }))
    scrollMessages.value = reports.slice(0, 8).map(r => ({
      time: (r.createdAt || '').replace('T', ' ').substring(0, 19),
      text: `${r.reporterName || '-'} | ${r.processName || '-'} | ${r.productName || '-'} | 良品${r.goodQuantity || 0} | 不良${r.defectQuantity || 0}`
    }))

    const processMap: Record<string, any> = {}
    realWorkOrders.forEach(wo => {
      ;(wo.tasks || []).forEach((task: any) => {
      const pName = task.processName || '未知工序'
      if (!processMap[pName]) {
        processMap[pName] = { process: pName, taskCount: 0, planned: 0, good: 0, defect: 0, remaining: 0 }
      }
      processMap[pName].taskCount += 1
      processMap[pName].planned += (task.plannedQuantity || 0)
      processMap[pName].good += (task.completedQuantity || 0)
      processMap[pName].defect += (task.defectQuantity || 0)
      processMap[pName].remaining = Math.max(0, processMap[pName].planned - processMap[pName].good)
      })
    })
    taskProgressData.value = Object.values(processMap)
    dashboardKpi.delayedTasks = taskProgressData.value.filter(item => item.remaining > 0).length

  } catch (e) {
    console.error('Failed to load dashboard data:', e)
  }
}

// 组件挂载时加载数据
loadDashboardData()

defineExpose({ loadDashboardData })
</script>

<template>
  <section class="dashboard-fullscreen">
    <div class="db-header">
      <span class="db-time">最近更新: {{ dashboardTime }}</span>
      <h1 class="db-title">小工单数据看板</h1>
      <div style="display: flex; gap: 12px;">
        <button class="db-fullscreen-btn" @click="toggleDashboardFullscreen">{{ isDashboardFullscreen ? '退出全屏' : '进入全屏' }}</button>
        <button class="db-fullscreen-btn" @click="exitDashboard">退出看板</button>
      </div>
    </div>

    <div class="db-grid">
      <!-- 左上：工单产出分析 -->
      <div class="db-card db-card-wide">
        <div class="db-card-head">
          <span class="db-card-title">工单产出分析</span>
          <span class="db-card-tag">近10天</span>
        </div>
        <div class="db-chart-area">
          <div class="db-sub-title">工单产出趋势图({{ currentMonth }})</div>
          <div class="db-legend">
            <span class="db-legend-item"><i class="dot cyan"></i>完成工单</span>
            <span class="db-legend-item"><i class="dot blue"></i>工单产出</span>
          </div>
          <div class="db-bar-chart" style="position: relative;">
            <!-- SVG 折线 -->
            <svg viewBox="0 0 100 100" style="position:absolute; width:100%; height:100%; left:0; top:0; pointer-events:none;" preserveAspectRatio="none">
              <polyline 
                fill="none" 
                stroke="#f59e0b" 
                stroke-width="0.6" 
                :points="barData.map((d, i) => `${(i + 0.5) * (100 / barData.length)},${100 - (d.done / 10 * 100)}`).join(' ')"
              />
              <circle 
                v-for="(d, i) in barData" :key="'pt'+i"
                :cx="(i + 0.5) * (100 / barData.length)" 
                :cy="100 - (d.done / 10 * 100)" 
                r="0.8" fill="#f59e0b"
              />
            </svg>

            <div v-for="d in barData" :key="d.day" class="db-bar-group">
              <div class="db-bar-wrapper">
                <div class="db-bar bar-cyan" :style="{ height: d.done * 10 + '%' }"></div>
                <div class="db-bar bar-blue" :style="{ height: (d.output / 1000 * 100) + '%' }"></div>
              </div>
              <span class="db-bar-label">{{ d.day }}</span>
            </div>
          </div>
        </div>
        <div class="db-perf-section">
          <div class="db-sub-title">员工绩效Top5</div>
          <div v-for="p in perfData" :key="p.name" class="db-perf-row">
            <span class="db-perf-name">{{ p.name }}</span>
            <div class="db-perf-bar-bg">
              <div class="db-perf-bar-fill" :style="{ width: (p.value / perfMax * 100) + '%' }"></div>
            </div>
            <span class="db-perf-val">{{ p.value }}</span>
          </div>
        </div>
      </div>

      <!-- 中上：核心指标 -->
      <div class="db-card db-card-center">
        <div class="db-kpi-grid-v4">
          <div class="db-kpi-box-v4">
            <div class="db-kpi-box-v4-title">在制工单数</div>
            <div class="db-kpi-box-v4-value cyan">7</div>
          </div>
          <div class="db-kpi-box-v4">
            <div class="db-kpi-box-v4-title">在制工单计划数</div>
            <div class="db-kpi-box-v4-value cyan">{{ dashboardKpi.planCount }}</div>
          </div>
          <div class="db-kpi-box-v4">
            <div class="db-kpi-box-v4-title">在制任务计划数</div>
            <div class="db-kpi-box-v4-value cyan">24878</div>
          </div>
          <div class="db-kpi-box-v4">
            <div class="db-kpi-box-v4-title">在制任务数</div>
            <div class="db-kpi-box-v4-value cyan">37</div>
          </div>
        </div>

        <div class="db-center-body">
          <!-- 左侧指标面板 -->
          <div class="db-side-panel">
            <div class="db-side-item">
              <span class="db-side-label">在制工单延期数</span>
              <span class="db-side-value red">{{ dashboardKpi.delayedOrders }}</span>
            </div>
            <div class="db-side-item">
              <span class="db-side-label">今日完成工单数</span>
              <span class="db-side-value green">{{ dashboardKpi.todayDone }}</span>
            </div>
            <div class="db-side-item">
              <span class="db-side-label">今日工单产出数</span>
              <span class="db-side-value cyan">{{ dashboardKpi.todayOutput }}</span>
            </div>
          </div>

          <!-- 中间三维工厂渲染区 -->
          <div class="db-factory-center">
            <img src="/factory.png" alt="Factory" style="max-width: 100%; max-height: 180px; object-fit: contain; filter: drop-shadow(0 0 15px rgba(0,200,255,0.3));" />
          </div>

          <!-- 右侧指标面板 -->
          <div class="db-side-panel">
            <div class="db-side-item">
              <span class="db-side-label">在制任务延期数</span>
              <span class="db-side-value red">{{ dashboardKpi.delayedTasks }}</span>
            </div>
            <div class="db-side-item">
              <span class="db-side-label">今日完成任务数</span>
              <span class="db-side-value green">{{ dashboardKpi.todayTasks }}</span>
            </div>
            <div class="db-side-item">
              <span class="db-side-label">今日不良品数</span>
              <span class="db-side-value red">0</span>
            </div>
          </div>
        </div>

        <!-- 报工记录表格 (替代滚动消息: scrollMessages) -->
        <div class="db-scroll-messages" style="max-height: 140px; overflow: hidden; width: 100%; box-sizing: border-box;">
          <table class="db-report-table">
            <thead>
              <tr>
                <th style="width: 23%;">报工创建时间</th>
                <th style="width: 13%;">生产人员</th>
                <th style="width: 16%;">工序名称</th>
                <th style="width: 26%;">产品名称</th>
                <th style="width: 10%;">良品数</th>
                <th style="width: 12%;">不良品数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(rep, ri) in displayReports" :key="ri">
                <td :title="rep.createdAt">{{ rep.createdAt }}</td>
                <td>{{ rep.reporterName }}</td>
                <td>{{ rep.processName }}</td>
                <td :title="rep.productName">{{ rep.productName }}</td>
                <td class="text-cyan">{{ rep.goodQuantity }}</td>
                <td class="text-red">{{ rep.defectQuantity }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 右上：不良品分析 -->
      <div class="db-card db-card-wide">
        <div class="db-card-head">
          <span class="db-card-title">不良品分析</span>
          <span class="db-card-tag">近10天</span>
        </div>
        <div class="db-sub-title">不良品趋势图({{ currentMonth }})</div>
        <div class="db-legend">
          <span class="db-legend-item"><i class="dot orange"></i>不良品数</span>
          <span class="db-legend-item"><i class="dot red"></i>不良品率</span>
        </div>
        <div class="db-defect-chart" style="position: relative;">
          <!-- SVG 折线 -->
          <svg viewBox="0 0 100 100" style="position:absolute; width:100%; height:100%; left:0; top:0; pointer-events:none;" preserveAspectRatio="none">
            <polyline 
              fill="none" 
              stroke="#f59e0b" 
              stroke-width="0.6" 
              :points="defectTrend.map((d, i) => `${(i + 0.5) * (100 / defectTrend.length)},${100 - (d.count / 15 * 100)}`).join(' ')"
            />
            <circle 
              v-for="(d, i) in defectTrend" :key="'dpt'+i"
              :cx="(i + 0.5) * (100 / defectTrend.length)" 
              :cy="100 - (d.count / 15 * 100)" 
              r="0.8" fill="#f59e0b"
            />
          </svg>

          <div v-for="d in defectTrend" :key="d.day" class="db-defect-bar-group">
            <div class="db-defect-bar" :style="{ height: Math.max((d.count / 15 * 100), 4) + '%' }"></div>
            <span class="db-defect-rate">{{ d.rate }}%</span>
            <span class="db-bar-label">{{ d.day }}</span>
          </div>
        </div>
        <div class="db-sub-title" style="margin-top:16px">不良品项分布图</div>
        <div class="db-pie-container">
          <div class="db-pie">
            <div class="db-pie-ring"></div>
          </div>
          <div class="db-pie-legend">
            <div v-for="(item, idx) in pieData" :key="idx" class="db-pie-legend-item">
              <i class="db-pie-dot" :style="{ background: item.color }"></i>
              <span>{{ item.name }}</span>
              <span class="db-pie-pct">{{ item.count }} ({{ item.pct }})</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 左下：工单概览 -->
      <div class="db-card db-card-table" style="overflow-y: hidden; display: flex; flex-direction: column; padding: 0 12px 12px 12px; background: transparent; border: none; box-shadow: none;">
        <div class="db-table-header" style="flex-shrink: 0;">
          <span>工单概览</span>
        </div>
        <table class="db-table-v2">
          <thead>
            <tr>
              <th style="width: 15%;">工单编号 <i class="sort-arrow">▼</i></th>
              <th style="width: 20%;">产品名称 <i class="sort-arrow">▼</i></th>
              <th style="width: 10%;">计划数</th>
              <th style="width: 10%;">客户简称</th>
              <th style="width: 10%;">是否加急</th>
              <th style="width: 35%;">生产进度</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in tableData" :key="ri">
              <td class="td-code" style="width: 15%;">{{ row.code }}</td>
              <td style="width: 20%;">{{ row.product }}</td>
              <td style="width: 10%;">{{ row.qty }}</td>
              <td style="width: 10%;">{{ row.customer }}</td>
              <td style="width: 10%;"><span :class="['db-tag', row.urgent ? 'urgent' : 'normal']">{{ row.urgent ? '加急订单' : '-' }}</span></td>
              <td style="width: 35%;">
                <div class="db-stepper">
                  <div class="db-step" v-for="step in ['下料','冲压','压铸','折弯','冲压','电解','包装']" :key="step">
                    <div class="db-step-circle">0%</div>
                    <div class="db-step-name">{{ step }}</div>
                  </div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 右下：任务执行进度 -->
      <div class="db-card db-card-table" style="overflow-y: hidden; display: flex; flex-direction: column; padding: 0 12px 12px 12px; background: transparent; border: none; box-shadow: none;">
        <div class="db-table-header" style="flex-shrink: 0;">
          <span>任务执行进度</span>
          <span class="db-table-tag">今天</span>
        </div>
        <table class="db-table-v2">
          <thead>
            <tr>
              <th style="width: 16%;">工序名称</th>
              <th style="width: 16%;">生产任务数</th>
              <th style="width: 16%;">计划数</th>
              <th style="width: 16%;">良品数</th>
              <th style="width: 16%;">不良品数</th>
              <th style="width: 16%;">未完成数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in taskProgressData" :key="ri">
              <td style="width: 16%;">{{ row.process }}</td>
              <td style="width: 16%;">{{ row.taskCount }}</td>
              <td style="width: 16%;">{{ row.planned }}</td>
              <td class="td-green" style="width: 16%;">{{ row.good }}</td>
              <td class="td-red" style="width: 16%;">{{ row.defect }}</td>
              <td style="width: 16%;">{{ row.remaining }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

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
  planCount: 5285,
  delayedOrders: 7,
  delayedTasks: 37,
  todayDone: 1,
  todayTasks: 5,
  todayOutput: 250,
  defectRate: 1.96
})

const barData = ref([
  { day: 12, done: 3, output: 800 }, { day: 13, done: 5, output: 600 },
  { day: 14, done: 8, output: 1000 }, { day: 15, done: 4, output: 700 },
  { day: 16, done: 6, output: 400 }, { day: 17, done: 2, output: 350 },
  { day: 18, done: 7, output: 900 }, { day: 19, done: 5, output: 500 },
  { day: 20, done: 3, output: 850 }, { day: 21, done: 4, output: 650 }
])

const perfData = ref([
  { name: '虞竹', value: 1621 },
  { name: '张俊健', value: 1224 },
  { name: '段超', value: 863 },
  { name: '乔峰', value: 350 },
  { name: '慕容复', value: 300 }
])
const perfMax = computed(() => Math.max(...perfData.value.map(p => p.value)))

const defectTrend = ref([
  { day: 12, count: 5, rate: 0.8 }, { day: 13, count: 3, rate: 0.5 },
  { day: 14, count: 8, rate: 1.2 }, { day: 15, count: 2, rate: 0.3 },
  { day: 16, count: 6, rate: 0.9 }, { day: 17, count: 4, rate: 0.6 },
  { day: 18, count: 10, rate: 1.5 }, { day: 19, count: 7, rate: 1.1 },
  { day: 20, count: 3, rate: 0.4 }, { day: 21, count: 5, rate: 0.8 }
])

const pieData = ref([
  { name: '针扎', color: '#3b82f6', count: 30, pct: '37.50%' },
  { name: '割纹', color: '#f59e0b', count: 10, pct: '12.50%' },
  { name: '划边', color: '#10b981', count: 9, pct: '11.25%' },
  { name: '飞溅', color: '#ef4444', count: 10, pct: '12.50%' },
  { name: '气泡', color: '#8b5cf6', count: 10, pct: '12.50%' },
  { name: '其他', color: '#6b7280', count: 11, pct: '13.75%' }
])

const scrollMessages = ref([
  { time: '2023-04-21 11:30:09', text: '段超 | 包装 | 02|宝慧轴承S6... | 良品150 | 不良品5' },
  { time: '2023-04-20 12:16:31', text: '虞竹 | 包装 | 04|智能wifi-7组... | 05|18型支承... | 良品600' },
  { time: '2023-04-19 11:46:52', text: '张俊健 | 包装仓 | T-1白/小T-shirt | 良品100' },
  { time: '2023-04-19 11:39:49', text: '张俊健 | 驱动包 | T-1白/小T-shirt | 不良品5' }
])

const tableData = ref([
  { code: 'GD20230401-6', product: '股骨头髁关节假体', qty: 234, customer: '厦门市医院', urgent: false, progress: 75 },
  { code: 'GD20230401-5', product: '美标维纶绞片M5...', qty: 133, customer: '宁德时代', urgent: true, progress: 60 },
  { code: 'GD20230401-4', product: '智能wifi-7组座', qty: 1000, customer: '格力电器', urgent: false, progress: 45 },
  { code: 'GD20230401-3', product: '弹簧座', qty: 1000, customer: '华为手机', urgent: false, progress: 30 },
  { code: 'GD20230401-2', product: '18型支承轴', qty: 600, customer: '花花公子', urgent: false, progress: 85 },
  { code: 'GD20230401-1', product: '黑湖精密模具', qty: 2018, customer: '上汽大众', urgent: false, progress: 90 },
  { code: 'GD20220120', product: '结构钛铝板', qty: 300, customer: '特斯拉', urgent: false, progress: 55 }
])

const taskProgressData = ref([
  { process: '注塑', taskCount: 4, planned: 1467, good: 1142, defect: 6, remaining: 325 },
  { process: '外协 电镀', taskCount: 3, planned: 517, good: 229, defect: 0, remaining: 288 },
  { process: 'CNC', taskCount: 4, planned: 2985, good: 2021, defect: 7, remaining: 964 },
  { process: '包装', taskCount: 4, planned: 5235, good: 2830, defect: 63, remaining: 2405 },
  { process: '折弯', taskCount: 4, planned: 2535, good: 2261, defect: 8, remaining: 274 },
  { process: '机加', taskCount: 5, planned: 3485, good: 2652, defect: 8, remaining: 833 },
  { process: '下料', taskCount: 7, planned: 4235, good: 4279, defect: 52, remaining: -44 },
  { process: '冲压', taskCount: 7, planned: 4785, good: 3534, defect: 19, remaining: 1251 }
])

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
      urgent: false, // 假设没有此字段
      progress: Math.floor(Math.random() * 100) // 模拟进度
    }))

    // 简单的统计聚合
    const totalPlan = realWorkOrders.reduce((sum, wo) => sum + (wo.plannedQuantity || 0), 0)
    dashboardKpi.planCount = totalPlan

    // 2. 获取报工记录，聚合任务执行进度
    const reportRes = await fetchReports(t, { page: 1, size: 200 })
    const reports = reportRes.items || []
    
    const processMap: Record<string, any> = {}
    reports.forEach(r => {
      const pName = r.processName || '未知工序'
      if (!processMap[pName]) {
        processMap[pName] = { process: pName, taskCount: 0, planned: 0, good: 0, defect: 0, remaining: 0 }
      }
      processMap[pName].taskCount += 1
      processMap[pName].good += (r.goodQuantity || 0)
      processMap[pName].defect += (r.defectQuantity || 0)
      // 真实业务中计划数应该是工单任务里的计划数
      processMap[pName].planned += (r.goodQuantity || 0) + (r.defectQuantity || 0) + 100
      processMap[pName].remaining = processMap[pName].planned - processMap[pName].good
    })
    
    if (Object.keys(processMap).length > 0) {
      taskProgressData.value = Object.values(processMap)
    }

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
            <svg style="position:absolute; width:100%; height:100%; left:0; top:0; pointer-events:none;" preserveAspectRatio="none">
              <polyline 
                fill="none" 
                stroke="#f59e0b" 
                stroke-width="2" 
                :points="barData.map((d, i) => `${(i + 0.5) * (100 / barData.length)}%,${100 - (d.done / 10 * 100)}%`).join(' ')"
              />
              <circle 
                v-for="(d, i) in barData" :key="'pt'+i"
                :cx="`${(i + 0.5) * (100 / barData.length)}%`" 
                :cy="`${100 - (d.done / 10 * 100)}%`" 
                r="3" fill="#041228" stroke="#f59e0b" stroke-width="1.5"
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
        <div class="db-kpi-grid">
          <!-- 第1框 -->
          <div class="db-kpi-box">
            <div class="db-kpi-box-title">在制工单计划数</div>
            <div class="db-kpi-value cyan">{{ dashboardKpi.planCount }}</div>
            <div class="db-kpi-box-sub mt-2">
              <span>在制工单延期数</span>
              <span class="red-text">{{ dashboardKpi.delayedOrders }}</span>
            </div>
            <div class="db-kpi-box-sub">
              <span>今日完成工单数</span>
              <span class="green-text">{{ dashboardKpi.todayDone }}</span>
            </div>
            <div class="db-kpi-box-sub">
              <span>今日工单产出数</span>
              <span class="cyan-text">{{ dashboardKpi.todayOutput }}</span>
            </div>
          </div>
          
          <!-- 第2框 -->
          <div class="db-kpi-box">
            <div style="display:flex; justify-content:space-between;">
              <div>
                <div class="db-kpi-box-title">在制工单数</div>
                <div class="db-kpi-value">7</div>
              </div>
              <div>
                <div class="db-kpi-box-title">在制任务数</div>
                <div class="db-kpi-value">37</div>
              </div>
            </div>
          </div>

          <!-- 第3框 -->
          <div class="db-kpi-box">
            <div class="db-kpi-box-title">在制任务计划数</div>
            <div class="db-kpi-value">24878</div>
            <div class="db-kpi-box-sub mt-2">
              <span>在制任务延期数</span>
              <span class="red-text">{{ dashboardKpi.delayedTasks }}</span>
            </div>
            <div class="db-kpi-box-sub">
              <span>今日完成任务数</span>
              <span class="green-text">{{ dashboardKpi.todayTasks }}</span>
            </div>
            <div class="db-kpi-box-sub">
              <span>今日不良品率</span>
              <span class="red-text">{{ dashboardKpi.defectRate }}%</span>
            </div>
          </div>
        </div>
        
        <div class="db-factory-visual">
          <img src="/factory.png" alt="Factory" style="max-width: 100%; max-height: 180px; object-fit: contain; filter: drop-shadow(0 0 15px rgba(0,200,255,0.3));" />
        </div>
        <!-- 滚动消息 -->
        <div class="db-scroll-messages">
          <div v-for="(msg, mi) in scrollMessages" :key="mi" class="db-msg-row">
            <span class="db-msg-time">{{ msg.time }}</span>
            <span class="db-msg-text">{{ msg.text }}</span>
          </div>
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
          <svg style="position:absolute; width:100%; height:100%; left:0; top:0; pointer-events:none;" preserveAspectRatio="none">
            <polyline 
              fill="none" 
              stroke="#3b82f6" 
              stroke-width="2" 
              :points="defectTrend.map((d, i) => `${(i + 0.5) * (100 / defectTrend.length)}%,${100 - (d.count / 15 * 100)}%`).join(' ')"
            />
            <circle 
              v-for="(d, i) in defectTrend" :key="'dpt'+i"
              :cx="`${(i + 0.5) * (100 / defectTrend.length)}%`" 
              :cy="`${100 - (d.count / 15 * 100)}%`" 
              r="3" fill="#041228" stroke="#3b82f6" stroke-width="1.5"
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

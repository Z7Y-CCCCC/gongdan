<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, watchEffect } from 'vue'
import QRCode from 'qrcode'
import {
  createUser,
  exportUsersCsv,
  fetchUsers,
  importUsersCsv,
  login,
  resetUserPassword,
  unbindUserPhone,
  updateUser,
  updateUserPermission,
  updateUserStatus,
  fetchDepartments,
  fetchCurrentFactory,
  createDepartment,
  updateDepartment,
  deleteDepartment,
  fetchUnits,
  createUnit,
  updateUnit,
  deleteUnit,
  fetchDefects,
  createDefect,
  updateDefect,
  deleteDefect,
  fetchProcesses,
  createProcess,
  updateProcess,
  deleteProcess,
  fetchProcessRoutes,
  createProcessRoute,
  updateProcessRoute,
  deleteProcessRoute,
  type CreateUserPayload,
  type LoginResponse,
  type Role,
  type UpdateUserPayload,
  type UserInfo,
  type DepartmentInfo,
  type CreateDepartmentPayload,
  type UpdateDepartmentPayload,
  type UnitInfo,
  type UnitPayload,
  type DefectInfo,
  type DefectPayload,
  type ProcessInfo,
  type ProcessPayload,
  type ProcessRouteInfo,
  type ProcessRoutePayload,
  phonePasswordLogin,
  phoneSmsLogin,
  sendSmsCode,
  selectFactory,
  type PhoneLoginResult,
  type FactoryOptionItem,
  fetchWorkOrders,
  fetchReports,
  fetchSalesOrders,
  fetchPluginRuntimeManifest,
  fetchNotifications,
  fetchNotificationUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
  type NotificationInfo,
  type PluginRuntimeManifestInfo
} from './api'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'
import LoadingProgressOverlay from './components/LoadingProgressOverlay.vue'
import WorkspaceTabs, { type WorkspaceTabItem } from './components/WorkspaceTabs.vue'
import UiIcon from './components/UiIcon.vue'
import { useAuth } from './composables/useAuth'
import { buildWorkshopDashboard } from './utils/workshopDashboard'

const ProductDefinitionPage = defineAsyncComponent(() => import('./components/ProductDefinitionPage.vue'))
const BomPage = defineAsyncComponent(() => import('./components/BomPage.vue'))
const SalesOrderPage = defineAsyncComponent(() => import('./components/SalesOrderPage.vue'))
const WorkOrderPage = defineAsyncComponent(() => import('./components/WorkOrderPage.vue'))
const TaskPage = defineAsyncComponent(() => import('./components/TaskPage.vue'))
const CustomWorkOrderFieldsPage = defineAsyncComponent(() => import('./components/CustomWorkOrderFieldsPage.vue'))
const ReportApprovalPage = defineAsyncComponent(() => import('./components/ReportApprovalPage.vue'))
const BaseDataPage = defineAsyncComponent(() => import('./components/BaseDataPage.vue'))
const UserManagementPage = defineAsyncComponent(() => import('./components/UserManagementPage.vue'))
const DepartmentManagementPage = defineAsyncComponent(() => import('./components/DepartmentManagementPage.vue'))
const FactorySettingsPage = defineAsyncComponent(() => import('./components/FactorySettingsPage.vue'))
const HomeDashboardPage = defineAsyncComponent(() => import('./views/HomeDashboardPage.vue'))
const InboundReceiptPage = defineAsyncComponent(() => import('./components/InboundReceiptPage.vue'))
const OutboundReceiptPage = defineAsyncComponent(() => import('./components/OutboundReceiptPage.vue'))
const InventoryBalancePage = defineAsyncComponent(() => import('./components/InventoryBalancePage.vue'))
const InventoryDetailPage = defineAsyncComponent(() => import('./components/InventoryDetailPage.vue'))
const InventorySummaryPage = defineAsyncComponent(() => import('./components/InventorySummaryPage.vue'))
const CustomReceiptFieldsPage = defineAsyncComponent(() => import('./components/CustomReceiptFieldsPage.vue'))
const SuperAdminAuthCodesPage = defineAsyncComponent(() => import('./components/SuperAdminAuthCodesPage.vue'))
const BindFactoryPage = defineAsyncComponent(() => import('./components/BindFactoryPage.vue'))
const WarehousePage = defineAsyncComponent(() => import('./components/WarehousePage.vue'))
const SupplierPage = defineAsyncComponent(() => import('./components/SupplierPage.vue'))
const CustomerPage = defineAsyncComponent(() => import('./components/CustomerPage.vue'))
const ReportCenterPage = defineAsyncComponent(() => import('./components/ReportCenterPage.vue'))
const AnalyticsReportPage = defineAsyncComponent(() => import('./components/AnalyticsReportPage.vue'))
const AdvancedOperationsPage = defineAsyncComponent(() => import('./components/AdvancedOperationsPage.vue'))
const OnlineUsersPage = defineAsyncComponent(() => import('./components/OnlineUsersPage.vue'))
const CacheMonitorPage = defineAsyncComponent(() => import('./components/CacheMonitorPage.vue'))
const CacheListPage = defineAsyncComponent(() => import('./components/CacheListPage.vue'))
const InternalChatPage = defineAsyncComponent(() => import('./components/InternalChatPage.vue'))
const NotificationCenterPage = defineAsyncComponent(() => import('./components/NotificationCenterPage.vue'))
const PluginCenterPage = defineAsyncComponent(() => import('./components/PluginCenterPage.vue'))
const ExternalDataSourcePage = defineAsyncComponent(() => import('./components/ExternalDataSourcePage.vue'))
const PluginRuntimePage = defineAsyncComponent(() => import('./components/PluginRuntimePage.vue'))

const sharedAuth = useAuth()

const { token, currentUser, isSuperAdmin, currentFactoryCode, isLoggedIn } = sharedAuth
const isInternalChatWindow = ref(['#/message-center', '#/im-chat'].includes(window.location.hash))
const users = ref<UserInfo[]>([])
const loading = ref(false)
const usersLoading = ref(false)
const errorMessage = ref('')
let errorTimer: ReturnType<typeof setTimeout> | null = null
watch(errorMessage, (v) => {
  if (errorTimer) clearTimeout(errorTimer)
  if (v) errorTimer = setTimeout(() => { errorMessage.value = '' }, 5000)
})
const showCreateModal = ref(false)
const showEditModal = ref(false)
const showPermissionModal = ref(false)
const selectedUserIds = ref<number[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const importLogs = ref<{ time: string; fileName: string; total: number; success: number; fail: number; errors: string[] }[]>([])
const importResult = ref<{ total: number; success: number; fail: number; errors: string[] } | null>(null)
const showImportLogModal = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(10)
const confirmState = reactive({
  show: false,
  title: '',
  message: '',
  action: () => {}
})
const successUser = ref<UserInfo | null>(null)
const successUserPassword = ref('')
const actionMessage = ref('')
let actionTimer: ReturnType<typeof setTimeout> | null = null
watch(actionMessage, (v) => {
  if (actionTimer) clearTimeout(actionTimer)
  if (v) actionTimer = setTimeout(() => { actionMessage.value = '' }, 3000)
})
const editingUser = ref<UserInfo | null>(null)
const permissionUser = ref<UserInfo | null>(null)
const permissionRole = ref<Role>('ADMIN')
const isSidebarCollapsed = ref(false)
const showResetPasswordModal = ref(false)
const resetPasswordUser = ref<UserInfo | null>(null)
const resetPasswordForm = reactive({
  password: '',
  confirmPassword: ''
})
const showUserDropdown = ref(false)
const showNotificationDropdown = ref(false)
const notificationsLoading = ref(false)
const notificationError = ref('')
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const activeConfigMenu = ref('department') // default to department based on user request
const activeMainMenu = ref('home') // 'home' | 'sysconfig' | 'basedata' | 'super-admin' | ...
const activeBasedataMenu = ref('unit') // default to unit
const activeProductionMenu = ref('workorder')
const activeCustomMenu = ref('workorder')
const activeInventoryMenu = ref('inbound') // 'inbound' | 'outbound' | 'detail' | 'balance' | 'summary'
const activeMonitorMenu = ref<'online' | 'cache' | 'cacheList'>('online')
const activePluginKey = ref('')
const runtimePlugins = ref<PluginRuntimeManifestInfo[]>([])
const selectedRuntimePlugin = computed(() => runtimePlugins.value.find(item => item.pluginKey === activePluginKey.value) || null)
const activePageCacheKey = computed(() => {
  if (activeMainMenu.value === 'production') return `production-${activeProductionMenu.value}`
  if (activeMainMenu.value === 'inventory') return `inventory-${activeInventoryMenu.value}`
  if (activeMainMenu.value === 'basedata') return `basedata-${activeBasedataMenu.value}`
  if (activeMainMenu.value === 'custom') return `custom-${activeCustomMenu.value}`
  if (activeMainMenu.value === 'sysconfig') return `sysconfig-${activeConfigMenu.value}`
  if (activeMainMenu.value === 'monitor') return `monitor-${activeMonitorMenu.value}`
  if (activeMainMenu.value === 'plugin') return `plugin-${activePluginKey.value}`
  return activeMainMenu.value
})
const pageMotionStage = ref<HTMLElement | null>(null)

type WorkspaceLoadingStageStatus = 'pending' | 'loading' | 'done' | 'error'
interface WorkspaceLoadingStage {
  key: string
  label: string
  detail: string
  status: WorkspaceLoadingStageStatus
}

function createWorkspaceLoadingStages(): WorkspaceLoadingStage[] {
  return [
    { key: 'restore-session', label: '恢复登录会话', detail: '读取本地登录状态', status: 'pending' },
    { key: 'load-users', label: '加载用户数据', detail: '等待接口响应', status: 'pending' },
    { key: 'load-departments', label: '加载部门数据', detail: '等待接口响应', status: 'pending' },
    { key: 'load-units', label: '加载单位数据', detail: '等待接口响应', status: 'pending' },
    { key: 'load-defects', label: '加载不良品项', detail: '等待接口响应', status: 'pending' },
    { key: 'load-processes', label: '加载工序数据', detail: '等待接口响应', status: 'pending' },
    { key: 'load-routes', label: '加载工艺路线', detail: '等待接口响应', status: 'pending' },
    { key: 'load-notifications', label: '同步工作台通知', detail: '等待接口响应', status: 'pending' },
    { key: 'load-plugin-manifest', label: '加载插件运行清单', detail: '等待接口响应', status: 'pending' },
    { key: 'prepare-workspace', label: '准备工作区', detail: '等待页面渲染', status: 'pending' }
  ]
}

const workspaceLoadingStages = ref<WorkspaceLoadingStage[]>(createWorkspaceLoadingStages())
const workspaceLoadingVisible = ref(Boolean(token.value && currentUser.value && !isInternalChatWindow.value))
const workspaceLoadingProgress = ref(workspaceLoadingVisible.value ? 4 : 0)
const workspaceLoadingStage = ref(workspaceLoadingVisible.value ? '正在恢复登录会话' : '')
const workspaceLoadingError = ref('')
let workspaceBootstrapRun = 0

type WorkspaceTab = WorkspaceTabItem & {
  main: string
  config?: string
  basedata?: string
  production?: string
  inventory?: string
  custom?: string
  monitor?: string
  plugin?: string
}

const workspaceTabs = ref<WorkspaceTab[]>([
  { id: 'home', label: '分析页', main: 'home', closable: false, pinned: true }
])
const activeWorkspaceTabId = ref('home')
const tabContextMenu = reactive({ show: false, x: 0, y: 0, tabId: '' })
const showChromeQuickMenu = ref(false)
const showQuickApps = ref(false)
const showLanguageMenu = ref(false)
const showTimezoneDialog = ref(false)
const showWorkspaceSearch = ref(false)
const workspaceSearchInput = ref<HTMLInputElement | null>(null)
const isWorkspaceMaximized = ref(false)
const isAppFullscreen = ref(false)
let nativeFullscreenActive = false
const isDarkTheme = ref(localStorage.getItem('heihu_theme') === 'dark')
const timezoneOptions = [
  { value: 'America/New_York', label: 'America/New_York (GMT-5)' },
  { value: 'Europe/London', label: 'Europe/London (GMT0)' },
  { value: 'Asia/Shanghai', label: 'Asia/Shanghai (GMT+8)' },
  { value: 'Asia/Tokyo', label: 'Asia/Tokyo (GMT+9)' },
  { value: 'Asia/Seoul', label: 'Asia/Seoul (GMT+9)' }
]
const selectedTimezone = ref(localStorage.getItem('heihu_timezone') || 'Asia/Shanghai')
const timezoneDraft = ref(selectedTimezone.value)

watch(isDarkTheme, value => {
  localStorage.setItem('heihu_theme', value ? 'dark' : 'light')
  document.documentElement.classList.toggle('dark', value)
}, { immediate: true })

const workspaceLabels: Record<string, string> = {
  home: '分析页',
  dashboard: '车间看板',
  advanced: '高级运营',
  report: '生产报表',
  analytics: '分析报表',
  'production-sales': '销售订单',
  'production-workorder': '工单',
  'production-task': '任务',
  'production-reporting': '报工工作台',
  'inventory-inbound': '入库单',
  'inventory-outbound': '出库单',
  'inventory-detail': '库存收发明细',
  'inventory-balance': '库存余额',
  'inventory-summary': '进销存汇总',
  'basedata-product': '产品定义',
  'basedata-bom': '物料清单',
  'basedata-unit': '单位',
  'basedata-defect': '不良品项',
  'basedata-process': '工序',
  'basedata-route': '工艺路线',
  'basedata-warehouse': '仓库',
  'basedata-supplier': '供应商',
  'basedata-customer': '客户',
  'custom-workorder': '工单字段',
  'custom-reporting': '报工字段',
  'custom-receipt': '出入库单字段',
  'custom-receipt_detail': '出入库明细字段',
  'sysconfig-department': '部门',
  'sysconfig-user': '用户',
  'sysconfig-factory': '工厂',
  'sysconfig-plugin-center': '插件中心',
  'sysconfig-external-data-sources': '外部数据源',
  'super-admin': '授权码管理',
  'monitor-online': '在线用户',
  'monitor-cache': '缓存监控',
  'monitor-cacheList': '缓存列表'
}

function captureWorkspaceTab(): WorkspaceTab {
  const id = activePageCacheKey.value
  return {
    id,
    label: workspaceLabels[id] || selectedRuntimePlugin.value?.name || '工作台',
    main: activeMainMenu.value,
    config: activeConfigMenu.value,
    basedata: activeBasedataMenu.value,
    production: activeProductionMenu.value,
    inventory: activeInventoryMenu.value,
    custom: activeCustomMenu.value,
    monitor: activeMonitorMenu.value,
    plugin: activePluginKey.value,
    closable: id !== 'home',
    pinned: id === 'home'
  }
}

function syncWorkspaceTab() {
  const next = captureWorkspaceTab()
  const existing = workspaceTabs.value.find(tab => tab.id === next.id)
  if (existing) {
    const pinned = existing.id === 'home' || existing.pinned
    Object.assign(existing, next)
    existing.pinned = pinned
  } else workspaceTabs.value.push(next)
  activeWorkspaceTabId.value = next.id
  tabContextMenu.show = false
}

function activateWorkspaceTab(tab: WorkspaceTab) {
  activeMainMenu.value = tab.main
  if (tab.config) activeConfigMenu.value = tab.config
  if (tab.basedata) activeBasedataMenu.value = tab.basedata
  if (tab.production) activeProductionMenu.value = tab.production
  if (tab.inventory) activeInventoryMenu.value = tab.inventory
  if (tab.custom) activeCustomMenu.value = tab.custom
  if (tab.monitor) activeMonitorMenu.value = tab.monitor as 'online' | 'cache' | 'cacheList'
  if (tab.plugin) activePluginKey.value = tab.plugin
  activeWorkspaceTabId.value = tab.id
  tabContextMenu.show = false
}

function activateWorkspaceTabFromEvent(tab: WorkspaceTabItem) {
  activateWorkspaceTab(tab as WorkspaceTab)
}

function openTabContextMenu(event: MouseEvent, tab: WorkspaceTabItem) {
  const menuWidth = 220
  const menuHeight = 324
  tabContextMenu.x = Math.min(event.clientX, window.innerWidth - menuWidth - 12)
  tabContextMenu.y = Math.min(event.clientY, window.innerHeight - menuHeight - 12)
  tabContextMenu.tabId = tab.id
  tabContextMenu.show = true
  showChromeQuickMenu.value = false
}

function closeTabContextMenu() {
  tabContextMenu.show = false
}

function closeWorkspaceTab(id: string) {
  const index = workspaceTabs.value.findIndex(tab => tab.id === id)
  const tab = workspaceTabs.value[index]
  if (!tab || !tab.closable || tab.pinned) return
  workspaceTabs.value.splice(index, 1)
  if (activeWorkspaceTabId.value === id) {
    activateWorkspaceTab(workspaceTabs.value[Math.max(0, index - 1)] || workspaceTabs.value[0])
  }
  closeTabContextMenu()
}

function toggleWorkspaceTabPinned(tab: WorkspaceTabItem) {
  const target = workspaceTabs.value.find(item => item.id === tab.id)
  if (!target || target.id === 'home') return
  target.pinned = !target.pinned
}

function toggleTabPinned() {
  const tab = workspaceTabs.value.find(item => item.id === tabContextMenu.tabId)
  if (tab) toggleWorkspaceTabPinned(tab)
  closeTabContextMenu()
}

function closeTabsWhere(predicate: (tab: WorkspaceTab, index: number) => boolean) {
  const currentId = activeWorkspaceTabId.value
  workspaceTabs.value = workspaceTabs.value.filter((tab, index) => tab.pinned || !tab.closable || !predicate(tab, index))
  if (!workspaceTabs.value.some(tab => tab.id === currentId)) activateWorkspaceTab(workspaceTabs.value[0])
  closeTabContextMenu()
}

function closeTabsLeft() {
  const index = workspaceTabs.value.findIndex(tab => tab.id === tabContextMenu.tabId)
  closeTabsWhere((_tab, tabIndex) => tabIndex < index)
}

function closeTabsRight() {
  const index = workspaceTabs.value.findIndex(tab => tab.id === tabContextMenu.tabId)
  closeTabsWhere((_tab, tabIndex) => tabIndex > index)
}

function closeOtherTabs() {
  const keepId = tabContextMenu.tabId
  closeTabsWhere(tab => tab.id !== keepId)
}

function closeAllTabs() {
  workspaceTabs.value = workspaceTabs.value.filter(tab => tab.pinned || !tab.closable)
  activateWorkspaceTab(workspaceTabs.value[0])
  showChromeQuickMenu.value = false
  closeTabContextMenu()
}

function refreshWorkspace() {
  window.location.reload()
}

function openCurrentTabInNewWindow() {
  window.open(window.location.href, '_blank', 'noopener,noreferrer')
  closeTabContextMenu()
}

function jumpFromQuickApp(main: string, sub = '') {
  activeMainMenu.value = main
  if (main === 'production' && sub) activeProductionMenu.value = sub
  if (main === 'inventory' && sub) activeInventoryMenu.value = sub
  if (main === 'basedata' && sub) activeBasedataMenu.value = sub
  if (main === 'sysconfig' && sub) activeConfigMenu.value = sub
  if (main === 'monitor' && sub) activeMonitorMenu.value = sub as 'online' | 'cache' | 'cacheList'
  if (main === 'plugin' && sub) activePluginKey.value = sub
  showQuickApps.value = false
}

function openRuntimePlugin(pluginKey: string) {
  activeMainMenu.value = 'plugin'
  activePluginKey.value = pluginKey
  expandedMenus.plugins = true
}

function selectLanguage(language: string) {
  showLanguageMenu.value = false
  if (language === 'zh-CN') showAction('当前界面已是简体中文')
  else showAction('英文界面将在多语言资源配置后启用')
}

function toggleChromeQuickMenu() {
  showChromeQuickMenu.value = !showChromeQuickMenu.value
  showQuickApps.value = false
  showLanguageMenu.value = false
  showTimezoneDialog.value = false
  showWorkspaceSearch.value = false
  tabContextMenu.show = false
}

function toggleQuickApps() {
  showQuickApps.value = !showQuickApps.value
  showChromeQuickMenu.value = false
  showLanguageMenu.value = false
  showTimezoneDialog.value = false
}

function toggleLanguageMenu() {
  showLanguageMenu.value = !showLanguageMenu.value
  showQuickApps.value = false
  showChromeQuickMenu.value = false
  showTimezoneDialog.value = false
}

function toggleTheme() {
  isDarkTheme.value = !isDarkTheme.value
}

function openInternalChat() {
  const url = new URL(window.location.href)
  url.hash = '#/im-chat'
  const messageWindow = window.open(url.toString(), '_blank', 'noopener,noreferrer')
  if (!messageWindow) {
    errorMessage.value = '浏览器阻止了内部沟通窗口，请允许弹窗后重试'
    return
  }
}

function openTimezoneDialog() {
  timezoneDraft.value = selectedTimezone.value
  showTimezoneDialog.value = true
  showQuickApps.value = false
  showChromeQuickMenu.value = false
  showLanguageMenu.value = false
}

function closeTimezoneDialog() {
  showTimezoneDialog.value = false
}

function confirmTimezone() {
  selectedTimezone.value = timezoneDraft.value
  localStorage.setItem('heihu_timezone', selectedTimezone.value)
  showTimezoneDialog.value = false
  const selected = timezoneOptions.find(option => option.value === selectedTimezone.value)
  showAction(`时区已切换为 ${selected?.label || selectedTimezone.value}`)
}

function syncNativeFullscreenState() {
  if (document.fullscreenElement) {
    nativeFullscreenActive = true
    isAppFullscreen.value = true
    return
  }

  if (nativeFullscreenActive) {
    nativeFullscreenActive = false
    isAppFullscreen.value = false
  }
}

async function toggleAppFullscreen() {
  if (isAppFullscreen.value) {
    // Remove the app-level fallback immediately so the button never feels stuck.
    isAppFullscreen.value = false
    nativeFullscreenActive = false
    if (document.fullscreenElement) {
      try {
        await document.exitFullscreen?.()
      } catch {
        // The CSS fallback has already exited; browser-level fullscreen may be unavailable.
      }
    }
    return
  }

  // Enter the reliable workspace mode first. Native browser fullscreen is only an enhancement.
  isAppFullscreen.value = true
  if (!document.documentElement.requestFullscreen) return

  try {
    await document.documentElement.requestFullscreen()
    nativeFullscreenActive = true
  } catch {
    // Some local browsers deny the native API; the workspace fallback remains active.
  }
}

function focusWorkspaceSearch() {
  showWorkspaceSearch.value = true
  showChromeQuickMenu.value = false
  nextTick(() => workspaceSearchInput.value?.focus())
}

function submitWorkspaceSearch() {
  handleSearch()
  showWorkspaceSearch.value = false
}

watch(activePageCacheKey, async () => {
  syncWorkspaceTab()
  await nextTick()
  const stage = pageMotionStage.value
  if (!stage || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  stage.getAnimations().forEach(animation => animation.cancel())
  stage.animate(
    [
      { opacity: 0.45, transform: 'translateY(7px)' },
      { opacity: 1, transform: 'translateY(0)' }
    ],
    { duration: 220, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)' }
  )
})

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
  activeMainMenu.value = 'sysconfig'
  activeConfigMenu.value = 'department'
}

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
    const authToken = token.value || localStorage.getItem('heihu_token') || ''
    if (!authToken) return

    // 1. 获取真实工单数据
    const woResponse = await fetchWorkOrders(authToken, { page: 1, size: 50 })
    const realWorkOrders = woResponse.items || []
    
    // 2. 获取报工记录，聚合任务执行进度
    const [reportRes, salesOrderRes] = await Promise.all([
      fetchReports(authToken, { page: 1, size: 200 }),
      fetchSalesOrders(authToken, { page: 1, size: 500 })
    ])
    const reports = reportRes.items || []
    const customerNamesByOrderCode = new Map((salesOrderRes.items || []).map(order => [order.code, order.customerName]))
    const workshopDashboard = buildWorkshopDashboard(realWorkOrders, reports, new Date(), customerNamesByOrderCode)
    tableData.value = workshopDashboard.tableData
    taskProgressData.value = workshopDashboard.taskProgressData
    Object.assign(dashboardKpi, workshopDashboard.metrics)
    dashboardReports.value = reports.map(r => ({
      ...r,
      createdAt: normalizeDate(r.createdAt)
    }))

    const analyticsReports = reports.filter(r => r.status === 'APPROVED')

    const dailyMap = new Map<string, { day: number; done: number; output: number; defect: number }>()
    const reporterMap = new Map<string, number>()
    const defectMap = new Map<string, number>()
    analyticsReports.forEach(r => {
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

  } catch (e) {
    console.error('Failed to load dashboard data:', e)
  }
}

watch(activeMainMenu, (newVal) => {
  if (!hasAdminAccess.value && newVal !== 'home') {
    activeMainMenu.value = 'home'
    return
  }
  if (newVal === 'dashboard') {
    loadDashboardData()
  }
  // Route guard: redirect non-super-admin users away from super-admin page
  if (newVal === 'super-admin' && !isSuperAdmin.value) {
    activeMainMenu.value = 'home'
  }
})

const dashboardKpi = reactive({
  inProcessOrderCount: 0,
  planCount: 0,
  inProcessTaskPlanCount: 0,
  inProcessTaskCount: 0,
  delayedOrders: 0,
  delayedTasks: 0,
  todayDone: 0,
  todayTasks: 0,
  todayOutput: 0,
  todayDefect: 0,
  defectRate: 0
})

const barData = ref<Array<{ day: number; done: number; output: number; defect: number }>>([])

const perfData = ref<Array<{ name: string; value: number }>>([])
const perfMax = computed(() => Math.max(1, ...perfData.value.map(p => p.value)))

const defectTrend = ref<Array<{ day: number; count: number; rate: number }>>([])

const pieData = ref<Array<{ name: string; color: string; count: number; pct: string }>>([])
const pieBackground = computed(() => {
  const total = pieData.value.reduce((sum, item) => sum + item.count, 0)
  if (total <= 0) return '#16314b'
  let cursor = 0
  const segments = pieData.value.map(item => {
    const start = cursor
    cursor += item.count / total * 100
    return `${item.color} ${start}% ${cursor}%`
  })
  return `conic-gradient(${segments.join(', ')})`
})

const scrollMessages = ref<Array<{ time: string; text: string }>>([])

const dashboardReports = ref<any[]>([])

const notificationItems = ref<NotificationInfo[]>([])
const notificationUnreadCount = ref(0)
const notificationCount = computed(() => notificationUnreadCount.value)
const notificationPreview = computed(() => notificationItems.value.slice(0, 8))
let notificationPollTimer: ReturnType<typeof setInterval> | null = null

function normalizeDate(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').substring(0, 16)
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: selectedTimezone.value,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(date).replace(/\//g, '-')
  } catch {
    return value.replace('T', ' ').substring(0, 16)
  }
}

async function loadNotificationCount(): Promise<boolean> {
  if (!token.value) {
    notificationUnreadCount.value = 0
    return true
  }
  try {
    notificationUnreadCount.value = await fetchNotificationUnreadCount(token.value)
    return true
  } catch (error) {
    notificationUnreadCount.value = 0
    notificationError.value = error instanceof Error ? error.message : '通知数量加载失败'
    return false
  }
}

async function loadNotifications(): Promise<boolean> {
  if (!token.value) {
    notificationItems.value = []
    notificationUnreadCount.value = 0
    return true
  }
  notificationsLoading.value = true
  notificationError.value = ''
  try {
    const result = await fetchNotifications(token.value, { scope: 'active', page: 1, size: 8 })
    notificationItems.value = result.items || []
    notificationUnreadCount.value = result.unreadCount || 0
    return true
  } catch (error) {
    notificationItems.value = []
    notificationError.value = error instanceof Error ? error.message : '通知加载失败'
    return false
  } finally {
    notificationsLoading.value = false
  }
}

function startNotificationPolling() {
  if (notificationPollTimer) clearInterval(notificationPollTimer)
  notificationPollTimer = setInterval(() => {
    if (!token.value || isInternalChatWindow.value) return
    void loadNotificationCount()
    if (showNotificationDropdown.value) void loadNotifications()
  }, 60000)
}

async function loadPluginRuntimeManifest(): Promise<boolean> {
  if (!token.value) {
    runtimePlugins.value = []
    return true
  }
  try {
    runtimePlugins.value = await fetchPluginRuntimeManifest(token.value)
    if (activePluginKey.value && !runtimePlugins.value.some(item => item.pluginKey === activePluginKey.value)) {
      activePluginKey.value = ''
      if (activeMainMenu.value === 'plugin') activeMainMenu.value = 'home'
    }
    return true
  } catch (error) {
    // 插件清单是可选能力，清单服务异常不能阻断核心工作台启动。
    runtimePlugins.value = []
    return true
  }
}

async function toggleNotifications() {
  showNotificationDropdown.value = !showNotificationDropdown.value
  showUserDropdown.value = false
  if (showNotificationDropdown.value) {
    await loadNotifications()
  }
}

async function markAllNotificationsAsRead() {
  if (!token.value || notificationUnreadCount.value === 0) return
  try {
    await markAllNotificationsRead(token.value)
    notificationItems.value.forEach(item => {
      item.unread = false
      if (item.state === 'UNREAD') item.state = 'READ'
    })
    notificationUnreadCount.value = 0
  } catch (error) {
    notificationError.value = error instanceof Error ? error.message : '全部已读失败'
  }
}

function openNotificationCenter() {
  activeMainMenu.value = 'notifications'
  showNotificationDropdown.value = false
  showUserDropdown.value = false
}

async function openNotification(item: NotificationInfo) {
  if (item.unread && token.value) {
    try {
      await markNotificationRead(token.value, item.id)
      item.unread = false
      item.state = 'READ'
      notificationUnreadCount.value = Math.max(0, notificationUnreadCount.value - 1)
    } catch {
      // Navigation remains available even when the read-state update is temporarily unavailable.
    }
  }
  activeMainMenu.value = item.targetMain || 'home'
  if (item.targetMain === 'production') {
    activeProductionMenu.value = item.targetSub || 'workorder'
    expandedMenus.production = true
  }
  showNotificationDropdown.value = false
}

const displayReports = computed(() => {
  if (dashboardReports.value && dashboardReports.value.length > 0) {
    return dashboardReports.value.slice(0, 5)
  }
  return []
})

const tableData = ref<Array<{ code: string; product: string; qty: number; customer: string; urgent: boolean; progress: number; steps: { name: string; progress: number }[] }>>([])

const taskProgressData = ref<Array<{ process: string; taskCount: number; planned: number; good: number; defect: number; remaining: number }>>([])

const showEditPrintTemplateModal = ref(false)

const workOrderFields = ref([
  '工单编号',
  '产品编号',
  '产品名称',
  '产品规格',
  '计划数',
  '计划开始时间',
  '计划结束时间',
  '工单备注',
  '工单外链',
  '单位',
  '创建人',
  '创建时间',
  '更新人',
  '更新时间'
])

const printTemplates = ref([
  { id: 1, type: '30x40', name: '工单二维码(30*40mm)', qrSource: '工单二维码', row1: '产品名称', row2: '工单编号', titleField: '' },
  { 
    id: 2, 
    type: '80x60', 
    name: '工单二维码(80*60mm)', 
    qrSource: '工单外链', 
    titleLabel: '工单编号',
    titleField: '工单编号',
    row1Label: '产品编号',
    row1Field: ['产品编号'],
    row2Label: '产品名称',
    row2Field: ['产品名称'],
    row3Label: '产品规格',
    row3Field: ['产品规格'],
    row4Label: '工单计划数',
    row4Field: ['计划数'],
    row5Label: '计划时间',
    row5Field: ['计划开始时间', '计划结束时间'],
    row6Label: '备注',
    row6Field: ['工单备注']
  }
])
const activeTemplateId = ref(1)
const activeTemplateIndex = computed(() => {
  const index = printTemplates.value.findIndex(t => t.id === activeTemplateId.value)
  return index === -1 ? 0 : index
})
const activeTpl = computed(() => {
  return printTemplates.value[activeTemplateIndex.value] as any
})

function openEditPrintTemplate() {
  try {
    const saved = localStorage.getItem('print_templates_data')
    if (saved) {
      const parsed = JSON.parse(saved)
      // 合并旧数据和新增加的数据项以保证字段完整
      parsed.forEach((savedTpl: any) => {
        const localTpl = printTemplates.value.find(t => t.id === savedTpl.id)
        if (localTpl) {
          Object.assign(localTpl, savedTpl)
        }
      })
    }
  } catch(e) {
    console.error('读取打印模板失败:', e)
  }
  showEditPrintTemplateModal.value = true
}

function savePrintTemplates() {
  try {
    localStorage.setItem('print_templates_data', JSON.stringify(printTemplates.value))
  } catch(e) {
    console.error('保存打印模板失败:', e)
  }
  showAction('保存成功')
  showEditPrintTemplateModal.value = false
}

function ensureArray(val: any): string[] {
  if (Array.isArray(val)) return val
  if (typeof val === 'string' && val) return [val]
  return []
}

function toggleRowField(rowIndex: number, fieldName: string) {
  const tpl = printTemplates.value[activeTemplateIndex.value] as any
  const key = `row${rowIndex}Field`
  let arr = ensureArray(tpl[key])
  if (arr.includes(fieldName)) {
    arr = arr.filter((f: string) => f !== fieldName)
  } else {
    arr.push(fieldName)
  }
  tpl[key] = arr
}

const qrPreviewUrl = ref('')
watchEffect(() => {
  const tpl = printTemplates.value[activeTemplateIndex.value]
  if (!tpl) return
  let text = ''
  if (tpl.qrSource === '工单外链') {
    text = `${window.location.origin}/workorder/preview`
  } else if (tpl.qrSource === '工单编号') {
    text = 'GD-PREVIEW'
  } else if (tpl.qrSource === '工单二维码') {
    text = 'WORK_ORDER_QR_PREVIEW'
  } else {
    text = tpl.qrSource || 'WORK_ORDER_PREVIEW'
  }
  QRCode.toDataURL(text, { width: 400, margin: 2, color: { dark: '#000000', light: '#ffffff' } }).then((url: string) => {
    qrPreviewUrl.value = url
  }).catch((err: any) => console.error('QR Generate Error:', err))
})

// 全局 Tooltip（fixed 定位，不被 overflow 裁剪）
const tooltipState = reactive({ visible: false, text: '', x: 0, y: 0 })
function showTooltip(e: MouseEvent, text: string) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  tooltipState.x = rect.left + rect.width / 2
  tooltipState.y = rect.top - 8
  tooltipState.text = text
  tooltipState.visible = true
}
function hideTooltip() { tooltipState.visible = false }

// 单位管理
const units = ref<UnitInfo[]>([])
const unitFilterForm = reactive({ name: '', remark: '' })
const unitFilteredList = computed(() =>
  units.value.filter(u => {
    const nameOk = !unitFilterForm.name || u.name.includes(unitFilterForm.name)
    const remarkOk = !unitFilterForm.remark || !u.remark || u.remark.includes(unitFilterForm.remark)
    return nameOk && remarkOk
  })
)
const showUnitModal = ref(false)
const editingUnit = ref<UnitInfo | null>(null)
const unitForm = reactive({ name: '', remark: '' })

async function loadUnits(): Promise<boolean> {
  try {
    const res = await fetchUnits(token.value)
    units.value = res.items
    return true
  } catch (e) {
    console.error('加载单位失败', e)
    return false
  }
}
function openCreateUnit() {
  editingUnit.value = null
  unitForm.name = ''
  unitForm.remark = ''
  showUnitModal.value = true
}
function openEditUnit(unit: UnitInfo) {
  editingUnit.value = unit
  unitForm.name = unit.name
  unitForm.remark = unit.remark || ''
  showUnitModal.value = true
}
async function handleSaveUnit() {
  if (!unitForm.name.trim()) return
  try {
    if (editingUnit.value) {
      const updated = await updateUnit(token.value, editingUnit.value.id, { name: unitForm.name, remark: unitForm.remark })
      const idx = units.value.findIndex(u => u.id === editingUnit.value!.id)
      if (idx !== -1) units.value[idx] = updated
    } else {
      const created = await createUnit(token.value, { name: unitForm.name, remark: unitForm.remark })
      units.value.unshift(created)
    }
    showUnitModal.value = false
  } catch (e: any) { errorMessage.value = e.message || '保存单位失败' }
}
async function handleDeleteUnit(unit: UnitInfo) {
  try {
    await deleteUnit(token.value, unit.id)
    units.value = units.value.filter(u => u.id !== unit.id)
  } catch (e: any) { errorMessage.value = e.message || '删除单位失败' }
}

// 不良品项管理
const defects = ref<DefectInfo[]>([])
const defectFilterForm = reactive({ code: '', name: '' })
const defectFilteredList = computed(() =>
  defects.value.filter(d => {
    const codeOk = !defectFilterForm.code || d.code.includes(defectFilterForm.code)
    const nameOk = !defectFilterForm.name || d.name.includes(defectFilterForm.name)
    return codeOk && nameOk
  })
)
const showDefectModal = ref(false)
const editingDefect = ref<DefectInfo | null>(null)
const defectForm = reactive({ code: '', name: '' })

async function loadDefects(): Promise<boolean> {
  try {
    const res = await fetchDefects(token.value)
    defects.value = res.items
    return true
  } catch (e) {
    console.error('加载不良品项失败', e)
    return false
  }
}
function openCreateDefect() {
  editingDefect.value = null
  defectForm.code = ''
  defectForm.name = ''
  showDefectModal.value = true
}
function openEditDefect(d: DefectInfo) {
  editingDefect.value = d
  defectForm.code = d.code
  defectForm.name = d.name
  showDefectModal.value = true
}
async function handleSaveDefect() {
  if (!defectForm.name.trim()) return
  try {
    if (editingDefect.value) {
      const updated = await updateDefect(token.value, editingDefect.value.id, { code: defectForm.code, name: defectForm.name })
      const idx = defects.value.findIndex(d => d.id === editingDefect.value!.id)
      if (idx !== -1) defects.value[idx] = updated
    } else {
      const created = await createDefect(token.value, { code: defectForm.code || undefined, name: defectForm.name })
      defects.value.unshift(created)
    }
    showDefectModal.value = false
  } catch (e: any) { errorMessage.value = e.message || '保存不良品项失败' }
}
async function handleDeleteDefect(d: DefectInfo) {
  try {
    await deleteDefect(token.value, d.id)
    defects.value = defects.value.filter(item => item.id !== d.id)
  } catch (e: any) { errorMessage.value = e.message || '删除不良品项失败' }
}

// 工序管理
interface CollectDataRow {
  type: string
  name: string
}
interface ProcessItem {
  id: number
  code: string
  name: string
  reportPermission: string[]
  reportRatio: number
  defectItemIds: number[]
  taskPunchIn: boolean
  goodQuantity: string
  machineName: string
  feedCount: string
  correspondingMachine: string
  requirements: string
  collectData: CollectDataRow[]
}
const processes = ref<ProcessItem[]>([])
const processFilterForm = reactive({ code: '', name: '', creator: '' })
const processFilteredList = computed(() =>
  processes.value.filter(p => {
    const codeOk = !processFilterForm.code || p.code.includes(processFilterForm.code)
    const nameOk = !processFilterForm.name || p.name.includes(processFilterForm.name)
    return codeOk && nameOk
  })
)
// 'list' | 'create' | 'edit'
const processView = ref<'list' | 'create' | 'edit'>('list')
const editingProcess = ref<ProcessItem | null>(null)
const processNextId = ref(1)
const processForm = reactive<{
  code: string
  name: string
  reportPermission: string[]
  reportRatio: number
  defectItemIds: number[]
  taskPunchIn: boolean
  goodQuantity: string
  machineName: string
  feedCount: string
  correspondingMachine: string
  requirements: string
  collectData: CollectDataRow[]
}>({
  code: '',
  name: '',
  reportPermission: ['所有人'],
  reportRatio: 1,
  defectItemIds: [],
  taskPunchIn: false,
  goodQuantity: '',
  machineName: '',
  feedCount: '',
  correspondingMachine: '',
  requirements: '',
  collectData: []
})

// ===== 工艺路线管理 =====
interface RouteStep {
  id: number          // 行内唯一id，用于拖拽key
  type: 'process' | 'route'
  processId: number | null  // 选中的工序或工艺路线 id
  reportRatio: number
  remark: string
}
interface RouteItem {
  id: number
  code: string
  name: string
  steps: RouteStep[]
  creator: string
  createdAt: string
}
const routes = ref<RouteItem[]>([])
const routeFilterForm = reactive({ code: '', name: '', processId: '' })
const routeFilteredList = computed(() =>
  routes.value.filter(r => {
    const codeOk = !routeFilterForm.code || r.code.includes(routeFilterForm.code)
    const nameOk = !routeFilterForm.name || r.name.includes(routeFilterForm.name)
    return codeOk && nameOk
  })
)
const routeView = ref<'list' | 'create' | 'edit'>('list')
const editingRoute = ref<RouteItem | null>(null)
const routeNextId = ref(1)
const routeStepNextId = ref(1)
const routeForm = reactive<{ code: string; name: string; steps: RouteStep[] }>({
  code: '',
  name: '',
  steps: []
})

// 工序下拉搜索（每行独立）
const routeStepSearchText = ref<Record<number, string>>({})
const routeStepDropdownOpen = ref<Record<number, boolean>>({})

function filteredItemsForStep(stepId: number) {
  const step = routeForm.steps.find(s => s.id === stepId)
  const s = (routeStepSearchText.value[stepId] || '').trim().toLowerCase()
  if (step && step.type === 'route') {
    // 类型为工艺路线 → 返回路线列表（排除当前正在编辑的路线，防止自引用）
    const editingId = editingRoute.value?.id
    const list = routes.value.filter(r => r.id !== editingId)
    if (!s) return list.map(r => ({ id: r.id, name: r.name, code: r.code }))
    return list.filter(r => (r.name || '').toLowerCase().includes(s) || (r.code || '').toLowerCase().includes(s))
      .map(r => ({ id: r.id, name: r.name, code: r.code }))
  }
  // 类型为工序 → 返回工序列表
  if (!s) return processes.value.map(p => ({ id: p.id, name: p.name, code: p.code }))
  return processes.value.filter(p => (p.name || '').toLowerCase().includes(s) || (p.code || '').toLowerCase().includes(s))
    .map(p => ({ id: p.id, name: p.name, code: p.code }))
}

function openRouteStepDropdown(stepId: number) {
  const isOpen = routeStepDropdownOpen.value[stepId]
  const newMap: Record<number, boolean> = {}
  Object.keys(routeStepDropdownOpen.value).forEach(k => {
    newMap[Number(k)] = false
  })
  newMap[stepId] = !isOpen
  routeStepDropdownOpen.value = newMap
}

function closeAllRouteStepDropdowns() {
  const newMap: Record<number, boolean> = {}
  Object.keys(routeStepDropdownOpen.value).forEach(k => {
    newMap[Number(k)] = false
  })
  routeStepDropdownOpen.value = newMap
}
function selectRouteStepItem(step: RouteStep, itemId: number) {
  step.processId = itemId
  if (step.type === 'process') {
    const p = processes.value.find(x => x.id === itemId)
    step.reportRatio = p ? p.reportRatio : 1
  } else {
    step.reportRatio = 1
  }
  routeStepDropdownOpen.value[step.id] = false
}
function getStepItemName(step: RouteStep) {
  if (!step.processId) return ''
  if (step.type === 'route') {
    const r = routes.value.find(x => x.id === step.processId)
    return r ? r.name : ''
  }
  const p = processes.value.find(x => x.id === step.processId)
  return p ? p.name : ''
}
function getProcessName(pid: number | null) {
  if (!pid) return ''
  const p = processes.value.find(x => x.id === pid)
  if (p) return p.name
  const r = routes.value.find(x => x.id === pid)
  return r ? r.name : ''
}

function addRouteStep() {
  const id = routeStepNextId.value++
  routeForm.steps.push({ id, type: 'process', processId: null, reportRatio: 1, remark: '' })
  routeStepSearchText.value[id] = ''
  routeStepDropdownOpen.value[id] = false
}
function removeRouteStep(idx: number) {
  const step = routeForm.steps[idx]
  delete routeStepSearchText.value[step.id]
  delete routeStepDropdownOpen.value[step.id]
  routeForm.steps.splice(idx, 1)
}

// 拖拽排序
const routeDragIdx = ref<number | null>(null)
const routeDragOverIdx = ref<number | null>(null)
function onRouteDragStart(idx: number, e: DragEvent) {
  routeDragIdx.value = idx
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
function onRouteDragOver(idx: number, e: DragEvent) {
  e.preventDefault()
  routeDragOverIdx.value = idx
}
function onRouteDrop(idx: number) {
  if (routeDragIdx.value === null || routeDragIdx.value === idx) {
    routeDragIdx.value = null
    routeDragOverIdx.value = null
    return
  }
  const moved = routeForm.steps.splice(routeDragIdx.value, 1)[0]
  routeForm.steps.splice(idx, 0, moved)
  routeDragIdx.value = null
  routeDragOverIdx.value = null
}
function onRouteDragEnd() {
  routeDragIdx.value = null
  routeDragOverIdx.value = null
}

function openCreateRoute() {
  editingRoute.value = null
  routeForm.code = ''
  routeForm.name = ''
  routeForm.steps = []
  routeStepSearchText.value = {}
  routeStepDropdownOpen.value = {}
  routeView.value = 'create'
}
function openEditRoute(r: RouteItem) {
  editingRoute.value = r
  routeForm.code = r.code
  routeForm.name = r.name
  routeForm.steps = r.steps.map(s => ({ ...s }))
  routeStepSearchText.value = {}
  routeStepDropdownOpen.value = {}
  routeForm.steps.forEach(s => {
    routeStepSearchText.value[s.id] = ''
    routeStepDropdownOpen.value[s.id] = false
  })
  routeView.value = 'edit'
}
async function handleSaveRoute() {
  if (!routeForm.name.trim()) { errorMessage.value = '工艺路线名称不能为空'; return }
  if (!token.value) return
  const payload: ProcessRoutePayload = {
    code: routeForm.code.trim() || undefined,
    name: routeForm.name.trim(),
    steps: routeForm.steps.map(s => ({
      type: s.type,
      processId: s.processId,
      reportRatio: s.reportRatio,
      remark: s.remark
    }))
  }
  try {
    if (editingRoute.value) {
      await updateProcessRoute(token.value, editingRoute.value.id, payload)
    } else {
      await createProcessRoute(token.value, payload)
    }
    await loadRoutes()
    routeView.value = 'list'
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '保存失败'
  }
}
function confirmDeleteRoute(r: RouteItem) {
  confirmState.title = '删除确认'
  confirmState.message = `是否确要删除工艺路线「${r.name}」？`
  confirmState.action = async () => {
    if (!token.value) return
    try {
      await deleteProcessRoute(token.value, r.id)
      await loadRoutes()
    } catch (e) {
      errorMessage.value = e instanceof Error ? e.message : '删除失败'
    }
  }
  confirmState.show = true
}
async function loadRoutes(): Promise<boolean> {
  if (!token.value) return false
  try {
    const res = await fetchProcessRoutes(token.value, { size: 999 })
    routes.value = res.items.map(r => ({
      id: r.id,
      code: r.code,
      name: r.name,
      steps: (r.steps || []).map((s, i) => ({
        id: i + 1,
        type: (s.type as 'process' | 'route') || 'process',
        processId: s.processId,
        reportRatio: s.reportRatio ?? 1,
        remark: s.remark || ''
      })),
      creator: r.creator || '',
      createdAt: r.createdAt ? r.createdAt.slice(0, 10) : ''
    }))
    return true
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '工艺路线加载失败'
    return false
  }
}

// 部门数据（提前声明，供报工权限树使用）
const departments = ref<DepartmentInfo[]>([])
const departmentTotal = ref(0)
const departmentsLoading = ref(false)
const departmentFilterForm = reactive({ code: '', name: '', page: 1, size: 10 })

// 报工权限选项 - 树形结构（部门 + 员工，动态从 departments & users 生成）
interface PermEmployee { id: string; name: string }
interface PermDept { id: string; name: string; employees: PermEmployee[] }
const permTree = computed<PermDept[]>(() =>
  departments.value.map(dept => ({
    id: `dept-${dept.id}`,
    name: dept.name,
    employees: users.value
      .filter(u => u.departmentId === dept.id && u.enabled)
      .map(u => ({ id: `emp-${u.id}`, name: u.name }))
  }))
)
const permSearchText = ref('')
const permExpandedDepts = ref<Set<string>>(new Set())
const filteredPermTree = computed(() => {
  const s = permSearchText.value.trim().toLowerCase()
  if (!s) return permTree.value
  return permTree.value.map(dept => ({
    ...dept,
    employees: dept.employees.filter(e => e.name.toLowerCase().includes(s))
  })).filter(dept => dept.name.toLowerCase().includes(s) || dept.employees.length > 0)
})
function togglePermDeptExpand(deptId: string) {
  if (permExpandedDepts.value.has(deptId)) permExpandedDepts.value.delete(deptId)
  else permExpandedDepts.value.add(deptId)
}
function isDeptExpanded(deptId: string) { return permExpandedDepts.value.has(deptId) }
// 判断员工 key 是否显示为选中（直接选中、或部门已选、或所有人已选）
function isPermSelected(key: string) {
  const perms = processForm.reportPermission
  if (perms.includes('all')) return true
  if (perms.includes(key)) return true
  // 如果是员工 key，检查所在部门是否已选
  if (key.startsWith('emp-')) {
    for (const dept of permTree.value) {
      if (dept.employees.some(e => e.id === key) && perms.includes(dept.id)) return true
    }
  }
  return false
}
function isDeptPartialSelected(dept: PermDept) {
  const perms = processForm.reportPermission
  // 'all' 或整个部门已选 → 不是"部分"
  if (perms.includes('all')) return false
  if (perms.includes(dept.id)) return false
  // 没有员工的部门不存在部分选中
  if (dept.employees.length === 0) return false
  const anySelected = dept.employees.some(e => perms.includes(e.id))
  const allSelected = dept.employees.every(e => perms.includes(e.id))
  return anySelected && !allSelected
}
function isDeptAllSelected(dept: PermDept) {
  const perms = processForm.reportPermission
  // 'all' 全选
  if (perms.includes('all')) return true
  // dept-id 在数组里 表示整个部门已选
  if (perms.includes(dept.id)) return true
  // 或者每个员工都单独被选中
  return dept.employees.length > 0 && dept.employees.every(e => perms.includes(e.id))
}
function togglePermAll() {
  // 已是全选 → 清空；否则 → 全选（设为 all）
  if (processForm.reportPermission.includes('all')) {
    processForm.reportPermission = []
  } else {
    processForm.reportPermission = ['all']
  }
}
function togglePermDept(dept: PermDept) {
  // 取消"所有人"
  const allIdx = processForm.reportPermission.indexOf('all')
  if (allIdx !== -1) processForm.reportPermission.splice(allIdx, 1)

  if (isDeptAllSelected(dept)) {
    // 已全选 → 取消部门id和所有员工id
    processForm.reportPermission = processForm.reportPermission.filter(
      k => k !== dept.id && !dept.employees.some(e => e.id === k)
    )
  } else {
    // 未全选 → 只存 dept-id，移除旧的散装员工id（避免重复）
    processForm.reportPermission = processForm.reportPermission.filter(
      k => !dept.employees.some(e => e.id === k)
    )
    if (!processForm.reportPermission.includes(dept.id)) {
      processForm.reportPermission.push(dept.id)
    }
  }
}
function togglePermEmployee(dept: PermDept, emp: PermEmployee) {
  // 取消"所有人"
  const allIdx = processForm.reportPermission.indexOf('all')
  if (allIdx !== -1) processForm.reportPermission.splice(allIdx, 1)

  const deptSelected = processForm.reportPermission.includes(dept.id)

  if (deptSelected) {
    // 部门整体已选 → 展开为散装员工，再取消该员工
    processForm.reportPermission = processForm.reportPermission.filter(k => k !== dept.id)
    dept.employees.forEach(e => {
      if (e.id !== emp.id && !processForm.reportPermission.includes(e.id)) {
        processForm.reportPermission.push(e.id)
      }
    })
  } else {
    const empIdx = processForm.reportPermission.indexOf(emp.id)
    if (empIdx !== -1) {
      // 已选员工 → 取消
      processForm.reportPermission.splice(empIdx, 1)
    } else {
      // 未选员工 → 加入
      processForm.reportPermission.push(emp.id)
      // 如果部门所有员工都被单独选中了，合并成 dept-id
      if (dept.employees.every(e => processForm.reportPermission.includes(e.id))) {
        processForm.reportPermission = processForm.reportPermission.filter(
          k => !dept.employees.some(e => e.id === k)
        )
        if (!processForm.reportPermission.includes(dept.id)) {
          processForm.reportPermission.push(dept.id)
        }
      }
    }
  }
}
function getPermLabel(key: string): string {
  if (key === 'all') return '所有人'
  for (const dept of permTree.value) {
    if (dept.id === key) return dept.name
    const emp = dept.employees.find(e => e.id === key)
    if (emp) return emp.name
  }
  return key
}
function removeProcessPermission(key: string) {
  processForm.reportPermission = processForm.reportPermission.filter(x => x !== key)
}

/**
 * 将报工权限 key 数组转换为展示用标签列表
 * 返回 [{ label, tooltip, partial }]
 *   label: 显示文本
 *   tooltip: 鼠标悬停提示（有权限的员工列表，仅 partial 时有值）
 *   partial: 是否"有部分员工无权限"
 */
function formatPermissionTags(perms: string[]): { label: string; tooltip: string; partial: boolean }[] {
  if (!perms || perms.length === 0) return [{ label: '—', tooltip: '', partial: false }]
  if (perms.includes('all')) return [{ label: '所有人', tooltip: '', partial: false }]

  const tags: { label: string; tooltip: string; partial: boolean }[] = []
  const handled = new Set<string>()

  for (const dept of permTree.value) {
    const deptSelected = perms.includes(dept.id)
    const selectedEmps = dept.employees.filter(e => perms.includes(e.id))
    const allEmps = dept.employees

    if (deptSelected || (allEmps.length > 0 && selectedEmps.length === allEmps.length)) {
      // 整个部门都有权限
      tags.push({ label: dept.name, tooltip: '', partial: false })
      handled.add(dept.id)
      allEmps.forEach(e => handled.add(e.id))
    } else if (selectedEmps.length > 0) {
      // 部分员工有权限
      const names = selectedEmps.map(e => e.name).join('、')
      tags.push({
        label: `${dept.name}（有部分员工无权限）`,
        tooltip: `有权限的员工：${names}`,
        partial: true
      })
      handled.add(dept.id)
      selectedEmps.forEach(e => handled.add(e.id))
    }
  }

  // 未被部门覆盖的单独员工 key
  for (const key of perms) {
    if (!handled.has(key) && key.startsWith('emp-')) {
      const name = getPermLabel(key)
      tags.push({ label: name, tooltip: '', partial: false })
    }
  }

  return tags.length > 0 ? tags : [{ label: '—', tooltip: '', partial: false }]
}

// 工序不良品项下拉
const showProcessDefectDropdown = ref(false)
// 工序报工权限下拉
const showProcessPermDropdown = ref(false)

/**
 * 标签框展示用的 key 列表（过滤掉被 dept-id 覆盖的散装员工 key，避免重复显示）
 */
const visiblePermKeys = computed(() => {
  const perms = processForm.reportPermission
  if (perms.includes('all')) return ['all']
  // 找出所有已选 dept-id 覆盖的员工 id 集合
  const coveredEmpIds = new Set<string>()
  for (const dept of permTree.value) {
    if (perms.includes(dept.id)) {
      dept.employees.forEach(e => coveredEmpIds.add(e.id))
    }
  }
  // 只显示 dept-id 和未被覆盖的散装员工 key
  return perms.filter(k => !coveredEmpIds.has(k))
})

function openCreateProcess() {
  editingProcess.value = null
  processForm.code = ''
  processForm.name = ''
  processForm.reportPermission = ['all']
  processForm.reportRatio = 1
  processForm.defectItemIds = []
  processForm.taskPunchIn = false
  processForm.goodQuantity = ''
  processForm.machineName = ''
  processForm.feedCount = ''
  processForm.correspondingMachine = ''
  processForm.requirements = ''
  processForm.collectData = []
  permSearchText.value = ''
  permExpandedDepts.value = new Set()
  processView.value = 'create'
}
function openEditProcess(p: ProcessItem) {
  editingProcess.value = p
  processForm.code = p.code
  processForm.name = p.name
  processForm.reportPermission = [...p.reportPermission]
  processForm.reportRatio = p.reportRatio
  processForm.defectItemIds = [...p.defectItemIds]
  processForm.taskPunchIn = p.taskPunchIn
  processForm.goodQuantity = p.goodQuantity
  processForm.machineName = p.machineName
  processForm.feedCount = p.feedCount
  processForm.correspondingMachine = p.correspondingMachine
  processForm.requirements = p.requirements
  processForm.collectData = p.collectData.map(r => ({ ...r }))
  processView.value = 'edit'
}
async function handleSaveProcess() {
  if (!processForm.name.trim()) { errorMessage.value = '工序名称不能为空'; return }
  if (!token.value) return
  const payload: ProcessPayload = {
    code: processForm.code.trim() || undefined,
    name: processForm.name.trim(),
    reportPermission: [...processForm.reportPermission],
    reportRatio: processForm.reportRatio,
    defectItemIds: [...processForm.defectItemIds],
    taskPunchIn: processForm.taskPunchIn,
    goodQuantity: processForm.goodQuantity,
    machineName: processForm.machineName,
    feedCount: processForm.feedCount,
    correspondingMachine: processForm.correspondingMachine,
    requirements: processForm.requirements,
    collectData: processForm.collectData.map(r => ({ type: r.type, name: r.name }))
  }
  try {
    if (editingProcess.value) {
      await updateProcess(token.value, editingProcess.value.id, payload)
    } else {
      await createProcess(token.value, payload)
    }
    await loadProcesses()
    processView.value = 'list'
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '保存失败'
  }
}
async function handleDeleteProcess(p: ProcessItem) {
  if (!token.value) return
  try {
    await deleteProcess(token.value, p.id)
    await loadProcesses()
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '删除失败'
  }
}
async function loadProcesses(): Promise<boolean> {
  if (!token.value) return false
  try {
    const res = await fetchProcesses(token.value, { size: 999 })
    processes.value = res.items.map(p => ({
      id: p.id,
      code: p.code,
      name: p.name,
      reportPermission: p.reportPermission || ['所有人'],
      reportRatio: p.reportRatio ?? 1,
      defectItemIds: p.defectItemIds || [],
      taskPunchIn: p.taskPunchIn ?? false,
      goodQuantity: p.goodQuantity || '',
      machineName: p.machineName || '',
      feedCount: p.feedCount || '',
      correspondingMachine: p.correspondingMachine || '',
      requirements: p.requirements || '',
      collectData: (p.collectData || []).map(c => ({ type: c.type, name: c.name }))
    }))
    return true
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : '工序加载失败'
    return false
  }
}
function addCollectDataRow() {
  if (processForm.collectData.length < 50) {
    processForm.collectData.push({ type: '', name: '' })
  }
}
function removeCollectDataRow(idx: number) {
  processForm.collectData.splice(idx, 1)
}
function toggleProcessDefect(id: number) {
  const pos = processForm.defectItemIds.indexOf(id)
  if (pos !== -1) processForm.defectItemIds.splice(pos, 1)
  else processForm.defectItemIds.push(id)
}
function getDefectLabel(id: number) {
  const d = defects.value.find(x => x.id === id)
  return d ? `${d.code} | ${d.name}` : String(id)
}

// 菜单展开状态，默认系统配置展开
const expandedMenus = reactive<Record<string, boolean>>({
  production: false,
  upstream: false,
  inventory: false,
  basedata: false,
    custom: false,
    report: false,
    advanced: false,
    sysconfig: false,
    monitor: false,
    plugins: false
  })

function toggleMenu(key: string) {
  expandedMenus[key] = !expandedMenus[key]
}
const showDepartmentModal = ref(false)
const editingDepartment = ref<DepartmentInfo | null>(null)
const departmentForm = reactive<CreateDepartmentPayload>({ code: '', name: '', userIds: [] })
const allUsersList = ref<UserInfo[]>([]) // Used for selecting users when creating a department
const showUserSelectDropdown = ref(false)
const userSelectTriggerRef = ref<HTMLElement | null>(null)
const dropdownStyle = ref<{ top: string; left: string; width: string }>({
  top: '0px',
  left: '0px',
  width: '200px'
})

function openUserSelectDropdown() {
  if (userSelectTriggerRef.value) {
    const rect = userSelectTriggerRef.value.getBoundingClientRect()
    dropdownStyle.value = {
      top: rect.bottom + 4 + 'px',
      left: rect.left + 'px',
      width: rect.width + 'px'
    }
  }
  showUserSelectDropdown.value = !showUserSelectDropdown.value
}

const selectedUserNames = computed(() => {
  if (!departmentForm.userIds || departmentForm.userIds?.length === 0) return '请选择部门人员'
  return allUsersList.value
    .filter(u => departmentForm.userIds!.includes(u.id))
    .map(u => u.name)
    .join('\u3001')
})

const loginForm = reactive({
  factoryCode: '',
  username: '',
  password: ''
})

// Phone login state
const loginTab = ref<'phone' | 'factory'>('factory')
const rememberMe = ref(false)

// Restore saved login identifiers only. Never persist the password in browser storage.
try {
  const saved = localStorage.getItem('gongdan_remember_me')
  if (saved) {
    const parsed = JSON.parse(saved)
    if (parsed.remember) {
      rememberMe.value = true
      loginForm.factoryCode = parsed.factoryCode || ''
      loginForm.username = parsed.username || ''
      if (parsed.password) {
        localStorage.setItem('gongdan_remember_me', JSON.stringify({
          remember: true,
          factoryCode: loginForm.factoryCode,
          username: loginForm.username
        }))
      }
    }
  }
} catch (e) {
  console.error('Failed to read rememberMe from localStorage:', e)
}
const phoneLoginMode = ref<'password' | 'sms'>('password')
const phoneForm = reactive({
  phone: '',
  password: '',
  code: ''
})
const smsCountdown = ref(0)
const sendingSms = ref(false)
let smsTimer: ReturnType<typeof setInterval> | null = null
const phoneError = ref('')
const showFactorySelect = ref(false)
const showBindFactory = ref(false)
const bindingPhone = ref('')
const factoryList = ref<FactoryOptionItem[]>([])
const pendingPhoneCredential = reactive({
  phone: '',
  loginType: '' as 'password' | 'sms',
  credential: ''
})

const isPhoneValid = computed(() => /^1\d{10}$/.test(phoneForm.phone))

const filterForm = reactive({
  name: '',
  status: '启用中',
  account: '',
  role: '全部',
  department: '',
  virtual: '全部'
})

const createForm = reactive<CreateUserPayload>({
  username: '',
  phone: '',
  name: '',
  password: '',
  role: 'ADMIN',
  departmentId: undefined,
  virtualUser: false
})

const editForm = reactive<UpdateUserPayload>({
  username: '',
  phone: '',
  name: '',
  role: 'ADMIN',
  departmentId: undefined,
  virtualUser: false
})

const hasAdminAccess = computed(() => Boolean(isSuperAdmin.value || currentUser.value?.role === 'ADMIN'))
const filteredUsers = computed(() => {
  return users.value.filter((item) => {
    const matchName = !filterForm.name || item.name.includes(filterForm.name)
    const matchAccount = !filterForm.account || item.username.includes(filterForm.account)
    const matchRole = filterForm.role === '全部' || item.roleLabel === filterForm.role
    const matchDepartment =
      !filterForm.department || filterForm.department === '全部' || item.departmentName === filterForm.department
    const matchStatus =
      filterForm.status === '全部' ||
      (filterForm.status === '全部' ? true : filterForm.status === '启用中' ? item.enabled : !item.enabled)
    const matchVirtual =
      filterForm.virtual === '全部' ||
      (filterForm.virtual === '全部' ? true : filterForm.virtual === '是' ? item.virtualUser : !item.virtualUser)
    return matchName && matchAccount && matchRole && matchDepartment && matchStatus && matchVirtual
  })
})

const isAllSelected = computed({
  get() {
    return filteredUsers.value.length > 0 && selectedUserIds.value.length === filteredUsers.value.length
  },
  set(val) {
    if (val) {
      selectedUserIds.value = filteredUsers.value.map(u => u.id)
    } else {
      selectedUserIds.value = []
    }
  }
})

function resetWorkspaceLoading() {
  workspaceLoadingStages.value = createWorkspaceLoadingStages()
  workspaceLoadingProgress.value = 0
  workspaceLoadingStage.value = '准备启动'
  workspaceLoadingError.value = ''
  workspaceLoadingVisible.value = true
}

function updateWorkspaceLoadingProgress() {
  const total = workspaceLoadingStages.value.length
  const completed = workspaceLoadingStages.value.filter(stage => stage.status === 'done').length
  workspaceLoadingProgress.value = total > 0 ? Math.round((completed / total) * 100) : 100
}

async function runWorkspaceLoadingStage(
  runId: number,
  key: string,
  task: () => Promise<string | void> | string | void
) {
  const stage = workspaceLoadingStages.value.find(item => item.key === key)
  if (!stage || runId !== workspaceBootstrapRun) return

  stage.status = 'loading'
  stage.detail = '正在请求数据，请稍候'
  workspaceLoadingStage.value = stage.label
  updateWorkspaceLoadingProgress()

  try {
    const detail = await task()
    if (runId !== workspaceBootstrapRun) return
    stage.status = 'done'
    stage.detail = detail || '已完成'
    updateWorkspaceLoadingProgress()
  } catch (error) {
    if (runId !== workspaceBootstrapRun) return
    stage.status = 'error'
    stage.detail = '请求失败，已暂停'
    workspaceLoadingStage.value = stage.label
    workspaceLoadingError.value = `${stage.label}失败：${error instanceof Error ? error.message : '接口返回失败'}`
    updateWorkspaceLoadingProgress()
    throw error
  }
}

async function bootstrapWorkspace(): Promise<boolean> {
  if (!token.value || !currentUser.value || !hasAdminAccess.value) {
    workspaceLoadingVisible.value = false
    return true
  }

  const runId = ++workspaceBootstrapRun
  resetWorkspaceLoading()
  errorMessage.value = ''

  try {
    await runWorkspaceLoadingStage(runId, 'restore-session', async () => {
      if (!isSuperAdmin.value) {
        const factory = await fetchCurrentFactory(token.value)
        if (runId !== workspaceBootstrapRun) return
        sharedAuth.setFactoryCode(factory.code)
      }
      await nextTick()
      return `已恢复 ${currentFactoryCode.value} 工厂会话`
    })
    await runWorkspaceLoadingStage(runId, 'load-users', async () => {
      if (!await loadUsers()) throw new Error(errorMessage.value || '用户接口没有返回成功')
      return `已加载 ${users.value.length} 个用户`
    })
    await runWorkspaceLoadingStage(runId, 'load-departments', async () => {
      if (!await loadDepartments()) throw new Error(errorMessage.value || '部门接口没有返回成功')
      const allDepartments = await fetchDepartments(token.value, { code: '', name: '', page: 1, size: 999 })
      departments.value = allDepartments.items
      departmentTotal.value = allDepartments.total
      return `已加载 ${allDepartments.items.length} 个部门`
    })
    await runWorkspaceLoadingStage(runId, 'load-units', async () => {
      if (!await loadUnits()) throw new Error('单位接口没有返回成功')
      return `已加载 ${units.value.length} 个单位`
    })
    await runWorkspaceLoadingStage(runId, 'load-defects', async () => {
      if (!await loadDefects()) throw new Error('不良品项接口没有返回成功')
      return `已加载 ${defects.value.length} 个不良品项`
    })
    await runWorkspaceLoadingStage(runId, 'load-processes', async () => {
      if (!await loadProcesses()) throw new Error(errorMessage.value || '工序接口没有返回成功')
      return `已加载 ${processes.value.length} 道工序`
    })
    await runWorkspaceLoadingStage(runId, 'load-routes', async () => {
      if (!await loadRoutes()) throw new Error(errorMessage.value || '工艺路线接口没有返回成功')
      return `已加载 ${routes.value.length} 条工艺路线`
    })
    await runWorkspaceLoadingStage(runId, 'load-notifications', async () => {
      if (!await loadNotificationCount()) throw new Error(notificationError.value || '通知数量接口没有返回成功')
      return `已同步 ${notificationUnreadCount.value} 条未读通知`
    })
    await runWorkspaceLoadingStage(runId, 'load-plugin-manifest', async () => {
      await loadPluginRuntimeManifest()
      return runtimePlugins.value.length > 0
        ? `已挂载 ${runtimePlugins.value.length} 个插件入口`
        : '当前工厂没有启用插件'
    })
    await runWorkspaceLoadingStage(runId, 'prepare-workspace', async () => {
      await nextTick()
      await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
      return '工作区已准备完成'
    })

    if (runId !== workspaceBootstrapRun) return false
    workspaceLoadingProgress.value = 100
    workspaceLoadingStage.value = '加载完成'
    await new Promise(resolve => setTimeout(resolve, 220))
    if (runId === workspaceBootstrapRun) workspaceLoadingVisible.value = false
    return true
  } catch (error) {
    if (runId === workspaceBootstrapRun) {
      workspaceLoadingVisible.value = true
      if (!workspaceLoadingError.value) {
        workspaceLoadingError.value = error instanceof Error ? error.message : '工作区初始化失败'
      }
    }
    return false
  }
}

function retryWorkspaceBootstrap() {
  void bootstrapWorkspace()
}

function persistSession(response: LoginResponse) {
  sharedAuth.persistSession(response)
  // 登录后默认进入主页仪表盘
  activeMainMenu.value = 'home'
  startNotificationPolling()
}

const genericCustomFieldScopes: Record<string, string> = {
  product: '产品',
  bom: '物料清单',
  process: '工序',
  reporting: '报工',
  customer: '客户'
}

const searchQuery = ref('')
function handleSearch() {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return

  const menuMap: Array<{ label: string; mainMenu: string; subMenu: string; expandedMenu: string }> = [
    { label: '销售订单', mainMenu: 'production', subMenu: 'sales', expandedMenu: 'production' },
    { label: '工单', mainMenu: 'production', subMenu: 'workorder', expandedMenu: 'production' },
    { label: '任务', mainMenu: 'production', subMenu: 'task', expandedMenu: 'production' },
    { label: '报工', mainMenu: 'production', subMenu: 'reporting', expandedMenu: 'production' },
    { label: '报工工作台', mainMenu: 'production', subMenu: 'workbench', expandedMenu: 'production' },
    { label: '产品定义', mainMenu: 'basedata', subMenu: 'product', expandedMenu: 'basedata' },
    { label: '物料清单', mainMenu: 'basedata', subMenu: 'bom', expandedMenu: 'basedata' },
    { label: '单位', mainMenu: 'basedata', subMenu: 'unit', expandedMenu: 'basedata' },
    { label: '不良品项', mainMenu: 'basedata', subMenu: 'defect', expandedMenu: 'basedata' },
    { label: '工序', mainMenu: 'basedata', subMenu: 'process', expandedMenu: 'basedata' },
    { label: '工艺路线', mainMenu: 'basedata', subMenu: 'route', expandedMenu: 'basedata' },
    { label: '仓库', mainMenu: 'basedata', subMenu: 'warehouse', expandedMenu: 'basedata' },
    { label: '供应商', mainMenu: 'basedata', subMenu: 'supplier', expandedMenu: 'basedata' },
    { label: '客户', mainMenu: 'basedata', subMenu: 'customer', expandedMenu: 'basedata' },
    { label: '产品', mainMenu: 'custom', subMenu: 'product', expandedMenu: 'custom' },
    { label: '客户', mainMenu: 'custom', subMenu: 'customer', expandedMenu: 'custom' },
    { label: '部门', mainMenu: 'sysconfig', subMenu: 'department', expandedMenu: 'sysconfig' },
    { label: '用户', mainMenu: 'sysconfig', subMenu: 'user', expandedMenu: 'sysconfig' },
    { label: '插件中心', mainMenu: 'sysconfig', subMenu: 'plugin-center', expandedMenu: 'sysconfig' },
    ...(hasAdminAccess.value ? [{ label: '外部数据源', mainMenu: 'sysconfig', subMenu: 'external-data-sources', expandedMenu: 'sysconfig' }] : []),
    { label: '在线用户', mainMenu: 'monitor', subMenu: 'online', expandedMenu: 'monitor' },
    { label: '缓存监控', mainMenu: 'monitor', subMenu: 'cache', expandedMenu: 'monitor' },
    { label: '缓存列表', mainMenu: 'monitor', subMenu: 'cacheList', expandedMenu: 'monitor' },
    { label: '车间看板', mainMenu: 'dashboard', subMenu: '', expandedMenu: '' },
    { label: '分析报表', mainMenu: 'analytics', subMenu: '', expandedMenu: 'report' },
    { label: 'SOP', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '文档库', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '消息推送', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '企业微信', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '质检返工', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '设备工位', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '排产甘特', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '工资绩效', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '现场事件', mainMenu: 'advanced', subMenu: '', expandedMenu: 'advanced' },
    { label: '主页', mainMenu: 'home', subMenu: '', expandedMenu: '' }
  ]

  const runtimePlugin = runtimePlugins.value.find(item =>
    item.name.toLowerCase().includes(query) || item.pluginKey.toLowerCase().includes(query)
  )
  if (runtimePlugin) {
    openRuntimePlugin(runtimePlugin.pluginKey)
    searchQuery.value = ''
    return
  }

  const match = menuMap.find(m => m.label.includes(query))
  if (match) {
    activeMainMenu.value = match.mainMenu
    if (match.expandedMenu) {
      expandedMenus[match.expandedMenu as keyof typeof expandedMenus] = true
    }
    if (match.mainMenu === 'production') activeProductionMenu.value = match.subMenu
    else if (match.mainMenu === 'basedata') activeBasedataMenu.value = match.subMenu
    else if (match.mainMenu === 'custom') activeCustomMenu.value = match.subMenu
    else if (match.mainMenu === 'sysconfig') activeConfigMenu.value = match.subMenu
    else if (match.mainMenu === 'monitor') activeMonitorMenu.value = match.subMenu as 'online' | 'cache' | 'cacheList'
    searchQuery.value = ''
  }
}

async function handleLogin() {
  if (loading.value) return
  errorMessage.value = ''
  loading.value = true
  const submittedLogin = { ...loginForm }
  try {
    const response = await login(submittedLogin)
    if (rememberMe.value) {
      localStorage.setItem('gongdan_remember_me', JSON.stringify({
        remember: true,
        factoryCode: submittedLogin.factoryCode,
        username: submittedLogin.username
      }))
    } else {
      localStorage.removeItem('gongdan_remember_me')
    }
    persistSession(response)
    sharedAuth.setFactoryCode(submittedLogin.factoryCode)
    await bootstrapWorkspace()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}

// Phone login functions
async function handleSendSms() {
  if (!isPhoneValid.value) {
    phoneError.value = '请输入正确的手机号'
    return
  }
  if (smsCountdown.value > 0 || sendingSms.value) return
  sendingSms.value = true
  try {
    await sendSmsCode({ phone: phoneForm.phone })
    smsCountdown.value = 60
    smsTimer = setInterval(() => {
      smsCountdown.value--
      if (smsCountdown.value <= 0) {
        if (smsTimer) clearInterval(smsTimer)
        smsTimer = null
      }
    }, 1000)
  } catch (e) {
    phoneError.value = e instanceof Error ? e.message : '发送验证码失败'
  } finally {
    sendingSms.value = false
  }
}

async function handlePhoneLogin() {
  if (loading.value) return
  phoneError.value = ''
  if (!isPhoneValid.value) {
    phoneError.value = '请输入正确的手机号'
    return
  }
  loading.value = true
  try {
    let result: PhoneLoginResult
    if (phoneLoginMode.value === 'password') {
      if (!phoneForm.password) {
        phoneError.value = '请输入密码'
        loading.value = false
        return
      }
      result = await phonePasswordLogin({ phone: phoneForm.phone, password: phoneForm.password })
      pendingPhoneCredential.phone = phoneForm.phone
      pendingPhoneCredential.loginType = 'password'
      pendingPhoneCredential.credential = phoneForm.password
    } else {
      if (!phoneForm.code) {
        phoneError.value = '请输入验证码'
        loading.value = false
        return
      }
      result = await phoneSmsLogin({ phone: phoneForm.phone, code: phoneForm.code })
      pendingPhoneCredential.phone = phoneForm.phone
      pendingPhoneCredential.loginType = 'sms'
      pendingPhoneCredential.credential = phoneForm.code
    }
    await handlePhoneLoginResult(result)
  } catch (e) {
    phoneError.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}

async function handlePhoneLoginResult(result: PhoneLoginResult) {
  if (result.bindingRequired && result.phone) {
    // 需要绑定工厂，携带手机号跳转到绑定页面，不显示错误提示
    bindingPhone.value = result.phone
    showBindFactory.value = true
  } else if (result.selectRequired && result.factories) {
    factoryList.value = result.factories
    showFactorySelect.value = true
  } else if (result.token && result.user) {
    persistSession({ token: result.token, user: result.user, isSuperAdmin: false })
    await bootstrapWorkspace()
  }
}

function handleBindSuccess(payload: { token: string; user: any }) {
  showBindFactory.value = false
  bindingPhone.value = ''
  // 绑定成功后不自动登录，用户需要使用默认密码重新登录
  // BindFactoryPage 组件内部已处理成功状态展示
}

async function handleSelectFactory(factoryId: number) {
  if (loading.value) return
  loading.value = true
  phoneError.value = ''
  try {
    const result = await selectFactory({
      phone: pendingPhoneCredential.phone,
      factoryId,
      loginType: pendingPhoneCredential.loginType as 'password' | 'sms',
      credential: pendingPhoneCredential.credential
    })
    if (result.token && result.user) {
      persistSession({ token: result.token, user: result.user, isSuperAdmin: false })
      const factory = factoryList.value.find(f => f.factoryId === factoryId)
      if (factory) {
        sharedAuth.setFactoryCode(factory.factoryCode)
      }
      showFactorySelect.value = false
      await bootstrapWorkspace()
    }
  } catch (e) {
    phoneError.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}

async function loadUsers(): Promise<boolean> {
  if (!token.value) {
    return false
  }
  usersLoading.value = true
  try {
    users.value = await fetchUsers(token.value)
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '用户列表加载失败'
    return false
  } finally {
    usersLoading.value = false
  }
}

async function loadDepartments(): Promise<boolean> {
  if (!token.value) return false
  departmentsLoading.value = true
  try {
    const res = await fetchDepartments(token.value, departmentFilterForm)
    departments.value = res.items
    departmentTotal.value = res.total
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '部门加载失败'
    return false
  } finally {
    departmentsLoading.value = false
  }
}

async function loadAllUsersForDepartment() {
  if (!token.value) return
  try {
    const allUsers = await fetchUsers(token.value)
    allUsersList.value = allUsers
  } catch (error) {
    console.error('Failed to load all users', error)
  }
}

function handleDepartmentPageChange(dir: number) {
  const newPage = departmentFilterForm.page + dir
  if (newPage < 1) return
  const maxPage = Math.ceil(departmentTotal.value / departmentFilterForm.size) || 1
  if (newPage > maxPage) return
  departmentFilterForm.page = newPage
  loadDepartments()
}

function resetDepartmentFilter() {
  departmentFilterForm.code = ''
  departmentFilterForm.name = ''
  departmentFilterForm.page = 1
  loadDepartments()
}

function openCreateDepartmentModal() {
  editingDepartment.value = null
  departmentForm.code = ''
  departmentForm.name = ''
  departmentForm.userIds = []
  showDepartmentModal.value = true
  loadAllUsersForDepartment()
}

function openEditDepartmentModal(dept: DepartmentInfo) {
  editingDepartment.value = dept
  departmentForm.code = dept.code
  departmentForm.name = dept.name
  // We need the user IDs in this department. 
  // Since DepartmentInfo only has userNames string, we fetch all users and map them.
  loadAllUsersForDepartment().then(() => {
    departmentForm.userIds = allUsersList.value
      .filter(u => u.departmentId === dept.id)
      .map(u => u.id)
    showDepartmentModal.value = true
  })
}

async function handleSaveDepartment() {
  if (!token.value) return
  loading.value = true
  try {
    if (editingDepartment.value) {
      await updateDepartment(token.value, editingDepartment.value.id, departmentForm)
    } else {
      await createDepartment(token.value, departmentForm)
    }
    showDepartmentModal.value = false
    loadDepartments()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    loading.value = false
  }
}

function confirmDeleteDepartment(dept: DepartmentInfo) {
  confirmState.title = '删除确认'
  confirmState.message = `是否确要删除「${dept.name}」？其下用户的部门信息将被清空。`
  confirmState.action = async () => {
    if (!token.value) return
    loading.value = true
    try {
      await deleteDepartment(token.value, dept.id)
      loadDepartments()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '删除失败'
    } finally {
      loading.value = false
      confirmState.show = false
    }
  }
  confirmState.show = true
}

function resetCreateForm() {
  createForm.username = ''
  createForm.phone = ''
  createForm.name = ''
  createForm.password = ''
  createForm.role = 'ADMIN'
  createForm.departmentId = undefined
  createForm.virtualUser = false
}

async function handleCreateUser() {
  if (!token.value) {
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const created = await createUser(token.value, {
      ...createForm,
      username: createForm.username.trim(),
      phone: createForm.phone?.trim(),
      name: createForm.name.trim(),
      password: createForm.password,
      role: createForm.role as Role,
      departmentId: createForm.departmentId,
      virtualUser: Boolean(createForm.virtualUser)
    })
    await loadUsers()
    successUser.value = created
    successUserPassword.value = createForm.password
    showCreateModal.value = false
    resetCreateForm()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '创建用户失败'
  } finally {
    loading.value = false
  }
}

function syncCurrentUser(updated: UserInfo) {
  if (currentUser.value?.id === updated.id) {
    currentUser.value = updated
    localStorage.setItem('heihu_user', JSON.stringify(updated))
  }
}

async function copySuccessUser() {
  if (!successUser.value) return
  const text = `账号：${successUser.value.username}\n姓名：${successUser.value.name}\n角色：${successUser.value.roleLabel}\n部门：${successUser.value.departmentName || '无'}\n密码：${successUserPassword.value}`
  try {
    await navigator.clipboard.writeText(text)
    showAction('账号信息已复制到剪贴板')
  } catch (err) {
    errorMessage.value = '复制失败，请手动复制'
  }
}

function showAction(message: string) {
  actionMessage.value = message
}

function openEditUser(user: UserInfo) {
  editingUser.value = user
  editForm.username = user.username
  editForm.phone = user.phone || ''
  editForm.name = user.name
  editForm.role = user.role
  editForm.departmentId = user.departmentId
  editForm.virtualUser = user.virtualUser
  showEditModal.value = true
}

async function handleUpdateUser() {
  if (!token.value || !editingUser.value) {
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const updated = await updateUser(token.value, editingUser.value.id, {
      username: editForm.username.trim(),
      phone: editForm.phone?.trim(),
      name: editForm.name.trim(),
      role: editForm.role,
      departmentId: editForm.departmentId,
      virtualUser: Boolean(editForm.virtualUser)
    })
    syncCurrentUser(updated)
    await loadUsers()
    showEditModal.value = false
    showAction(`用户「${updated.name}」已更新`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '更新用户失败'
  } finally {
    loading.value = false
  }
}

function openPermissionConfig(user: UserInfo) {
  permissionUser.value = user
  permissionRole.value = user.role
  showPermissionModal.value = true
}

async function handleUpdatePermission() {
  if (!token.value || !permissionUser.value) {
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const updated = await updateUserPermission(token.value, permissionUser.value.id, permissionRole.value)
    syncCurrentUser(updated)
    await loadUsers()
    showPermissionModal.value = false
    showAction(`用户「${updated.name}」权限已更新为${updated.roleLabel}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '权限配置失败'
  } finally {
    loading.value = false
  }
}

function openResetPassword(user: UserInfo) {
  resetPasswordUser.value = user
  resetPasswordForm.password = ''
  resetPasswordForm.confirmPassword = ''
  showNewPassword.value = false
  showConfirmPassword.value = false
  showResetPasswordModal.value = true
}

async function handleResetPassword() {
  if (!token.value || !resetPasswordUser.value) {
    return
  }
  const newPassword = resetPasswordForm.password
  const confirmPassword = resetPasswordForm.confirmPassword
  if (!newPassword.trim()) {
    errorMessage.value = '新密码不能为空'
    return
  }
  if (newPassword !== confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  errorMessage.value = ''
  loading.value = true
  try {
    const result = await resetUserPassword(token.value, resetPasswordUser.value.id, newPassword)
    showAction(`用户「${resetPasswordUser.value.name}」的密码已重置，新密码：${newPassword}`)
    showResetPasswordModal.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '重置密码失败'
  } finally {
    loading.value = false
  }
}

function confirmUnbindPhone(user: UserInfo) {
  if (!user.phone) {
    showAction(`用户「${user.name}」当前没有绑定手机号`)
    return
  }
  confirmState.title = '解绑手机号'
  confirmState.message = `是否解绑「${user.name}」的手机号 ${user.phone} 吗？`
  confirmState.action = () => handleUnbindPhone(user)
  confirmState.show = true
}

async function handleUnbindPhone(user: UserInfo) {
  if (!token.value) {
    return
  }
  confirmState.show = false
  errorMessage.value = ''
  loading.value = true
  try {
    const updated = await unbindUserPhone(token.value, user.id)
    syncCurrentUser(updated)
    await loadUsers()
    showAction(`用户「${updated.name}」手机号已解绑`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '解绑手机号失败'
  } finally {
    loading.value = false
  }
}

function confirmToggleEnabled(user: UserInfo) {
  const nextEnabled = !user.enabled
  const action = nextEnabled ? '启用' : '停用'
  confirmState.title = `${action}用户`
  confirmState.message = `是否${action}用户「${user.name}」？`
  confirmState.action = () => handleToggleEnabled(user, nextEnabled, action)
  confirmState.show = true
}

async function handleToggleEnabled(user: UserInfo, nextEnabled: boolean, actionName: string) {
  if (!token.value) {
    return
  }
  confirmState.show = false
  errorMessage.value = ''
  loading.value = true
  try {
    const updated = await updateUserStatus(token.value, user.id, nextEnabled)
    syncCurrentUser(updated)
    await loadUsers()
    showAction(`用户「${updated.name}」已${actionName}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : `${actionName}用户失败`
  } finally {
    loading.value = false
  }
}

function handlePrintUser(user: UserInfo) {
  const printWindow = window.open('', '_blank', 'width=560,height=680')
  if (!printWindow) {
    errorMessage.value = '浏览器阻止打印窗口，请允许弹窗后重试'
    return
  }
  const loginScope = user.role === 'ADMIN' ? '电脑端 + 手机端' : '仅手机端'
  printWindow.document.write(`
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <meta charset="UTF-8" />
        <title>用户信息 - ${user.name}</title>
        <style>
          body { font-family: "Microsoft YaHei", Arial, sans-serif; padding: 32px; color: #1f2725; }
          h1 { font-size: 22px; margin: 0 0 24px; }
          table { width: 100%; border-collapse: collapse; }
          td { border: 1px solid #dfe7e4; padding: 12px; font-size: 14px; }
          td:first-child { width: 120px; background: #f6f8f7; color: #59635f; }
        </style>
      </head>
      <body>
        <h1>小工单用户信息</h1>
        <table>
          <tr><td>账号</td><td>${user.username}</td></tr>
          <tr><td>姓名</td><td>${user.name}</td></tr>
          <tr><td>角色</td><td>${user.roleLabel}</td></tr>
          <tr><td>登录范围</td><td>${loginScope}</td></tr>
          <tr><td>手机号</td><td>${user.phone || '-'}</td></tr>
          <tr><td>部门</td><td>${user.departmentName || '-'}</td></tr>
          <tr><td>状态</td><td>${user.enabled ? '启用中' : '停用中'}</td></tr>
          <tr><td>虚拟用户</td><td>${user.virtualUser ? '是' : '否'}</td></tr>
        </table>
      </body>
    </html>
  `)
  printWindow.document.close()
  printWindow.focus()
  printWindow.print()
}

async function handleExport() {
  if (!token.value) {
    errorMessage.value = '请先登录'
    return
  }
  try {
    await exportUsersCsv(token.value)
    showAction('用户数据导出成功（含密码哈希）')
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '导出失败'
  }
}

function triggerImport() {
  if (fileInput.value) {
    fileInput.value.value = ''
    fileInput.value.click()
  }
}

async function handleImport(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files || target.files.length === 0) return
  if (!token.value) {
    errorMessage.value = '请先登录'
    return
  }

  const file = target.files[0]
  const fileName = file.name

  try {
    const result = await importUsersCsv(token.value, file)
    importResult.value = result

    importLogs.value.unshift({
      time: new Date().toLocaleString('zh-CN'),
      fileName,
      total: result.total,
      success: result.success,
      fail: result.fail,
      errors: result.errors
    })

    await loadUsers()

    if (result.fail === 0) {
      showAction(`导入完成：共 ${result.total} 条，全部成功`)
    } else {
      showAction(`导入完成：共 ${result.total} 条，成功 ${result.success} 条，失败 ${result.fail} 条`)
    }
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '导入失败'
  }
  target.value = ''
}

function openImportLog() {
  showImportLogModal.value = true
}

function handlePrintTemplate() {
  const dataToprint = selectedUserIds.value.length > 0
    ? filteredUsers.value.filter(u => selectedUserIds.value.includes(u.id))
    : filteredUsers.value
  if (dataToprint.length === 0) {
    errorMessage.value = '当前没有可打印的用户数据'
    return
  }
  const printWindow = window.open('', '_blank', 'width=900,height=700')
  if (!printWindow) {
    errorMessage.value = '浏览器阻止打印窗口，请允许弹窗后重试'
    return
  }
  const rows = dataToprint.map((u, i) => `
    <tr>
      <td>${i + 1}</td>
      <td>${u.departmentName || '-'}</td>
      <td>${u.username}</td>
      <td>${u.name}</td>
      <td>${u.roleLabel}</td>
      <td>${u.phone || '-'}</td>
      <td>${u.enabled ? '启用' : '停用'}</td>
    </tr>
  `).join('')
  printWindow.document.write(`
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <meta charset="UTF-8" />
        <title>用户列表打印</title>
        <style>
          body { font-family: "Microsoft YaHei", Arial, sans-serif; padding: 24px; color: #1f2725; }
          h1 { font-size: 20px; margin: 0 0 16px; text-align: center; }
          .info { font-size: 12px; color: #87918e; text-align: right; margin-bottom: 12px; }
          table { width: 100%; border-collapse: collapse; }
          th, td { border: 1px solid #dfe7e4; padding: 8px 12px; font-size: 13px; text-align: left; }
          th { background: #f6f8f7; color: #59635f; font-weight: 700; }
        </style>
      </head>
      <body>
        <h1>小工单 - 用户列表</h1>
        <p class="info">打印时间：${new Date().toLocaleString('zh-CN')}，共 ${dataToprint.length} 人</p>
        <table>
          <thead>
            <tr><th>序号</th><th>部门</th><th>账号</th><th>姓名</th><th>角色</th><th>手机号</th><th>状态</th></tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
        <script>window.print();<\/script>
      </body>
    </html>
  `)
  printWindow.document.close()
  printWindow.focus()
}

function resetAuthenticatedWorkspace() {
  workspaceBootstrapRun += 1
  workspaceLoadingVisible.value = false
  users.value = []
  departments.value = []
  units.value = []
  defects.value = []
  processes.value = []
  routes.value = []
  selectedUserIds.value = []
  tableData.value = []
  taskProgressData.value = []
  dashboardReports.value = []
  workspaceTabs.value = [{ id: 'home', label: '分析页', main: 'home', closable: false, pinned: true }]
  activeWorkspaceTabId.value = 'home'
  for (const modal of [showCreateModal, showEditModal, showPermissionModal, showImportLogModal,
    showResetPasswordModal, showEditPrintTemplateModal, showUnitModal, showDefectModal,
    showDepartmentModal, showUserDropdown, showChromeQuickMenu, showQuickApps,
    showTimezoneDialog, showWorkspaceSearch, showFactorySelect, showBindFactory]) {
    modal.value = false
  }
  confirmState.show = false
  confirmState.action = () => {}
  successUser.value = null
  successUserPassword.value = ''
  editingUser.value = null
  permissionUser.value = null
  resetPasswordUser.value = null
  resetPasswordForm.password = ''
  resetPasswordForm.confirmPassword = ''
  loginForm.password = ''
  phoneForm.password = ''
  phoneForm.code = ''
  pendingPhoneCredential.credential = ''
  runtimePlugins.value = []
  activePluginKey.value = ''
  notificationItems.value = []
  notificationUnreadCount.value = 0
  notificationError.value = ''
  if (notificationPollTimer) {
    clearInterval(notificationPollTimer)
    notificationPollTimer = null
  }
  showNotificationDropdown.value = false
  // 退出登录时重置主菜单和子菜单的焦点
  activeMainMenu.value = 'home'
  activeConfigMenu.value = 'department'
}

watch(token, (nextToken, previousToken) => {
  if (previousToken && previousToken !== nextToken) resetAuthenticatedWorkspace()
}, { flush: 'sync' })

function logout() {
  sharedAuth.logout()
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncNativeFullscreenState)

  if (token.value && currentUser.value && !isInternalChatWindow.value) {
    // 页面刷新后默认进入主页仪表盘，并显示可诊断的初始化进度。
    activeMainMenu.value = 'home'
    startNotificationPolling()
    void bootstrapWorkspace()
  } else {
    workspaceLoadingVisible.value = false
  }

  window.addEventListener('click', (e) => {
    const target = e.target as HTMLElement
    if (!target.closest('.user-profile')) {
      showUserDropdown.value = false
    }
    if (!target.closest('.notification-wrap')) {
      showNotificationDropdown.value = false
    }
    if (!target.closest('.custom-multiselect')) {
      showUserSelectDropdown.value = false
    }
    // 报工权限下拉：点击非其内部区域时收起
    if (!target.closest('.perm-dropdown-wrap')) {
      showProcessPermDropdown.value = false
    }
    // 不良品项下拉：点击非其内部区域时收起
    if (!target.closest('.defect-dropdown-wrap')) {
      showProcessDefectDropdown.value = false
    }
    // 工序下拉框：点击非其内部区域时收起
    if (!target.closest('.route-step-select') && !target.closest('.route-step-dropdown')) {
      closeAllRouteStepDropdowns()
    }
    if (!target.closest('.chrome-quick-wrap')) {
      showChromeQuickMenu.value = false
    }
    if (!target.closest('.chrome-dropdown-wrap')) {
      showQuickApps.value = false
      showLanguageMenu.value = false
    }
    if (!target.closest('.tab-context-menu') && !target.closest('.workspace-tabs')) {
      closeTabContextMenu()
    }
  })

  window.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault()
      focusWorkspaceSearch()
    }
  })
  document.addEventListener('fullscreenchange', () => {
    isAppFullscreen.value = Boolean(document.fullscreenElement)
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncNativeFullscreenState)
  if (notificationPollTimer) clearInterval(notificationPollTimer)
  if (smsTimer) clearInterval(smsTimer)
  if (errorTimer) clearTimeout(errorTimer)
  if (actionTimer) clearTimeout(actionTimer)
})

const formatDateTime = (val: string) => {
  if (!val) return '-';
  const d = new Date(val);
  if (Number.isNaN(d.getTime())) return '-';
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: selectedTimezone.value,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(d).replace(/\//g, '-');
  } catch {
    // Fall back to the browser timezone if a stored timezone is no longer valid.
  }
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  const seconds = String(d.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
};

function handleNavigate(menu: { main: string; sub: string }) {
  activeMainMenu.value = menu.main
  if (menu.main === 'inventory') {
    activeInventoryMenu.value = menu.sub
  }
}
</script>

<template>
  <main v-if="!isLoggedIn" class="login-page">
    <section class="login-hero">
      <div class="brand">
        <span class="brand-mark"><img class="logo-mark" src="/logo.jpg" alt="数字工单通" /></span>
        <span class="brand-name">
          <strong>数字工单通</strong>
          <small>生产现场协同平台</small>
        </span>
      </div>
      <div class="hero-copy">
        <p class="hero-kicker">轻量化生产执行与现场协同</p>
        <h1>让每一张工单<br />都清楚、及时、可追溯</h1>
        <p class="hero-description">从计划下达到移动报工，把人员、设备、物料和质量信息沉淀在同一条生产链路中。</p>
        <div class="feature-grid">
          <div class="feature-item">
            <span class="feature-icon">01</span>
            <div>
              <strong>扫码接单，移动作业</strong>
              <p>一线工人手机扫码，无纸化生产</p>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">02</span>
            <div>
              <strong>计时计件薪资实时核算</strong>
              <p>解放财务劳动力，绩效薪资实时核算</p>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-icon">03</span>
            <div>
              <strong>工厂全局大屏监控</strong>
              <p>一站式数据管理驱动生产</p>
            </div>
          </div>
        </div>
        <div class="hero-assurance"><span></span> 本地部署可用 · 数据留在企业内部</div>
      </div>
    </section>
    <section class="login-panel">
      <div class="login-box">
        <div class="login-heading">
          <span>账号登录</span>
          <h2>进入生产工作台</h2>
          <p class="login-sub">使用工厂账号或已绑定的手机号登录</p>
        </div>

        <!-- Tab switcher -->
        <div v-if="!showBindFactory && !showFactorySelect" class="login-tabs">
          <span class="login-tab" :class="{ active: loginTab === 'factory' }" @click="loginTab = 'factory'">工厂账号登录</span>
          <span class="login-tab" :class="{ active: loginTab === 'phone' }" @click="loginTab = 'phone'">手机号登录</span>
        </div>

        <!-- Factory select overlay -->
        <div v-if="showFactorySelect" class="factory-select-panel">
          <h3>请选择工厂</h3>
          <div class="factory-list">
            <div v-for="f in factoryList" :key="f.factoryId" class="factory-item" @click="handleSelectFactory(f.factoryId)">
              <span class="factory-name-label">{{ f.factoryName }}</span>
              <span class="factory-code-label">{{ f.factoryCode }}</span>
            </div>
          </div>
          <p v-if="phoneError" class="form-error">{{ phoneError }}</p>
          <button class="text-btn" type="button" @click="showFactorySelect = false">返回</button>
        </div>

        <!-- Bind factory overlay -->
        <BindFactoryPage
          v-if="showBindFactory"
          :phone="bindingPhone"
          @back="showBindFactory = false; bindingPhone = ''"
          @bound="handleBindSuccess"
        />

        <!-- Phone login form -->
        <form v-if="loginTab === 'phone' && !showFactorySelect && !showBindFactory" class="login-form" @submit.prevent="handlePhoneLogin">
          <div class="phone-mode-switch">
            <span :class="{ active: phoneLoginMode === 'password' }" @click="phoneLoginMode = 'password'">密码登录</span>
            <span class="mode-divider">|</span>
            <span :class="{ active: phoneLoginMode === 'sms' }" @click="phoneLoginMode = 'sms'">验证码登录</span>
          </div>
          <label>
            <span>手机号</span>
            <input v-model="phoneForm.phone" autocomplete="tel" placeholder="请输入手机号" maxlength="11" />
          </label>
          <label v-if="phoneLoginMode === 'password'">
            <span>密码</span>
            <input v-model="phoneForm.password" autocomplete="current-password" placeholder="请输入密码" type="password" />
          </label>
          <label v-else class="sms-label">
            <span>验证码</span>
            <div class="sms-input-row">
              <input v-model="phoneForm.code" placeholder="请输入验证码" maxlength="6" />
              <button type="button" class="sms-btn" :disabled="sendingSms || smsCountdown > 0 || !isPhoneValid" @click="handleSendSms">
                {{ smsCountdown > 0 ? smsCountdown + 's后重新获取' : '获取验证码' }}
              </button>
            </div>
          </label>
          <p v-if="phoneError" class="form-error">{{ phoneError }}</p>
          <button class="primary-btn login-btn" :disabled="loading" type="submit">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <!-- Factory account login form (existing) -->
        <form v-if="loginTab === 'factory' && !showFactorySelect && !showBindFactory" class="login-form" @submit.prevent="handleLogin">
          <label>
            <span>工厂代码</span>
            <input v-model="loginForm.factoryCode" autocomplete="organization" placeholder="请输入工厂代码" />
          </label>
          <label>
            <span>用户名</span>
            <input v-model="loginForm.username" autocomplete="username" placeholder="请输入用户名" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" autocomplete="current-password" placeholder="请输入密码" type="password" />
          </label>
          <div class="remember-me-row">
            <label class="remember-me-label">
              <input type="checkbox" v-model="rememberMe" />
              <span>记住账号</span>
            </label>
          </div>
          <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
          <button class="primary-btn login-btn" :disabled="loading" type="submit">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>
        <p v-if="!showFactorySelect && !showBindFactory" class="login-help">账号由工厂管理员统一创建与维护</p>
      </div>
    </section>
  </main>

  <main v-else-if="isInternalChatWindow" class="admin-shell internal-chat-window-shell" :class="{ 'dark-theme': isDarkTheme }">
    <InternalChatPage
      :token="token"
      :current-user="currentUser"
    />
  </main>

  <main v-else class="admin-shell" :class="{ 'sidebar-collapsed': isSidebarCollapsed, 'workspace-maximized': isWorkspaceMaximized, 'app-fullscreen': isAppFullscreen, 'dark-theme': isDarkTheme }">
    <aside class="sidebar">
      <button class="collapse-toggle" type="button" @click="isSidebarCollapsed = !isSidebarCollapsed">
        <svg v-if="!isSidebarCollapsed" viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M15 18l-6-6 6-6"/></svg>
        <svg v-else viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M9 18l6-6-6-6"/></svg>
      </button>
      <div class="factory-name" v-show="!isSidebarCollapsed" :title="currentFactoryCode">{{ currentFactoryCode }}</div>
      <nav v-if="hasAdminAccess">
        <a v-if="currentUser && currentUser.role === 'ADMIN'" class="nav-item" :class="{ 'active-parent': activeMainMenu === 'home' }" :title="isSidebarCollapsed ? '主页' : ''" @click="activeMainMenu = 'home'">
          <UiIcon name="home" :size="18" />
          <span v-show="!isSidebarCollapsed">主页</span>
        </a>

        <div class="nav-group">
          <a class="nav-item" :title="isSidebarCollapsed ? '生产管理' : ''" @click.stop="toggleMenu('production')">
            <UiIcon name="file" :size="18" />
            <span v-show="!isSidebarCollapsed">生产管理</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.production }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.production" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'production' && activeProductionMenu === 'sales' }" @click="activeMainMenu = 'production'; activeProductionMenu = 'sales'"><UiIcon name="file" :size="15" />销售订单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'production' && activeProductionMenu === 'workorder' }" @click="activeMainMenu = 'production'; activeProductionMenu = 'workorder'"><UiIcon name="file" :size="15" />工单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'production' && activeProductionMenu === 'task' }" @click="activeMainMenu = 'production'; activeProductionMenu = 'task'"><UiIcon name="calendar" :size="15" />任务</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'production' && activeProductionMenu === 'reporting' }" @click="activeMainMenu = 'production'; activeProductionMenu = 'reporting'"><UiIcon name="activity" :size="15" />报工</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'production' && activeProductionMenu === 'workbench' }" @click="activeMainMenu = 'production'; activeProductionMenu = 'workbench'"><UiIcon name="check" :size="15" />报工工作台</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item" :title="isSidebarCollapsed ? '上下游管理' : ''" @click.stop="toggleMenu('upstream')">
            <UiIcon name="share" :size="18" />
            <span v-show="!isSidebarCollapsed">上下游管理</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.upstream }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.upstream" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'customer' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'customer'; expandedMenus.basedata = true"><UiIcon name="user" :size="15" />客户</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'supplier' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'supplier'; expandedMenus.basedata = true"><UiIcon name="share" :size="15" />供应商</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item" :title="isSidebarCollapsed ? '库存管理' : ''" @click.stop="toggleMenu('inventory')">
            <UiIcon name="layers" :size="18" />
            <span v-show="!isSidebarCollapsed">库存管理</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.inventory }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.inventory" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'inventory' && activeInventoryMenu === 'inbound' }" @click="activeMainMenu = 'inventory'; activeInventoryMenu = 'inbound'"><UiIcon name="download" :size="15" />入库单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'inventory' && activeInventoryMenu === 'outbound' }" @click="activeMainMenu = 'inventory'; activeInventoryMenu = 'outbound'"><UiIcon name="upload" :size="15" />出库单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'inventory' && activeInventoryMenu === 'detail' }" @click="activeMainMenu = 'inventory'; activeInventoryMenu = 'detail'"><UiIcon name="list" :size="15" />库存收发明细</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'inventory' && activeInventoryMenu === 'balance' }" @click="activeMainMenu = 'inventory'; activeInventoryMenu = 'balance'"><UiIcon name="layers" :size="15" />库存余额</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'inventory' && activeInventoryMenu === 'summary' }" @click="activeMainMenu = 'inventory'; activeInventoryMenu = 'summary'"><UiIcon name="activity" :size="15" />进销存汇总</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item" :title="isSidebarCollapsed ? '基础数据' : ''" @click.stop="toggleMenu('basedata')">
            <UiIcon name="list" :size="18" />
            <span v-show="!isSidebarCollapsed">基础数据</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.basedata }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.basedata" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'product' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'product'"><UiIcon name="package" :size="15" />产品定义</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'bom' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'bom'"><UiIcon name="layers" :size="15" />物料清单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'unit' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'unit'"><UiIcon name="table-columns" :size="15" />单位</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'defect' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'defect'"><UiIcon name="warning" :size="15" />不良品项</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'process' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'process'; processView = 'list'"><UiIcon name="settings" :size="15" />工序</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'route' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'route'; routeView = 'list'"><UiIcon name="share" :size="15" />工艺路线</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'warehouse' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'warehouse'"><UiIcon name="home" :size="15" />仓库</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'supplier' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'supplier'"><UiIcon name="share" :size="15" />供应商</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'basedata' && activeBasedataMenu === 'customer' }" @click="activeMainMenu = 'basedata'; activeBasedataMenu = 'customer'"><UiIcon name="user" :size="15" />客户</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item" :title="isSidebarCollapsed ? '自定义配置' : ''" @click.stop="toggleMenu('custom')">
            <UiIcon name="table-columns" :size="18" />
            <span v-show="!isSidebarCollapsed">自定义配置</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.custom }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.custom" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'product' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'product'"><UiIcon name="package" :size="15" />产品</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'bom' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'bom'"><UiIcon name="layers" :size="15" />物料清单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'process' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'process'"><UiIcon name="settings" :size="15" />工序</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'workorder' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'workorder'"><UiIcon name="file" :size="15" />工单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'reporting' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'reporting'"><UiIcon name="activity" :size="15" />报工</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'customer' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'customer'"><UiIcon name="user" :size="15" />客户</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'receipt' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'receipt'"><UiIcon name="archive" :size="15" />出入库单</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'custom' && activeCustomMenu === 'receipt_detail' }" @click="activeMainMenu = 'custom'; activeCustomMenu = 'receipt_detail'"><UiIcon name="list" :size="15" />出入库明细</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item" :title="isSidebarCollapsed ? '高级运营' : ''" @click.stop="toggleMenu('advanced')">
            <UiIcon name="file" :size="18" />
            <span v-show="!isSidebarCollapsed">高级运营</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.advanced }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.advanced" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'advanced' }" @click="activeMainMenu = 'advanced'"><UiIcon name="settings" :size="15" />SOP / 推送 / 质检 / 设备</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'advanced' }" @click="activeMainMenu = 'advanced'"><UiIcon name="activity" :size="15" />排产 / 绩效 / 现场</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item" :title="isSidebarCollapsed ? '报表' : ''" @click.stop="toggleMenu('report')">
            <UiIcon name="activity" :size="18" />
            <span v-show="!isSidebarCollapsed">报表</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.report }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.report" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'report' }" @click="activeMainMenu = 'report'"><UiIcon name="activity" :size="15" />生产报表</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'analytics' }" @click="activeMainMenu = 'analytics'"><UiIcon name="table-columns" :size="15" />分析报表</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item expanded" :title="isSidebarCollapsed ? '系统配置' : ''" @click.stop="toggleMenu('sysconfig')">
            <UiIcon name="settings" :size="18" />
            <span v-show="!isSidebarCollapsed">系统配置</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.sysconfig }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.sysconfig" class="sub-items-container">
              <a class="sub-item" :class="{ 'selected': activeMainMenu === 'sysconfig' && activeConfigMenu === 'department' }" @click="activeMainMenu = 'sysconfig'; activeConfigMenu = 'department'"><UiIcon name="share" :size="15" />部门</a>
              <a class="sub-item" :class="{ 'selected': activeMainMenu === 'sysconfig' && activeConfigMenu === 'user' }" @click="activeMainMenu = 'sysconfig'; activeConfigMenu = 'user'"><UiIcon name="user" :size="15" />用户</a>
              <a class="sub-item" :class="{ 'selected': activeMainMenu === 'sysconfig' && activeConfigMenu === 'factory' }" @click="activeMainMenu = 'sysconfig'; activeConfigMenu = 'factory'"><UiIcon name="home" :size="15" />工厂</a>
              <a class="sub-item" :class="{ 'selected': activeMainMenu === 'sysconfig' && activeConfigMenu === 'plugin-center' }" @click="activeMainMenu = 'sysconfig'; activeConfigMenu = 'plugin-center'"><UiIcon name="layers" :size="15" />插件中心</a>
              <a v-if="hasAdminAccess" class="sub-item" :class="{ 'selected': activeMainMenu === 'sysconfig' && activeConfigMenu === 'external-data-sources' }" @click="activeMainMenu = 'sysconfig'; activeConfigMenu = 'external-data-sources'"><UiIcon name="layers" :size="15" />外部数据源</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item expanded" :title="isSidebarCollapsed ? '系统监控' : ''" @click.stop="toggleMenu('monitor')">
            <UiIcon name="activity" :size="18" />
            <span v-show="!isSidebarCollapsed">系统监控</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.monitor }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.monitor" class="sub-items-container">
              <a class="sub-item" :class="{ selected: activeMainMenu === 'monitor' && activeMonitorMenu === 'online' }" @click="activeMainMenu = 'monitor'; activeMonitorMenu = 'online'"><UiIcon name="user" :size="15" />在线用户</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'monitor' && activeMonitorMenu === 'cache' }" @click="activeMainMenu = 'monitor'; activeMonitorMenu = 'cache'"><UiIcon name="activity" :size="15" />缓存监控</a>
              <a class="sub-item" :class="{ selected: activeMainMenu === 'monitor' && activeMonitorMenu === 'cacheList' }" @click="activeMainMenu = 'monitor'; activeMonitorMenu = 'cacheList'"><UiIcon name="list" :size="15" />缓存列表</a>
            </div>
          </Transition>
        </div>

        <div v-if="runtimePlugins.length > 0" class="nav-group">
          <a class="nav-item expanded" :title="isSidebarCollapsed ? '插件扩展' : ''" @click.stop="toggleMenu('plugins')">
            <UiIcon name="layers" :size="18" />
            <span v-show="!isSidebarCollapsed">插件扩展</span>
            <svg v-show="!isSidebarCollapsed" class="nav-chevron" :class="{ open: expandedMenus.plugins }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 12 15 18 9"/></svg>
          </a>
          <Transition name="sidebar-expand">
            <div v-show="!isSidebarCollapsed && expandedMenus.plugins" class="sub-items-container">
              <a
                v-for="plugin in runtimePlugins"
                :key="plugin.pluginKey"
                class="sub-item"
                :class="{ selected: activeMainMenu === 'plugin' && activePluginKey === plugin.pluginKey }"
                @click="openRuntimePlugin(plugin.pluginKey)"
              ><UiIcon name="layers" :size="15" />{{ plugin.name }}</a>
            </div>
          </Transition>
        </div>

        <div class="nav-group">
          <a class="nav-item" :class="{ 'active-parent': activeMainMenu === 'dashboard' }" :title="isSidebarCollapsed ? '车间看板' : ''" @click.stop="activeMainMenu = 'dashboard'">
            <UiIcon name="table-columns" :size="18" />
            <span v-show="!isSidebarCollapsed">车间看板</span>
          </a>
        </div>

        <div v-if="isSuperAdmin" class="nav-group">
          <a class="nav-item" :class="{ 'active-parent': activeMainMenu === 'super-admin' }" :title="isSidebarCollapsed ? '授权码管理' : ''" @click.stop="activeMainMenu = 'super-admin'">
            <UiIcon name="lock" :size="18" />
            <span v-show="!isSidebarCollapsed">授权码管理</span>
          </a>
        </div>
      </nav>
      <nav v-else>
        <a class="nav-item active-parent" :title="isSidebarCollapsed ? '移动端' : ''">
          <UiIcon name="smartphone" :size="18" />
          <span v-show="!isSidebarCollapsed">移动端作业</span>
        </a>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          <template v-if="isInternalChatWindow">
            内部沟通
          </template>
          <template v-else-if="activeMainMenu === 'home'">
            主页
          </template>
          <template v-else-if="activeMainMenu === 'basedata'">
            <template v-if="activeBasedataMenu === 'process' && processView !== 'list'">
              基础数据 <span>></span> <a href="#" style="color:inherit;text-decoration:none" @click.prevent="processView = 'list'">工序</a> <span>></span> {{ processView === 'create' ? '创建工序' : '编辑工序' }}
            </template>
            <template v-else-if="activeBasedataMenu === 'route' && routeView !== 'list'">
              基础数据 <span>></span> <a href="#" style="color:inherit;text-decoration:none" @click.prevent="routeView = 'list'">工艺路线</a> <span>></span> {{ routeView === 'create' ? '创建工艺路线' : '编辑工艺路线' }}
            </template>
            <template v-else>
              基础数据 <span>></span> {{ activeBasedataMenu === 'unit' ? '单位' : activeBasedataMenu === 'product' ? '产品定义' : activeBasedataMenu === 'bom' ? '物料清单' : activeBasedataMenu === 'defect' ? '不良品项' : activeBasedataMenu === 'process' ? '工序' : activeBasedataMenu === 'route' ? '工艺路线' : activeBasedataMenu === 'warehouse' ? '仓库' : activeBasedataMenu === 'customer' ? '客户' : '供应商' }}
            </template>
          </template>
          <template v-else-if="activeMainMenu === 'production'">
            生产管理 <span>></span> {{ activeProductionMenu === 'sales' ? '销售订单' : activeProductionMenu === 'workorder' ? '工单' : activeProductionMenu === 'task' ? '任务' : activeProductionMenu === 'reporting' ? '报工' : '报工工作台' }}
          </template>
          <template v-else-if="activeMainMenu === 'monitor'">
            系统监控 <span>></span> {{ activeMonitorMenu === 'online' ? '在线用户' : activeMonitorMenu === 'cache' ? '缓存监控' : '缓存列表' }}
          </template>
          <template v-else-if="activeMainMenu === 'custom'">
            自定义配置 <span>></span> {{ activeCustomMenu === 'product' ? '产品' : activeCustomMenu === 'bom' ? '物料清单' : activeCustomMenu === 'process' ? '工序' : activeCustomMenu === 'workorder' ? '工单' : activeCustomMenu === 'reporting' ? '报工' : activeCustomMenu === 'customer' ? '客户' : activeCustomMenu === 'receipt' ? '出入库单' : activeCustomMenu === 'receipt_detail' ? '出入库明细' : '客户' }}
          </template>
          <template v-else-if="activeMainMenu === 'inventory'">
            库存管理 <span>></span> {{ activeInventoryMenu === 'inbound' ? '入库单' : activeInventoryMenu === 'outbound' ? '出库单' : activeInventoryMenu === 'detail' ? '库存收发明细' : activeInventoryMenu === 'balance' ? '库存余额' : '进销存汇总' }}
          </template>
          <template v-else-if="activeMainMenu === 'dashboard'">
            车间看板
          </template>
          <template v-else-if="activeMainMenu === 'advanced'">
            高级运营
          </template>
          <template v-else-if="activeMainMenu === 'report'">
            报表 <span>></span> 生产报表
          </template>
          <template v-else-if="activeMainMenu === 'analytics'">
            报表 <span>></span> 分析报表
          </template>
          <template v-else-if="activeMainMenu === 'super-admin'">
            授权码管理
          </template>
          <template v-else-if="activeMainMenu === 'plugin'">
            插件扩展 <span>></span> {{ selectedRuntimePlugin?.name || '插件' }}
          </template>
          <template v-else-if="activeMainMenu === 'notifications'">
            通知中心
          </template>
          <template v-else>系统配置 <span>></span> {{ activeConfigMenu === 'department' ? '部门' : activeConfigMenu === 'factory' ? '工厂' : activeConfigMenu === 'plugin-center' ? '插件中心' : activeConfigMenu === 'external-data-sources' ? '外部数据源' : '用户' }}</template>
        </div>
        <div class="top-actions">
          <div class="chrome-actions" aria-label="工作台工具">
            <div class="chrome-dropdown-wrap">
              <button class="topbar-icon-btn" type="button" title="应用中心" @click.stop="toggleQuickApps">
                <UiIcon name="grid" :size="17" />
              </button>
              <Transition name="dropdown-motion">
                <div v-if="showQuickApps" class="chrome-quick-menu chrome-app-menu" @click.stop>
                  <div class="chrome-menu-title">应用中心</div>
                  <button type="button" @click="jumpFromQuickApp('home')"><UiIcon name="grid" :size="15" /> 分析页</button>
                  <button type="button" @click="jumpFromQuickApp('production', 'workorder')"><UiIcon name="file" :size="15" /> 工单</button>
                  <button type="button" @click="jumpFromQuickApp('production', 'workbench')"><UiIcon name="check" :size="15" /> 报工工作台</button>
                  <button type="button" @click="jumpFromQuickApp('inventory', 'balance')"><UiIcon name="layers" :size="15" /> 库存余额</button>
                </div>
              </Transition>
            </div>
            <button class="topbar-icon-btn" type="button" title="展开或收起侧栏" @click="isSidebarCollapsed = !isSidebarCollapsed">
              <UiIcon name="menu" :size="18" />
            </button>
            <button class="topbar-icon-btn" type="button" title="刷新当前页面" @click="refreshWorkspace">
              <UiIcon name="refresh" :size="17" />
            </button>
            <div class="workspace-search-wrap">
              <button v-if="!showWorkspaceSearch" class="header-search-trigger" type="button" title="搜索（Ctrl K）" @click="focusWorkspaceSearch">
                <UiIcon name="search" :size="15" />
                <span>搜索</span>
                <kbd>Ctrl K</kbd>
              </button>
              <input
                v-else
                ref="workspaceSearchInput"
                v-model="searchQuery"
                class="header-search-input"
                type="search"
                placeholder="搜索菜单"
                @keydown.enter="submitWorkspaceSearch"
                @keydown.esc="showWorkspaceSearch = false"
              />
            </div>
            <div class="chrome-quick-wrap">
              <button class="topbar-icon-btn" type="button" title="工作台设置" @click.stop="toggleChromeQuickMenu">
                <UiIcon name="settings" :size="17" />
              </button>
              <Transition name="dropdown-motion">
                <div v-if="showChromeQuickMenu" class="chrome-quick-menu" @click.stop>
                  <button type="button" @click="isSidebarCollapsed = !isSidebarCollapsed; showChromeQuickMenu = false">
                    <UiIcon name="menu" :size="15" /> {{ isSidebarCollapsed ? '展开侧栏' : '收起侧栏' }}
                  </button>
                  <button type="button" @click="refreshWorkspace">
                    <UiIcon name="refresh" :size="15" /> 刷新当前页
                  </button>
                  <button type="button" @click="toggleAppFullscreen(); showChromeQuickMenu = false">
                    <UiIcon name="fullscreen" :size="15" /> {{ isAppFullscreen ? '退出全屏' : '全屏工作区' }}
                  </button>
                  <button type="button" @click="closeAllTabs">
                    <UiIcon name="close" :size="15" /> 清理工作区标签
                  </button>
                </div>
              </Transition>
            </div>
            <button class="topbar-icon-btn theme-toggle-btn" type="button" :title="isDarkTheme ? '切换浅色主题' : '切换深色主题'" :aria-label="isDarkTheme ? '切换浅色主题' : '切换深色主题'" @click="toggleTheme">
              <UiIcon :key="isDarkTheme ? 'moon' : 'sun'" :name="isDarkTheme ? 'moon' : 'sun'" :size="17" />
            </button>
            <div class="chrome-dropdown-wrap">
              <button class="topbar-icon-btn" type="button" title="语言" @click.stop="toggleLanguageMenu">
                <UiIcon name="languages" :size="17" />
              </button>
              <Transition name="dropdown-motion">
                <div v-if="showLanguageMenu" class="chrome-quick-menu chrome-language-menu" @click.stop>
                  <div class="chrome-menu-title">界面语言</div>
                  <button type="button" @click="selectLanguage('zh-CN')">简体中文</button>
                  <button type="button" @click="selectLanguage('en-US')">English</button>
                </div>
              </Transition>
            </div>
            <div class="chrome-dropdown-wrap">
              <button class="topbar-icon-btn" type="button" title="设置时区" aria-label="设置时区" @click.stop="openTimezoneDialog">
                <UiIcon name="timezone" :size="17" />
              </button>
            </div>
            <button class="topbar-icon-btn" type="button" :title="isAppFullscreen ? '退出全屏' : '全屏工作区'" @click="toggleAppFullscreen">
              <UiIcon :name="isAppFullscreen ? 'minimize' : 'fullscreen'" :size="17" />
            </button>
            <div class="chrome-dropdown-wrap">
              <button class="topbar-icon-btn" type="button" title="IM 聊天" aria-label="IM 聊天" @click="openInternalChat">
                <UiIcon name="message" :size="17" />
              </button>
            </div>
          </div>
          <template v-if="activeMainMenu === 'production' && activeProductionMenu === 'workorder'">
            <button class="text-btn" style="color:#586360;font-size:13px;display:flex;align-items:center;gap:4px" @click="openEditPrintTemplate">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/></svg>
              打印模板
            </button>
            <div class="topbar-divider" style="height:14px;background:#dce3e0;width:1px;margin:0 12px"></div>
          </template>

          <div class="notification-wrap">
            <button class="topbar-icon-btn notification-btn" type="button" title="通知" @click.stop="toggleNotifications">
              <UiIcon name="bell" :size="18" />
              <span v-if="notificationCount" class="notification-badge">{{ notificationCount > 99 ? '99+' : notificationCount }}</span>
            </button>
            <Transition name="dropdown-motion">
              <div v-if="showNotificationDropdown" class="notification-dropdown">
                <div class="notification-head">
                  <div>
                    <strong>通知中心</strong>
                    <span>{{ notificationUnreadCount ? `${notificationUnreadCount} 条未读` : '暂无未读通知' }}</span>
                  </div>
                  <div class="notification-head-actions">
                    <button v-if="notificationUnreadCount" type="button" class="notification-text-action" @click.stop="markAllNotificationsAsRead">全部已读</button>
                    <button type="button" class="notification-refresh" title="刷新通知" aria-label="刷新通知" @click.stop="loadNotifications">
                      <UiIcon name="refresh" :size="15" />
                    </button>
                  </div>
                </div>
                <div v-if="notificationsLoading" class="notification-state">加载中...</div>
                <div v-else-if="notificationError" class="notification-state error">{{ notificationError }}</div>
                <div v-else-if="notificationPreview.length === 0" class="notification-state">暂无通知</div>
                <template v-else>
                  <button
                    v-for="item in notificationPreview"
                    :key="item.id"
                    type="button"
                    class="notification-item"
                    :class="{ unread: item.unread }"
                    @click.stop="openNotification(item)"
                  >
                    <span class="notification-dot" :class="item.priority.toLowerCase()"></span>
                    <span class="notification-content">
                      <strong>{{ item.title }}</strong>
                      <span>{{ item.content }}</span>
                    </span>
                    <time :title="normalizeDate(item.createdAt)">{{ normalizeDate(item.createdAt) || '-' }}</time>
                  </button>
                </template>
                <div class="notification-foot">
                  <button type="button" class="notification-view-all" @click.stop="openNotificationCenter">查看全部通知</button>
                </div>
              </div>
            </Transition>
          </div>
          <div class="topbar-divider"></div>
          <div class="user-profile" @click="showUserDropdown = !showUserDropdown">
            <UiIcon name="user" :size="18" />
            <span class="username">{{ currentUser?.name }}</span>
            <svg class="dropdown-arrow" :class="{ 'open': showUserDropdown }" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M6 9l6 6 6-6"/></svg>
            <Transition name="dropdown-motion">
              <div v-if="showUserDropdown" class="user-dropdown">
                <button type="button" @click.stop="logout">
                  <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                  退出登录
                </button>
              </div>
            </Transition>
          </div>
        </div>
      </header>

      <WorkspaceTabs
        :tabs="workspaceTabs"
        :active-id="activeWorkspaceTabId"
        @select="activateWorkspaceTabFromEvent"
        @close="closeWorkspaceTab($event.id)"
        @pin="toggleWorkspaceTabPinned"
        @contextmenu="openTabContextMenu"
      />

      <div
        v-if="tabContextMenu.show"
        class="tab-context-menu"
        :style="{ left: `${tabContextMenu.x}px`, top: `${tabContextMenu.y}px` }"
        role="menu"
        @click.stop
      >
        <button type="button" role="menuitem" @click="closeWorkspaceTab(tabContextMenu.tabId)">
          <UiIcon name="close" :size="15" /> 关闭
        </button>
        <button type="button" role="menuitem" @click="toggleTabPinned">
          <UiIcon name="pin" :size="15" /> {{ workspaceTabs.find(tab => tab.id === tabContextMenu.tabId)?.pinned ? '取消固定' : '固定' }}
        </button>
        <button type="button" role="menuitem" @click="isWorkspaceMaximized = !isWorkspaceMaximized; closeTabContextMenu()">
          <UiIcon name="maximize" :size="15" /> {{ isWorkspaceMaximized ? '恢复窗口' : '最大化' }}
        </button>
        <button type="button" role="menuitem" @click="refreshWorkspace">
          <UiIcon name="refresh" :size="15" /> 重新加载
        </button>
        <button type="button" role="menuitem" @click="openCurrentTabInNewWindow">
          <UiIcon name="fullscreen" :size="15" /> 在新窗口打开
        </button>
        <div class="tab-menu-divider"></div>
        <button type="button" role="menuitem" :disabled="workspaceTabs.findIndex(tab => tab.id === tabContextMenu.tabId) <= 1" @click="closeTabsLeft">
          <UiIcon name="arrowLeft" :size="15" /> 关闭左侧标签页
        </button>
        <button type="button" role="menuitem" :disabled="workspaceTabs.findIndex(tab => tab.id === tabContextMenu.tabId) >= workspaceTabs.length - 1" @click="closeTabsRight">
          <UiIcon name="arrowRight" :size="15" /> 关闭右侧标签页
        </button>
        <button type="button" role="menuitem" @click="closeOtherTabs">
          <UiIcon name="layers" :size="15" /> 关闭其它标签页
        </button>
        <button type="button" role="menuitem" @click="closeAllTabs">
          <UiIcon name="close" :size="15" /> 关闭全部标签页
        </button>
      </div>

      <section v-if="!hasAdminAccess && !isInternalChatWindow" class="content">
        <div class="placeholder-page">
          <div class="empty-state">当前账号为生产作业账号，请使用手机端接单、报工和查看任务。</div>
        </div>
      </section>

      <div ref="pageMotionStage" class="page-motion-stage">
        <KeepAlive :key="token" :max="32">
          <NotificationCenterPage
            v-if="activeMainMenu === 'notifications' && currentUser"
            key="notifications"
            :token="token"
            @open="openNotification"
            @notice="showAction"
          />
          <HomeDashboardPage
            v-else-if="activeMainMenu === 'home' && currentUser && currentUser.role === 'ADMIN'"
            key="home"
            :token="token"
          />
          <SuperAdminAuthCodesPage v-else-if="activeMainMenu === 'super-admin'" key="super-admin" />
          <PluginRuntimePage
            v-else-if="activeMainMenu === 'plugin'"
            :key="`plugin-${activePluginKey}`"
            :token="token"
            :plugin="selectedRuntimePlugin"
          />
          <AdvancedOperationsPage
            v-else-if="activeMainMenu === 'advanced'"
            key="advanced"
            :token="token"
            @notice="showAction"
          />
          <ReportCenterPage
            v-else-if="activeMainMenu === 'report'"
            key="report"
            :token="token"
            @notice="showAction"
          />
          <AnalyticsReportPage
            v-else-if="activeMainMenu === 'analytics'"
            key="analytics"
            :token="token"
            @notice="showAction"
          />
          <ProductDefinitionPage
            v-else-if="activeMainMenu === 'basedata' && activeBasedataMenu === 'product'"
            key="basedata-product"
            :token="token"
            @notice="showAction"
          />
          <BomPage
            v-else-if="activeMainMenu === 'basedata' && activeBasedataMenu === 'bom'"
            key="basedata-bom"
            :token="token"
            @notice="showAction"
          />
          <WarehousePage
            v-else-if="activeMainMenu === 'basedata' && activeBasedataMenu === 'warehouse'"
            key="basedata-warehouse"
            :token="token"
            @notice="showAction"
          />
          <SupplierPage
            v-else-if="activeMainMenu === 'basedata' && activeBasedataMenu === 'supplier'"
            key="basedata-supplier"
            :token="token"
            @notice="showAction"
          />
          <CustomerPage
            v-else-if="activeMainMenu === 'basedata' && activeBasedataMenu === 'customer'"
            key="basedata-customer"
            :token="token"
            @notice="showAction"
          />
          <WorkOrderPage
            v-else-if="activeMainMenu === 'production' && activeProductionMenu === 'workorder'"
            key="production-workorder"
            :token="token"
            @notice="showAction"
          />
          <SalesOrderPage
            v-else-if="activeMainMenu === 'production' && activeProductionMenu === 'sales'"
            key="production-sales"
            :token="token"
            @notice="showAction"
          />
          <ReportApprovalPage
            v-else-if="activeMainMenu === 'production' && (activeProductionMenu === 'workbench' || activeProductionMenu === 'reporting')"
            key="production-reporting"
            :token="token"
            @notice="showAction"
          />
          <OnlineUsersPage
            v-else-if="activeMainMenu === 'monitor' && activeMonitorMenu === 'online'"
            key="monitor-online"
            :token="token"
            @notice="showAction"
          />
          <CacheMonitorPage
            v-else-if="activeMainMenu === 'monitor' && activeMonitorMenu === 'cache'"
            key="monitor-cache"
            :token="token"
            @notice="showAction"
          />
          <CacheListPage
            v-else-if="activeMainMenu === 'monitor' && activeMonitorMenu === 'cacheList'"
            key="monitor-cacheList"
            :token="token"
            @notice="showAction"
          />
          <TaskPage
            v-else-if="activeMainMenu === 'production' && activeProductionMenu === 'task'"
            key="production-task"
            :token="token"
            @notice="showAction"
          />
          <CustomWorkOrderFieldsPage
            v-else-if="activeMainMenu === 'custom' && activeCustomMenu === 'workorder'"
            key="custom-workorder"
            :token="token"
            @notice="showAction"
          />
          <CustomReceiptFieldsPage
            v-else-if="activeMainMenu === 'custom' && genericCustomFieldScopes[activeCustomMenu]"
            :key="`custom-${activeCustomMenu}`"
            :token="token"
            :scope="activeCustomMenu"
            :scope-label="genericCustomFieldScopes[activeCustomMenu]"
            @notice="showAction"
          />
          <CustomReceiptFieldsPage
            v-else-if="activeMainMenu === 'custom' && activeCustomMenu === 'receipt'"
            key="custom-receipt"
            :token="token"
            scope="receipt"
            scope-label="出入库单"
            @notice="showAction"
          />
          <CustomReceiptFieldsPage
            v-else-if="activeMainMenu === 'custom' && activeCustomMenu === 'receipt_detail'"
            key="custom-receipt-detail"
            :token="token"
            scope="receipt_detail"
            scope-label="出入库明细"
            @notice="showAction"
          />
          <InboundReceiptPage
            v-else-if="activeMainMenu === 'inventory' && activeInventoryMenu === 'inbound'"
            key="inventory-inbound"
            :token="token"
            @notice="showAction"
          />
          <InventoryBalancePage
            v-else-if="activeMainMenu === 'inventory' && activeInventoryMenu === 'balance'"
            key="inventory-balance"
            :token="token"
            @notice="showAction"
            @navigate="handleNavigate"
          />
          <OutboundReceiptPage
            v-else-if="activeMainMenu === 'inventory' && activeInventoryMenu === 'outbound'"
            key="inventory-outbound"
            :token="token"
            @notice="showAction"
          />
          <InventoryDetailPage
            v-else-if="activeMainMenu === 'inventory' && activeInventoryMenu === 'detail'"
            key="inventory-detail"
            :token="token"
            @notice="showAction"
          />
          <InventorySummaryPage
            v-else-if="activeMainMenu === 'inventory' && activeInventoryMenu === 'summary'"
            key="inventory-summary"
            :token="token"
            @notice="showAction"
          />
          <BaseDataPage
            v-else-if="activeMainMenu === 'basedata' && ['unit', 'defect', 'process', 'route'].includes(activeBasedataMenu)"
            :key="`basedata-${activeBasedataMenu}`"
            :token="token"
            :active-basedata-menu="activeBasedataMenu"
            :users="users"
            @error="msg => errorMessage = msg"
            @notice="msg => actionMessage = msg"
            @confirm="payload => { confirmState.title = payload.title; confirmState.message = payload.message; confirmState.action = payload.action; confirmState.show = true }"
          />
          <UserManagementPage
            v-else-if="activeMainMenu === 'sysconfig' && activeConfigMenu === 'user'"
            key="sysconfig-user"
            :token="token"
            :users="users"
            :departments="departments"
            :current-user="currentUser"
            @error="msg => errorMessage = msg"
            @notice="msg => actionMessage = msg"
            @confirm="payload => { confirmState.title = payload.title; confirmState.message = payload.message; confirmState.action = payload.action; confirmState.show = true }"
            @reload-users="loadUsers"
            @update-current-user="syncCurrentUser"
          />
          <FactorySettingsPage
            v-else-if="activeMainMenu === 'sysconfig' && activeConfigMenu === 'factory'"
            key="sysconfig-factory"
            :token="token"
            @error="msg => errorMessage = msg"
            @notice="msg => actionMessage = msg"
          />
          <ExternalDataSourcePage
            v-else-if="activeMainMenu === 'sysconfig' && activeConfigMenu === 'external-data-sources' && hasAdminAccess"
            :key="`sysconfig-external-data-sources-${currentFactoryCode}`"
            :token="token"
            @error="msg => errorMessage = msg"
            @notice="msg => actionMessage = msg"
          />
          <PluginCenterPage
            v-else-if="activeMainMenu === 'sysconfig' && activeConfigMenu === 'plugin-center'"
            key="sysconfig-plugin-center"
            :token="token"
            :is-super-admin="isSuperAdmin"
            @error="msg => errorMessage = msg"
            @notice="msg => actionMessage = msg"
            @plugins-changed="loadPluginRuntimeManifest"
          />
          <DepartmentManagementPage
            v-else-if="activeMainMenu === 'sysconfig' && activeConfigMenu === 'department'"
            key="sysconfig-department"
            :token="token"
            :departments="departments"
            :department-total="departmentTotal"
            @error="msg => errorMessage = msg"
            @notice="msg => actionMessage = msg"
            @confirm="payload => { confirmState.title = payload.title; confirmState.message = payload.message; confirmState.action = payload.action; confirmState.show = true }"
            @reload-departments="loadDepartments"
          />
        </KeepAlive>
      </div>

      <section
        class="content placeholder-page"
        v-if="activeMainMenu === 'production' && activeProductionMenu !== 'sales' && activeProductionMenu !== 'workorder' && activeProductionMenu !== 'workbench' && activeProductionMenu !== 'reporting' && activeProductionMenu !== 'task'"
      >
        <div class="empty-state">请选择左侧已开放模块。</div>
      </section>

      <section
        class="content placeholder-page"
        v-if="activeMainMenu === 'custom' && activeCustomMenu !== 'workorder' && activeCustomMenu !== 'receipt' && activeCustomMenu !== 'receipt_detail' && !genericCustomFieldScopes[activeCustomMenu]"
      >
        <div class="empty-state">请选择左侧已开放模块。</div>
      </section>

      <section
        class="content placeholder-page"
        v-if="activeMainMenu === 'inventory' && activeInventoryMenu !== 'inbound' && activeInventoryMenu !== 'balance' && activeInventoryMenu !== 'outbound' && activeInventoryMenu !== 'detail' && activeInventoryMenu !== 'summary'"
      >
        <div class="empty-state">请选择左侧已开放模块。</div>
      </section>

      <!-- 单位管理 -->
      <section
        class="content placeholder-page"
        v-if="activeMainMenu === 'basedata' && !['product', 'bom', 'warehouse', 'supplier', 'customer', 'unit', 'defect', 'process', 'route'].includes(activeBasedataMenu)"
      >
        <div class="empty-state">请选择左侧已开放模块。</div>
      </section>

      <section class="content" v-if="false" v-show="activeMainMenu === 'basedata' && activeBasedataMenu === 'unit'">
        <div class="filters">
          <label>
            <span>名称</span>
            <input v-model="unitFilterForm.name" placeholder="请输入" />
          </label>
          <label>
            <span>备注</span>
            <input v-model="unitFilterForm.remark" placeholder="请输入备注" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button" @click="loadUnits">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn" type="button" @click="openCreateUnit">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            创建单位
          </button>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:50px"><input type="checkbox" /></th>
                <th style="width:60px">序号</th>
                <th>名称</th>
                <th>备注</th>
                <th style="width:120px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(unit, idx) in unitFilteredList" :key="unit.id">
                <td><input type="checkbox" /></td>
                <td>{{ idx + 1 }}</td>
                <td>{{ unit.name }}</td>
                <td>{{ unit.remark || '-' }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditUnit(unit)">编辑</button>
                  <button class="action-link danger" type="button" @click="handleDeleteUnit(unit)">删除</button>
                </td>
              </tr>
              <tr v-if="unitFilteredList.length === 0">
                <td colspan="5" style="text-align:center;color:#aaa;padding:32px">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 不良品项管理 -->
      <section class="content" v-if="false" v-show="activeMainMenu === 'basedata' && activeBasedataMenu === 'defect'">
        <div class="filters">
          <label>
            <span style="white-space:nowrap">不良品项编号</span>
            <input v-model="defectFilterForm.code" placeholder="请输入不良品项编号" />
          </label>
          <label>
            <span style="white-space:nowrap">不良品项名称</span>
            <input v-model="defectFilterForm.name" placeholder="请输入不良品项名称" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn" type="button" @click="openCreateDefect">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            创建不良品项
          </button>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:50px"><input type="checkbox" /></th>
                <th style="width:60px">序号</th>
                <th>不良品项编号</th>
                <th>不良品项名称</th>
                <th style="width:120px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(d, idx) in defectFilteredList" :key="d.id">
                <td><input type="checkbox" /></td>
                <td>{{ idx + 1 }}</td>
                <td>{{ d.code }}</td>
                <td>{{ d.name }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditDefect(d)">编辑</button>
                  <button class="action-link danger" type="button" @click="handleDeleteDefect(d)">删除</button>
                </td>
              </tr>
              <tr v-if="defectFilteredList.length === 0">
                <td colspan="5" style="text-align:center;color:#aaa;padding:32px">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 工序管理 - 列表 -->
      <section class="content" v-if="false" v-show="activeMainMenu === 'basedata' && activeBasedataMenu === 'process' && processView === 'list'">
        <div class="filters">
          <label>
            <span>工序编号</span>
            <input v-model="processFilterForm.code" placeholder="请输入工序编号" />
          </label>
          <label>
            <span>工序名称</span>
            <select v-model="processFilterForm.name">
              <option value="">请选择</option>
              <option v-for="p in processes" :key="p.id" :value="p.name">{{ p.name }}</option>
            </select>
          </label>
          <label>
            <span>创建人</span>
            <select v-model="processFilterForm.creator">
              <option value="">请选择</option>
              <option value="管理员">管理员</option>
            </select>
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn create-btn" type="button" @click="openCreateProcess()">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            创建工序
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            导出
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            导入
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
            默认SOP模板
          </button>
          <button class="toolbar-btn" type="button">
            <UiIcon name="table-columns" :size="14" />
            字段配置
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg>
            排序
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><line x1="21" y1="10" x2="3" y2="10"/><line x1="21" y1="6" x2="3" y2="6"/><line x1="21" y1="14" x2="3" y2="14"/><line x1="21" y1="18" x2="3" y2="18"/></svg>
            行高
          </button>
          <div style="margin-left:auto">
            <button class="toolbar-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              导入日志
            </button>
          </div>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:40px"><input type="checkbox" /></th>
                <th style="width:50px">序号</th>
                <th style="min-width:140px">工序编号</th>
                <th style="min-width:150px">工序名称</th>
                <th style="min-width:200px">报工权限</th>
                <th style="min-width:120px">报工数配比</th>
                <th style="min-width:100px">任务打卡</th>
                <th class="action-cell" style="min-width:240px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(p, index) in processFilteredList" :key="p.id">
                <td><input type="checkbox" /></td>
                <td>{{ index + 1 }}</td>
                <td>{{ p.code }}</td>
                <td>{{ p.name }}</td>
                <td class="perm-cell">
                  <template v-for="(tag, ti) in formatPermissionTags(p.reportPermission)" :key="ti">
                    <span
                      class="perm-cell-tag"
                      :class="{ 'perm-partial': tag.partial }"
                      @mouseenter="tag.tooltip ? showTooltip($event, tag.tooltip) : null"
                      @mouseleave="hideTooltip"
                    >{{ tag.label }}</span>
                  </template>
                </td>
                <td>{{ p.reportRatio }}</td>
                <td>{{ p.taskPunchIn ? '开启' : '关闭' }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditProcess(p)">编辑</button>
                  <button class="action-link" type="button">打印</button>
                  <button class="action-link" type="button">SOP模版</button>
                  <button class="action-link danger" type="button" @click="handleDeleteProcess(p)">删除</button>
                </td>
              </tr>
              <tr v-if="processFilteredList.length === 0">
                <td colspan="8" style="text-align:center;color:#aaa;padding:32px">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 工序管理 - 创建/编辑表单 -->
      <section class="content process-form-page" v-if="false" v-show="activeMainMenu === 'basedata' && activeBasedataMenu === 'process' && (processView === 'create' || processView === 'edit')">
        <h2 class="process-form-title">{{ processView === 'edit' ? '编辑工序' : '创建工序' }}</h2>
        <div class="process-form">
          <!-- 工序编号 -->
          <div class="process-field">
            <label class="process-label">工序编号</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.code" placeholder="请输入，否则将自动生成" />
            </div>
          </div>
          <!-- 工序名称 -->
          <div class="process-field">
            <label class="process-label required">工序名称</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.name" placeholder="请填写" />
            </div>
          </div>
          <!-- 报工权限 -->
          <div class="process-field">
            <label class="process-label required">报工权限</label>
            <div class="process-input-wrap perm-dropdown-wrap">
              <div class="perm-tag-box" @click="showProcessPermDropdown = !showProcessPermDropdown">
                <span class="perm-tag" v-for="k in visiblePermKeys" :key="k">
                  {{ getPermLabel(k) }}
                  <span class="tag-close" @click.stop="removeProcessPermission(k)">×</span>
                </span>
                <span class="perm-placeholder" v-if="visiblePermKeys.length === 0">请选择</span>
              </div>
              <div class="perm-dropdown perm-tree-dropdown" v-show="showProcessPermDropdown">
                <!-- 搜索 -->
                <div class="perm-search-bar">
                  <svg viewBox="0 0 24 24" width="13" height="13" stroke="#aaa" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                  <input v-model="permSearchText" placeholder="搜索字段" @click.stop />
                </div>
                <!-- 所有人 -->
                <div class="perm-tree-row" @click.stop="togglePermAll()">
                  <span class="perm-checkbox" :class="{ checked: isPermSelected('all') }">
                    <svg v-if="isPermSelected('all')" viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="#fff" stroke-width="2"><polyline points="2,6 5,9 10,3"/></svg>
                  </span>
                  <span class="perm-tree-label">所有人</span>
                </div>
                <!-- 部门列表 -->
                <template v-for="dept in filteredPermTree" :key="dept.id">
                  <div class="perm-tree-row perm-dept-row">
                    <span class="perm-expand-icon" @click.stop="togglePermDeptExpand(dept.id)">
                      <svg :style="{ transform: isDeptExpanded(dept.id) ? 'rotate(90deg)' : 'rotate(0)' }" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2.5" fill="none"><polyline points="9 18 15 12 9 6"/></svg>
                    </span>
                    <span class="perm-checkbox"
                      :class="{ checked: isDeptAllSelected(dept), partial: isDeptPartialSelected(dept) }"
                      @click.stop="togglePermDept(dept)">
                      <svg v-if="isDeptAllSelected(dept)" viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="#fff" stroke-width="2"><polyline points="2,6 5,9 10,3"/></svg>
                      <span v-else-if="isDeptPartialSelected(dept)" style="width:8px;height:2px;background:#fff;display:block;margin:auto;"></span>
                    </span>
                    <span class="perm-tree-label" @click.stop="togglePermDeptExpand(dept.id)">
                      {{ dept.name }} ({{ dept.employees.length }}人)
                    </span>
                  </div>
                  <!-- 员工列表 -->
                  <template v-if="isDeptExpanded(dept.id)">
                    <div class="perm-tree-row perm-emp-row" v-for="emp in dept.employees" :key="emp.id"
                      @click.stop="togglePermEmployee(dept, emp)">
                      <span style="width:20px"></span>
                      <span class="perm-checkbox" :class="{ checked: isPermSelected(emp.id) }">
                        <svg v-if="isPermSelected(emp.id)" viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="#fff" stroke-width="2"><polyline points="2,6 5,9 10,3"/></svg>
                      </span>
                      <span class="perm-tree-label">{{ emp.name }}</span>
                    </div>
                  </template>
                </template>
              </div>
            </div>
          </div>
          <!-- 计划数默认值 -->
          <div class="process-field">
            <label class="process-label required">计划数默认值</label>
            <div class="process-input-wrap">
              <div class="formula-box">
                <button class="formula-edit-btn" type="button">
                  <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  修改
                </button>
                <div class="formula-text">
                  <span class="formula-fx">fx</span>
                  <span>$(任务.工单计划数) * $(任务.报工效配比)</span>
                </div>
              </div>
            </div>
          </div>
          <!-- 报工数配比 -->
          <div class="process-field">
            <label class="process-label required">报工数配比</label>
            <div class="process-input-wrap">
              <input class="process-input" type="number" v-model.number="processForm.reportRatio" min="1" />
            </div>
          </div>
          <!-- 不良品项列表 -->
          <div class="process-field">
            <label class="process-label">不良品项列表</label>
            <div class="process-input-wrap defect-dropdown-wrap" style="position:relative">
              <div class="perm-tag-box" @click="showProcessDefectDropdown = !showProcessDefectDropdown">
                <span class="perm-tag" v-for="id in processForm.defectItemIds" :key="id">
                  {{ getDefectLabel(id) }}
                  <span class="tag-close" @click.stop="toggleProcessDefect(id)">×</span>
                </span>
                <span class="perm-placeholder" v-if="processForm.defectItemIds.length === 0">请选择不良品项</span>
              </div>
              <div class="perm-dropdown" v-show="showProcessDefectDropdown">
                <div class="perm-option" v-for="d in defects" :key="d.id"
                  :class="{ active: processForm.defectItemIds.includes(d.id) }"
                  @click="toggleProcessDefect(d.id)">
                  {{ d.code }} | {{ d.name }}
                  <span v-if="processForm.defectItemIds.includes(d.id)" style="margin-left:auto;color:var(--accent)">✓</span>
                </div>
                <div v-if="defects.length === 0" style="padding:12px;color:#aaa;text-align:center">暂无不良品项</div>
              </div>
            </div>
          </div>
          <!-- 任务打卡 -->
          <div class="process-field">
            <label class="process-label">任务打卡</label>
            <div class="process-input-wrap">
              <div class="radio-group">
                <label class="radio-label">
                  <input type="radio" :value="false" v-model="processForm.taskPunchIn" />
                  <span>关闭</span>
                </label>
                <label class="radio-label">
                  <input type="radio" :value="true" v-model="processForm.taskPunchIn" />
                  <span>开启</span>
                </label>
              </div>
            </div>
          </div>
          <!-- 良品数量 -->
          <div class="process-field">
            <label class="process-label">良品数量</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.goodQuantity" placeholder="请填写" />
            </div>
          </div>
          <!-- 工序名称机械 -->
          <div class="process-field">
            <label class="process-label">工序名称机械</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.machineName" placeholder="请填写" />
            </div>
          </div>
          <!-- 投料数 -->
          <div class="process-field">
            <label class="process-label">投料数</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.feedCount" placeholder="请填写" />
            </div>
          </div>
          <!-- 对应机台 -->
          <div class="process-field">
            <label class="process-label">对应机台</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.correspondingMachine" placeholder="请填写" />
            </div>
          </div>
          <!-- 要求 -->
          <div class="process-field">
            <label class="process-label">要求</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="processForm.requirements" placeholder="请填写" />
            </div>
          </div>
          <!-- 工序采集数据 -->
          <div class="process-field process-field-collect">
            <label class="process-label">工序采集数据</label>
            <div class="process-input-wrap">
              <div class="collect-wrap">
                <table class="collect-table">
                  <thead>
                    <tr>
                      <th>类型</th>
                      <th>名称</th>
                      <th style="width:40px"></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, idx) in processForm.collectData" :key="idx">
                      <td><input class="collect-input" v-model="row.type" placeholder="请填写" /></td>
                      <td><input class="collect-input" v-model="row.name" placeholder="请填写" /></td>
                      <td><button type="button" class="collect-del" @click="removeCollectDataRow(idx)">×</button></td>
                    </tr>
                  </tbody>
                </table>
                <div class="collect-empty" v-if="processForm.collectData.length === 0">
                  <svg viewBox="0 0 80 60" width="72" height="54" fill="none">
                    <rect x="10" y="8" width="60" height="44" rx="4" fill="#f2f4f3" stroke="#dde3e0" stroke-width="1.5"/>
                    <rect x="18" y="18" width="44" height="6" rx="2" fill="#e2e8e5"/>
                    <rect x="18" y="30" width="32" height="5" rx="2" fill="#eaeeec"/>
                    <rect x="18" y="41" width="20" height="4" rx="2" fill="#eaeeec"/>
                  </svg>
                  <span>暂无采集数据</span>
                </div>
                <button type="button" class="add-collect-row" @click="addCollectDataRow()"
                  :disabled="processForm.collectData.length >= 50">
                  <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none"><circle cx="12" cy="12" r="9"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                  添加一行 最多支持添加50个
                </button>
              </div>
            </div>
          </div>
        </div>
        <!-- 底部按钮 -->
        <div class="process-form-footer">
          <button class="btn-cancel" type="button" @click="processView = 'list'">取消</button>
          <button class="btn-save" type="button" @click="handleSaveProcess()">保存</button>
        </div>
      </section>

      <!-- 工艺路线 - 列表 -->
      <section class="content" v-if="false" v-show="activeMainMenu === 'basedata' && activeBasedataMenu === 'route' && routeView === 'list'">
        <div class="filters">
          <label>
            <span>工艺路线编号</span>
            <input v-model="routeFilterForm.code" placeholder="请输入工艺路线编号" />
          </label>
          <label>
            <span>工艺路线名称</span>
            <input v-model="routeFilterForm.name" placeholder="请输入工艺路线名称" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>
        <div class="toolbar">
          <button class="primary-btn create-btn" type="button" @click="openCreateRoute()">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            创建工艺路线
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            导出
          </button>
          <button class="toolbar-btn" type="button">
            <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            导入
          </button>
          <button class="toolbar-btn" type="button">
            <UiIcon name="table-columns" :size="13" />
            字段配置
          </button>
          <button class="toolbar-btn" style="margin-left:auto" type="button">导入日志</button>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:40px"><input type="checkbox" /></th>
                <th style="min-width:150px">工艺路线编号</th>
                <th style="min-width:180px">工艺路线名称</th>
                <th style="min-width:240px">工序/工艺路线列表</th>
                <th style="min-width:120px">创建人</th>
                <th style="min-width:180px">创建时间</th>
                <th style="min-width:180px">操作</th>
              </tr>
            </thead>
          <tbody>
            <tr v-for="(r, idx) in routeFilteredList" :key="r.id">
              <td style="text-align:center">{{ idx + 1 }}</td>
              <td>
                <a href="#" style="color:var(--accent);text-decoration:none" @click.prevent="openEditRoute(r)">{{ r.code }}</a>
              </td>
              <td>{{ r.name }}</td>
              <td style="color:#586360">
                {{ r.steps.map(s => getProcessName(s.processId)).filter(Boolean).join('、') || '-' }}
              </td>
              <td>{{ r.creator }}</td>
              <td>{{ r.createdAt }}</td>
              <td>
                <button class="link-btn" type="button" @click="openEditRoute(r)">编辑</button>
                <button class="link-btn" type="button">复制</button>
                <button class="link-btn danger" type="button" @click="confirmDeleteRoute(r)">删除</button>
              </td>
            </tr>
            <tr v-if="routeFilteredList.length === 0">
              <td colspan="7" style="text-align:center;padding:48px;color:#bbb">
                <svg viewBox="0 0 64 64" width="40" height="40" style="display:block;margin:0 auto 10px" fill="none"><rect x="8" y="12" width="48" height="40" rx="4" fill="#f2f4f3" stroke="#dde3e0" stroke-width="1.5"/><line x1="18" y1="24" x2="46" y2="24" stroke="#dde3e0" stroke-width="2"/><line x1="18" y1="32" x2="38" y2="32" stroke="#dde3e0" stroke-width="2"/></svg>
                暂无工艺路线
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      </section>

      <!-- 工艺路线 - 创建/编辑 -->
      <section class="process-form-page" v-if="false" v-show="activeMainMenu === 'basedata' && activeBasedataMenu === 'route' && routeView !== 'list'">
        <div class="process-form-scroll">
          <h2 class="process-form-title">{{ routeView === 'create' ? '创建工艺路线' : '编辑工艺路线' }}</h2>
          <!-- 基本信息 -->
          <div class="process-field">
            <label class="process-label">工艺路线编号</label>
            <div class="process-input-wrap" style="max-width:50%">
              <input class="process-input" v-model="routeForm.code" placeholder="请输入，忽略将自动生成" />
            </div>
          </div>
          <div class="process-field">
            <label class="process-label required">工艺路线名称</label>
            <div class="process-input-wrap" style="max-width:50%">
              <input class="process-input" v-model="routeForm.name" placeholder="请填写" />
            </div>
          </div>

          <!-- 工序列表 -->
          <div class="route-steps-section">
            <div class="route-steps-header">
              <span class="route-steps-title">工序列表</span>
              <div style="display:flex;gap:8px;margin-top:8px">
                <button type="button" class="primary-btn" style="height:32px;padding:0 14px;font-size:13px" @click="addRouteStep()">
                  <svg viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2.5" fill="none"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                  添加一行
                </button>
                <button type="button" class="toolbar-btn" style="height:32px;font-size:13px">
                  <UiIcon name="table-columns" :size="13" />
                  字段配置
                </button>
              </div>
            </div>

            <!-- 工序表格 -->
            <div class="collect-wrap route-steps-wrap">
              <table class="collect-table route-steps-table">
                <thead>
                  <tr>
                    <th style="width:36px"></th>
                    <th style="width:36px"><input type="checkbox" /></th>
                    <th>类型</th>
                    <th>工序/工艺路线 <span style="color:#e5534b">*</span></th>
                    <th style="width:120px">报工数配比 <span style="color:#e5534b">*</span></th>
                    <th style="width:120px">备注</th>
                    <th style="width:60px">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(step, idx) in routeForm.steps" :key="step.id"
                    :class="{ 'drag-over': routeDragOverIdx === idx }"
                    draggable="true"
                    @dragstart="onRouteDragStart(idx, $event)"
                    @dragover="onRouteDragOver(idx, $event)"
                    @drop="onRouteDrop(idx)"
                    @dragend="onRouteDragEnd()"
                  >
                    <!-- 拖拽手柄 -->
                    <td style="text-align:center;cursor:grab;color:#bbb;font-size:16px;letter-spacing:1px">&#8942;&#8942;</td>
                    <td style="text-align:center"><input type="checkbox" /></td>
                    <!-- 类型 -->
                    <td>
                      <div class="radio-group" style="gap:8px">
                        <label class="radio-label">
                          <input type="radio" value="process" v-model="step.type" @change="step.processId = null" />
                          <span>工序</span>
                        </label>
                        <label class="radio-label">
                          <input type="radio" value="route" v-model="step.type" @change="step.processId = null" />
                          <span>工艺路线</span>
                        </label>
                      </div>
                    </td>
                    <!-- 工序/工艺路线下拉选择 -->
                    <td style="position:relative">
                      <div class="route-step-select" @click.stop="openRouteStepDropdown(step.id)">
                        <span v-if="step.processId" class="route-step-selected">{{ getStepItemName(step) }}</span>
                        <span v-else class="route-step-placeholder">请选择</span>
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="#aaa" stroke-width="2" fill="none"
                          :style="{ transform: routeStepDropdownOpen[step.id] ? 'rotate(180deg)' : 'none' }">
                          <polyline points="6 9 12 15 18 9"/>
                        </svg>
                      </div>
                      <div class="route-step-dropdown" v-show="routeStepDropdownOpen[step.id]">
                        <div class="perm-search-bar">
                          <svg viewBox="0 0 24 24" width="13" height="13" stroke="#aaa" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                          <input v-model="routeStepSearchText[step.id]" placeholder="搜索字段" @click.stop />
                        </div>
                        <div v-if="filteredItemsForStep(step.id).length === 0" style="padding:12px;color:#aaa;text-align:center;font-size:13px">暂无数据</div>
                        <div
                          class="perm-option"
                          v-for="item in filteredItemsForStep(step.id)" :key="item.id"
                          :class="{ active: step.processId === item.id }"
                          @click.stop="selectRouteStepItem(step, item.id)">
                          {{ item.name }}
                          <span v-if="step.processId === item.id" style="margin-left:auto;color:var(--accent)">✓</span>
                        </div>
                      </div>
                    </td>
                    <!-- 报工数配比 -->
                    <td>
                      <input class="collect-input" type="number" v-model.number="step.reportRatio" min="1" style="width:100%" />
                    </td>
                    <!-- 备注 -->
                    <td>
                      <input class="collect-input" v-model="step.remark" placeholder="请填写" style="width:100%" />
                    </td>
                    <!-- 操作 -->
                    <td style="text-align:center">
                      <button type="button" class="link-btn danger" @click="removeRouteStep(idx)">删除</button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="collect-empty" v-if="routeForm.steps.length === 0">
                <svg viewBox="0 0 80 60" width="72" height="54" fill="none">
                  <rect x="10" y="8" width="60" height="44" rx="4" fill="#f2f4f3" stroke="#dde3e0" stroke-width="1.5"/>
                  <rect x="18" y="18" width="44" height="6" rx="2" fill="#e2e8e5"/>
                  <rect x="18" y="30" width="32" height="5" rx="2" fill="#eaeeec"/>
                  <rect x="18" y="41" width="20" height="4" rx="2" fill="#eaeeec"/>
                </svg>
                <span>暂无数据</span>
              </div>
            </div>
          </div>
        </div>
        <!-- 底部按钮 -->
        <div class="process-form-footer">
          <button class="btn-cancel" type="button" @click="routeView = 'list'">取消</button>
          <button class="btn-save" type="button" @click="handleSaveRoute()">保存</button>
        </div>
      </section>

      <!-- 用户管理 -->
      <section class="content" v-if="false" v-show="activeMainMenu === 'sysconfig' && activeConfigMenu === 'user'">
        <div class="filters">
          <label>
            <span>姓名</span>
            <input v-model="filterForm.name" placeholder="请输入名称" />
          </label>
          <label>
            <span>状态</span>
            <select v-model="filterForm.status">
              <option>全部</option>
              <option>启用中</option>
              <option>停用中</option>
            </select>
          </label>
          <label>
            <span>账号</span>
            <input v-model="filterForm.account" placeholder="请输入账号" />
          </label>
          <label>
            <span>虚拟用户</span>
            <select v-model="filterForm.virtual">
              <option>全部</option>
              <option>是</option>
              <option>否</option>
            </select>
          </label>
          <label>
            <span>角色</span>
            <select v-model="filterForm.role">
              <option>全部</option>
              <option>管理员</option>
              <option>生产人员</option>
            </select>
          </label>
          <label>
            <span>部门</span>
            <select v-model="filterForm.department">
              <option value="">全部</option>
              <option v-for="dept in departments" :key="dept.id" :value="dept.name">{{ dept.name }}</option>
            </select>
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button" @click="loadUsers">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>

        <div class="toolbar">
          <div class="toolbar-left">
            <button class="primary-btn" type="button" @click="showCreateModal = true">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
              新增用户
            </button>
            <button class="ghost-btn" type="button" @click="handleExport">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              导出
            </button>
            <label class="ghost-btn" style="cursor:pointer">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              导入
              <input type="file" accept=".csv" style="display:none" @change="handleImport" />
            </label>
          </div>
        </div>

        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th style="min-width: 100px;">姓名</th>
                <th style="min-width: 120px;">账号</th>
                <th style="min-width: 180px;">手机号</th>
                <th style="min-width: 110px;">角色</th>
                <th style="min-width: 130px;">部门</th>
                <th style="min-width: 100px;">状态</th>
                <th style="min-width: 100px;">虚拟用户</th>
                <th style="min-width: 180px;">创建时间</th>
                <th style="min-width: 440px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in filteredUsers" :key="user.id">
                <td>{{ user.name }}</td>
                <td>{{ user.username }}</td>
                <td>
                  <span>{{ user.phone || '-' }}</span>
                </td>
                <td>{{ user.roleLabel }}</td>
                <td>{{ user.departmentName || '-' }}</td>
                <td>
                  <span class="status-badge" :class="user.enabled ? 'enabled' : 'disabled'">
                    {{ user.enabled ? '启用中' : '停用中' }}
                  </span>
                </td>
                <td>{{ user.virtualUser ? '是' : '否' }}</td>
                <td>{{ formatDateTime(user.createdAt) }}</td>
                <td>
                  <button class="action-link" type="button" @click="openResetPassword(user)">重置密码</button>
                  <button class="action-link" type="button" @click="confirmUnbindPhone(user)">解绑手机号</button>
                  <button class="action-link" type="button" @click="handlePrintUser(user)">打印</button>
                  <button class="action-link" type="button" @click="openEditUser(user)">编辑</button>
                  <button class="action-link" type="button" @click="confirmToggleEnabled(user)">{{ user.enabled ? '停用' : '启用' }}</button>
                  <button class="action-link" type="button" @click="openPermissionConfig(user)">权限配置</button>
                </td>
              </tr>
              <tr v-if="filteredUsers.length === 0">
                <td colspan="9" style="text-align:center;color:#aaa;padding:32px">暂无用户数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <span class="page-total">共 {{ users.length }} 条结果</span>
          <button class="page-btn" type="button" :disabled="currentPage <= 1" @click="currentPage--">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="page-num">{{ currentPage }}</span>
          <button class="page-btn" type="button" :disabled="currentPage >= totalPages" @click="currentPage++">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <select class="size-select" v-model="pageSize">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </section>

      <!-- 部门管理 -->
      <section class="content" v-if="false" v-show="activeMainMenu === 'sysconfig' && activeConfigMenu === 'department'">
        <div class="filters">
          <label>
            <span>部门名称</span>
            <input v-model="departmentFilterForm.name" placeholder="请输入部门名称" />
          </label>
          <label>
            <span>部门编码</span>
            <input v-model="departmentFilterForm.code" placeholder="请输入部门编码" />
          </label>
          <div class="filter-actions">
            <button class="primary-btn query-btn" type="button" @click="loadDepartments">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              查询
            </button>
          </div>
        </div>

        <div class="toolbar">
          <button class="primary-btn" @click="openCreateDepartmentModal">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            新建部门
          </button>
        </div>

        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>部门名称</th>
                <th>部门编码</th>
                <th>创建时间</th>
                <th>创建人</th>
                <th style="width:120px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dept in departments" :key="dept.id">
                <td>{{ dept.name }}</td>
                <td>{{ dept.code }}</td>
                <td>{{ formatDateTime(dept.createdAt) }}</td>
                <td>{{ dept.createdBy || '-' }}</td>
                <td class="action-cell">
                  <button class="action-link" type="button" @click="openEditDepartmentModal(dept)">编辑</button>
                  <button class="action-link danger" type="button" @click="confirmDeleteDepartment(dept)">删除</button>
                </td>
              </tr>
              <tr v-if="departments.length === 0">
                <td colspan="5" style="text-align:center;color:#aaa;padding:32px">暂无部门数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <span class="page-total">共 {{ departmentTotal }} 条结果</span>
          <button class="page-btn" type="button" :disabled="departmentFilterForm.page <= 1" @click="handleDepartmentPageChange(-1)">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="page-num">{{ departmentFilterForm.page }}</span>
          <button class="page-btn" type="button" :disabled="departmentFilterForm.page >= Math.ceil(departmentTotal / departmentFilterForm.size) || departmentTotal === 0" @click="handleDepartmentPageChange(1)">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <select class="size-select" v-model="departmentFilterForm.size" @change="loadDepartments">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>
      </section>

      <!-- ====== 车间看板（全屏深蓝科技风） ====== -->
      <section class="dashboard-fullscreen" v-show="activeMainMenu === 'dashboard'">
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
                <span class="db-legend-item"><i class="dot cyan"></i>报工次数</span>
                <span class="db-legend-item"><i class="dot blue"></i>良品产出</span>
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
                <div class="db-kpi-box-v4-value cyan">{{ dashboardKpi.inProcessOrderCount }}</div>
              </div>
              <div class="db-kpi-box-v4">
                <div class="db-kpi-box-v4-title">在制工单计划数</div>
                <div class="db-kpi-box-v4-value cyan">{{ dashboardKpi.planCount }}</div>
              </div>
              <div class="db-kpi-box-v4">
                <div class="db-kpi-box-v4-title">在制任务计划数</div>
                <div class="db-kpi-box-v4-value cyan">{{ dashboardKpi.inProcessTaskPlanCount }}</div>
              </div>
              <div class="db-kpi-box-v4">
                <div class="db-kpi-box-v4-title">在制任务数</div>
                <div class="db-kpi-box-v4-value cyan">{{ dashboardKpi.inProcessTaskCount }}</div>
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
                  <span class="db-side-value red">{{ dashboardKpi.todayDefect }}</span>
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
                <div class="db-pie-ring" :style="{ background: pieBackground }"></div>
              </div>
              <div class="db-pie-legend">
                <div v-if="pieData.length === 0" class="db-pie-empty">暂无不良数据</div>
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
                      <div class="db-step" v-for="(step, stepIndex) in row.steps" :key="`${row.code}-${stepIndex}`">
                        <div class="db-step-circle">{{ step.progress }}%</div>
                        <div class="db-step-name">{{ step.name }}</div>
                      </div>
                      <span v-if="row.steps.length === 0" style="color:#7893a6;">未配置工序</span>
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
              <span class="db-table-tag">当前</span>
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
                <tr v-if="taskProgressData.length === 0">
                  <td colspan="6" style="text-align:center;color:#7893a6;padding:18px 0;">暂无在制任务</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

    </section>
  </main>

  <div v-if="showTimezoneDialog" class="timezone-overlay modal-backdrop" @click.self="closeTimezoneDialog">
    <section class="timezone-dialog modal" role="dialog" aria-modal="true" aria-labelledby="timezone-dialog-title">
      <header class="timezone-dialog-header modal-header">
        <h2 id="timezone-dialog-title">设置时区</h2>
        <button class="timezone-close" type="button" title="关闭" aria-label="关闭" @click="closeTimezoneDialog">
          <UiIcon name="close" :size="16" />
        </button>
      </header>
      <div class="timezone-options modal-body" role="radiogroup" aria-label="时区选项">
        <label v-for="option in timezoneOptions" :key="option.value" class="timezone-option">
          <input v-model="timezoneDraft" type="radio" name="system-timezone" :value="option.value" />
          <span>{{ option.label }}</span>
        </label>
      </div>
      <footer class="timezone-dialog-footer modal-footer">
        <button class="ghost-btn" type="button" @click="closeTimezoneDialog">取消</button>
        <button class="primary-btn" type="button" @click="confirmTimezone">确认</button>
      </footer>
    </section>
  </div>

  <!-- 导入日志弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>导入结果</h2>
        <button class="close-btn" type="button" @click="showImportLogModal = false">×</button>
      </header>
      <div style="padding: 22px 44px 24px; min-width: 340px;">
        <p>总计：{{ importResult?.total }} 条</p>
        <p style="color:#0c9d82">成功：{{ importResult?.success }} 条</p>
        <p v-if="importResult?.fail" style="color:#d74434">失败：{{ importResult?.fail }} 条</p>
        <ul v-if="importResult?.errors?.length" style="margin-top:8px;padding-left:16px;color:#d74434;font-size:13px">
          <li v-for="err in importResult?.errors" :key="err">{{ err }}</li>
        </ul>
      </div>
    </section>
  </div>

  <!-- 新增用户弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>新增用户</h2>
        <button class="close-btn" type="button" @click="showCreateModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleCreateUser">
        <label>
          <span><em>*</em> 姓名</span>
          <input v-model="createForm.name" placeholder="请输入姓名" required />
        </label>
        <label>
          <span><em>*</em> 账号</span>
          <input v-model="createForm.username" placeholder="请输入账号" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="createForm.phone" placeholder="请输入手机号" />
        </label>
        <label>
          <span><em>*</em> 密码</span>
          <input v-model="createForm.password" placeholder="请输入密码" type="password" required />
        </label>
        <label>
          <span>角色</span>
          <select v-model="createForm.role">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <label>
          <span>部门</span>
          <select v-model="createForm.departmentId">
            <option :value="undefined">无</option>
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </label>
        <label>
          <span>虚拟用户</span>
          <input type="checkbox" v-model="createForm.virtualUser" style="width:auto;justify-self:start" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showCreateModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 编辑用户弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>编辑用户</h2>
        <button class="close-btn" type="button" @click="showEditModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleUpdateUser">
        <label>
          <span><em>*</em> 姓名</span>
          <input v-model="editForm.name" placeholder="请输入姓名" required />
        </label>
        <label>
          <span><em>*</em> 账号</span>
          <input v-model="editForm.username" placeholder="请输入账号" required />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="editForm.phone" placeholder="请输入手机号" />
        </label>
        <label>
          <span>角色</span>
          <select v-model="editForm.role">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <label>
          <span>部门</span>
          <select v-model="editForm.departmentId">
            <option :value="undefined">无</option>
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </label>
        <label>
          <span>虚拟用户</span>
          <input type="checkbox" v-model="editForm.virtualUser" style="width:auto;justify-self:start" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showEditModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 重置密码弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>重置密码</h2>
        <button class="close-btn" type="button" @click="showResetPasswordModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleResetPassword">
        <label>
          <span><em>*</em> 新密码</span>
          <div style="position:relative">
            <input v-model="resetPasswordForm.password" :type="showNewPassword ? 'text' : 'password'" placeholder="请输入新密码" required />
            <button type="button" class="password-toggle" @click="showNewPassword = !showNewPassword">
              <!-- 睁眼 -->
              <svg v-if="showNewPassword" viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <!-- 闭眼 -->
              <svg v-else viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10a13.5 13.5 0 0 0 18 0" /><path d="M6 12v3" /><path d="M10 13.5v3.5" /><path d="M14 13.5v3.5" /><path d="M18 12v3" /></svg>
            </button>
          </div>
        </label>
        <label>
          <span><em>*</em> 确认密码</span>
          <div style="position:relative">
            <input v-model="resetPasswordForm.confirmPassword" :type="showConfirmPassword ? 'text' : 'password'" placeholder="请再次输入密码" required />
            <button type="button" class="password-toggle" @click="showConfirmPassword = !showConfirmPassword">
              <!-- 睁眼 -->
              <svg v-if="showConfirmPassword" viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <!-- 闭眼 -->
              <svg v-else viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10a13.5 13.5 0 0 0 18 0" /><path d="M6 12v3" /><path d="M10 13.5v3.5" /><path d="M14 13.5v3.5" /><path d="M18 12v3" /></svg>
            </button>
          </div>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showResetPasswordModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 权限配置弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>权限配置</h2>
        <button class="close-btn" type="button" @click="showPermissionModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleUpdatePermission">
        <label>
          <span>角色</span>
          <select v-model="permissionRole">
            <option value="ADMIN">管理员</option>
            <option value="PRODUCTION">生产人员</option>
          </select>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showPermissionModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 新建用户成功弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal success-modal">
      <div class="success-icon">
        <svg viewBox="0 0 24 24" width="48" height="48" stroke="#0c9d82" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"/><polyline points="9 12 11 14 15 10"/></svg>
      </div>
      <h2>新建用户成功</h2>
      <div class="success-info">
        <p><span>账号：</span>{{ successUser?.username }}</p>
        <p><span>姓名：</span>{{ successUser?.name }}</p>
        <p><span>角色：</span>{{ successUser?.roleLabel }}</p>
        <p><span>密码：</span>{{ successUserPassword }}</p>
      </div>
      <footer class="modal-footer">
        <button class="ghost-btn wide" type="button" @click="copySuccessUser">复制信息</button>
        <button class="primary-btn wide" type="button" @click="successUser = null">关闭</button>
      </footer>
    </section>
  </div>

  <!-- 确认弹窗 -->
  <DeleteConfirmDialog
    :visible="confirmState.show"
    :title="confirmState.title"
    :message="confirmState.message"
    @confirm="() => { confirmState.action && confirmState.action(); confirmState.show = false }"
    @update:visible="val => { confirmState.show = val }"
  />

  <!-- 操作成功提示 -->
  <div v-if="actionMessage" class="action-toast success-toast">
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#fff" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="8 12 11 15 16 9"/></svg>
    <span>{{ actionMessage }}</span>
  </div>

  <!-- 错误提示 -->
  <div v-if="errorMessage" class="action-toast error-toast" @click="errorMessage = ''">
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#fff" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
    <span>{{ errorMessage }}</span>
  </div>

  <!-- 部门弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>{{ editingDepartment ? '编辑部门' : '新建部门' }}</h2>
        <button class="close-btn" type="button" @click="showDepartmentModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleSaveDepartment">
        <label>
          <span><em>*</em> 部门名称</span>
          <input v-model="departmentForm.name" placeholder="请输入部门名称" required />
        </label>
        <label>
          <span>部门编码</span>
          <input v-model="departmentForm.code" placeholder="留空系统自动生成" />
        </label>
        <label style="align-items: flex-start;">
          <span style="padding-top: 8px;">部门人员</span>
          <div class="custom-multiselect">
            <div ref="userSelectTriggerRef" class="multiselect-trigger" @click.stop="openUserSelectDropdown">
              <span :class="{ placeholder: !departmentForm.userIds || departmentForm.userIds?.length === 0 }">
                {{ selectedUserNames }}
              </span>
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" :style="{ transform: showUserSelectDropdown ? 'rotate(180deg)' : '', transition: 'transform 0.2s' }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </div>
            <Teleport to="body">
              <div v-if="showUserSelectDropdown" class="multiselect-dropdown multiselect-dropdown-fixed" :style="dropdownStyle" @click.stop>
                <label v-for="u in allUsersList" :key="u.id" class="multiselect-option">
                  <input type="checkbox" :value="u.id" v-model="departmentForm.userIds" />
                  <span>{{ u.name }} ({{ u.username }})</span>
                </label>
                <div v-if="allUsersList.length === 0" style="padding:8px 12px;color:#aaa;font-size:13px">暂无人员</div>
              </div>
            </Teleport>
          </div>
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showDepartmentModal = false">取消</button>
          <button class="primary-btn wide" :disabled="loading" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 单位弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>{{ editingUnit ? '编辑单位' : '创建单位' }}</h2>
        <button class="close-btn" type="button" @click="showUnitModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleSaveUnit">
        <label>
          <span><em>*</em> 名称</span>
          <input v-model="unitForm.name" placeholder="请输入单位名称" required />
        </label>
        <label>
          <span>备注</span>
          <input v-model="unitForm.remark" placeholder="请输入备注" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showUnitModal = false">取消</button>
          <button class="primary-btn wide" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 不良品项弹窗 -->
  <div v-if="false" class="modal-backdrop">
    <section class="modal">
      <header class="modal-header">
        <h2>{{ editingDefect ? '编辑不良品项' : '创建不良品项' }}</h2>
        <button class="close-btn" type="button" @click="showDefectModal = false">×</button>
      </header>
      <form class="create-form" @submit.prevent="handleSaveDefect">
        <label>
          <span style="white-space:nowrap;min-width:90px"><em>*</em> 不良品项名称</span>
          <input v-model="defectForm.name" placeholder="请输入" required />
        </label>
        <label>
          <span style="white-space:nowrap;min-width:90px">不良品项编号</span>
          <input v-model="defectForm.code" placeholder="请输入，您略将自动生成" />
        </label>
        <footer class="modal-footer">
          <button class="ghost-btn wide" type="button" @click="showDefectModal = false">取消</button>
          <button class="primary-btn wide" type="submit">确定</button>
        </footer>
      </form>
    </section>
  </div>

  <!-- 全局 Tooltip -->
  <div
    v-if="tooltipState.visible"
    class="global-tooltip"
    :style="{ left: tooltipState.x + 'px', top: tooltipState.y + 'px' }"
  >{{ tooltipState.text }}</div>

  <!-- 编辑打印模板弹窗 -->
  <div v-if="showEditPrintTemplateModal" class="modal-backdrop" style="z-index: 10000">
    <section class="modal" style="width: 900px; padding: 0;">
      <header class="modal-header" style="padding: 16px 24px; border-bottom: 1px solid #eee;">
        <h2 style="font-size: 16px; margin: 0;">编辑打印模板</h2>
        <button class="close-btn" type="button" @click="showEditPrintTemplateModal = false">×</button>
      </header>
      <div style="display: flex; height: 500px;">
        <!-- 左侧模板列表 -->
        <div style="width: 220px; border-right: 1px solid #eee; background: #fafafa; display: flex; flex-direction: column;">
          <div style="padding: 12px; display: flex; gap: 16px; border-bottom: 1px solid #eee;">
            <span style="color: #00b889; border-bottom: 2px solid #00b889; padding-bottom: 4px; font-size: 13px; cursor: pointer;">内置打印</span>
            <span style="color: #8a9693; font-size: 13px; cursor: pointer;">Excel模板打印</span>
          </div>
          <div style="padding: 16px; overflow-y: auto; flex: 1;">
            <button class="ghost-btn" style="width: 100%; border-style: dashed; margin-bottom: 16px; color: #00b889; border-color: #00b889;">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" style="margin-right: 4px; vertical-align: middle;"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              创建模板
            </button>
            <div style="display: flex; flex-direction: column; gap: 12px;">
              <div v-for="tpl in printTemplates" :key="tpl.id" 
                   @click="activeTemplateId = tpl.id"
                   style="border: 1px solid #e5e6e6; border-radius: 4px; padding: 12px; cursor: pointer; position: relative; text-align: center;"
                   :style="tpl.id === activeTemplateId ? 'background: #e8f7f3; border-color: #00b889;' : 'background: #fff;'">
                <div style="font-size: 12px; color: #555; margin-bottom: 8px; white-space: pre-wrap;">{{ tpl.type === '30x40' ? '30mm\n*\n40mm' : '80mm\n*\n60mm' }}</div>
                <div style="font-size: 12px; font-weight: bold; margin-bottom: 8px;">{{ tpl.name.split('(')[0] }}(...</div>
                <div style="font-size: 12px; color: #3498db;">删除</div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 中间编辑区 -->
        <div style="flex: 1; border-right: 1px solid #eee; padding: 24px; overflow-y: auto; background: #fdfdfd;">
          <h3 style="font-size: 14px; margin-top: 0; margin-bottom: 8px; color: #333;">配置打印模板 {{ activeTpl.type.replace('x', '*') }}mm</h3>
          <p style="font-size: 12px; color: #8a9693; margin-top: 0; margin-bottom: 24px;">通过页面配置模板需要打印的标题、字段及二维码内容</p>
          
          <div class="process-field">
            <label class="process-label required" style="font-weight: normal; margin-bottom: 8px; display: block;">模板名称</label>
            <div class="process-input-wrap">
              <input class="process-input" v-model="activeTpl.name" />
            </div>
          </div>

          <!-- 30x40 模板配置项 -->
          <template v-if="activeTpl.type === '30x40'">
            <div class="process-field" style="margin-top: 16px;">
              <label class="process-label required" style="font-weight: normal; margin-bottom: 8px; display: block;">二维码内容</label>
              <div class="process-input-wrap">
                <select class="process-input" v-model="activeTpl.qrSource" style="cursor: pointer; background: #fff;">
                  <option v-for="f in workOrderFields" :key="f" :value="f">{{ f }}</option>
                </select>
              </div>
            </div>
            <div class="process-field" style="margin-top: 16px;">
              <label class="process-label" style="font-weight: normal; margin-bottom: 8px; display: block;">第一行取值</label>
              <div class="process-input-wrap">
                <select class="process-input" v-model="activeTpl.row1" style="cursor: pointer; background: #fff;">
                  <option v-for="f in workOrderFields" :key="f" :value="f">{{ f }}</option>
                </select>
              </div>
            </div>
            <div class="process-field" style="margin-top: 16px;">
              <label class="process-label" style="font-weight: normal; margin-bottom: 8px; display: block;">第二行取值</label>
              <div class="process-input-wrap">
                <select class="process-input" v-model="activeTpl.row2" style="cursor: pointer; background: #fff;">
                  <option v-for="f in workOrderFields" :key="f" :value="f">{{ f }}</option>
                </select>
              </div>
            </div>
          </template>

          <!-- 80x60 模板配置项 -->
          <template v-else>
            <div class="process-field" style="margin-top: 16px;">
              <label class="process-label required" style="font-weight: normal; margin-bottom: 8px; display: block;">二维码内容</label>
              <div class="process-input-wrap">
                <select class="process-input" v-model="activeTpl.qrSource" style="cursor: pointer; background: #fff;">
                  <option v-for="f in workOrderFields" :key="f" :value="f">{{ f }}</option>
                </select>
              </div>
            </div>

            <!-- 标题行配置 -->
            <div style="display: flex; gap: 12px; align-items: center; margin-top: 16px;">
              <div style="flex: 1;">
                <label class="process-label" style="font-weight: normal; margin-bottom: 8px; display: block;">标题行名称</label>
                <input class="process-input" v-model="activeTpl.titleLabel" placeholder="如：工单编号" />
              </div>
              <div style="flex: 1;">
                <label class="process-label" style="font-weight: normal; margin-bottom: 8px; display: block;">标题行取值</label>
                <select class="process-input" v-model="activeTpl.titleField" style="cursor: pointer; background: #fff;">
                  <option v-for="f in workOrderFields" :key="f" :value="f">{{ f }}</option>
                </select>
              </div>
            </div>

            <!-- 第1~6行配置 -->
            <div v-for="i in 6" :key="`row-${i}`" style="display: flex; gap: 12px; align-items: flex-start; margin-top: 12px;">
              <div style="flex: 1;">
                <label class="process-label" style="font-weight: normal; margin-bottom: 8px; display: block;">第{{ i }}行名称</label>
                <input class="process-input" v-model="activeTpl[`row${i}Label`]" />
              </div>
              <div style="flex: 1;">
                <label class="process-label" style="font-weight: normal; margin-bottom: 8px; display: block;">第{{ i }}行取值 <span style="font-size: 11px; color: #8a9693;">(可多选)</span></label>
                <div style="border: 1px solid #dce3e0; border-radius: 4px; background: #fff; max-height: 140px; overflow-y: auto; padding: 4px 0;">
                  <label v-for="f in workOrderFields" :key="`row${i}-${f}`" style="display: flex; align-items: center; gap: 6px; padding: 4px 10px; cursor: pointer; font-size: 13px; color: #333;" :style="ensureArray(activeTpl[`row${i}Field`]).includes(f) ? 'background: #e8f7f3;' : ''">
                    <input type="checkbox" :value="f" :checked="ensureArray(activeTpl[`row${i}Field`]).includes(f)" @change="toggleRowField(i, f)" style="accent-color: #00b889; cursor: pointer;" />
                    {{ f }}
                  </label>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- 右侧预览区 -->
        <div style="width: 320px; padding: 24px; display: flex; flex-direction: column; background: #fff;">
          <h3 style="font-size: 14px; margin-top: 0; margin-bottom: 24px; color: #333;">模板效果预览</h3>
          <div style="flex: 1; display: flex; align-items: center; justify-content: center;">
            <!-- 30x40 预览 -->
            <div v-if="activeTpl.type === '30x40'" style="width: 200px; border: 1px solid #ccc; padding: 20px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.05); background: #fff;">
              <img v-if="qrPreviewUrl" :src="qrPreviewUrl" width="120" height="120" style="margin-bottom: 16px; mix-blend-mode: multiply;" />
              <svg v-else viewBox="0 0 100 100" width="120" height="120" style="margin-bottom: 16px;">
                <rect width="100" height="100" fill="#000" />
                <rect x="10" y="10" width="30" height="30" fill="#fff" />
                <rect x="60" y="10" width="30" height="30" fill="#fff" />
                <rect x="10" y="60" width="30" height="30" fill="#fff" />
                <rect x="60" y="60" width="30" height="30" fill="#fff" />
                <rect x="45" y="45" width="10" height="10" fill="#fff" />
              </svg>
              <div style="font-size: 14px; font-weight: bold; margin-bottom: 8px;">${工单.{{ activeTpl.row1 }}}</div>
              <div style="font-size: 12px; color: #666;">${工单.{{ activeTpl.row2 }}}</div>
            </div>
            
            <!-- 80x60 预览 -->
            <div v-else style="width: 280px; border: 2px solid #333; padding: 2px; background: #fff; display: flex; flex-direction: column; box-shadow: 0 2px 10px rgba(0,0,0,0.08);">
              <div style="text-align: center; font-size: 12px; font-weight: bold; border-bottom: 1px solid #333; padding: 6px 4px; word-break: break-all;">
                {{ activeTpl.titleLabel }}: ${工单.{{ activeTpl.titleField }}}
              </div>
              <div style="display: flex;">
                <div style="flex: 1; border-right: 1px solid #333; display: flex; flex-direction: column;">
                  <div v-for="i in 5" :key="`prev-row-${i}`" style="display: flex; border-bottom: 1px solid #333; font-size: 9px; line-height: 1.2;">
                    <div style="width: 55px; border-right: 1px solid #333; padding: 4px; display: flex; align-items: center; justify-content: center; font-weight: bold; background: #fdfdfd; text-align: center;">
                      {{ activeTpl[`row${i}Label`] }}
                    </div>
                    <div style="flex: 1; padding: 4px; display: flex; flex-direction: column; justify-content: center; word-break: break-all;">
                      <div v-for="(fld, fi) in ensureArray(activeTpl[`row${i}Field`])" :key="fi">${工单.{{ fld }}}</div>
                    </div>
                  </div>
                  <div style="display: flex; font-size: 9px; min-height: 25px; line-height: 1.2;">
                    <div style="width: 55px; border-right: 1px solid #333; padding: 4px; display: flex; align-items: center; justify-content: center; font-weight: bold; background: #fdfdfd; text-align: center;">
                      {{ activeTpl.row6Label }}
                    </div>
                    <div style="flex: 1; padding: 4px; display: flex; flex-direction: column; justify-content: center; word-break: break-all;">
                      <div v-for="(fld, fi) in ensureArray(activeTpl.row6Field)" :key="fi">${工单.{{ fld }}}</div>
                    </div>
                  </div>
                </div>
                <div style="width: 90px; display: flex; align-items: center; justify-content: center; padding: 6px;">
                  <img v-if="qrPreviewUrl" :src="qrPreviewUrl" width="76" height="76" style="mix-blend-mode: multiply;" />
                  <svg v-else viewBox="0 0 100 100" width="76" height="76">
                    <rect width="100" height="100" fill="#000" />
                    <rect x="10" y="10" width="30" height="30" fill="#fff" />
                    <rect x="60" y="10" width="30" height="30" fill="#fff" />
                    <rect x="10" y="60" width="30" height="30" fill="#fff" />
                    <rect x="60" y="60" width="30" height="30" fill="#fff" />
                    <rect x="45" y="45" width="10" height="10" fill="#fff" />
                  </svg>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <footer class="modal-footer" style="padding: 16px 24px; border-top: 1px solid #eee; background: #fff;">
        <button class="ghost-btn" style="min-width: 80px;" type="button" @click="showEditPrintTemplateModal = false">取消</button>
        <button class="primary-btn" style="min-width: 80px;" type="button" @click="savePrintTemplates">保存</button>
      </footer>
    </section>
  </div>

  <LoadingProgressOverlay
    :visible="workspaceLoadingVisible && !isInternalChatWindow"
    :progress="workspaceLoadingProgress"
    :stage="workspaceLoadingStage"
    :stages="workspaceLoadingStages"
    :error="workspaceLoadingError"
    @retry="retryWorkspaceBootstrap"
  />
</template>

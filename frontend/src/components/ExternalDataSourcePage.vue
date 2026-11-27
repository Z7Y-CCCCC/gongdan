<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { externalSources, type ExternalSourceInfo, type ExternalSourceConfig, type ExternalSourceCheck, type ExternalSchemaResult, type ExternalSourceCapabilities } from '../api'
import UiIcon from './UiIcon.vue'
import { useDialogFocus } from '../composables/useDialogFocus'
import { workspaceDialogMotion } from '../workspaceDialogMotion'
import { t } from '../i18n'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{ (e: 'error', message: string): void; (e: 'notice', message: string): void }>()
const items = ref<ExternalSourceInfo[]>([])
const total = ref(0)
const page = ref(0)
const more = ref(false)
const archived = ref(false)
const query = ref('')
const filter = ref('ALL')
const enabledFilter = ref('ALL')
const now = ref(Date.now())
let clock: ReturnType<typeof setInterval> | undefined
const busy = ref(false)
const error = ref('')
const message = ref('')
const editing = ref(false)
const selected = ref<ExternalSourceInfo | null>(null)
const emptyCapabilities = (): ExternalSourceCapabilities => ({ secretsReady: false, sqliteFiles: [], databaseDrivers: [], pendingDatabaseDrivers: [] })
const capabilities = ref<ExternalSourceCapabilities>(emptyCapabilities())
const capabilitiesLoaded = ref(false)
const result = ref<ExternalSourceCheck | null>(null)
const resultKind = ref<'HTTP' | 'DATABASE'>('HTTP')
const resultContext = ref('')
const editorForm = ref<HTMLFormElement | null>(null)
const history = ref<ExternalSourceCheck[] | null>(null)
const historySource = ref<ExternalSourceInfo | null>(null)
const historyPage = ref(0)
const historyMore = ref(false)
const schemaResult = ref<ExternalSchemaResult | null>(null)
const schemaSource = ref<ExternalSourceInfo | null>(null)
const schemaTable = ref('')
const schemaPage = ref(0)
const confirmArchive = ref<ExternalSourceInfo | null>(null)
const editorDialog = ref<HTMLElement | null>(null)
const editorName = ref<HTMLInputElement | null>(null)
const historyDialog = ref<HTMLElement | null>(null)
const schemaDialog = ref<HTMLElement | null>(null)
const archiveDialog = ref<HTMLElement | null>(null)
let editorBaseline = ''
useDialogFocus(editorDialog, () => editing.value, closeEditor, () => editorName.value)
useDialogFocus(historyDialog, () => Boolean(history.value), closeHistory)
useDialogFocus(schemaDialog, () => Boolean(schemaResult.value), closeSchema)
useDialogFocus(archiveDialog, () => Boolean(confirmArchive.value), closeArchive)
let generation = 0
let active = true
let historyGeneration = 0
let schemaGeneration = 0
type RequestContext = { current: () => boolean; token: string }
const defaults = (): ExternalSourceConfig => ({
  code: '', name: '', description: '', kind: 'HTTP', driver: 'MYSQL', baseUrl: '', healthPath: '/health',
  authType: 'NONE', headerName: 'X-API-Key', username: '', host: '', port: 3306, database: '', schema: '',
  fileId: '', tls: true, timeoutSeconds: 5, secretAction: 'REPLACE', secretValue: ''
})
const form = reactive<ExternalSourceConfig>(defaults())
const filtered = computed(() => items.value.filter(s =>
  (filter.value === 'ALL' || s.config.kind === filter.value) &&
  (enabledFilter.value === 'ALL' || s.enabled === (enabledFilter.value === 'ENABLED')) &&
  (s.config.name + s.config.code).toLowerCase().includes(query.value.toLowerCase())))
const needsSecret = computed(() => form.kind === 'DATABASE' ? form.driver !== 'SQLITE' : form.authType !== 'NONE')
const databaseDrivers: ExternalSourceConfig['driver'][] = ['MYSQL', 'POSTGRESQL', 'SQLSERVER', 'SQLITE']
const editorSupported = computed(() => supported(form))
const canKeepSecret = computed(() => !!selected.value?.secretConfigured && secretIdentity(selected.value.config) === secretIdentity(form))
const healthLabels: Record<string, string> = { UNKNOWN: '未检查', CHECKING: '检查中', UP: '可达', FAILED: '失败', STALE: '已过期' }
const typeLabels: Record<string, string> = { HTTP: 'HTTP API', MYSQL: 'MySQL', POSTGRESQL: 'PostgreSQL', SQLSERVER: 'SQL Server', SQLITE: 'SQLite' }
const finalHealth = computed(() => form.baseUrl.trim().replace(/\/+$/, '') + form.healthPath.trim())
const pendingDriverMessage = '该数据库适配尚未完成真实服务连接与 TLS 验收，暂不开放保存、测试、启用和元数据浏览。'
function supported(config: ExternalSourceConfig) { return config.kind === 'HTTP' || capabilities.value.databaseDrivers.includes(config.driver) }
function requireSupported(config: ExternalSourceConfig) { if (!supported(config)) throw new Error(pendingDriverMessage) }
function secretIdentity(config: ExternalSourceConfig) {
  if (config.kind === 'DATABASE') return JSON.stringify(['DATABASE', config.driver, config.host, config.port, config.database, config.schema, config.username, config.tls])
  let scope = config.baseUrl.trim().replace(/\/+$/, '')
  try {
    const url = new URL(config.baseUrl.trim())
    scope = JSON.stringify([url.protocol, url.hostname.toLowerCase(), url.port || (url.protocol === 'https:' ? '443' : '80'), url.pathname.replace(/\/+$/, '')])
  } catch { /* Invalid URLs remain distinct until normal form/server validation. */ }
  return JSON.stringify(['HTTP', scope, config.authType, config.authType === 'API_KEY' ? config.headerName.toLowerCase() : '', config.authType === 'BASIC' ? config.username : ''])
}
watch(() => secretIdentity(form), () => {
  form.secretValue = ''
  if (!canKeepSecret.value && form.secretAction === 'KEEP') form.secretAction = 'REPLACE'
}, { flush: 'sync' })
watch(() => form.secretAction, action => { if (action !== 'REPLACE') form.secretValue = '' }, { flush: 'sync' })
function time(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function target(s: ExternalSourceInfo) {
  const c = s.config
  return c.kind === 'HTTP' ? c.baseUrl : c.driver === 'SQLITE' ? c.fileId : c.host + ':' + c.port + '/' + c.database
}
function invalidateResult() { result.value = null; resultContext.value = ''; message.value = '' }
function clearSecrets() {
  editing.value = false; selected.value = null
  Object.assign(form, defaults(), { version: undefined, sourceId: undefined })
  invalidateResult()
}
function closeHistory() { historyGeneration++; history.value = null; historySource.value = null; historyPage.value = 0; historyMore.value = false }
function closeSchema() { schemaGeneration++; schemaResult.value = null; schemaSource.value = null; schemaTable.value = ''; schemaPage.value = 0 }
function closeArchive() { if (!busy.value) confirmArchive.value = null }
function closeEditor() {
  if (busy.value) return
  const hasChanges = JSON.stringify({ ...form, secretValue: '' }) !== editorBaseline || Boolean(form.secretValue)
  if (hasChanges && !window.confirm(t('关闭后，本次修改不会保存。'))) return
  clearSecrets()
  editorBaseline = ''
}
function clearDialogs() { clearSecrets(); closeHistory(); closeSchema(); confirmArchive.value = null }
function sourceHealth(source: ExternalSourceInfo) {
  const checked = source.lastCheck
  return checked && ((checked.configVersion != null && checked.configVersion !== source.version) ||
    now.value - new Date(checked.startedAt).getTime() > 15 * 60 * 1000 ||
    (checked.status === 'CHECKING' && now.value - new Date(checked.startedAt).getTime() > 35000)) ? 'STALE' : source.health
}
function startClock() { now.value = Date.now(); if (clock === undefined) clock = setInterval(() => { now.value = Date.now() }, 30000) }
function stopClock() { clearInterval(clock); clock = undefined }
startClock()
onBeforeUnmount(() => { active = false; generation++; clearDialogs(); stopClock() })
onDeactivated(() => { active = false; generation++; clearDialogs(); busy.value = false; stopClock() })
onActivated(() => { active = true; startClock(); void refresh() })
async function run(work: (request: RequestContext) => Promise<void>) {
  if (busy.value || !active || !props.token) return
  const runId = generation
  const request = { current: () => active && generation === runId, token: props.token }
  busy.value = true; error.value = ''; message.value = ''
  try { await work(request) }
  catch (e) { if (request.current()) error.value = e instanceof Error ? e.message : '操作失败' }
  finally { if (request.current()) busy.value = false }
}
async function load(request: RequestContext, nextPage = page.value, nextArchived = archived.value) {
  const data = await externalSources.list(request.token, nextPage, nextArchived)
  if (!request.current()) return
  if (!data.items.length && data.total > 0 && nextPage > 0) { await load(request, nextPage - 1, nextArchived); return }
  if (data.total === 0) nextPage = 0
  items.value = data.items; total.value = data.total; more.value = data.hasMore; page.value = nextPage; archived.value = nextArchived
}
async function refresh() {
  await run(async request => {
    const caps = await externalSources.capabilities(request.token)
    if (!request.current()) return
    capabilities.value = caps; capabilitiesLoaded.value = true
    await load(request)
  })
}
watch(() => props.token, () => {
  generation++; busy.value = false; clearDialogs(); items.value = []; total.value = 0; more.value = false; page.value = 0
  archived.value = false; query.value = ''; filter.value = 'ALL'; enabledFilter.value = 'ALL'
  capabilities.value = emptyCapabilities(); capabilitiesLoaded.value = false; error.value = ''; message.value = ''
  void refresh()
}, { immediate: true, flush: 'sync' })
function populate(source?: ExternalSourceInfo) {
  selected.value = source || null
  Object.assign(form, defaults(), source?.config || {}, { secretValue: '', secretAction: source?.secretConfigured ? 'KEEP' : 'REPLACE', version: source?.version, sourceId: source?.id })
}
function open(source?: ExternalSourceInfo) {
  populate(source)
  editorBaseline = JSON.stringify({ ...form, secretValue: '' })
  invalidateResult(); editing.value = true; error.value = ''
}
function kindChanged() {
  form.secretValue = ''; form.secretAction = 'REPLACE'; form.authType = 'NONE'; form.username = ''
  if (form.kind === 'DATABASE') {
    form.driver = databaseDrivers.find(driver => capabilities.value.databaseDrivers.includes(driver)) || 'SQLITE'
    driverChanged()
  }
}
function authChanged() {
  form.secretValue = ''; form.secretAction = 'REPLACE'
  if (form.authType !== 'BASIC') form.username = ''
  if (form.authType === 'API_KEY' && !form.headerName) form.headerName = 'X-API-Key'
}
function driverChanged() {
  const ports: Record<string, number> = { MYSQL: 3306, POSTGRESQL: 5432, SQLSERVER: 1433, SQLITE: 1 }
  form.port = ports[form.driver] || 3306
  form.schema = form.driver === 'POSTGRESQL' ? 'public' : form.driver === 'SQLSERVER' ? 'dbo' : ''
}
function payload(): ExternalSourceConfig {
  const config: ExternalSourceConfig = { ...defaults(), code: form.code.trim(), name: form.name.trim(), description: form.description,
    kind: form.kind, healthPath: '', headerName: '', timeoutSeconds: form.timeoutSeconds, version: form.version, sourceId: form.sourceId,
    secretAction: needsSecret.value ? form.secretAction : 'CLEAR', secretValue: needsSecret.value && form.secretAction === 'REPLACE' ? form.secretValue : '' }
  if (form.kind === 'HTTP') Object.assign(config, { baseUrl: form.baseUrl.trim(), healthPath: form.healthPath.trim(), authType: form.authType,
    headerName: form.authType === 'API_KEY' ? form.headerName : '', username: form.authType === 'BASIC' ? form.username : '' })
  else if (form.driver === 'SQLITE') Object.assign(config, { driver: form.driver, fileId: form.fileId })
  else Object.assign(config, { driver: form.driver, host: form.host, port: form.port, database: form.database, schema: form.schema, username: form.username, tls: form.tls })
  return config
}
function validEditor() {
  if (!editorSupported.value) { error.value = pendingDriverMessage; return false }
  if (form.kind === 'DATABASE' && form.driver === 'SQLITE' && form.fileId && !capabilities.value.sqliteFiles.includes(form.fileId)) {
    error.value = 'SQLite 文件登记已失效，请选择当前工厂已登记的只读文件'; return false
  }
  return editorForm.value?.reportValidity() ?? false
}
function showResult(check: ExternalSourceCheck, kind: 'HTTP' | 'DATABASE', context: string) {
  result.value = check; resultKind.value = kind; resultContext.value = context
}
async function save(andTest = false) {
  if (!validEditor()) return
  await run(async request => {
    invalidateResult()
    const saved = await externalSources.save(request.token, payload(), selected.value?.id)
    if (!request.current()) return
    populate(saved)
    editorBaseline = JSON.stringify({ ...form, secretValue: '' })
    message.value = '配置已保存'
    if (andTest) {
      try {
        const check = await externalSources.test(request.token, saved.id)
        if (!request.current()) return
        showResult(check, saved.config.kind, saved.config.name + ' · 已保存配置')
        message.value = check.status === 'UP' ? '保存成功，连接测试通过' : '保存成功；连接测试未通过或未配置'
      } catch (e) {
        if (!request.current()) return
        message.value = '配置已保存，测试未完成'
        error.value = e instanceof Error ? e.message : '测试失败'
      }
    }
    if (request.current()) await load(request)
  })
}
async function draftTest() {
  if (!validEditor()) return
  await run(async request => {
    invalidateResult()
    const check = await externalSources.testDraft(request.token, payload())
    if (request.current()) { showResult(check, form.kind, form.name + ' · 未保存配置'); message.value = '当前表单测试完成，未保存配置' }
  })
}
async function test(source: ExternalSourceInfo) {
  await run(async request => {
    requireSupported(source.config); invalidateResult()
    const check = await externalSources.test(request.token, source.id)
    if (!request.current()) return
    showResult(check, source.config.kind, source.config.name + ' · 已保存配置'); message.value = source.config.name + '：' + check.message
    await load(request)
  })
}
async function action(source: ExternalSourceInfo, value: 'enable' | 'disable' | 'archive' | 'restore') {
  await run(async request => {
    if (value === 'enable') requireSupported(source.config)
    await externalSources.action(request.token, source, value)
    if (!request.current()) return
    confirmArchive.value = null; message.value = '状态已更新'
    result.value = null; resultContext.value = ''
    await load(request)
  })
}
async function showHistory(source: ExternalSourceInfo, nextPage = 0) {
  const dialogId = historyGeneration
  await run(async request => {
    const data = await externalSources.history(request.token, source.id, nextPage)
    if (!request.current() || historyGeneration !== dialogId) return
    historySource.value = source; historyPage.value = nextPage; history.value = data.content; historyMore.value = !data.last
  })
}
async function showSchema(source: ExternalSourceInfo, table = '', nextPage = 0) {
  const dialogId = schemaGeneration
  await run(async request => {
    requireSupported(source.config)
    const data = await externalSources.schema(request.token, source.id, table, nextPage)
    if (!request.current() || schemaGeneration !== dialogId) return
    schemaSource.value = source; schemaTable.value = table; schemaPage.value = nextPage; schemaResult.value = data
  })
}
async function changePage(delta: number) {
  await run(request => load(request, Math.max(0, page.value + delta)))
}
async function changeArchive(event: Event) {
  const input = event.target as HTMLInputElement
  const nextArchived = input.checked
  input.checked = archived.value
  await run(request => load(request, 0, nextArchived))
}
</script>

<template>
  <section class="external-sources">
    <header class="source-header">
      <div><h2>外部数据源</h2></div>
      <button class="primary ui-icon-button" :disabled="busy || !capabilitiesLoaded" @click="open()" title="新增数据源" aria-label="新增数据源" type="button"><UiIcon name="plus" /></button>
    </header>
    <div class="scope-note"><strong>当前阶段：连接管理</strong><span>健康可达 ≠ 业务数据可用。数据接口与字段映射将在后续配置；这里不执行备份或业务同步。</span></div>
    <div v-if="capabilitiesLoaded && !capabilities.secretsReady" class="warning">尚未配置服务端凭据加密密钥。需要密码或 Token 的连接须先由运维配置密钥。</div>
    <p v-if="capabilities.pendingDatabaseDrivers?.length" class="hint">{{ capabilities.pendingDatabaseDrivers.map(driver => typeLabels[driver] || driver).join(' / ') }}：待真实服务连接与 TLS 验收，暂不开放使用。</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="message" role="status" class="success">{{ message }}</p>
    <div class="toolbar">
      <div class="type-tabs" aria-label="数据源类型"><button v-for="t in [['ALL','全部'],['DATABASE','数据库'],['HTTP','HTTP API']]" :key="t[0]" :class="{ active: filter === t[0] }" @click="filter = t[0]">{{ t[1] }}</button></div>
      <input v-model="query" aria-label="搜索当前页数据源" placeholder="搜索当前页名称 / 编码" />
      <select v-model="enabledFilter" aria-label="启停筛选"><option value="ALL">全部状态</option><option value="ENABLED">已启用</option><option value="DISABLED">已停用</option></select>
      <label><input :checked="archived" type="checkbox" :disabled="busy" @change="changeArchive" />已归档</label>
      <button :disabled="busy" @click="refresh" title="刷新" aria-label="刷新" class="ui-icon-button" type="button"><UiIcon name="refresh" /></button>
    </div>
    <div class="table-scroll"><table>
      <thead><tr><th>名称 / 编码</th><th>类型 / 目标</th><th>状态</th><th>最近连接健康</th><th>耗时 / HTTP</th><th>最后检查</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="source in filtered" :key="source.id">
          <td><strong>{{ source.config.name }}</strong><small>{{ source.config.code }}</small></td>
          <td>{{ typeLabels[source.config.kind === 'HTTP' ? 'HTTP' : source.config.driver] }}<small class="target" :title="target(source)">{{ target(source) }}</small><small v-if="!supported(source.config)" class="driver-warning">适配待验收，暂不可用</small></td>
          <td>{{ source.archived ? '已归档' : source.enabled ? '已启用' : '已停用' }}</td>
          <td><span :class="['badge', sourceHealth(source).toLowerCase()]">{{ healthLabels[sourceHealth(source)] || sourceHealth(source) }}</span><small>业务数据：未配置</small></td>
          <td>{{ source.lastCheck?.durationMs != null ? source.lastCheck.durationMs + ' ms' : '—' }}<small>HTTP {{ source.config.kind === 'DATABASE' ? '不适用' : source.lastCheck?.httpStatus ?? '—' }}</small></td>
          <td>{{ time(source.lastCheck?.finishedAt || source.lastCheck?.startedAt) }}</td>
          <td><div class="actions">
            <template v-if="!source.archived">
              <button :disabled="busy" @click="open(source)" title="编辑" aria-label="编辑" class="ui-icon-button" type="button"><UiIcon name="edit" /></button><button :disabled="busy || !supported(source.config)" :title="(!supported(source.config) ? pendingDriverMessage : undefined) || '测试连接'" @click="test(source)" aria-label="测试连接"  type="button"><UiIcon name="activity" />测试</button>
              <button v-if="source.config.kind === 'DATABASE'" :disabled="busy || !supported(source.config)" :title="(!supported(source.config) ? pendingDriverMessage : undefined) || '表与字段'" @click="showSchema(source)" aria-label="表与字段"  type="button"><UiIcon name="table-columns" />结构</button>
              <button :disabled="busy || (!source.enabled && !supported(source.config))" :title="!source.enabled && !supported(source.config) ? pendingDriverMessage : undefined" @click="action(source, source.enabled ? 'disable' : 'enable')">{{ source.enabled ? '停用' : '启用' }}</button>
              <button :disabled="busy" @click="confirmArchive = source">归档</button>
            </template>
            <button v-else :disabled="busy" @click="action(source, 'restore')">恢复为停用</button>
            <button :disabled="busy" @click="showHistory(source)" title="记录" aria-label="记录"  type="button"><UiIcon name="clock" />记录</button>
          </div></td>
        </tr>
        <tr v-if="!filtered.length"><td colspan="7" class="empty"><UiIcon name="layers" :size="32" /><h3>{{ busy ? '正在加载…' : '暂无数据源' }}</h3><p>外部连接不会替换本系统数据库。</p></td></tr>
      </tbody>
    </table></div>
    <footer class="pagination"><span>共 {{ total }} 个 · 第 {{ page + 1 }} 页</span><button :disabled="busy || page === 0" @click="changePage(-1)" title="上一页" aria-label="上一页" class="ui-icon-button" type="button"><UiIcon name="arrowLeft" /></button><button :disabled="busy || !more" @click="changePage(1)" title="下一页" aria-label="下一页" class="ui-icon-button" type="button"><UiIcon name="arrowRight" /></button></footer>
    <div v-if="result && !editing" class="test-result"><p>{{ resultContext }}<template v-if="result.configVersion != null"> · 版本 {{ result.configVersion }}</template></p><strong>{{ healthLabels[result.status] }} · {{ result.message }}</strong><small>HTTP {{ resultKind === 'DATABASE' ? '不适用' : result.httpStatus ?? '—' }} · {{ result.durationMs ?? '—' }} ms · 追踪编号 {{ result.traceId }}</small></div>

    <Transition v-bind="workspaceDialogMotion">
    <div v-if="editing" class="overlay workspace-dialog-motion-backdrop">
      <section ref="editorDialog" class="dialog workspace-dialog-motion-panel" role="dialog" aria-modal="true" aria-labelledby="source-form-title" tabindex="-1">
        <header><div><h3 id="source-form-title">{{ selected ? '编辑数据源' : '新增数据源' }}</h3><p>配置由当前工厂隔离保存，外部请求仅从后端发出。</p></div><button :disabled="busy" aria-label="关闭配置" @click="closeEditor" title="关闭配置" class="ui-icon-button" type="button"><UiIcon name="close" /></button></header>
        <form ref="editorForm" @submit.prevent="save(false)" @input="invalidateResult" @change="invalidateResult">
          <fieldset :disabled="busy">
            <div class="form-grid">
              <label>名称<input ref="editorName" v-model="form.name" maxlength="128" required placeholder="例如：车间设备采集服务" /></label>
              <label>编码<input v-model="form.code" :disabled="!!selected" maxlength="64" pattern="[A-Za-z0-9_\-]+" required placeholder="例如：workshop_mes" /></label>
              <label>类型<select v-model="form.kind" :disabled="!!selected" @change="kindChanged"><option value="HTTP">HTTP API</option><option value="DATABASE">数据库</option></select></label>
              <label>请求总超时（秒）<input v-model.number="form.timeoutSeconds" type="number" min="1" max="30" required /></label>
              <template v-if="form.kind === 'HTTP'">
                <label class="wide">API 根地址<input v-model="form.baseUrl" type="url" maxlength="1000" required placeholder="https://mes.example.com/api/v1" /></label>
                <label>健康检查路径<input v-model="form.healthPath" maxlength="300" placeholder="/health（可留空）" /></label>
                <label>请求方式<input value="GET（第一版固定）" disabled /></label>
                <p class="hint wide">最终检查地址：{{ form.healthPath.trim() ? finalHealth : '未配置，不会自动请求根地址' }}</p>
                <label>认证方式<select v-model="form.authType" @change="authChanged"><option value="NONE">无认证</option><option value="API_KEY">API Key（Header）</option><option value="BEARER">Bearer Token</option><option value="BASIC">Basic</option></select></label>
                <label v-if="form.authType === 'API_KEY'">请求头名称<input v-model="form.headerName" required maxlength="64" placeholder="X-API-Key" /></label>
                <label v-if="form.authType === 'BASIC'">用户名<input v-model="form.username" required maxlength="128" autocomplete="off" /></label>
              </template>
              <template v-else>
                <label>数据库类型<select v-model="form.driver" :disabled="!!selected" @change="driverChanged"><option v-for="driver in databaseDrivers" :key="driver" :value="driver" :disabled="!capabilities.databaseDrivers.includes(driver)">{{ typeLabels[driver] }}{{ capabilities.databaseDrivers.includes(driver) ? '' : '（待真实连接 / TLS 验收，暂不可用）' }}</option></select></label>
                <p v-if="!editorSupported" class="warning wide">{{ pendingDriverMessage }}</p>
                <template v-if="form.driver === 'SQLITE'">
                  <label>已登记只读文件<select v-model="form.fileId" required><option value="" disabled>请选择运维登记文件</option><option v-if="form.fileId && !capabilities.sqliteFiles.includes(form.fileId)" :value="form.fileId" disabled>{{ form.fileId }}（登记已失效）</option><option v-for="id in capabilities.sqliteFiles" :key="id" :value="id">{{ id }}</option></select></label>
                  <p class="hint wide">SQLite 读取后端专用目录中的只读文件，不是您电脑上的文件。不支持任意路径或文件上传。</p>
                  <p v-if="!capabilities.sqliteFiles.length" class="warning wide">当前工厂尚未登记 SQLite 文件，请先联系运维配置只读文件。</p>
                </template>
                <template v-else>
                  <label>主机（固定 IPv4）<input v-model="form.host" required placeholder="10.20.0.15" /></label>
                  <label>端口<input v-model.number="form.port" type="number" min="1" max="65535" required /></label>
                  <label>数据库名<input v-model="form.database" required maxlength="128" /></label>
                  <label>Schema<input v-model="form.schema" maxlength="128" :required="form.driver === 'POSTGRESQL' || form.driver === 'SQLSERVER'" placeholder="例如 public / dbo" /></label>
                  <label>只读用户名<input v-model="form.username" required maxlength="128" autocomplete="off" /></label>
                  <label class="check-label"><input v-model="form.tls" type="checkbox" />TLS 并验证证书（推荐）</label>
                  <p class="hint wide">只读权限需由对方数据库授予。连接测试不会尝试写入；不提供 SQL 控制台。</p>
                </template>
              </template>
              <template v-if="needsSecret">
                <label>凭据操作<select v-model="form.secretAction"><option v-if="canKeepSecret" value="KEEP">保留已保存凭据</option><option value="REPLACE">设置 / 替换凭据</option></select></label>
                <label v-if="form.secretAction === 'REPLACE'">{{ form.kind === 'HTTP' && form.authType === 'BEARER' ? 'Token' : form.kind === 'HTTP' && form.authType === 'API_KEY' ? 'API Key' : '密码' }}<input v-model="form.secretValue" type="password" maxlength="4096" required autocomplete="new-password" /></label>
                <p class="hint wide">凭据加密保存，不回显、不持久存储到浏览器。更换连接目标或认证身份需重新填写；改为无认证并保存会清除旧凭据。</p>
              </template>
              <p v-else-if="selected?.secretConfigured" class="warning wide">保存后将清除已保存凭据。</p>
              <label class="wide">用途说明<textarea v-model="form.description" maxlength="500" rows="2" placeholder="例如：后续在工单详情展示批次温度" /></label>
            </div>
          </fieldset>
          <p class="hint">目标须由运维按工厂配置出口白名单；默认禁止明文连接和未批准地址。公开健康接口成功不能证明业务认证正确。</p>
          <p v-if="error" role="alert" class="error">{{ error }}</p><p v-if="message" role="status" class="success">{{ message }}</p>
          <div v-if="result" class="test-result"><p>{{ resultContext }}<template v-if="result.configVersion != null"> · 版本 {{ result.configVersion }}</template></p><strong>{{ healthLabels[result.status] }} · {{ result.message }}</strong><small>HTTP {{ resultKind === 'DATABASE' ? '不适用' : result.httpStatus ?? '—' }} · {{ result.durationMs ?? '—' }} ms · {{ result.traceId }}</small></div>
          <footer><button type="button" :disabled="busy || !editorSupported" @click="draftTest">测试当前配置</button><button type="submit" :disabled="busy || !editorSupported">保存配置</button><button type="button" class="primary" :disabled="busy || !editorSupported" @click="save(true)">保存并测试</button></footer>
        </form>
      </section>
    </div>
    </Transition>
    <Transition v-bind="workspaceDialogMotion">
    <div v-if="history" class="overlay workspace-dialog-motion-backdrop"><section ref="historyDialog" class="dialog workspace-dialog-motion-panel" role="dialog" aria-modal="true" aria-label="检查记录" tabindex="-1">
      <header><h3>{{ historySource?.config.name }} · 检查记录</h3><button @click="closeHistory" title="关闭" aria-label="关闭" class="ui-icon-button" type="button"><UiIcon name="close" /></button></header>
      <p>仅手动检查，健康结果 15 分钟后标记过期。检查记录保留 30 天。</p>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <div class="table-scroll"><table><thead><tr><th>时间 / 版本</th><th>结果</th><th>HTTP / 耗时</th><th>说明 / 追踪编号</th></tr></thead><tbody>
        <tr v-for="check in history" :key="check.id"><td>{{ time(check.startedAt) }}<small>版本 {{ check.configVersion }}</small></td><td>{{ healthLabels[check.status] }}</td><td>{{ check.httpStatus ?? '—' }} / {{ check.durationMs ?? '—' }} ms</td><td>{{ check.message }}<small>{{ check.traceId }}</small></td></tr>
        <tr v-if="!history.length"><td colspan="4">暂无检查记录</td></tr>
      </tbody></table></div>
      <footer><button :disabled="busy || historyPage === 0" @click="historySource && showHistory(historySource, historyPage - 1)" title="上一页" aria-label="上一页" class="ui-icon-button" type="button"><UiIcon name="arrowLeft" /></button><span>第 {{ historyPage + 1 }} 页</span><button :disabled="busy || !historyMore" @click="historySource && showHistory(historySource, historyPage + 1)" title="下一页" aria-label="下一页" class="ui-icon-button" type="button"><UiIcon name="arrowRight" /></button></footer>
    </section></div>
    </Transition>
    <Transition v-bind="workspaceDialogMotion">
    <div v-if="schemaResult" class="overlay workspace-dialog-motion-backdrop"><section ref="schemaDialog" class="dialog workspace-dialog-motion-panel" role="dialog" aria-modal="true" aria-label="表与字段" tabindex="-1">
      <header><h3>{{ schemaSource?.config.name }} · {{ schemaTable || '表与视图' }}</h3><button @click="closeSchema" title="关闭" aria-label="关闭" class="ui-icon-button" type="button"><UiIcon name="close" /></button></header>
      <p>{{ schemaResult.message }}</p><p v-if="error" role="alert" class="error">{{ error }}</p>
      <button v-if="schemaTable" :disabled="busy" @click="schemaSource && showSchema(schemaSource)">返回表列表</button>
      <div class="table-scroll"><table><thead><tr><th>Schema</th><th>{{ schemaTable ? '字段名' : '表名' }}</th><th>类型</th><th>{{ schemaTable ? '可空 / 主键' : '操作' }}</th></tr></thead><tbody>
        <tr v-for="(row, i) in schemaResult.items" :key="i"><td>{{ row.schema || '—' }}</td><td>{{ row.column || row.table }}</td><td>{{ row.type }}</td><td><template v-if="schemaTable">{{ row.nullable == null ? '未知' : row.nullable ? '可空' : '不可空' }} / {{ row.primaryKey ? '主键' : '—' }}</template><button v-else :disabled="busy" @click="schemaSource && showSchema(schemaSource, row.table)">查看字段</button></td></tr>
        <tr v-if="!schemaResult.items.length"><td colspan="4">暂无可见{{ schemaTable ? '字段' : '表或视图' }}，可能为空或当前账号无可见权限。</td></tr>
      </tbody></table></div>
      <footer><button :disabled="busy || schemaPage === 0" @click="schemaSource && showSchema(schemaSource, schemaTable, schemaPage - 1)" title="上一页" aria-label="上一页" class="ui-icon-button" type="button"><UiIcon name="arrowLeft" /></button><span>第 {{ schemaPage + 1 }} 页</span><button :disabled="busy || !schemaResult.hasMore" @click="schemaSource && showSchema(schemaSource, schemaTable, schemaPage + 1)" title="下一页" aria-label="下一页" class="ui-icon-button" type="button"><UiIcon name="arrowRight" /></button></footer>
    </section></div>
    </Transition>
    <Transition v-bind="workspaceDialogMotion">
    <div v-if="confirmArchive" class="overlay workspace-dialog-motion-backdrop"><section ref="archiveDialog" class="dialog compact workspace-dialog-motion-panel" role="dialog" aria-modal="true" aria-label="归档数据源" tabindex="-1"><h3>归档「{{ confirmArchive.config.name }}」？</h3><p>将停用连接并移入归档列表，保留配置和检查记录。可随时恢复为停用状态。</p><footer><button :disabled="busy" @click="closeArchive" title="取消" aria-label="取消" class="ui-icon-button" type="button"><UiIcon name="close" /></button><button class="primary" :disabled="busy" @click="action(confirmArchive, 'archive')">确认归档</button></footer></section></div>
    </Transition>
  </section>
</template>

<style scoped>
.external-sources { padding:24px; color:var(--text-primary,#24334b); }
.source-header,.toolbar,.source-header>div,header,footer,.actions { display:flex; gap:12px; }
.source-header,header { align-items:center; justify-content:space-between; }
.source-header>div { flex-direction:column; gap:6px; }
h2,h3,p { margin:0; } h2 { font-size:24px; } h3 { font-size:18px; }
p,small { color:var(--text-secondary,#718096); font-size:13px; line-height:1.6; }
.eyebrow { color:#3578cf; font-size:12px; font-weight:600; letter-spacing:1px; }
button { border:1px solid var(--border-color,#dce3ed); border-radius:7px; background:var(--bg-primary,#fff); color:inherit; padding:8px 13px; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; gap:6px; white-space:nowrap; }
button:hover:not(:disabled) { border-color:#4b8de5; color:#3479d3; }
button:disabled { opacity:.5; cursor:not-allowed; }
.primary { background:#347bd6; color:white; border-color:#347bd6; }
.primary:hover:not(:disabled) { background:#2767bb; color:white; }
.scope-note { display:flex; gap:18px; align-items:center; padding:15px 18px; margin:22px 0 15px; background:var(--bg-secondary,#f0f6fd); border:1px solid var(--border-color,#dce9f9); border-radius:9px; font-size:13px; }
.scope-note span { color:var(--text-secondary,#687b93); }
.toolbar { flex-wrap:wrap; align-items:center; margin:20px 0 14px; }
.type-tabs { display:flex; gap:4px; margin-right:auto; }
.type-tabs .active { background:#e9f2ff; color:#2567ba; border-color:#a9caef; }
input,select,textarea { border:1px solid var(--border-color,#d8e1ed); background:var(--bg-primary,#fff); color:inherit; border-radius:6px; padding:9px 11px; box-sizing:border-box; font:inherit; min-width:0; }
input:focus,select:focus,textarea:focus { outline:2px solid #b8d6fc; outline-offset:1px; }
.toolbar label { display:flex; gap:6px; align-items:center; font-size:13px; }
.table-scroll { overflow:auto; border:1px solid var(--border-color,#e0e6ef); border-radius:9px; }
table { width:100%; border-collapse:collapse; font-size:13px; }
th { background:var(--bg-secondary,#f7f9fc); color:var(--text-secondary,#6b7d92); text-align:left; font-weight:500; white-space:nowrap; }
td,th { padding:14px 13px; border-bottom:1px solid var(--border-color,#edf0f5); }
td small,.test-result small { display:block; margin-top:5px; }
.target { max-width:235px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.actions { flex-wrap:wrap; max-width:235px; gap:5px; }.actions button { padding:4px 7px; font-size:12px; }
.badge { border-radius:20px; padding:4px 10px; background:#edf1f6; color:#64758b; white-space:nowrap; }
.badge.up { background:#e7f7ee; color:#23865d; }.badge.failed { background:#fff0ee; color:#c65044; }.badge.stale { background:#fff4db; color:#9b721f; }
.driver-warning { color:#9b721f; }
.empty { text-align:center; padding:55px 16px; }.empty h3 { margin:12px 0 8px; }
.pagination { justify-content:flex-end; align-items:center; margin-top:15px; font-size:13px; }.pagination span { margin-right:auto; }
.overlay { position:fixed; inset:0; z-index:1100; background:rgba(17,31,50,.4); display:flex; justify-content:center; align-items:center; padding:24px; }
.dialog { background:var(--bg-primary,#fff); border-radius:13px; padding:25px; width:850px; max-width:100%; max-height:90vh; overflow:auto; box-shadow:0 18px 70px #102e4a35; }
.dialog table { min-width:0; }
.dialog header { margin-bottom:18px; }.dialog header p { margin-top:5px; }.dialog footer { margin-top:20px; align-items:center; justify-content:flex-end; }
fieldset { border:0; padding:0; margin:0; }.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
.form-grid label { display:flex; flex-direction:column; gap:7px; font-size:13px; text-align:left; align-items:stretch; }.form-grid .check-label { flex-direction:row; align-items:center; }
.wide { grid-column:1/-1; }.hint { margin:10px 0; font-size:12px; }.form-grid .hint { margin:0; }
.warning,.error,.success,.test-result { padding:11px 15px; border-radius:7px; margin:12px 0; font-size:13px; }
.warning { background:#fff5df; color:#916b23; }.error { background:#fff0ed; color:#b63e35; }.success { background:#eaf7ee; color:#267646; }.test-result { background:var(--bg-secondary,#f2f6fc); border:1px solid var(--border-color,#dce5f0); }
.compact { width:460px; }.compact p { margin-top:12px; }
@media(max-width:800px) { .external-sources { padding:14px; }.source-header,.scope-note { align-items:flex-start; flex-direction:column; }.form-grid { grid-template-columns:1fr; }.dialog { padding:18px; }.overlay { padding:12px; } }
</style>

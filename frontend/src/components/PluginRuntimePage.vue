<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { ApiError, proxyPluginRequest, type PluginRuntimeManifestInfo } from '../api'
import UiIcon from './UiIcon.vue'
import { t, uiLocale } from '../i18n'

const props = defineProps<{
  token: string
  plugin: PluginRuntimeManifestInfo | null
}>()

const frameRef = ref<HTMLIFrameElement | null>(null)
const allowedMethods = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE'])
const maxBridgeBodyBytes = 1024 * 1024
let themeObserver: MutationObserver | null = null
let lastSentTheme = ''
let active = true
let contextVersion = 0
const frameGeneration = ref(0)
const pendingRequests = new Set<string>()

const frontendEntry = computed(() => {
  const value = props.plugin?.manifest?.frontendEntry
  if (typeof value !== 'string' || !value.trim()) return ''
  try {
    const url = new URL(value.trim(), window.location.href)
    // Same-origin scripts can access the parent and defeat this iframe sandbox.
    if (!['http:', 'https:'].includes(url.protocol) || url.origin === window.location.origin) return ''
    return url.href
  } catch {
    return ''
  }
})
const invalidFrontendEntry = computed(() => Boolean(props.plugin?.manifest?.frontendEntry) && !frontendEntry.value)

const targetOrigin = computed(() => {
  if (!frontendEntry.value) return ''
  try {
    return new URL(frontendEntry.value, window.location.href).origin
  } catch {
    return ''
  }
})

function currentTheme() {
  return document.documentElement.classList.contains('dark') || document.querySelector('.admin-shell.dark-theme')
    ? 'dark'
    : 'light'
}

function sendContextToPlugin() {
  const plugin = props.plugin
  if (!active || !props.token || !plugin || !frameRef.value?.contentWindow || !targetOrigin.value) return
  const theme = currentTheme()
  frameRef.value.contentWindow.postMessage({
    type: 'platform-context',
    pluginKey: plugin.pluginKey,
    theme,
    locale: uiLocale.value,
    context: { locale: uiLocale.value }
  }, targetOrigin.value)
  lastSentTheme = theme
}

function syncThemeToPlugin() {
  if (currentTheme() !== lastSentTheme) sendContextToPlugin()
}

function safePluginPath(path: unknown, pluginKey: string) {
  if (typeof path !== 'string' || path.length > 1000) return ''
  const pathname = path.split('?', 1)[0]
  const prefix = `/api/plugin/${pluginKey}`
  const lower = path.toLowerCase()
  if (!(pathname === prefix || pathname.startsWith(`${prefix}/`))) return ''
  if (path.startsWith('//') || path.includes('\\') || path.includes('..')
    || lower.includes('%2e') || lower.includes('%2f') || lower.includes('%5c') || lower.includes('://')) return ''
  return path
}

async function handlePluginMessage(event: MessageEvent) {
  const plugin = props.plugin
  const frame = frameRef.value
  const sender = frame?.contentWindow
  const origin = targetOrigin.value
  const token = props.token
  const version = contextVersion
  if (!active || !token || !plugin || !frame || !sender || !origin) return
  if (event.source !== sender || event.origin !== origin) return
  const current = () => active && props.token === token && contextVersion === version && props.plugin?.pluginKey === plugin.pluginKey
    && props.plugin?.version === plugin.version && frameRef.value === frame && frame.contentWindow === sender && targetOrigin.value === origin
  const data = event.data
  if (!data || typeof data !== 'object' || data.pluginKey !== plugin.pluginKey) return
  if (data.type === 'plugin-ready') {
    sendContextToPlugin()
    return
  }
  if (data.type !== 'plugin-api-request') return

  const requestId = typeof data.requestId === 'string' ? data.requestId : ''
  const respond = (payload: Record<string, unknown>) => {
    if (!current()) return
    sender.postMessage({ type: 'plugin-api-response', pluginKey: plugin.pluginKey, requestId, ...payload }, origin)
  }
  const method = typeof data.method === 'string' ? data.method.toUpperCase() : 'GET'
  const path = safePluginPath(data.path, plugin.pluginKey)
  if (!requestId || requestId.length > 128 || !allowedMethods.has(method) || !path) {
    if (requestId) respond({ ok: false, status: 400, body: { message: t('插件请求参数不合法') } })
    return
  }
  const requestKey = `${version}:${requestId}`
  if (pendingRequests.has(requestKey)) return
  if (pendingRequests.size >= 32) { respond({ ok: false, status: 429, body: { message: t('插件请求过多，请稍后重试') } }); return }
  pendingRequests.add(requestKey)

  try {
    if (data.body != null && new Blob([JSON.stringify(data.body)]).size > maxBridgeBodyBytes) {
      throw new ApiError('插件请求内容过大', 413)
    }
    const body = await proxyPluginRequest(token, plugin.pluginKey, path, method as 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE', data.body)
    respond({ ok: true, status: 200, body })
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 500
    const message = error instanceof Error ? error.message : '插件请求失败'
    respond({ ok: false, status, body: { message: t(message) } })
  } finally {
    pendingRequests.delete(requestKey)
  }
}
watch(uiLocale, sendContextToPlugin)
watch([() => props.token, () => props.plugin?.pluginKey, () => props.plugin?.version, frontendEntry], () => {
  contextVersion++
  pendingRequests.clear()
  frameGeneration.value++
  lastSentTheme = ''
}, { flush: 'sync' })
onDeactivated(() => { active = false; contextVersion++; pendingRequests.clear() })
onActivated(() => { active = true; sendContextToPlugin() })

onMounted(() => {
  window.addEventListener('message', handlePluginMessage)
  themeObserver = new MutationObserver(syncThemeToPlugin)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  if (document.body) themeObserver.observe(document.body, { attributes: true, subtree: true, attributeFilter: ['class'] })
})
onBeforeUnmount(() => {
  active = false
  contextVersion++
  pendingRequests.clear()
  window.removeEventListener('message', handlePluginMessage)
  themeObserver?.disconnect()
  themeObserver = null
})
</script>

<template>
  <section class="content plugin-runtime-page">
    <div v-if="plugin" class="plugin-runtime-header">
      <div>
        <div class="plugin-runtime-title"><UiIcon name="layers" /><h2>{{ plugin.name }}</h2><span>v{{ plugin.version }}</span></div>
      </div>
    </div>

    <div v-if="plugin && frontendEntry" class="plugin-runtime-frame-wrap">
      <iframe :key="frameGeneration" ref="frameRef" class="plugin-runtime-frame" :src="frontendEntry" :title="plugin.name" sandbox="allow-forms allow-modals allow-popups allow-same-origin allow-scripts" referrerpolicy="no-referrer" @load="sendContextToPlugin"></iframe>
    </div>
    <div v-else class="plugin-runtime-empty">
      <UiIcon name="activity" :size="30" />
      <strong>{{ invalidFrontendEntry ? '插件页面地址不安全' : '插件页面尚未接入' }}</strong>
      <span v-if="invalidFrontendEntry">插件必须使用与管理端不同源的 HTTP(S) 地址，以保持运行隔离。请在插件中心修改页面地址。</span>
      <span v-else>当前插件已经进入运行清单，但还没有配置 frontendEntry。请在插件中心登记页面地址后重新打开。</span>
    </div>
  </section>
</template>

<style scoped>
.plugin-runtime-page { display: flex; flex-direction: column; gap: 16px; min-height: calc(100vh - 150px); }
.plugin-runtime-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 4px 2px; }
.plugin-runtime-title { display: flex; align-items: center; gap: 9px; }.plugin-runtime-title .ui-icon { color: #148b6c; }.plugin-runtime-title h2 { margin: 0; color: #20302c; font-size: 20px; }.plugin-runtime-title span { padding: 3px 7px; border-radius: 4px; color: #6e7e77; background: #f0f5f2; font: 11px Consolas, monospace; }.plugin-runtime-header p { margin: 7px 0 0; color: #82908a; font-size: 12px; }
.plugin-runtime-frame-wrap { flex: 1; min-height: 620px; overflow: hidden; border: 1px solid #dfe9e4; border-radius: 7px; background: #fff; }.plugin-runtime-frame { width: 100%; height: 100%; min-height: 620px; display: block; border: 0; }
.plugin-runtime-empty { min-height: 380px; display: grid; place-items: center; align-content: center; gap: 9px; padding: 30px; border: 1px dashed #ccdcd5; border-radius: 7px; color: #879690; background: #fbfdfc; text-align: center; }.plugin-runtime-empty .ui-icon { color: #16a780; }.plugin-runtime-empty strong { color: #50615b; font-size: 15px; }.plugin-runtime-empty span { max-width: 430px; font-size: 12px; line-height: 1.6; }
:root.dark .plugin-runtime-title h2 { color: #e0e8ec; }:root.dark .plugin-runtime-title span { color: #b9c8ce; background: #253039; }:root.dark .plugin-runtime-header p { color: #91a0a8; }:root.dark .plugin-runtime-frame-wrap { border-color: #34414b; background: #171d23; }:root.dark .plugin-runtime-empty { color: #91a0a8; background: #171d23; border-color: #3c4a53; }:root.dark .plugin-runtime-empty strong { color: #d3dfe4; }
</style>

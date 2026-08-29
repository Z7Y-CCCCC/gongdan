<script setup lang="ts">
import { computed } from 'vue'
import type { PluginRuntimeManifestInfo } from '../api'
import UiIcon from './UiIcon.vue'

const props = defineProps<{
  token: string
  plugin: PluginRuntimeManifestInfo | null
}>()

const frontendEntry = computed(() => {
  const value = props.plugin?.manifest?.frontendEntry
  return typeof value === 'string' ? value.trim() : ''
})

const backendUrl = computed(() => {
  const value = props.plugin?.manifest?.backendUrl
  return typeof value === 'string' ? value.trim() : ''
})

function sendAuthToPlugin(event: Event) {
  const frame = event.currentTarget as HTMLIFrameElement | null
  if (!frame?.contentWindow || !frontendEntry.value) return
  try {
    frame.contentWindow.postMessage(
      { type: 'platform-auth', token: props.token, pluginKey: props.plugin?.pluginKey },
      new URL(frontendEntry.value, window.location.href).origin
    )
  } catch {
    // Invalid entry URLs are reported by the empty state or the browser frame itself.
  }
}
</script>

<template>
  <section class="content plugin-runtime-page">
    <div v-if="plugin" class="plugin-runtime-header">
      <div>
        <div class="plugin-runtime-title"><UiIcon name="layers" /><h2>{{ plugin.name }}</h2><span>v{{ plugin.version }}</span></div>
        <p>这是独立扩展页面。插件发生异常时，主系统可以单独下线它。</p>
      </div>
      <a v-if="backendUrl" class="plugin-runtime-service" :href="backendUrl" target="_blank" rel="noreferrer">服务地址</a>
    </div>

    <div v-if="plugin && frontendEntry" class="plugin-runtime-frame-wrap">
      <iframe class="plugin-runtime-frame" :src="frontendEntry" :title="plugin.name" sandbox="allow-forms allow-modals allow-popups allow-same-origin allow-scripts" referrerpolicy="same-origin" @load="sendAuthToPlugin"></iframe>
    </div>
    <div v-else class="plugin-runtime-empty">
      <UiIcon name="activity" :size="30" />
      <strong>插件页面尚未接入</strong>
      <span>当前插件已经进入运行清单，但还没有配置 frontendEntry。请在插件中心登记页面地址后重新打开。</span>
    </div>
  </section>
</template>

<style scoped>
.plugin-runtime-page { display: flex; flex-direction: column; gap: 16px; min-height: calc(100vh - 150px); }
.plugin-runtime-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 4px 2px; }
.plugin-runtime-title { display: flex; align-items: center; gap: 9px; }.plugin-runtime-title .ui-icon { color: #148b6c; }.plugin-runtime-title h2 { margin: 0; color: #20302c; font-size: 20px; }.plugin-runtime-title span { padding: 3px 7px; border-radius: 4px; color: #6e7e77; background: #f0f5f2; font: 11px Consolas, monospace; }.plugin-runtime-header p { margin: 7px 0 0; color: #82908a; font-size: 12px; }
.plugin-runtime-service { color: #1389e8; font-size: 12px; text-decoration: none; }.plugin-runtime-service:hover { text-decoration: underline; }
.plugin-runtime-frame-wrap { flex: 1; min-height: 620px; overflow: hidden; border: 1px solid #dfe9e4; border-radius: 7px; background: #fff; }.plugin-runtime-frame { width: 100%; height: 100%; min-height: 620px; display: block; border: 0; }
.plugin-runtime-empty { min-height: 380px; display: grid; place-items: center; align-content: center; gap: 9px; padding: 30px; border: 1px dashed #ccdcd5; border-radius: 7px; color: #879690; background: #fbfdfc; text-align: center; }.plugin-runtime-empty .ui-icon { color: #16a780; }.plugin-runtime-empty strong { color: #50615b; font-size: 15px; }.plugin-runtime-empty span { max-width: 430px; font-size: 12px; line-height: 1.6; }
:root.dark .plugin-runtime-title h2 { color: #e0e8ec; }:root.dark .plugin-runtime-title span { color: #b9c8ce; background: #253039; }:root.dark .plugin-runtime-header p { color: #91a0a8; }:root.dark .plugin-runtime-frame-wrap { border-color: #34414b; background: #171d23; }:root.dark .plugin-runtime-empty { color: #91a0a8; background: #171d23; border-color: #3c4a53; }:root.dark .plugin-runtime-empty strong { color: #d3dfe4; }
</style>

<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { ApiError, establishUReportSession } from '../api'
import { useLatestRequest } from '../composables/useLatestRequest'
import { useRefreshOnActivated } from '../composables/useRefreshOnActivated'
import { t, uiLocale, type UiLocale } from '../i18n'
import { currentReportPath, reportHasUnsavedChanges, reportLocaleUrl } from '../utils/reportLocale'

const props = defineProps<{ token: string }>()

const iframeSrc = ref('')
const frameRef = ref<HTMLIFrameElement | null>(null)
const frameLocale = ref<UiLocale>(uiLocale.value)
const languagePending = computed(() => Boolean(iframeSrc.value) && !frameLoading.value && frameLocale.value !== uiLocale.value)
const loading = ref(true)
const frameLoading = ref(false)
const errorMessage = ref('')
let disposed = false
let active = true
const beginLoad = useLatestRequest()
let frameTimeout: ReturnType<typeof setTimeout> | undefined
function clearFrameTimeout() { clearTimeout(frameTimeout); frameTimeout = undefined }

async function load(reportPath = '') {
  const isCurrent = beginLoad()
  const requestToken = props.token
  clearFrameTimeout()
  loading.value = true
  frameLoading.value = false
  iframeSrc.value = ''
  errorMessage.value = ''
  if (!requestToken || !active) { loading.value = false; return }
  const current = () => !disposed && active && isCurrent() && props.token === requestToken
  try {
    const session = await establishUReportSession(requestToken)
    if (!current()) return
    frameLocale.value = uiLocale.value
    iframeSrc.value = reportLocaleUrl(reportPath || session.path, frameLocale.value, Date.now())
    frameLoading.value = true
    frameTimeout = setTimeout(() => {
      if (current() && frameLoading.value) onFrameError()
    }, 20000)
  } catch (error) {
    if (!current()) return
    errorMessage.value = error instanceof ApiError ? error.message : '报表设计器启动失败，请稍后重试'
  } finally {
    if (current()) loading.value = false
  }
}

function requestReload() {
  if (loading.value || frameLoading.value) return
  if (iframeSrc.value && reportHasUnsavedChanges(frameRef.value)
    && !window.confirm(t('重新加载将丢弃未保存的报表修改，是否继续？'))) return
  void load(currentReportPath(frameRef.value, iframeSrc.value))
}

function onFrameLoaded() {
  clearFrameTimeout()
  frameLoading.value = false
  try {
    const nativeLocale = frameRef.value?.contentDocument?.documentElement.getAttribute('data-workorder-report-locale')
    if (nativeLocale === 'en-US' || nativeLocale === 'zh-CN') frameLocale.value = nativeLocale
  } catch {}
}
function onFrameError() {
  clearFrameTimeout()
  frameLoading.value = false
  iframeSrc.value = ''
  errorMessage.value = '报表设计器加载失败，请重试'
}

onMounted(load)
onActivated(() => { active = true })
useRefreshOnActivated(load)
onDeactivated(() => { active = false; beginLoad(); clearFrameTimeout(); iframeSrc.value = ''; frameLoading.value = false })
watch(() => props.token, () => { beginLoad(); clearFrameTimeout(); iframeSrc.value = ''; void load() })
onBeforeUnmount(() => {
  disposed = true
  beginLoad()
  clearFrameTimeout()
  iframeSrc.value = ''
})
</script>

<template>
  <section class="content ureport-page">
    <div class="ureport-header">
      <div>
        <h2>高级报表</h2>
      </div>
      <div class="ureport-actions">
        <button v-if="languagePending" class="ghost-btn" type="button" @click="requestReload">应用语言</button>
        <button class="ghost-btn ui-icon-button" type="button" :disabled="loading || frameLoading" @click="requestReload" title="刷新" aria-label="刷新"><UiIcon name="refresh" /></button>
      </div>
    </div>

    <div v-if="loading" class="ureport-state">
      <strong>正在启动报表设计器</strong>
    </div>

    <div v-else-if="errorMessage" class="ureport-state ureport-state-error">
      <strong>{{ errorMessage }}</strong>
      <span>请确认当前账号具有管理员权限，或联系系统管理员。</span>
      <button class="primary-btn ui-icon-button" type="button" @click="load()" title="重新加载" aria-label="重新加载"><UiIcon name="refresh" /></button>
    </div>

    <iframe
      v-else-if="iframeSrc"
      :key="iframeSrc"
      ref="frameRef"
      class="ureport-frame"
      :src="iframeSrc"
      title="UReport2 高级报表"
      @load="onFrameLoaded"
      @error="onFrameError"
    ></iframe>
  </section>
</template>

<style scoped>
.ureport-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 144px);
}

.ureport-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.ureport-header h2 {
  margin: 0 0 6px;
  color: #1f2d2a;
  font-size: 20px;
}

.ureport-actions { display: flex; align-items: center; gap: 8px; }

.ureport-header p {
  margin: 0;
  color: #64716d;
  font-size: 13px;
}

.ureport-state {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  padding: 32px;
  border: 1px dashed #cbdad4;
  border-radius: 12px;
  background: #fbfdfc;
  color: #64716d;
  text-align: center;
}

.ureport-state strong {
  color: #1f2d2a;
  font-size: 18px;
}

.ureport-state-error {
  border-color: #f0c5c5;
  background: #fffafa;
}

.ureport-state-error strong {
  color: #a53c3c;
}

.ureport-frame {
  flex: 1;
  width: 100%;
  min-height: 760px;
  border: 1px solid #dfe9e4;
  border-radius: 12px;
  background: #fff;
}

:global(.dark) .ureport-header h2,
:global(.dark) .ureport-state strong {
  color: #e6f2ed;
}

:global(.dark) .ureport-header p,
:global(.dark) .ureport-state {
  color: #96a5ad;
}

:global(.dark) .ureport-state {
  border-color: #344641;
  background: #15201e;
}

:global(.dark) .ureport-state-error {
  border-color: #6c3e3e;
  background: #251919;
}

:global(.dark) .ureport-frame {
  border-color: #344641;
  background: #101917;
}
</style>

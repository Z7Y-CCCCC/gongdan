<template>
  <view v-if="modelValue" class="scan-mask" @click.self="close">
    <view ref="dialog" class="scan-dialog" role="dialog" aria-modal="true" aria-label="扫码工单" @keydown="onKeydown">
      <view class="scan-head"><text>扫码工单</text><IconButton icon="closeempty" label="关闭扫码" @click="close" /></view>
      <view class="scan-choices"><button :disabled="busy" @click="choose('camera')"><uni-icons type="camera" size="20" aria-hidden="true" />拍照</button><button :disabled="busy" @click="choose('album')"><uni-icons type="image" size="20" aria-hidden="true" />相册</button></view>
      <view class="scan-field"><text>工单号 / 二维码内容</text><input v-model="input" aria-label="工单号或二维码内容" maxlength="2048" placeholder="输入或粘贴" :disabled="busy" @input="fromImage = false" @confirm="open" /></view>
      <text v-if="error" class="scan-error" role="alert">{{ error }}</text>
      <text v-if="busy" class="scan-state" role="status">识别中…</text>
      <button class="scan-open" :disabled="busy || !input.trim()" @click="open">打开工单</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import IconButton from './IconButton.vue'
import { scannedWorkOrderRoute } from '../utils/scan-target'
import { QR_MAX_FILE_BYTES, readQrImage } from '../utils/qr-image'
import { navigatePage } from '../utils/navigation'
import { ensureLogin } from '../services/api'
import { mobileSessionKey } from '../utils/session-key'
import { useMobilePageActivity } from '../composables/useMobilePageActivity'

const pageActivity = useMobilePageActivity()

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const input = ref(''), error = ref(''), busy = ref(false), dialog = ref<any>(null)
const fromImage = ref(false)
let generation = 0
let returnFocus: HTMLElement | null = null
const element = () => dialog.value?.$el || dialog.value
function close() { generation++; busy.value = false; emit('update:modelValue', false) }
watch(() => props.modelValue, async shown => {
  const current = ++generation
  busy.value = false
  if (!shown) { if (returnFocus?.isConnected) returnFocus.focus(); return }
  error.value = ''; input.value = ''; fromImage.value = false
  returnFocus = document.activeElement as HTMLElement | null
  await nextTick()
  if (props.modelValue && generation === current) element()?.querySelector('button')?.focus()
}, { flush: 'post' })
onBeforeUnmount(() => { generation++ })
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); close(); return }
  if (event.key !== 'Tab') return
  const controls = [...element()?.querySelectorAll('button:not([disabled]), input:not([disabled])') || []] as HTMLElement[]
  const first = controls[0], last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
function open() {
  if (busy.value || !ensureLogin()) return
  try { const route = scannedWorkOrderRoute(input.value, !fromImage.value); close(); navigatePage(route) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '二维码内容无效' }
}
function choose(source: 'camera' | 'album') {
  if (busy.value || !ensureLogin()) return
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture(true)
  const current = ++generation
  const valid = () => isPageCurrent() && generation === current && props.modelValue && session === mobileSessionKey()
  busy.value = true; error.value = ''
  uni.chooseImage({ count: 1, sourceType: [source], success: async result => {
    try {
      if (!valid()) return
      const file = Array.isArray(result.tempFiles) ? result.tempFiles[0] : result.tempFiles
      if (file && file.size > QR_MAX_FILE_BYTES) throw new Error('图片不能超过10MB')
      const value = await readQrImage(result.tempFilePaths[0])
      if (!valid()) return
      input.value = value
      fromImage.value = true
      busy.value = false
      open()
    } catch (cause) { if (valid()) error.value = cause instanceof Error ? cause.message : '二维码识别失败' }
    finally { if (generation === current) busy.value = false }
  }, fail: failure => { if (generation === current) { busy.value = false; if (valid() && !failure.errMsg?.includes('cancel')) error.value = '选择图片失败，请重试' } } })
}
</script>

<style scoped>
.scan-mask { position: fixed; inset: 0; z-index: 120; background: rgba(18,35,29,.42); display: flex; align-items: center; justify-content: center; padding: 24rpx; }
.scan-dialog { width: 100%; max-width: 460px; padding: 28rpx; border-radius: 20rpx; background: var(--mobile-surface, white); }
.scan-head { display: flex; align-items: center; justify-content: space-between; font-size: 32rpx; font-weight: 650; }
.scan-choices { display: flex; gap: 20rpx; margin: 24rpx 0; }
.scan-choices button { flex: 1; margin: 0; border-radius: 12rpx; background: var(--mobile-subtle, #e6f7f3); color: var(--mobile-link, #007c63); display: flex; justify-content: center; align-items: center; gap: 12rpx; min-height: 44px; font-size: 28rpx; }
.scan-field { display: flex; flex-direction: column; gap: 14rpx; color: var(--mobile-muted, #536b60); font-size: 26rpx; }
.scan-field input { height: 88rpx; min-height: 44px; border: 1px solid var(--mobile-border, #cadbd4); border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; }
.scan-open { display: flex; align-items: center; justify-content: center; width: 100%; margin: 24rpx 0 0; padding: 0 20rpx; min-height: 44px; border-radius: 12rpx; background: #007c63; color: white; font-size: 28rpx; }
.scan-error, .scan-state { display: block; margin-top: 16rpx; font-size: 26rpx; }
.scan-error { color: var(--mobile-error, #b53b3b); }
button[disabled] { opacity: .48; }
button::after { border: 0; }
</style>

<template>
  <view v-if="images.length" class="report-images">
    <button class="photo-button" aria-label="查看报工照片" :disabled="busy" :aria-busy="busy" @click="preview"><uni-icons type="image" size="18" aria-hidden="true" /><text>照片 {{ images.length }}</text></button>
    <text v-if="error" class="photo-error" role="alert">{{ error }}</text>
  </view>
  <!-- #ifdef H5 -->
  <Teleport to="body">
    <dialog v-if="showPreview" ref="previewDialog" class="photo-preview" role="dialog" aria-modal="true" aria-label="报工照片预览" @keydown="trapPreview" @cancel.prevent="showPreview = false">
      <view class="photo-toolbar"><text>{{ currentIndex + 1 }} / {{ previewUrls.length }}</text><IconButton icon="closeempty" label="关闭照片预览" @click="showPreview = false" /></view>
      <img :src="previewUrls[currentIndex]" :alt="'报工照片 ' + (currentIndex + 1)" class="preview-image" @error="error = '照片读取失败，请关闭后重试'" />
      <text v-if="error" class="photo-error" role="alert">{{ error }}</text>
      <view class="photo-navigation"><IconButton icon="back" label="上一张照片" :disabled="currentIndex === 0" @click="currentIndex--" /><IconButton icon="forward" label="下一张照片" :disabled="currentIndex >= previewUrls.length - 1" @click="currentIndex++" /></view>
    </dialog>
  </Teleport>
  <!-- #endif -->
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount, nextTick, watch } from 'vue'
import IconButton from './IconButton.vue'
import type { ReportImage } from '../types/api'
import { downloadReportImage, ensureLogin } from '../services/api'
import { mobileSessionKey } from '../utils/session-key'
import { useMobilePageActivity } from '../composables/useMobilePageActivity'
import { claimPhotoPreview } from '../utils/photo-preview-owner'

const props = defineProps<{ images: ReportImage[]; active?: boolean }>()
const pageActivity = useMobilePageActivity()
const busy = ref(false)
const error = ref('')
let alive = true
let generation = 0
const showPreview = ref(false), previewUrls = ref<string[]>([]), currentIndex = ref(0), previewDialog = ref<any>(null)
let returnFocus: HTMLElement | null = null
let restoreFocusAllowed = () => false
let releaseOwner = () => {}
watch(showPreview, async visible => {
  if (typeof document === 'undefined') return
  if (visible) {
    await nextTick()
    const dialog = previewDialog.value?.$el || previewDialog.value
    if (showPreview.value) {
      if (typeof dialog?.showModal === 'function') dialog.showModal()
      else dialog?.setAttribute('open', '')
      dialog?.querySelector('button')?.focus()
    }
  } else {
    await nextTick()
    releaseUrls()
    previewUrls.value = []
    releaseOwner()
    if (restoreFocusAllowed() && returnFocus?.isConnected) returnFocus.focus()
  }
}, { flush: 'post' })
function trapPreview(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); showPreview.value = false; return }
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault()
    currentIndex.value = Math.max(0, Math.min(previewUrls.value.length - 1, currentIndex.value + (event.key === 'ArrowRight' ? 1 : -1)))
    return
  }
  if (event.key !== 'Tab') return
  const dialog = previewDialog.value?.$el || previewDialog.value
  const buttons = Array.from(dialog?.querySelectorAll('button:not([disabled])') || []) as HTMLElement[]
  const first = buttons[0], last = buttons[buttons.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
const temporaryUrls = new Set<string>()
function releaseUrls() {
  if (typeof URL !== 'undefined') temporaryUrls.forEach(url => { if (url.startsWith('blob:')) URL.revokeObjectURL(url) })
  temporaryUrls.clear()
}
function invalidatePreview() {
  generation++
  busy.value = false
  showPreview.value = false
  previewUrls.value = []
  releaseUrls()
  releaseOwner()
}
watch(() => props.active, active => { if (active === false) invalidatePreview() }, { flush: 'sync' })
watch(() => props.images.map(image => image.id).join(','), () => invalidatePreview(), { flush: 'sync' })
const routeChanged = () => invalidatePreview()
if (typeof window !== 'undefined') window.addEventListener('hashchange', routeChanged)
onBeforeUnmount(() => {
  alive = false
  invalidatePreview()
  if (typeof window !== 'undefined') window.removeEventListener('hashchange', routeChanged)
})

async function preview() {
  if (!alive || props.active === false || busy.value || !props.images.length || !ensureLogin()) return
  const session = mobileSessionKey()
  const isPageCurrent = pageActivity.capture()
  releaseOwner = claimPhotoPreview(invalidatePreview)
  const request = ++generation
  let batchFailed = false
  const current = () => !batchFailed && alive && props.active !== false && request === generation && isPageCurrent() && session === mobileSessionKey()
  restoreFocusAllowed = current
  const acquired = new Set<string>()
  const releaseAcquired = () => {
    acquired.forEach(url => {
      temporaryUrls.delete(url)
      if (typeof URL !== 'undefined' && url.startsWith('blob:')) URL.revokeObjectURL(url)
    })
    acquired.clear()
  }
  if (typeof document !== 'undefined') returnFocus = document.activeElement as HTMLElement | null
  busy.value = true
  error.value = ''
  releaseUrls()
  try {
    const images = props.images.slice(0, 9)
    if (images.some(image => !Number.isSafeInteger(image.id) || image.id <= 0)) throw new Error('照片记录无效')
    const urls = await Promise.all(images.map(async image => {
      const url = await downloadReportImage(image.id)
      if (!current()) {
        if (typeof URL !== 'undefined' && url.startsWith('blob:')) URL.revokeObjectURL(url)
      } else { temporaryUrls.add(url); acquired.add(url) }
      return url
    }))
    if (!current()) { releaseAcquired(); return }
    // #ifdef H5
    if (typeof window !== 'undefined') {
      previewUrls.value = urls
      currentIndex.value = 0
      showPreview.value = true
      return
    }
    // #endif
    uni.previewImage({ urls, current: urls[0], fail: () => { if (current()) error.value = '照片打开失败，请重试' } })
  } catch (cause) {
    const wasCurrent = current()
    // Promise.all stops at the first failure, not when every download settles.
    // Late successes from this batch must revoke their URLs instead of keeping
    // files after the failed preview; a new retry keeps its independent owner.
    batchFailed = true
    releaseAcquired()
    if (wasCurrent) { releaseOwner(); error.value = cause instanceof Error ? cause.message : '照片加载失败，请重试' }
  } finally {
    if (alive && request === generation) {
      busy.value = false
      if (!current()) releaseOwner()
    }
  }
}
</script>

<style scoped>
.report-images { display: flex; align-items: center; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
.photo-button { display: inline-flex; align-items: center; gap: 10rpx; min-height: 44px; margin: 0; padding: 0 20rpx; background: var(--mobile-subtle, #e6f7f3); color: var(--mobile-link, #007c63); font-size: 26rpx; border-radius: 12rpx; }
.photo-button::after { border: 0; }
.photo-button[disabled] { opacity: .48; }
.photo-error { color: var(--mobile-error, #b53b3b); font-size: 24rpx; }
.photo-preview { position: fixed; inset: 0; z-index: 300; display: flex; flex-direction: column; align-items: center; width: 100%; height: 100%; max-width: none; max-height: none; margin: 0; border: 0; padding: 24rpx; background: #13211d; color: white; }
.photo-preview::backdrop { background: rgba(18,35,29,.6); }
.photo-toolbar { width: 100%; display: flex; align-items: center; justify-content: space-between; font-size: 28rpx; }
.preview-image { display: block; min-height: 0; flex: 1; width: 100%; object-fit: contain; padding: 24rpx 0; }
.photo-navigation { display: flex; justify-content: center; gap: 32rpx; }
.photo-preview .photo-error { color: var(--mobile-error, #ffc0b6); }
</style>

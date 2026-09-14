<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { ensureLogin } from './services/api'

onLaunch(() => {
  ensureLogin()
})
</script>

<style>
:root {
  --motion-fast: 140ms;
  --motion-base: 220ms;
  --motion-ease: cubic-bezier(0.2, 0.8, 0.2, 1);
}

html,
body,
#app {
  width: 100%;
  min-height: 100%;
  margin: 0;
  background: #f3f5f5;
  overflow-x: hidden;
}

page {
  min-height: 100%;
  background: #f3f5f5;
  color: #202926;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}

view,
scroll-view,
swiper,
swiper-item {
  display: block;
  box-sizing: border-box;
}

text {
  box-sizing: border-box;
}

input,
textarea,
button {
  box-sizing: border-box;
  font: inherit;
}

button {
  border: 0;
  padding: 0;
  transition: transform var(--motion-fast) var(--motion-ease), opacity var(--motion-fast) ease,
    background-color var(--motion-base) ease, color var(--motion-base) ease,
    border-color var(--motion-base) ease, box-shadow var(--motion-base) ease;
}

button::after {
  border: 0;
}

.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  background: #fff;
  border-radius: 14rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.04);
}

.green {
  color: #00a982;
}

.primary {
  background: #00b889;
  color: #fff;
}

.ghost {
  background: #fff;
  color: #00a982;
  border: 1rpx solid #9edbd0;
}

.muted {
  color: #8a9693;
}

.page,
.container {
  animation: mobile-page-in var(--motion-base) var(--motion-ease) both;
}

.card,
.summary-card,
.form-card,
.event-card,
.order-card,
.task-card,
.report-card {
  transition: transform var(--motion-fast) var(--motion-ease), box-shadow var(--motion-base) ease,
    border-color var(--motion-base) ease, background-color var(--motion-base) ease;
}

.btn,
.btn-submit,
.submit-btn,
.tab-item,
.type-pill,
.form-item,
.picker-item,
.scan-fab,
.icon-button,
.search-icon {
  transition: transform var(--motion-fast) var(--motion-ease), opacity var(--motion-fast) ease,
    background-color var(--motion-base) ease, color var(--motion-base) ease,
    border-color var(--motion-base) ease, box-shadow var(--motion-base) ease;
}

button:active,
.btn:active,
.btn-submit:active,
.submit-btn:active,
.tab-item:active,
.type-pill:active,
.form-item:active,
.picker-item:active,
.scan-fab:active,
.icon-button:active,
.search-icon:active {
  transform: scale(0.98);
}

.picker-mask,
.uni-mask {
  animation: mobile-mask-in var(--motion-base) ease both;
}

.picker-content,
.uni-popup__wrapper {
  animation: mobile-sheet-in var(--motion-base) var(--motion-ease) both;
}

@keyframes mobile-page-in {
  from { opacity: 0; transform: translateY(10rpx); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes mobile-mask-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes mobile-sheet-in {
  from { opacity: 0; transform: translateY(24rpx); }
  to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 1ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 1ms !important;
  }
}
</style>

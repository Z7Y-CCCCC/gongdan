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
  --motion-change: 180ms;
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
  --motion-fast: 140ms;
  --motion-base: 220ms;
  --motion-change: 180ms;
  --motion-ease: cubic-bezier(0.2, 0.8, 0.2, 1);
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
input { min-height: 44px; }
input, textarea { border: 1rpx solid #dce6e1; border-radius: 16rpx; background: #f7faf8; color: #202926; }

button {
  border: 0;
  padding: 0;
  margin: 0;
  min-height: 44px;
  min-width: 44px;
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
  color: #00856b;
}

.primary {
  background: #00856b;
  color: #fff;
}

.ghost {
  background: #fff;
  color: #00856b;
  border: 1rpx solid #9edbd0;
}

.muted {
  color: #64756d;
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

@keyframes mobile-mask-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes mobile-sheet-in {
  from { opacity: 0; transform: translateY(16rpx); }
  to { opacity: 1; transform: translateY(0); }
}

.progress-fill,
.rank-fill { transition: width var(--motion-change) var(--motion-ease), background-color var(--motion-fast) ease; }
.tab,
.dropdown { transition: color var(--motion-fast) ease, background-color var(--motion-fast) ease; }
.button-row { display: flex; align-items: center; justify-content: flex-end; gap: 12rpx; }
.ui-page-head { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.ui-meta { font-size: 24rpx; color: #71847e; line-height: 1.5; }
.ui-card-button { text-align: left; line-height: 1.4; }
.order-code { overflow-wrap: anywhere; word-break: break-word; }
.empty .icon-action, .stats-state .icon-action, .list-state .icon-action { margin: 16rpx auto 0; }

/* #ifdef H5 */
.task-list, .order-list, .list-content, .process-list, .picker-list { overflow-y: auto; overscroll-behavior: contain; -webkit-overflow-scrolling: touch; }
.status-tabs { overflow-x: auto; overscroll-behavior-x: contain; -webkit-overflow-scrolling: touch; }
button:focus-visible,
input:focus-visible,
textarea:focus-visible { outline: 2px solid #00a982; outline-offset: 3px; }
scroll-view:focus-visible { outline: 2px solid #00856b; outline-offset: -2px; }
/* #endif */

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 1ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 1ms !important;
    scroll-behavior: auto !important;
  }
  button:active, .btn:active, .tab-item:active, .picker-item:active { transform: none !important; }
}
</style>

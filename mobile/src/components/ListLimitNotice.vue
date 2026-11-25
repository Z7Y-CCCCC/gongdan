<template>
  <view v-if="total > loaded" class="limit-notice" role="status">
    <uni-icons type="info" size="16" color="#805900" aria-hidden="true" />
    <text>{{ label }} {{ loaded }} / {{ total }} · 仅统计已加载记录</text>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ total: number; loaded: number; label?: string }>(), { label: '' })
</script>

<style scoped>
.limit-notice { display: flex; align-items: center; gap: 10rpx; padding: 16rpx 20rpx; margin: 12rpx 0; background: var(--mobile-warning-bg, #fff5dd); color: var(--mobile-warning, #805900); font-size: 24rpx; line-height: 1.5; border-radius: 16rpx; }
</style>

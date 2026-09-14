<template>
  <view v-if="total > loaded" class="limit-notice">
    {{ label }}已加载 {{ loaded }} / {{ total }} 条；列表与统计仅涵盖已加载记录，请缩小查询范围或在管理端查询。
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ total: number; loaded: number; label?: string }>(), { label: '' })
</script>

<style scoped>
.limit-notice { padding: 18rpx 24rpx; margin: 12rpx 0; background: #fff5dd; color: #805900; font-size: 24rpx; line-height: 1.5; border-radius: 10rpx; }
</style>

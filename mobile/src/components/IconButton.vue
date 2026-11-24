<template>
  <button class="icon-action" :class="{ 'icon-action-primary': primary }" :aria-label="label" :title="label"
          :aria-busy="busy" :disabled="disabled || busy" :loading="busy" @click="$emit('click', $event)">
    <uni-icons v-if="!busy" :type="icon" size="22" :color="primary ? '#ffffff' : '#41635a'" aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ label: string; icon: string; disabled?: boolean; busy?: boolean; primary?: boolean }>(), {
  disabled: false, busy: false, primary: false
})
defineEmits<{ click: [event: unknown] }>()
</script>

<style scoped>
.icon-action { width: 88rpx; min-width: 44px; height: 88rpx; min-height: 44px; padding: 0; margin: 0; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: #edf5f2; border-radius: 24rpx; line-height: 1; }
.icon-action-primary { background: #00856b; }
.icon-action[disabled] { opacity: 0.48; }
</style>

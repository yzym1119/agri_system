<script setup lang="ts">
import { largeText, toggleLargeText } from '../../services/preferences';
defineProps<{ title: string }>();
</script>
<template>
  <view class="reading-header">
    <text class="section-title">{{ title }}</text>
    <button class="text-button" hover-class="button-pressed" aria-label="切换文字大小" :aria-pressed="largeText" @tap="toggleLargeText">{{ largeText ? '标准字' : '大字' }}</button>
  </view>
</template>

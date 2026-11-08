<template>
  <view class="ai" :class="'ai--' + name" :style="'width:' + size + 'px;height:' + size + 'px'">
    <view class="s1"></view>
    <view class="s2"></view>
    <view class="s3"></view>
  </view>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 20 });
</script>

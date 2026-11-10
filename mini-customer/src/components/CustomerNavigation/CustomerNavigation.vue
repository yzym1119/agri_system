<script setup lang="ts">
import { largeText } from '../../services/preferences';
defineProps<{ active: 'home' | 'profile' }>();
function open(page: string) { uni.reLaunch({ url: '/pages/' + page + '/index' }); }
</script>
<template>
  <view class="customer-nav" :class="{ 'large-text': largeText }" role="navigation" aria-label="主要页面">
    <view class="nav-inner">
      <button class="nav-button" :class="{ 'nav-active': active === 'home' }" hover-class="button-pressed" :aria-current="active === 'home' ? 'page' : false" @tap="open('home')">首页</button>
      <button class="nav-button" :class="{ 'nav-active': active === 'profile' }" hover-class="button-pressed" :aria-current="active === 'profile' ? 'page' : false" @tap="open('profile')">我的</button>
    </view>
  </view>
</template>

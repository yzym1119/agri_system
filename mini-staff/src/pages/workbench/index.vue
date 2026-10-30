<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { staffAuth } from '../../services/auth';
const name = ref(''), store = ref(''), checking = ref(true), error = ref('');
const canRecover = ref(false),canQueryInventory=ref(false),canReceive=ref(false);
let epoch = 0;
const openDeviceCheck = () => uni.navigateTo({ url: '/pages/device-check/index' });
const openRecovery = () => uni.navigateTo({ url:'/pages/recovery/index' });
const openInventory = () => uni.navigateTo({ url:'/pages/scan/index' });
const openReceiving = () => uni.navigateTo({url:'/pages/receipt/list/index'});
onShow(async () => {
  const current = ++epoch;
  checking.value = true; error.value = ''; name.value = ''; store.value = '';
  canRecover.value = false;canQueryInventory.value=false;canReceive.value=false;
  try {
    await staffAuth.refresh();
    if (epoch !== current) return;
    const actor = staffAuth.current();
    if (!actor) throw Error('UNAUTHORIZED');
    name.value = actor.name; store.value = actor.storeId;
    canRecover.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    canQueryInventory.value=actor.permissions.includes('*')||actor.permissions.includes('inventory:view');
    canReceive.value=actor.permissions.includes('*')||actor.permissions.includes('purchase:inbound');
  } catch {
    if (epoch === current) {
      error.value = '当前身份未能确认，请重新登录。';
      uni.reLaunch({ url: '/pages/login/index' });
    }
  } finally { if (epoch === current) checking.value = false; }
});
onHide(() => { epoch++; });
async function logout() {
  epoch++; checking.value = true; name.value = ''; store.value = '';
  await staffAuth.logout(); uni.reLaunch({ url: '/pages/login/index' });
}
</script>
<template>
  <view class="page">
    <text class="title">门店工作台</text>
    <text v-if="checking" class="intro">正在核对当前员工和门店…</text>
    <template v-else-if="name">
      <text class="name">{{ name }}</text>
      <text class="intro">当前门店编号：{{ store }}</text>
      <view class="notice"><text>按实际到货登记收货，可随时查询本店最新批次与货位。</text></view>
      <button v-if="canReceive" class="primary" @click="openReceiving">采购收货 / 原收货核对</button>
      <button v-if="canQueryInventory" class="primary" @click="openInventory">扫码 / 搜索查货</button>
      <button class="primary" @click="openDeviceCheck">检查手机设备</button>
      <button v-if="canRecover" @click="openRecovery">核对上次收银</button>
      <button @click="logout">退出登录</button>
    </template>
    <text v-if="error" class="intro">{{ error }}</text>
  </view>
</template>
<style scoped>
.page { padding:24px 20px; max-width:600px; margin:auto; }
.title { display:block; font-size:24px; font-weight:600; margin-bottom:20px; }
.name { display:block; font-size:20px; }
.intro { display:block; color:#4b5563; line-height:1.7; margin:12px 0; }
.notice { margin:28px 0; padding:20px; border:1px solid #e5e7eb; border-radius:10px; background:#fff; line-height:1.7; }
button { margin:16px 0; color:#1d5d73; background:#fff; border:1px solid #1d5d73; }
.primary { color:#fff; background:#1d5d73; }
</style>

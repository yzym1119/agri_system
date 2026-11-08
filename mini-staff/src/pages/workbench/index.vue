<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { staffAuth } from '../../services/auth';
const name = ref(''), store = ref(''), checking = ref(true), error = ref('');
const canRecover = ref(false),canQueryInventory=ref(false),canReceive=ref(false),canDeliver=ref(false);
let epoch = 0;
const openDeviceCheck = () => uni.navigateTo({ url: '/pages/device-check/index' });
const openRecovery = () => uni.navigateTo({ url:'/pages/recovery/index' });
const openSale = () => uni.navigateTo({ url: '/pages/sale/index' });
const canSale = ref(false);
const openInventory = () => uni.navigateTo({ url:'/pages/scan/index' });
const openReceiving = () => uni.navigateTo({url:'/pages/receipt/list/index'});
const openDelivery = () => uni.navigateTo({url:'/pages/delivery/list/index'});
const canPickup=ref(false);
const openPickup=()=>uni.navigateTo({url:'/pages/pickup/list/index'});
onShow(async () => {
  const current = ++epoch;
  checking.value = true; error.value = ''; name.value = ''; store.value = '';
  canRecover.value = false;canSale.value=false;canQueryInventory.value=false;canReceive.value=false;canDeliver.value=false;
  canPickup.value=false;
  try {
    await staffAuth.refresh();
    if (epoch !== current) return;
    const actor = staffAuth.current();
    if (!actor) throw Error('UNAUTHORIZED');
    name.value = actor.name; store.value = actor.storeId;
    canRecover.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    canSale.value = canRecover.value;
    canQueryInventory.value=actor.permissions.includes('*')||actor.permissions.includes('inventory:view');
    canReceive.value=actor.permissions.includes('*')||actor.permissions.includes('purchase:inbound');
    canDeliver.value=actor.permissions.includes('*')||actor.permissions.includes('order:delivery:assigned');
    canPickup.value=actor.permissions.includes('*')||actor.permissions.includes('order:pickup:verify');
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
    <view v-if="checking" class="card">
      <view class="row">
        <view class="hero-avatar hero-avatar-quiet"><AppIcon name="user" :size="20" /></view>
        <view class="grow"><text class="t-sub">正在核对员工与门店</text><text class="t-cap">请稍候</text></view>
      </view>
    </view>

    <template v-else-if="name">
      <view class="card-hero">
        <view class="row">
          <view class="hero-avatar"><AppIcon name="store" :size="22" /></view>
          <view class="grow">
            <text class="hero-name">{{ name }}</text>
            <text class="hero-meta">门店 {{ store }}</text>
          </view>
          <button hover-class="btn-hover" hover-stay-time="60" class="hero-exit" @click="logout">退出</button>
        </view>
      </view>

      <view class="section section-first">
        <view class="section-head">
          <text class="section-title">常用业务</text>
          <text class="section-note">按权限显示</text>
        </view>
      </view>

      <view class="tiles">
        <view v-if="canSale" class="tile">
          <view hover-class="tile-hover" hover-stay-time="80" class="tile-box" @click="openSale">
            <view class="tile-icon"><AppIcon name="bag" :size="22" /></view>
            <text class="tile-name">移动开单</text>
            <text class="tile-desc">扫码加购，后端报价</text>
          </view>
        </view>
        <view v-if="canReceive" class="tile">
          <view hover-class="tile-hover" hover-stay-time="80" class="tile-box" @click="openReceiving">
            <view class="tile-icon"><AppIcon name="doc" :size="20" /></view>
            <text class="tile-name">采购收货</text>
            <text class="tile-desc">按实际到货登记</text>
          </view>
        </view>
        <view v-if="canQueryInventory" class="tile">
          <view hover-class="tile-hover" hover-stay-time="80" class="tile-box" @click="openInventory">
            <view class="tile-icon"><AppIcon name="scan" :size="20" /></view>
            <text class="tile-name">扫码查货</text>
            <text class="tile-desc">条码、批次、货位</text>
          </view>
        </view>
        <view v-if="canPickup" class="tile">
          <view hover-class="tile-hover" hover-stay-time="80" class="tile-box" @click="openPickup">
            <view class="tile-icon"><AppIcon name="box" :size="20" /></view>
            <text class="tile-name">库房提货</text>
            <text class="tile-desc">按原批次核对交付</text>
          </view>
        </view>
        <view v-if="canDeliver" class="tile">
          <view hover-class="tile-hover" hover-stay-time="80" class="tile-box" @click="openDelivery">
            <view class="tile-icon"><AppIcon name="truck" :size="20" /></view>
            <text class="tile-name">配送任务</text>
            <text class="tile-desc">本人任务与签收</text>
          </view>
        </view>
        <view v-if="canRecover" class="tile">
          <view hover-class="tile-hover" hover-stay-time="80" class="tile-box" @click="openRecovery">
            <view class="tile-icon"><AppIcon name="clock" :size="20" /></view>
            <text class="tile-name">收银核对</text>
            <text class="tile-desc">结果未定时先核对</text>
          </view>
        </view>
      </view>

      <view v-if="!canSale && !canReceive && !canQueryInventory && !canPickup && !canDeliver && !canRecover" class="card empty">
        <view class="empty-icon"><AppIcon name="user" :size="20" /></view>
        <text class="empty-text">当前账号还没有业务权限，请联系店长分配后再进入。</text>
      </view>

      <view class="section"><text class="section-title">本机</text></view>
      <view class="card card-flat">
        <view hover-class="tap-hover" hover-stay-time="80" class="list-item list-item-last row-between" @click="openDeviceCheck">
          <view class="row">
            <AppIcon name="device" :size="20" class="t-primary" />
            <view class="ml10">
              <text class="t-sub">检查这台手机</text>
              <text class="t-cap">扫码、录音、存储与网络</text>
            </view>
          </view>
          <AppIcon name="chevron" :size="14" class="chev" />
        </view>
      </view>
    </template>

    <view v-if="error" class="alert alert-danger">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <text>{{ error }}</text>
    </view>
  </view>
</template>

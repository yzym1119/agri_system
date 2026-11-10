<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import type { CustomerProfile } from '@agri/mobile-contracts/customer-auth';
import { customerAuth, errorText } from '../../services/runtime';
import { largeText } from '../../services/preferences';
const snapshot = ref(customerAuth.current()), profile = ref<CustomerProfile | null>(null), busy = ref(false), error = ref('');
let visible = false, generation = 0;
async function load() {
  visible = true; const ticket = ++generation;
  snapshot.value = customerAuth.current(); profile.value = null; error.value = ''; busy.value = false;
  if (snapshot.value.kind !== 'CUSTOMER') return;
  busy.value = true;
  try { const value = await customerAuth.refreshProfile(); if (visible && ticket === generation) { profile.value = value; snapshot.value = customerAuth.current(); } }
  catch (failure) { if (failure instanceof Error && failure.message === 'STALE_SESSION') return; if (visible && ticket === generation) { error.value = '本人资料暂时无法确认，请重新微信登录。'; snapshot.value = customerAuth.current(); } }
  finally { if (visible && ticket === generation) busy.value = false; }
}
function hide() { visible = false; ++generation; }
onShow(load); onHide(hide); onUnload(hide);
function login() { uni.navigateTo({ url: '/pages/login/index' }); }
function claim() { uni.navigateTo({ url: '/pages/identity/claim/index' }); }
function logout() { ++generation; void customerAuth.logout(); snapshot.value = customerAuth.current(); profile.value = null; busy.value = false; error.value = ''; }
</script>
<template>
  <view class="customer-page with-navigation" :class="{ 'large-text': largeText }">
    <CustomerNavigation active="profile" />
    <ReadingHeader title="我的" />
    <text v-if="busy" class="loading">正在核对本人资料…</text>
    <view v-if="error" class="notice" role="alert">{{ error }}</view>
    <template v-if="profile">
      <text class="title">{{ profile.name }}</text>
      <view class="panel"><text class="panel-title">门店客户资料</text><view class="row"><text class="row-label">手机号</text><text class="row-value">{{ profile.phoneMasked || '尚未登记联系方式' }}</text></view><view class="row"><text class="row-label">微信关联</text><text class="row-value">已关联本人档案</text></view></view>
      <button class="button button-secondary" hover-class="button-pressed" @tap="claim">核对本机原申请</button>
    </template>
    <template v-else-if="!busy">
      <text class="title">{{ snapshot.kind === 'ONBOARDING' ? '继续核对档案' : '查看自己的资料' }}</text>
      <text class="paragraph secondary">使用微信登录，才能查看本人门店资料。手机号授权与门店档案核验是不同步骤。</text>
      <button class="button" hover-class="button-pressed" @tap="login">{{ snapshot.kind === 'ONBOARDING' ? '继续登录核验' : '微信登录' }}</button>
    </template>
    <text class="help">这里可以核对门店档案和查看本人资料。如需核对订单或账款，请联系门店。</text>
    <button v-if="snapshot.kind !== 'GUEST'" class="button button-secondary" hover-class="button-pressed" @tap="logout">退出当前登录</button>
  </view>
</template>

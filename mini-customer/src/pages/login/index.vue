<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { customerAuth, serviceConfigured, errorText } from '../../services/runtime';
import { largeText } from '../../services/preferences';
const snapshot = ref(customerAuth.current()), busy = ref(false), error = ref('');
let visible = false, generation = 0;
let attempt: ReturnType<typeof customerAuth.beginWechatLogin> | null = null;
onShow(() => { visible = true; ++generation; busy.value = false; snapshot.value = customerAuth.current(); });
function hide() { visible = false; ++generation; attempt?.cancel(); attempt = null; busy.value = false; }
onHide(hide); onUnload(hide);
async function run(phoneCode?: string) {
  if (!visible || busy.value || !serviceConfigured) return;
  const ticket = generation; busy.value = true; error.value = '';
  try {
    attempt = phoneCode ? customerAuth.beginPhoneAuthorization(phoneCode) : customerAuth.beginWechatLogin();
    await attempt.result;
    if (!visible || ticket !== generation) return;
    snapshot.value = customerAuth.current();
    if (snapshot.value.kind === 'CUSTOMER') uni.reLaunch({ url: '/pages/profile/index' });
  } catch (failure) {
    if (failure instanceof Error && failure.message === 'STALE_SESSION') return;
    if (visible && ticket === generation) { error.value = errorText(failure); snapshot.value = customerAuth.current(); }
  } finally { if (visible && ticket === generation) { busy.value = false; attempt = null; } }
}
async function login() { await run(); }
async function authorizePhone(event: { detail?: { code?: string } }) {
  if (!event.detail?.code) { if (visible) error.value = '您没有授权手机号。可以稍后再试，也可以返回首页。'; return; }
  await run(event.detail.code);
}
function continueClaim() { if (!busy.value) uni.navigateTo({ url: '/pages/identity/claim/index' }); }
function home() { uni.reLaunch({ url: '/pages/home/index' }); }
function mine() { uni.reLaunch({ url: '/pages/profile/index' }); }
</script>
<template>
  <view class="customer-page" :class="{ 'large-text': largeText }">
    <ReadingHeader title="微信登录" />
    <view class="title"><text>登录自己的</text><text class="title-line">门店资料。</text></view>
    <text class="paragraph secondary">登录后才能查看您自己的资料。手机号只在您主动授权后，用于核对门店档案。</text>
    <view v-if="!serviceConfigured" class="notice">门店服务暂未连接，请联系门店或稍后再试。</view>
    <view v-if="error" class="notice" role="alert">{{ error }}</view>
    <template v-if="snapshot.kind === 'GUEST'">
      <button class="button" :class="{ 'button-disabled': busy || !serviceConfigured }" hover-class="button-pressed" :disabled="busy || !serviceConfigured" :loading="busy" @tap="login">{{ busy ? '正在连接微信' : '使用微信登录' }}</button>
    </template>
    <template v-else-if="snapshot.kind === 'ONBOARDING' && snapshot.state === 'PHONE_REQUIRED'">
      <text class="paragraph">还需要您授权微信手机号，再核对是否已有门店档案。</text>
      <button class="button" :class="{ 'button-disabled': busy }" hover-class="button-pressed" open-type="getPhoneNumber" :disabled="busy" :loading="busy" @getphonenumber="authorizePhone">授权微信手机号</button>
      <button class="button button-secondary" hover-class="button-pressed" :disabled="busy" @tap="continueClaim">核对本机原申请</button>
    </template>
    <template v-else-if="snapshot.kind === 'ONBOARDING'">
      <view class="panel"><text class="panel-title">手机号已核验</text><text>{{ snapshot.phoneMasked }}</text><text class="help">这一步还没有认领旧档案。下一步由门店系统核对。</text></view>
      <button class="button" hover-class="button-pressed" @tap="continueClaim">继续核对门店档案</button>
    </template>
    <button v-else class="button" hover-class="button-pressed" @tap="mine">查看我的资料</button>
    <button class="button button-secondary" hover-class="button-pressed" @tap="home">先返回首页</button>
    <text class="help">不授权也能返回首页。旧档案关联须核验，不会自动转移余额、欠款或订单。</text>
  </view>
</template>

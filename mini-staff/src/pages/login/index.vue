<script setup lang="ts">
import { ref } from 'vue';
import { onHide } from '@dcloudio/uni-app';
import { serviceConfigured, staffAuth } from '../../services/auth';
const account = ref(''), password = ref(''), busy = ref(false), error = ref('');
let epoch = 0;
let cancelLogin: (() => void) | undefined;
const openDeviceCheck = () => uni.navigateTo({ url: '/pages/device-check/index' });
onHide(() => { epoch++; password.value = ''; cancelLogin?.(); cancelLogin = undefined; });
async function login() {
  if (busy.value || !serviceConfigured) return;
  const current = ++epoch;
  busy.value = true; error.value = '';
  const secret = password.value;
  password.value = '';
  try {
    const attempt = staffAuth.beginLogin(account.value, secret);
    cancelLogin = attempt.cancel;
    await attempt.result;
    if (epoch === current) uni.reLaunch({ url: '/pages/workbench/index' });
  } catch (failure) {
    if (epoch !== current) return;
    const code = failure instanceof Error ? failure.message : '';
    error.value = code === 'CREDENTIALS_REQUIRED' ? '请输入员工编码或手机号和密码。'
      : code === 'IDENTITY_INVALID' ? '员工资料缺少有效门店或权限，请联系老板核对。'
      : code === 'RESULT_UNKNOWN' ? '网络连接未完成，请检查网络后重新登录。'
      : '未能登录，请核对账号密码及员工状态。';
  } finally { busy.value = false; }
}
</script>
<template>
  <view class="page login">
    <view class="brand">
      <view class="brand-mark"><AppIcon name="store" :size="28" /></view>
      <text class="brand-title">门店工作台</text>
      <text class="brand-desc">使用门店员工账号登录，不使用客户微信身份</text>
    </view>

    <view v-if="!serviceConfigured" class="alert alert-danger">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <text>服务地址尚未配置，暂时无法登录；设备检查仍可使用。</text>
    </view>

    <view class="card">
      <view class="field field-first">
        <text class="field-label">员工编码或手机号</text>
        <input v-model="account" class="input" :disabled="busy" maxlength="64" placeholder="输入员工编码或手机号" placeholder-style="color:#9aa6b2" />
      </view>
      <view class="field">
        <text class="field-label">密码</text>
        <input v-model="password" class="input" :disabled="busy" password maxlength="128" placeholder="输入密码" placeholder-style="color:#9aa6b2" @confirm="login" />
      </view>
      <view v-if="error" class="error-line">
        <AppIcon name="alert" :size="14" class="mr4 t-danger" />
        <text>{{ error }}</text>
      </view>
    </view>

    <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-primary" :loading="busy" :disabled="busy || !serviceConfigured || !account.trim() || !password" @click="login">登录</button>
    <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-quiet btn-gap" :disabled="busy" @click="openDeviceCheck">先检查这台手机</button>
    <text class="foot">密码不在本机保存，下次打开需重新登录。没有账号请联系老板。</text>
  </view>
</template>
<style scoped>
.login { padding-top: 24px; }
.error-line { display: flex; align-items: flex-start; margin-top: 12px; font-size: 13px; color: #a12925; line-height: 1.6; }
</style>

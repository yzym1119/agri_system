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
  <view class="staff-page login-page">
    <view class="brand"><text class="brand-mark">农</text><view><text class="brand-name">农资门店</text><text class="staff-muted">员工工作台</text></view></view>
    <view class="welcome"><text class="title">把门店工作，带在身边</text><text class="intro">开单、查货、收货与配送，登录后按你的岗位展示。</text></view>
    <view class="staff-panel login-panel">
      <text class="panel-title">登录员工账号</text>
      <text v-if="!serviceConfigured" class="notice">服务暂未配置，请联系管理员。你仍可以检查手机设备。</text>
      <label>员工编码或手机号<input v-model="account" :disabled="busy" maxlength="64" placeholder="请输入员工编码或手机号" placeholder-style="color:#626f7e" /></label>
      <label>密码<input v-model="password" :disabled="busy" password maxlength="128" placeholder="请输入登录密码" placeholder-style="color:#626f7e" @confirm="login" /></label>
      <text v-if="error" class="staff-error" role="alert">{{ error }}</text>
      <button class="staff-primary login-button" :loading="busy" :disabled="busy || !serviceConfigured || !account.trim() || !password" @click="login">{{ busy ? '正在登录…' : '登录工作台' }}</button>
      <text class="staff-muted">没有账号或忘记密码？请联系老板核对员工资料。</text>
    </view>
    <view class="login-footer"><button :disabled="busy" class="device-button" @click="openDeviceCheck">检查这台手机 ›</button><text class="staff-muted">仅供门店员工使用 · 本机不保存密码</text></view>
  </view>
</template>
<style scoped>
.login-page { padding-top:40px; }
.brand { display:flex; align-items:center; gap:12px; }.brand-mark { display:flex; align-items:center; justify-content:center; width:48px; height:48px; background:#1d5d73; color:#fff; font-weight:600; font-size:24px; border-radius:14px; }.brand-name { font-weight:600; font-size:18px; }
.welcome { padding:36px 0 24px; }.title { display:block; font-size:26px; font-weight:650; line-height:1.45; letter-spacing:-.3px; }.intro { display:block; margin-top:12px; color:#4b5563; font-size:15px; line-height:1.7; }
.login-panel { padding:24px 20px; }.panel-title { display:block; font-size:18px; font-weight:600; margin-bottom:24px; }
label { display:block; margin-top:20px; font-size:14px; font-weight:550; }input { margin-top:8px; min-height:48px; padding:0 12px; border:1px solid #a8b2bc; background:#fff; border-radius:8px; font-size:16px; }
.login-button { margin:28px 0 16px; }.notice { display:block; padding:12px; border:1px solid #e5e7eb; background:#f5f6f8; color:#4b5563; line-height:1.65; font-size:14px; }
.login-footer { text-align:center; padding:16px 0; }.device-button { background:transparent; color:#1d5d73; font-size:14px; }
</style>

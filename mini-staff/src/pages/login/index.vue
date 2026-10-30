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
  <view class="page">
    <text class="title">员工登录</text>
    <text class="intro">使用门店已有员工账号。没有账号时请联系老板，不要使用客户微信身份代替。</text>
    <text v-if="!serviceConfigured" class="notice">服务地址尚未配置，请先联系管理员。设备检查仍可使用。</text>
    <label>员工编码或手机号<input v-model="account" :disabled="busy" maxlength="64" placeholder="输入员工编码或手机号" placeholder-style="color:#626f7e" /></label>
    <label>密码<input v-model="password" :disabled="busy" password maxlength="128" placeholder="输入密码" placeholder-style="color:#626f7e" @confirm="login" /></label>
    <text v-if="error" class="error" role="alert">{{ error }}</text>
    <button class="primary" :loading="busy" :disabled="busy || !serviceConfigured || !account.trim() || !password" @click="login">登录</button>
    <button :disabled="busy" @click="openDeviceCheck">先检查这台手机</button>
    <text class="intro">本机不保存密码。重新打开小程序后需再次登录。</text>
  </view>
</template>
<style scoped>
.page { padding:32px 20px; max-width:560px; margin:auto; }
.title { display:block; font-size:26px; font-weight:600; margin-bottom:12px; }
.intro,.notice { display:block; line-height:1.7; color:#4b5563; margin:12px 0; }
.notice { padding:16px; background:#fff; border:1px solid #e5e7eb; border-radius:8px; }
label { display:block; margin-top:24px; }
input { margin-top:8px; min-height:48px; padding:0 12px; border:1px solid #626f7e; background:#fff; border-radius:8px; }
button { margin:20px 0; color:#1d5d73; background:#fff; border:1px solid #1d5d73; }
.primary { background:#1d5d73; color:#fff; }
.error { display:block; color:#a12925; margin:16px 0; line-height:1.7; }
</style>

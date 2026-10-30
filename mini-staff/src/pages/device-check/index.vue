<script setup lang="ts">
import { ref } from 'vue';
import { createMobilePlatform, type SecureRandomPort } from '@agri/mobile-contracts';
declare const wx: SecureRandomPort;
const random = typeof wx !== 'undefined' && typeof wx.getRandomValues === 'function'
  ? { getRandomValues: (options: Parameters<SecureRandomPort['getRandomValues']>[0]) => wx.getRandomValues(options) } : undefined;
const platform = createMobilePlatform(uni, random);
const busy = ref(false);
const message = ref('选择一项检查，结果会显示在这里。');
async function check(kind: 'scan' | 'storage' | 'random') {
  if (busy.value) return;
  busy.value = true;
  try {
    if (kind === 'scan') {
      const result = await platform.scan();
      message.value = result.status === 'SCANNED' ? '识别到：' + result.code
        : result.status === 'CANCELLED' ? '已取消扫码，可以重新检查。' : '相机未授权，后续业务页会保留手输入口。';
    } else if (kind === 'random') {
      await platform.createRequestKey();
      message.value = '安全请求号生成成功。这里只做能力检查，没有提交业务。';
    } else {
      const key = 'agri:device-probe:' + Date.now();
      platform.writeStorage(key, '设备验证');
      platform.removeStorage(key);
      message.value = '本机记录读写和删除核对成功。';
    }
  } catch {
    message.value = '检查未通过，请记录手机型号与操作步骤。此设备暂不开放业务提交。';
  } finally { busy.value = false; }
}
</script>
<template>
  <view class="page">
    <text class="title">先检查这台手机</text>
    <text class="intro">在门店实际使用的手机上验证。当前仅做设备检查，不收款、不改库存。</text>
    <view class="checks">
      <button class="primary" :disabled="busy" @click="check('scan')">扫描商品或批次标签</button>
      <button :disabled="busy" @click="check('storage')">检查本机记录</button>
      <button :disabled="busy" @click="check('random')">检查安全请求号</button>
    </view>
    <view class="result"><text>{{ busy ? '正在检查，请稍候…' : message }}</text></view>
    <text class="hint">请分别记录安卓和 iPhone 的结果。编译成功不代表相机或弱网已通过验收。</text>
  </view>
</template>
<style scoped>
.page { padding:24px 20px; padding-bottom:calc(24px + env(safe-area-inset-bottom)); max-width:600px; margin:auto; }
.title { display:block; font-size:24px; font-weight:600; margin-bottom:12px; }
.intro,.hint { display:block; color:#4b5563; line-height:1.7; }
.checks { display:flex; flex-direction:column; gap:12px; margin:28px 0 20px; }
button { width:100%; background:#fff; color:#1d5d73; border:1px solid #1d5d73; }
button.primary { background:#1d5d73; color:#fff; }
button[disabled] { opacity:.55; }
.result { background:#fff; padding:20px; border:1px solid #e5e7eb; border-radius:10px; line-height:1.7; overflow-wrap:anywhere; }
.hint { margin-top:20px; }
</style>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import type { Coordinate } from '@agri/mobile-contracts';
import { mobilePlatform as platform } from '../../services/platform';
import type { RecordingState } from '@agri/mobile-contracts/recorder';
import { getDeviceRecorder } from '../../services/device-recorder';
const busy = ref(false);
const message = ref('选择一项检查，结果会显示在这里。');
const address = ref('');
const latitude = ref('');
const longitude = ref('');
const network = ref('尚未读取');
let visible = false, viewEpoch = 0, networkVersion = 0;
const recording = ref<RecordingState>({ phase: 'IDLE', clip: '' });
const recordingBusy = computed(() => ['STARTING', 'RECORDING', 'STOPPING'].includes(recording.value.phase));
let recorder: ReturnType<typeof getDeviceRecorder> | undefined;
let unsubscribe = () => {};
const audio = uni.createInnerAudioContext();
audio.onError(() => { if (visible) message.value = '试听失败，可以清除后重新录一段。'; });
function ensureRecorder() {
  if (!recorder) {
    recorder = getDeviceRecorder();
    unsubscribe = recorder.subscribe(state => { recording.value = state; });
  }
  return recorder;
}
function refreshNetwork() {
  const current = ++networkVersion, epoch = viewEpoch;
  network.value = '读取中…';
  uni.getNetworkType({
    success: result => {
      if (visible && current === networkVersion && epoch === viewEpoch)
        network.value = result.networkType === 'none' ? '当前离线' : '已连接：' + result.networkType + '（不代表后台可用）';
    },
    fail: () => { if (visible && current === networkVersion && epoch === viewEpoch) network.value = '网络状态读取失败，可重试'; },
  });
}
const networkChanged = () => { if (visible) refreshNetwork(); };
onShow(() => {
  visible = true; viewEpoch++;
  recorder?.show();
  uni.offNetworkStatusChange(networkChanged);
  uni.onNetworkStatusChange(networkChanged);
  refreshNetwork();
  message.value = '页面已就绪。返回前台后请重新核对检查结果。';
});
function leave() {
  visible = false; viewEpoch++; networkVersion++;
  uni.offNetworkStatusChange(networkChanged);
  recorder?.hide();
  audio.stop();
}
onHide(leave);
onUnload(() => { leave(); unsubscribe(); audio.destroy(); });
async function check(kind: 'scan' | 'storage' | 'random' | 'navigation') {
  if (busy.value || recordingBusy.value) return;
  busy.value = true;
  const epoch = viewEpoch;
  let resultMessage = '';
  try {
    if (kind === 'scan') {
      const result = await platform.scan();
      resultMessage = result.status === 'SCANNED' ? '识别到：' + result.code
        : result.status === 'CANCELLED' ? '已取消扫码，可以重新检查。' : '相机未授权，后续业务页会保留手输入口。';
    } else if (kind === 'random') {
      await platform.createRequestKey();
      resultMessage = '安全请求号生成成功。这里只做能力检查，没有提交业务。';
    } else if (kind === 'navigation') {
      let coordinate: Coordinate | undefined;
      if (latitude.value.trim() || longitude.value.trim()) {
        const numberText = /^-?\d+(\.\d+)?$/;
        if (!numberText.test(latitude.value.trim()) || !numberText.test(longitude.value.trim()))
          throw Error('坐标必须同时填写完整的数字');
        coordinate = { latitude: Number(latitude.value), longitude: Number(longitude.value), system: 'GCJ02' };
      }
      const result = await platform.navigateToAddress(address.value.trim(), coordinate);
      resultMessage = result.status === 'ADDRESS_ONLY' ? '没有可信坐标，请复制地址到地图中搜索：' + result.address : '已打开地图，请核对目的地；不代表已签收。';
    } else {
      const key = 'agri:device-probe:' + Date.now();
      platform.writeStorage(key, '设备验证');
      platform.removeStorage(key);
      resultMessage = '本机记录读写和删除核对成功。';
    }
  } catch (failure) {
    const randomFailures = {
      RANDOM_UNAVAILABLE: '当前微信环境未提供安全随机能力，请在手机微信中复测。',
      RANDOM_FAILED: '安全随机调用未完成，请重试；仍失败时请在手机微信中复测。',
      RANDOM_INVALID: '安全随机返回数据异常，请记录微信版本并联系管理员。',
    };
    const randomFailure = failure instanceof Error && Object.prototype.hasOwnProperty.call(randomFailures, failure.message)
      ? randomFailures[failure.message as keyof typeof randomFailures] : undefined;
    resultMessage = kind === 'navigation' ? '未能打开目的地。请核对地址及两项GCJ02坐标；也可只填写地址手工搜索。'
      : (kind === 'random' && randomFailure ? randomFailure : '检查未通过，请记录手机型号与操作步骤。') + '此设备暂不开放业务提交。';
  } finally {
    busy.value = false;
    if (visible && epoch === viewEpoch) message.value = resultMessage;
  }
}
function startRecording() {
  if (busy.value || recordingBusy.value) return;
  try { const probe = ensureRecorder(); probe.show(); audio.stop(); probe.start(); }
  catch { message.value = '此设备暂不能启动录音，请使用文字采集。'; }
}
function play() {
  if (!recording.value.clip || recordingBusy.value) return;
  audio.src = recording.value.clip;
  audio.play();
}
function clearRecording() { audio.stop(); recorder?.clear(); }
const recordingText = computed(() => ({
  IDLE: '录音仅在这台手机临时试听，不上传。', STARTING: '正在请求录音，请确认麦克风权限。',
  RECORDING: '正在录音，最长10秒。', STOPPING: '正在核对录音停止，请勿再次启动。若一直无响应，请关闭小程序。',
  READY: '录音已停止，可以试听或清除。', ERROR: '录音失败或未获授权，可重试或使用文字采集。',
}[recording.value.phase]));
</script>
<template>
  <view class="page">
    <text class="t-body block">在门店实际使用的手机上验证。只做设备检查，不收款、不改库存。</text>

    <view class="section section-first"><text class="section-title">基础能力</text></view>
    <view class="card card-flat">
      <view class="list-item row-between">
        <view class="row"><AppIcon name="scan" :size="20" class="t-primary" /><text class="t-sub ml10">扫码</text></view>
        <button class="btn btn-sm btn-auto" :disabled="busy || recordingBusy" @click="check('scan')">检查</button>
      </view>
      <view class="list-item row-between">
        <view class="row"><AppIcon name="box" :size="20" class="t-primary" /><text class="t-sub ml10">本机记录</text></view>
        <button class="btn btn-sm btn-auto" :disabled="busy || recordingBusy" @click="check('storage')">检查</button>
      </view>
      <view class="list-item list-item-last row-between">
        <view class="row"><AppIcon name="check" :size="20" class="t-primary" /><text class="t-sub ml10">安全请求号</text></view>
        <button class="btn btn-sm btn-auto" :disabled="busy || recordingBusy" @click="check('random')">检查</button>
      </view>
    </view>

    <view class="alert alert-info mt12">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <text>{{ busy ? '正在检查，请稍候…' : message }}</text>
    </view>

    <view class="section"><text class="section-title">录音与试听</text></view>
    <view class="card">
      <text class="t-meta">{{ recordingText }}</text>
      <view class="row mt12">
        <button class="btn btn-primary btn-sm grow mr8" :disabled="busy || recordingBusy" @click="startRecording">开始录音</button>
        <button class="btn btn-ghost btn-sm grow" :disabled="!['STARTING', 'RECORDING'].includes(recording.phase)" @click="recorder?.stop()">停止</button>
      </view>
      <view class="row mt8">
        <button class="btn btn-ghost btn-sm grow mr8" :disabled="!recording.clip" @click="play">试听</button>
        <button class="btn btn-ghost btn-sm grow" :disabled="!recording.clip" @click="clearRecording">清除</button>
      </view>
    </view>

    <view class="section"><text class="section-title">网络</text></view>
    <view class="card">
      <text class="t-body">{{ network }}</text>
      <button class="btn btn-ghost btn-sm mt8" @click="refreshNetwork">重新读取网络状态</button>
      <text class="t-cap mt8">切断并恢复网络、切后台再返回，观察状态是否更新。这里不向后台发请求，不能据此判定弱网业务恢复通过。</text>
    </view>

    <view class="section"><text class="section-title">目的地导航</text></view>
    <view class="card">
      <view class="field field-first">
        <text class="field-label">测试目的地地址</text>
        <input v-model="address" class="input" maxlength="200" placeholder="填写实际测试地址" placeholder-style="color:#9aa6b2" />
      </view>
      <text class="t-cap mt8">有已核对的 GCJ02 坐标时再填两项；不读取手机定位，也不猜测地址坐标。</text>
      <view class="row mt12">
        <view class="grow mr8">
          <text class="field-label">纬度（可不填）</text>
          <input v-model="latitude" class="input" maxlength="24" placeholder="GCJ02 纬度" placeholder-style="color:#9aa6b2" />
        </view>
        <view class="grow">
          <text class="field-label">经度（可不填）</text>
          <input v-model="longitude" class="input" maxlength="24" placeholder="GCJ02 经度" placeholder-style="color:#9aa6b2" />
        </view>
      </view>
      <button class="btn btn-ghost btn-sm mt12" :disabled="busy || recordingBusy || !address.trim()" @click="check('navigation')">检查目的地</button>
    </view>

    <text class="t-cap mt12">请分别记录安卓和 iPhone 的结果。编译成功不代表真机或业务验收通过。</text>
  </view>
</template>

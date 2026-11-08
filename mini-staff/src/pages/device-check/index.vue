<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import type { Coordinate } from '@agri/mobile-contracts';
import { mobilePlatform as platform } from '../../services/platform';
import type { RecordingState } from '@agri/mobile-contracts/recorder';
import { getDeviceRecorder } from '../../services/device-recorder';
const busy = ref(false), advanced=ref(false);
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
      : (kind === 'random' && randomFailure ? randomFailure : '检查未通过，请记录手机型号与操作步骤。') + '请先完成设备核对，再进行业务提交。';
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
  <view class="staff-page staff-tools"><view class="page-heading"><text class="eyebrow">工作保障</text><text class="title">检查这台手机</text></view>
    <text class="intro">在门店实际使用的手机上验证。当前仅做设备检查，不收款、不改库存。</text>
    <view class="checks">
      <button class="staff-primary" :disabled="busy || recordingBusy" @click="check('scan')">扫描商品或批次标签</button>
      <button :disabled="busy || recordingBusy" @click="check('storage')">检查本机记录</button>
    </view>
    <view class="staff-panel result"><text>{{ busy ? '正在检查，请稍候…' : message }}</text></view>
    <button class="staff-secondary" :disabled="busy || recordingBusy" @click="advanced=!advanced">{{ advanced?'收起更多检查':'更多设备检查 ↓' }}</button>
    <view v-if="advanced" class="staff-panel section"><text class="heading">安全请求号</text><text class="intro">检查本机能否生成业务防重所需的安全请求号，不会发起业务。</text>      <button :disabled="busy || recordingBusy" @click="check('random')">检查安全请求号</button>
    </view>
    <view v-if="advanced" class="staff-panel section">
      <text class="heading">录一段并试听</text>
      <text class="intro">{{ recordingText }}</text>
      <button class="staff-primary" :disabled="busy || recordingBusy" @click="startRecording">开始录音（最长10秒）</button>
      <button :disabled="!['STARTING', 'RECORDING'].includes(recording.phase)" @click="recorder?.stop()">停止录音</button>
      <view class="actions">
        <button :disabled="!recording.clip" @click="play">试听</button>
        <button :disabled="!recording.clip" @click="clearRecording">清除</button>
      </view>
    </view>
    <view class="staff-panel section">
      <text class="heading">网络与前后台</text>
      <text class="intro">{{ network }}</text>
      <button @click="refreshNetwork">重新读取网络状态</button>
      <text class="hint">切断网络、恢复网络，再切到后台返回，观察状态是否更新。这里没有向后台发送请求，不能据此判定弱网业务恢复通过。</text>
    </view>
    <view v-if="advanced" class="staff-panel section">
      <text class="heading">目的地导航</text>
      <label>测试目的地地址<input placeholder-style="color:#626f7e" v-model="address" maxlength="200" placeholder="填写实际测试地址" /></label>
      <text class="hint">有已核对的GCJ02坐标时再填写两项；不读取手机定位，也不猜测地址坐标。</text>
      <label>纬度（可不填）<input placeholder-style="color:#626f7e" v-model="latitude" maxlength="24" placeholder="GCJ02纬度" /></label>
      <label>经度（可不填）<input placeholder-style="color:#626f7e" v-model="longitude" maxlength="24" placeholder="GCJ02经度" /></label>
      <button :disabled="busy || recordingBusy || !address.trim()" @click="check('navigation')">检查目的地</button>
    </view>
    <text class="hint">请分别记录安卓和 iPhone 的结果。编译成功不代表真机或业务验收通过。</text>
  </view>
</template>

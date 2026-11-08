<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import type { PendingSaleRecord, POSResult } from '@agri/mobile-contracts/submission';
import { staffAuth } from '../../services/auth';
import { posRecovery, readPOSResult, cancelPOSRequest } from '../../services/pos-recovery';
const pending = ref<PendingSaleRecord | null>(null), result = ref<POSResult | null>(null);
const loading = ref(true), busy = ref(false), allowed = ref(false), confirmed = ref(false);
const message = ref(''), restored = ref(false);
let visible=false,actorEpoch=-1;
let epoch = 0;
const formatCents = (value: number) => { const text=String(value).padStart(3,'0'); return text.slice(0,-2)+'.'+text.slice(-2); };
onShow(async () => {
  visible=true;actorEpoch=-1;restored.value=false;
  const current = ++epoch;
  loading.value = true; allowed.value = false; busy.value = false; pending.value = null;
  result.value = null; confirmed.value = false; message.value = '';
  try {
    await staffAuth.refresh();
    if (current !== epoch) return;
    const actor = staffAuth.current();
    if (!actor) throw Error('UNAUTHORIZED');
    actorEpoch=actor.epoch;
    allowed.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    if (!allowed.value) { message.value = '当前账号没有核对收银权限，请联系老板。原记录仍保留。'; return; }
    pending.value = posRecovery.restore(); restored.value=true;
    if (!pending.value) message.value = '本机当前员工、门店没有待核对收银请求。';
  } catch (failure) {
    if (current !== epoch) return;
    if (!staffAuth.current()) { uni.reLaunch({ url:'/pages/login/index' }); return; }
    message.value = '本机核对记录读取异常，保持暂停收银。请联系老板核对，不要清除小程序数据。';
  } finally { if (current === epoch) loading.value = false; }
});
onHide(() => { visible=false;actorEpoch=-1;epoch++;allowed.value=false;pending.value=null;result.value=null;confirmed.value=false; });
async function act(kind: 'read' | 'cancel' | 'finish') {
  if (!visible || staffAuth.current()?.epoch!==actorEpoch || !allowed.value || busy.value || !pending.value || (kind !== 'read' && !confirmed.value)) return;
  const current = epoch, expected = result.value?.orderId;
  if (kind === 'finish' && (result.value?.status !== 'COMPLETED' || !expected)) return;
  busy.value = true; message.value = '';
  try {
    const answer = kind === 'read' ? await posRecovery.lookupOriginal(readPOSResult)
      : kind === 'cancel' ? await posRecovery.cancelPending(true, cancelPOSRequest)
      : await posRecovery.finishCompleted(expected!, true, readPOSResult);
    if (current !== epoch) return;
    result.value = answer; confirmed.value = false;
    pending.value = posRecovery.restore(); restored.value=true;
    message.value = pending.value ? (answer.status === 'COMPLETED' ? '原请求已成交，请核对原订单后结束本单。'
      : '结果仍需核对；没有查到不代表未成交，请勿再次收款。')
      : (answer.status === 'COMPLETED' ? '原成交单据已结束，没有再次收款或扣库存。' : '原请求已由后台结束；请核对客户实际交款。');
  } catch {
    if (current !== epoch) return;
    result.value = null; confirmed.value = false;
    if (!staffAuth.current()) { uni.reLaunch({url:'/pages/login/index'}); return; }
    message.value = '核对未完成或记录已变化，保留原请求暂停收银。不要换号重收或清除本机记录。';
  } finally { if (current === epoch) busy.value = false; }
}
</script>
<template>
  <view class="staff-page staff-tools"><view class="page-heading"><text class="eyebrow">收银结果核对</text><text class="title">先核对，再继续收银</text><text class="intro">保留原请求，避免同一笔销售重复收款。</text></view>
    <text v-if="loading" class="intro">正在核对当前身份…</text>
    <template v-else-if="allowed && pending">
      <view class="notice"><text class="subtitle">本次结果尚待确认</text><text class="intro">请勿再次收款，先查询下方原请求。</text></view>
      <view class="staff-panel"><text class="muted">原请求应结算金额</text><text class="stock-amount">¥{{ formatCents(pending.summary.netCents) }}</text></view>
      <text class="intro">{{ pending.summary.customerId ? '客户编号：' + pending.summary.customerId : '散客' }} · {{ pending.summary.lines.length }} 条物料</text>
      <text class="code">请求号：{{ pending.requestKey }}</text>
      <view v-for="(line, index) in pending.summary.lines" :key="index" class="line">
        <text>商品编号 {{ line.productId }} · {{ line.quantity }} {{ line.unit }}</text>
        <text>¥{{ formatCents(line.lineCents) }}</text>
      </view>
      <button class="staff-primary" :disabled="busy" :loading="busy" @click="act('read')">核对原请求结果</button>
      <view v-if="result?.status === 'COMPLETED'" class="staff-panel result">
        <text class="heading">原请求已成交</text><text>原订单编号：{{ result.orderId }}</text>
        <checkbox-group @change="confirmed = $event.detail.value.includes('confirmed')">
          <label><checkbox value="confirmed" :checked="confirmed" :disabled="busy" />我已核对原订单已成交</label>
        </checkbox-group>
        <button :disabled="busy || !confirmed" @click="act('finish')">结束已成交单据</button>
      </view>
      <view v-else class="staff-panel result">
        <text>确需重新开单时，先让后台结束原请求。若原请求已成交，会返回原订单。</text>
        <checkbox-group @change="confirmed = $event.detail.value.includes('confirmed')">
          <label><checkbox value="confirmed" :checked="confirmed" :disabled="busy" />我已核对实际交款；结束请求不等于退款</label>
        </checkbox-group>
        <button :disabled="busy || !confirmed" @click="act('cancel')">结束原未成交请求</button>
      </view>
    </template>
    <view v-if="allowed && restored && !pending && !loading" class="staff-panel"><text class="subtitle">本机没有待核对请求</text><text class="intro">仅表示当前员工、门店在此手机上的记录已核对；其他手机或丢失记录仍需门店核查。</text></view>
    <text v-if="message" class="intro" role="status">{{ message }}</text>
    <text class="hint">这里只核对本机保存的原请求，不会创建新订单。换手机或清除数据后的缺失记录，请联系门店人工核查。</text>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import type { PendingSaleRecord, POSResult } from '@agri/mobile-contracts/submission';
import { staffAuth } from '../../services/auth';
import { posRecovery, readPOSResult, cancelPOSRequest } from '../../services/pos-recovery';
const pending = ref<PendingSaleRecord | null>(null), result = ref<POSResult | null>(null);
const loading = ref(true), busy = ref(false), allowed = ref(false), confirmed = ref(false);
const message = ref('');
let epoch = 0;
const formatCents = (value: number) => { const text=String(value).padStart(3,'0'); return text.slice(0,-2)+'.'+text.slice(-2); };
onShow(async () => {
  const current = ++epoch;
  loading.value = true; allowed.value = false; busy.value = false; pending.value = null;
  result.value = null; confirmed.value = false; message.value = '';
  try {
    await staffAuth.refresh();
    if (current !== epoch) return;
    const actor = staffAuth.current();
    if (!actor) throw Error('UNAUTHORIZED');
    allowed.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    if (!allowed.value) { message.value = '当前账号没有核对收银权限，请联系老板。原记录仍保留。'; return; }
    pending.value = posRecovery.restore();
    if (!pending.value) message.value = '本机当前员工、门店没有待核对收银请求。';
  } catch (failure) {
    if (current !== epoch) return;
    if (!staffAuth.current()) { uni.reLaunch({ url:'/pages/login/index' }); return; }
    message.value = '本机核对记录读取异常，保持暂停收银。请联系老板核对，不要清除小程序数据。';
  } finally { if (current === epoch) loading.value = false; }
});
onHide(() => { epoch++; });
async function act(kind: 'read' | 'cancel' | 'finish') {
  if (!allowed.value || busy.value || !pending.value || (kind !== 'read' && !confirmed.value)) return;
  const current = epoch, expected = result.value?.orderId;
  if (kind === 'finish' && (result.value?.status !== 'COMPLETED' || !expected)) return;
  busy.value = true; message.value = '';
  try {
    const answer = kind === 'read' ? await posRecovery.lookupOriginal(readPOSResult)
      : kind === 'cancel' ? await posRecovery.cancelPending(true, cancelPOSRequest)
      : await posRecovery.finishCompleted(expected!, true, readPOSResult);
    if (current !== epoch) return;
    result.value = answer; confirmed.value = false;
    pending.value = posRecovery.restore();
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
  <view class="page">
    <text v-if="loading" class="card t-meta">正在核对当前身份…</text>

    <template v-else-if="allowed && pending">
      <view class="alert alert-danger">
        <AppIcon name="alert" :size="16" class="alert-icon" />
        <view class="grow">
          <text class="t-strong">这笔请求结果需要核对</text>
          <text class="t-meta">请勿再次向客户收款，核对前不要换号重开。</text>
        </view>
      </view>

      <view class="card">
        <text class="t-cap">应结算</text>
        <text class="t-num mt4">¥{{ formatCents(pending.summary.netCents) }}</text>
        <view class="divider"></view>
        <view class="kv"><text class="kv-key">客户</text><text class="kv-val">{{ pending.summary.customerId ? pending.summary.customerId : '散客' }}</text></view>
        <view class="kv"><text class="kv-key">物料</text><text class="kv-val">{{ pending.summary.lines.length }} 条</text></view>
        <view class="kv"><text class="kv-key">请求号</text><text class="kv-val">{{ pending.requestKey }}</text></view>
      </view>

      <view class="card card-flat">
        <view v-for="(line, index) in pending.summary.lines" :key="index" class="list-item row-between" :class="{ 'list-item-last': index === pending.summary.lines.length - 1 }">
          <text class="t-meta grow">商品 {{ line.productId }} · {{ line.quantity }} {{ line.unit }}</text>
          <text class="t-strong">¥{{ formatCents(line.lineCents) }}</text>
        </view>
      </view>

      <button class="btn btn-primary" :disabled="busy" :loading="busy" @click="act('read')">核对原请求结果</button>

      <view v-if="result?.status === 'COMPLETED'" class="card card-ok mt12">
        <view class="row"><AppIcon name="check" :size="18" class="t-ok mr8" /><text class="t-sub">原请求已成交</text></view>
        <text class="t-code mt4">原订单编号 {{ result.orderId }}</text>
        <checkbox-group @change="confirmed = $event.detail.value.includes('confirmed')">
          <label class="check-row"><checkbox class="check-box" value="confirmed" :checked="confirmed" :disabled="busy" /><text>我已核对原订单已成交</text></label>
        </checkbox-group>
        <button class="btn btn-primary btn-sm" :disabled="busy || !confirmed" @click="act('finish')">结束已成交单据</button>
      </view>

      <view v-else class="card mt12">
        <text class="t-body">确需重新开单时，先让后台结束原请求。若原请求已成交，会返回原订单。</text>
        <checkbox-group @change="confirmed = $event.detail.value.includes('confirmed')">
          <label class="check-row"><checkbox class="check-box" value="confirmed" :checked="confirmed" :disabled="busy" /><text>我已核对实际交款；结束请求不等于退款</text></label>
        </checkbox-group>
        <button class="btn btn-ghost btn-sm" :disabled="busy || !confirmed" @click="act('cancel')">结束原未成交请求</button>
      </view>
    </template>

    <view v-if="message" class="alert alert-info" role="status">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <text>{{ message }}</text>
    </view>

    <text class="t-cap mt12">只核对本机保存的原请求，不会创建新订单。换手机或清除数据后的缺失记录，请联系门店人工核查。</text>
  </view>
</template>

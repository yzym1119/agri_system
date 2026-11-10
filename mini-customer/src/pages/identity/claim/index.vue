<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import type { CustomerEnrollment } from '@agri/mobile-contracts/customer-auth';
import { customerAuth, customerEnrollment, serviceConfigured, errorText } from '../../../services/runtime';
import { largeText } from '../../../services/preferences';
const snapshot = ref(customerAuth.current()), hasPending = ref(false), outcome = ref<CustomerEnrollment | null>(null), busy = ref(false), error = ref('');
let visible = false, generation = 0;
function hide() { visible = false; ++generation; busy.value = false; }
onHide(hide); onUnload(hide);
async function act(submit: boolean) {
  if (!visible || busy.value || !serviceConfigured) return;
  const ticket = generation, epoch = customerAuth.current().epoch; busy.value = true; error.value = '';
  try {
    const reply = submit ? await customerEnrollment.submit() : await customerEnrollment.query();
    if (!visible || ticket !== generation || customerAuth.current().epoch !== (reply?.epoch ?? epoch)) return;
    const result = reply?.result ?? null;
    outcome.value = result;
    snapshot.value = customerAuth.current(); hasPending.value = customerEnrollment.current() !== null;
  } catch (failure) {
    if (failure instanceof Error && failure.message === 'STALE_SESSION') return;
    if (visible && ticket === generation) {
      error.value = errorText(failure); snapshot.value = customerAuth.current();
      try { hasPending.value = customerEnrollment.current() !== null; } catch { hasPending.value = true; }
    }
  } finally { if (visible && ticket === generation) busy.value = false; }
}
onShow(async () => {
  visible = true; ++generation; busy.value = false; error.value = ''; outcome.value = null; snapshot.value = customerAuth.current();
  if (!serviceConfigured) { error.value = '门店服务暂未连接，请稍后再试。'; return; }
  try { hasPending.value = customerEnrollment.current() !== null; if (hasPending.value && snapshot.value.kind !== 'GUEST') await act(false); }
  catch (failure) { hasPending.value = true; error.value = errorText(failure); }
});
function query() { return act(false); }
function submit() { return act(true); }
function login() { uni.navigateTo({ url: '/pages/login/index' }); }
function mine() { uni.reLaunch({ url: '/pages/profile/index' }); }
</script>
<template>
  <view class="customer-page" :class="{ 'large-text': largeText }">
    <ReadingHeader title="核对门店档案" />
    <view class="title"><text>接上门店里</text><text class="title-line">您自己的档案。</text></view>
    <text class="paragraph secondary">手机号相同也不直接认领旧档案。已有档案时，须请门店核验；没有匹配档案时，才开通新的客户资料。</text>
    <view v-if="error" class="notice" role="alert">{{ error }}</view>
    <text v-if="busy" class="loading">正在核对原申请，请稍候…</text>
    <view v-if="outcome" class="panel">
      <template v-if="outcome.status === 'CREATED' || outcome.status === 'LINKED'"><text class="panel-title">档案已确认，请重新微信登录</text><text>重新登录后，系统会读取您当前关联的本人资料。</text></template>
      <template v-else-if="outcome.status === 'REVIEW_REQUIRED'"><text class="panel-title">需要门店核验</text><text>请携带这次使用的微信到门店，或按门店原联系方式核实。等待期间不会开放旧档案的资金和订单。</text></template>
      <template v-else-if="outcome.status === 'REJECTED'"><text class="panel-title">这次申请未通过核验</text><text>请联系门店确认原因，再重新微信登录申请。</text></template>
      <template v-else><text class="panel-title">申请结果尚未确认</text><text>请保留原申请继续核对，不要重复创建。您可以先查询结果，再重试这次申请。</text></template>
    </view>
    <button v-if="snapshot.kind === 'GUEST'" class="button" hover-class="button-pressed" @tap="login">微信登录后继续</button>
    <template v-else>
      <view v-if="snapshot.kind === 'CUSTOMER' && !hasPending && !outcome" class="panel"><text class="panel-title">本机没有待核对申请</text><text>您已登录，可以返回查看本人资料。</text></view>
      <button v-if="hasPending" class="button" hover-class="button-pressed" :disabled="busy" @tap="query">核对原申请结果</button>
      <button v-if="snapshot.kind === 'ONBOARDING' && snapshot.state === 'PHONE_VERIFIED' && (!hasPending || outcome?.status === 'UNKNOWN')" class="button" hover-class="button-pressed" :disabled="busy || !serviceConfigured" @tap="submit">{{ hasPending ? '按原申请重试' : '确认核对门店档案' }}</button>
      <button v-if="snapshot.kind === 'ONBOARDING' && snapshot.state === 'PHONE_REQUIRED' && (!hasPending || outcome?.status === 'UNKNOWN')" class="button" hover-class="button-pressed" @tap="login">先授权微信手机号</button>
      <button v-if="snapshot.kind === 'CUSTOMER'" class="button button-secondary" hover-class="button-pressed" @tap="mine">返回我的资料</button>
    </template>
    <text class="help">未确认的申请会保留。请使用同一个微信继续核对，不要重复申请，也不要清理小程序数据。</text>
  </view>
</template>

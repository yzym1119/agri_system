<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { customerAuth, serviceConfigured } from '../../services/runtime';
import { largeText } from '../../services/preferences';
const snapshot = ref(customerAuth.current());
onShow(() => { snapshot.value = customerAuth.current(); });
function openAccount() {
  if (snapshot.value.kind === 'CUSTOMER') uni.reLaunch({ url: '/pages/profile/index' });
  else uni.navigateTo({ url: '/pages/login/index' });
}
</script>
<template>
  <view class="customer-page with-navigation" :class="{ 'large-text': largeText }">
    <CustomerNavigation active="home" />
    <ReadingHeader title="门店服务" />
    <view class="title"><text>自己的资料，</text><text class="title-line">一处查看。</text></view>
    <text class="paragraph secondary">使用微信登录，核对您在门店的客户档案。不用记账号和密码。</text>
    <button class="button" hover-class="button-pressed" @tap="openAccount">{{ snapshot.kind === 'CUSTOMER' ? '查看我的资料' : '微信登录' }}</button>
    <view v-if="!serviceConfigured" class="notice">门店服务暂未连接。您可以先了解登录流程，稍后再试。</view>
    <view class="section">
      <text class="panel-title">登录前，您可以知道</text>
      <view class="row"><text class="row-value">授权手机号，用于核对门店档案。</text><text class="help">有旧档案时，由门店核验后关联，不按同号自动认领。</text></view>
      <view class="row"><text class="row-value">自己的信息，仅本人查看。</text><text class="help">登录不会自动合并两个档案里的余额、欠款或订单。</text></view>
    </view>
    <text class="help">需要农技咨询或办理其他业务，请联系门店。进入首页不会自动开通客户档案。</text>
  </view>
</template>

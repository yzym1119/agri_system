<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import type { PublicStore } from '@agri/mobile-contracts/customer-catalog';
import { customerAuth, publicCatalog, serviceConfigured } from '../../services/runtime';
import { largeText } from '../../services/preferences';
const snapshot = ref(customerAuth.current());
const stores=ref<PublicStore[]>([]),storeLoading=ref(false),storeError=ref(''),nextAfter=ref<string|null>(null);
let visible=false,generation=0;
async function loadStores(more=false){
  if(!visible||!serviceConfigured||more&&storeLoading.value)return;
  const ticket=++generation;storeLoading.value=true;storeError.value='';if(!more){stores.value=[];nextAfter.value=null;}
  try{const page=await publicCatalog.stores(more?nextAfter.value??undefined:undefined);if(visible&&ticket===generation){stores.value=more?[...stores.value,...page.rows]:page.rows;nextAfter.value=page.nextAfter;}}
  catch{if(visible&&ticket===generation)storeError.value='暂时没有取得门店资料，请稍后重试。';}
  finally{if(visible&&ticket===generation)storeLoading.value=false;}
}
onShow(async()=>{visible=true;snapshot.value=customerAuth.current();await loadStores();});
function hide(){visible=false;++generation;storeLoading.value=false;}onHide(hide);onUnload(hide);
function openCatalog(){uni.navigateTo({url:'/pages/catalog/list/index'});}
function openKnowledge(){uni.navigateTo({url:'/pages/knowledge/list/index'});}
function dialable(phone:string){const n=phone.replace(/[() -]/g,'');return /^\+?[0-9]{7,20}$/.test(n)?n:'';}
function callStore(store:PublicStore){const phoneNumber=dialable(store.phone);if(!phoneNumber)return;const ticket=generation;uni.makePhoneCall({phoneNumber,fail:()=>{if(visible&&ticket===generation)storeError.value='未能拨打电话，您也可以先查看门店地址。';}});}
function copyAddress(store:PublicStore){const ticket=generation;uni.setClipboardData({data:store.address,fail:()=>{if(visible&&ticket===generation)storeError.value='地址没有复制成功，请重试。';}});}
function openAccount() {
  if (snapshot.value.kind === 'CUSTOMER') uni.reLaunch({ url: '/pages/profile/index' });
  else uni.navigateTo({ url: '/pages/login/index' });
}
</script>
<template>
  <view class="customer-page with-navigation" :class="{ 'large-text': largeText }">
    <CustomerNavigation active="home" />
    <ReadingHeader title="门店服务" />
    <view class="title"><text>门店资料随手查，</text><text class="title-line">有事直接联系。</text></view>
    <text class="paragraph secondary">商品资料可以直接查看，不用先登录。种植情况拿不准时，请联系门店。</text>
    <button class="button" hover-class="button-pressed" @tap="openCatalog">查询商品</button>
    <view v-if="!serviceConfigured" class="notice">门店服务暂未连接。您可以先了解登录流程，稍后再试。</view>
    <view class="section">
      <text class="panel-title">联系门店</text>
      <text v-if="storeLoading" class="loading">正在读取门店资料…</text>
      <view v-if="storeError" class="notice" role="alert">{{ storeError }}<button class="button button-secondary" hover-class="button-pressed" @tap="loadStores(false)">重新读取</button></view>
      <text v-if="!storeLoading && !storeError && !stores.length" class="paragraph secondary">{{ serviceConfigured ? '门店尚未公开联系资料，请稍后再看。' : '门店联系资料暂时不可用。' }}</text>
      <view v-for="store in stores" :key="store.storeId" class="panel"><text class="panel-title">{{ store.name }}</text><text class="paragraph">{{ store.phone }}</text><text class="paragraph secondary">{{ store.address }}</text><button v-if="dialable(store.phone)" class="button" hover-class="button-pressed" @tap="callStore(store)">拨打门店电话</button><button class="button button-secondary" hover-class="button-pressed" @tap="copyAddress(store)">复制地址</button></view>
      <button v-if="nextAfter" class="button button-secondary" hover-class="button-pressed" :disabled="storeLoading" @tap="loadStores(true)">更多门店</button>
    </view>
    <button class="button button-secondary" hover-class="button-pressed" @tap="openKnowledge">查看门店已审核资料</button>
    <button class="button button-secondary" hover-class="button-pressed" @tap="openAccount">{{ snapshot.kind === 'CUSTOMER' ? '查看我的资料' : '登录核对本人档案' }}</button>
    <text class="help">种植问题可在“问农技”提交；订单和账款请联系门店核对。进入首页不会自动开通客户档案。</text>
  </view>
</template>

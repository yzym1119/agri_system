<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { ReceivingOrders,PendingReceipt } from '@agri/mobile-contracts/receipt';
import { staffAuth } from '../../../services/auth';
import { receiptRecovery,readReceivingOrders } from '../../../services/receipt';
const orders=ref<ReceivingOrders['orders']>([]),pending=ref<PendingReceipt|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
const keyword=ref(''),page=ref(1),hasMore=ref(false),loaded=ref(false);
let generation=0,visible=false,actorEpoch=-1,lastQuery='';
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
  visible=true;const ticket=++generation;allowed.value=false;loading.value=true;loaded.value=false;orders.value=[];pending.value=null;message.value='';
  try{
    await staffAuth.refresh();if(!visible||ticket!==generation)return;
    const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
    allowed.value=actor.permissions.includes('*')||actor.permissions.includes('purchase:inbound');
    if(!allowed.value){message.value='当前账号没有采购收货权限，请联系老板。';return;}
    pending.value=receiptRecovery.restore();if(!pending.value)await load(1,lastQuery);
  }catch{
    if(!visible||ticket!==generation)return;
    if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}
    message.value='本机收货记录读取异常，暂停新的收货。请联系老板核对，不要清除数据。';allowed.value=false;
  }finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;orders.value=[];pending.value=null;});
async function load(next:number,query=lastQuery){
  if(!current()||pending.value)return;const ticket=++generation;loading.value=true;loaded.value=false;orders.value=[];message.value='';
  try{const result=await readReceivingOrders(next,query,actorEpoch);if(!current(ticket))return;
    loaded.value=true;orders.value=result.orders;page.value=result.page;hasMore.value=result.hasMore;lastQuery=query;
    if(!result.orders.length)message.value='当前没有待收货采购单，或没有匹配到这个单号。';
  }catch{if(visible&&ticket===generation){if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='待收采购单读取未完成，请重试。';}}
  finally{if(ticket===generation)loading.value=false;}
}
const search=()=>load(1,keyword.value.trim());
function open(id:string){if(!current()||loading.value)return;uni.navigateTo({url:'/pages/receipt/detail/index?orderId='+encodeURIComponent(pending.value?.snapshot.purchaseOrderId??id)});}
</script>
<template>
  <view class="page">
    <view hover-class="alert-hover" hover-stay-time="80" v-if="pending" class="alert" @click="open(pending.snapshot.purchaseOrderId)">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <view class="grow">
        <text class="t-strong">有 1 笔收货结果待核对</text>
        <text class="t-meta">采购单 {{ pending.snapshot.orderNo }} · 点此先核对</text>
      </view>
      <AppIcon name="chevron" :size="14" class="chev" />
    </view>

    <view v-if="loading" class="card row"><view class="spinner spinner-primary"></view><text class="t-meta">正在读取待收采购单…</text></view>
    <template v-else-if="allowed && !pending">
      <view class="row block">
        <view class="grow search-wrap">
          <AppIcon name="search" :size="16" class="search-ic" />
          <input v-model="keyword" class="input input-search" maxlength="100" placeholder="采购单号，留空看全部" confirm-type="search" @confirm="search" />
        </view>
        <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-sm btn-auto ml8" :disabled="loading" @click="search">查询</button>
      </view>

      <view hover-class="tap-hover" hover-stay-time="80" v-for="order in orders" :key="order.id" class="card tap" @click="open(order.id)">
        <view class="row-between">
          <text class="t-sub grow">{{ order.orderNo }}</text>
          <AppIcon name="chevron" :size="14" class="chev" />
        </view>
        <view class="row-between mt4">
          <text class="t-meta">{{ order.supplierName ?? '供应商名称待核对' }}</text>
          <text class="t-cap t-primary">{{ pending ? '先核对上次收货' : '登记本次到货' }}</text>
        </view>
      </view>

      <view v-if="loaded && !orders.length" class="card empty">
        <view class="empty-icon"><AppIcon name="doc" :size="22" /></view>
        <text class="empty-text">{{ message || '当前没有待收货采购单。' }}</text>
      </view>

      <view v-if="page > 1 || hasMore" class="row-between mt4">
        <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-sm btn-auto" :disabled="loading || page === 1" @click="load(page - 1)">上一页</button>
        <text class="t-meta">第 {{ page }} 页</text>
        <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-sm btn-auto" :disabled="loading || !hasMore" @click="load(page + 1)">下一页</button>
      </view>
    </template>
    <view v-else-if="!allowed" class="empty">
      <view class="empty-icon"><AppIcon name="alert" :size="22" /></view>
      <text class="empty-text">{{ message || '当前账号没有采购收货权限，请联系老板。' }}</text>
    </view>
    <text v-if="message && (orders.length || !loaded)" class="t-cap" role="status">{{ message }}</text>
  </view>
</template>

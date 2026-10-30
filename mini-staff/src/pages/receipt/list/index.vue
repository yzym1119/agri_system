<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { ReceivingOrders,PendingReceipt } from '@agri/mobile-contracts/receipt';
import { staffAuth } from '../../../services/auth';
import { receiptRecovery,readReceivingOrders } from '../../../services/receipt';
const orders=ref<ReceivingOrders['orders']>([]),pending=ref<PendingReceipt|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
const keyword=ref(''),page=ref(1),hasMore=ref(false);
let generation=0,visible=false,actorEpoch=-1,lastQuery='';
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
  visible=true;const ticket=++generation;allowed.value=false;loading.value=true;orders.value=[];pending.value=null;message.value='';
  try{
    await staffAuth.refresh();if(!visible||ticket!==generation)return;
    const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
    allowed.value=actor.permissions.includes('*')||actor.permissions.includes('purchase:inbound');
    if(!allowed.value){message.value='当前账号没有采购收货权限，请联系老板。';return;}
    pending.value=receiptRecovery.restore();await load(1,lastQuery);
  }catch{
    if(!visible||ticket!==generation)return;
    if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}
    message.value='本机收货记录读取异常，暂停新的收货。请联系老板核对，不要清除数据。';allowed.value=false;
  }finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;orders.value=[];pending.value=null;});
async function load(next:number,query=lastQuery){
  if(!current())return;const ticket=++generation;loading.value=true;orders.value=[];message.value='';
  try{const result=await readReceivingOrders(next,query,actorEpoch);if(!current(ticket))return;
    orders.value=result.orders;page.value=result.page;hasMore.value=result.hasMore;lastQuery=query;
    if(!result.orders.length)message.value='当前没有待收货采购单，或没有匹配到这个单号。';
  }catch{if(visible&&ticket===generation){if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='待收采购单读取未完成，请重试。';}}
  finally{if(ticket===generation)loading.value=false;}
}
const search=()=>load(1,keyword.value.trim());
function open(id:string){if(!current()||loading.value)return;uni.navigateTo({url:'/pages/receipt/detail/index?orderId='+encodeURIComponent(pending.value?.snapshot.purchaseOrderId??id)});}
</script>
<template>
 <view class="page">
  <text class="title">采购收货</text><text class="intro">按本次实际到货登记，确认后增加本店库存。</text>
  <text v-if="loading" class="intro">正在读取待收采购单…</text>
  <template v-else-if="allowed">
   <view v-if="pending" class="panel"><text class="subtitle">先核对上次收货</text><text class="code">采购单：{{ pending.snapshot.orderNo }}</text><text class="intro">本机还有原收货待核对，请先处理这笔。</text><button class="primary" @click="open(pending.snapshot.purchaseOrderId)">核对原收货</button></view>
   <view class="panel"><text class="label">采购单号</text><input v-model="keyword" maxlength="100" placeholder="输入单号，也可以留空查看全部" confirm-type="search" @confirm="search" /><button :disabled="loading" @click="search">查询待收采购单</button></view>
   <view v-for="order in orders" :key="order.id" class="panel"><text class="subtitle">{{ order.orderNo }}</text><text class="intro">{{ order.supplierName ?? '供应商名称待核对' }}</text><button @click="open(order.id)">{{ pending ? '先核对上次收货' : '核对并登记到货' }}</button></view>
   <view v-if="page>1 || hasMore" class="pagination"><button :disabled="loading || page===1" @click="load(page-1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="loading || !hasMore" @click="load(page+1)">下一页</button></view>
  </template>
  <text v-if="message" class="intro" role="status">{{ message }}</text>
 </view>
</template>
<style scoped>
.page{padding:24px 20px;max-width:600px;margin:auto}.title{display:block;font-size:24px;font-weight:600}.intro{display:block;color:#4b5563;line-height:1.7;margin:12px 0}.panel{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px;margin:20px 0}.subtitle{display:block;font-size:20px;font-weight:600;line-height:1.6}.label,.code{display:block;line-height:1.7;word-break:break-all}input{box-sizing:border-box;width:100%;height:48px;padding:0 12px;background:#f5f6f8;border:1px solid #d1d5db;border-radius:6px;font-size:16px}button{min-height:48px;margin:16px 0;color:#1d5d73;background:#fff;border:1px solid #1d5d73}.primary{color:#fff;background:#1d5d73}.pagination{display:flex;gap:12px;align-items:center;justify-content:space-between}
</style>

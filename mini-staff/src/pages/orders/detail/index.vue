<script setup lang="ts">
import { ref } from 'vue';
import { onLoad,onShow,onHide } from '@dcloudio/uni-app';
import { ownOrderState,ownDeliveryLabel,type OwnOrderDetail } from '@agri/mobile-contracts/staff-orders';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import { staffAuth } from '../../../services/auth';
import { readOwnOrder } from '../../../services/orders';
import { money,preciseDecimal } from '../../../utils/money';
const detail=ref<OwnOrderDetail|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
let orderId='',visible=false,generation=0,actorEpoch=-1;
const current=(ticket=generation)=>visible&&allowed.value&&generation===ticket&&staffAuth.current()?.epoch===actorEpoch;
onLoad(options=>{orderId=isPositiveLongId(options?.orderId)?options.orderId:'';});
onShow(async()=>{
 visible=true;const ticket=++generation;loading.value=true;allowed.value=false;detail.value=null;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:own:query');
  if(!allowed.value){message.value='当前账号没有本人订单查询权限，请联系店长。';return;}
  if(!orderId){message.value='订单编号无效，请返回订单列表重新选择。';return;}await reload();
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;detail.value=null;message.value='';});
async function reload(){
 if(!current()||!orderId)return;const ticket=++generation;loading.value=true;detail.value=null;message.value='';
 try{const result=await readOwnOrder(orderId,actorEpoch);if(current(ticket))detail.value=result;}
 catch{if(visible&&ticket===generation&&(!staffAuth.current()||staffAuth.current()?.epoch===actorEpoch)){
  if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='订单读取未完成：请重试，或联系店长核对订单归属与权限。';
 }}finally{if(ticket===generation)loading.value=false;}
}
function copyOrder(){if(current()&&detail.value)uni.setClipboardData({data:detail.value.order.orderNo});}
const amount=(v:string|null)=>v===null?'待核对':'¥'+money(v);
</script>
<template>
 <view class="page">
  <view v-if="loading" class="card row"><view class="spinner spinner-primary"></view><text class="t-meta">正在读取原订单…</text></view>
  <view v-if="message" class="alert alert-danger" role="status"><text>{{ message }}</text></view>
  <template v-if="detail && allowed">
   <view class="card">
    <view class="row-between"><text class="t-sub grow">{{ detail.order.customerLabel }}</text><text class="chip chip-line">{{ ownOrderState(detail.order.state) }}</text></view>
    <text class="t-code mt8">{{ detail.order.orderNo }}</text><text class="t-meta mt4">{{ detail.order.createdAt?.replace('T',' ') || '下单时间待核对' }}</text>
    <button class="btn btn-quiet btn-sm mt8" @click="copyOrder">复制订单号</button>
   </view>
   <view class="section"><text class="section-title">账款情况</text><text class="section-note">原净额含赊销；冲欠、还款和退钱分别记录。</text></view>
   <view class="card">
    <view class="kv"><text class="kv-key">原净应结算</text><text class="kv-val t-strong">{{ amount(detail.order.finance.payAmount) }}</text></view>
    <view class="kv"><text class="kv-key">开单已收</text><text class="kv-val">{{ amount(detail.order.finance.receivedAmount) }}</text></view>
    <view class="kv"><text class="kv-key">原赊销</text><text class="kv-val">{{ amount(detail.order.finance.creditAmount) }}</text></view>
    <view class="kv"><text class="kv-key">累计实还</text><text class="kv-val">{{ amount(detail.order.finance.repaidAmount) }}</text></view>
    <view class="kv"><text class="kv-key">退货冲欠</text><text class="kv-val">{{ amount(detail.order.finance.creditReductionAmount) }}</text></view>
    <view class="kv"><text class="kv-key">累计退已收</text><text class="kv-val">{{ amount(detail.order.finance.cashRefundedAmount) }}</text></view>
    <view class="divider"></view><view class="kv"><text class="kv-key">当前欠款</text><text class="kv-val t-strong">{{ amount(detail.order.finance.remainingCreditAmount) }}</text></view>
   </view>
   <view class="section"><text class="section-title">原成交商品</text><text class="section-note">套餐展示原成交快照及实际物料，不按当前套餐重新拆分。</text></view>
   <view class="card">
    <view v-for="(item,index) in detail.items" :key="item.itemId" class="list-item" :class="{'list-item-last':index===detail.items.length-1}">
     <template v-if="item.packageGroupNo !== null">
      <text class="t-body t-strong package-name">{{ item.packageName }}</text>
      <text class="t-meta mt4">第{{ item.packageGroupNo }}组 · {{ item.packageQuantity }}套 · {{ amount(item.packageUnitPrice) }}/套</text>
     </template>
     <view class="row-between mt8"><text class="t-sub grow">{{ item.productName || '商品资料待核对' }}</text><text class="t-strong">{{ amount(item.totalPrice) }}</text></view>
     <text class="t-meta mt4">原售 {{ item.quantity === null ? '待核对' : preciseDecimal(item.quantity) }} {{ item.unit || '单位待核对' }}</text>
     <text class="t-cap mt4">已退 {{ item.refundedQuantity === null ? '待核对' : preciseDecimal(item.refundedQuantity) }} {{ item.unit || '' }} · 已退金额 {{ amount(item.refundedAmount) }}</text>
    </view>
    <text v-if="!detail.items.length" class="t-meta">原单未返回商品明细，请联系店长核对。</text>
   </view>
   <view class="section"><text class="section-title">配送记录</text></view>
   <view class="card"><text class="t-body">{{ ownDeliveryLabel(detail.order.deliveryType,detail.order.deliveryStatus) }}</text><text class="t-cap mt8">本页仅供查询。提货、配送任务请从工作台进入对应功能。</text></view>
  </template>
  <button v-if="allowed" class="btn btn-ghost" :disabled="loading" @click="reload">重新读取原单</button>
 </view>
</template>
<style scoped>
.package-name { display: block; overflow-wrap: anywhere; word-break: break-all; }
</style>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { PickupOrder,PickupOrderLine,PickupOrders } from '@agri/mobile-contracts/pickup';
import { staffAuth } from '../../../services/auth';
import { readPickupOrders } from '../../../services/pickup';
const orders=ref<PickupOrder[]>([]),state=ref<PickupOrders['state']>('PENDING'),nextCursor=ref<string|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
let generation=0,actorEpoch=-1,visible=false;
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
 visible=true;const ticket=++generation;loading.value=true;allowed.value=false;orders.value=[];nextCursor.value=null;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;
  const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:pickup:verify');
  if(!allowed.value){message.value='当前账号没有提货确认权限，请联系店长。';return;}await load(null);
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;orders.value=[];nextCursor.value=null;});
async function load(after:string|null){
 if(!current())return;const ticket=++generation,filter=state.value;loading.value=true;message.value='';if(after===null){orders.value=[];nextCursor.value=null;}
 try{const result=await readPickupOrders(filter,after,actorEpoch);if(!current(ticket))return;
  // 整单卡按原订单合并；同一订单的旧卡被新读取替换，避免项数停留在上一次状态。
  const merged=after===null?result.orders:[...orders.value.filter(old=>!result.orders.some(fresh=>fresh.orderId===old.orderId)),...result.orders];
  orders.value=merged;nextCursor.value=result.nextCursor;
  if(!merged.length)message.value=filter==='PENDING'?'当前没有本店待交任务。':'当前没有本店已提货记录。';
 }catch{if(visible&&ticket===generation){if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='任务读取未完成，请重新查询或联系店长确认权限。';}}
 finally{if(ticket===generation)loading.value=false;}
}
async function chooseState(value:PickupOrders['state']){if(!current())return;state.value=value;await load(null);}
const more=()=>current()&&!loading.value&&nextCursor.value?load(nextCursor.value):undefined;
function openOrder(order:PickupOrder){if(current()&&!loading.value&&orders.value.includes(order))uni.navigateTo({url:'/pages/pickup/order/index?orderId='+encodeURIComponent(order.orderId)});}
function open(line:PickupOrderLine){if(!current()||loading.value||!orders.value.some(order=>order.lines.includes(line)))return;
 uni.navigateTo({url:'/pages/pickup/detail/index?requestId='+encodeURIComponent(line.requestId)});}
const lineText=(line:PickupOrderLine)=>line.status==='FULFILLED'?'已交货':line.canConfirm?(line.remainingQuantity??'数量待核对')+' '+(line.unit??''):'待核对';
</script>
<template>
 <view class="page">
  <view class="tabs">
   <view hover-class="tab-hover" hover-stay-time="80" class="tab" :class="{ 'tab-on': state === 'PENDING' }" @click="chooseState('PENDING')">待交任务</view>
   <view hover-class="tab-hover" hover-stay-time="80" class="tab" :class="{ 'tab-on': state === 'FULFILLED' }" @click="chooseState('FULFILLED')">已提货</view>
  </view>
  <view v-if="loading" class="card row"><view class="spinner spinner-primary"></view><text class="t-meta">正在读取本店提货任务…</text></view>
  <template v-else-if="allowed">
   <view v-for="order in orders" :key="order.orderId" class="card">
    <view class="row-between">
     <text class="t-sub grow">{{ order.orderNo || '订单号待核对' }}</text>
     <text class="chip chip-sm" :class="order.pendingCount ? 'chip-warn' : 'chip-ok'">{{ order.pendingCount ? '待交 ' + order.pendingCount + ' 项' : '整单已交完' }}</text>
    </view>
    <text class="t-meta mt4">客户 {{ order.customerLabel || '散客' }} · 待交 {{ order.pendingCount }} 项 · 已交 {{ order.fulfilledCount }} 项</text>
    <button class="btn btn-primary btn-sm mt12" @click="openOrder(order)">打开整单备货清单</button>
    <view v-for="line in order.lines" :key="line.requestId" hover-class="tap-hover" hover-stay-time="80" class="line tap" :class="{ 'line-done': line.status === 'FULFILLED' }" @click="open(line)">
     <view class="grow">
      <text class="t-body">{{ line.productName || '商品资料待核对' }}</text>
      <text class="t-cap mt4">{{ lineText(line) }}</text>
      <text v-if="!line.canConfirm && line.blockReason" class="t-cap">{{ line.blockReason }}</text>
     </view>
     <text class="chip chip-sm" :class="line.status === 'FULFILLED' ? 'chip-ok' : line.canConfirm ? 'chip-warn' : 'chip-line'">{{ line.status === 'FULFILLED' ? '已交' : line.canConfirm ? '待交' : '待核' }}</text>
     <AppIcon name="chevron" :size="14" class="chev" />
    </view>
   </view>
   <view v-if="!orders.length" class="card empty">
    <view class="empty-icon"><AppIcon name="box" :size="22" /></view>
    <text class="empty-text">{{ message || '当前没有任务。' }}</text>
   </view>
   <button hover-class="btn-hover" hover-stay-time="60" v-if="nextCursor" class="btn btn-ghost" :disabled="loading" @click="more">读取更多订单</button>
   <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-quiet" :disabled="loading" @click="load(null)">重新查询</button>
   <text class="t-cap mt8">按原订单聚合展示；点具体物料进入原请求逐批核对与交付。</text>
  </template>
  <view v-else class="empty">
   <view class="empty-icon"><AppIcon name="alert" :size="22" /></view>
   <text class="empty-text">{{ message || '当前账号没有该页面的访问权限，请联系店长。' }}</text>
  </view>
  <text v-if="message && orders.length" class="t-cap" role="status">{{ message }}</text>
 </view>
</template>

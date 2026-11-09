<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import { ownOrderPath,ownOrderState,type OwnOrder,type OwnOrderFilter } from '@agri/mobile-contracts/staff-orders';
import { staffAuth } from '../../../services/auth';
import { readOwnOrders } from '../../../services/orders';
import { money } from '../../../utils/money';
const orders=ref<OwnOrder[]>([]),from=ref(''),to=ref(''),state=ref<number|undefined>(undefined);
const nextCursor=ref<string|null>(null),loading=ref(true),allowed=ref(false),loaded=ref(false),message=ref('');
const states=['全部状态',...Array.from({length:9},(_,n)=>ownOrderState(n+1))];
let visible=false,generation=0,actorEpoch=-1;
const current=(ticket=generation)=>visible&&allowed.value&&generation===ticket&&staffAuth.current()?.epoch===actorEpoch;
const filters=():OwnOrderFilter=>({from:from.value||undefined,to:to.value||undefined,state:state.value});
const filterKey=()=>JSON.stringify(filters());
onShow(async()=>{
 visible=true;const ticket=++generation;allowed.value=false;loading.value=true;loaded.value=false;orders.value=[];nextCursor.value=null;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:own:query');
  if(!allowed.value){message.value='当前账号没有本人订单查询权限，请联系店长。';return;}await search();
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;orders.value=[];nextCursor.value=null;message.value='';});
async function load(after:string|null){
 if(!current())return;const ticket=++generation,key=filterKey(),query={...filters(),...(after?{after}:{})};loading.value=true;message.value='';
 if(after===null){orders.value=[];nextCursor.value=null;loaded.value=false;}
 try{ownOrderPath(query);const result=await readOwnOrders(query,actorEpoch);if(!current(ticket)||key!==filterKey())return;
  orders.value=after===null?result.orders:[...orders.value,...result.orders.filter(o=>!orders.value.some(old=>old.orderId===o.orderId))];nextCursor.value=result.nextCursor;loaded.value=true;
 }catch(error){if(visible&&ticket===generation&&(!staffAuth.current()||staffAuth.current()?.epoch===actorEpoch)){
  if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value=error instanceof Error&&error.message.includes('查询日期')?error.message:'订单读取未完成，请重试；也可联系店长核对权限。';
 }}finally{if(ticket===generation)loading.value=false;}
}
const search=()=>load(null);
const more=()=>!loading.value&&nextCursor.value?load(nextCursor.value):undefined;
function chooseState(event:{detail:{value:string}}){if(!current())return;const value=Number(event.detail.value);state.value=value===0?undefined:value;void search();}
function changeDate(which:'from'|'to',event:{detail:{value:string}}){if(!current())return;if(which==='from')from.value=event.detail.value;else to.value=event.detail.value;void search();}
function reset(){if(!current())return;from.value='';to.value='';state.value=undefined;void search();}
function open(order:OwnOrder){if(current()&&!loading.value&&orders.value.includes(order))uni.navigateTo({url:'/pages/orders/detail/index?orderId='+encodeURIComponent(order.orderId)});}
const amount=(v:string|null)=>v===null?'待核对':'¥'+money(v);
</script>
<template>
 <view class="page">
  <view v-if="allowed" class="card">
   <text class="t-sub">我开出的订单</text><text class="t-meta mt4">仅当前门店、本人开单。日期按下单时间查询。</text>
   <view class="row mt12">
    <picker class="grow picker" mode="date" :value="from" @change="changeDate('from',$event)"><text class="picker-value">{{ from || '开始日期' }}</text></picker>
    <text class="t-cap">至</text>
    <picker class="grow picker" mode="date" :value="to" @change="changeDate('to',$event)"><text class="picker-value">{{ to || '结束日期' }}</text></picker>
   </view>
   <picker class="picker mt8" :range="states" :value="state ?? 0" @change="chooseState"><text class="picker-value">{{ states[state ?? 0] }}</text></picker>
   <view class="row mt8"><button class="btn btn-primary grow" :disabled="loading" @click="search">查询订单</button><button class="btn btn-quiet btn-auto" @click="reset">清除筛选</button></view>
  </view>
  <view v-if="loading" class="card row"><view class="spinner spinner-primary"></view><text class="t-meta">正在读取本人订单…</text></view>
  <view v-if="message" class="alert alert-danger" role="status"><text>{{ message }}</text></view>
  <template v-if="allowed">
   <view v-for="order in orders" :key="order.orderId" class="card tap" hover-class="tap-hover" @click="open(order)">
    <view class="row-between"><text class="t-sub grow">{{ order.customerLabel }}</text><text class="chip chip-line">{{ ownOrderState(order.state) }}</text></view>
    <text class="t-code mt8">{{ order.orderNo }}</text><text class="t-cap mt4">{{ order.createdAt?.replace('T',' ') || '下单时间待核对' }}</text>
    <view class="kv mt8"><text class="kv-key">原净应结算</text><text class="kv-val t-strong">{{ amount(order.finance.payAmount) }}</text></view>
    <view class="kv"><text class="kv-key">当前欠款</text><text class="kv-val">{{ amount(order.finance.remainingCreditAmount) }}</text></view>
    <view class="row-between mt8"><text class="t-meta">查看商品、账款与配送记录</text><AppIcon name="chevron" :size="14" /></view>
   </view>
   <view v-if="loaded && !loading && !orders.length && !message" class="card empty"><AppIcon name="doc" :size="24" /><text class="empty-text">没有符合条件的本人订单，可调整日期或清除筛选。</text></view>
   <button v-if="nextCursor" class="btn btn-ghost" :disabled="loading" @click="more">加载更多订单</button>
   <button v-if="message" class="btn btn-ghost" :disabled="loading" @click="search">重新查询</button>
  </template>
 </view>
</template>

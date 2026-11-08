<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { PickupTask,PickupTasks } from '@agri/mobile-contracts/pickup';
import { staffAuth } from '../../../services/auth';
import { readPickupTasks } from '../../../services/pickup';
const tasks=ref<PickupTask[]>([]),state=ref<PickupTasks['state']>('PENDING'),nextCursor=ref<string|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
let generation=0,actorEpoch=-1,visible=false;
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
 visible=true;const ticket=++generation;loading.value=true;allowed.value=false;tasks.value=[];nextCursor.value=null;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;
  const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:pickup:verify');
  if(!allowed.value){message.value='当前账号没有提货确认权限，请联系店长。';return;}await load(null);
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;tasks.value=[];nextCursor.value=null;});
async function load(after:string|null){
 if(!current())return;const ticket=++generation,filter=state.value;loading.value=true;message.value='';if(after===null){tasks.value=[];nextCursor.value=null;}
 try{const result=await readPickupTasks(filter,after,actorEpoch);if(!current(ticket))return;
  tasks.value=after===null?result.tasks:[...tasks.value,...result.tasks.filter(t=>!tasks.value.some(old=>old.requestId===t.requestId))];nextCursor.value=result.nextCursor;
  if(!tasks.value.length)message.value=filter==='PENDING'?'当前没有本店待交任务。':'当前没有本店已提货记录。';
 }catch{if(visible&&ticket===generation){if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='任务读取未完成，请重新查询或联系店长确认权限。';}}
 finally{if(ticket===generation)loading.value=false;}
}
async function chooseState(value:PickupTasks['state']){if(!current())return;state.value=value;await load(null);}
const more=()=>current()&&!loading.value&&nextCursor.value?load(nextCursor.value):undefined;
function open(task:PickupTask){if(!current()||loading.value||!tasks.value.includes(task))return;uni.navigateTo({url:'/pages/pickup/detail/index?requestId='+encodeURIComponent(task.requestId)});}
</script>
<template>
 <view class="page">
  <view class="tabs">
   <view hover-class="tab-hover" hover-stay-time="80" class="tab" :class="{ 'tab-on': state === 'PENDING' }" @click="chooseState('PENDING')">待交任务</view>
   <view hover-class="tab-hover" hover-stay-time="80" class="tab" :class="{ 'tab-on': state === 'FULFILLED' }" @click="chooseState('FULFILLED')">已提货</view>
  </view>
  <view v-if="loading" class="card row"><view class="spinner spinner-primary"></view><text class="t-meta">正在读取本店提货任务…</text></view>
  <template v-else-if="allowed">
   <view hover-class="tap-hover" hover-stay-time="80" v-for="task in tasks" :key="task.requestId" class="card tap" @click="open(task)">
    <view class="row-between">
     <text class="t-sub grow">{{ task.orderNo || '订单号待核对' }}</text>
     <text class="chip" :class="task.status === 'FULFILLED' ? 'chip-ok' : 'chip-warn'">{{ task.status === 'FULFILLED' ? '已提货' : '待交付' }}</text>
    </view>
    <text class="t-meta mt4">{{ task.productName || '商品资料待核对' }}</text>
    <view class="row-between mt8">
     <text class="t-cap">{{ task.status === 'FULFILLED' ? (task.fulfilledAt?.replace('T', ' ') ?? '提货时间未记录') : (task.createdAt?.replace('T', ' ') ?? '申请时间未记录') }}</text>
     <AppIcon name="chevron" :size="14" class="chev" />
    </view>
   </view>
   <view v-if="!tasks.length" class="card empty">
    <view class="empty-icon"><AppIcon name="box" :size="22" /></view>
    <text class="empty-text">{{ message || '当前没有任务。' }}</text>
   </view>
   <button hover-class="btn-hover" hover-stay-time="60" v-if="nextCursor" class="btn btn-ghost" :disabled="loading" @click="more">读取更多任务</button>
   <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-quiet" :disabled="loading" @click="load(null)">重新查询</button>
  </template>
  <view v-else class="empty">
   <view class="empty-icon"><AppIcon name="alert" :size="22" /></view>
   <text class="empty-text">{{ message || '当前账号没有该页面的访问权限，请联系店长。' }}</text>
  </view>
  <text v-if="message && tasks.length" class="t-cap" role="status">{{ message }}</text>
 </view>
</template>

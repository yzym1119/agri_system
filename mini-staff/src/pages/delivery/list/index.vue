<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { AssignedTask,AssignedTasks } from '@agri/mobile-contracts/fulfillment';
import { staffAuth } from '../../../services/auth';
import { readAssignedTasks } from '../../../services/fulfillment';
const tasks=ref<AssignedTask[]>([]),state=ref<AssignedTasks['state']>('PENDING'),nextCursor=ref<string|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
let generation=0,actorEpoch=-1,visible=false;
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
 visible=true;const ticket=++generation;loading.value=true;allowed.value=false;tasks.value=[];nextCursor.value=null;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;
  const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:delivery:assigned');
  if(!allowed.value){message.value='当前账号没有本人配送权限，请联系店长。';return;}await load(null);
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;tasks.value=[];nextCursor.value=null;});
async function load(after:string|null){
 if(!current())return;const ticket=++generation,filter=state.value;loading.value=true;message.value='';if(after===null){tasks.value=[];nextCursor.value=null;}
 try{const result=await readAssignedTasks(filter,after,actorEpoch);if(!current(ticket))return;
  tasks.value=after===null?result.tasks:[...tasks.value,...result.tasks.filter(t=>!tasks.value.some(old=>old.orderId===t.orderId))];nextCursor.value=result.nextCursor;
  if(!tasks.value.length)message.value=filter==='PENDING'?'当前没有分配给你的待交任务。':'当前没有本人的已签收记录。';
 }catch{if(visible&&ticket===generation){if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='任务读取未完成，请重新查询或联系店长确认权限。';}}
 finally{if(ticket===generation)loading.value=false;}
}
async function chooseState(value:AssignedTasks['state']){if(!current())return;state.value=value;await load(null);}
const more=()=>current()&&!loading.value&&nextCursor.value?load(nextCursor.value):undefined;
function open(task:AssignedTask){if(!current()||loading.value||!tasks.value.includes(task))return;uni.navigateTo({url:'/pages/delivery/detail/index?orderId='+encodeURIComponent(task.orderId)});}
</script>
<template>
 <view class="page">
  <view class="tabs">
   <view class="tab" :class="{ 'tab-on': state === 'PENDING' }" @click="chooseState('PENDING')">待交任务</view>
   <view class="tab" :class="{ 'tab-on': state === 'COMPLETED' }" @click="chooseState('COMPLETED')">已签收</view>
  </view>
  <text v-if="loading" class="card t-meta">正在读取本人任务…</text>
  <template v-else-if="allowed">
   <view v-for="task in tasks" :key="task.orderId" class="card tap" @click="open(task)">
    <view class="row-between">
     <text class="t-sub grow">{{ task.orderNo }}</text>
     <text class="chip" :class="task.status === 'COMPLETED' ? 'chip-ok' : 'chip-warn'">{{ task.status === 'COMPLETED' ? '已签收' : '待交付' }}</text>
    </view>
    <text class="t-meta mt4">{{ task.status === 'COMPLETED' ? (task.receivedAt?.replace('T', ' ') ?? '签收时间未记录') : (task.deliveryAt?.replace('T', ' ') ?? '派送时间未记录') }}</text>
    <view class="row-between mt8">
     <text class="t-cap">{{ task.status === 'COMPLETED' ? '查看完成记录' : '核对本次交付' }}</text>
     <AppIcon name="chevron" :size="14" class="chev" />
    </view>
   </view>
   <view v-if="!tasks.length" class="card empty">
    <view class="empty-icon"><AppIcon name="truck" :size="22" /></view>
    <text class="empty-text">{{ message || '当前没有任务。' }}</text>
   </view>
   <button v-if="nextCursor" class="btn btn-ghost" :disabled="loading" @click="more">读取更多任务</button>
   <button class="btn btn-quiet" :disabled="loading" @click="load(null)">重新查询</button>
  </template>
  <text v-if="message && tasks.length" class="t-cap" role="status">{{ message }}</text>
 </view>
</template>

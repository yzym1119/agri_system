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
 <view class="page"><text class="title">我的配送任务</text><text class="intro">这里只列分配给当前员工的门店配送。进入详情后，先核对最新物料和收货人。</text>
  <template v-if="allowed"><view class="filters"><button :class="{primary:state==='PENDING'}" @click="chooseState('PENDING')">待交任务</button><button :class="{primary:state==='COMPLETED'}" @click="chooseState('COMPLETED')">已签收</button></view>
   <view v-for="task in tasks" :key="task.orderId" class="panel"><text class="subtitle">{{ task.orderNo }}</text><text class="intro">{{ task.status==='COMPLETED'?'已签收':'待核对交付' }}</text><text class="intro">{{ task.status==='COMPLETED'?(task.receivedAt?.replace('T',' ')??'签收时间未记录'):(task.deliveryAt?.replace('T',' ')??'派送时间未记录') }}</text><button :disabled="loading" @click="open(task)">{{ task.status==='COMPLETED'?'查看完成记录':'核对本次交付' }}</button></view>
   <button v-if="nextCursor" :disabled="loading" @click="more">读取更多任务</button><button :disabled="loading" @click="load(null)">重新查询</button>
  </template><text v-if="loading" class="intro">正在读取本人任务…</text><text v-if="message" class="intro" role="status">{{ message }}</text>
 </view>
</template>
<style scoped>
.page{padding:24px 20px;max-width:600px;margin:auto}.title{display:block;font-size:24px;font-weight:600}.intro{display:block;color:#4b5563;line-height:1.7;margin:12px 0}.panel{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px;margin:20px 0}.subtitle{display:block;font-size:20px;font-weight:600;line-height:1.6;word-break:break-all}.filters{display:flex;gap:12px}.filters button{flex:1}button{min-height:48px;margin:16px 0;color:#1d5d73;background:#fff;border:1px solid #1d5d73}.primary{color:#fff;background:#1d5d73}
</style>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { PickupTask,PickupTasks } from '@agri/mobile-contracts/pickup';
import { staffAuth } from '../../../services/auth';
import { readPickupTasks } from '../../../services/pickup';
const tasks=ref<PickupTask[]>([]),state=ref<PickupTasks['state']>('PENDING'),nextCursor=ref<string|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
const loaded=ref(false);
let generation=0,actorEpoch=-1,visible=false;
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
 visible=true;const ticket=++generation;loading.value=true;loaded.value=false;allowed.value=false;tasks.value=[];nextCursor.value=null;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;
  const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:pickup:verify');
  if(!allowed.value){message.value='当前账号没有提货确认权限，请联系店长。';return;}await load(null);
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;tasks.value=[];nextCursor.value=null;});
async function load(after:string|null){
 if(!current())return;const ticket=++generation,filter=state.value;loading.value=true;message.value='';if(after===null){loaded.value=false;tasks.value=[];nextCursor.value=null;}
 try{const result=await readPickupTasks(filter,after,actorEpoch);if(!current(ticket))return;
  loaded.value=true;tasks.value=after===null?result.tasks:[...tasks.value,...result.tasks.filter(t=>!tasks.value.some(old=>old.requestId===t.requestId))];nextCursor.value=result.nextCursor;
  if(!tasks.value.length)message.value=filter==='PENDING'?'当前没有本店待交任务。':'当前没有本店已提货记录。';
 }catch{if(visible&&ticket===generation){if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='任务读取未完成，请重新查询或联系店长确认权限。';}}
 finally{if(ticket===generation)loading.value=false;}
}
async function chooseState(value:PickupTasks['state']){if(!current())return;state.value=value;await load(null);}
const more=()=>current()&&!loading.value&&nextCursor.value?load(nextCursor.value):undefined;
function open(task:PickupTask){if(!current()||loading.value||!tasks.value.includes(task))return;uni.navigateTo({url:'/pages/pickup/detail/index?requestId='+encodeURIComponent(task.requestId)});}
</script>
<template>
 <view class="staff-page"><view class="page-heading"><text class="eyebrow">库房交付</text><text class="title">提货任务</text><text class="intro">先找商品和货位，再按批次核对本次交付。</text></view>
  <template v-if="allowed"><view class="filters"><button :class="state==='PENDING'?'filter active':'filter'" @click="chooseState('PENDING')">待核对</button><button :class="state==='FULFILLED'?'filter active':'filter'" @click="chooseState('FULFILLED')">已提货</button></view>
   <view class="section-head"><text class="subtitle">{{ state==='PENDING'?'待处理任务':'完成记录' }}</text><button class="text-button" :disabled="loading" @click="load(null)">刷新</button></view>
   <view v-for="task in tasks" :key="task.requestId" class="staff-panel task-card"><view class="card-head"><text class="task-title">{{ task.productName || '商品名称待核对' }}</text><text class="status-label">{{ task.status==='FULFILLED'?'已提货':'待核对' }}</text></view><text class="order-number">订单 {{ task.orderNo || '订单号待核对' }}</text><text class="muted">{{ task.status==='FULFILLED'?'提货时间：'+(task.fulfilledAt?.replace('T',' ')??'未记录'):'申请时间：'+(task.createdAt?.replace('T',' ')??'未记录') }}</text><button class="staff-secondary" :disabled="loading" @click="open(task)">{{ task.status==='FULFILLED'?'查看完成记录 ›':'核对批次与数量 ›' }}</button></view>
   <view v-if="loaded && !loading && !tasks.length" class="staff-panel empty-panel"><text class="subtitle">{{ state==='PENDING'?'暂时没有待处理任务':'还没有完成记录' }}</text><text class="intro">{{ state==='PENDING'?'可稍后刷新，或联系店长确认任务安排。':'完成交付后，可在这里核对记录。' }}</text></view>
   <button v-if="nextCursor" class="staff-secondary" :disabled="loading" @click="more">加载更多</button>
  </template><text v-if="loading" class="intro" role="status">正在读取任务，请稍候…</text><text v-if="message" class="notice" role="status">{{ message }}</text>
 </view>
</template>
<style scoped>
.page-heading{padding:12px 0 20px}.eyebrow{display:block;color:#1d5d73;font-size:13px;font-weight:600;margin-bottom:10px}.title{display:block;font-size:25px;font-weight:650;line-height:1.45}.intro{display:block;color:#4b5563;font-size:14px;line-height:1.7;margin:12px 0}.subtitle{display:block;font-size:17px;font-weight:600}.filters{display:flex;gap:8px;background:#eaf1f4;padding:5px;border-radius:10px}.filter{flex:1;background:transparent;color:#4b5563;font-size:15px;margin:0;border:0}.filter.active{background:#fff;color:#1d5d73;font-weight:600;border:1px solid #1d5d73}.section-head{display:flex;align-items:center;justify-content:space-between;margin:20px 0 12px}.text-button{background:transparent;color:#1d5d73;font-size:14px;margin:0;padding:0 8px}.task-card{margin:12px 0}.card-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.task-title{display:block;font-size:18px;font-weight:600;line-height:1.5;overflow-wrap:anywhere}.status-label{flex-shrink:0;font-size:12px;color:#1d5d73;background:#eaf1f4;padding:3px 8px;border-radius:5px}.order-number{display:block;color:#4b5563;font-size:13px;line-height:1.7;overflow-wrap:anywhere;margin:8px 0}.muted{display:block;color:#4b5563;font-size:13px;line-height:1.7}.task-card button{font-size:15px;margin:16px 0 0}.notice{display:block;font-size:14px;line-height:1.7;color:#4b5563;margin:16px 0}.empty-panel{margin:16px 0}
</style>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad,onShow,onHide } from '@dcloudio/uni-app';
import { deliveryItemKey,type AssignedReceipt,type DeliveryResult } from '@agri/mobile-contracts/fulfillment';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import { staffAuth } from '../../../services/auth';
import { readAssignedReceipt,confirmAssignedReceipt } from '../../../services/fulfillment';
import { mobilePlatform } from '../../../services/platform';
const preview=ref<AssignedReceipt|null>(null),result=ref<DeliveryResult|null>(null),checkedItems=ref<string[]>([]),receiverConfirmed=ref(false);
const loading=ref(true),busy=ref(false),allowed=ref(false),uncertain=ref(false),message=ref('');
let orderId:string|null=null,generation=0,actorEpoch=-1,visible=false,attemptedKey:string|null=null,scope='',storageKey='';
const stored=(key:string)=>JSON.stringify({version:1,scope,confirmationKey:key});
function restoreAttempt(){
 try{const raw=mobilePlatform.readStorage(storageKey);
  if(uncertain.value){if(!attemptedKey||raw!==stored(attemptedKey))attemptedKey=null;return;}
  if(raw===null)return;
  uncertain.value=true;const value=JSON.parse(raw);
  attemptedKey=value?.version===1&&value.scope===scope&&typeof value.confirmationKey==='string'&&/^[a-f0-9]{64}$/.test(value.confirmationKey)&&raw===stored(value.confirmationKey)?value.confirmationKey:null;
 }catch{uncertain.value=true;attemptedKey=null;}
}
function clearAttempt(){
 if(!attemptedKey)return false;
 try{if(mobilePlatform.readStorage(storageKey)!==stored(attemptedKey))return false;
  mobilePlatform.removeStorage(storageKey);return mobilePlatform.readStorage(storageKey)===null;
 }catch{return false;}
}
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onLoad(options=>{orderId=isPositiveLongId(options?.orderId)?options.orderId:null;});
onShow(async()=>{
 visible=true;const ticket=++generation;allowed.value=false;loading.value=true;busy.value=false;preview.value=null;result.value=null;checkedItems.value=[];receiverConfirmed.value=false;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;
  const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  const nextScope=actor.staffId+':'+actor.storeId+':'+orderId;
  if(scope!==nextScope){uncertain.value=false;attemptedKey=null;scope=nextScope;}
  storageKey='agri.delivery.pending.v1:'+scope;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:delivery:assigned');
  if(!allowed.value){message.value='当前账号没有本人配送权限，请联系店长。';return;}
  if(!orderId){message.value='任务编号无效，请返回重新选择。';return;}restoreAttempt();await readCurrent();
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;preview.value=null;result.value=null;checkedItems.value=[];receiverConfirmed.value=false;message.value='';});
async function readCurrent(){
 if(!current()||busy.value||!orderId)return;const ticket=++generation;loading.value=true;preview.value=null;result.value=null;checkedItems.value=[];receiverConfirmed.value=false;message.value='';
 try{const answer=await readAssignedReceipt(orderId,actorEpoch);if(!current(ticket))return;
  preview.value=answer;
  if(answer.status==='COMPLETED'){const cleared=!uncertain.value||clearAttempt();uncertain.value=!cleared;if(cleared)attemptedKey=null;message.value=cleared?'这项任务已签收，无需再次交货或确认。':'这项任务已签收，但本机核对记录未能清理，请联系店长；不要再次交货。';}
  else if(uncertain.value&&answer.confirmationKey!==attemptedKey)message.value='原签收结果尚未确认，任务内容已变化。请联系店长核对，不要再次交货。';
 }catch(failure){if(visible&&ticket===generation){
  if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}
  // 改派/撤权是后端在写入前明确拒绝，必须与网络结果未知分开表述；冻结时仍不解除原待核对。
  const denied=failure instanceof Error&&(failure.message==='BUSINESS_REJECTED'||failure.message==='FORBIDDEN');
  message.value=denied
   ?(uncertain.value?'这项任务已不属于你，可能已被改派。原签收结果仍需店长核对，请勿再次交货。'
                   :'这项任务已不属于你，可能已被改派。请返回任务列表重新读取，不要继续交货。')
   :'任务读取未完成，请重新查询或联系店长核对；原签收结果未确认时请勿再次交货。';
 }}
 finally{if(ticket===generation)loading.value=false;}
}
function canConfirm(){
 const p=preview.value;return current()&&!loading.value&&!busy.value&&!!p&&p.canConfirm&&p.status==='PENDING'&&!!p.confirmationKey&&receiverConfirmed.value
  &&p.items.length===checkedItems.value.length&&p.items.every(item=>checkedItems.value.includes(deliveryItemKey(item)))&&(!uncertain.value||attemptedKey===p.confirmationKey);
}
async function confirm(){
 if(!canConfirm()||!preview.value||!orderId)return;const ticket=generation,key=preview.value.confirmationKey!,target=orderId,wasUnknown=uncertain.value;
 try{const original=mobilePlatform.readStorage(storageKey);
  if(wasUnknown){if(original!==stored(key))throw Error('PENDING_CHANGED');}
  else {if(original!==null)throw Error('PENDING_EXISTS');mobilePlatform.writeStorage(storageKey,stored(key));}
  if(mobilePlatform.readStorage(storageKey)!==stored(key))throw Error('PENDING_UNVERIFIED');
 }catch{uncertain.value=true;attemptedKey=null;message.value='本机核对记录未能确认，已停止提交，请联系店长核对原任务。';return;}
 attemptedKey=key;uncertain.value=true;busy.value=true;message.value='';
 try{const answer=await confirmAssignedReceipt(target,key,actorEpoch);if(!current(ticket))return;
  result.value=answer;preview.value=null;const cleared=clearAttempt();uncertain.value=!cleared;if(cleared)attemptedKey=null;message.value=cleared?'本次签收已记录，没有重复出库或收款。':'签收已完成，但本机核对记录未能清理，请联系店长；不要再次交货。';
 }catch(failure){if(!current(ticket))return;
  if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}
  const rejected=failure instanceof Error&&failure.message==='BUSINESS_REJECTED';
  uncertain.value=wasUnknown||!rejected||!clearAttempt();preview.value=null;
  if(!uncertain.value)attemptedKey=null;
  message.value=uncertain.value?'签收结果待核对，请勿再次交货。先读取同一任务的状态。'
   :'任务已变化，可能已被改派或物料不同；请重新读取并逐项核对后再确认。';
 }finally{if(ticket===generation){checkedItems.value=[];receiverConfirmed.value=false;busy.value=false;}}
}
async function showNavigation(){
 const p=preview.value;if(!current()||!p?.canConfirm||!p.receiverAddress||busy.value)return;const ticket=generation;
 try{const navigation=await mobilePlatform.navigateToAddress(p.receiverAddress);if(!current(ticket))return;message.value=navigation.status==='ADDRESS_ONLY'?'当前只有文字地址，可复制后粘贴到地图搜索。':'地图已打开，请核对实际目的地。';}
 catch{if(current(ticket))message.value='导航未打开，可查看完整地址或复制到地图搜索。';}
}
function copyAddress(){
 const p=preview.value;if(!current()||!p?.canConfirm||!p.receiverAddress||busy.value)return;const ticket=generation;
 uni.setClipboardData({data:p.receiverAddress,success:()=>{if(current(ticket))message.value='地址已复制，可粘贴到地图搜索。';},fail:()=>{if(current(ticket))message.value='复制未完成，可查看上方完整文字地址。';}});
}
const back=()=>uni.redirectTo({url:'/pages/delivery/list/index'});
</script>
<template>
 <view class="page" :class="{ 'page-dock': !loading && preview && preview.canConfirm }">
  <text v-if="loading" class="card t-meta">正在读取任务最新信息…</text>

  <view v-if="uncertain" class="alert alert-danger">
   <AppIcon name="alert" :size="16" class="alert-icon" />
   <view class="grow"><text class="t-strong">上次签收结果待核对</text><text class="t-meta">核对完成前请勿再次交货。</text></view>
  </view>

  <template v-if="!loading && allowed && preview">
   <view class="card card-tight">
    <view class="row-between">
     <text class="t-title grow">{{ preview.orderNo }}</text>
     <text class="chip" :class="preview.status === 'COMPLETED' ? 'chip-ok' : 'chip-warn'">{{ preview.status === 'COMPLETED' ? '已签收' : '待交付' }}</text>
    </view>
   </view>

   <template v-if="preview.status === 'COMPLETED'">
    <view class="card card-ok">
     <view class="row"><AppIcon name="check" :size="18" class="t-ok mr8" /><text class="t-sub">已签收</text></view>
     <text class="t-meta mt4">{{ preview.receivedAt?.replace('T', ' ') ?? '签收时间未记录' }}</text>
     <text class="t-cap mt8">历史确认不按后续退货后的余额反推交付数量。</text>
    </view>
   </template>

   <template v-else-if="preview.canConfirm">
    <view class="section section-first"><text class="section-title">收货人</text></view>
    <view class="card">
     <view class="row-between">
      <view class="grow">
       <text class="t-sub">{{ preview.receiverName }}</text>
       <text class="t-meta mt4">{{ preview.receiverPhone }}</text>
      </view>
      <AppIcon name="pin" :size="18" class="t-primary" />
     </view>
     <view class="addr mt12"><text class="t-body">{{ preview.receiverAddress }}</text></view>
     <view class="row mt12">
      <button class="icon-btn grow mr8" :disabled="busy" @click="showNavigation"><AppIcon name="pin" :size="18" /><text class="icon-btn-label">导航</text></button>
      <button class="icon-btn grow" :disabled="busy" @click="copyAddress"><AppIcon name="copy" :size="18" /><text class="icon-btn-label">复制地址</text></button>
     </view>
    </view>

    <view class="section">
     <view class="section-head"><text class="section-title">本次待交商品</text><text class="section-note">已核 {{ checkedItems.length }}/{{ preview.items.length }}</text></view>
    </view>
    <checkbox-group @change="checkedItems = $event.detail.value">
     <label v-for="item in preview.items" :key="deliveryItemKey(item)" class="card check-card">
      <checkbox class="check-box" :value="deliveryItemKey(item)" :checked="checkedItems.includes(deliveryItemKey(item))" :disabled="busy" />
      <view class="grow">
       <text class="t-sub">{{ item.productName }}</text>
       <text class="qty">{{ item.quantity }} {{ item.unit }}</text>
      </view>
     </label>
    </checkbox-group>

    <view class="card">
     <checkbox-group @change="receiverConfirmed = $event.detail.value.includes('yes')">
      <label class="check-row"><checkbox class="check-box" value="yes" :checked="receiverConfirmed" :disabled="busy" /><text>已核对收货人，商品已实际交给客户</text></label>
     </checkbox-group>
     <text class="t-cap">只记录本任务签收，不再次扣库存或收款。</text>
    </view>
   </template>

   <view v-else class="alert">
    <AppIcon name="alert" :size="16" class="alert-icon" />
    <text>{{ preview.blockReason }}</text>
   </view>
  </template>

  <view v-if="result" class="card card-ok">
   <view class="row"><AppIcon name="check" :size="18" class="t-ok mr8" /><text class="t-sub">签收已完成</text></view>
   <text class="t-meta mt4">{{ result.receivedAt?.replace('T', ' ') ?? '签收时间未记录' }}</text>
  </view>

  <text v-if="message" class="t-body mt8" role="status">{{ message }}</text>
  <button v-if="allowed && orderId" class="btn btn-ghost btn-gap" :disabled="busy || loading" @click="readCurrent">{{ uncertain ? '核对本任务签收结果' : '重新读取任务' }}</button>
  <button class="btn btn-quiet" :disabled="busy" @click="back">返回配送任务</button>

  <view v-if="!loading && preview && preview.canConfirm" class="dock">
   <view class="dock-inner">
    <button class="btn btn-primary" :disabled="!canConfirm()" :loading="busy" @click="confirm">确认已实际交付</button>
   </view>
  </view>
 </view>
</template>

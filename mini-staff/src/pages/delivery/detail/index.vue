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
 <view class="page"><text class="title">核对配送交付</text><text v-if="loading" class="intro">正在读取本人任务的最新信息…</text>
  <view v-if="uncertain" class="notice"><text>上次签收结果待核对，请勿再次交货。</text></view>
  <template v-if="!loading && allowed && preview">
   <text class="subtitle">{{ preview.orderNo }}</text>
   <template v-if="preview.status==='COMPLETED'"><view class="panel"><text class="subtitle">已签收</text><text class="intro">{{ preview.receivedAt?.replace('T',' ')??'签收时间未记录' }}</text><text class="intro">历史确认不按后续退货后的余额反推交付数量。</text></view></template>
   <template v-else-if="preview.canConfirm">
    <view class="panel"><text class="subtitle">收货人</text><text class="intro">{{ preview.receiverName }} · {{ preview.receiverPhone }}</text><text class="address">{{ preview.receiverAddress }}</text><button :disabled="busy" @click="showNavigation">查看导航方式</button><button :disabled="busy" @click="copyAddress">复制完整地址</button></view>
    <text class="subtitle">本次待交商品</text>
    <checkbox-group @change="checkedItems=$event.detail.value"><label v-for="item in preview.items" :key="deliveryItemKey(item)" class="panel item"><checkbox :value="deliveryItemKey(item)" :checked="checkedItems.includes(deliveryItemKey(item))" :disabled="busy" /><view><text class="subtitle">{{ item.productName }}</text><text class="quantity">{{ item.quantity }} {{ item.unit }}</text></view></label></checkbox-group>
    <view class="panel"><checkbox-group @change="receiverConfirmed=$event.detail.value.includes('yes')"><label><checkbox value="yes" :checked="receiverConfirmed" :disabled="busy" />已核对收货人，且以上商品已实际交给客户</label></checkbox-group><text class="intro">本操作只记录这项任务的签收，不会再次扣库存或收款。</text><button class="primary" :disabled="!canConfirm()" :loading="busy" @click="confirm">确认已实际交付</button></view>
   </template>
   <view v-else class="notice"><text>{{ preview.blockReason }}</text></view>
  </template>
  <view v-if="result" class="panel"><text class="subtitle">签收已完成</text><text class="intro">{{ result.receivedAt?.replace('T',' ')??'签收时间未记录' }}</text></view>
  <text v-if="message" class="intro" role="status">{{ message }}</text><button v-if="allowed && orderId" :disabled="busy || loading" @click="readCurrent">{{ uncertain?'核对本任务签收结果':'重新读取任务' }}</button><button :disabled="busy" @click="back">返回我的配送任务</button>
 </view>
</template>
<style scoped>
.page{padding:24px 20px;max-width:600px;margin:auto}.title{display:block;font-size:24px;font-weight:600}.subtitle{display:block;font-size:20px;font-weight:600;line-height:1.6;word-break:break-all}.intro{display:block;color:#4b5563;line-height:1.7;margin:12px 0}.panel{display:block;background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px;margin:20px 0}.notice{background:#fff;border:1px solid #c8ac80;border-radius:10px;padding:20px;margin:20px 0;line-height:1.7}.address{display:block;line-height:1.8;word-break:break-all}.quantity{display:block;font-size:24px;color:#1d5d73;margin-top:8px}.item{display:flex;align-items:flex-start;gap:12px}label{line-height:1.8}button{min-height:48px;margin:16px 0;color:#1d5d73;background:#fff;border:1px solid #1d5d73}.primary{color:#fff;background:#1d5d73}
</style>

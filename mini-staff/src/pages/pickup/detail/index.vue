<script setup lang="ts">
import { ref } from 'vue';
import { onLoad,onShow,onHide } from '@dcloudio/uni-app';
import type { PickupPreview } from '@agri/mobile-contracts/pickup';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import { staffAuth } from '../../../services/auth';
import { readPickupPreview,confirmPickup } from '../../../services/pickup';
import { mobilePlatform } from '../../../services/platform';
const preview=ref<PickupPreview|null>(null),checked=ref<string[]>([]),handedOver=ref(false),loading=ref(true),busy=ref(false),uncertain=ref(false),message=ref('');
let requestId:string|null=null,visible=false,generation=0,epoch=-1,allowed=false,attemptedKey:string|null=null,scope='';
let storageKey='';
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
const current=(ticket=generation)=>visible&&allowed&&ticket===generation&&staffAuth.current()?.epoch===epoch;
onLoad(options=>{requestId=isPositiveLongId(options?.requestId)?options.requestId:null;});
onShow(async()=>{
 visible=true;const ticket=++generation;allowed=false;loading.value=true;busy.value=false;preview.value=null;checked.value=[];handedOver.value=false;message.value='';
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');epoch=actor.epoch;
  const nextScope=actor.staffId+':'+actor.storeId+':'+requestId;
  if(scope!==nextScope){uncertain.value=false;attemptedKey=null;scope=nextScope;}
  storageKey='agri.pickup.pending.v1:'+scope;
  allowed=actor.permissions.includes('*')||actor.permissions.includes('order:pickup:verify');
  if(!allowed){message.value='当前账号没有提货确认权限，请联系店长。';return;}
  if(!requestId){message.value='提货编号无效，请返回重新选择。';return;}restoreAttempt();await readCurrent();
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed=false;preview.value=null;checked.value=[];handedOver.value=false;});
async function readCurrent(){
 if(!current()||busy.value||!requestId)return;const ticket=++generation;loading.value=true;preview.value=null;checked.value=[];handedOver.value=false;message.value='';
 try{const answer=await readPickupPreview(requestId,epoch);if(!current(ticket))return;preview.value=answer;
  if(answer.status==='FULFILLED'){const cleared=!uncertain.value||clearAttempt();uncertain.value=!cleared;if(cleared)attemptedKey=null;message.value=cleared?'原提货请求已完成，无需再次交货。':'原提货请求已完成，但本机核对记录未能清理，请联系店长；不要再次交货。';}
  else if(uncertain.value&&answer.confirmationKey!==attemptedKey)message.value='上次结果尚待核对，当前物料已变化。请联系店长，不要再次交货。';
 }catch{if(current(ticket))message.value='读取未完成，请重新核对同一提货请求。';}
 finally{if(ticket===generation)loading.value=false;}
}
function canConfirm(){const p=preview.value;return current()&&!busy.value&&!loading.value&&p?.status==='PENDING'&&p.canConfirm&&!!p.confirmationKey&&handedOver.value
 &&p.allocations.length>0&&checked.value.length===p.allocations.length&&new Set(checked.value).size===checked.value.length&&p.allocations.every(a=>checked.value.includes(a.id))&&(!uncertain.value||attemptedKey===p.confirmationKey);}
async function confirm(){
 if(!canConfirm()||!preview.value||!requestId)return;const ticket=generation,id=requestId,key=preview.value.confirmationKey!,allocations=preview.value.allocations.map(a=>a.id),wasUnknown=uncertain.value;
 try{const original=mobilePlatform.readStorage(storageKey);
  if(wasUnknown){if(original!==stored(key))throw Error('PENDING_CHANGED');}
  else {if(original!==null)throw Error('PENDING_EXISTS');mobilePlatform.writeStorage(storageKey,stored(key));}
  if(mobilePlatform.readStorage(storageKey)!==stored(key))throw Error('PENDING_UNVERIFIED');
 }catch{uncertain.value=true;attemptedKey=null;message.value='本机核对记录未能确认，已停止提交，请联系店长核对原请求。';return;}
 attemptedKey=key;uncertain.value=true;busy.value=true;message.value='';
 try{await confirmPickup(id,key,allocations,epoch);if(!current(ticket))return;preview.value=null;message.value='确认已发送，正在核对原提货结果。';}
 catch(error){if(!current(ticket))return;const rejected=error instanceof Error&&error.message==='BUSINESS_REJECTED';uncertain.value=wasUnknown||!rejected||!clearAttempt();if(!uncertain.value)attemptedKey=null;preview.value=null;message.value=uncertain.value?'结果待核对，请勿再次交货。':'请求或数量已变化，请重新读取并核对。';}
 finally{if(ticket===generation){busy.value=false;checked.value=[];handedOver.value=false;}}
 if(current(ticket))await readCurrent();
}
async function scan(){
 if(!current()||busy.value||loading.value||!preview.value?.canConfirm)return;const ticket=generation;busy.value=true;
 try{const scanned=await mobilePlatform.scan();if(!current(ticket))return;
  if(scanned.status!=='SCANNED'){message.value='未扫描，可按批次和货位逐项人工核对。';return;}
  const lines=preview.value?.allocations.filter(a=>a.internalBatchNo===scanned.code)||[];
  message.value=lines.length?'找到批次，请核对货位 '+lines.map(a=>a.locationName).join('、')+' 和下方数量，再手动勾选。':'此标签不属于本次待交内部批次，商品条码不能代替批次标签。';
 }catch{if(current(ticket))message.value='扫码未完成，可逐项人工核对。';}finally{if(ticket===generation)busy.value=false;}
}
const back=()=>uni.redirectTo({url:'/pages/pickup/list/index'});
</script>
<template>
 <view class="page"><text class="title">核对库房提货</text><text v-if="loading" class="intro">正在读取最新待交数量…</text>
 <text v-if="uncertain" class="notice">上次结果待核对，请勿再次交货。</text>
 <template v-if="preview && !loading"><text class="subtitle">{{ preview.productName || '商品资料待核对' }}</text><text class="intro">{{ preview.orderNo || '订单号待核对' }}</text>
  <view v-if="preview.status==='FULFILLED'" class="panel"><text class="subtitle">提货已完成</text><text class="intro">{{ preview.fulfilledAt?.replace('T',' ') || '完成时间未记录' }}</text><text class="intro">不按后续退货余额反推历史交付数量。</text></view>
  <template v-else-if="preview.canConfirm"><button :disabled="busy" @click="scan">扫描内部批次标签辅助核对</button>
   <checkbox-group @change="checked=$event.detail.value"><label v-for="a in preview.allocations" :key="a.id" class="panel item"><checkbox :value="a.id" :checked="checked.includes(a.id)" :disabled="busy"/><view><text class="subtitle">{{ a.locationName }}</text><text class="batch">{{ a.internalBatchNo }}</text><text class="quantity">本次交付 {{ a.quantity }} {{ a.unit }}</text></view></label></checkbox-group>
   <view class="panel"><checkbox-group @change="handedOver=$event.detail.value.includes('yes')"><label><checkbox value="yes" :checked="handedOver" :disabled="busy"/>已逐批核对，以上商品已实际交给客户</label></checkbox-group><text class="intro">确认只记录交付，不再次扣库存或收款。</text><button class="primary" :disabled="!canConfirm()" :loading="busy" @click="confirm">确认已实际交付</button></view>
  </template><text v-else class="notice">{{ preview.blockReason }}</text>
 </template><text v-if="message" class="intro" role="status">{{ message }}</text><button :disabled="busy || loading" @click="readCurrent">核对本请求最新结果</button><button :disabled="busy" @click="back">返回提货任务</button></view>
</template>
<style scoped>
.page{padding:24px 20px;max-width:600px;margin:auto}.title{display:block;font-size:24px;font-weight:600}.subtitle{display:block;font-size:20px;font-weight:600;line-height:1.6}.intro,.batch{display:block;line-height:1.7;word-break:break-all;color:#4b5563;margin:12px 0}.panel{display:block;background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px;margin:20px 0}.item{display:flex;gap:12px;align-items:flex-start}.quantity{display:block;font-size:24px;color:#1d5d73}.notice{display:block;line-height:1.8;border:1px solid #c8ac80;padding:20px;margin:16px 0}label{line-height:1.8}button{min-height:48px;margin:16px 0;color:#1d5d73;background:#fff;border:1px solid #1d5d73}.primary{color:#fff;background:#1d5d73}
</style>

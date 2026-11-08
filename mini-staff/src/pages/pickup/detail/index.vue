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
const quantityLabel=(value:string)=>value.includes('.')?value.replace(/0+$/,'').replace(/\.$/,''):value;
const back=()=>uni.redirectTo({url:'/pages/pickup/list/index'});
</script>
<template>
 <view class="staff-page"><view class="page-heading"><text class="eyebrow">库房交付</text><text class="title">核对提货</text><text class="intro">按货位找到商品，逐批核对数量，再记录交付。</text></view><text v-if="loading" class="intro">正在读取最新待交数量…</text>
 <view v-if="uncertain" class="notice"><text class="subtitle">上次结果需要核对</text><text class="intro">请勿再次交货。先读取同一请求的最新状态。</text></view>
 <template v-if="preview && !loading"><view class="staff-panel object-panel"><text class="subtitle">{{ preview.productName || '商品资料待核对' }}</text><text class="code">订单 {{ preview.orderNo || '订单号待核对' }}</text></view>
  <view v-if="preview.status==='FULFILLED'" class="staff-panel section-panel success-panel"><text class="status-label">已完成</text><text class="subtitle">本次提货已记录</text><text class="intro">{{ preview.fulfilledAt?.replace('T',' ') || '完成时间未记录' }}</text><text class="muted">这是原交付记录。后续退货不会用于推算当时交付数量。</text></view>
  <template v-else-if="preview.canConfirm"><view class="section-head"><text class="subtitle">1. 逐批核对实物</text><text class="muted">共 {{ preview.allocations.length }} 条</text></view><button class="staff-secondary" :disabled="busy" @click="scan">扫描批次标签辅助核对</button><text class="muted">扫码只辅助找批次，请按下方货位和数量逐项勾选。</text>
   <checkbox-group @change="checked=$event.detail.value"><label v-for="a in preview.allocations" :key="a.id" class="staff-panel allocation-row"><checkbox :value="a.id" :checked="checked.includes(a.id)" :disabled="busy"/><view class="allocation-info"><text class="location">{{ a.locationName }}</text><text class="quantity">{{ quantityLabel(a.quantity) }} <text class="unit">{{ a.unit }}</text></text><text class="code">内部批次 {{ a.internalBatchNo }}</text><text class="muted">本次待交数量</text></view></label></checkbox-group>
   <view class="staff-panel section-panel"><text class="subtitle">2. 确认实际交付</text><checkbox-group @change="handedOver=$event.detail.value.includes('yes')"><label class="confirmation"><checkbox value="yes" :checked="handedOver" :disabled="busy"/>已逐批核对，以上商品已实际交给客户</label></checkbox-group><text class="muted">确认仅记录交付，不会再次出库或收款。</text><text v-if="!canConfirm()" class="hint">{{ uncertain ? '请先核对上次结果，内容变化时联系店长处理。' : '请逐项勾选实物，并确认已经交给客户。' }}</text><button class="staff-primary" :disabled="!canConfirm()" :loading="busy" @click="confirm">确认提货已交付</button></view>
  </template><view v-else class="notice"><text class="subtitle">当前不能交付</text><text class="intro">{{ preview.blockReason }}</text></view>
 </template><text v-if="message" class="notice" role="status">{{ message }}</text><button class="staff-secondary" :disabled="busy || loading" @click="readCurrent">{{ uncertain ? '核对上次提货结果' : '重新读取最新数量' }}</button><button class="back-button" :disabled="busy" @click="back">返回提货任务</button></view>
</template>
<style scoped>
.page-heading{padding:12px 0 20px}.eyebrow{display:block;color:#1d5d73;font-size:13px;font-weight:600;margin-bottom:10px}.title{display:block;font-size:25px;font-weight:650;line-height:1.45}.subtitle{display:block;font-size:17px;font-weight:600;line-height:1.6;overflow-wrap:anywhere}.intro{display:block;font-size:14px;line-height:1.7;color:#4b5563;margin:12px 0}.muted,.code{display:block;font-size:13px;line-height:1.7;color:#4b5563}.code{overflow-wrap:anywhere}.order-reference{margin-bottom:16px}.section-panel,.object-panel{margin:16px 0}.section-head{display:flex;justify-content:space-between;align-items:center;gap:12px;margin:24px 0 12px}.allocation-row{display:flex;align-items:flex-start;gap:12px;margin:12px 0}.allocation-info{flex:1;min-width:0}.location{display:block;font-size:17px;font-weight:600;line-height:1.5;overflow-wrap:anywhere}.quantity{display:block;font-size:26px;line-height:1.5;font-weight:600;color:#1d5d73;margin:8px 0}.unit{font-size:15px;font-weight:400}.confirmation{display:block;font-size:15px;line-height:1.8;margin:16px 0}.notice{display:block;background:#fff;border:1px solid #c8ac80;border-radius:10px;padding:16px;margin:16px 0;line-height:1.7;font-size:14px}.hint{display:block;font-size:13px;color:#4b5563;line-height:1.7;margin-top:12px}.success-panel{border-color:#7ea76d}.status-label{display:inline-block;background:#edf4e9;color:#35582c;font-size:12px;padding:3px 8px;border-radius:5px;margin-bottom:12px}.staff-primary,.staff-secondary{margin:16px 0;font-size:15px}.back-button,.text-button{background:transparent;color:#1d5d73;font-size:14px;margin:12px 0}.receiver-name{display:block;font-size:22px;font-weight:600;margin-top:8px}.phone{display:block;font-size:17px;line-height:1.7;color:#1f2937;margin:6px 0}.address{display:block;font-size:16px;line-height:1.8;color:#1f2937;overflow-wrap:anywhere;border-top:1px solid #e5e7eb;padding-top:16px;margin-top:16px}.contact-actions{display:flex;gap:12px}.contact-actions button{flex:1;font-size:14px;margin-bottom:0}
</style>

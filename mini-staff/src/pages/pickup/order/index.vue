<script setup lang="ts">
import { ref,computed } from 'vue';
import { onLoad,onShow,onHide } from '@dcloudio/uni-app';
import { createPickupPreparationProgress,type PickupPreparation,type PreparationLine,type PreparationChecks } from '@agri/mobile-contracts/pickup-preparation';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import { staffAuth } from '../../../services/auth';
import { readPickupPreparation } from '../../../services/pickup';
import { mobilePlatform } from '../../../services/platform';
const preparation=ref<PickupPreparation|null>(null),checked=ref<PreparationChecks>({}),onlyUnchecked=ref(false),keyword=ref('');
const loading=ref(true),busy=ref(false),allowed=ref(false),message=ref(''),storageMessage=ref(''),storageTrusted=ref(false);
const scanMatches=ref<string[]>([]),expanded=ref<string[]>([]),pendingRequests=ref<string[]>([]),pendingUnverified=ref(false);
let orderId='',visible=false,generation=0,actorEpoch=-1,staffId='',storeId='';
let progress:ReturnType<typeof createPickupPreparationProgress>|null=null;
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onLoad(options=>{orderId=isPositiveLongId(options?.orderId)?options.orderId:'';});
onShow(async()=>{
 visible=true;const ticket=++generation;allowed.value=false;loading.value=true;busy.value=false;preparation.value=null;checked.value={};scanMatches.value=[];pendingRequests.value=[];pendingUnverified.value=false;message.value='';storageMessage.value='';storageTrusted.value=false;
 try{await staffAuth.refresh();if(!visible||ticket!==generation)return;const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;staffId=actor.staffId;storeId=actor.storeId;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('order:pickup:verify');
  if(!allowed.value){message.value='当前账号没有库房提货权限，请联系店长。';return;}
  if(!orderId){message.value='订单编号无效，请返回提货列表重新选择。';return;}
  progress=createPickupPreparationProgress(mobilePlatform,{staffId,storeId},orderId);await reload();
 }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
 finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;preparation.value=null;checked.value={};scanMatches.value=[];expanded.value=[];pendingRequests.value=[];progress=null;});
async function reload(){
 if(!current()||busy.value||!orderId)return;const ticket=++generation;loading.value=true;preparation.value=null;checked.value={};scanMatches.value=[];message.value='';storageMessage.value='';storageTrusted.value=false;pendingRequests.value=[];pendingUnverified.value=false;
 try{const answer=await readPickupPreparation(orderId,actorEpoch);if(!current(ticket))return;preparation.value=answer;
  try{const restored=progress!.restore(answer);checked.value=restored.checked;storageTrusted.value=true;if(restored.invalidated)storageMessage.value='原数量、批次或售后状态有变化，相关备货勾选已清除，请重新核对。';}
  catch{checked.value={};storageMessage.value='本机备货进度未能恢复，当前不计为已备好。可清除本机勾选后逐批重新核对。';}
  try{pendingRequests.value=answer.lines.filter(l=>mobilePlatform.readStorage('agri.pickup.pending.v1:'+staffId+':'+storeId+':'+l.requestId)!==null).map(l=>l.requestId);}
  catch{pendingUnverified.value=true;message.value='本机原交货记录未能读取，请联系店长核对后再交货。';}
  expanded.value=answer.lines.filter(l=>l.status==='PENDING'&&!isPrepared(l)).slice(0,1).map(l=>l.requestId);
 }catch{if(visible&&ticket===generation&&(!staffAuth.current()||staffAuth.current()?.epoch===actorEpoch)){
  if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='整单清单读取未完成，请重试或联系店长核对原单与权限。';
 }}finally{if(ticket===generation)loading.value=false;}
}
function isPrepared(line:PreparationLine){const ids=checked.value[line.requestId]??[];return line.status==='PENDING'&&line.canPrepare&&line.allocations.length>0&&ids.length===line.allocations.length&&line.allocations.every(a=>ids.includes(a.id));}
const readyCount=computed(()=>preparation.value?.lines.filter(isPrepared).length??0);
const visibleLines=computed(()=>{
 const q=keyword.value.trim().toLowerCase();return preparation.value?.lines.filter(l=>(!onlyUnchecked.value||(l.status==='PENDING'&&!isPrepared(l)))
  &&(!scanMatches.value.length||scanMatches.value.includes(l.requestId))&&(!q||[l.productName,l.productCode,...l.allocations.flatMap(a=>[a.internalBatchNo,a.locationName])].some(v=>v?.toLowerCase().includes(q))))??[];
});
function saveAllocationIds(requestId:string,ids:string[]){
 if(!current()||loading.value||busy.value||!storageTrusted.value||!preparation.value||!progress)return;
 const line=preparation.value.lines.find(l=>l.requestId===requestId);
 if(!line?.canPrepare||new Set(ids).size!==ids.length||!ids.every(id=>line.allocations.some(a=>a.id===id)))return;
 const next={...checked.value,[requestId]:[...ids]};
 try{progress.save(preparation.value,next);checked.value=next;storageMessage.value='本机备货勾选已保存；仍需在原请求核对实际交货。';}
 catch{checked.value={};storageTrusted.value=false;storageMessage.value='本机未能保存备货进度，当前不计为已备好。请重新读取或清除本机勾选后核对。';}
}
function setAllocations(requestId:string,event:unknown){const value=(event as {detail?:{value?:unknown}})?.detail?.value;if(Array.isArray(value)&&value.every(v=>typeof v==='string'))saveAllocationIds(requestId,value);}
function toggleUnchecked(event:unknown){const value=(event as {detail?:{value?:unknown}})?.detail?.value;if(current()&&typeof value==='boolean')onlyUnchecked.value=value;}
function resetProgress(){
 if(!current()||loading.value||busy.value||!progress)return;
 try{progress.reset();checked.value={};storageTrusted.value=true;storageMessage.value='仅清除了本机备货勾选；原交货记录没有变动。';}
 catch{storageTrusted.value=false;checked.value={};storageMessage.value='本机备货进度未能清除，请重试或联系店长。';}
}
function toggleLine(id:string){if(!current()||busy.value||loading.value)return;expanded.value=expanded.value.includes(id)?expanded.value.filter(v=>v!==id):[...expanded.value,id];}
function open(line:PreparationLine){
 if(!current()||busy.value||loading.value||!preparation.value?.lines.includes(line))return;
 if(pendingUnverified.value){message.value='原交货结果未能核对，请先联系店长。';return;}
 if(pendingRequests.value.length&&!pendingRequests.value.includes(line.requestId)){message.value='请先核对本单原交货结果，再处理其他物料。';return;}
 uni.navigateTo({url:'/pages/pickup/detail/index?requestId='+encodeURIComponent(line.requestId)});
}
function resolvePending(){const line=preparation.value?.lines.find(l=>pendingRequests.value.includes(l.requestId));if(line)open(line);}
async function scan(){
 if(!current()||loading.value||busy.value||!preparation.value)return;const ticket=generation;busy.value=true;message.value='';
 try{const result=await mobilePlatform.scan();if(!current(ticket))return;
  if(result.status!=='SCANNED'){message.value='未扫描，可按商品、批次和货位手动查询核对。';return;}
  const matches=preparation.value?.lines.filter(l=>l.productCode===result.code||l.allocations.some(a=>a.internalBatchNo===result.code))??[];
  scanMatches.value=matches.map(l=>l.requestId);keyword.value='';onlyUnchecked.value=false;expanded.value=[...scanMatches.value];
  message.value=matches.length?'匹配到 '+matches.length+' 项，请按原请求、批次和货位逐项手动核对；扫码没有自动勾选。':'本单未找到这个商品编码或内部批次，请手动查询；商品条码不能代替批次核对。';
 }catch{if(current(ticket))message.value='扫码未完成，可按商品、批次和货位手动核对。';}
 finally{if(ticket===generation)busy.value=false;}
}
</script>
<template>
 <view class="page">
  <view v-if="loading" class="card row"><view class="spinner spinner-primary"></view><text class="t-meta">正在读取整单最新批次与剩余数量…</text></view>
  <view v-if="message" class="alert alert-info" role="status"><text>{{ message }}</text></view>
  <template v-if="preparation && allowed">
   <view class="card">
    <text class="t-sub">整单备货 · {{ preparation.customerLabel || '散客' }}</text><text class="t-code mt4">{{ preparation.orderNo || '订单号待核对' }}</text>
    <text class="t-body mt8">原任务：待交 {{ preparation.pendingCount }} 项 · 已交 {{ preparation.fulfilledCount }} 项</text>
    <text class="t-sub mt8">本机备好 {{ readyCount }}/{{ preparation.pendingCount }} 项</text>
    <text class="t-meta mt4">备货勾选只做本机备忘，不代表已交货，也不是领取任务。请约定一人处理本单。</text>
   </view>
   <view v-if="pendingRequests.length || pendingUnverified" class="card">
    <text class="t-sub t-warn">本单有交货结果需要先核对</text><text class="t-meta mt4">请打开原请求查询，不要再次搬货或改用其他物料确认。</text>
    <button v-if="pendingRequests.length" class="btn btn-primary mt8" @click="resolvePending">核对原交货请求</button>
   </view>
   <view class="card">
    <input class="input input-search" v-model="keyword" placeholder="商品名 / 编码 / 批次 / 货位" />
    <view class="row-between mt12"><text class="t-body">只看未备好</text><switch :checked="onlyUnchecked" :disabled="busy" @change="toggleUnchecked" /></view>
    <button class="btn btn-ghost mt12" :disabled="busy" @click="scan"><AppIcon name="scan" :size="16" class="mr8" />扫描内部批次或商品编码定位</button>
    <button v-if="scanMatches.length" class="btn btn-quiet mt8" @click="scanMatches=[]">清除扫码筛选，查看全部</button>
   </view>
   <view v-if="storageMessage" class="alert alert-info" role="status"><text>{{ storageMessage }}</text></view>
   <view v-for="line in visibleLines" :key="line.requestId" class="card">
    <view class="row-between"><text class="t-sub grow prep-name">{{ line.productName || '商品资料待核对' }}</text><text class="chip chip-sm" :class="line.status==='FULFILLED'?'chip-ok':isPrepared(line)?'chip-primary':'chip-line'">{{ line.status==='FULFILLED'?'已交货':isPrepared(line)?'本机备好':'未备好' }}</text></view>
    <text class="t-code mt4">请求 {{ line.requestId }}<template v-if="line.productCode"> · {{ line.productCode }}</template></text>
    <text v-if="line.blockReason" class="t-meta t-warn mt8">{{ line.blockReason }}</text>
    <template v-if="line.canPrepare">
     <button class="btn btn-quiet btn-sm mt8" :disabled="busy" @click="toggleLine(line.requestId)">{{ expanded.includes(line.requestId)?'收起批次':'展开批次与货位' }} · 已核 {{ (checked[line.requestId] || []).length }}/{{ line.allocations.length }} 批</button>
     <checkbox-group v-if="expanded.includes(line.requestId)" @change="setAllocations(line.requestId,$event)">
      <label v-for="allocation in line.allocations" :key="allocation.id" class="check-card mt8">
       <checkbox class="check-box" :value="allocation.id" :checked="(checked[line.requestId] || []).includes(allocation.id)" :disabled="busy || !storageTrusted" />
       <view class="grow"><text class="t-body">{{ allocation.locationName }} · {{ allocation.quantity }} {{ allocation.unit }}</text><text class="t-code mt4">{{ allocation.internalBatchNo }}</text></view>
      </label>
     </checkbox-group>
    </template>
    <text v-if="line.status==='FULFILLED'" class="t-meta mt8">{{ line.fulfilledAt?.replace('T',' ') || '完成时间待核对' }}；不从现在余量倒推历史交付量。</text>
    <button class="btn btn-ghost btn-sm mt12" :disabled="busy" @click="open(line)">{{ pendingRequests.includes(line.requestId)?'核对这项原交货结果':line.status==='FULFILLED'?'查看原完成记录':'在原请求核对交货' }}</button>
   </view>
   <view v-if="!visibleLines.length" class="card empty"><text class="empty-text">当前筛选下没有待核对项。备货勾选不等于交货完成；可关闭筛选查看全部物料。</text></view>
   <button class="btn btn-ghost" :disabled="busy || loading" @click="reload">重新读取最新清单</button>
   <button class="btn btn-quiet mt8" :disabled="busy || loading" @click="resetProgress">清除本机备货勾选</button>
  </template>
  <button v-else-if="allowed && message" class="btn btn-ghost" :disabled="loading" @click="reload">重新读取原单</button>
 </view>
</template>
<style scoped>
.prep-name { overflow-wrap: anywhere; word-break: break-all; }
</style>

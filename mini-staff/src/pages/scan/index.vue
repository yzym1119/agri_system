<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { InventoryMatch,InventoryMatches } from '@agri/mobile-contracts/inventory';
import { staffAuth } from '../../services/auth';
import { mobilePlatform } from '../../services/platform';
import { resolveCode,searchProducts,readLocationProducts } from '../../services/scan';
const code=ref(''),keyword=ref(''),message=ref(''),loading=ref(true),busy=ref(false),allowed=ref(false);
const matches=ref<InventoryMatch[]>([]),hasMore=ref(false),page=ref(1),locationName=ref('');
let generation=0,actorEpoch=-1,visible=false,locationId:string|undefined,lastSearch='';
let mode:'scan'|'search'|'location'='scan';
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
function clearResults(){matches.value=[];hasMore.value=false;}
onShow(async()=>{
  visible=true;const ticket=++generation;loading.value=true;allowed.value=false;busy.value=false;clearResults();message.value='';locationName.value='';locationId=undefined;
  try {
    await staffAuth.refresh();if(!visible||ticket!==generation)return;
    const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
    allowed.value=actor.permissions.includes('*')||actor.permissions.includes('inventory:view');
    if(!allowed.value)message.value='当前账号没有库存查询权限，请联系老板。';
  }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
  finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;busy.value=false;clearResults();});
function failure(ticket:number){
  if(!visible||ticket!==generation)return;
  if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}
  if(!current(ticket))return;
  message.value='查询未完成，请重试或联系老板确认权限。当前没有可用结果。';
}
async function resolveManual(){if(!current()||busy.value)return;await resolve(code.value.trim());}
async function resolve(value:string){
  if(!current()||busy.value)return;
  if(!value||value.length>100){message.value='请输入不超过100字的完整编码。';return;}
  const ticket=++generation;busy.value=true;clearResults();message.value='';locationId=undefined;locationName.value='';mode='scan';
  try{
    const answer=await resolveCode(value,actorEpoch);if(!current(ticket))return;
    code.value=value;matches.value=answer.matches;hasMore.value=answer.hasMore;page.value=1;
    message.value=answer.type==='UNKNOWN'?'没有匹配到本店可查询的记录。请核对编码，或按商品名称搜索。'
      :answer.type==='AMBIGUOUS'?'这个编码有多个匹配项，请核对后选择，不能直接当作批次。':'已识别，请核对下方资料后查看库存。';
  }catch{failure(ticket);}finally{if(ticket===generation)busy.value=false;}
}
async function scan(){
  if(!current()||busy.value)return;
  const ticket=++generation;busy.value=true;message.value='';clearResults();
  try{
    const result=await mobilePlatform.scan();if(!current(ticket))return;
    if(result.status==='SCANNED') {busy.value=false;await resolve(result.code);}
    else message.value=result.status==='DENIED'?'相机权限未开启，可以在下方手输编码。':'已取消扫码，可重新扫描或手输编码。';
  }catch{if(current(ticket))message.value='暂时无法扫码，可以手输编码查询。';}
  finally{if(ticket===generation)busy.value=false;}
}
async function list(kind:'search'|'location',nextPage:number,newLocation?:InventoryMatch,searchTerm=lastSearch){
  if(!current()||busy.value)return;
  const query=kind==='search'?searchTerm:'';
  if(kind==='search'&&(!query||query.length>100)){message.value='请输入不超过100字的商品名称或编码。';return;}
  const selectedLocation=newLocation?.id??locationId;
  if(kind==='location'&&!selectedLocation)return;
  const ticket=++generation;busy.value=true;clearResults();message.value='';
  try{
    const answer:InventoryMatches=kind==='search'?await searchProducts(query,nextPage,actorEpoch):await readLocationProducts(selectedLocation!,nextPage,actorEpoch);
    if(!current(ticket))return;
    mode=kind;page.value=answer.page;matches.value=answer.matches;hasMore.value=answer.hasMore;
    locationId=kind==='location'?selectedLocation:undefined;
    locationName.value=kind==='location'?(newLocation?.name??locationName.value):'';lastSearch=query;
    message.value=answer.matches.length?'请核对商品后查看库存。':'这里没有查询到商品，库存可能已经变化。';
  }catch{failure(ticket);}finally{if(ticket===generation)busy.value=false;}
}
const search=()=>list('search',1,undefined,keyword.value.trim());
const next=()=>mode!=='scan'&&hasMore.value?list(mode,page.value+1):undefined;
const previous=()=>mode!=='scan'&&page.value>1?list(mode,page.value-1):undefined;
function choose(match:InventoryMatch){
  if(!current()||busy.value||!matches.value.includes(match))return;
  if(match.type==='LOCATION')return list('location',1,match);
  let url='/pages/inventory/detail/index?productId='+encodeURIComponent(match.productId!);
  if(match.type==='RECEIPT_BATCH')url+='&batchId='+encodeURIComponent(match.id);
  if(locationId)url+='&locationId='+encodeURIComponent(locationId);
  uni.navigateTo({url});
}
const kind=(match:InventoryMatch)=>match.type==='SKU'?'商品':match.type==='LOCATION'?'货位':'内部收货批次';
</script>
<template>
  <view class="page">
    <text v-if="loading" class="card t-meta">正在核对当前员工和门店…</text>
    <template v-else-if="allowed">
      <view class="card">
        <button class="btn btn-primary" :disabled="busy" :loading="busy" @click="scan"><AppIcon name="scan" :size="18" class="mr8" />扫描条码 / 内部标签</button>
        <view class="field">
          <text class="field-label">手输完整编码</text>
          <view class="row">
            <input v-model="code" class="input grow" maxlength="100" :disabled="busy" placeholder="编码、条码或货位码" placeholder-style="color:#9aa6b2" confirm-type="search" @confirm="resolveManual" />
            <button class="btn btn-sm btn-auto ml8" :disabled="busy" @click="resolveManual">识别</button>
          </view>
        </view>
        <view class="field">
          <text class="field-label">找不到编码？按名称搜索</text>
          <view class="row">
            <input v-model="keyword" class="input grow" maxlength="100" :disabled="busy" placeholder="商品名称或编码" placeholder-style="color:#9aa6b2" confirm-type="search" @confirm="search" />
            <button class="btn btn-sm btn-auto ml8" :disabled="busy" @click="search">搜索</button>
          </view>
        </view>
      </view>

      <view v-if="locationName" class="section">
        <view class="section-head"><text class="section-title">{{ locationName }}</text><text class="section-note">有库存的商品</text></view>
      </view>

      <view v-if="message" class="alert alert-info" role="status">
        <AppIcon name="alert" :size="16" class="alert-icon" />
        <text>{{ message }}</text>
      </view>

      <view v-for="match in matches" :key="match.type + ':' + match.id + ':' + match.unitId" class="card tap" @click="choose(match)">
        <view class="row-between">
          <text class="t-sub grow">{{ match.name }}</text>
          <text class="chip chip-sm" :class="match.enabled ? 'chip-line' : 'chip-warn'">{{ kind(match) }}{{ match.enabled ? '' : ' · 已停用' }}</text>
        </view>
        <text class="t-code mt4">{{ match.code }}</text>
        <view class="row-between mt8">
          <text class="t-cap">{{ match.unitName ? '识别单位：' + match.unitName : '按主单位查看' }}</text>
          <text class="t-cap t-primary">{{ match.type === 'LOCATION' ? '查看货位' : '查看库存' }} ›</text>
        </view>
        <text v-if="match.type === 'SKU'" class="t-cap mt4">商品码不能证明具体批次或有效期。</text>
        <text v-if="!match.enabled" class="t-cap t-warn mt4">已停用，仅作查询参考。</text>
      </view>

      <text v-if="hasMore && mode === 'scan'" class="t-cap mt8">匹配项较多，请核对标签，或输入更具体的商品名称搜索。</text>

      <view v-if="mode !== 'scan' && (page > 1 || hasMore)" class="row-between mt8">
        <button class="btn btn-sm btn-auto" :disabled="busy || page === 1" @click="previous">上一页</button>
        <text class="t-meta">第 {{ page }} 页</text>
        <button class="btn btn-sm btn-auto" :disabled="busy || !hasMore" @click="next">下一页</button>
      </view>
    </template>
    <view v-else-if="message" class="alert alert-info" role="status">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <text>{{ message }}</text>
    </view>
  </view>
</template>

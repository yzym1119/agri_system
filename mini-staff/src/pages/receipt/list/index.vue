<script setup lang="ts">
import { ref } from 'vue';
import { onShow,onHide } from '@dcloudio/uni-app';
import type { ReceivingOrders,PendingReceipt } from '@agri/mobile-contracts/receipt';
import { staffAuth } from '../../../services/auth';
import { receiptRecovery,readReceivingOrders } from '../../../services/receipt';
const orders=ref<ReceivingOrders['orders']>([]),pending=ref<PendingReceipt|null>(null),loading=ref(true),allowed=ref(false),message=ref('');
const keyword=ref(''),page=ref(1),hasMore=ref(false),loaded=ref(false);
let generation=0,visible=false,actorEpoch=-1,lastQuery='';
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
  visible=true;const ticket=++generation;allowed.value=false;loading.value=true;loaded.value=false;orders.value=[];pending.value=null;message.value='';
  try{
    await staffAuth.refresh();if(!visible||ticket!==generation)return;
    const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
    allowed.value=actor.permissions.includes('*')||actor.permissions.includes('purchase:inbound');
    if(!allowed.value){message.value='当前账号没有采购收货权限，请联系老板。';return;}
    pending.value=receiptRecovery.restore();if(!pending.value)await load(1,lastQuery);
  }catch{
    if(!visible||ticket!==generation)return;
    if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}
    message.value='本机收货记录读取异常，暂停新的收货。请联系老板核对，不要清除数据。';allowed.value=false;
  }finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;orders.value=[];pending.value=null;});
async function load(next:number,query=lastQuery){
  if(!current()||pending.value)return;const ticket=++generation;loading.value=true;loaded.value=false;orders.value=[];message.value='';
  try{const result=await readReceivingOrders(next,query,actorEpoch);if(!current(ticket))return;
    loaded.value=true;orders.value=result.orders;page.value=result.page;hasMore.value=result.hasMore;lastQuery=query;
    if(!result.orders.length)message.value='当前没有待收货采购单，或没有匹配到这个单号。';
  }catch{if(visible&&ticket===generation){if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='待收采购单读取未完成，请重试。';}}
  finally{if(ticket===generation)loading.value=false;}
}
const search=()=>load(1,keyword.value.trim());
function open(id:string){if(!current()||loading.value)return;uni.navigateTo({url:'/pages/receipt/detail/index?orderId='+encodeURIComponent(pending.value?.snapshot.purchaseOrderId??id)});}
</script>
<template>
 <view class="staff-page">
  <view class="page-heading"><text class="eyebrow">采购入库</text><text class="title">今天收到哪些货？</text><text class="intro">找到采购单，按实物登记到货数量与批次。</text></view>
  <view v-if="loading" class="staff-panel"><text class="subtitle">正在读取待收采购单</text><text class="intro">请稍候，核对当前员工和本机收货记录。</text></view>
  <template v-else-if="allowed">
   <view v-if="pending" class="staff-panel recovery-panel"><text class="status-label">有一笔收货需要核对</text><text class="subtitle">先确认上次的收货结果</text><text class="order-number">采购单 {{ pending.snapshot.orderNo }}</text><text class="intro">请先查原收货结果，再继续登记其他到货。本机保留了原请求与内容。</text><button class="staff-primary" @click="open(pending.snapshot.purchaseOrderId)">去核对原收货 ›</button></view>
   <template v-else>
    <view class="search-box"><input v-model="keyword" maxlength="100" placeholder="输入采购单号查找" placeholder-style="color:#626f7e" confirm-type="search" @confirm="search" /><button class="text-button" @click="search">查询</button></view>
    <view class="section-head"><text class="subtitle">待收采购单</text><text v-if="loaded" class="muted">本页 {{ orders.length }} 笔</text></view>
    <view v-for="order in orders" :key="order.id" class="staff-panel order-card"><text class="status-label">待登记到货</text><text class="supplier">{{ order.supplierName ?? '供应商名称待核对' }}</text><text class="order-number">{{ order.orderNo }}</text><button class="staff-secondary" @click="open(order.id)">查看商品，登记收货 ›</button></view>
    <view v-if="loaded && !orders.length" class="staff-panel"><text class="subtitle">没有匹配的待收采购单</text><text class="intro">可清空单号重新查询；尚未建立采购单时，请先在后台登记采购。</text></view>
    <view v-if="loaded && (page>1 || hasMore)" class="pagination"><button class="staff-secondary" :disabled="loading || page===1" @click="load(page-1)">上一页</button><text>第 {{ page }} 页</text><button class="staff-secondary" :disabled="loading || !hasMore" @click="load(page+1)">下一页</button></view>
   </template>
  </template>
  <text v-if="message" class="intro" role="status">{{ message }}</text>
 </view>
</template>
<style scoped>
.page-heading{padding:12px 0 20px}.eyebrow{display:block;color:#1d5d73;font-size:13px;font-weight:600;margin-bottom:10px}.title{display:block;font-size:25px;font-weight:650;line-height:1.45}.intro{display:block;color:#4b5563;font-size:14px;line-height:1.7;margin:12px 0}.subtitle{display:block;font-size:17px;font-weight:600;line-height:1.5}.muted{font-size:13px;color:#4b5563}.section-head{display:flex;justify-content:space-between;align-items:center;margin:24px 0 12px}.search-box{display:flex;align-items:center;gap:8px;background:#fff;border:1px solid #a8b2bc;border-radius:10px;padding:4px 12px}.search-box input{flex:1;min-width:0;min-height:44px;font-size:15px}.text-button{color:#1d5d73;background:transparent;font-size:14px;margin:0;padding:0 8px}.order-card{margin:12px 0}.supplier{display:block;font-size:19px;font-weight:600;line-height:1.5;overflow-wrap:anywhere}.order-number{display:block;color:#4b5563;font-size:13px;overflow-wrap:anywhere;line-height:1.7;margin:8px 0 16px}.status-label{display:inline-block;color:#1d5d73;background:#eaf1f4;border-radius:5px;padding:3px 8px;font-size:12px;margin-bottom:12px}.order-card button{font-size:15px;margin:0}.recovery-panel{border-color:#c8ac80}.recovery-panel button{margin-top:20px}.pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:14px;margin-top:24px}.pagination button{margin:0;font-size:14px}
</style>

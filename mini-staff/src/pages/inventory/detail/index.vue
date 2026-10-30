<script setup lang="ts">
import { ref } from 'vue';
import { onLoad,onShow,onHide } from '@dcloudio/uni-app';
import { inventoryTarget,type InventoryTarget,type InventoryView } from '@agri/mobile-contracts/inventory';
import { staffAuth } from '../../../services/auth';
import { readInventory } from '../../../services/scan';
const inventory=ref<InventoryView|null>(null),message=ref(''),loading=ref(true),allowed=ref(false);
let target:InventoryTarget|undefined,visible=false,generation=0,actorEpoch=-1;
onLoad(options=>{try{target=inventoryTarget(options);}catch{message.value='商品、批次或货位编号无效，请返回重新查询。';}});
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
onShow(async()=>{
  visible=true;const ticket=++generation;loading.value=true;allowed.value=false;inventory.value=null;
  try{
    await staffAuth.refresh();if(!visible||ticket!==generation)return;
    const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
    allowed.value=actor.permissions.includes('*')||actor.permissions.includes('inventory:view');
    if(!allowed.value){message.value='当前账号没有库存查询权限，请联系老板。';return;}
    if(target)await load(1);else message.value='商品、批次或货位编号无效，请返回重新查询。';
  }catch{if(visible&&ticket===generation)uni.reLaunch({url:'/pages/login/index'});}
  finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{visible=false;generation++;allowed.value=false;inventory.value=null;});
async function load(page:number){
  if(!current()||!target)return;
  const ticket=++generation;loading.value=true;inventory.value=null;message.value='';
  try{
    const answer=await readInventory(target,page,actorEpoch);if(!current(ticket))return;
    inventory.value=answer;
  }catch{
    if(visible&&ticket===generation){
      if(!staffAuth.current()){allowed.value=false;uni.reLaunch({url:'/pages/login/index'});return;}
      message.value='库存读取未完成，请刷新重试。暂时不能确认库存数量。';
    }
  }finally{if(ticket===generation)loading.value=false;}
}
const retry=()=>load(1);
const back=()=>uni.navigateBack();
</script>
<template>
  <view class="page">
    <text class="title">库存详情</text>
    <text v-if="loading" class="intro">正在读取本店最新库存…</text>
    <template v-else-if="allowed && inventory">
      <view class="panel">
        <text class="subtitle">{{ inventory.productName }}{{ inventory.productEnabled ? '' : ' · 已停用' }}</text>
        <text class="code">商品编码 {{ inventory.productCode }}</text>
        <text class="intro">{{ target?.batchId || target?.locationId ? '当前选中范围' : '当前门店' }} · 按 {{ inventory.mainUnitName }} 统计</text>
        <text class="amount">{{ inventory.quantity === null ? '单位资料待核对' : inventory.quantity + ' ' + inventory.mainUnitName }}</text>
        <text class="intro">未占用：{{ inventory.unreservedQuantity === null ? '待核对' : inventory.unreservedQuantity + ' ' + inventory.mainUnitName }}</text>
        <text class="hint">未占用数量仅扣除已占用库存，不代表可以直接销售。有效期和业务限制仍须核对。</text>
      </view>
      <text class="subtitle">批次与存放位置</text>
      <text v-if="inventory.totalRows === 0" class="intro">当前范围没有正库存记录。</text>
      <view v-for="row in inventory.rows" :key="row.batchId+':'+row.locationId" class="panel">
        <text class="subtitle">{{ row.locationName ?? '货位资料待核对' }}{{ row.locationEnabled ? '' : ' · 货位不可用' }}</text>
        <text class="intro">现存 {{ row.baseQuantity }} {{ row.baseUnitName ?? '单位待核对' }} · 未占用 {{ row.unreservedBaseQuantity }} {{ row.baseUnitName ?? '单位待核对' }}</text>
        <text v-if="row.supplierBatchNo" class="code">厂家批号：{{ row.supplierBatchNo }}</text>
        <text class="code">内部批次：{{ row.internalBatchNo ?? '待核对' }}</text>
        <text class="intro">生产日期：{{ row.productionDate ?? '未知' }}<br />有效期至：{{ row.expiryDate ?? '未知，请查看实物标签' }}</text>
        <text class="hint">原批次收货：{{ row.originalQuantity ?? '待核对' }} {{ row.originalUnitName ?? '单位待核对' }}（整批，不是本货位剩余量）</text>
      </view>
      <view v-if="inventory.totalRows > 30" class="pagination">
        <button :disabled="loading || inventory.page === 1" @click="load(inventory.page-1)">上一页</button><text>第 {{ inventory.page }} 页</text><button :disabled="loading || inventory.page*30 >= inventory.totalRows" @click="load(inventory.page+1)">下一页</button>
      </view>
      <button :disabled="loading" @click="retry">刷新最新库存</button>
    </template>
    <text v-if="message" class="intro" role="status">{{ message }}</text>
    <button v-if="message && allowed && target" :disabled="loading" @click="retry">重新读取</button>
    <button @click="back">返回查货</button>
  </view>
</template>
<style scoped>
.page{padding:24px 20px;max-width:600px;margin:auto}.title{display:block;font-size:24px;font-weight:600}.intro,.hint{display:block;color:#4b5563;line-height:1.7;margin:12px 0}.hint{font-size:14px}.panel{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px;margin:20px 0}.subtitle{display:block;font-size:20px;font-weight:600;line-height:1.6}.amount{display:block;color:#1d5d73;font-size:28px;font-weight:600;line-height:1.6}.code{display:block;overflow-wrap:anywhere;line-height:1.7}button{min-height:48px;margin:16px 0;color:#1d5d73;background:#fff;border:1px solid #1d5d73}.pagination{display:flex;gap:12px;align-items:center;justify-content:space-between}
</style>

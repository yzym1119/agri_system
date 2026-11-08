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
const quantityLabel=(v:string)=>v.includes('.')?v.replace(/0+$/,'').replace(/\.$/,''):v;
const retry=()=>load(1);
const back=()=>uni.navigateBack();
</script>
<template>
 <view class="staff-page staff-tools"><view class="page-heading"><text class="eyebrow">门店库存</text><text class="title">库存与存放位置</text></view><text v-if="loading" class="intro">正在读取本店最新库存…</text>
  <template v-else-if="allowed && inventory"><view class="staff-panel inventory-summary"><text class="subtitle">{{ inventory.productName }}{{ inventory.productEnabled?'':' · 已停用' }}</text><text class="code">{{ inventory.productCode }}</text><text class="muted">{{ target?.batchId || target?.locationId ? '当前选中范围' : '当前门店' }} · 主单位库存</text><text class="stock-amount">{{ inventory.quantity===null?'单位资料待核对':quantityLabel(inventory.quantity)+' '+inventory.mainUnitName }}</text><text class="intro">未占用 {{ inventory.unreservedQuantity===null?'待核对':quantityLabel(inventory.unreservedQuantity)+' '+inventory.mainUnitName }}</text><text class="hint">未占用量仅扣除库存占用，销售前仍需核对有效期和业务限制。</text></view>
   <view class="section-head"><text class="subtitle">批次与货位</text></view><view v-if="inventory.totalRows===0" class="staff-panel"><text class="intro">当前范围没有正库存记录。</text></view>
   <view v-for="row in inventory.rows" :key="row.batchId+':'+row.locationId" class="staff-panel stock-card"><text class="subtitle">{{ row.locationName ?? '货位资料待核对' }}{{ row.locationEnabled?'':' · 货位不可用' }}</text><view class="stock-row"><view><text class="muted">货位现存</text><text class="row-amount">{{ quantityLabel(row.baseQuantity) }} {{ row.baseUnitName ?? '单位待核对' }}</text></view><view><text class="muted">未占用</text><text class="row-amount">{{ quantityLabel(row.unreservedBaseQuantity) }} {{ row.baseUnitName ?? '单位待核对' }}</text></view></view>
    <view class="batch-facts"><text class="code">内部批次 {{ row.internalBatchNo ?? '待核对' }}</text><text v-if="row.supplierBatchNo" class="code">厂家批号 {{ row.supplierBatchNo }}</text><text class="intro">生产日期 {{ row.productionDate ?? '未知' }}<br />有效期至 {{ row.expiryDate ?? '未知，请核对实物' }}</text><text class="hint">原批收货 {{ row.originalQuantity ?? '待核对' }} {{ row.originalUnitName ?? '单位待核对' }}（整批原数量，不是此货位余量）</text></view>
   </view><view v-if="inventory.totalRows>30" class="pagination"><button class="staff-secondary" :disabled="loading || inventory.page===1" @click="load(inventory.page-1)">上一页</button><text>第 {{ inventory.page }} 页</text><button class="staff-secondary" :disabled="loading || inventory.page*30>=inventory.totalRows" @click="load(inventory.page+1)">下一页</button></view><button class="staff-secondary" :disabled="loading" @click="retry">刷新库存</button>
  </template><text v-if="message" class="notice" role="status">{{ message }}</text><button v-if="message && allowed && target" class="staff-secondary" :disabled="loading" @click="retry">重新读取</button><button class="back-button" @click="back">返回查货</button>
 </view>
</template>

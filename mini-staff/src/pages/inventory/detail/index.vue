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
    <text v-if="loading" class="card t-meta">正在读取本店最新库存…</text>
    <template v-else-if="allowed && inventory">
      <view class="card">
        <view class="row-between">
          <view class="grow">
            <text class="t-title">{{ inventory.productName }}</text>
            <text class="t-code mt4">{{ inventory.productCode }}</text>
          </view>
          <text class="chip chip-sm" :class="inventory.productEnabled ? 'chip-primary' : 'chip-warn'">{{ inventory.productEnabled ? '在售' : '已停用' }}</text>
        </view>
        <view class="divider"></view>
        <text class="t-cap">{{ target?.batchId || target?.locationId ? '当前选中范围' : '本店合计' }} · 按 {{ inventory.mainUnitName }} 统计</text>
        <text class="t-num mt4">{{ inventory.quantity === null ? '待核对' : inventory.quantity + ' ' + inventory.mainUnitName }}</text>
        <view class="kv mt8">
          <text class="kv-key">未占用</text>
          <text class="kv-val">{{ inventory.unreservedQuantity === null ? '待核对' : inventory.unreservedQuantity + ' ' + inventory.mainUnitName }}</text>
        </view>
        <text class="t-cap mt8">未占用只扣除已占用库存，不代表可以直接销售；有效期与业务限制仍须核对。</text>
      </view>

      <view class="section">
        <view class="section-head"><text class="section-title">批次与货位</text><text class="section-note">{{ inventory.totalRows }} 条</text></view>
      </view>

      <view v-if="inventory.totalRows === 0" class="card empty">
        <view class="empty-icon"><AppIcon name="box" :size="22" /></view>
        <text class="empty-text">当前范围没有正库存记录。</text>
      </view>

      <view v-for="row in inventory.rows" :key="row.batchId + ':' + row.locationId" class="card">
        <view class="row-between">
          <text class="t-sub grow">{{ row.locationName ?? '货位资料待核对' }}</text>
          <text v-if="!row.locationEnabled" class="chip chip-sm chip-warn">货位不可用</text>
        </view>
        <view class="row mt12">
          <view class="grow">
            <text class="t-cap">现存</text>
            <text class="t-strong mt4">{{ row.baseQuantity }} {{ row.baseUnitName ?? '单位待核对' }}</text>
          </view>
          <view class="grow">
            <text class="t-cap">未占用</text>
            <text class="t-strong mt4">{{ row.unreservedBaseQuantity }} {{ row.baseUnitName ?? '单位待核对' }}</text>
          </view>
        </view>
        <view class="divider"></view>
        <view class="kv"><text class="kv-key">内部批次</text><text class="kv-val">{{ row.internalBatchNo ?? '待核对' }}</text></view>
        <view v-if="row.supplierBatchNo" class="kv"><text class="kv-key">厂家批号</text><text class="kv-val">{{ row.supplierBatchNo }}</text></view>
        <view class="kv"><text class="kv-key">生产日期</text><text class="kv-val">{{ row.productionDate ?? '未知' }}</text></view>
        <view class="kv"><text class="kv-key">有效期至</text><text class="kv-val">{{ row.expiryDate ?? '未知，请查看实物标签' }}</text></view>
        <text class="t-cap mt8">原批次收货 {{ row.originalQuantity ?? '待核对' }} {{ row.originalUnitName ?? '单位待核对' }}（整批，不是本货位剩余量）</text>
      </view>

      <view v-if="inventory.totalRows > 30" class="row-between mt8">
        <button class="btn btn-sm btn-auto" :disabled="loading || inventory.page === 1" @click="load(inventory.page - 1)">上一页</button>
        <text class="t-meta">第 {{ inventory.page }} 页</text>
        <button class="btn btn-sm btn-auto" :disabled="loading || inventory.page * 30 >= inventory.totalRows" @click="load(inventory.page + 1)">下一页</button>
      </view>

      <button class="btn btn-ghost btn-gap" :disabled="loading" @click="retry">刷新最新库存</button>
    </template>

    <view v-if="message" class="alert alert-info mt8" role="status">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <text>{{ message }}</text>
    </view>
    <button v-if="message && allowed && target" class="btn btn-ghost" :disabled="loading" @click="retry">重新读取</button>
    <button class="btn btn-quiet" @click="back">返回查货</button>
  </view>
</template>

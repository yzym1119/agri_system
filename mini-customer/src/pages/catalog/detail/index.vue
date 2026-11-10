<script setup lang="ts">
import {ref} from 'vue';import {onLoad,onShow,onHide,onUnload} from '@dcloudio/uni-app';import {isPositiveLongId} from '@agri/mobile-contracts/ids';
import type {PublicProductDetail} from '@agri/mobile-contracts/customer-catalog';import {publicCatalog,serviceConfigured} from '../../../services/runtime';import {largeText} from '../../../services/preferences';
const detail=ref<PublicProductDetail|null>(null),busy=ref(false),error=ref(''),selectedUnit=ref(''),imageFailed=ref(false);let productId='',visible=false,generation=0;
onLoad((options)=>{productId=isPositiveLongId(options?.productId)?options!.productId:'';detail.value=null;++generation;});
async function load(unit?:string){
 if(!visible)return;if(!productId){error.value='商品编号无效，请返回重新查询。';return;}if(!serviceConfigured){error.value='门店服务暂未连接，请稍后再试。';return;}
 const ticket=++generation,id=productId;busy.value=true;error.value='';
 try{const value=await publicCatalog.product(id,unit);if(visible&&ticket===generation&&id===productId){detail.value=value;selectedUnit.value=value.referencePrice.unitName??unit??'';imageFailed.value=false;}}
 catch{if(visible&&ticket===generation){detail.value=null;error.value='商品可能已下架或资料发生变化，请返回重新查询。';}}
 finally{if(visible&&ticket===generation)busy.value=false;}
}
onShow(async()=>{visible=true;detail.value=null;await load();});function hide(){visible=false;++generation;busy.value=false;detail.value=null;}onHide(hide);onUnload(hide);
function selectUnit(unit:string){selectedUnit.value=unit;return load(unit);}
</script>
<template>
 <view class="customer-page" :class="{'large-text':largeText}"><ReadingHeader title="商品详情"/>
  <text v-if="busy" class="loading">正在读取商品与参考价…</text><view v-if="error" class="notice" role="alert">{{error}}<button class="button button-secondary" hover-class="button-pressed" @tap="load()">重新读取</button></view>
  <template v-if="detail"><text class="title">{{detail.product.name}}</text><image v-if="detail.product.imageUrl&&!imageFailed" :src="detail.product.imageUrl" mode="widthFix" class="product-image" @error="imageFailed=true"/>
   <view class="panel"><text v-if="detail.product.specification" class="paragraph">规格：{{detail.product.specification}}</text><text v-if="detail.product.manufacturer" class="paragraph">厂家：{{detail.product.manufacturer}}</text><text v-if="detail.product.registrationNo" class="paragraph">登记证号：{{detail.product.registrationNo}}</text>
    <text class="panel-title">销售单位</text><view class="unit-options"><button v-for="unit in detail.saleUnits" :key="unit.unitName" class="unit-button" :class="{'unit-selected':selectedUnit===unit.unitName}" hover-class="button-pressed" @tap="selectUnit(unit.unitName)">{{unit.unitName}}</button></view>
    <text v-for="unit in detail.saleUnits" :key="unit.unitName" class="help">1{{unit.unitName}}＝{{unit.baseQuantityPerUnit}}{{detail.baseUnitName}}</text>
    <text v-if="!busy&&detail.referencePrice.state==='AVAILABLE'" class="price">零售参考价 ¥{{detail.referencePrice.unitPrice}} / {{detail.referencePrice.unitName}}</text><text v-else-if="!busy" class="help">单位或价格待门店确认。</text>
    <text class="help">这里仅供查询，不代表已预留库存或可以直接下单。适用作物、地区与实际用法，请核对包装标签并咨询门店。</text>
   </view>
   <view v-if="detail.product.detailText" class="section"><text class="panel-title">商品说明</text><text class="multiline">{{detail.product.detailText}}</text></view>
   <view v-if="detail.product.storageConditions" class="section"><text class="panel-title">存储条件</text><text class="multiline">{{detail.product.storageConditions}}</text></view>
   <text v-if="detail.product.shelfLifeDays" class="help">档案保质期：{{detail.product.shelfLifeDays}}天。具体批次日期以实物为准。</text>
  </template>
 </view>
</template>

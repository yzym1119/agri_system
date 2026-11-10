<script setup lang="ts">
import {ref} from 'vue';import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import type {PublicProduct} from '@agri/mobile-contracts/customer-catalog';
import {publicCatalog,serviceConfigured} from '../../../services/runtime';import {largeText} from '../../../services/preferences';
const keyword=ref(''),rows=ref<PublicProduct[]>([]),nextAfter=ref<string|null>(null),busy=ref(false),error=ref('');
let visible=false,generation=0,activeKeyword='';
async function load(more=false){
 if(!visible||!serviceConfigured||more&&busy.value)return;const ticket=++generation;busy.value=true;error.value='';if(!more){rows.value=[];nextAfter.value=null;activeKeyword=keyword.value.trim();}
 try{const page=await publicCatalog.products(activeKeyword,more?nextAfter.value??undefined:undefined);if(visible&&ticket===generation){rows.value=more?[...rows.value,...page.rows]:page.rows;nextAfter.value=page.nextAfter;}}
 catch{if(visible&&ticket===generation)error.value='商品资料暂时没有读取成功，请重新查询。';}
 finally{if(visible&&ticket===generation)busy.value=false;}
}
onShow(async()=>{visible=true;await load();});function hide(){visible=false;++generation;busy.value=false;}onHide(hide);onUnload(hide);
function open(product:PublicProduct){uni.navigateTo({url:'/pages/catalog/detail/index?productId='+product.productId});}
</script>
<template>
 <view class="customer-page" :class="{'large-text':largeText}"><ReadingHeader title="商品资料"/>
  <text class="title">找找您需要的商品</text><text class="paragraph secondary">查看规格和零售参考价。实际供货与适用范围，请向门店确认。</text>
  <label class="field-label" for="product-search">商品名称或编号</label><input id="product-search" v-model="keyword" class="search-input" maxlength="100" placeholder="例如：玉米" confirm-type="search" @confirm="load(false)"/>
  <button class="button" hover-class="button-pressed" :disabled="!serviceConfigured" @tap="load(false)">查询</button>
  <view v-if="!serviceConfigured" class="notice">门店服务暂未连接，请稍后再试。</view><text v-if="busy" class="loading">正在查询商品…</text><view v-if="error" class="notice" role="alert">{{error}}</view>
  <text v-if="serviceConfigured&&!busy&&!error&&!rows.length" class="help">没有找到已公开的商品。可以换个名称，或联系门店。</text>
  <button v-for="product in rows" :key="product.productId" class="catalog-row" hover-class="row-pressed" @tap="open(product)"><text class="panel-title">{{product.name}}</text><text v-if="product.specification" class="row-label">规格：{{product.specification}}</text><text v-if="product.manufacturer" class="row-label">厂家：{{product.manufacturer}}</text><text class="catalog-link">查看详情与参考价</text></button>
  <button v-if="nextAfter" class="button button-secondary" hover-class="button-pressed" :disabled="busy" @tap="load(true)">继续查看更多商品</button>
 </view>
</template>

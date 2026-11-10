<script setup lang="ts">
import {ref,computed} from 'vue';import {onShow,onHide,onUnload} from '@dcloudio/uni-app';
import {knowledgeScope} from '@agri/mobile-contracts/customer-knowledge';import type {KnowledgeScope,KnowledgeSummary} from '@agri/mobile-contracts/customer-knowledge';
import {publicKnowledge,serviceConfigured} from '../../../services/runtime';import {largeText} from '../../../services/preferences';
const scopes=ref<KnowledgeScope[]>([]),region=ref(''),crop=ref(''),stage=ref(''),keyword=ref(''),rows=ref<KnowledgeSummary[]>([]),nextAfter=ref<string|null>(null),busy=ref(false),scopeLoading=ref(false),error=ref(''),searched=ref(false);
const regions=computed(()=>[...new Set(scopes.value.map(s=>s.region))]),crops=computed(()=>[...new Set(scopes.value.filter(s=>s.region===region.value).map(s=>s.crop))]),stages=computed(()=>[...new Set(scopes.value.filter(s=>s.region===region.value&&s.crop===crop.value).map(s=>s.growthStage))]);
let visible=false,generation=0,scopeGeneration=0,activeKeyword='';
function selected():KnowledgeScope|null{try{const s=knowledgeScope({region:region.value,crop:crop.value,growthStage:stage.value});return scopes.value.some(v=>v.region===s.region&&v.crop===s.crop&&v.growthStage===s.growthStage)?s:null}catch{return null}}
function invalidate(){++generation;busy.value=false;rows.value=[];nextAfter.value=null;searched.value=false;error.value=''}
function picked(event:unknown,options:string[]){const value=(event as {detail?:{value?:unknown}})?.detail?.value;if(typeof value!=='string'&&typeof value!=='number')return null;const index=Number(value);return Number.isInteger(index)&&index>=0&&index<options.length?options[index]!:null}
function selectRegion(event:unknown){const value=picked(event,regions.value);if(value===null)return;invalidate();region.value=value;crop.value='';stage.value=''}
function selectCrop(event:unknown){const value=picked(event,crops.value);if(value===null)return;invalidate();crop.value=value;stage.value=''}
function selectStage(event:unknown){const value=picked(event,stages.value);if(value===null)return;invalidate();stage.value=value}
async function search(more=false){const scope=selected();if(!visible||!serviceConfigured||scopeLoading.value||!scope){if(visible&&!scopeLoading.value)error.value='请先选择地区、作物和生长阶段。';return}if(more&&busy.value)return;const ticket=++generation;busy.value=true;error.value='';if(!more){rows.value=[];nextAfter.value=null;activeKeyword=keyword.value.trim()}const requestScope=JSON.stringify(scope);
 try{const page=await publicKnowledge.page(scope,activeKeyword,more?nextAfter.value??undefined:undefined);if(visible&&ticket===generation&&JSON.stringify(selected())===requestScope){rows.value=more?[...rows.value,...page.rows]:page.rows;nextAfter.value=page.nextAfter;searched.value=true}}
 catch{if(visible&&ticket===generation)error.value='知识暂时没有读取成功，请重新查询或联系门店。'}finally{if(visible&&ticket===generation)busy.value=false}}
async function loadScopes(){if(!visible||!serviceConfigured)return;const run=++scopeGeneration;invalidate();scopeLoading.value=true;
 try{const values=await publicKnowledge.scopes();if(!visible||run!==scopeGeneration)return;scopes.value=values;const previous=!!region.value||!!crop.value||!!stage.value;if(previous&&!selected()){region.value='';crop.value='';stage.value=''}scopeLoading.value=false;if(selected())await search()}
 catch{if(visible&&run===scopeGeneration){scopes.value=[];error.value='适用范围暂时没有读取成功，请重试。'}}finally{if(visible&&run===scopeGeneration)scopeLoading.value=false}}
function open(row:KnowledgeSummary){const scope=selected();if(!scope||busy.value||!rows.value.some(v=>v.id===row.id)||row.scope.region!==scope.region||row.scope.crop!==scope.crop||row.scope.growthStage!==scope.growthStage)return;uni.navigateTo({url:'/pages/knowledge/detail/index?logicalKey='+row.logicalKey+'&revision='+row.revision+'&scope='+encodeURIComponent(JSON.stringify(scope))})}
function home(){uni.reLaunch({url:'/pages/home/index'})}
onShow(async()=>{visible=true;await loadScopes()});function hide(){visible=false;++scopeGeneration;scopeLoading.value=false;invalidate()}onHide(hide);onUnload(hide);
</script>
<template>
 <view class="customer-page" :class="{'large-text':largeText}"><ReadingHeader title="农技知识"/><text class="title">先选您的种植情况</text><text class="paragraph secondary">只查门店已审核、适用于您所选范围的知识。情况拿不准时，直接联系门店。</text>
  <view v-if="!serviceConfigured" class="notice">门店服务暂未连接，请稍后再试。</view><text v-if="scopeLoading" class="loading">正在读取适用范围…</text>
  <template v-if="serviceConfigured&&!scopeLoading&&scopes.length"><text class="field-label">您在哪里种植？</text><picker :range="regions" @change="selectRegion"><view class="scope-picker">{{region||'点这里选择地区'}}<text class="picker-hint">选择</text></view></picker><text class="field-label">种的是什么？</text><picker :range="crops" :disabled="!region" @change="selectCrop"><view class="scope-picker" :class="{'scope-disabled':!region}">{{crop||'点这里选择作物'}}<text class="picker-hint">选择</text></view></picker><text class="field-label">现在处于哪个阶段？</text><picker :range="stages" :disabled="!crop" @change="selectStage"><view class="scope-picker" :class="{'scope-disabled':!crop}">{{stage||'点这里选择生长阶段'}}<text class="picker-hint">选择</text></view></picker>
   <text class="field-label">想查什么？（可不填）</text><input v-model="keyword" class="search-input" maxlength="100" placeholder="输入问题中的关键词" confirm-type="search" @confirm="search(false)"/><button class="button" hover-class="button-pressed" :disabled="!region||!crop||!stage||busy" @tap="search(false)">查看适用知识</button><text class="help">没有您的地区、作物或阶段？请联系门店，不要套用其他范围的经验。</text>
  </template>
  <view v-if="error" class="notice" role="alert">{{error}}<button v-if="!scopes.length" class="button button-secondary" @tap="loadScopes">重新读取范围</button></view><text v-if="busy" class="loading">正在查找适用知识…</text><text v-if="serviceConfigured&&!scopeLoading&&!error&&!scopes.length" class="paragraph">门店暂未公开适用知识。可以先联系门店了解。</text><text v-if="searched&&!busy&&!error&&!rows.length" class="help">这个范围下没有找到相关知识。可以换个关键词，或联系门店。</text>
  <button v-for="row in rows" :key="row.id" class="catalog-row" hover-class="row-pressed" @tap="open(row)"><text class="panel-title">{{row.title}}</text><text class="paragraph secondary">{{row.summary}}</text><text class="row-label">{{row.scope.region}} · {{row.scope.crop}} · {{row.scope.growthStage}}</text><text class="catalog-link">阅读适用条件与步骤</text></button><button v-if="nextAfter" class="button button-secondary" :disabled="busy" @tap="search(true)">继续查看更多知识</button><button class="button button-secondary" @tap="home">返回首页联系门店</button>
 </view>
</template>

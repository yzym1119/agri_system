<script setup lang="ts">
import { ref, watch, onScopeDispose } from 'vue';
import type { AssistantCapability, AssistantResult } from '@agri/mobile-contracts/customer-assistant';
import type { PendingAssistant } from '@agri/mobile-contracts/customer-assistant-submission';
import { customerAuth, customerAssistant, assistantSubmissions, assistantErrorText } from '../../services/runtime';
const props=defineProps<{caseId:string;version:string;factsVersion:string;active:boolean;disabled:boolean}>();
const emit=defineEmits<{refresh:[]}>();
const capability=ref<AssistantCapability|null>(null),result=ref<AssistantResult|null>(null),pending=ref<PendingAssistant|null>(null);
const busy=ref(false),loading=ref(false),error=ref(''),consent=ref(false),blocked=ref(false);
let generation=0;
function current(t:number,epoch:number){return props.active&&generation===t&&customerAuth.current().epoch===epoch&&customerAuth.current().kind==='CUSTOMER';}
function restore(){try{pending.value=assistantSubmissions.current(props.caseId);blocked.value=false;}catch(e){blocked.value=true;error.value=assistantErrorText(e);}}
async function load(){
 const t=++generation,who=customerAuth.current();busy.value=false;loading.value=false;consent.value=false;capability.value=null;result.value=null;error.value='';pending.value=null;
 if(!props.active||who.kind!=='CUSTOMER')return;
 loading.value=true;restore();
 try{
  const next=await customerAssistant.capability(props.caseId,who.epoch);if(!current(t,who.epoch))return;
  if(next.version!==props.version||next.factsVersion!==props.factsVersion){emit('refresh');return;}capability.value=next;
  if(pending.value&&!blocked.value){const found=await assistantSubmissions.query(props.caseId);if(current(t,who.epoch)){result.value=found;restore();}}
 }catch(e){failure(e,t,who.epoch);}finally{if(current(t,who.epoch))loading.value=false;}
}
function failure(e:unknown,t:number,epoch:number){if(current(t,epoch)){error.value=assistantErrorText(e);restore();}else if(props.active&&generation===t&&customerAuth.current().kind==='GUEST')emit('refresh');}
watch(()=>[props.active,props.caseId,props.version,props.factsVersion],()=>{void load();},{immediate:true});
onScopeDispose(()=>{generation++;});
async function submit(){
 if(!props.active||props.disabled||busy.value||loading.value||blocked.value||!capability.value?.enabled||pending.value&&pending.value.phase!=='VERIFIED')return;
 if(!consent.value){error.value='先阅读并勾选下面的说明，再请AI查找参考。';return;}
 const t=generation,who=customerAuth.current();if(who.kind!=='CUSTOMER')return;
 busy.value=true;error.value='';
 try{const value=await assistantSubmissions.submit(props.caseId,{expectedVersion:props.version,factsVersion:props.factsVersion,shareContextConfirmed:true});
  if(current(t,who.epoch)){result.value=value;restore();consent.value=false;emit('refresh');}
 }catch(e){failure(e,t,who.epoch);}finally{if(current(t,who.epoch))busy.value=false;}
}
async function recover(retry=false){
 if(!props.active||busy.value||loading.value||blocked.value)return;
 const t=generation,who=customerAuth.current();if(who.kind!=='CUSTOMER')return;busy.value=true;error.value='';
 try{const value=await(retry?assistantSubmissions.retry(props.caseId):assistantSubmissions.query(props.caseId));if(current(t,who.epoch)){result.value=value;restore();if(pending.value?.phase==='VERIFIED')emit('refresh');}}
 catch(e){failure(e,t,who.epoch);}finally{if(current(t,who.epoch))busy.value=false;}
}
function check(){return recover(false);}
function retryOriginal(){return recover(true);}
function consentChanged(e:{detail:{value:string[]}}){consent.value=e.detail.value.includes('share');}
function missingLabel(field:string){return field==='region'?'种植地区':field==='crop'?'作物名称':'生长阶段';}
function copySource(url:string){uni.setClipboardData({data:url});}
</script>
<template>
 <view class="section assistant-section">
  <text class="panel-title">AI资料参考</text>
  <text v-if="loading" class="loading">正在核对服务与原处理结果…</text>
  <text v-if="error" class="notice case-block" role="alert">{{ error }}</text>
  <template v-if="!loading">
   <text v-if="capability?.reason === 'DISABLED'" class="paragraph secondary">AI参考暂未开启。可以继续补充情况，请门店帮忙。</text>
   <text v-else-if="capability?.reason === 'QUOTA_EXHAUSTED'" class="paragraph secondary">今天的AI查询次数或额度已用完，可以请门店帮忙。</text>
   <text v-else-if="capability?.reason === 'STORE_DISABLED'" class="paragraph secondary">门店暂时不能提供AI参考，请联系门店。</text>
   <text v-else-if="capability?.reason === 'PENDING' && !pending" class="notice case-block">这个问题还有一次AI处理待核对。请回到原设备核对，也可以继续请门店帮忙。</text>
   <template v-if="result">
    <template v-if="result.state === 'REFERENCE' && result.currentFactsVersion === factsVersion">
     <text class="status">根据官方资料整理的参考</text><text class="help">适用于上面已保存的种植情况。不是门店专业处置意见；用药、剂量或混配请由门店确认。</text>
     <view v-for="(citation,index) in result.citations" :key="citation.url + index" class="case-entry">
      <text class="case-message">{{ citation.quote }}</text><text class="help">出处：{{ citation.title }} · {{ citation.publishedOn }}</text>
      <button class="button button-secondary" hover-class="button-pressed" @tap="copySource(citation.url)">复制这条资料的来源链接</button>
     </view>
    </template>
    <template v-else-if="result.state === 'CLARIFY'"><text class="status">还需要补充一些情况</text><text class="paragraph">{{ result.missingFacts.length ? '请在下方补充：' + result.missingFacts.map(missingLabel).join('、') + '。' : '请简要描述现场情况，不要填写身份证号、验证码或详细住址。也可以请门店帮忙。' }}</text></template>
    <text v-else-if="result.state === 'HUMAN'" class="paragraph">这次已转交门店。请看门店回复；有新情况可以继续补充。</text>
    <text v-else-if="['NOT_FOUND','QUEUED','RUNNING','UNKNOWN'].includes(result.state)" class="notice case-block">{{ result.state === 'NOT_FOUND' ? '暂未查到确认结果，原请求已保留，不要另发一遍。' : '这次AI处理还未取得确认结果，原请求已保留。可以稍后核对，也可以请门店帮忙。' }}</text>
    <text v-else-if="result.state === 'STALE'" class="paragraph secondary">种植情况或账号授权已变化，这次旧参考不再展示。请按当前情况继续补充或联系门店。</text>
    <text v-else-if="result.state === 'NOT_DISPATCHED'" class="paragraph secondary">已确认这次没有开始外部查询。可以核对当前情况后再试，或请门店帮忙。</text>
    <text v-else class="paragraph secondary">这次没有取得仍有效的资料参考。可以继续补充情况或请门店帮忙。</text>
   </template>
   <view v-if="pending && pending.phase !== 'VERIFIED' || blocked">
    <text class="help">原记录会一直保留。核对不会重新创建问题。</text>
    <button class="button button-secondary" :disabled="busy || blocked" @tap="check">{{ busy ? '正在核对…' : '核对这次处理结果' }}</button>
    <button v-if="pending && !blocked" class="button button-secondary" :disabled="busy" @tap="retryOriginal">重试原请求（不另起一次）</button>
   </view>
   <view v-else-if="capability?.enabled">
    <checkbox-group @change="consentChanged"><label class="assistant-consent"><checkbox value="share" :checked="consent" :disabled="busy || disabled" color="#0e6b5a" /><text>同意按已保存的种植情况查询外部资料，需要时交给门店确认。不会发送手机号、详细住址或账户信息。</text></label></checkbox-group>
    <button class="button" hover-class="button-pressed" :disabled="busy || disabled || !consent" @tap="submit">{{ busy ? '正在查找，请稍候…' : '请AI查找参考' }}</button>
   </view>
   <button v-if="!capability && !blocked && !pending" class="button button-secondary" :disabled="busy" @tap="load">重新读取AI服务状态</button>
  </template>
 </view>
</template>

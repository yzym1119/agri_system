<template>
  <div class="app-page">
    <section class="page-header">
      <div><h1>微信关联核验</h1><p>核对小程序申请人与门店原客户档案，确认后关联。相同手机号不代表已经确认是同一人。</p></div>
      <el-button :loading="loading" @click="loadQueue()" :disabled="!canRead">刷新队列</el-button>
    </section>
    <el-alert v-if="!canRead" title="需要客户查询及微信关联查询权限，请联系管理员授权。" type="warning" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert v-if="message" :title="message" type="info" :closable="false" show-icon />
    <section v-if="pending" class="pending-panel" aria-live="polite">
      <strong>原决定待核对 · 申请 #{{ pending.enrollmentId }}</strong>
      <p>{{ pending.closingDecision ? '正在结束整次申请。结束成功后客户需要重新申请；已完成的原关联不会被撤销。' : '已保存原提交内容。请勿重新选择客户或更换请求号；查询结果未知不代表未执行。' }}</p>
      <div class="actions">
        <el-button @click="queryPending" :loading="checking" :disabled="!canRead">核对原决定</el-button>
        <el-button @click="retryPending" :loading="busy" :disabled="!canBind || checking">{{ pending.closingDecision ? '重试原结束申请' : '按原内容重试' }}</el-button>
        <el-button v-if="!pending.closingDecision && !conflict" @click="closePendingApplication" :disabled="!canBind || busy || checking">结束本次申请（拒绝）</el-button>
        <el-button v-if="conflict" @click="acknowledgeConflict" :disabled="busy || checking || !canRead">查看其他决定并结束本地等待</el-button>
      </div>
    </section>
    <div v-if="canRead" class="review-layout">
      <el-card shadow="never" class="queue-panel">
        <div class="queue-toolbar">
          <label for="enrollment-status">申请状态</label>
          <el-select id="enrollment-status" v-model="status" @change="loadQueue()" :disabled="!!pending">
            <el-option v-for="(label,value) in statuses" :key="value" :label="label" :value="value" />
          </el-select>
        </div>
        <el-skeleton v-if="loading" :rows="5" animated />
        <el-empty v-else-if="!rows.length && !error" description="当前没有这类申请。客户提交手机号核验后，已有档案的申请会出现在待核验中。" :image-size="76" />
        <ul v-else class="request-list">
          <li v-for="row in rows" :key="row.id"><button type="button" class="request-item" :class="{ selected: selectedId === row.id }" :disabled="!!pending && pending.enrollmentId !== row.id" @click="openEnrollment(row.id)">
            <span class="request-top"><strong>{{ row.displayName }}</strong><span>{{ statuses[row.status] }}</span></span>
            <span>{{ row.phoneMasked }} · 申请 #{{ row.id }}</span><span class="muted">{{ displayTime(row.createdAt) }}</span>
          </button></li>
        </ul>
        <el-button v-if="nextBefore" @click="loadQueue(nextBefore)" :disabled="loading || !!pending" class="next-page">下一页</el-button>
      </el-card>
      <el-card shadow="never" class="detail-panel">
        <el-skeleton v-if="detailLoading" :rows="8" animated />
        <el-empty v-else-if="!detail" description="选择左侧申请，逐项核对后处理。" :image-size="90" />
        <template v-else>
          <div class="detail-heading"><h2>{{ detail.request.displayName }}</h2><el-tag>{{ statuses[detail.request.status] }}</el-tag></div>
          <dl class="request-facts"><div><dt>核验手机号</dt><dd>{{ detail.request.phoneMasked }}</dd></div><div><dt>号码核验时间</dt><dd>{{ displayTime(detail.verifiedAt) }}</dd></div><div><dt>申请编号</dt><dd>#{{ detail.request.id }}</dd></div><div><dt>微信标识尾号</dt><dd>{{ detail.openIdMasked }}</dd></div></dl>
          <template v-if="detail.request.status === 'REVIEW_REQUIRED'">
            <p class="explanation">系统只确认了微信取得的号码。请通过当面核对，或门店原有联系方式回访确认本人；不要仅凭新号码认领旧账户。</p>
            <el-form label-position="top" :disabled="!canBind || !!pending || busy">
              <el-form-item label="本次处理">
                <el-radio-group v-model="draft.action"><el-radio value="LINK">核验后关联原档案</el-radio><el-radio value="REJECT">无法确认，拒绝本次申请</el-radio></el-radio-group>
              </el-form-item>
              <template v-if="draft.action === 'LINK'">
                <h3>选择原客户档案</h3><p class="muted">同号匹配最多列出两份，请人工选择；停用或删除的档案不能关联。</p>
                <el-radio-group v-model="draft.targetCustomerId" class="candidate-list" aria-label="原客户档案">
                  <el-radio v-for="candidate in allCandidates" :key="candidate.id" :value="candidate.id" :disabled="candidate.status !== 1 || candidate.deleted === 1">
                    <span class="candidate-text"><strong>{{ candidate.name }} · 档案 #{{ candidate.id }}</strong><span>{{ candidate.phoneMasked }}{{ candidate.deleted === 1 ? ' · 已删除' : candidate.status !== 1 ? ' · 已停用' : '' }}</span></span>
                  </el-radio>
                </el-radio-group>
                <div class="candidate-search"><el-input v-model="candidateKeyword" placeholder="未找到？输入原客户姓名或手机号" maxlength="50" @keyup.enter="searchCandidates" aria-label="查找其他客户档案" /><el-button @click="searchCandidates" :loading="searching">查找档案</el-button></div>
                <p v-if="searchMessage" class="muted" role="status">{{ searchMessage }}</p>
              </template>
              <el-form-item label="核验方式">
                <el-select v-model="draft.verificationMethod"><el-option label="当面核对本人及原档案资料" value="IN_PERSON" /><el-option label="通过门店原有联系方式回访" value="KNOWN_CONTACT_CALLBACK" /><el-option v-if="draft.action === 'REJECT'" label="尚不能确认本人" value="UNCONFIRMED" /></el-select>
              </el-form-item>
              <el-form-item label="核验经过与处理原因（必填）"><el-input v-model="draft.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明核对了哪些资料、为何确认或无法确认。请勿填写密码、验证码等凭据。" /></el-form-item>
              <el-checkbox v-model="draft.confirmed">{{ draft.action === 'LINK' ? '已核对本人及选中档案，确认允许该微信登录此客户账户。' : '确认拒绝本次申请；客户可重新申请，原档案及账款不变。' }}</el-checkbox>
              <div class="submit-row"><el-button type="primary" @click="submitDecision" :loading="busy">{{ draft.action === 'LINK' ? '确认关联选中档案' : '确认拒绝本次申请' }}</el-button><span v-if="!canBind" class="muted">当前只有查询权限。</span></div>
            </el-form>
          </template>
          <section v-else class="audit-panel"><h3>处理记录</h3><p>关联档案：{{ detail.customerId ? '#' + detail.customerId : '未关联' }}</p><p>核验方式：{{ methods[detail.verificationMethod || ''] || '无需人工核验' }}</p><p>处理原因：{{ detail.reason || '新号码开户，未认领原账户。' }}</p><p class="muted">{{ displayTime(detail.reviewedAt || detail.request.createdAt) }}{{ detail.reviewedBy ? ' · 员工 #' + detail.reviewedBy : '' }}</p></section>
        </template>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref,reactive,computed,watch,onMounted,onUnmounted} from 'vue'
import {useUserStore} from '@/store/user'
import {usePermissionStore} from '@/store/permission'
import {ElMessageBox} from 'element-plus'
import {getEnrollmentQueue,getEnrollment,findEnrollmentCandidates,submitEnrollmentDecision,getEnrollmentDecision} from '@/api/customer-enrollment'
import type {EnrollmentStatus,EnrollmentSummary,EnrollmentCandidate,EnrollmentDetail,EnrollmentDecision} from '@/api/customer-enrollment'

const user=useUserStore(),permissions=usePermissionStore()
const canRead=computed(()=>permissions.permissions.includes('customer:query')&&permissions.permissions.includes('customer:identity:query'))
const canBind=computed(()=>canRead.value&&permissions.permissions.includes('customer:identity:bind'))
const statuses:Record<EnrollmentStatus,string>={REVIEW_REQUIRED:'待人工核验',CREATED:'新客已开户',LINKED:'已关联',REJECTED:'已拒绝'}
const methods:Record<string,string>={IN_PERSON:'当面核对',KNOWN_CONTACT_CALLBACK:'原有联系方式回访',UNCONFIRMED:'未能确认'}
const status=ref<EnrollmentStatus>('REVIEW_REQUIRED'),rows=ref<EnrollmentSummary[]>([]),nextBefore=ref<string|null>(null)
const selectedId=ref(''),detail=ref<EnrollmentDetail|null>(null),loading=ref(false),detailLoading=ref(false)
const error=ref(''),message=ref(''),searchMessage=ref(''),candidateKeyword=ref(''),searchedCandidates=ref<EnrollmentCandidate[]>([]),searching=ref(false)
const draft=reactive({action:'LINK' as 'LINK'|'REJECT',targetCustomerId:'',verificationMethod:'' as EnrollmentDecision['verificationMethod']|'',reason:'',confirmed:false})
interface Pending extends EnrollmentDecision {enrollmentId:string;actorId:string;closingDecision?:EnrollmentDecision}
const pending=ref<Pending|null>(null),busy=ref(false),checking=ref(false),conflict=ref(false)
const closingReason='核验未完成，员工主动结束本次申请；请客户核对资料后重新申请。'
let alive=true,epoch=0,listRun=0,detailRun=0,searchRun=0,storageBlocked=false,pendingRaw=''
const actorId=()=>{const value=user.userInfo?.id;return typeof value==='number'&&Number.isSafeInteger(value)&&value>0?String(value):typeof value==='string'&&/^[1-9][0-9]{0,18}$/.test(value)?value:''}
const storageKey=()=>`agri.identity-enrollment.pending.v1.${actorId()}`
const capture=()=>({token:user.token,actor:actorId(),epoch})
const current=(s:ReturnType<typeof capture>)=>alive&&!!s.token&&!!s.actor&&s.token===user.token&&s.actor===actorId()&&s.epoch===epoch&&canRead.value
const displayTime=(value:string)=>value.replace('T',' ').slice(0,19)
const allCandidates=computed(()=>Array.from(new Map([...(detail.value?.candidates||[]),...searchedCandidates.value].map(c=>[c.id,c])).values()))
function resetDraft(){Object.assign(draft,{action:'LINK',targetCustomerId:'',verificationMethod:'',reason:'',confirmed:false});candidateKeyword.value='';searchedCandidates.value=[];searchMessage.value='';searching.value=false;++searchRun}
async function loadQueue(before?:string){
  const s=capture(),run=++listRun;if(!current(s))return
  loading.value=true;error.value='';rows.value=[];nextBefore.value=null
  try{const result=await getEnrollmentQueue(status.value,before);if(current(s)&&run===listRun){rows.value=result.rows;nextBefore.value=result.nextBefore}}
  catch{if(current(s)&&run===listRun)error.value='核验队列读取失败，请刷新重试；这不代表没有申请。'}
  finally{if(current(s)&&run===listRun)loading.value=false}
}
async function openEnrollment(id:string){
  if(pending.value&&pending.value.enrollmentId!==id){message.value='请先核对原决定，再处理其他申请。';return}
  const s=capture(),run=++detailRun;if(!current(s))return
  selectedId.value=id;detail.value=null;detailLoading.value=true;resetDraft();error.value=''
  try{const result=await getEnrollment(id);if(current(s)&&run===detailRun&&selectedId.value===id)detail.value=result}
  catch{if(current(s)&&run===detailRun)error.value='申请详情读取失败，请重新选择或刷新。'}
  finally{if(current(s)&&run===detailRun)detailLoading.value=false}
}
async function searchCandidates(){
  const s=capture(),id=selectedId.value,keyword=candidateKeyword.value.trim(),run=++searchRun;searchedCandidates.value=[];searchMessage.value=''
  if(!current(s)||pending.value)return
  if(keyword.length<2||keyword.length>50){searchMessage.value='请至少输入两个字或号码，再查找原档案。';return}
  searching.value=true
  try{const result=await findEnrollmentCandidates(keyword);if(current(s)&&run===searchRun&&selectedId.value===id&&candidateKeyword.value.trim()===keyword){searchedCandidates.value=result;searchMessage.value=result.length?'请人工核对并选择，不会自动关联。':'没有找到启用中的客户档案，请核对关键词。'}}
  catch{if(current(s)&&run===searchRun&&selectedId.value===id)searchMessage.value='客户查询失败，请重试；不能据此判断没有档案。'}
  finally{if(current(s)&&run===searchRun)searching.value=false}
}
function savedOriginal(){
  try{if(!pending.value||!pendingRaw||localStorage.getItem(storageKey())!==pendingRaw)throw Error('changed');return true}
  catch{storageBlocked=true;message.value='本机原决定记录读取失败或已被替换，请保留待核对状态并联系管理员。';return false}
}
function clearOriginal(){
  if(!savedOriginal())return false
  try{localStorage.removeItem(storageKey());if(localStorage.getItem(storageKey())!==null)throw Error('remove');pending.value=null;pendingRaw='';return true}
  catch{storageBlocked=true;message.value='结果已读取，但本机原记录未能清理，仍保留原请求供核对。';return false}
}
async function restorePending(){
  const s=capture();if(!current(s))return
  try{
    const raw=localStorage.getItem(storageKey());if(raw===null)return
    const p=JSON.parse(raw)
    if(!p||p.actorId!==s.actor||!/^[1-9][0-9]{0,18}$/.test(p.enrollmentId)||!(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/).test(p.requestKey)||!['LINK','REJECT'].includes(p.action)||!['IN_PERSON','KNOWN_CONTACT_CALLBACK','UNCONFIRMED'].includes(p.verificationMethod)||typeof p.reason!=='string'||!p.reason.trim()||p.reason.length>500||(p.action==='LINK'?!/^[1-9][0-9]{0,18}$/.test(p.targetCustomerId)||p.verificationMethod==='UNCONFIRMED':p.targetCustomerId!==null))throw Error('invalid')
    if(p.closingDecision){const c=p.closingDecision;if(!(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/).test(c.requestKey)||c.requestKey===p.requestKey||c.action!=='REJECT'||c.targetCustomerId!==null||c.verificationMethod!=='UNCONFIRMED'||c.reason!==closingReason)throw Error('invalid closing');Object.freeze(c)}
    pendingRaw=raw;pending.value=Object.freeze(p);await openEnrollment(p.enrollmentId);if(current(s))await queryPending()
  }catch{if(current(s)){storageBlocked=true;error.value='原决定记录无法可靠读取，已停止新的关联操作；请联系管理员核对。'}}
}
async function submitDecision(){
  if(storageBlocked){message.value='本机原决定记录不可用，已停止新的关联操作，请联系管理员核对。';return}
  if(!canBind.value||busy.value||pending.value||!detail.value||detail.value.request.status!=='REVIEW_REQUIRED')return
  const s=capture();if(!current(s))return
  const reason=draft.reason.trim(),target=allCandidates.value.find(c=>c.id===draft.targetCustomerId)
  if(!draft.confirmed||!reason||reason.length>500||/[\x00-\x09\x0b-\x1f\x7f]/.test(reason)||!draft.verificationMethod||draft.action==='LINK'&&(!target||target.status!==1||target.deleted===1||draft.verificationMethod==='UNCONFIRMED')){message.value='请先选择有效原档案、核验方式，填写原因并确认。';return}
  try{
    if(localStorage.getItem(storageKey())!==null){await restorePending();return}
    const requestKey=globalThis.crypto.randomUUID()
    if(!(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/).test(requestKey))throw Error('key')
    const p:Pending={enrollmentId:detail.value.request.id,actorId:s.actor,requestKey,action:draft.action,targetCustomerId:draft.action==='LINK'?draft.targetCustomerId:null,verificationMethod:draft.verificationMethod,reason}
    const raw=JSON.stringify(p);localStorage.setItem(storageKey(),raw);if(localStorage.getItem(storageKey())!==raw)throw Error('storage')
    pendingRaw=raw;pending.value=Object.freeze(p);conflict.value=false
  }catch{storageBlocked=true;message.value='未能可靠保存原决定，未发送关联操作。请检查本机存储后重试。';return}
  await sendOriginal()
}
async function sendOriginal(){
  const p=pending.value,s=capture();if(!p||busy.value||!current(s)||!canBind.value||!savedOriginal())return
  busy.value=true;message.value='正在提交原决定，请勿重复处理。'
  const original=p.closingDecision||p
  const command:EnrollmentDecision={requestKey:original.requestKey,action:original.action,targetCustomerId:original.targetCustomerId,verificationMethod:original.verificationMethod,reason:original.reason}
  try{await submitEnrollmentDecision(p.enrollmentId,command)}catch{if(current(s))message.value='提交结果尚未确认，正在核对原决定；不会更换请求号。'}
  finally{if(current(s)){busy.value=false;await queryPending()}}
}
async function retryPending(){if(!storageBlocked&&!checking.value)await sendOriginal()}
async function closePendingApplication(){
  const p=pending.value,s=capture();if(!p||p.closingDecision||busy.value||checking.value||storageBlocked||!canBind.value||!current(s))return
  try{await ElMessageBox.confirm('这会拒绝整次申请，客户需要重新申请；如果原关联已经完成，将保留原结果，不会撤销关联。确认结束本次申请？','结束本次申请',{confirmButtonText:'确认结束申请',cancelButtonText:'继续核对',type:'warning'})}catch{return}
  if(!current(s)||pending.value!==p||!canBind.value||!savedOriginal())return
  try{
    const requestKey=globalThis.crypto.randomUUID();if(!(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/).test(requestKey)||requestKey===p.requestKey)throw Error('key')
    const closingDecision:EnrollmentDecision=Object.freeze({requestKey,action:'REJECT',targetCustomerId:null,verificationMethod:'UNCONFIRMED',reason:closingReason})
    const next:Pending=Object.freeze({...p,closingDecision}),raw=JSON.stringify(next)
    localStorage.setItem(storageKey(),raw);if(localStorage.getItem(storageKey())!==raw)throw Error('storage')
    pendingRaw=raw;pending.value=next;conflict.value=false
  }catch{storageBlocked=true;message.value='未能可靠保存结束申请请求，未发送新的操作；原决定继续待核对。';return}
  await sendOriginal()
}
function matchesResult(r:any,p:Pending,c:EnrollmentDecision){return r.enrollmentId===p.enrollmentId&&r.requestKey===c.requestKey&&r.status===(c.action==='LINK'?'LINKED':'REJECTED')&&r.action===c.action&&r.targetCustomerId===c.targetCustomerId&&r.verificationMethod===c.verificationMethod&&r.reason===c.reason}
async function finishResult(p:Pending,closing:boolean){
  if(clearOriginal()){conflict.value=false;message.value=closing?'已确认结束本次申请，迟到的关联请求不能再执行；请客户重新申请。':p.action==='LINK'?'已确认原决定：微信已关联到选中的原客户档案。':'已确认原决定：本次申请已拒绝。';await openEnrollment(p.enrollmentId);await loadQueue()}
}
async function queryPending(){
  const p=pending.value,s=capture();if(!p||checking.value||!current(s)||!savedOriginal())return
  checking.value=true
  try{
    const command=p.closingDecision||p,r=await getEnrollmentDecision(p.enrollmentId,command.requestKey)
    if(!current(s)||pending.value!==p)return
    if(r.enrollmentId!==p.enrollmentId||r.requestKey!==command.requestKey)throw Error('identity')
    if(p.closingDecision){
      if(matchesResult(r,p,command)){await finishResult(p,true);return}
      if(r.status!=='UNKNOWN'&&r.status!=='CONFLICT')throw Error('closing content')
      const a=await getEnrollmentDecision(p.enrollmentId,p.requestKey);if(!current(s)||pending.value!==p)return
      if(a.enrollmentId!==p.enrollmentId||a.requestKey!==p.requestKey)throw Error('original identity')
      if(matchesResult(a,p,p)){await finishResult(p,false);return}
      if(a.status==='CONFLICT'&&r.status==='CONFLICT'){conflict.value=true;message.value='两笔原决定均未取得该申请，已有其他最终决定。请查看实际处理记录。';return}
      if(a.status!=='UNKNOWN'&&a.status!=='CONFLICT')throw Error('original content')
      message.value='结束申请的结果仍未知，请继续核对或重试原结束请求；不会改回原关联或生成新请求。';return
    }
    if(r.status==='UNKNOWN'){message.value='原决定结果仍未知。请稍后核对，或按原内容重试；不能修改内容重新关联。';return}
    if(r.status==='CONFLICT'){conflict.value=true;message.value='此申请已有其他决定，不能当作本次成功。请查看其他决定后结束本地等待。';return}
    if(!matchesResult(r,p,p))throw Error('content')
    await finishResult(p,false)
  }catch{if(current(s))message.value='原决定结果读取失败或内容不一致，仍保留原请求待核对。'}
  finally{if(current(s))checking.value=false}
}
async function acknowledgeConflict(){
  const p=pending.value,s=capture();if(!p||!conflict.value||busy.value||checking.value||!current(s)||!savedOriginal())return
  checking.value=true
  try{
    const r=await getEnrollmentDecision(p.enrollmentId,p.requestKey);if(!current(s)||pending.value!==p)return
    const b=p.closingDecision?await getEnrollmentDecision(p.enrollmentId,p.closingDecision.requestKey):null
    if(current(s)&&pending.value===p&&r.enrollmentId===p.enrollmentId&&r.requestKey===p.requestKey&&r.status==='CONFLICT'&&(!p.closingDecision||b?.enrollmentId===p.enrollmentId&&b?.requestKey===p.closingDecision.requestKey&&b?.status==='CONFLICT')&&clearOriginal()){conflict.value=false;message.value='已结束本地等待，以下展示该申请的实际处理记录。';await openEnrollment(p.enrollmentId);await loadQueue()}
  }
  catch{if(current(s))message.value='尚未核对其他决定，原请求继续保留。'}finally{if(current(s))checking.value=false}
}
watch(()=>candidateKeyword.value,()=>{++searchRun;searchedCandidates.value=[];searching.value=false;searchMessage.value='';draft.targetCustomerId=''}, {flush:'sync'})
watch(()=>[user.token,actorId(),canRead.value],()=>{++epoch;++listRun;++detailRun;++searchRun;rows.value=[];detail.value=null;selectedId.value='';pending.value=null;pendingRaw='';storageBlocked=false;busy.value=false;checking.value=false;loading.value=false;detailLoading.value=false;searching.value=false;error.value='会话或权限已变化，请重新进入核验页读取当前资料。';message.value='';conflict.value=false;resetDraft()}, {flush:'sync'})
onMounted(async()=>{await restorePending();await loadQueue()})
onUnmounted(()=>{alive=false;++epoch})
</script>

<style scoped>
.page-header p,.explanation,.pending-panel p{margin:8px 0;color:var(--el-text-color-regular);line-height:1.7;max-width:75ch}
.app-page>.el-alert{margin-bottom:16px}
.review-layout{display:grid;grid-template-columns:minmax(270px,0.8fr) minmax(0,1.6fr);gap:20px;align-items:start}
.queue-panel,.detail-panel{min-width:0}
.queue-toolbar{display:flex;align-items:center;gap:12px;margin-bottom:16px}.queue-toolbar .el-select{flex:1}
.request-list{list-style:none;margin:0;padding:0}.request-item{display:flex;flex-direction:column;gap:8px;width:100%;padding:16px 12px;border:0;border-bottom:1px solid var(--el-border-color-lighter);background:var(--el-bg-color);color:var(--el-text-color-primary);text-align:left;font:inherit;cursor:pointer;overflow-wrap:anywhere}
.request-item:hover,.request-item.selected{background:var(--el-fill-color-light)}.request-item.selected{box-shadow:inset 3px 0 var(--el-color-primary)}.request-item:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:-2px}.request-item:disabled{opacity:.65;cursor:default}
.request-top,.detail-heading{display:flex;justify-content:space-between;gap:12px;align-items:center}.request-top span,.muted{color:var(--el-text-color-regular);font-size:13px;line-height:1.6}
.detail-heading h2{font-size:18px;margin:0}.detail-panel h3{font-size:14px;margin:20px 0 8px}.request-facts{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:24px 0}.request-facts dt{font-size:13px;color:var(--el-text-color-regular);margin-bottom:6px}.request-facts dd{margin:0;overflow-wrap:anywhere}
.candidate-list{display:flex;align-items:stretch;flex-direction:column;width:100%;gap:8px}.candidate-list :deep(.el-radio){height:auto;min-height:48px;margin-right:0;white-space:normal}.candidate-list :deep(.el-radio__label){white-space:normal}.candidate-text{display:flex;flex-direction:column;gap:4px;line-height:1.6;overflow-wrap:anywhere}.candidate-text span{color:var(--el-text-color-regular);font-size:13px}.candidate-search,.actions{display:flex;flex-wrap:wrap;gap:8px}.candidate-search{margin:16px 0}.candidate-search .el-input{flex:1;min-width:180px}.actions .el-button+.el-button{margin-left:0}.submit-row{display:flex;flex-wrap:wrap;align-items:center;gap:12px;margin-top:20px}.detail-panel :deep(.el-checkbox){height:auto;align-items:flex-start;white-space:normal}.detail-panel :deep(.el-checkbox__label){white-space:normal;line-height:1.6}.detail-panel :deep(.el-select){width:100%}
.pending-panel{padding:16px;margin-bottom:20px;border:1px solid var(--el-border-color);border-left:3px solid var(--el-color-primary);border-radius:8px;background:var(--el-bg-color)}.audit-panel p{line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.next-page{margin-top:16px}
@media(max-width:960px){.review-layout{grid-template-columns:1fr}.request-facts{grid-template-columns:1fr 1fr}}
@media(max-width:480px){.request-facts{grid-template-columns:1fr}.candidate-search .el-input{min-width:100%}}
</style>

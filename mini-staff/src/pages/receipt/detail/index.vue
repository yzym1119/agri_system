<script setup lang="ts">
import { ref } from 'vue';
import { onLoad,onShow,onHide } from '@dcloudio/uni-app';
import { buildReceiptSnapshot,receiptMultiply,type ReceivingDetail,type ReceivingItem,type ReceiptSnapshot,type PendingReceipt,type ReceiptResult } from '@agri/mobile-contracts/receipt';
import { isPositiveLongId } from '@agri/mobile-contracts/ids';
import { staffAuth } from '../../../services/auth';
import { receiptRecovery,readReceivingDetail,sendReceipt,readReceiptResult } from '../../../services/receipt';
import { mobilePlatform } from '../../../services/platform';
type BatchDraft={clientId:number;originalUnit:string;originalQuantity:string;originalUnitCost:string;supplierBatchNo:string;productionDate:string;expiryDate:string;unknownDates:boolean};
type LineDraft={purchaseOrderItemId:string;included:boolean;remark:string;batches:BatchDraft[]};
const detail=ref<ReceivingDetail|null>(null),drafts=ref<LineDraft[]>([]),preview=ref<ReceiptSnapshot|null>(null),pending=ref<PendingReceipt|null>(null),result=ref<ReceiptResult|null>(null);
const allowed=ref(false),loading=ref(true),busy=ref(false),confirmed=ref(false),finished=ref(false),message=ref(''),remark=ref(''),exceptionReason=ref('');
const scanInput=ref(''),scanMessage=ref(''),scanMatches=ref<ReceivingItem[]>([]),locatedLine=ref('');
let orderId:string|null=null,generation=0,actorEpoch=-1,visible=false,batchSequence=0;
type DraftOwner={staffId:string;storeId:string;orderId:string};
let loadedOwner:DraftOwner|null=null,savedDraft:(DraftOwner&{lines:LineDraft[];remark:string;exceptionReason:string})|null=null;
function cacheDraft(){if(!loadedOwner||!detail.value||busy.value||pending.value||finished.value)return;
 savedDraft={...loadedOwner,lines:JSON.parse(JSON.stringify(drafts.value)) as LineDraft[],remark:remark.value,exceptionReason:exceptionReason.value};}
const current=(ticket=generation)=>visible&&allowed.value&&ticket===generation&&staffAuth.current()?.epoch===actorEpoch;
const editable=()=>current()&&!loading.value&&!busy.value&&!pending.value&&!finished.value;
onLoad(options=>{orderId=isPositiveLongId(options?.orderId)?options.orderId:null;});
onShow(async()=>{
 cacheDraft();loadedOwner=null;
 visible=true;const ticket=++generation;allowed.value=false;loading.value=true;busy.value=false;confirmed.value=false;finished.value=false;
 detail.value=null;drafts.value=[];preview.value=null;pending.value=null;result.value=null;message.value='';remark.value='';exceptionReason.value='';scanInput.value='';scanMessage.value='';scanMatches.value=[];locatedLine.value='';
 try{
  await staffAuth.refresh();if(!visible||ticket!==generation)return;
  const actor=staffAuth.current();if(!actor)throw Error('UNAUTHORIZED');actorEpoch=actor.epoch;
  allowed.value=actor.permissions.includes('*')||actor.permissions.includes('purchase:inbound');
  if(!allowed.value){savedDraft=null;message.value='当前账号没有采购收货权限，请联系老板。';return;}
  if(savedDraft&&(savedDraft.staffId!==actor.staffId||savedDraft.storeId!==actor.storeId||savedDraft.orderId!==orderId))savedDraft=null;
  pending.value=receiptRecovery.restore();
  if(pending.value){savedDraft=null;message.value='本机仍有原收货待核对，请先核对原结果，不要重新登记。';return;}
  if(!orderId){message.value='采购单编号无效，请返回重新选择。';return;}
  const loaded=await readReceivingDetail(orderId,actorEpoch);if(!current(ticket))return;
  detail.value=loaded;const cached=savedDraft;
  drafts.value=loaded.items.map(item=>cached?.lines.find(l=>l.purchaseOrderItemId===item.id)??{purchaseOrderItemId:item.id,included:item.remainingBaseQuantity!=='0',remark:'',batches:[newBatch(item.orderedUnit)]});
  loadedOwner={staffId:actor.staffId,storeId:actor.storeId,orderId:loaded.id};
  if(cached){remark.value=cached.remark;exceptionReason.value=cached.exceptionReason;message.value='已恢复尚未提交的输入；采购资料已重新读取，请重新核对本次数量、进价和日期。';}
  savedDraft=null;
 }catch{
  if(!visible||ticket!==generation)return;
  if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}
  try{pending.value=receiptRecovery.restore();message.value='采购资料读取未完成，请返回待收列表重试；不要按旧资料收货。';}
  catch{allowed.value=false;message.value='本机收货记录读取异常，暂停新的收货，请联系老板核对，不要清除数据。';}
 }finally{if(ticket===generation)loading.value=false;}
});
onHide(()=>{cacheDraft();loadedOwner=null;visible=false;generation++;allowed.value=false;detail.value=null;drafts.value=[];preview.value=null;pending.value=null;result.value=null;confirmed.value=false;remark.value='';exceptionReason.value='';scanInput.value='';scanMessage.value='';scanMatches.value=[];locatedLine.value='';});
function locateCode(value:string){
 if(!editable()||!detail.value)return;scanMatches.value=[];locatedLine.value='';const code=value.trim();
 if(!code||code.length>100){scanMessage.value='请输入完整商品编码或条码。';return;}
 scanInput.value=code;scanMatches.value=detail.value.items.filter(item=>item.scanCodes.includes(code));
 scanMessage.value=scanMatches.value.length?'编码 '+code+' 匹配到 '+scanMatches.value.length+' 条采购明细，请核对后选择。':'没有匹配到本采购单商品，请核对条码或直接在下方查找；不能按内部批次前缀猜商品。';
}
async function scanProduct(){
 if(!editable())return;const ticket=generation;busy.value=true;scanMatches.value=[];scanMessage.value='';
 try{const answer=await mobilePlatform.scan();if(!current(ticket))return;busy.value=false;
  if(answer.status==='SCANNED')locateCode(answer.code);else scanMessage.value=answer.status==='DENIED'?'相机未开启，可手输商品编码或条码定位。':'已取消扫码，可手输编码或直接查找下方商品。';
 }catch{if(current(ticket))scanMessage.value='暂时无法扫码，可手输编码定位。';}finally{if(ticket===generation)busy.value=false;}
}
function chooseLine(item:ReceivingItem){
 if(!editable()||!scanMatches.value.includes(item))return;locatedLine.value=item.id;const ticket=generation;
 uni.pageScrollTo({selector:'#receipt-line-'+item.id,duration:150,fail:()=>{if(current(ticket))scanMessage.value='已标出 '+item.productName+'，请在下方核对采购行。';}});
}
function newBatch(unit:string):BatchDraft{return {clientId:++batchSequence,originalUnit:unit,originalQuantity:'',originalUnitCost:'',supplierBatchNo:'',productionDate:'',expiryDate:'',unknownDates:false};}
function itemFor(line:LineDraft){return detail.value?.items.find(i=>i.id===line.purchaseOrderItemId);}
function addBatch(line:LineDraft){if(!editable()||!drafts.value.includes(line)||line.batches.length>=50)return;line.batches.push(newBatch(itemFor(line)?.orderedUnit??''));}
function removeBatch(line:LineDraft,batch:BatchDraft){if(!editable()||!drafts.value.includes(line)||line.batches.length<2)return;line.batches=line.batches.filter(b=>b!==batch);}
function changeUnit(line:LineDraft,batch:BatchDraft,index:number){
 if(!editable()||!drafts.value.includes(line)||!line.batches.includes(batch))return;const unit=itemFor(line)?.units[index];if(!unit||unit.name===batch.originalUnit)return;
 batch.originalUnit=unit.name;batch.originalQuantity='';batch.originalUnitCost='';message.value='单位已变更，请重新填写这批的数量和每单位实际进价。';
}
function hint(item:ReceivingItem,batch:BatchDraft){try{const unit=item.units.find(u=>u.name===batch.originalUnit);if(!unit||!batch.originalQuantity)return '';
 const base=receiptMultiply(batch.originalQuantity,unit.baseQuantityPerUnit);return batch.originalQuantity+' '+unit.name+' = '+base+' '+item.baseUnitName;
 }catch{return '请填写普通数字，不使用科学计数法。';}}
function review(){
 if(!editable()||!detail.value)return;message.value='';
 try{preview.value=buildReceiptSnapshot(detail.value,drafts.value.filter(l=>l.included),{remark:remark.value,exceptionReason:exceptionReason.value});}
 catch(failure){preview.value=null;const code=failure instanceof Error?failure.message:'';
  message.value=code==='OVER_RECEIPT_REASON_REQUIRED'?'本次各批合计超过这条采购明细的待收数量，请先填写超收说明，或更正数量后重新核对。'
   :code==='QUANTITY_GRANULARITY'?'换算到最小单位后必须是完整数量，请核对箱内件数和实收数量。'
   :code==='DATE_CONFIRMATION_REQUIRED'?'请核对生产日期和有效期；两者都不知道时，明确勾选“日期未知”。'
   :'请核对各批实际数量、每单位进价及单位；数量和进价须大于0，最多12位整数、8位有效小数。至少选择一条本次收货商品。';}
}
async function submit(){
 if(!editable()||!preview.value)return;const ticket=generation,snapshot=preview.value;busy.value=true;message.value='';confirmed.value=false;
 try{const answer=await receiptRecovery.submit(snapshot,sendReceipt,readReceiptResult);if(!current(ticket))return;result.value=answer;}
 catch{if(!visible||ticket!==generation)return;if(!staffAuth.current()){uni.reLaunch({url:'/pages/login/index'});return;}message.value='本次结果仍需核对，不能重新登记或改内容。请核对原收货结果。';}
 finally{if(visible&&ticket===generation){preview.value=null;try{pending.value=receiptRecovery.restore();}catch{allowed.value=false;message.value='本机收货记录异常，暂停收货。请联系老板核对，不要清除数据。';}busy.value=false;}}
}
async function act(kind:'read'|'retry'|'finish'){
 if(!current()||busy.value||!pending.value||(kind!=='read'&&!confirmed.value))return;
 const expected=result.value?.receiptId;if(kind==='finish'&&(result.value?.status!=='COMPLETED'||!expected))return;
 const ticket=generation;busy.value=true;message.value='';confirmed.value=false;
 try{
  const answer=kind==='read'?await receiptRecovery.lookupOriginal(readReceiptResult):kind==='retry'?await receiptRecovery.retryOriginal(true,sendReceipt,readReceiptResult)
    :await receiptRecovery.finishCompleted(expected!,true,readReceiptResult);
  if(!current(ticket))return;result.value=answer;pending.value=receiptRecovery.restore();
  if(!pending.value){finished.value=true;message.value='原收货已核对结束，没有重复增加库存。';}
  else message.value=answer.status==='COMPLETED'?'原请求已收货，请核对原批次后结束这笔。':'尚未查到原结果，不代表没有收货。保留原内容，请勿重新登记。';
 }catch{if(visible&&ticket===generation){result.value=null;if(!staffAuth.current())uni.reLaunch({url:'/pages/login/index'});else message.value='原结果尚未核对或内容已变化，请保持暂停并联系老板。原请求仍保留。';}}
 finally{if(ticket===generation)busy.value=false;}
}
const back=()=>uni.redirectTo({url:'/pages/receipt/list/index'});
</script>
<template>
 <view class="page">
  <text class="title">登记本次到货</text>
  <text v-if="loading" class="intro">正在核对当前员工、采购单和本机记录…</text>
  <template v-else-if="allowed && pending">
   <view class="panel"><text class="subtitle">原收货待核对</text><text class="code">采购单 {{ pending.snapshot.orderNo }}</text><text class="code">请求号 {{ pending.requestKey }}</text><text class="intro">不能修改这笔内容或重新登记。同号重试不会建立另一笔收货。</text></view>
   <view v-for="line in pending.snapshot.lines" :key="line.purchaseOrderItemId" class="panel"><text class="subtitle">{{ line.productName }}</text><view v-for="(batch,index) in line.batches" :key="index" class="batch"><text>第 {{ index+1 }} 批 · {{ batch.originalQuantity }} {{ batch.originalUnit }} = {{ batch.baseQuantity }} {{ line.baseUnitName }}</text><text class="intro">实际进价 ¥{{ batch.originalUnitCost }} / {{ batch.originalUnit }}</text><text class="intro">生产：{{ batch.productionDate ?? '未知' }} · 有效期至：{{ batch.expiryDate ?? '未知' }}</text></view></view>
   <button class="primary" :disabled="busy" :loading="busy" @click="act('read')">核对原收货结果</button>
   <view v-if="result?.status==='COMPLETED'" class="panel"><text class="subtitle">原收货已完成</text><text class="code">收货单 {{ result.receiptNo }}</text><view v-for="line in result.lines" :key="line.purchaseOrderItemId"><text v-for="batch in line.batches" :key="batch.id" class="code">内部批次：{{ batch.internalBatchNo }}</text></view><checkbox-group @change="confirmed=$event.detail.value.includes('yes')"><label><checkbox value="yes" :checked="confirmed" :disabled="busy" />我已核对原收货单和批次</label></checkbox-group><button :disabled="busy || !confirmed" @click="act('finish')">结束已完成收货</button></view>
   <view v-else class="panel"><text class="intro">查询不到不代表未入库。仅在确需继续时，按原内容、原请求号重试；不能换号。</text><checkbox-group @change="confirmed=$event.detail.value.includes('yes')"><label><checkbox value="yes" :checked="confirmed" :disabled="busy" />我已核对，按原内容重试</label></checkbox-group><button :disabled="busy || !confirmed" @click="act('retry')">同原内容重试</button></view>
  </template>
  <template v-else-if="allowed && preview">
   <text class="subtitle">确认本次实际收货</text><text class="code">{{ preview.orderNo }}</text>
   <view v-for="line in preview.lines" :key="line.purchaseOrderItemId" class="panel"><text class="subtitle">{{ line.productName }}</text><view v-for="(batch,index) in line.batches" :key="index" class="batch"><text>第 {{ index+1 }} 批：{{ batch.originalQuantity }} {{ batch.originalUnit }} = {{ batch.baseQuantity }} {{ line.baseUnitName }}</text><text class="intro">¥{{ batch.originalUnitCost }} / {{ batch.originalUnit }} · 本批 ¥{{ receiptMultiply(batch.originalQuantity,batch.originalUnitCost) }}</text><text class="code">厂家批号：{{ batch.supplierBatchNo ?? '未提供' }}</text><text class="intro">生产：{{ batch.productionDate ?? '未知' }} · 有效期至：{{ batch.expiryDate ?? '未知' }}</text></view></view>
   <text v-if="preview.exceptionReason" class="intro">超收说明：{{ preview.exceptionReason }}</text><text v-if="preview.remark" class="intro">备注：{{ preview.remark }}</text>
   <button class="primary" :disabled="busy" :loading="busy" @click="submit">确认本次入库</button><button :disabled="busy" @click="preview=null">返回修改</button>
  </template>
  <template v-else-if="allowed && detail && !finished">
   <text class="subtitle">{{ detail.orderNo }}</text><text class="intro">{{ detail.supplierName ?? '供应商待核对' }} · 按实际到货填写，不照搬采购总量。</text>
   <view class="panel"><button :disabled="busy" @click="scanProduct">扫描商品，定位采购行</button><input v-model="scanInput" :disabled="busy" maxlength="100" placeholder="也可手输商品编码或条码" @input="scanMatches=[];scanMessage=''" /><button :disabled="busy" @click="locateCode(scanInput)">识别并定位</button><text class="hint">这里只定位本采购单商品，不代填数量或进价，也不作为收货批次证据。</text><text v-if="scanMessage" class="intro" role="status">{{ scanMessage }}</text><button v-for="item in scanMatches" :key="item.id" :disabled="busy" @click="chooseLine(item)">{{ item.productName }} · 原采购 {{ item.orderedQuantity }} {{ item.orderedUnit }} · 定位这行</button></view>
   <view v-for="line in drafts" :id="'receipt-line-'+line.purchaseOrderItemId" :key="line.purchaseOrderItemId" class="panel" :class="{located:locatedLine===line.purchaseOrderItemId}">
    <template v-if="itemFor(line)">
     <text class="subtitle">{{ itemFor(line)!.productName }}</text><text class="intro">采购 {{ itemFor(line)!.orderedQuantity }} {{ itemFor(line)!.orderedUnit }} · 已收 {{ itemFor(line)!.receivedBaseQuantity }} {{ itemFor(line)!.baseUnitName }} · 待收 {{ itemFor(line)!.remainingBaseQuantity }} {{ itemFor(line)!.baseUnitName }}</text>
     <checkbox-group @change="line.included=$event.detail.value.includes('yes')"><label><checkbox value="yes" :checked="line.included" :disabled="busy" />本次收这个商品</label></checkbox-group>
     <template v-if="line.included">
      <view v-for="(batch,index) in line.batches" :key="batch.clientId" class="batch">
       <text class="label">第 {{ index+1 }} 批实际到货</text>
       <text class="label">收货单位</text><picker :range="itemFor(line)!.units.map(u=>u.name)" :disabled="busy" @change="changeUnit(line,batch,Number($event.detail.value))"><view class="picker">{{ batch.originalUnit }} · 点击选择</view></picker>
       <text class="label">数量（{{ batch.originalUnit }}）</text><input v-model="batch.originalQuantity" type="digit" :disabled="busy" maxlength="32" placeholder="填写本批实际数量" /><text class="hint">{{ hint(itemFor(line)!,batch) }}</text>
       <text class="label">实际进价（元 / {{ batch.originalUnit }}）</text><input v-model="batch.originalUnitCost" type="digit" :disabled="busy" maxlength="32" placeholder="必须填写，不自动沿用其他批次进价" />
       <text class="label">厂家批号（可空）</text><input v-model="batch.supplierBatchNo" :disabled="busy" maxlength="100" placeholder="查看包装实物标签" />
       <text class="label">生产日期</text><picker mode="date" :value="batch.productionDate" :disabled="busy" @change="batch.productionDate=$event.detail.value;batch.unknownDates=false"><view class="picker">{{ batch.productionDate || '点击填写，确实未知可留空' }}</view></picker><button v-if="batch.productionDate" class="quiet" @click="batch.productionDate=''">清除生产日期</button>
       <text class="label">有效期至</text><picker mode="date" :value="batch.expiryDate" :disabled="busy" @change="batch.expiryDate=$event.detail.value;batch.unknownDates=false"><view class="picker">{{ batch.expiryDate || '点击填写，确实未知可留空' }}</view></picker><button v-if="batch.expiryDate" class="quiet" @click="batch.expiryDate=''">清除有效期</button>
       <checkbox-group v-if="!batch.productionDate && !batch.expiryDate" @change="batch.unknownDates=$event.detail.value.includes('yes')"><label><checkbox value="yes" :checked="batch.unknownDates" :disabled="busy" />已查看实物，两项日期都未知</label></checkbox-group>
       <button v-if="line.batches.length>1" :disabled="busy" @click="removeBatch(line,batch)">移除这批</button>
      </view>
      <button :disabled="busy || line.batches.length>=50" @click="addBatch(line)">增加另一个实际批次</button>
     </template>
    </template>
   </view>
   <view class="panel"><text class="label">超收说明（超过原采购量时必填）</text><textarea v-model="exceptionReason" :disabled="busy" maxlength="500" placeholder="例如供应商实发数量超出原采购量" /><text class="label">本次收货备注（可空）</text><textarea v-model="remark" :disabled="busy" maxlength="500" /></view>
   <button class="primary" :disabled="busy" @click="review">核对本次收货摘要</button>
  </template>
  <text v-if="message" class="intro" role="status">{{ message }}</text><button :disabled="busy" @click="back">返回待收采购单</button>
 </view>
</template>
<style scoped>
.page{padding:24px 20px;max-width:600px;margin:auto}.title{display:block;font-size:24px;font-weight:600}.subtitle{display:block;font-size:20px;font-weight:600;line-height:1.6}.intro,.hint{display:block;color:#4b5563;line-height:1.7;margin:12px 0}.hint{font-size:14px}.panel{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px;margin:20px 0}.located{border-color:#1d5d73}.batch{border-top:1px solid #e5e7eb;margin-top:20px;padding-top:16px}.label{display:block;font-weight:600;line-height:1.7;margin:16px 0 8px}.code{display:block;line-height:1.7;word-break:break-all}.picker,input,textarea{box-sizing:border-box;width:100%;min-height:48px;padding:12px;background:#f5f6f8;border:1px solid #d1d5db;border-radius:6px;font-size:16px;line-height:1.5}textarea{height:100px}button{min-height:48px;margin:16px 0;color:#1d5d73;background:#fff;border:1px solid #1d5d73}.primary{color:#fff;background:#1d5d73}.quiet{font-size:14px}label{display:block;line-height:1.7;margin:16px 0}
</style>

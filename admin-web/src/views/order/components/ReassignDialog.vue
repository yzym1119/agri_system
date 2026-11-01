<script setup lang="ts">
import { computed,ref,watch,onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReassignment,reassignDelivery,type ReassignmentPreview } from '@/api/order'
import { getStaffList } from '@/api/staff'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import { hasPermission } from '@/utils/permission'
const props=defineProps<{modelValue:boolean;orderId:number|null}>()
const emit=defineEmits<{(e:'update:modelValue',value:boolean):void;(e:'success'):void}>()
const user=useUserStore(),permissions=usePermissionStore()
const detail=ref<ReassignmentPreview|null>(null),target=ref(''),reason=ref(''),options=ref<{id:string;name:string}[]>([])
const loading=ref(false),busy=ref(false),uncertain=ref(false),message=ref('')
const pendingVersion=ref<string|null>(null)
let generation=0,loadedActor=''
const identity=()=>String(user.userInfo?.id)+':'+String(user.userInfo?.extra?.storeId)
const hasShip=()=>hasPermission('order:ship',permissions.permissions)
const hasStaff=()=>hasPermission('staff:list',permissions.permissions)
const integer=(value:unknown,zero=false):value is string=>typeof value==='string'&&(zero?/^(0|[1-9]\d{0,18})$/:/^[1-9]\d{0,18}$/).test(value)&&(value.length<19||value<='9223372036854775807')
const storageKey=()=> 'agri.reassignment.pending.v1:'+identity()+':'+props.orderId
function restorePending(){
 try{const value=localStorage.getItem(storageKey());if(value!==null){uncertain.value=true;pendingVersion.value=integer(value,true)?value:null}}
 catch{uncertain.value=true;pendingVersion.value=null}
}
function clearPending(key:string,version:string){
 try{if(localStorage.getItem(key)!==version)return false;localStorage.removeItem(key);return localStorage.getItem(key)===null}catch{return false}
}
const canFinishReview=computed(()=>uncertain.value&&!loading.value&&!busy.value&&detail.value&&pendingVersion.value!==null&&integer(detail.value.version,true)
 &&(detail.value.version.length>pendingVersion.value.length||(detail.value.version.length===pendingVersion.value.length&&detail.value.version>pendingVersion.value)))
function finishReview(){
 if(!canFinishReview.value||!pendingVersion.value)return
 if(!clearPending(storageKey(),pendingVersion.value)){message.value='本机原核对记录未能清理，请联系店长';return}
 uncertain.value=false;pendingVersion.value=null;message.value='原版本已失效，旧请求不能再改派；这不代表原操作一定成功。';void reload()
}
const current=(ticket:number,id:number|null,actor:string)=>ticket===generation&&props.modelValue&&props.orderId===id&&identity()===actor&&hasShip()
async function reload(){
 if(!props.modelValue||!props.orderId||!hasShip()||busy.value)return
 const ticket=++generation,id=props.orderId,actor=identity();loading.value=true;detail.value=null;options.value=[];target.value='';reason.value=''
 if(!uncertain.value)message.value=''
 try{const answer=await getReassignment(id);if(!current(ticket,id,actor))return
  if(!integer(answer.storeId)||answer.orderId!==String(id)||!integer(answer.version,true)||typeof answer.canReassign!=='boolean'||!(answer.currentStaffId===null||integer(answer.currentStaffId)))throw Error('INVALID_PREVIEW')
  detail.value=answer;loadedActor=actor
  if(uncertain.value)message.value='上次结果尚待核对。当前显示最新指派，请联系店长确认原操作，不要重复提交。'
  if(!answer.canReassign||!hasStaff())return
  const staff=await getStaffList({pageNum:1,pageSize:1000,status:1});if(!current(ticket,id,actor)||!hasStaff())return
  options.value=staff.records.filter(s=>s.status===1&&Number.isSafeInteger(s.id)&&String(s.storeId)===answer.storeId&&String(s.id)!==answer.currentStaffId).map(s=>({id:String(s.id),name:s.name}))
 }catch{if(current(ticket,id,actor))message.value='改派信息未能完整读取，请重新查询；不能按旧资料提交。'}
 finally{if(ticket===generation)loading.value=false}
}
const canSubmit=computed(()=>props.modelValue&&hasShip()&&hasStaff()&&identity()===loadedActor&&!loading.value&&!busy.value&&!uncertain.value&&detail.value?.canReassign===true&&integer(detail.value.version,true)&&options.value.some(s=>s.id===target.value)&&!!reason.value.trim()&&reason.value.trim().length<=200)
async function submit(){
 if(!canSubmit.value||!detail.value||!props.orderId)return
 const ticket=generation,id=props.orderId,actor=identity(),body={expectedVersion:detail.value.version!,targetStaffId:target.value,reason:reason.value.trim()};busy.value=true
 const key=storageKey()
 try{if(localStorage.getItem(key)!==null)throw Error('PENDING_EXISTS');localStorage.setItem(key,body.expectedVersion);if(localStorage.getItem(key)!==body.expectedVersion)throw Error('PENDING_UNVERIFIED')}
 catch{busy.value=false;uncertain.value=true;pendingVersion.value=null;message.value='本机原记录无法确认，已停止提交，请联系店长核对';return}
 pendingVersion.value=body.expectedVersion;uncertain.value=true
 try{await reassignDelivery(id,body);if(!current(ticket,id,actor))return;if(!clearPending(key,body.expectedVersion)){message.value='后台已记录改派，但本机记录未能清理，请先核对当前指派';return}uncertain.value=false;pendingVersion.value=null;ElMessage.success('改派已记录，原配送员的待交权限已更新');emit('success');emit('update:modelValue',false)}
 catch{if(current(ticket,id,actor)){uncertain.value=true;message.value='改派结果未确认，请先核对当前指派，不要重复提交。'}}
 finally{if(ticket===generation)busy.value=false}
}
watch(()=>[props.modelValue,props.orderId,identity()],()=>{generation++;detail.value=null;options.value=[];target.value='';reason.value='';busy.value=false;uncertain.value=false;pendingVersion.value=null;message.value='';if(props.modelValue&&hasShip()){restorePending();void reload()}},{immediate:true})
watch(()=>permissions.permissions.join('|'),()=>{const pending=busy.value;generation++;busy.value=false;uncertain.value=uncertain.value||pending;detail.value=null;options.value=[];target.value='';if(props.modelValue)void reload()})
onUnmounted(()=>{generation++})
</script>
<template>
 <el-dialog :model-value="modelValue" title="更换配送员" width="min(540px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @update:model-value="emit('update:modelValue',$event)">
  <el-skeleton v-if="loading" :rows="4" animated/>
  <template v-else-if="detail"><el-descriptions :column="1" border><el-descriptions-item label="当前配送员编号">{{ detail.currentStaffId || '未记录' }}</el-descriptions-item><el-descriptions-item label="指派版本">{{ detail.version }}</el-descriptions-item></el-descriptions>
   <p class="explanation">改派后由新配送员继续交付，原发货时间保持不变，不会再次出库或收款。</p>
   <el-alert v-if="!detail.canReassign" :title="detail.blockReason || '当前不能改派'" type="warning" :closable="false"/>
   <el-alert v-else-if="!hasStaff()" title="没有员工查询权限，请联系店长处理。" type="warning" :closable="false"/>
   <el-form v-else label-width="90px"><el-form-item label="新配送员" required><el-select v-model="target" :disabled="busy || uncertain" placeholder="选择本店在职员工" style="width:100%"><el-option v-for="s in options" :key="s.id" :label="s.name" :value="s.id"/></el-select></el-form-item><el-form-item label="改派原因" required><el-input v-model="reason" type="textarea" :rows="3" maxlength="200" show-word-limit :disabled="busy || uncertain" placeholder="例如原配送员请假，由其他员工接手"/></el-form-item></el-form>
  </template><el-alert v-if="message" :title="message" type="warning" :closable="false" show-icon/>
  <p v-if="uncertain" class="explanation">仅当当前版本已前进，原版本请求永久失效后，才可结束旧核对；这不表示原改派一定由本次操作完成。</p>
  <template #footer><el-button :disabled="busy" @click="emit('update:modelValue',false)">关闭</el-button><el-button :disabled="busy || loading" @click="reload">{{ uncertain?'核对当前指派':'重新读取' }}</el-button><el-button v-if="uncertain" :disabled="!canFinishReview" @click="finishReview">结束已失效的旧版本核对</el-button><el-button type="primary" :disabled="!canSubmit" :loading="busy" @click="submit">确认改派</el-button></template>
 </el-dialog>
</template>
<style scoped>.explanation{font-size:14px;line-height:1.7;color:var(--el-text-color-regular)}.el-form{margin-top:20px}</style>

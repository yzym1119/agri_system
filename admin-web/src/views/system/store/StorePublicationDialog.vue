<script setup lang="ts">
import {ref,computed,watch,onUnmounted} from 'vue'
import {useUserStore} from '@/store/user'
import {usePermissionStore} from '@/store/permission'
import {getStorePublication,setStorePublication} from '@/api/store-publication'
import type {StorePublication} from '@/api/store-publication'
const props=defineProps<{modelValue:boolean;storeId:string}>(),emit=defineEmits<{(event:'update:modelValue',value:boolean):void}>()
const user=useUserStore(),permissions=usePermissionStore()
const allowed=computed(()=>permissions.permissions.includes('store:view')&&permissions.permissions.includes('store:publish'))
const preview=ref<StorePublication|null>(null),loading=ref(false),busy=ref(false),confirmed=ref(false),uncertain=ref(false),error=ref('')
let alive=true,epoch=0,readRun=0
const capture=()=>({token:user.token,actor:String(user.userInfo?.id??''),id:props.storeId,epoch})
const current=(s:ReturnType<typeof capture>)=>alive&&props.modelValue&&allowed.value&&!!s.token&&s.token===user.token&&s.actor===String(user.userInfo?.id??'')&&s.id===props.storeId&&s.epoch===epoch
async function load(){
 const s=capture(),run=++readRun;if(!current(s))return;loading.value=true;preview.value=null;confirmed.value=false;error.value=''
 try{const value=await getStorePublication(s.id,s.token);if(current(s)&&run===readRun){preview.value=value;uncertain.value=false}}
 catch{if(current(s)&&run===readRun)error.value='公开状态没有读取成功，请稍后重试。'}
 finally{if(current(s)&&run===readRun)loading.value=false}
}
async function submit(){
 const s=capture();if(!current(s)||busy.value||loading.value||uncertain.value||!confirmed.value||!preview.value)return
 const original={...preview.value},visible=!original.visible;if(visible&&!original.canPublish)return
 busy.value=true;uncertain.value=true;error.value=''
 try{const result=await setStorePublication(original,visible,s.token);if(current(s)){preview.value=result;confirmed.value=false;uncertain.value=false}}
 catch{if(current(s))error.value='提交结果尚未确认。请重新读取当前公开状态，不要连续提交。'}
 finally{if(current(s))busy.value=false}
}
watch(()=>[props.modelValue,props.storeId,user.token,String(user.userInfo?.id??''),allowed.value],()=>{++epoch;++readRun;preview.value=null;confirmed.value=false;uncertain.value=false;error.value='';busy.value=false;loading.value=false;if(props.modelValue&&allowed.value)void load()},{immediate:true})
onUnmounted(()=>{alive=false;++epoch;++readRun})
</script>
<template>
 <el-dialog :model-value="modelValue" title="门店公开资料" width="min(560px, calc(100vw - 32px))" @update:model-value="emit('update:modelValue',$event)">
  <p>启用状态用于门店内部业务。客户小程序仅展示在这里确认公开的名称、联系电话和地址。</p>
  <el-alert v-if="!allowed" title="需要门店详情与公开资料权限" type="warning" :closable="false"/>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon/>
  <div v-loading="loading">
   <el-descriptions v-if="preview" :column="1" border><el-descriptions-item label="当前公开状态">{{preview.visible?'已公开':'未公开'}}</el-descriptions-item><el-descriptions-item label="门店名称">{{preview.name}}</el-descriptions-item><el-descriptions-item label="联系电话">{{preview.phone||'未填写'}}</el-descriptions-item><el-descriptions-item label="地址">{{preview.address||'未填写'}}</el-descriptions-item></el-descriptions>
   <p v-if="preview">公开后，编辑名称、电话和地址将同步更新客户看到的资料。停用或删除门店后，公开查询不再展示。</p>
   <p v-if="preview&&!preview.visible&&!preview.canPublish">请先启用门店并完善名称、联系电话和地址，再确认公开。</p>
   <el-checkbox v-if="preview" v-model="confirmed" :disabled="busy||uncertain">{{preview.visible?'确认从客户小程序撤回以上门店资料':'我已核对以上资料，同意向客户公开'}}</el-checkbox>
  </div>
  <template #footer><el-button @click="emit('update:modelValue',false)">关闭</el-button><el-button v-if="allowed" :disabled="busy||loading" @click="load">重新读取状态</el-button><el-button v-if="preview&&allowed" type="primary" :loading="busy" :disabled="uncertain||loading||!confirmed||(!preview.visible&&!preview.canPublish)" @click="submit">{{preview.visible?'撤回公开资料':'确认公开资料'}}</el-button></template>
 </el-dialog>
</template>

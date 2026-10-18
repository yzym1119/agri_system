<template>
  <el-dialog :model-value="visible" title="录入还款" width="480px" :close-on-click-modal="false" :close-on-press-escape="!loading && !validating" :show-close="!loading && !validating" @close="onClose">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="客户" prop="customerName">
        <el-input v-model="form.customerName" disabled />
      </el-form-item>

      <el-form-item label="本店欠款" prop="currentTotalDebt">
        <el-input v-model="displayDebt" disabled />
      </el-form-item>

      <el-form-item label="还款金额" prop="amount">
        <el-input-number
          v-if="!debtLoading"
          v-model="form.amount"
          :min="0.01"
          :max="attemptedPayload ? undefined : Math.max(0.01, currentStoreDebt)"
          :disabled="loading || validating || debtLoading || !!debtError || currentStoreDebt <= 0 || !!attemptedPayload"
          :precision="2"
          style="width: 100%"
        />
        <span v-else role="status">正在核对本店欠款…</span>
      </el-form-item>

      <el-form-item label="还款方式" prop="paymentMethod">
        <el-radio-group v-model="form.paymentMethod" :disabled="loading || validating || !!attemptedPayload">
          <el-radio :label="1">现金</el-radio>
          <el-radio :label="2">微信</el-radio>
          <el-radio :label="3">支付宝</el-radio>
          <el-radio :label="4">银行转账</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="收款门店">
        <el-input :model-value="storeLabel" disabled />
      </el-form-item>
      <el-alert v-if="debtError" :title="debtError" type="error" :closable="false" style="margin-bottom: 16px" />
      <el-alert v-if="attemptedPayload" title="本次收款已提交，重试会核对同一笔收款；请保持金额和收款方式不变。" type="info" :closable="false" style="margin-bottom: 16px" />

      <el-form-item label="外部单号" prop="transactionId">
        <el-input v-model="form.transactionId" :disabled="loading || validating || !!attemptedPayload" placeholder="可选：第三方交易单号" />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" :disabled="loading || validating || !!attemptedPayload" type="textarea" :rows="2" placeholder="选填" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="loading" @click="onClose">取消</el-button>
      <el-button type="primary" :loading="loading || validating" :disabled="!attemptedPayload && (debtLoading || !!debtError || currentStoreDebt <= 0)" @click="handleSubmit">{{ attemptedPayload ? '核对本次收款' : '确定' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {getCreditStatistics, repayCredit} from '@/api/credit'
import {useUserStore} from '@/store/user'

const emit = defineEmits(['update:modelValue', 'success'])
const props = defineProps<{
  modelValue: boolean
  customerId: number
  customerName?: string
  currentTotalDebt?: number
}>()
const visible = computed({get: () => props.modelValue, set: (v: boolean) => emit('update:modelValue', v)})
const formRef = ref()
const loading = ref(false)
const validating = ref(false)
const debtLoading = ref(false)
const debtError = ref('')
const currentStoreDebt = ref(0)
const currentStoreId = ref<number>()
const requestKey = ref('')
const attemptedPayload = ref('')
const operationCustomerId = ref<number>()
let debtRequestVersion = 0
const userStore = useUserStore()
const form = reactive({customerName: '', amount: 0, paymentMethod: 1, transactionId: '', remark: ''})
const displayDebt = computed(() => `￥${currentStoreDebt.value.toFixed(2)}`)
const storeLabel = computed(() => {
  const info: any = userStore.userInfo
  return info?.extra?.storeName || info?.storeName || (currentStoreId.value ? `当前门店（${currentStoreId.value}）` : '当前登录员工所属门店')
})
const rules = {
  amount: [
    {required: true, message: '请输入还款金额', trigger: 'blur'},
    {validator: (_rule: any, value: any, callback: any) => {
      const amount = Number(value)
      if (!Number.isFinite(amount) || amount <= 0) return callback(new Error('还款金额必须大于0'))
      if (!attemptedPayload.value && amount > currentStoreDebt.value) return callback(new Error('还款金额不能大于本店当前欠款'))
      callback()
    }, trigger: 'blur'}
  ],
  paymentMethod: [{required: true, message: '请选择还款方式', trigger: 'change'}]
}
const newRequestKey = () => {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
}
const beginRepayment = async () => {
  const customerId = props.customerId
  const version = ++debtRequestVersion
  if (!requestKey.value || operationCustomerId.value !== customerId) {
    requestKey.value = newRequestKey()
    attemptedPayload.value = ''
    operationCustomerId.value = customerId
    form.amount = 0
    form.paymentMethod = 1
    form.transactionId = ''
    form.remark = ''
  }
  form.customerName = props.customerName || ''
  debtLoading.value = true
  debtError.value = ''
  try {
    const summary = await getCreditStatistics(customerId)
    if (version !== debtRequestVersion || !props.modelValue || customerId !== props.customerId) return
    currentStoreDebt.value = Number(summary.totalDebt || 0)
    currentStoreId.value = summary.storeId
    if (!attemptedPayload.value) form.amount = currentStoreDebt.value
  } catch (error: any) {
    if (version !== debtRequestVersion) return
    currentStoreDebt.value = 0
    debtError.value = error?.message || '无法读取本店欠款，请重新打开后重试'
  } finally {
    if (version === debtRequestVersion) debtLoading.value = false
  }
}
watch(() => [props.modelValue, props.customerId] as const, ([value]) => {
  if (value) void beginRepayment()
  else ++debtRequestVersion
})
const onClose = () => {
  if (loading.value || validating.value) return
  emit('update:modelValue', false)
}
const repayData = () => ({customerId: props.customerId, amount: Number(form.amount), paymentMethod: Number(form.paymentMethod),
  transactionId: form.transactionId.trim() || undefined, remark: form.remark.trim() || undefined})
const handleSubmit = async () => {
  if (loading.value || validating.value || !props.modelValue || (!attemptedPayload.value && (debtLoading.value || debtError.value))
      || operationCustomerId.value !== props.customerId || !requestKey.value || !formRef.value) return
  const submittedRequestKey = requestKey.value
  const contextVersion = debtRequestVersion
  const data = repayData()
  const signature = JSON.stringify(data)
  validating.value = true
  try {
    await formRef.value.validate(async (valid: boolean) => {
      if (!valid || loading.value || !props.modelValue || contextVersion !== debtRequestVersion
          || props.customerId !== data.customerId || operationCustomerId.value !== data.customerId
          || requestKey.value !== submittedRequestKey || JSON.stringify(repayData()) !== signature
          || (!attemptedPayload.value && (debtLoading.value || debtError.value))) return
      if (attemptedPayload.value && attemptedPayload.value !== signature) {
        ElMessage.warning('本次收款已提交，请保持原金额和收款方式重试，避免重复收款')
        return
      }
      loading.value = true
      attemptedPayload.value = signature
      try {
        await repayCredit({...data, requestKey: submittedRequestKey})
        ElMessage.success('还款成功')
        if (requestKey.value === submittedRequestKey && props.customerId === data.customerId) {
          requestKey.value = ''
          attemptedPayload.value = ''
          emit('success')
          emit('update:modelValue', false)
        }
      } catch (error: any) {
        ElMessage.error(error?.message || '还款失败，可按原内容重试本次收款')
        const businessCode = Number(error?.apiBusinessCode)
        if ((businessCode === 10001 || businessCode >= 400 && businessCode < 500)
            && requestKey.value === submittedRequestKey && props.customerId === data.customerId
            && operationCustomerId.value === data.customerId && contextVersion === debtRequestVersion) {
          requestKey.value = ''
          attemptedPayload.value = ''
          if (props.modelValue) await beginRepayment()
        }
      } finally {
        loading.value = false
      }
    })
  } finally {
    validating.value = false
  }
}
</script>

<style scoped>
.el-dialog__body {
  padding-top: 8px;
}
</style>


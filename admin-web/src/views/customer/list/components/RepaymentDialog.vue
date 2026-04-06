<template>
  <el-dialog :model-value="visible" title="录入还款" width="480px" @close="onClose">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="客户" prop="customerName">
        <el-input v-model="form.customerName" disabled />
      </el-form-item>

      <el-form-item label="当前欠款" prop="currentTotalDebt">
        <el-input v-model="displayDebt" disabled />
      </el-form-item>

      <el-form-item label="还款金额" prop="amount">
        <el-input-number
          v-model="form.amount"
          :min="0.01"
          :max="currentTotalDebt || form.amount"
          :precision="2"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="还款方式" prop="paymentMethod">
        <el-radio-group v-model="form.paymentMethod">
          <el-radio :label="1">现金</el-radio>
          <el-radio :label="2">微信</el-radio>
          <el-radio :label="3">支付宝</el-radio>
          <el-radio :label="4">银行转账</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="外部单号" prop="transactionId">
        <el-input v-model="form.transactionId" placeholder="可选：第三方交易单号" />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" rows="2" placeholder="选填" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {ref, watch, computed} from 'vue'
import {ElMessage} from 'element-plus'
import {repayCredit} from '@/api/credit'
import {useUserStore} from '@/store/user'

const emit = defineEmits(['update:modelValue', 'success'])

const props = defineProps<{
  modelValue: boolean
  customerId: number
  customerName?: string
  currentTotalDebt?: number
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const formRef = ref()
const loading = ref(false)

const form = ref({
  customerName: props.customerName || '',
  amount: props.currentTotalDebt ? Number(props.currentTotalDebt) : 0,
  paymentMethod: 1,
  transactionId: '',
  remark: ''
})

watch(() => props.currentTotalDebt, (v) => {
  form.value.amount = v ? Number(v) : 0
})

watch(() => props.customerName, (v) => {
  form.value.customerName = v || ''
})

const displayDebt = computed(() => {
  return props.currentTotalDebt != null ? `￥${Number(props.currentTotalDebt).toFixed(2)}` : '￥0.00'
})

const rules = {
  amount: [
    { required: true, message: '请输入还款金额', trigger: 'blur' },
    { validator: (_rule: any, value: any, callback: any) => {
        const num = Number(value)
        if (!value || isNaN(num) || num <= 0) {
          callback(new Error('还款金额必须大于0'))
          return
        }
        if (props.currentTotalDebt != null && num > Number(props.currentTotalDebt)) {
          callback(new Error('还款金额不能大于当前欠款'))
          return
        }
        callback()
      }, trigger: 'blur'
    }
  ],
  paymentMethod: [{ required: true, message: '请选择还款方式', trigger: 'change' }]
}

const onClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    loading.value = true
    try {
      const userStore = useUserStore()
      const createdBy = userStore.userInfo ? userStore.userInfo.id : undefined
      // try to derive storeId from userInfo.extra.storeId or fallback to 0
      const maybeExtra: any = userStore.userInfo as any
      const storeId = (maybeExtra && maybeExtra.extra && maybeExtra.extra.storeId) ? maybeExtra.extra.storeId
        : (maybeExtra && (maybeExtra.storeId as any)) ? maybeExtra.storeId : 0

      await repayCredit({
        customerId: props.customerId,
        storeId: storeId,
        amount: Number(form.value.amount),
        paymentMethod: Number(form.value.paymentMethod),
        transactionId: form.value.transactionId || undefined,
        remark: form.value.remark || undefined,
        createdBy: createdBy
      })
      ElMessage.success('还款成功')
      emit('success')
      emit('update:modelValue', false)
    } catch (err: any) {
      ElMessage.error(err?.message || '还款失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.el-dialog__body {
  padding-top: 8px;
}
</style>



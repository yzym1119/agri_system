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

      <el-form-item label="门店" prop="storeId">
        <el-select
          v-model="form.storeId"
          clearable
          filterable
          placeholder="请选择还款门店"
          style="width: 100%"
        >
          <el-option
            v-for="store in storeOptions"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
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
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {repayCredit} from '@/api/credit'
import {getAllStores} from '@/api/store'
import {useUserStore} from '@/store/user'
import type {StoreDetailVO} from '@/types/store'

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
const storeOptions = ref<StoreDetailVO[]>([])
const userStore = useUserStore()

const form = reactive({
  customerName: props.customerName || '',
  amount: props.currentTotalDebt ? Number(props.currentTotalDebt) : 0,
  storeId: undefined as number | undefined,
  paymentMethod: 1,
  transactionId: '',
  remark: ''
})

watch(() => props.currentTotalDebt, (v) => {
  form.amount = v ? Number(v) : 0
})

watch(() => props.customerName, (v) => {
  form.customerName = v || ''
})

watch(() => props.modelValue, (value) => {
  if (!value) return
  form.customerName = props.customerName || ''
  form.amount = props.currentTotalDebt ? Number(props.currentTotalDebt) : 0
  form.paymentMethod = 1
  form.transactionId = ''
  form.remark = ''
  if (!form.storeId) {
    form.storeId = resolveDefaultStoreId()
  }
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
  storeId: [{ required: true, message: '请选择还款门店', trigger: 'change' }],
  paymentMethod: [{ required: true, message: '请选择还款方式', trigger: 'change' }]
}

const resolveDefaultStoreId = () => {
  const maybeExtra: any = userStore.userInfo as any
  return maybeExtra?.extra?.storeId || maybeExtra?.storeId || storeOptions.value[0]?.id
}

const loadStores = async () => {
  try {
    storeOptions.value = await getAllStores()
    if (!form.storeId) {
      form.storeId = resolveDefaultStoreId()
    }
  } catch (error) {
    console.error('加载门店列表失败:', error)
    storeOptions.value = []
  }
}

const onClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    loading.value = true
    try {
      const createdBy = userStore.userInfo ? userStore.userInfo.id : undefined

      await repayCredit({
        customerId: props.customerId,
        storeId: form.storeId!,
        amount: Number(form.amount),
        paymentMethod: Number(form.paymentMethod),
        transactionId: form.transactionId || undefined,
        remark: form.remark || undefined,
        createdBy
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

onMounted(() => {
  loadStores()
})
</script>

<style scoped>
.el-dialog__body {
  padding-top: 8px;
}
</style>


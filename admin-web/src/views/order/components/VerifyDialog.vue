<template>
  <el-dialog
    v-model="dialogVisible"
    title="自提核销"
    width="400px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-if="order" style="margin-bottom: 20px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="自提码">
          <span style="font-size: 24px; font-weight: bold; color: #409eff">{{ order.pickupCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="客户">{{ order.customerName || '散客' }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">¥{{ formatMoney(order.payAmount) }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="核销码" prop="pickupCode">
        <el-input
          v-model="form.pickupCode"
          placeholder="请输入6位自提核销码"
          maxlength="6"
          style="width: 100%"
          @keyup.enter="handleSubmit"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">核销</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {reactive, ref, watch} from 'vue'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'
import {verifyPickup} from '@/api/order'

interface Props {
  modelValue: boolean
  order: any | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  pickupCode: ''
})

const rules = reactive<FormRules>({
  pickupCode: [
    { required: true, message: '请输入自提核销码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '核销码必须为6位数字', trigger: 'blur' }
  ]
})

watch(
  () => props.modelValue,
  (val) => {
    dialogVisible.value = val
    if (val && props.order) {
      form.pickupCode = props.order.pickupCode || ''
    } else {
      resetForm()
    }
  }
)

watch(dialogVisible, (val) => {
  emit('update:modelValue', val)
})

const resetForm = () => {
  form.pickupCode = ''
  formRef.value?.clearValidate()
}

const formatMoney = (amount: number | string | null | undefined) => {
  if (amount == null) return '0.00'
  return Number(amount).toFixed(2)
}

const handleClose = () => {
  dialogVisible.value = false
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      await verifyPickup({
        pickupCode: form.pickupCode
      })
      ElMessage.success('核销成功')
      emit('success')
      handleClose()
    } catch (error) {
      console.error('核销失败:', error)
      ElMessage.error('核销失败，请检查核销码是否正确')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped lang="scss">
</style>










<template>
  <el-dialog
    v-model="dialogVisible"
    :title="mode === 'express' ? '发货' : '派单'"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item v-if="mode === 'express'" label="物流公司" prop="logisticsCompany">
        <el-input v-model="form.logisticsCompany" placeholder="请输入物流公司名称" />
      </el-form-item>
      <el-form-item v-if="mode === 'express'" label="物流单号" prop="trackingNo">
        <el-input v-model="form.trackingNo" placeholder="请输入物流单号" />
      </el-form-item>
      <el-form-item v-if="mode === 'local'" label="配送员" prop="deliveryStaffId">
        <el-select v-model="form.deliveryStaffId" placeholder="请选择配送员" style="width: 100%">
          <el-option
            v-for="staff in deliveryStaffOptions"
            :key="staff.id"
            :label="staff.name"
            :value="staff.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="order" label="收货地址">
        <div style="color: #606266">
          <div>{{ order.receiverAddress }}</div>
          <div style="margin-top: 4px; font-size: 12px">
            {{ order.receiverName }} {{ order.receiverPhone }}
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'
import {shipOrder} from '@/api/order'
import {getStaffList} from '@/api/staff'
import type {OrderPageItem} from '@/types/order'

interface Props {
  modelValue: boolean
  order: OrderPageItem | null
  mode: 'express' | 'local'
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
const deliveryStaffOptions = ref<any[]>([])

const form = reactive({
  logisticsCompany: '',
  trackingNo: '',
  deliveryStaffId: null as number | null
})

const rules = computed<FormRules>(() => {
  const baseRules: FormRules = {}
  if (props.mode === 'express') {
    baseRules.logisticsCompany = [
      { required: true, message: '请输入物流公司名称', trigger: 'blur' }
    ]
    baseRules.trackingNo = [
      { required: true, message: '请输入物流单号', trigger: 'blur' }
    ]
  } else if (props.mode === 'local') {
    baseRules.deliveryStaffId = [
      { required: true, message: '请选择配送员', trigger: 'change' }
    ]
  }
  return baseRules
})

watch(
  () => props.modelValue,
  (val) => {
    dialogVisible.value = val
    if (val) {
      resetForm()
    }
  }
)

watch(dialogVisible, (val) => {
  emit('update:modelValue', val)
})

const resetForm = () => {
  form.logisticsCompany = ''
  form.trackingNo = ''
  form.deliveryStaffId = null
  formRef.value?.clearValidate()
}

const handleClose = () => {
  dialogVisible.value = false
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (!props.order) {
      ElMessage.error('订单信息不存在')
      return
    }

    submitting.value = true
    try {
      const data: any = {}
      if (props.mode === 'express') {
        data.logisticsCompany = form.logisticsCompany
        data.trackingNo = form.trackingNo
      } else {
        data.deliveryStaffId = form.deliveryStaffId
      }

      await shipOrder(props.order.id, data)
      ElMessage.success(props.mode === 'express' ? '发货成功' : '派单成功')
      emit('success')
      handleClose()
    } catch (error) {
      console.error('操作失败:', error)
      ElMessage.error((error as any)?.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

// 加载配送员列表
const loadDeliveryStaff = async () => {
  try {
    const response = await getStaffList({
      pageNum: 1,
      pageSize: 1000,
      status: 1 // 仅查询在职员工
    })
    deliveryStaffOptions.value = response?.records || []
  } catch (error) {
    console.error('加载配送员列表失败:', error)
  }
}

onMounted(() => {
  loadDeliveryStaff()
})
</script>

<style scoped lang="scss">
</style>

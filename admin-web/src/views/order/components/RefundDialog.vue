<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="700px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-if="order" style="margin-bottom: 20px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="应收净额">¥{{ formatMoney(orderPaymentSummary.netReceivable) }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ order.customerName || '散客' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatDateTime(order.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <!-- 商品列表 -->
      <el-form-item :label="itemSectionLabel" required>
        <el-table :data="form.items" border style="width: 100%">
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="productName" label="商品名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="quantity" label="购买数量" width="100" align="right">
            <template #default="{ row }">
              {{ row.quantity }} {{ row.unit }}
            </template>
          </el-table-column>
          <el-table-column label="剩余可退" width="120" align="right">
            <template #default="{ row }">
              {{ row.refundableQuantity }} {{ row.unit }}
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="单价" width="100" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column :label="quantityColumnLabel" width="150" align="center">
            <template #default="{ row, $index }">
              <el-input-number
                v-model="form.items[$index].returnQuantity"
                :min="0"
                :max="row.refundableQuantity"
                :precision="Number.isInteger(row.refundableQuantity) ? 0 : 4"
                :step="Number.isInteger(row.refundableQuantity) ? 1 : 0.01"
                style="width: 100%"
                @change="calculateRefundAmount"
              />
            </template>
          </el-table-column>
          <el-table-column :label="amountColumnLabel" width="120" align="right">
            <template #default="{ row }">
              <span style="font-weight: 600">¥{{ formatMoney(row.refundAmount) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <!-- 退款金额 -->
      <el-form-item label="退款金额" prop="refundAmount">
        <el-input-number
          v-model="form.refundAmount"
          :min="0.01"
          :max="maxRefundAmount"
          :precision="2"
          :step="0.01"
          style="width: 100%"
          placeholder="请输入退款金额"
        />
        <div style="font-size: 12px; color: #909399; margin-top: 4px">
          最大可退：¥{{ formatMoney(maxRefundAmount) }}
        </div>
      </el-form-item>

      <!-- 退货原因 -->
      <el-form-item :label="reasonLabel" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          :placeholder="reasonPlaceholder"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'
import {createAfterSales} from '@/api/afterSales'
import {getOrderDetail, refundOrder} from '@/api/order'
import {getOrderPaymentSummary} from '@/utils/orderPayment'
import type {OrderBase, OrderDetail} from '@/types/order'

interface Props {
  modelValue: boolean
  order: OrderBase | null
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
const orderDetail = ref<OrderDetail | null>(null)

interface RefundItem {
  orderItemId: number
  productName: string
  quantity: number
  refundedQuantity: number
  refundableQuantity: number
  unit: string
  unitPrice: number
  refundedAmount: number
  refundableAmount: number
  returnQuantity: number
  refundAmount: number
}

const form = reactive<{
  items: RefundItem[]
  refundAmount: number
  reason: string
}>({
  items: [],
  refundAmount: 0,
  reason: ''
})

const directRefundStatuses = [2, 3, 4]
const useDirectRefund = computed(() => directRefundStatuses.includes(Number(props.order?.orderStatus || 0)))
const dialogTitle = computed(() => useDirectRefund.value ? '订单退款' : '退货退款')
const itemSectionLabel = computed(() => useDirectRefund.value ? '退款商品' : '退货商品')
const quantityColumnLabel = computed(() => useDirectRefund.value ? '退款数量' : '退货数量')
const amountColumnLabel = computed(() => useDirectRefund.value ? '退款金额' : '退货金额')
const reasonLabel = computed(() => useDirectRefund.value ? '退款原因' : '退货原因')
const reasonPlaceholder = computed(() => useDirectRefund.value ? '请输入退款原因' : '请输入退货原因')
const orderPaymentSummary = computed(() => getOrderPaymentSummary(props.order))

const maxRefundAmount = computed(() => {
  return form.items.reduce((sum, item) => sum + Number(item.refundableAmount || 0), 0)
})

const rules = reactive<FormRules>({
  refundAmount: [
    { required: true, message: '请输入退款金额', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value <= 0) {
          callback(new Error('退款金额必须大于0'))
        } else if (value > maxRefundAmount.value) {
          callback(new Error(`退款金额不能超过${formatMoney(maxRefundAmount.value)}`))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  reason: [
    {
      validator: (rule, value, callback) => {
        if (!value || !String(value).trim()) {
          callback(new Error(reasonPlaceholder.value))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

watch(
  () => props.modelValue,
  (val) => {
    dialogVisible.value = val
    if (val && props.order) {
      loadOrderDetail()
    } else {
      resetForm()
    }
  }
)

watch(dialogVisible, (val) => {
  emit('update:modelValue', val)
})

const loadOrderDetail = async () => {
  if (!props.order?.id) return

  try {
    orderDetail.value = await getOrderDetail(props.order.id)
    if (orderDetail.value && orderDetail.value.items) {
      // 使用 reactive 确保响应式
      form.items = orderDetail.value.items
        .map((item: any) => {
          const quantity = Number(item.quantity)
          return {
            orderItemId: item.id,
            productName: item.productName,
            quantity: quantity,
            refundedQuantity: Number(item.refundedQuantity) || 0,
            refundableQuantity: Number(item.refundableQuantity ?? item.quantity) || 0,
            unit: item.unit || '',
            unitPrice: Number(item.unitPrice) || 0,
            refundedAmount: Number(item.refundedAmount) || 0,
            refundableAmount: Number(item.refundableAmount ?? item.totalPrice) || 0,
            returnQuantity: 0,
            refundAmount: 0
          }
        })
        .filter((item: RefundItem) => item.refundableQuantity > 0 && item.refundableAmount > 0)
    }
  } catch (error) {
    console.error('加载订单详情失败:', error)
    ElMessage.error((error as any)?.message || '加载订单详情失败')
  }
}

const resetForm = () => {
  form.items = []
  form.refundAmount = 0
  form.reason = ''
  formRef.value?.clearValidate()
}

const formatMoney = (amount: number | string | null | undefined) => {
  if (amount == null) return '0.00'
  return Number(amount).toFixed(2)
}

const formatDateTime = (dateTime: string | null | undefined) => {
  if (!dateTime) return '-'
  return dateTime.replace('T', ' ')
}

const calculateRefundAmount = () => {
  // 计算每个商品的退货金额
  form.items.forEach((item) => {
    if (item.returnQuantity > 0) {
      item.refundAmount = item.unitPrice * item.returnQuantity
      if (item.refundAmount > item.refundableAmount) {
        item.refundAmount = item.refundableAmount
      }
    } else {
      item.refundAmount = 0
    }
  })

  // 计算总退款金额
  const total = form.items.reduce((sum, item) => sum + item.refundAmount, 0)
  form.refundAmount = Math.min(total, maxRefundAmount.value)
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

    // 检查是否有退款/退货商品
    const validItems = form.items.filter((item) => item.returnQuantity > 0)
    if (validItems.length === 0) {
      ElMessage.warning(`请至少选择一个商品进行${useDirectRefund.value ? '退款' : '退货'}`)
      return
    }

    submitting.value = true
    try {
      const data = {
        orderId: props.order.id,
        refundAmount: form.refundAmount,
        refundReason: form.reason,
        items: validItems.map((item) => ({
          orderItemId: item.orderItemId,
          quantity: item.returnQuantity,
          refundAmount: item.refundAmount
        }))
      }

      if (useDirectRefund.value) {
        await refundOrder(props.order.id, data)
        ElMessage.success('订单退款成功')
      } else {
        await createAfterSales({
          orderId: data.orderId,
          refundAmount: data.refundAmount,
          reason: form.reason,
          items: data.items
        })
        ElMessage.success('退货退款成功')
      }
      emit('success')
      handleClose()
    } catch (error) {
      console.error(`${useDirectRefund.value ? '订单退款' : '退货退款'}失败:`, error)
      ElMessage.error((error as any)?.message || `${useDirectRefund.value ? '订单退款' : '退货退款'}失败`)
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped lang="scss">
</style>

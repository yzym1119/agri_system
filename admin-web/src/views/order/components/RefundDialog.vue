<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="min(1120px, 96vw)"
    class="refund-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting && !validating"
    :show-close="!submitting && !validating"
    @close="handleClose"
  >
    <div v-if="displayOrder" style="margin-bottom: 20px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="订单号">{{ displayOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="应收净额">¥{{ formatMoney(orderPaymentSummary.netReceivable) }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ displayOrder.customerName || '散客' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatDateTime(displayOrder.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <el-form v-loading="detailsLoading" ref="formRef" :model="form" :rules="rules" label-width="120px">
      <section class="refund-items" aria-label="退货商品与数量">
        <h3>{{ itemSectionLabel }}</h3>
        <p class="quantity-guidance">数量按原订单单位填写，不能在退货时改单位。整件不可拆分；半箱等数量须按商品实际包装核对。</p>
        <div v-for="row in form.items" :key="row.orderItemId" class="refund-line">
          <div class="refund-product">
            <strong>{{ row.productName }}</strong>
            <span v-if="row.packageGroupNo" class="line-muted">套餐：{{ row.packageName }}</span>
            <span v-if="row.baseUnitName && row.allowFractionalBaseQuantity != null" class="line-muted">{{ row.allowFractionalBaseQuantity ? `可按计量数量退回（${row.baseUnitName}）` : `须退完整${row.baseUnitName}，不可拆零` }}</span>
            <span class="line-muted">原购 {{ formatQuantity(row.quantity) }} {{ row.unit }} · 剩余可退 {{ formatQuantity(row.refundableQuantity) }} {{ row.unit }}</span>
          </div>
          <div class="refund-quantity">
            <label :for="`return-quantity-${row.orderItemId}`">{{ quantityColumnLabel }}（{{ row.unit || '原单位' }}）</label>
            <el-input :id="`return-quantity-${row.orderItemId}`"
              :model-value="quantityDrafts[row.orderItemId] ?? String(row.returnQuantity)"
              :aria-label="`${row.productName}${quantityColumnLabel}，单位${row.unit}`"
              inputmode="decimal" :disabled="submitting || validating || detailsLoading || !!pendingFormalRequest"
              @input="(value: string) => quantityDrafts[row.orderItemId] = value"
              @change="(value: string) => setReturnQuantity(row, value)"
              @blur="setReturnQuantity(row, quantityDrafts[row.orderItemId] ?? String(row.returnQuantity))">
              <template #append>{{ row.unit }}</template>
            </el-input>
            <el-button link type="primary" :disabled="submitting || validating || detailsLoading || !!pendingFormalRequest" @click="setReturnQuantity(row, String(row.refundableQuantity))">全部退回</el-button>
          </div>
          <div class="refund-line-amount"><span>{{ amountColumnLabel }}</span><strong>¥{{ formatMoney(row.refundAmount) }}</strong></div>
        </div>
        <el-empty v-if="!detailsLoading && !form.items.length" description="没有剩余可退商品" :image-size="48" />
      </section>

      <el-alert v-if="pendingFormalRequest" title="本次退货结果尚未确认，请按原请求核对。系统不会换新报价再次退货。" type="warning" :closable="false" style="margin-bottom: 16px" />
      <el-table v-if="completedAfterSales.length" :data="completedAfterSales" border size="small" style="margin-bottom: 16px">
        <el-table-column prop="afterSalesNo" label="已完成退货" />
        <el-table-column label="成交冲销"><template #default="{ row }">¥{{ formatMoney(row.refundAmount) }}</template></el-table-column>
        <el-table-column label="冲欠款"><template #default="{ row }">{{ row.creditReductionAmount == null ? '待核对' : '¥' + formatMoney(row.creditReductionAmount) }}</template></el-table-column>
        <el-table-column label="退已收款"><template #default="{ row }">{{ row.cashRefundAmount == null ? '待核对' : '¥' + formatMoney(row.cashRefundAmount) }}</template></el-table-column>
      </el-table>
      <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" style="margin-bottom: 16px" />
      <div v-loading="previewLoading" style="width: 100%; margin-bottom: 20px">
        <el-descriptions v-if="refundPreview" :column="2" border size="small">
          <el-descriptions-item label="本次成交冲销">¥{{ formatMoney(refundPreview.refundAmount) }}</el-descriptions-item>
          <el-descriptions-item label="本次冲欠款">¥{{ formatMoney(refundPreview.creditReductionAmount) }}</el-descriptions-item>
          <el-descriptions-item label="本次退已收款">¥{{ formatMoney(refundPreview.cashRefundAmount) }}</el-descriptions-item>
          <el-descriptions-item label="原单当前欠款">¥{{ formatMoney(refundPreview.remainingDebt) }}</el-descriptions-item>
        </el-descriptions>
        <div v-else class="preview-hint">选择退货数量后，系统核对原单欠款和已收资金。</div>
        <div v-if="refundPreview" class="preview-hint">退已收款包括现金、原支付和已分配到此单的还款，不会退回其他订单资金。</div>
        <el-table v-if="refundPreview?.sources?.length" :data="refundPreview.sources" border size="small" style="margin-top: 10px">
          <el-table-column prop="paymentMethodName" label="原收款来源" />
          <el-table-column label="本次退回" align="right"><template #default="{ row }">¥{{ formatMoney(row.amount) }}</template></el-table-column>
          <el-table-column label="退款前可退" align="right"><template #default="{ row }">¥{{ formatMoney(row.refundableAmount) }}</template></el-table-column>
        </el-table>
        <el-button v-if="previewError" text type="primary" :loading="previewLoading" @click="calculateRefundAmount">重新核对金额</el-button>
      </div>

      <!-- 退货原因 -->
      <el-form-item :label="reasonLabel" prop="reason">
        <el-input
          v-model="form.reason"
          :disabled="submitting || validating || !!pendingFormalRequest"
          type="textarea"
          :rows="3"
          :placeholder="reasonPlaceholder"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting || validating" @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting || validating" :disabled="previewLoading || detailsLoading || (!refundPreview && !pendingFormalRequest) || !!(pendingFormalRequest && pendingFormalRequest.data.orderId !== props.order?.id)" @click="handleSubmit">{{ pendingFormalRequest ? '核对本次退货' : '确定' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {useUserStore} from '@/store/user'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'
import {createAfterSales} from '@/api/afterSales'
import {getOrderDetail, getRefundPreview, refundOrder} from '@/api/order'
import {isWholeBaseQuantity} from '@/utils/quantity'
import {formatQuantity} from '@/utils/number'
import {getOrderPaymentSummary} from '@/utils/orderPayment'
import type {OrderBase, OrderDetail, RefundPreview} from '@/types/order'

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
const validating = ref(false)
const orderDetail = ref<OrderDetail | null>(null)
const userStore = useUserStore()
interface RefundSubmission {
  direct: boolean
  operatorId?: number
  order: OrderBase
  displayItems: RefundItem[]
  preview: RefundPreview
  data: {orderId: number; quoteId: string; refundAmount: number; refundReason: string;
    items: Array<{orderItemId: number; quantity: number; refundAmount: number}>}
}
const pendingFormalRequest = ref<RefundSubmission | null>(null)
const displayOrder = computed(() => pendingFormalRequest.value?.order ?? props.order)
const completedAfterSales = computed(() => (orderDetail.value?.afterSalesList || []).filter(row => row.status === 50))
const definiteBusinessRejection = (error: any) => {
  const code = Number(error?.apiBusinessCode)
  return code === 10001 || (code >= 400 && code < 500) || String(error?.message || '').startsWith('REFUND_QUOTE_STALE')
}
const detailsLoading = ref(false)
const refundPreview = ref<RefundPreview | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
let previewVersion = 0
let detailVersion = 0
let dialogSessionVersion = 0
let previewSignature = ''
const selectionSignature = () => JSON.stringify({orderId: props.order?.id,
  items: form.items.filter(item => item.returnQuantity > 0).map(item => ({orderItemId: item.orderItemId, quantity: item.returnQuantity}))})

interface RefundItem {
  orderItemId: number
  baseUnitName?: string | null
  allowFractionalBaseQuantity?: boolean | null
  originalToBaseRatio?: number | null
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
  totalPrice: number
  packageGroupNo?: number
  packageName?: string
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

const quantityDrafts = reactive<Record<number, string>>({})
const setReturnQuantity = async (item: RefundItem, input: string) => {
  if (submitting.value || validating.value || detailsLoading.value || pendingFormalRequest.value) return
  const raw = String(input).trim(), quantity = Number(raw)
  delete quantityDrafts[item.orderItemId]
  if (!/^\d+(\.\d{1,4})?$/.test(raw) || !Number.isFinite(quantity) || quantity < 0 || quantity > item.refundableQuantity) {
    ElMessage.warning(`数量须在0至${item.refundableQuantity}${item.unit}之间，最多4位小数`)
    return
  }
  if (item.allowFractionalBaseQuantity === false && item.originalToBaseRatio != null && !isWholeBaseQuantity(raw, item.originalToBaseRatio)) {
    ElMessage.warning(`换算后必须是完整的${item.baseUnitName || '最小单位'}，不能拆成小数`)
    return
  }
  if (quantity === item.returnQuantity) return
  item.returnQuantity = quantity
  await calculateRefundAmount()
}

const directRefundStatuses = [2, 3, 4]
const useDirectRefund = computed(() => directRefundStatuses.includes(Number(props.order?.orderStatus || 0)))
const dialogTitle = computed(() => useDirectRefund.value ? '订单退款' : '退货退款')
const itemSectionLabel = computed(() => useDirectRefund.value ? '退款商品' : '退货商品')
const quantityColumnLabel = computed(() => useDirectRefund.value ? '退款数量' : '退货数量')
const amountColumnLabel = computed(() => '成交冲销')
const reasonLabel = computed(() => useDirectRefund.value ? '退款原因' : '退货原因')
const reasonPlaceholder = computed(() => useDirectRefund.value ? '请输入退款原因' : '请输入退货原因')
const orderPaymentSummary = computed(() => getOrderPaymentSummary(displayOrder.value))

const rules = reactive<FormRules>({
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
  () => [props.modelValue, props.order?.id] as const,
  ([val]) => {
    ++dialogSessionVersion
    dialogVisible.value = val
    if (val && props.order) {
      resetForm()
      if (pendingFormalRequest.value) {
        if (pendingFormalRequest.value.data.orderId !== props.order.id) previewError.value = '上一订单退货结果尚待核对，请返回原订单；当前不能发起另一笔退货'
        return
      }
      void loadOrderDetail()
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
  const orderId = props.order.id
  const version = ++detailVersion
  detailsLoading.value = true
  try {
    const detail = await getOrderDetail(orderId)
    if (version !== detailVersion || !props.modelValue || props.order?.id !== orderId) return
    orderDetail.value = detail
    if (orderDetail.value && orderDetail.value.items) {
      // 使用 reactive 确保响应式
      form.items = orderDetail.value.items
        .map((item: any) => {
          const quantity = Number(item.quantity)
          return {
            orderItemId: item.id,
            baseUnitName: item.baseUnitName,
            allowFractionalBaseQuantity: item.allowFractionalBaseQuantity,
            originalToBaseRatio: item.originalToBaseRatio,
            productName: item.productName,
            quantity: quantity,
            refundedQuantity: Number(item.refundedQuantity) || 0,
            refundableQuantity: Number(item.refundableQuantity ?? item.quantity) || 0,
            unit: item.unit || '',
            unitPrice: Number(item.unitPrice) || 0,
            totalPrice: Number(item.totalPrice) || 0,
            packageGroupNo: item.packageGroupNo || undefined,
            packageName: item.packageName || undefined,
            refundedAmount: Number(item.refundedAmount) || 0,
            refundableAmount: Number(item.refundableAmount ?? item.totalPrice) || 0,
            returnQuantity: 0,
            refundAmount: 0
          }
        })
        .filter((item: RefundItem) => item.refundableQuantity > 0)
    }
  } catch (error) {
    console.error('加载订单详情失败:', error)
    if (version === detailVersion) ElMessage.error((error as any)?.message || '加载订单详情失败')
  } finally {
    if (version === detailVersion) detailsLoading.value = false
  }
}

const resetForm = () => {
  Object.keys(quantityDrafts).forEach(key => delete quantityDrafts[Number(key)])
  ++previewVersion
  ++detailVersion
  detailsLoading.value = false
  previewLoading.value = false
  previewError.value = ''
  if (pendingFormalRequest.value) {
    const request = pendingFormalRequest.value
    form.items = request.displayItems.map(item => ({...item}))
    form.reason = request.data.refundReason
    form.refundAmount = request.data.refundAmount
    refundPreview.value = request.preview
    return
  }
  refundPreview.value = null
  previewSignature = ''
  orderDetail.value = null
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

const calculateRefundAmount = async () => {
  if (pendingFormalRequest.value) return
  const version = ++previewVersion
  const orderId = props.order?.id
  const items = form.items.filter(item => item.returnQuantity > 0)
    .map(item => ({orderItemId: item.orderItemId, quantity: item.returnQuantity}))
  const signature = selectionSignature()
  refundPreview.value = null
  previewSignature = ''
  previewError.value = ''
  form.refundAmount = 0
  form.items.forEach(item => { item.refundAmount = 0 })
  if (!orderId || !items.length) { previewLoading.value = false; return }
  previewLoading.value = true
  try {
    const result = await getRefundPreview(orderId, {items})
    if (version !== previewVersion || signature !== selectionSignature() || !props.modelValue) return
    if (!result.quoteId || result.orderId !== orderId || !Array.isArray(result.items)
        || result.items.length !== items.length || items.some(requested => !result.items.some(item =>
          item.orderItemId === requested.orderItemId && Number(item.quantity) === requested.quantity))) {
      throw new Error('退货预览与所选商品不一致，请重新核对')
    }
    form.items.forEach(item => {
      const quoted = result.items.find(row => row.orderItemId === item.orderItemId)
      item.refundAmount = quoted ? Number(quoted.refundAmount) : 0
    })
    form.refundAmount = Number(result.refundAmount)
    refundPreview.value = result
    previewSignature = signature
  } catch (error: any) {
    if (version === previewVersion) previewError.value = error?.message || '无法核对退货资金，请重试'
  } finally {
    if (version === previewVersion) previewLoading.value = false
  }
}

const handleClose = () => {
  if (submitting.value) return
  dialogVisible.value = false
  resetForm()
}

const executeRefundRequest = async (request: RefundSubmission) => {
  if (submitting.value) return
  if (request.operatorId !== userStore.userInfo?.id) {
    ElMessage.warning('本次退货需由原操作员工核对，不能换员工重放')
    return
  }
  submitting.value = true
  pendingFormalRequest.value = request
  try {
    const data = request.data
    if (request.direct) await refundOrder(data.orderId, data)
    else await createAfterSales({orderId: data.orderId, quoteId: data.quoteId, refundAmount: data.refundAmount,
      reason: data.refundReason, items: data.items})
    pendingFormalRequest.value = null
    ElMessage.success(request.direct ? '订单退款成功' : '退货退款成功')
    submitting.value = false
    if (props.order?.id === data.orderId) {
      emit('success')
      handleClose()
    } else {
      resetForm()
      await loadOrderDetail()
    }
  } catch (error: any) {
    console.error('退货确认失败:', error)
    ElMessage.error(error?.message || '本次退货结果未确认，请按原请求核对')
    if (definiteBusinessRejection(error)) {
      pendingFormalRequest.value = null
      ++previewVersion
      refundPreview.value = null
      previewSignature = ''
      form.refundAmount = 0
      await loadOrderDetail()
      previewError.value = '已重新读取已完成退货和剩余数量，请重新选择后核对金额'
    }
  } finally {
    submitting.value = false
  }
}
const handleSubmit = async () => {
  if (submitting.value || validating.value || !props.modelValue || !formRef.value) return
  if (pendingFormalRequest.value) {
    if (pendingFormalRequest.value.data.orderId !== props.order?.id) return ElMessage.warning('请返回原订单核对本次退货')
    await executeRefundRequest(pendingFormalRequest.value)
    return
  }
  if (previewLoading.value || !refundPreview.value || previewSignature !== selectionSignature()) {
    ElMessage.warning('请先核对最新退货金额，再确认操作')
    return
  }
  if (!props.order) { ElMessage.error('订单信息不存在'); return }
  const items = form.items.filter(item => item.returnQuantity > 0)
  if (!items.length) { ElMessage.warning('请至少选择一件商品退货'); return }
  const session = dialogSessionVersion
  const version = previewVersion
  const signature = selectionSignature()
  const request: RefundSubmission = {direct: useDirectRefund.value, operatorId: userStore.userInfo?.id,
    order: {...props.order}, displayItems: form.items.map(item => ({...item})), preview: refundPreview.value,
    data: {orderId: props.order.id, quoteId: refundPreview.value.quoteId, refundAmount: form.refundAmount,
      refundReason: form.reason, items: items.map(item => ({orderItemId: item.orderItemId,
        quantity: item.returnQuantity, refundAmount: item.refundAmount}))}}
  validating.value = true
  try {
    await formRef.value.validate(async (valid) => {
      if (!valid) return
      if (!props.modelValue || session !== dialogSessionVersion || version !== previewVersion
          || props.order?.id !== request.data.orderId || refundPreview.value?.quoteId !== request.data.quoteId
          || previewLoading.value || signature !== selectionSignature() || previewSignature !== signature
          || form.reason !== request.data.refundReason || userStore.userInfo?.id !== request.operatorId
          || useDirectRefund.value !== request.direct) {
        ElMessage.warning('退货操作已变化，请重新核对并确认')
        return
      }
      await executeRefundRequest(request)
    })
  } finally {
    validating.value = false
  }
}
</script>

<style scoped lang="scss">
.preview-hint { margin-top: 8px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
</style>

<style scoped>
.refund-items { margin: 0 0 20px; min-width: 0; }
.refund-items h3 { font-size: 15px; margin: 0 0 8px; }
.quantity-guidance, .line-muted { color: #626f7e; font-size: 13px; line-height: 1.6; }
.quantity-guidance { margin: 0 0 12px; }
.refund-line { display: grid; grid-template-columns: minmax(0, 1fr) 200px 120px; gap: 20px; align-items: center; padding: 16px 0; border-bottom: 1px solid #e5e7eb; }
.refund-product { display: flex; flex-direction: column; gap: 6px; overflow-wrap: anywhere; }
.refund-quantity label { display: block; font-size: 13px; color: #4b5563; margin-bottom: 6px; }
.refund-line-amount { text-align: right; display: flex; flex-direction: column; gap: 8px; }
.refund-line-amount span { color: #626f7e; font-size: 13px; }
:deep(.el-descriptions__content) { overflow-wrap: anywhere; }
@media (max-width: 700px) { .refund-line { grid-template-columns: minmax(0,1fr) 100px; gap: 12px; } .refund-product { grid-column: 1 / -1; } .refund-quantity { min-width: 0; } }
</style>
<style>
.el-dialog.refund-dialog { display: flex; flex-direction: column; overflow: hidden; }
.el-dialog.refund-dialog > .el-dialog__header, .el-dialog.refund-dialog > .el-dialog__footer { flex-shrink: 0; }
.el-dialog.refund-dialog > .el-dialog__body { overflow-y: auto; min-height: 0; }
</style>

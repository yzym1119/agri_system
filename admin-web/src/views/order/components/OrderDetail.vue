<template>
  <el-drawer v-model="drawerVisible" title="订单详情" size="60%" @close="handleClose">
    <div v-if="loading" style="text-align: center; padding: 40px">
      <el-icon class="is-loading"><Loading /></el-icon>
      <div style="margin-top: 12px">加载中...</div>
    </div>

    <div v-else-if="orderDetail" class="order-detail">
      <!-- 状态进度条 -->
      <el-steps v-if="showProgressSteps" :active="getStatusStep" finish-status="success" style="margin-bottom: 24px">
        <el-step title="待支付" />
        <el-step title="已支付" />
        <el-step :title="orderDetail.orderType === 1 ? '已完成' : '待发货/自提'" />
        <el-step v-if="orderDetail.orderType !== 1" title="配送中" />
        <el-step v-if="orderDetail.orderType !== 1" title="已完成" />
      </el-steps>
      <el-alert
        v-else
        :title="terminalStateTitle"
        :type="terminalStateType"
        :description="terminalStateDescription"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <!-- 基本信息 -->
      <el-card shadow="never" style="margin-bottom: 16px; position: relative;">
        <template #header>
          <span style="font-weight: 600">基本信息</span>
        </template>
        <div style="position:absolute; right:16px; top:12px; display:flex; gap:8px; align-items:center;">
          <el-select v-model="printPaperWidth" size="small" placeholder="样式" style="width:140px">
            <el-option label="80mm 小票" :value="'receipt_80'"></el-option>
            <el-option label="标准销售单" :value="'invoice_std'"></el-option>
          </el-select>
          <el-button size="small" type="primary" @click="handlePrintReceipt">立即打印</el-button>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ orderDetail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="getStatusTagType(orderDetail.orderStatus)">
              {{ getStatusText(orderDetail.orderStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单类型">
            {{ getOrderTypeText(orderDetail.orderType) }}
          </el-descriptions-item>
          <el-descriptions-item label="门店">{{ orderDetail.storeName || orderDetail.storeId }}</el-descriptions-item>
          <el-descriptions-item label="客户">
            <span>{{ orderDetail.customerName || '散客' }}</span>
            <span v-if="orderDetail.receiverPhone || orderDetail.customerPhone || (orderDetail.customer && orderDetail.customer.phone)">
              （{{ orderDetail.receiverPhone || orderDetail.customerPhone || (orderDetail.customer && orderDetail.customer.phone) }}）
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatDateTime(orderDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="操作员">{{ orderDetail.operatorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ orderDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 收货信息 / 配送方式 -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>
          <span style="font-weight: 600">收货信息</span>
        </template>
        <div v-if="orderDetail.orderType === 2">
          <!-- 自提订单 -->
          <el-descriptions :column="1" border>
            <el-descriptions-item label="自提码">
              <span style="font-size: 32px; font-weight: bold; color: #409eff; letter-spacing: 4px">
                {{ orderDetail.pickupCode }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="门店地址">
              {{ orderDetail.storeAddress || '请到门店自提' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <div v-else-if="orderDetail.orderType === 1">
          <!-- POS / 现场提货 -->
          <el-descriptions :column="1" border>
            <el-descriptions-item label="配送方式">现场提货</el-descriptions-item>
          </el-descriptions>
        </div>
        <div v-else>
          <!-- 配送订单 -->
          <el-descriptions :column="1" border>
            <el-descriptions-item label="配送方式">
              {{ getDeliveryTypeText(orderDetail.delivery?.deliveryType) }}
            </el-descriptions-item>
            <el-descriptions-item label="收货人">{{ orderDetail.receiverName }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ orderDetail.receiverPhone }}</el-descriptions-item>
            <el-descriptions-item label="收货地址">{{ orderDetail.receiverAddress }}</el-descriptions-item>
            <el-descriptions-item v-if="orderDetail.delivery" label="物流公司">
              {{ orderDetail.delivery.logisticsCompany || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="orderDetail.delivery" label="物流单号">
              {{ orderDetail.delivery.trackingNo || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="orderDetail.delivery && orderDetail.delivery.deliveryStaffName" label="配送员">
              {{ orderDetail.delivery.deliveryStaffName }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-card>

      <el-card v-if="packageGroups.length" shadow="never" style="margin-bottom: 16px">
        <template #header><span style="font-weight: 600">套餐成交明细</span></template>
        <el-table :data="packageGroups" border>
          <el-table-column prop="name" label="套餐名称" min-width="180" />
          <el-table-column prop="quantity" label="套数" width="100" />
          <el-table-column label="每套成交价" width="140" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="套餐成交金额" width="150" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.quantity * row.unitPrice) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 商品明细：实际出入库与退款始终使用这些物料行 -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>
          <span style="font-weight: 600">商品明细</span>
        </template>
        <el-table :data="orderDetail.items || []" border>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="商品信息" min-width="180">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; gap: 12px">

                <div>
                  <div style="font-weight: 500">{{ row.productName }}</div>
                  <div v-if="row.packageGroupNo" style="font-size: 12px; color: #909399">来自套餐：{{ row.packageName }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="specification" label="规格" width="120" align="center">
            <template #default="{ row }">{{ row.specification || row.spec || row.skuSpec || '-' }}</template>
          </el-table-column>
          <el-table-column prop="manufacturer" label="厂家" width="140" align="center">
            <template #default="{ row }">{{ row.manufacturer || row.brand || row.supplierName || row.supplier || '-' }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="120" align="right">
            <template #default="{ row }">
              {{ formatStock(row.quantity) }} {{ row.unit || '' }}
            </template>
          </el-table-column>
          <el-table-column label="已退/可退" width="150" align="right">
            <template #default="{ row }">
              <div>{{ formatStock(row.refundedQuantity || 0) }} / {{ formatStock(row.refundableQuantity ?? row.quantity) }}</div>
              <div style="color: #909399; font-size: 12px">{{ row.unit || '' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="单价" width="100" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="totalPrice" label="小计" width="100" align="right">
            <template #default="{ row }">
              <span style="font-weight: 600">¥{{ formatMoney(row.totalPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已退/可退金额" width="160" align="right">
            <template #default="{ row }">
              <div>¥{{ formatMoney(row.refundedAmount || 0) }}</div>
              <div style="color: #909399; font-size: 12px">¥{{ formatMoney(row.refundableAmount ?? row.totalPrice) }}</div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 支付明细 -->
      <el-card shadow="never">
        <template #header>
          <span style="font-weight: 600">支付明细</span>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单总额">
            <span style="font-size: 16px">¥{{ formatMoney(orderDetail.totalAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="orderDetail.discountAmount" label="优惠金额">
            <span style="color: #f56c6c">-¥{{ formatMoney(orderDetail.discountAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="orderDetail.couponAmount" label="优惠券抵扣">
            <span style="color: #f56c6c">-¥{{ formatMoney(orderDetail.couponAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="应收净额">
            <span style="font-size: 16px; font-weight: 600">
              ¥{{ formatMoney(paymentSummary.netReceivable) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="已收金额">
            <span style="font-size: 18px; font-weight: 600; color: #f56c6c">
              ¥{{ formatMoney(paymentSummary.receivedAmount) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="paymentSummary.creditAmount > 0" label="赊销金额">
            <span style="font-size: 16px; font-weight: 600; color: #e6a23c">
              ¥{{ formatMoney(paymentSummary.creditAmount) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="主支付方式">
            {{ paymentMethodLabel }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 支付方式明细 -->
        <div v-if="orderDetail.payments && orderDetail.payments.length > 0" style="margin-top: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">支付方式明细：</div>
          <el-table :data="orderDetail.payments" border size="small">
            <el-table-column prop="paymentType" label="支付方式" width="150">
              <template #default="{ row }">{{ row.paymentTypeName || getPaymentTypeText(row.paymentType) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="150" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="outerTransNo" label="外部流水号" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="支付时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <!-- 退款汇总 -->
      <el-card v-if="hasRefundSummary" shadow="never" style="margin-top: 16px">
        <template #header>
          <span style="font-weight: 600">退款汇总</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="累计已退金额">
            <span style="color: #f56c6c; font-weight: 600">¥{{ formatMoney(refundSummary.refundedAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="剩余可退金额">
            <span>¥{{ formatMoney(refundSummary.refundableAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="已退款商品行">
            {{ refundSummary.refundedItemCount }} / {{ refundSummary.totalItemCount }}
          </el-descriptions-item>
          <el-descriptions-item label="累计已退数量">
            {{ formatStock(refundSummary.refundedQuantity) }}
          </el-descriptions-item>
          <el-descriptions-item label="售后完成">
            {{ refundSummary.completedAfterSalesCount }} 笔
          </el-descriptions-item>
          <el-descriptions-item label="售后处理中">
            {{ refundSummary.pendingAfterSalesCount }} 笔
          </el-descriptions-item>
          <el-descriptions-item label="退款模式">
            {{ refundModeText }}
          </el-descriptions-item>
          <el-descriptions-item label="最近退款时间">
            {{ latestRefundTimeText }}
          </el-descriptions-item>
          <el-descriptions-item label="最近原因" :span="2">
            {{ latestRefundReasonText }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 售后记录 -->
      <el-card v-if="orderDetail.afterSalesList && orderDetail.afterSalesList.length > 0" shadow="never" style="margin-top: 16px">
        <template #header>
          <span style="font-weight: 600">售后记录</span>
        </template>
        <el-table :data="orderDetail.afterSalesList" border size="small">
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="afterSalesNo" label="售后单号" width="180" />
          <el-table-column prop="type" label="类型" width="100" align="center">
            <template #default="{ row }">
              {{ row.type === 1 ? '客户取消' : '管理端退货' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getAfterSalesStatusTagType(row.status)">
                {{ getAfterSalesStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="refundAmount" label="退款金额" width="120" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.refundAmount) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="申请时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="refundTime" label="退款时间" width="170">
            <template #default="{ row }">
              {{ row.refundTime ? formatDateTime(row.refundTime) : '-' }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {getOrderDetail} from '@/api/order'
import {formatStock} from '@/utils/number'
import {buildReceiptHtml, printReceipt} from '@/utils/receipt'
import {getOrderDisplayPaymentMethod, getOrderPaymentSummary, getPaymentTypeText} from '@/utils/orderPayment'
import type {OrderDetail as OrderDetailType} from '@/types/order'

interface Props {
  modelValue: boolean
  orderId: number | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const drawerVisible = ref(false)
const loading = ref(false)
const orderDetail = ref<OrderDetailType | null>(null)
const packageGroups = computed(() => {
  const groups = new Map<number, { name: string; quantity: number; unitPrice: number }>()
  for (const item of orderDetail.value?.items || []) {
    if (item.packageGroupNo && !groups.has(item.packageGroupNo)) {
      groups.set(item.packageGroupNo, { name: item.packageName || '套餐',
        quantity: Number(item.packageQuantity || 0), unitPrice: Number(item.packageUnitPrice || 0) })
    }
  }
  return [...groups.values()]
})
const printPaperWidth = ref<'receipt_80' | 'invoice_std' | string>('receipt_80')
const terminalStatuses = [7, 8, 9]

watch(
  () => props.modelValue,
  (val) => {
    drawerVisible.value = val
    if (val && props.orderId) {
      loadOrderDetail()
    }
  }
)

watch(drawerVisible, (val) => {
  emit('update:modelValue', val)
})

const showProgressSteps = computed(() => {
  return !terminalStatuses.includes(Number(orderDetail.value?.orderStatus || 0))
})

const paymentSummary = computed(() => getOrderPaymentSummary(orderDetail.value))
const paymentMethodLabel = computed(() => getOrderDisplayPaymentMethod(orderDetail.value))

const getStatusStep = computed(() => {
  if (!orderDetail.value) return 0
  const status = orderDetail.value.orderStatus
  const type = orderDetail.value.orderType

  if (status === 1) return 0 // 待支付
  if (status === 2) return 1 // 已支付
  if (status === 3 || status === 4) return 2 // 待发货/自提
  if (status === 5) return type === 1 ? 2 : 3 // 已发货/配送中
  if (status === 6) {
    // 已完成：POS订单有3步，小程序订单有5步，返回总步数让所有步骤都显示为已完成
    return type === 1 ? 3 : 5
  }
  return 0
})

const refundSummary = computed(() => {
  const items = Array.isArray(orderDetail.value?.items) ? orderDetail.value.items : []
  const afterSalesList = Array.isArray(orderDetail.value?.afterSalesList) ? orderDetail.value.afterSalesList : []
  return {
    refundedAmount: items.reduce((sum: number, item: any) => sum + Number(item.refundedAmount || 0), 0),
    refundableAmount: items.reduce((sum: number, item: any) => sum + Number(item.refundableAmount || 0), 0),
    refundedQuantity: items.reduce((sum: number, item: any) => sum + Number(item.refundedQuantity || 0), 0),
    refundedItemCount: items.filter((item: any) => Number(item.refundedAmount || 0) > 0 || Number(item.refundedQuantity || 0) > 0).length,
    totalItemCount: items.length,
    completedAfterSalesCount: afterSalesList.filter((item: any) => Number(item.status) === 50).length,
    pendingAfterSalesCount: afterSalesList.filter((item: any) => Number(item.status) === 10 || Number(item.status) === 40).length
  }
})

const hasRefundSummary = computed(() => {
  return refundSummary.value.refundedAmount > 0
    || refundSummary.value.pendingAfterSalesCount > 0
    || terminalStatuses.includes(Number(orderDetail.value?.orderStatus || 0))
})

const latestAfterSalesRecord = computed(() => {
  const records = Array.isArray(orderDetail.value?.afterSalesList) ? [...orderDetail.value.afterSalesList] : []
  if (records.length === 0) return null
  records.sort((a: any, b: any) => {
    const timeA = new Date(a.refundTime || a.createdAt || 0).getTime()
    const timeB = new Date(b.refundTime || b.createdAt || 0).getTime()
    return timeB - timeA
  })
  return records[0]
})

const refundModeText = computed(() => {
  const status = Number(orderDetail.value?.orderStatus || 0)
  const hasAfterSales = Array.isArray(orderDetail.value?.afterSalesList) && orderDetail.value.afterSalesList.length > 0
  if (hasAfterSales) {
    return status === 9 ? '退货退款进行中' : '售后退货退款'
  }
  if (status === 8 || status === 9) {
    return '订单直退'
  }
  return '暂无退款'
})

const latestRefundTimeText = computed(() => {
  const latestRecord = latestAfterSalesRecord.value
  if (!latestRecord) {
    return refundSummary.value.refundedAmount > 0 ? '订单直退未记录售后时间' : '-'
  }
  return formatDateTime(latestRecord.refundTime || latestRecord.createdAt)
})

const latestRefundReasonText = computed(() => {
  const latestRecord = latestAfterSalesRecord.value
  if (!latestRecord) {
    return refundSummary.value.refundedAmount > 0 ? '订单直退未记录售后原因' : '-'
  }
  return latestRecord.reason || '-'
})

const terminalStateTitle = computed(() => {
  const status = Number(orderDetail.value?.orderStatus || 0)
  if (status === 7) return '订单已取消'
  if (status === 8) return '订单已退款'
  if (status === 9) return '订单部分退款中'
  return '订单已结束'
})

const terminalStateType = computed(() => {
  const status = Number(orderDetail.value?.orderStatus || 0)
  if (status === 7) return 'info'
  if (status === 8) return 'error'
  if (status === 9) return 'warning'
  return 'info'
})

const terminalStateDescription = computed(() => {
  const status = Number(orderDetail.value?.orderStatus || 0)
  if (status === 7) {
    return '该订单已取消，不再继续履约。'
  }
  if (status === 8) {
    return `该订单已累计退款 ¥${formatMoney(refundSummary.value.refundedAmount)}。`
  }
  if (status === 9) {
    return `该订单已累计退款 ¥${formatMoney(refundSummary.value.refundedAmount)}，仍有部分商品或金额可继续退款。`
  }
  return ''
})

const loadOrderDetail = async () => {
  if (!props.orderId) return

  loading.value = true
  try {
    const data = await getOrderDetail(props.orderId)
    orderDetail.value = data
  } catch (error) {
    console.error('加载订单详情失败:', error)
    ElMessage.error((error as any)?.message || '加载订单详情失败')
  } finally {
    loading.value = false
  }
}

const formatMoney = (amount: number | string | null | undefined) => {
  if (amount == null) return '0.00'
  return Number(amount).toFixed(2)
}

const formatDateTime = (dateTime: string | null | undefined) => {
  if (!dateTime) return '-'
  return dateTime.replace('T', ' ')
}

const getOrderTypeText = (type: number) => {
  const map: Record<number, string> = {
    1: '线下POS',
    2: '小程序自提',
    3: '小程序配送'
  }
  return map[type] || '未知'
}

const getDeliveryTypeText = (type?: number | null) => {
  if (type === 2) return '同城配送'
  if (type === 3) return '快递配送'
  return '待确定'
}

const getStatusText = (status: number) => {
  const map: Record<number, string> = {
    1: '待支付',
    2: '已支付',
    3: '待自提',
    4: '待发货',
    5: '已发货',
    6: '已完成',
    7: '已取消',
    8: '已退款',
    9: '部分退款'
  }
  return map[status] || '未知'
}

const getStatusTagType = (status: number) => {
  const map: Record<number, string> = {
    1: 'warning',
    2: 'success',
    3: 'info',
    4: 'info',
    5: 'primary',
    6: 'success',
    7: 'info',
    8: 'danger',
    9: 'warning'
  }
  return map[status] || ''
}

const getAfterSalesStatusText = (status: number) => {
  const map: Record<number, string> = {
    10: '已申请',
    40: '待退款',
    50: '已完成',
    90: '已驳回'
  }
  return map[status] || '未知'
}

const getAfterSalesStatusTagType = (status: number) => {
  const map: Record<number, string> = {
    10: 'info',
    40: 'warning',
    50: 'success',
    90: 'danger'
  }
  return map[status] || ''
}

const handleClose = () => {
  drawerVisible.value = false
  orderDetail.value = null
}
const handlePrintReceipt = async () => {
  if (!orderDetail.value) return
  const opts = { paperWidth: printPaperWidth.value }
  try {
    // Prefer client-side print preview
    await printReceipt(orderDetail.value, { paperWidth: opts.paperWidth as any })
  } catch (err) {
    // popup blocked - fallback: open preview in current window tab
    const built = buildReceiptHtml(orderDetail.value, { paperWidth: opts.paperWidth as any })
    const w = window.open('', '_blank')
    if (!w) {
      ElMessage.error('弹出窗口被拦截，无法打印，请允许弹出窗口')
      return
    }
    w.document.open()
    w.document.write(`<html><head><meta charset="utf-8"><title>小票预览</title></head><body>${built.html}</body></html>`)
    w.document.close()
  }
}

// Note: backend print integration removed. Printing is client-side only.
</script>

<style scoped lang="scss">
.order-detail {
  padding: 0;
}
</style>

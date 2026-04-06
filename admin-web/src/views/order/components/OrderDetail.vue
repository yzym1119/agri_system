<template>
  <el-drawer v-model="drawerVisible" title="订单详情" size="60%" @close="handleClose">
    <div v-if="loading" style="text-align: center; padding: 40px">
      <el-icon class="is-loading"><Loading /></el-icon>
      <div style="margin-top: 12px">加载中...</div>
    </div>

    <div v-else-if="orderDetail" class="order-detail">
      <!-- 状态进度条 -->
      <el-steps :active="getStatusStep" finish-status="success" style="margin-bottom: 24px">
        <el-step title="待支付" />
        <el-step title="已支付" />
        <el-step :title="orderDetail.orderType === 1 ? '已完成' : '待发货/自提'" />
        <el-step v-if="orderDetail.orderType !== 1" title="配送中" />
        <el-step v-if="orderDetail.orderType !== 1" title="已完成" />
      </el-steps>

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

      <!-- 商品明细 -->
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
          <el-table-column prop="unitPrice" label="单价" width="100" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="totalPrice" label="小计" width="100" align="right">
            <template #default="{ row }">
              <span style="font-weight: 600">¥{{ formatMoney(row.totalPrice) }}</span>
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
          <el-descriptions-item label="实付金额">
            <span style="font-size: 18px; font-weight: 600; color: #f56c6c">
              ¥{{ formatMoney(orderDetail.payAmount) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 支付方式明细 -->
        <div v-if="orderDetail.payments && orderDetail.payments.length > 0" style="margin-top: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">支付方式明细：</div>
          <el-table :data="orderDetail.payments" border size="small">
            <el-table-column prop="paymentType" label="支付方式" width="150">
              <template #default="{ row }">{{ getPaymentTypeText(row.paymentType) }}</template>
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

      <!-- 售后记录 -->
      <el-card v-if="orderDetail.afterSalesList && orderDetail.afterSalesList.length > 0" shadow="never">
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
const orderDetail = ref<any>(null)
const printPaperWidth = ref<'receipt_80' | 'invoice_std' | string>('receipt_80')

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

const loadOrderDetail = async () => {
  if (!props.orderId) return

  loading.value = true
  try {
    const data = await getOrderDetail(props.orderId)
    orderDetail.value = data
  } catch (error) {
    console.error('加载订单详情失败:', error)
    ElMessage.error('加载订单详情失败')
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

import { formatStock } from '@/utils/number'
import { buildReceiptHtml, printReceipt } from '@/utils/receipt'

const getOrderTypeText = (type: number) => {
  const map: Record<number, string> = {
    1: '线下POS',
    2: '小程序自提',
    3: '小程序配送'
  }
  return map[type] || '未知'
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

const getPaymentTypeText = (type: number) => {
  // 注意：paymentType 在 order_payment 表中定义为：
  // 1 - 现金 (POS 场景)、2 - 微信、3 - 本金余额、4 - 专用金、5 - 红包/优惠券、6 - 赊销
  const map: Record<number, string> = {
    1: '现金',
    2: '微信',
    3: '本金余额',
    4: '专用金',
    5: '红包/优惠券',
    6: '赊销',
    10: '混合支付'
  }
  return map[type] || '未知'
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


<template>
  <div class="page-container">
    <!-- 搜索筛选 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="搜索">
          <el-input
            v-model="queryParams.keyword"
            placeholder="订单号/手机号/收货人"
            clearable
            style="width: 250px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="门店">
          <el-select v-model="queryParams.storeId" placeholder="全部门店" clearable style="width: 200px">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单来源">
          <el-select v-model="queryParams.orderType" placeholder="全部" clearable style="width: 150px">
            <el-option label="POS" :value="1" />
            <el-option label="小程序自提" :value="2" />
            <el-option label="小程序配送" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="配送方式">
          <el-select v-model="queryParams.deliveryType" placeholder="全部" clearable style="width: 150px">
            <el-option label="自提" :value="1" />
            <el-option label="同城配送" :value="2" />
            <el-option label="快递配送" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryParams.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 状态 Tabs -->
    <el-card class="table-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待付款" name="1" />
        <el-tab-pane label="待发货/自提" name="3,4" />
        <el-tab-pane label="配送中" name="5" />
        <el-tab-pane label="已完成" name="6" />
        <el-tab-pane label="退款/售后" name="refund" />
      </el-tabs>

      <!-- 订单表格 -->
      <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px">
        
        <el-table-column prop="orderNo" label="订单号" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleCopyOrderNo(row.orderNo)">
              {{ row.orderNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="来源/类型" width="140">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <span v-if="row.orderType === 1">🖥️</span>
              <span v-else>📱</span>
              <span>{{ getOrderTypeText(row.orderType) }}</span>
            </div>
            <div v-if="row.orderType !== 1" style="margin-top: 4px">
              <el-tag v-if="row.deliveryType === 1" size="small" type="info">[自提]</el-tag>
              <el-tag v-else-if="row.deliveryType === 2" size="small" type="warning">[同城]</el-tag>
              <el-tag v-else-if="row.deliveryType === 3" size="small" type="success">[快递]</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品概览" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.items && row.items.length > 0">
              <span>{{ row.items[0].productName }}</span>
              <span v-if="row.items.length > 1" style="color: #909399">
                等{{ row.items.length }}件
              </span>
            </div>
            <span v-else style="color: #909399">暂无商品</span>
          </template>
        </el-table-column>
        <el-table-column label="客户信息" width="150">
          <template #default="{ row }">
            <div v-if="row.customerName">
              <div>{{ row.customerName }}</div>
              <div style="font-size: 12px; color: #909399">{{ row.customerPhone || row.receiverPhone }}</div>
            </div>
            <span v-else style="color: #909399">散客</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="220" align="right">
          <template #default="{ row }">
            <div>
              <div>订单总额：¥{{ formatMoney(row.totalAmount) }}</div>
              <div>应收净额：¥{{ formatMoney(getNetReceivable(row)) }}</div>
              <div style="color: #67c23a; font-weight: 600">已收：¥{{ formatMoney(getPaidCash(row)) }}</div>
              <div style="margin-top:6px">
                <span v-if="getOweAmount(row) > 0" style="color:#f56c6c; font-weight:600">赊销：¥{{ formatMoney(getOweAmount(row)) }}</span>
              </div>
              <div style="margin-top:6px">
                <el-tag size="small" :type="getPaymentMethodTagType(getDisplayPaymentType(row))">
                  {{ row.paymentMethodName || getPaymentMethodTagText(getDisplayPaymentType(row)) }}
                </el-tag>
              </div>
              <el-tooltip v-if="row.discountAmount || row.couponAmount" placement="top">
                <template #content>
                  <div v-if="row.discountAmount">优惠：¥{{ formatMoney(row.discountAmount) }}</div>
                  <div v-if="row.couponAmount">优惠券：¥{{ formatMoney(row.couponAmount) }}</div>
                </template>
                <el-button link type="info" size="small">查看明细</el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="orderStatus" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.orderStatus)">
              {{ getStatusText(row.orderStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" />
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
            <!-- 待发货/待自提状态的操作 -->
            <template v-if="canVerifyPickup(row) || canShipByExpress(row) || canShipByLocal(row)">
              <el-button
                v-if="canVerifyPickup(row)"
                v-permission="'order:pickup:verify'"
                link
                type="success"
                :loading="actionLoading[row.id]"
                @click="handleVerify(row)"
              >
                核销
              </el-button>
              <el-button
                v-if="canShipByExpress(row)"
                v-permission="'order:ship'"
                link
                type="warning"
                :loading="actionLoading[row.id]"
                @click="handleShip(row, 'express')"
              >
                快递发货
              </el-button>
              <el-button
                v-if="canShipByLocal(row)"
                v-permission="'order:ship'"
                link
                type="primary"
                :loading="actionLoading[row.id]"
                @click="handleShip(row, 'local')"
              >
                同城派单
              </el-button>
            </template>
            <!-- 配送中状态 -->
            <el-button
              v-if="row.orderStatus === 5"
              v-permission="'order:ship'"
              link
              type="success"
              :loading="actionLoading[row.id]"
              @click="handleConfirmReceipt(row)"
            >
              确认收货
            </el-button>
            <!-- 退款按钮 -->
            <el-button
              v-if="canRefund(row)"
              v-permission="'order:refund'"
              link
              type="danger"
              :loading="actionLoading[row.id]"
              @click="handleRefund(row)"
            >
              {{ getRefundActionText(row) }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 发货/派单弹窗 -->
    <ShipDialog
      v-model="shipVisible"
      :order="currentOrder"
      :mode="shipMode"
      @success="handleShipSuccess"
    />

    <!-- 自提核销弹窗 -->
    <VerifyDialog v-model="verifyVisible" :order="currentOrder" @success="handleVerifySuccess" />

    <!-- 退款弹窗 -->
    <RefundDialog v-model="refundVisible" :order="currentOrder" @success="handleRefundSuccess" />

    <!-- 订单详情抽屉 -->
    <OrderDetail v-model="detailVisible" :order-id="currentOrderId" />
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Refresh, Search} from '@element-plus/icons-vue'
import {confirmReceipt, getOrderList} from '@/api/order'
import {getAllStores} from '@/api/store'
import {getOrderPaymentSummary} from '@/utils/orderPayment'
import ShipDialog from '../components/ShipDialog.vue'
import VerifyDialog from '../components/VerifyDialog.vue'
import RefundDialog from '../components/RefundDialog.vue'
import OrderDetail from '../components/OrderDetail.vue'
import type {OrderListQuery, OrderPageItem, OrderPageResponse} from '@/types/order'
import type {StoreDetailVO} from '@/types/store'

// 响应式数据
const loading = ref(false)
const tableData = ref<OrderPageItem[]>([])
const total = ref(0)
const activeTab = ref('all')
const storeOptions = ref<StoreDetailVO[]>([])
const actionLoading = ref<Record<number, boolean>>({})
const shipVisible = ref(false)
const verifyVisible = ref(false)
const refundVisible = ref(false)
const detailVisible = ref(false)
const currentOrder = ref<OrderPageItem | null>(null)
const currentOrderId = ref<number | null>(null)
const shipMode = ref<'express' | 'local'>('express')

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  storeId: null as number | null,
  orderType: null as number | null,
  deliveryType: null as number | null,
  timeRange: null as [string, string] | null,
  orderStatus: null as string | null
})

// 计算属性
const canRefund = (row: OrderPageItem) => {
  // 已支付但未履约走订单直退；已发货/已完成/部分退款走售后退货
  if (row.orderStatus === 8) {
    // 现金已退完可能仍有零金额物料未退回，按剩余数量显示退货入口。
    return (row.items || []).some(item => Number(item.refundableQuantity
      ?? Math.max(0, Number(item.quantity) - Number(item.refundedQuantity || 0))) > 0)
  }
  return row.orderStatus === 2
    || row.orderStatus === 3
    || row.orderStatus === 4
    || row.orderStatus === 5
    || row.orderStatus === 6
    || row.orderStatus === 9
}

const canVerifyPickup = (row: OrderPageItem) => {
  return row.deliveryType === 1 && row.orderStatus === 3
}

const isPendingDeliveryOrder = (row: OrderPageItem) => {
  return row.orderType === 3 && (row.orderStatus === 3 || row.orderStatus === 4)
}

const canShipByExpress = (row: OrderPageItem) => {
  return isPendingDeliveryOrder(row) && (row.deliveryType == null || row.deliveryType === 3)
}

const canShipByLocal = (row: OrderPageItem) => {
  return isPendingDeliveryOrder(row) && (row.deliveryType == null || row.deliveryType === 2)
}

const getRefundActionText = (row: OrderPageItem) => {
  if (row.orderStatus === 8) return '继续退货'
  if (row.orderStatus === 9) return '继续退款'
  if (row.orderStatus === 2 || row.orderStatus === 3 || row.orderStatus === 4) {
    return '订单退款'
  }
  return '退货退款'
}

// 方法
const formatMoney = (amount: number | string | null | undefined) => {
  if (amount == null) return '0.00'
  return Number(amount).toFixed(2)
}

const getNetReceivable = (row: OrderPageItem) => {
  return getOrderPaymentSummary(row).netReceivable
}

const getPaidCash = (row: OrderPageItem) => {
  return getOrderPaymentSummary(row).receivedAmount
}

const getOweAmount = (row: OrderPageItem) => {
  return getOrderPaymentSummary(row).creditAmount
}

const getPaymentMethodTagText = (method?: number) => {
  if (!method) return '未知'
  if (method === 6) return '全额赊销'
  if (method === 10) return '混合支付'
  const map: Record<number, string> = {
    1: '现金',
    2: '微信',
    3: '支付宝',
    4: '本金余额',
    5: '优惠券'
  }
  return map[method] || '其他'
}

const getPaymentMethodTagType = (method?: number) => {
  if (!method) return 'info'
  if (method === 6) return 'danger'
  if (method === 10) return 'warning'
  return 'info'
}

const getOrderTypeText = (type: number) => {
  const map: Record<number, string> = {
    1: 'POS',
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

// 列表页优先展示后端给出的主支付方式；若缺失再回退到旧的支付明细推断逻辑
const getDisplayPaymentType = (row: OrderPageItem) => {
  if (row && row.paymentMethod) {
    return row.paymentMethod
  }
  if (row && row.payments && Array.isArray(row.payments) && row.payments.length > 0) {
    const types = row.payments
      .map((p: any) => Number(p.paymentType))
      .filter((t: number) => !isNaN(t))
      .map((t: number) => (t === 8 ? 4 : t)) // 支付流水中的专用金归并到主支付方式“余额”
      .filter((t: number) => t !== 5) // 忽略优惠券对主支付方式的影响
    const unique = Array.from(new Set(types))
    if (unique.length === 0) return row.paymentMethod || undefined
    if (unique.length === 1) return unique[0]
    return 10 // 混合
  }
  return undefined
}

const handleSearch = () => {
  queryParams.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 20,
    keyword: '',
    storeId: null,
    orderType: null,
    deliveryType: null,
    timeRange: null,
    orderStatus: null
  })
  activeTab.value = 'all'
  loadData()
}

const handleTabChange = (tabName: string) => {
  if (tabName === 'all') {
    queryParams.orderStatus = null
  } else if (tabName === 'refund') {
    // 退款/售后：状态 8-已退款，9-部分退款
    queryParams.orderStatus = '8,9'
  } else {
    queryParams.orderStatus = tabName
  }
  queryParams.pageNum = 1
  loadData()
}

const loadData = async () => {
  loading.value = true
  try {
    const params: OrderListQuery = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }

    if (queryParams.keyword) {
      params.keyword = queryParams.keyword
    }
    if (queryParams.storeId) {
      params.storeId = queryParams.storeId
    }
    if (queryParams.orderType) {
      params.orderType = queryParams.orderType
    }
    if (queryParams.deliveryType) {
      params.deliveryType = queryParams.deliveryType
    }
    if (queryParams.timeRange && queryParams.timeRange.length === 2) {
      params.startTime = queryParams.timeRange[0]
      params.endTime = queryParams.timeRange[1]
    }
    if (queryParams.orderStatus) {
      params.orderStatus = queryParams.orderStatus
    }

    const response: OrderPageResponse = await getOrderList(params)
    // 后端直接返回 Page<OrderPageVO>，包含 records 和 totalRow/total
    if (response) {
      tableData.value = response.records || []
      // MyBatis-Flex 的 Page 对象可能有 totalRow 或 total 字段
      total.value = response.totalRow || response.total || 0
    } else {
      tableData.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('加载订单列表失败:', error)
    ElMessage.error('加载订单列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  loadData()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadData()
}

const handleCopyOrderNo = (orderNo: string) => {
  navigator.clipboard.writeText(orderNo).then(() => {
    ElMessage.success('订单号已复制')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

const handleViewDetail = (row: OrderPageItem) => {
  currentOrderId.value = row.id
  detailVisible.value = true
}

const handleShip = (row: OrderPageItem, mode: 'express' | 'local') => {
  currentOrder.value = row
  shipMode.value = mode
  shipVisible.value = true
}

const handleVerify = (row: OrderPageItem) => {
  currentOrder.value = row
  verifyVisible.value = true
}

const handleShipSuccess = () => {
  shipVisible.value = false
  loadData()
}

const handleVerifySuccess = () => {
  verifyVisible.value = false
  loadData()
}

const handleRefund = (row: OrderPageItem) => {
  currentOrder.value = row
  refundVisible.value = true
}

const handleRefundSuccess = () => {
  refundVisible.value = false
  loadData()
}

const handleConfirmReceipt = async (row: OrderPageItem) => {
  try {
    await ElMessageBox.confirm('确认强制完成订单？', '确认收货', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    actionLoading.value[row.id] = true
    await confirmReceipt(row.id)
    ElMessage.success('确认收货成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('确认收货失败:', error)
      ElMessage.error((error as any)?.message || '确认收货失败')
    }
  } finally {
    actionLoading.value[row.id] = false
  }
}

// 初始化
onMounted(async () => {
  try {
    storeOptions.value = await getAllStores()
  } catch (error) {
    console.error('加载门店列表失败:', error)
  }
  loadData()
})
</script>

<style scoped lang="scss">
.table-card {
  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>

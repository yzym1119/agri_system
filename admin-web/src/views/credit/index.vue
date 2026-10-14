<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>赊销总览</h1>
      </div>
      <div class="header-controls">
        <el-button :icon="Refresh" @click="loadAccounts" :loading="loading">刷新</el-button>
      </div>
    </section>

    <div class="metric-grid">
      <div class="metric-card tone-red">
        <div class="metric-value">¥{{ formatMoney(summaryStats.totalOutstanding) }}</div>
        <div class="metric-label">本页未清欠款</div>
      </div>
      <div class="metric-card tone-orange">
        <div class="metric-value">{{ summaryStats.overdueCount }}</div>
        <div class="metric-label">本页逾期笔数</div>
      </div>
      <div class="metric-card tone-blue">
        <div class="metric-value">{{ summaryStats.customerCount }}</div>
        <div class="metric-label">本页涉及客户数</div>
      </div>
      <div class="metric-card tone-green">
        <div class="metric-value">¥{{ formatMoney(summaryStats.totalPaid) }}</div>
        <div class="metric-label">本页累计实还</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 140px" @change="handleSearch">
          <el-option label="未还清" :value="1" />
          <el-option label="已还清" :value="2" />
          <el-option label="已逾期" :value="3" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="accountList" border>
        <el-table-column label="客户" min-width="150">
          <template #default="{ row }">
            <div>{{ row.customerName || '-' }}</div>
            <div class="cell-sub">{{ row.customerPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单号" min-width="170">
          <template #default="{ row }">
            <el-button v-if="row.orderId" link type="primary" @click="openOrder(row.orderId)">
              {{ row.orderNo || `#${row.orderId}` }}
            </el-button>
            <span v-else>{{ row.orderNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原赊销金额" width="130" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.creditAmount) }}</template>
        </el-table-column>
        <el-table-column label="累计实还" width="130" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="退货冲欠款" width="130" align="right">
          <template #default="{ row }">{{ row.creditReductionAmount == null ? '待核对' : '¥' + formatMoney(row.creditReductionAmount) }}</template>
        </el-table-column>
        <el-table-column label="已退还款" width="130" align="right">
          <template #default="{ row }">{{ row.refundedRepaymentAmount == null ? '待核对' : '¥' + formatMoney(row.refundedRepaymentAmount) }}</template>
        </el-table-column>
        <el-table-column label="剩余欠款" width="130" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.remainingAmount || 0) > 0 ? '#f56c6c' : '', fontWeight: 600 }">
              ¥{{ formatMoney(row.remainingAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="到期日" width="120">
          <template #default="{ row }">{{ formatDate(row.dueDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="挂账时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <OrderDetail v-model="orderDetailVisible" :order-id="selectedOrderId" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getCreditAccounts } from '@/api/credit'
import OrderDetail from '@/views/order/components/OrderDetail.vue'
import type { CreditAccountItem } from '@/types/credit'

const loading = ref(false)
const accountList = ref<CreditAccountItem[]>([])
const total = ref(0)
const statusFilter = ref<number | undefined>(undefined)
const pageNum = ref(1)
const pageSize = ref(20)

const orderDetailVisible = ref(false)
const selectedOrderId = ref<number | null>(null)

const summaryStats = computed(() => {
  let totalOutstanding = 0
  let totalPaid = 0
  let overdueCount = 0
  const customers = new Set<number>()

  for (const item of accountList.value) {
    totalOutstanding += Number(item.remainingAmount || 0)
    totalPaid += Number(item.paidAmount || 0)
    if (item.status === 3) overdueCount++
    if (item.customerId) customers.add(item.customerId)
  }

  return {
    totalOutstanding,
    totalPaid,
    overdueCount,
    customerCount: customers.size
  }
})

const formatMoney = (value?: number | null) => Number(value || 0).toFixed(2)

const formatDate = (value?: string | null) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

const getStatusText = (status?: number) => {
  if (status === 1) return '未还清'
  if (status === 2) return '已还清'
  if (status === 3) return '已逾期'
  return '-'
}

const getStatusType = (status?: number) => {
  if (status === 1) return 'warning'
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  return 'info'
}

const loadAccounts = async () => {
  loading.value = true
  try {
    const page = await getCreditAccounts(undefined, pageNum.value, pageSize.value, statusFilter.value)
    accountList.value = page.records || []
    total.value = page.total ?? page.totalRow ?? 0
  } catch {
    accountList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  loadAccounts()
}

const handleReset = () => {
  statusFilter.value = undefined
  pageNum.value = 1
  loadAccounts()
}

const handlePageChange = (page: number) => {
  pageNum.value = page
  loadAccounts()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  pageNum.value = 1
  loadAccounts()
}

const openOrder = (orderId?: number | null) => {
  if (!orderId) return
  selectedOrderId.value = orderId
  orderDetailVisible.value = true
}

onMounted(() => {
  loadAccounts()
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

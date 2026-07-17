<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>我的绩效</h1>
        <div class="page-subtitle">{{ userStore.userInfo?.name || '-' }} · {{ currentMonth }}</div>
      </div>
      <div class="header-controls">
        <el-date-picker v-model="selectedMonth" type="month" placeholder="选择月份"
          value-format="YYYY-MM" @change="refreshAll" style="width: 160px" />
        <el-button @click="refreshAll" :loading="loading" :icon="Refresh">刷新</el-button>
      </div>
    </section>

    <section class="metric-grid" style="grid-template-columns: repeat(3, minmax(0, 1fr))">
      <article class="metric-card tone-blue" style="cursor:default">
        <div class="metric-label"><span>本月销售额</span><el-icon><Money /></el-icon></div>
        <div class="metric-value">{{ currency(dash.monthSales) }}</div>
        <div class="metric-foot">目标 {{ currency(dash.targetSalesAmount) }}</div>
      </article>
      <article class="metric-card tone-green" style="cursor:default">
        <div class="metric-label"><span>本月提成</span><el-icon><Coin /></el-icon></div>
        <div class="metric-value">{{ currency(dash.monthCommission) }}</div>
        <div class="metric-foot">目标 {{ currency(dash.targetCommission) }}</div>
      </article>
      <article class="metric-card tone-orange" style="cursor:default">
        <div class="metric-label"><span>新增客户</span><el-icon><User /></el-icon></div>
        <div class="metric-value">{{ dash.newCustomerCount ?? 0 }}</div>
        <div class="metric-foot">&nbsp;</div>
      </article>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <h3>目标进度</h3>
          <span class="goal-pct">{{ progressPct.toFixed(1) }}%</span>
        </div>
      </template>
      <el-progress :percentage="progressPct" :stroke-width="20" :color="progressColor" :show-text="false" />
      <div class="goal-legend">
        <span>已完成 {{ currency(dash.monthSales) }}</span>
        <span>目标 {{ currency(dash.targetSalesAmount) }}</span>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <h3>绩效流水</h3>
          <div class="card-actions">
            <el-button size="small" :disabled="logPage <= 1" @click="logPage--; loadLogs()">上一页</el-button>
            <span class="page-info">第 {{ logPage }} 页</span>
            <el-button size="small" :disabled="logs.length < logSize" @click="logPage++; loadLogs()">下一页</el-button>
          </div>
        </div>
      </template>
      <el-table :data="logs" v-loading="logsLoading" size="small">
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-success': (row.amount ?? 0) > 0 }">{{ currency(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="calcDesc" label="计算说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.needReview === 1" type="warning" size="small">待复核</el-tag>
            <el-tag v-else type="success" size="small">已确认</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Money, Coin, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getMyPerformanceDashboard, getMyPerformanceLogs } from '@/api/performance'
import type { StaffPerformanceDashboard, StaffPerformanceLogItem } from '@/types/performance'

const userStore = useUserStore()
const dash = ref<StaffPerformanceDashboard>({})
const logs = ref<StaffPerformanceLogItem[]>([])

const dashLoading = ref(false)
const logsLoading = ref(false)
const loading = computed(() => dashLoading.value || logsLoading.value)

const logPage = ref(1)
const logSize = 20

const now = new Date()
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const selectedMonth = ref(currentMonth)

const toNumber = (v?: number | null) => Number(v || 0)
const currency = (v?: number | null) => `¥${toNumber(v).toFixed(2)}`

const progressPct = computed(() => Math.min(toNumber(dash.value.goalProgress), 100))
const progressColor = computed(() => {
  const p = progressPct.value
  if (p >= 80) return '#7ea76d'
  if (p >= 50) return '#e6a23c'
  return '#f56c6c'
})

const bizTypeLabel = (type?: number) => {
  const map: Record<number, string> = { 1: '销售提成', 2: '新客奖励', 3: '手动调整', 4: '退款扣回' }
  return type != null ? (map[type] || `类型${type}`) : '-'
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const loadDashboard = async () => { dashLoading.value = true; try { dash.value = await getMyPerformanceDashboard() } catch { dash.value = {} } finally { dashLoading.value = false } }
const loadLogs = async () => { logsLoading.value = true; try { logs.value = await getMyPerformanceLogs({ month: selectedMonth.value || undefined, page: logPage.value, size: logSize }) } catch { logs.value = [] } finally { logsLoading.value = false } }

const refreshAll = async () => { logPage.value = 1; await Promise.all([loadDashboard(), loadLogs()]) }

onMounted(refreshAll)
</script>

<style scoped lang="scss">
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  h3 { margin: 0; font-size: 14px; font-weight: 700; color: #1f2937; }
}

.card-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.goal-pct {
  font-size: 22px;
  font-weight: 700;
  color: #1d5d73;
}

.goal-legend {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #9ca3af;
}

.page-info {
  font-size: 13px;
  color: #6b7280;
}
</style>

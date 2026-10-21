<template>
  <div class="app-page">
    <!-- 页面头 -->
    <section class="page-header">
      <div>
        <h1>报表分析</h1>
      </div>
      <div class="header-controls">
        <el-select v-model="storeId" clearable filterable placeholder="全部门店" style="width: 200px" @change="refreshAll">
          <el-option v-for="s in storeOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button @click="refreshAll" :loading="allLoading" :icon="Refresh">刷新</el-button>
      </div>
    </section>

    <!-- 汇总指标 -->
    <section class="metric-grid" style="grid-template-columns: repeat(4, minmax(0, 1fr))">
      <article class="metric-card tone-blue" style="cursor:default">
        <div class="metric-label"><span>总欠款</span></div>
        <div class="metric-value">{{ currency(creditRisk.totalDebt) }}</div>
        <div class="metric-foot">逾期 {{ currency(creditRisk.overdueDebt) }}</div>
      </article>
      <article class="metric-card tone-orange" style="cursor:default">
        <div class="metric-label"><span title="本月新建赊销的净实还 ÷（净实还＋剩余欠款）；冲欠不算实还，旧债本月还款不计入此比例。无有效应收或来源待核对时显示—。">本月赊销回款率</span></div>
        <div class="metric-value">{{ pct(creditRisk.repaymentRate) }}</div>
        <div class="metric-foot">本月回款 {{ currency(creditRisk.monthlyRepayment) }}</div>
      </article>
      <article class="metric-card tone-green" style="cursor:default">
        <div class="metric-label"><span>库存占用</span></div>
        <div class="metric-value">{{ currency(inventoryValue.totalInventoryValue) }}</div>
        <div class="metric-foot">180 天以上 {{ currency(inventoryValue.over180DaysValue) }}</div>
      </article>
      <article class="metric-card tone-slate" style="cursor:default">
        <div class="metric-label"><span>月度新增赊销</span></div>
        <div class="metric-value">{{ currency(creditRisk.monthlyNewCredit) }}</div>
        <div class="metric-foot">当前：{{ storeName }}</div>
      </article>
    </section>

    <!-- 销售趋势 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <h3>销售净额与毛利趋势</h3>
          <div class="card-actions">
            <el-date-picker v-model="salesDateRange" type="daterange" range-separator="至"
              start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD"
              unlink-panels style="width: 260px" size="default" />
            <el-button type="primary" plain @click="loadSalesTrend" :loading="salesLoading">查询</el-button>
            <el-button @click="handleExport('sales')" :loading="exporting.sales">导出</el-button>
          </div>
        </div>
      </template>
      <p class="text-secondary">按原订单创建日统计净销售额，扣除该订单累计已完成退货；包含赊销，不代表当日现金流。</p>
      <div class="split-view">
        <v-chart class="split-chart" :option="salesChartOption" autoresize v-loading="salesLoading" />
        <div class="split-table">
          <el-table :data="salesTrend" v-loading="salesLoading" size="small" :max-height="340">
            <el-table-column prop="date" label="日期" width="110" />
            <el-table-column label="销售额" min-width="100" align="right">
              <template #default="{ row }">{{ currency(row.saleAmount) }}</template>
            </el-table-column>
            <el-table-column label="成本额" min-width="100" align="right">
              <template #default="{ row }">{{ hasUnverifiedCost(row) ? '待核对' : currency(row.costAmount) }}</template>
            </el-table-column>
            <el-table-column label="毛利额" min-width="100" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-danger': !hasUnverifiedCost(row) && toNumber(row.profit) < 0 }">{{ hasUnverifiedCost(row) ? '待核对' : currency(row.profit) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="毛利率" width="80" align="right">
              <template #default="{ row }">{{ hasUnverifiedCost(row) ? '待核对' : pct(row.profitRate) }}</template>
            </el-table-column>
            <el-table-column label="成本依据" min-width="120">
              <template #default="{ row }"><span :title="row.costWarnings?.join('；')">{{ row.costStatus || (hasUnverifiedCost(row) ? '待核对' : '已核对') }}</span></template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-card>

    <!-- 大户排行 + 流失预警 -->
    <div class="dual-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <h3>大户排行榜</h3>
            <div class="card-actions">
              <el-select v-model="topLimit" style="width: 110px" @change="loadTopCustomers">
                <el-option v-for="n in [10, 20, 50]" :key="n" :label="`Top ${n}`" :value="n" />
              </el-select>
              <el-button @click="handleExport('top')" :loading="exporting.top" size="small">导出</el-button>
            </div>
          </div>
        </template>
        <el-table :data="topCustomers" v-loading="topLoading" size="small" :max-height="400"
                  @row-click="(row: any) => router.push('/marketing/customer/list')">
          <el-table-column label="客户" min-width="140">
            <template #default="{ row }">
              <div>{{ row.customerName || `客户#${row.customerId}` }}</div>
              <div class="cell-sub">{{ row.phone || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="累计消费" width="120" align="right">
            <template #default="{ row }">{{ currency(row.totalOrderAmount) }}</template>
          </el-table-column>
          <el-table-column label="最近购买" width="150">
            <template #default="{ row }">{{ formatDateTime(row.lastOrderTime) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <h3>流失预警</h3>
            <div class="card-actions">
              <el-button type="primary" plain @click="loadChurnRisks" :loading="churnLoading" size="small">刷新</el-button>
              <el-button @click="handleExport('churn')" :loading="exporting.churn" size="small">导出</el-button>
            </div>
          </div>
        </template>
        <el-table :data="churnRisks" v-loading="churnLoading" size="small" :max-height="400"
                  @row-click="(row: any) => router.push('/marketing/customer/list')">
          <el-table-column label="客户" min-width="140">
            <template #default="{ row }">
              <div>{{ row.customerName || `客户#${row.customerId}` }}</div>
              <div class="cell-sub">{{ row.phone || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="流失天数" width="90" align="right">
            <template #default="{ row }">
              <span :class="{ 'text-danger': (row.daysSinceLastOrder || 0) > 90 }">{{ row.daysSinceLastOrder || 0 }} 天</span>
            </template>
          </el-table-column>
          <el-table-column label="历史贡献" width="110" align="right">
            <template #default="{ row }">{{ currency(row.totalOrderAmount) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 滞销库存 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <h3>滞销库存分析</h3>
          <div class="card-actions">
            <el-button type="primary" plain @click="loadSlowMoving" :loading="slowLoading" size="small">刷新</el-button>
            <el-button @click="handleExport('slow')" :loading="exporting.slow" size="small">导出</el-button>
          </div>
        </div>
      </template>
      <el-table :data="slowMoving" v-loading="slowLoading" size="small"
                @row-click="(row: any) => router.push('/inventory/list')">
        <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次" width="130" show-overflow-tooltip />
        <el-table-column label="剩余数量" width="100" align="right">
          <template #default="{ row }">{{ toNumber(row.quantity).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="占用金额" width="120" align="right">
          <template #default="{ row }">{{ currency(row.value) }}</template>
        </el-table-column>
        <el-table-column label="生产日期" width="110">
          <template #default="{ row }">{{ formatDateOnly(row.productionDate) }}</template>
        </el-table-column>
        <el-table-column label="库龄" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': (row.ageDays || 0) > 180 }">{{ row.ageDays || 0 }} 天</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import {
  exportChurnRisks, exportSalesTrend, exportSlowMovingInventory, exportTopCustomers,
  getChurnRisks, getCreditRisk, getInventoryValue, getSalesTrend, getSlowMovingInventory, getTopCustomers
} from '@/api/report'
import { getAllStores } from '@/api/store'
import type {
  ChurnRiskItem, CreditRiskSummary, CustomerRankItem, InventoryValueSummary, SalesTrendItem, SlowMovingInventoryItem
} from '@/types/report'
import type { StoreDetailVO } from '@/types/store'
import {hasUnverifiedCost, profitForChart} from '@/utils/reportCost'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const router = useRouter()

const storeOptions = ref<StoreDetailVO[]>([])
const storeId = ref<number | undefined>(undefined)
const creditRisk = ref<CreditRiskSummary>({})
const inventoryValue = ref<InventoryValueSummary>({})
const salesTrend = ref<SalesTrendItem[]>([])
const topCustomers = ref<CustomerRankItem[]>([])
const churnRisks = ref<ChurnRiskItem[]>([])
const slowMoving = ref<SlowMovingInventoryItem[]>([])

const riskLoading = ref(false)
const inventoryLoading = ref(false)
const salesLoading = ref(false)
const topLoading = ref(false)
const churnLoading = ref(false)
const slowLoading = ref(false)

const allLoading = computed(() =>
  riskLoading.value || inventoryLoading.value || salesLoading.value ||
  topLoading.value || churnLoading.value || slowLoading.value
)

const topLimit = ref(20)
const formatDateText = (d: Date) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
const defaultRange = () => { const end = new Date(); const start = new Date(); start.setDate(end.getDate()-29); return [formatDateText(start), formatDateText(end)] }
const salesDateRange = ref<string[]>(defaultRange())
const exporting = reactive({ sales: false, top: false, churn: false, slow: false })

const toNumber = (v?: number | null) => Number(v || 0)
const currency = (v?: number | null) => `¥${toNumber(v).toFixed(2)}`
const pct = (v?: number | null) => v == null ? '—' : `${toNumber(v).toFixed(2)}%`
const storeName = computed(() => !storeId.value ? '全部门店' : storeOptions.value.find(s => s.id === storeId.value)?.name || `门店#${storeId.value}`)
const formatDateOnly = (v?: string) => v ? String(v).slice(0, 10) : '-'
const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const C = { primary: '#1d5d73', accent: '#7ea76d', muted: '#9ca3af' }

const salesChartOption = computed(() => ({
  tooltip: {
    trigger: 'axis', axisPointer: { type: 'cross' },
    formatter: (params: any) => {
      const list = Array.isArray(params) ? params : [params]
      const date = list[0]?.axisValueLabel || ''
      const rows = list.map((p: any) => `<div>${p.marker} ${p.seriesName}: ${p.value == null ? '待核对' : '¥' + Number(p.value).toFixed(2)}</div>`).join('')
      return `<div style="font-weight:600;margin-bottom:4px">${date}</div>${rows}`
    }
  },
  legend: { data: ['销售额', '成本额', '毛利额'], bottom: 0 },
  grid: { top: 12, right: 16, bottom: 40, left: 50, containLabel: false },
  xAxis: { type: 'category', data: salesTrend.value.map(r => r.date), axisTick: { show: false } },
  yAxis: { type: 'value', axisLabel: { formatter: (v: number) => `¥${Number(v).toFixed(0)}` } },
  series: [
    { name: '销售额', type: 'line', smooth: true, data: salesTrend.value.map(r => r.saleAmount ?? 0), lineStyle: { width: 2.5, color: C.primary }, itemStyle: { color: C.primary }, areaStyle: { color: 'rgba(29,93,115,0.08)' } },
    { name: '成本额', type: 'line', smooth: true, data: salesTrend.value.map(r => hasUnverifiedCost(r) ? null : r.costAmount ?? 0), lineStyle: { width: 2, color: C.muted, type: 'dashed' }, itemStyle: { color: C.muted } },
    { name: '毛利额', type: 'line', smooth: true, data: salesTrend.value.map(profitForChart), lineStyle: { width: 2.5, color: C.accent }, itemStyle: { color: C.accent }, areaStyle: { color: 'rgba(126,167,109,0.10)' } }
  ]
}))

const buildSalesParams = () => {
  if (!Array.isArray(salesDateRange.value) || salesDateRange.value.length !== 2) salesDateRange.value = defaultRange()
  return { startTime: `${salesDateRange.value[0]}T00:00:00`, endTime: `${salesDateRange.value[1]}T23:59:59`, storeId: storeId.value }
}

const loadCreditRisk = async () => { riskLoading.value = true; try { creditRisk.value = await getCreditRisk(storeId.value) } catch { creditRisk.value = {} } finally { riskLoading.value = false } }
const loadInventoryValue = async () => { inventoryLoading.value = true; try { inventoryValue.value = await getInventoryValue(storeId.value) } catch { inventoryValue.value = {} } finally { inventoryLoading.value = false } }
const loadSalesTrend = async () => { salesLoading.value = true; try { salesTrend.value = await getSalesTrend(buildSalesParams()) } catch { salesTrend.value = [] } finally { salesLoading.value = false } }
const loadTopCustomers = async () => { topLoading.value = true; try { topCustomers.value = await getTopCustomers(topLimit.value, storeId.value) } catch { topCustomers.value = [] } finally { topLoading.value = false } }
const loadChurnRisks = async () => { churnLoading.value = true; try { churnRisks.value = await getChurnRisks(storeId.value) } catch { churnRisks.value = [] } finally { churnLoading.value = false } }
const loadSlowMoving = async () => { slowLoading.value = true; try { slowMoving.value = await getSlowMovingInventory(storeId.value) } catch { slowMoving.value = [] } finally { slowLoading.value = false } }

const refreshAll = async () => { await Promise.all([loadCreditRisk(), loadInventoryValue(), loadSalesTrend(), loadTopCustomers(), loadChurnRisks(), loadSlowMoving()]) }

const handleExport = async (type: 'sales' | 'top' | 'churn' | 'slow') => {
  exporting[type] = true
  try {
    switch (type) {
      case 'sales': await exportSalesTrend(buildSalesParams()); break
      case 'top': await exportTopCustomers(topLimit.value, storeId.value); break
      case 'churn': await exportChurnRisks(storeId.value); break
      case 'slow': await exportSlowMovingInventory(storeId.value); break
    }
  } catch (error: any) { ElMessage.error(error?.message || '导出失败') }
  finally { exporting[type] = false }
}

onMounted(async () => {
  try { storeOptions.value = await getAllStores() } catch { /* non-admin */ }
  refreshAll()
})
</script>

<style scoped lang="scss">
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  h3 { margin: 0; font-size: 14px; font-weight: 700; color: #1f2937; }
}

.card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.split-view {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.split-chart { width: 100%; height: 340px; }
.split-table { min-width: 0; }

.cell-sub { margin-top: 4px; font-size: 12px; color: #9ca3af; }

@media (max-width: 1500px) {
  .split-view { grid-template-columns: 1fr; }
}
</style>

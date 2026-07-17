<template>
  <div class="app-page">
    <section class="page-header">
      <div><h1>门店统计</h1></div>
      <div class="header-controls">
        <el-select v-model="storeId" filterable placeholder="选择门店" style="width: 200px" @change="refreshAll">
          <el-option v-for="s in storeOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button @click="refreshAll" :loading="loading" :icon="Refresh">刷新</el-button>
      </div>
    </section>

    <el-empty v-if="!storeId" description="请选择门店查看数据" />

    <template v-else>
      <section class="metric-grid">
        <article v-for="m in metrics" :key="m.title" class="metric-card" :class="m.tone" style="cursor:default">
          <div class="metric-label">
            <span>{{ m.title }}</span>
            <el-icon><component :is="m.icon" /></el-icon>
          </div>
          <div class="metric-value">{{ m.value }}</div>
          <div class="metric-foot">{{ m.foot }}</div>
        </article>
      </section>

      <section class="chart-row">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <h3>销售趋势</h3>
              <el-radio-group v-model="trendRange" size="small" @change="loadSalesTrend">
                <el-radio-button :value="7">近 7 天</el-radio-button>
                <el-radio-button :value="30">近 30 天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <v-chart class="chart" :option="trendOption" autoresize v-loading="trendLoading" />
        </el-card>

        <el-card shadow="never">
          <template #header><div class="card-head"><h3>赊销风控</h3></div></template>
          <div class="risk-wrap" v-loading="riskLoading">
            <v-chart class="chart donut" :option="riskOption" autoresize />
            <div class="risk-details">
              <div class="risk-row"><span>总欠款</span><strong>{{ currency(creditRisk.totalDebt) }}</strong></div>
              <div class="risk-row"><span>逾期</span><strong class="warn">{{ currency(creditRisk.overdueDebt) }}</strong></div>
              <div class="risk-row"><span>本月新增赊销</span><strong>{{ currency(creditRisk.monthlyNewCredit) }}</strong></div>
              <div class="risk-row"><span>本月回款</span><strong>{{ currency(creditRisk.monthlyRepayment) }}</strong></div>
            </div>
          </div>
        </el-card>
      </section>

      <section class="chart-row">
        <el-card shadow="never">
          <template #header><div class="card-head"><h3>库存资金占用</h3></div></template>
          <v-chart class="chart" :option="inventoryOption" autoresize v-loading="inventoryLoading" />
        </el-card>

        <el-card shadow="never">
          <template #header><div class="card-head"><h3>滞销商品 Top 8</h3></div></template>
          <el-table :data="slowMoving" v-loading="slowLoading" :max-height="320" size="small">
            <el-table-column prop="productName" label="商品" min-width="160" show-overflow-tooltip />
            <el-table-column prop="batchNo" label="批次" width="120" show-overflow-tooltip />
            <el-table-column label="剩余数量" width="100" align="right">
              <template #default="{ row }">{{ (row.quantity ?? 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="占用金额" width="120" align="right">
              <template #default="{ row }">{{ currency(row.value) }}</template>
            </el-table-column>
            <el-table-column label="库龄" width="90" align="right">
              <template #default="{ row }">{{ row.ageDays || 0 }} 天</template>
            </el-table-column>
          </el-table>
        </el-card>
      </section>

      <section class="dual-grid">
        <el-card shadow="never">
          <template #header><div class="card-head"><h3>大户排行</h3></div></template>
          <el-table :data="topCustomers" v-loading="topLoading" :max-height="320" size="small">
            <el-table-column label="客户" min-width="140">
              <template #default="{ row }">{{ row.customerName || `客户#${row.customerId}` }}</template>
            </el-table-column>
            <el-table-column label="累计消费" width="130" align="right">
              <template #default="{ row }">{{ currency(row.totalOrderAmount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header><div class="card-head"><h3>流失预警</h3></div></template>
          <el-table :data="churnRisks" v-loading="churnLoading" :max-height="320" size="small">
            <el-table-column label="客户" min-width="120">
              <template #default="{ row }">{{ row.customerName || `客户#${row.customerId}` }}</template>
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
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, ShoppingCart, Money, User, Warning, Box, Coin, TrendCharts } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { getCreditRisk, getDashboardToday, getInventoryValue, getSalesTrend, getSlowMovingInventory, getTopCustomers, getChurnRisks } from '@/api/report'
import { getAllStores } from '@/api/store'
import type { CreditRiskSummary, DashboardSummary, InventoryValueSummary, SalesTrendItem, SlowMovingInventoryItem, CustomerRankItem, ChurnRiskItem } from '@/types/report'
import type { StoreDetailVO } from '@/types/store'

use([CanvasRenderer, LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const storeOptions = ref<StoreDetailVO[]>([])
const storeId = ref<number | undefined>(undefined)
const dashboard = ref<DashboardSummary>({})
const creditRisk = ref<CreditRiskSummary>({})
const inventoryValue = ref<InventoryValueSummary>({})
const salesTrend = ref<SalesTrendItem[]>([])
const slowMoving = ref<SlowMovingInventoryItem[]>([])
const topCustomers = ref<CustomerRankItem[]>([])
const churnRisks = ref<ChurnRiskItem[]>([])

const trendRange = ref(7)
const dashLoading = ref(false)
const riskLoading = ref(false)
const inventoryLoading = ref(false)
const trendLoading = ref(false)
const slowLoading = ref(false)
const topLoading = ref(false)
const churnLoading = ref(false)

const loading = computed(() =>
  dashLoading.value || riskLoading.value || inventoryLoading.value ||
  trendLoading.value || slowLoading.value || topLoading.value || churnLoading.value
)

const toNumber = (v?: number | null) => Number(v || 0)
const currency = (v?: number | null) => `¥${toNumber(v).toFixed(2)}`
const percent = (v?: number | null) => `${toNumber(v).toFixed(2)}%`

const metrics = computed(() => {
  const pending = toNumber(dashboard.value.pendingAfterSalesCount) + toNumber(dashboard.value.pendingDeliveryCount)
  return [
    { title: '今日订单', value: `${toNumber(dashboard.value.orderCount)}`, foot: `GMV ${currency(dashboard.value.gmv)}`, icon: ShoppingCart, tone: 'tone-blue' },
    { title: '今日净销额', value: currency(dashboard.value.netSales), foot: `新增赊销 ${currency(dashboard.value.newCreditAmount)}`, icon: Money, tone: 'tone-green' },
    { title: '新增客户', value: `${toNumber(dashboard.value.newCustomerCount)}`, foot: '', icon: User, tone: 'tone-orange' },
    { title: '待处理', value: `${pending}`, foot: `售后 ${toNumber(dashboard.value.pendingAfterSalesCount)} / 配送 ${toNumber(dashboard.value.pendingDeliveryCount)}`, icon: Warning, tone: 'tone-red' },
    { title: '库存预警', value: `${toNumber(dashboard.value.inventoryAlertCount)}`, foot: '库龄 > 180 天', icon: Warning, tone: 'tone-sand' },
    { title: '库存占用', value: currency(inventoryValue.value.totalInventoryValue), foot: `180 天+ ${currency(inventoryValue.value.over180DaysValue)}`, icon: Box, tone: 'tone-ink' },
    { title: '总欠款', value: currency(creditRisk.value.totalDebt), foot: `逾期 ${currency(creditRisk.value.overdueDebt)}`, icon: Coin, tone: 'tone-wine' },
    { title: '回款率', value: percent(creditRisk.value.repaymentRate), foot: `本月回款 ${currency(creditRisk.value.monthlyRepayment)}`, icon: TrendCharts, tone: 'tone-violet' }
  ]
})

const C = { primary: '#1d5d73', accent: '#7ea76d', warn: '#c2410c' }

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
  legend: { data: ['销售额', '毛利额'], bottom: 0 },
  grid: { top: 10, right: 20, bottom: 36, left: 50, containLabel: false },
  xAxis: { type: 'category', data: salesTrend.value.map(r => r.date), axisTick: { show: false } },
  yAxis: { type: 'value', axisLabel: { formatter: (v: number) => `¥${Number(v).toFixed(0)}` } },
  series: [
    { name: '销售额', type: 'line', smooth: true, data: salesTrend.value.map(r => r.saleAmount ?? 0), lineStyle: { width: 3, color: C.primary }, itemStyle: { color: C.primary }, areaStyle: { color: 'rgba(29,93,115,0.10)' } },
    { name: '毛利额', type: 'line', smooth: true, data: salesTrend.value.map(r => r.profit ?? 0), lineStyle: { width: 3, color: C.accent }, itemStyle: { color: C.accent }, areaStyle: { color: 'rgba(126,167,109,0.12)' } }
  ]
}))

const riskOption = computed(() => {
  const rate = toNumber(creditRisk.value.repaymentRate)
  const un = Math.max(0, 100 - rate)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
    series: [{
      type: 'pie', radius: ['62%', '85%'], center: ['50%', '50%'], avoidLabelOverlap: false,
      label: { show: true, position: 'center', formatter: () => `{r|${rate.toFixed(1)}%}\n{s|回款率}`, rich: { r: { fontSize: 24, fontWeight: 700, color: '#1f2937', lineHeight: 32 }, s: { fontSize: 12, color: '#9ca3af', lineHeight: 20 } } },
      labelLine: { show: false },
      data: [
        { value: rate, name: '已回款', itemStyle: { color: C.accent } },
        { value: un || 0, name: '未回款', itemStyle: { color: '#fde68a' } }
      ]
    }]
  }
})

const inventoryOption = computed(() => {
  const total = toNumber(inventoryValue.value.totalInventoryValue)
  const over = toNumber(inventoryValue.value.over180DaysValue)
  const fresh = Math.max(0, total - over)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['180 天内', '180 天以上'], bottom: 0 },
    grid: { top: 10, right: 20, bottom: 36, left: 50, containLabel: false },
    xAxis: { type: 'category', data: ['当前库存'], axisTick: { show: false } },
    yAxis: { type: 'value', axisLabel: { formatter: (v: number) => `¥${Number(v).toFixed(0)}` } },
    series: [
      { name: '180 天内', type: 'bar', stack: 't', data: [fresh], itemStyle: { color: C.primary, borderRadius: [0, 0, 4, 4] }, barWidth: 60 },
      { name: '180 天以上', type: 'bar', stack: 't', data: [over], itemStyle: { color: C.warn, borderRadius: [4, 4, 0, 0] }, barWidth: 60 }
    ]
  }
})

const todayISO = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const daysAgoISO = (n: number) => { const d = new Date(); d.setDate(d.getDate()-n); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }

const loadDashboard = async () => { if (!storeId.value) return; dashLoading.value = true; try { dashboard.value = await getDashboardToday(storeId.value) } catch { dashboard.value = {} } finally { dashLoading.value = false } }
const loadCreditRisk = async () => { if (!storeId.value) return; riskLoading.value = true; try { creditRisk.value = await getCreditRisk(storeId.value) } catch { creditRisk.value = {} } finally { riskLoading.value = false } }
const loadInventoryValue = async () => { if (!storeId.value) return; inventoryLoading.value = true; try { inventoryValue.value = await getInventoryValue(storeId.value) } catch { inventoryValue.value = {} } finally { inventoryLoading.value = false } }
const loadSalesTrend = async () => { if (!storeId.value) return; trendLoading.value = true; try { salesTrend.value = await getSalesTrend({ startTime: `${daysAgoISO(trendRange.value-1)}T00:00:00`, endTime: `${todayISO()}T23:59:59`, storeId: storeId.value }) } catch { salesTrend.value = [] } finally { trendLoading.value = false } }
const loadSlowMoving = async () => { if (!storeId.value) return; slowLoading.value = true; try { const data = await getSlowMovingInventory(storeId.value); slowMoving.value = (data || []).slice(0, 8) } catch { slowMoving.value = [] } finally { slowLoading.value = false } }
const loadTopCustomers = async () => { if (!storeId.value) return; topLoading.value = true; try { topCustomers.value = await getTopCustomers(10, storeId.value) } catch { topCustomers.value = [] } finally { topLoading.value = false } }
const loadChurnRisks = async () => { if (!storeId.value) return; churnLoading.value = true; try { churnRisks.value = await getChurnRisks(storeId.value) } catch { churnRisks.value = [] } finally { churnLoading.value = false } }

const refreshAll = async () => {
  if (!storeId.value) return
  await Promise.all([loadDashboard(), loadCreditRisk(), loadInventoryValue(), loadSalesTrend(), loadSlowMoving(), loadTopCustomers(), loadChurnRisks()])
}

onMounted(async () => {
  try { storeOptions.value = await getAllStores(); if (storeOptions.value.length > 0) { storeId.value = storeOptions.value[0].id; refreshAll() } }
  catch { /* non-admin */ }
})
</script>

<style scoped lang="scss">
.chart { width: 100%; height: 280px; }
.donut { height: 220px; }

.card-head {
  display: flex; justify-content: space-between; align-items: center;
  h3 { margin: 0; font-size: 14px; font-weight: 700; color: #1f2937; }
}

.risk-wrap {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px; align-items: center;
}

.risk-details { display: flex; flex-direction: column; gap: 8px; }

.risk-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; border-radius: 8px; background: #f9fafb;
  font-size: 13px; color: #6b7280;
  strong { font-size: 14px; color: #1f2937; &.warn { color: #dc2626; } }
}

@media (max-width: 600px) {
  .risk-wrap { grid-template-columns: 1fr; }
}
</style>

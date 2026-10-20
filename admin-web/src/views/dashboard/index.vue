<template>
  <div class="app-page">
    <!-- 页面头 -->
    <section class="page-header">
      <div>
        <h1>门店工作台</h1>
        <div class="page-subtitle">
          {{ userStore.userInfo?.name || '当前员工' }} · 经营数据与门店待办
        </div>
      </div>
      <div class="header-controls">
        <el-button @click="refreshAll" :loading="loading" :icon="Refresh">刷新</el-button>
      </div>
    </section>

    <!-- 指标卡 -->
    <section class="metric-grid">
      <article
        v-for="m in metrics"
        :key="m.title"
        class="metric-card"
        :class="{ 'metric-link': can(m.permission) }"
        :role="can(m.permission) ? 'link' : undefined"
        :tabindex="can(m.permission) ? 0 : undefined"
        @keydown.enter="go(m.route, m.permission)"
        @click="go(m.route, m.permission)"
      >
        <div class="metric-label">
          <el-tooltip v-if="m.description" :content="m.description" placement="top"><span>{{ m.title }}</span></el-tooltip>
          <span v-else>{{ m.title }}</span>
          <el-icon><component :is="m.icon" /></el-icon>
        </div>
        <div class="metric-value">{{ m.value }}</div>
        <div class="metric-foot">{{ m.foot }}</div>
      </article>
    </section>

    <section class="data-status" aria-live="polite">
      <div v-for="key in allowedSections" :key="key" :class="{ 'status-error': sectionState[key].error }">
        <span>{{ sectionNames[key] }} · {{ sectionNotice(key) }}</span>
        <el-button v-if="sectionState[key].error" link type="primary" @click="reloadSection(key)">重试</el-button>
      </div>
    </section>
    <section v-if="tasks.length" class="task-section">
      <h2>现在要办</h2>
      <div class="task-grid">
        <button v-for="task in tasks" :key="task.title" class="task-link" @click="go(task.route, task.permission)">
          <el-icon><component :is="task.icon" /></el-icon>
          <span><strong>{{ task.title }}</strong><small>{{ task.detail }}</small></span><span aria-hidden="true">→</span>
        </button>
      </div>
    </section>

    <!-- 销售趋势 + 赊销风控 -->
    <section class="chart-row">
      <el-card v-if="can('report:sales')" shadow="never">
        <template #header>
          <div class="card-head">
            <h3>销售趋势</h3>
            <el-radio-group v-model="trendRange" size="small" @change="loadSalesTrend">
              <el-radio-button :value="7">近 7 天</el-radio-button>
              <el-radio-button :value="30">近 30 天</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <v-chart v-if="sectionState.trend.loaded" class="chart" :option="trendOption" autoresize v-loading="trendLoading" />
        <el-empty v-else :description="sectionNotice('trend')" :image-size="64" />
      </el-card>

      <el-card v-if="can('report:finance:query')" shadow="never">
        <template #header>
          <div class="card-head"><h3>赊销风控</h3></div>
        </template>
        <el-empty v-if="!sectionState.risk.loaded" :description="sectionNotice('risk')" :image-size="64" />
        <div v-else class="risk-wrap" v-loading="riskLoading">
          <v-chart class="chart donut" :option="riskOption" autoresize />
          <div class="risk-details">
            <div class="risk-row">
              <span>总欠款</span>
              <el-button v-if="can('credit:account:view')" link type="primary" @click="go('/credit', 'credit:account:view')">{{ currency(creditRisk.totalDebt) }}</el-button><strong v-else>{{ currency(creditRisk.totalDebt) }}</strong>
            </div>
            <div class="risk-row">
              <span>逾期</span>
              <strong class="warn">{{ currency(creditRisk.overdueDebt) }}</strong>
            </div>
            <div class="risk-row">
              <span>本月新增赊销</span>
              <strong>{{ currency(creditRisk.monthlyNewCredit) }}</strong>
            </div>
            <div class="risk-row">
              <span>本月回款</span>
              <strong>{{ currency(creditRisk.monthlyRepayment) }}</strong>
            </div>
          </div>
        </div>
      </el-card>
    </section>

    <!-- 库存占用 + 滞销商品 -->
    <section class="chart-row">
      <el-card v-if="can('report:inventory')" shadow="never">
        <template #header>
          <div class="card-head"><h3>库存资金占用</h3></div>
        </template>
        <v-chart v-if="sectionState.inventory.loaded" class="chart" :option="inventoryOption" autoresize v-loading="inventoryLoading" />
        <el-empty v-else :description="sectionNotice('inventory')" :image-size="64" />
      </el-card>

      <el-card v-if="can('report:inventory')" shadow="never">
        <template #header>
          <div class="card-head">
            <h3>滞销商品 Top 8</h3>
            <el-button v-if="can('inventory:view')" size="small" text type="primary" @click="go('/inventory/warning', 'inventory:view')">查看全部 →</el-button>
          </div>
        </template>
        <el-empty v-if="!sectionState.slow.loaded" :description="sectionNotice('slow')" :image-size="64" />
        <el-table v-else :data="slowMoving" v-loading="slowLoading" :max-height="320" size="small"
                  @row-click="(row: any) => go('/inventory/list', 'inventory:view')">
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
  </div>
</template>

<script setup lang="ts">
import {profitForChart} from '@/utils/reportCost'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, ShoppingCart, Money, Document, Box, Coin } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import { hasPermission } from '@/utils/permission'
import {
  getCreditRisk,
  getDashboardToday,
  getInventoryValue,
  getSalesTrend,
  getSlowMovingInventory
} from '@/api/report'
import type {
  CreditRiskSummary,
  DashboardSummary,
  InventoryValueSummary,
  SalesTrendItem,
  SlowMovingInventoryItem
} from '@/types/report'

use([CanvasRenderer, LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const can = (permission: string) => hasPermission(permission, permissionStore.permissions)

const dashboard = ref<DashboardSummary>({})
const creditRisk = ref<CreditRiskSummary>({})
const inventoryValue = ref<InventoryValueSummary>({})
const salesTrend = ref<SalesTrendItem[]>([])
const slowMoving = ref<SlowMovingInventoryItem[]>([])

const trendRange = ref(7)
type Section = 'dashboard' | 'risk' | 'inventory' | 'trend' | 'slow'
const sectionPermissions: Record<Section, string> = { dashboard: 'report:dashboard', risk: 'report:finance:query', inventory: 'report:inventory', trend: 'report:sales', slow: 'report:inventory' }
const sections: Section[] = ['dashboard', 'risk', 'inventory', 'trend', 'slow']
const sectionState = reactive(Object.fromEntries(sections.map(key => [key, {loading: false, loaded: false, error: '', updatedAt: ''}])) as Record<Section, {loading: boolean, loaded: boolean, error: string, updatedAt: string}>)
const requestVersions: Record<Section, number> = {dashboard: 0, risk: 0, inventory: 0, trend: 0, slow: 0}
const contextIdentity = () => JSON.stringify([userStore.token, userStore.userInfo?.id, userStore.userInfo?.extra?.storeId, permissionStore.permissions])
const loading = computed(() => sections.some(key => sectionState[key].loading))
const trendLoading = computed(() => sectionState.trend.loading)
const riskLoading = computed(() => sectionState.risk.loading)
const inventoryLoading = computed(() => sectionState.inventory.loading)
const slowLoading = computed(() => sectionState.slow.loading)
const allowedSections = computed(() => sections.filter(key => can(sectionPermissions[key])))
const sectionNames: Record<Section, string> = {dashboard: '今日经营', risk: '赊销风控', inventory: '库存资金', trend: '销售趋势', slow: '滞销商品'}
const sectionNotice = (key: Section) => {
  const state = sectionState[key]
  if (state.error) return state.loaded ? '刷新失败，显示上次成功数据' : '暂未获取数据，请重试'
  if (state.loading) return state.loaded ? '正在刷新，显示上次成功数据' : '正在加载'
  return state.updatedAt ? `更新于 ${state.updatedAt}` : '尚未获取'
}
const toNumber = (v?: number | null) => Number(v || 0)
const currency = (v?: number | null) => `¥${toNumber(v).toFixed(2)}`
const shown = (key: Section, value: string) => sectionState[key].loaded ? value : '—'
const go = (path?: string, permission?: string) => { if (path && (!permission || can(permission))) router.push(path) }
const metrics = computed(() => [
  {title: '今日净销额', section: 'dashboard' as Section, value: shown('dashboard', currency(dashboard.value.netSales)), foot: '含赊销 · 已扣完成退货', description: '今日销售单截至当前的净成交，含赊销；已扣完成退货', icon: Money, route: '/order', permission: 'order:query'},
  {title: '今日订单', section: 'dashboard' as Section, value: shown('dashboard', `${toNumber(dashboard.value.orderCount)}`), foot: `新增赊销 ${shown('dashboard', currency(dashboard.value.newCreditAmount))}`, icon: ShoppingCart, route: '/order', permission: 'order:query'},
  {title: '总欠款', section: 'risk' as Section, value: shown('risk', currency(creditRisk.value.totalDebt)), foot: `逾期 ${shown('risk', currency(creditRisk.value.overdueDebt))}`, icon: Coin, route: '/credit', permission: 'credit:account:view'},
  {title: '库存占用', section: 'inventory' as Section, value: shown('inventory', currency(inventoryValue.value.totalInventoryValue)), foot: `180 天以上 ${shown('inventory', currency(inventoryValue.value.over180DaysValue))}`, icon: Box, route: '/inventory/list', permission: 'inventory:view'}
].filter(item => can(sectionPermissions[item.section])))
const tasks = computed(() => [
  {title: '待派送', route: '/order?deliveryStatus=2', permission: 'order:query', detail: shown('dashboard', `${toNumber(dashboard.value.pendingDeliveryCount)} 单可派送`), icon: Box},
  {title: '售后处理', route: '/after-sales', permission: 'afterSales:query', detail: shown('dashboard', `${toNumber(dashboard.value.pendingAfterSalesCount)} 单待处理`), icon: Document},
  {title: '采购收货', route: '/inventory/purchase/order?status=2', permission: 'purchase:query', detail: '查看待收货采购单', icon: ShoppingCart},
  {title: '欠款跟进', route: '/credit', permission: 'credit:account:view', detail: '核对客户欠款与回款', icon: Coin}
].filter(item => can(item.permission)))

const C = { primary: '#1d5d73', accent: '#7ea76d', warn: '#c2410c' }

const trendOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'cross' },
    formatter: (params: any) => {
      const list = Array.isArray(params) ? params : [params]
      const date = list[0]?.axisValueLabel || ''
      const rows = list.map((p: any) => `<div>${p.marker} ${p.seriesName}: ${p.value == null ? '待核对' : '¥' + Number(p.value).toFixed(2)}</div>`).join('')
      return `<div style="font-weight:600;margin-bottom:4px">${date}</div>${rows}`
    }
  },
  legend: { data: ['销售额', '毛利额'], bottom: 0 },
  grid: { top: 10, right: 20, bottom: 36, left: 50, containLabel: false },
  xAxis: { type: 'category', data: salesTrend.value.map(r => r.date), axisTick: { show: false } },
  yAxis: { type: 'value', axisLabel: { formatter: (v: number) => `¥${Number(v).toFixed(0)}` } },
  series: [
    { name: '销售额', type: 'line', smooth: true, data: salesTrend.value.map(r => r.saleAmount ?? 0), lineStyle: { width: 3, color: C.primary }, itemStyle: { color: C.primary }, areaStyle: { color: 'rgba(29,93,115,0.10)' } },
    { name: '毛利额', type: 'line', smooth: true, data: salesTrend.value.map(profitForChart), lineStyle: { width: 3, color: C.accent }, itemStyle: { color: C.accent }, areaStyle: { color: 'rgba(126,167,109,0.12)' } }
  ]
}))

const riskOption = computed(() => {
  const rate = toNumber(creditRisk.value.repaymentRate)
  const un = Math.max(0, 100 - rate)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
    series: [{
      type: 'pie', radius: ['62%', '85%'], center: ['50%', '50%'], avoidLabelOverlap: false,
      label: { show: true, position: 'center', formatter: () => `{r|${rate.toFixed(1)}%}\n{s|回款率}`, rich: { r: { fontSize: 26, fontWeight: 700, color: '#1f2937', lineHeight: 34 }, s: { fontSize: 12, color: '#596579', lineHeight: 22 } } },
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

const loadSection = async <T,>(key: Section, request: () => Promise<T>, apply: (data: T) => void) => {
  const version = ++requestVersions[key]
  if (!can(sectionPermissions[key])) return
  const identity = contextIdentity()
  const state = sectionState[key]
  state.loading = true
  state.error = ''
  const current = () => version === requestVersions[key] && identity === contextIdentity() && can(sectionPermissions[key])
  try {
    const data = await request()
    if (!current()) return
    apply(data)
    state.loaded = true
    state.updatedAt = new Date().toLocaleTimeString('zh-CN', {hour12: false})
  } catch {
    if (current()) state.error = '读取失败'
  } finally {
    if (current()) state.loading = false
  }
}
const loadDashboard = () => loadSection('dashboard', getDashboardToday, data => {dashboard.value = data})
const loadCreditRisk = () => loadSection('risk', getCreditRisk, data => {creditRisk.value = data})
const loadInventoryValue = () => loadSection('inventory', getInventoryValue, data => {inventoryValue.value = data})
const loadSalesTrend = () => loadSection('trend', () => getSalesTrend({startTime: `${daysAgoISO(trendRange.value-1)}T00:00:00`, endTime: `${todayISO()}T23:59:59`}), data => {salesTrend.value = data})
const loadSlowMoving = () => loadSection('slow', getSlowMovingInventory, data => {slowMoving.value = (data || []).slice(0, 8)})
const reloadSection = (key: Section) => ({dashboard: loadDashboard, risk: loadCreditRisk, inventory: loadInventoryValue, trend: loadSalesTrend, slow: loadSlowMoving}[key])()
const refreshAll = async () => {await Promise.all(sections.map(reloadSection))}
watch(contextIdentity, () => {
  sections.forEach(key => {
    ++requestVersions[key]
    Object.assign(sectionState[key], {loading: false, loaded: false, error: '', updatedAt: ''})
  })
  dashboard.value = {}; creditRisk.value = {}; inventoryValue.value = {}; salesTrend.value = []; slowMoving.value = []
  void refreshAll()
}, {immediate: true, flush: 'sync'})
onUnmounted(() => sections.forEach(key => ++requestVersions[key]))
</script>

<style scoped lang="scss">
.metric-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.metric-card { cursor: default; }
.metric-link { cursor: pointer; }
.metric-link:focus-visible, .task-link:focus-visible { outline: 3px solid #1d5d73; outline-offset: 3px; }
.data-status { display: flex; flex-wrap: wrap; gap: 4px 20px; margin: -8px 0 20px; color: #596579; font-size: 12px; }
.data-status > div { display: flex; align-items: center; gap: 8px; }
.status-error { color: #b45309; }
.task-section { margin-bottom: 20px; }
.task-section h2 { font-size: 16px; margin: 0 0 12px; }
.task-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.task-link { display: flex; align-items: center; gap: 12px; text-align: left; border: 1px solid #dce4e8; background: #fff; border-radius: 10px; padding: 16px; color: #1d5d73; cursor: pointer; font: inherit; }
.task-link:hover { border-color: #1d5d73; background: #f5f9fa; }
.task-link span:nth-child(2) { flex: 1; min-width: 0; }
.task-link strong { display: block; color: #243243; font-size: 14px; }
.task-link small { display: block; color: #596579; margin-top: 5px; font-size: 12px; }
.chart-row:empty { display: none; }
@media (max-width: 1100px) { .metric-grid, .task-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .metric-grid, .task-grid { grid-template-columns: 1fr; } }

.chart { width: 100%; height: 280px; }
.donut { height: 220px; }

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  h3 { margin: 0; font-size: 14px; font-weight: 700; color: #1f2937; }
}

.risk-wrap {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: center;
}

.risk-details {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.risk-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f9fafb;
  font-size: 13px;
  color: #6b7280;

  strong {
    font-size: 14px;
    color: #1f2937;

    &.link { cursor: pointer; &:hover { text-decoration: underline; color: #1d5d73; } }
    &.warn { color: #dc2626; }
  }
}

@media (max-width: 600px) {
  .risk-wrap { grid-template-columns: 1fr; }
}
</style>

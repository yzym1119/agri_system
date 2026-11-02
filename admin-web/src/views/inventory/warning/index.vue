<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>库存预警</h1>
      </div>
    </section>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- 低库存预警 -->
      <el-tab-pane label="低库存预警" name="low-stock">
        <el-card shadow="never">
          <template #header>
            <div class="toolbar">
              <el-select
                v-model="storeId"
                placeholder="全部门店"
                clearable
                style="width: 200px"
                @change="loadLowStock"
              >
                <el-option
                  v-for="store in stores"
                  :key="store.id"
                  :label="store.name"
                  :value="store.id"
                />
              </el-select>
              <el-button type="primary" @click="loadLowStock" :loading="lowStockLoading">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <el-table
            :data="lowStockList"
            v-loading="lowStockLoading"
            stripe
            border
            style="width: 100%"
          >
            <el-table-column prop="productCode" label="商品编码" width="140" />
            <el-table-column prop="productName" label="商品名称" min-width="200" />
            <el-table-column prop="currentStock" label="当前库存" width="130" align="right">
              <template #default="{ row }">
                <span class="text-danger">{{ formatQuantity(row.currentStock) }}</span>
                <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="minStock" label="最低库存" width="130" align="right">
              <template #default="{ row }">
                {{ row.minStock }} <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="gap" label="缺口" width="130" align="right">
              <template #default="{ row }">
                <span class="text-danger">-{{ row.gap }}</span>
                <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="reorderQuantity" label="建议补货" width="130" align="right">
              <template #default="{ row }">
                {{ row.reorderQuantity ? row.reorderQuantity + ' ' + (row.mainUnit || '') : '-' }}
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!lowStockLoading && lowStockList.length === 0" description="暂无低库存预警" />
        </el-card>
      </el-tab-pane>

      <!-- 积压库存 -->
      <el-tab-pane label="积压库存" name="overstock">
        <el-card shadow="never">
          <template #header>
            <div class="toolbar">
              <el-select
                v-model="storeId"
                placeholder="全部门店"
                clearable
                style="width: 200px"
                @change="loadOverstock"
              >
                <el-option
                  v-for="store in stores"
                  :key="store.id"
                  :label="store.name"
                  :value="store.id"
                />
              </el-select>
              <el-button type="primary" @click="loadOverstock" :loading="overstockLoading">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <el-table
            :data="overstockList"
            v-loading="overstockLoading"
            stripe
            border
            style="width: 100%"
          >
            <el-table-column prop="productName" label="商品名称" min-width="240" />
            <el-table-column prop="currentStock" label="当前库存" width="130" align="right">
              <template #default="{ row }">
                <span class="text-warning">{{ formatQuantity(row.currentStock) }}</span>
                <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="maxStock" label="最高库存" width="130" align="right">
              <template #default="{ row }">
                {{ row.maxStock }} <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="excess" label="超出" width="130" align="right">
              <template #default="{ row }">
                <span class="text-warning">+{{ row.excess }}</span>
                <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!overstockLoading && overstockList.length === 0" description="暂无积压库存预警" />
        </el-card>
      </el-tab-pane>

      <!-- 智能推荐 -->
      <el-tab-pane label="智能推荐" name="smart">
        <el-card shadow="never">
          <template #header>
            <div class="toolbar">
              <div class="toolbar-left">
                <el-select
                  v-model="smartStoreId"
                  placeholder="全部门店"
                  clearable
                  style="width: 180px"
                  @change="loadSmartRecommendations"
                >
                  <el-option
                    v-for="store in stores"
                    :key="store.id"
                    :label="store.name"
                    :value="store.id"
                  />
                </el-select>
                <el-select
                  v-model="smartDays"
                  style="width: 130px; margin-left: 12px"
                  @change="loadSmartRecommendations"
                >
                  <el-option :value="30" label="近30天" />
                  <el-option :value="60" label="近60天" />
                  <el-option :value="90" label="近90天" />
                  <el-option :value="180" label="近180天" />
                </el-select>
              </div>
              <div class="toolbar-right">
                <el-button
                  type="success"
                  :disabled="selectedRows.length === 0 || !selectedRows.every(canApplyRecommendation)"
                  :loading="applyLoading"
                  @click="applySelected"
                >
                  采纳选中 ({{ selectedRows.length }})
                </el-button>
                <el-button
                  type="primary"
                  @click="loadSmartRecommendations"
                  :loading="smartLoading"
                >
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </div>
          </template>

          <el-table
            ref="smartTableRef"
            :data="smartList"
            v-loading="smartLoading"
            stripe
            border
            style="width: 100%"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="45" :selectable="canSelectRecommendation" />
            <el-table-column prop="productCode" label="编码" width="120" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="currentStock" label="可用库存" width="110" align="right">
              <template #default="{ row }">
                {{ formatQuantity(row.currentStock) }} <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="avgDailySales" label="日均销量" width="110" align="right">
              <template #default="{ row }">
                {{ formatQuantity(row.avgDailySales) }} <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最低库存" width="170" align="center">
              <template #header>
                <span>最低库存</span>
                <el-tooltip content="推荐值 = 日均销量 × 7天 × 1.5安全系数 × 季节系数（向上取整）" placement="top">
                  <el-icon style="margin-left: 4px; cursor: help; vertical-align: middle;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <template #default="{ row }">
                <div class="compare-cell">
                  <span class="current-val">{{ row.currentMinStock != null ? row.currentMinStock + (row.mainUnit ? row.mainUnit : '') : '-' }}</span>
                  <el-icon style="margin: 0 4px; color: #909399;"><Right /></el-icon>
                  <span class="recommend-val">{{ formatQuantity(row.recommendedMinStock) }}<span class="unit-text">{{ row.mainUnit }}</span></span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="最高库存" width="170" align="center">
              <template #header>
                <span>最高库存</span>
                <el-tooltip content="推荐值 = 日均销量 × 30天 × 季节系数（向上取整）" placement="top">
                  <el-icon style="margin-left: 4px; cursor: help; vertical-align: middle;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <template #default="{ row }">
                <div class="compare-cell">
                  <span class="current-val">{{ row.currentMaxStock != null ? row.currentMaxStock + (row.mainUnit ? row.mainUnit : '') : '-' }}</span>
                  <el-icon style="margin: 0 4px; color: #909399;"><Right /></el-icon>
                  <span class="recommend-val">{{ formatQuantity(row.recommendedMaxStock) }}<span class="unit-text">{{ row.mainUnit }}</span></span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="补货量" width="170" align="center">
              <template #header>
                <span>补货量</span>
                <el-tooltip content="推荐值 = 日均销量 × 14天补货周期 × 季节系数（向上取整）" placement="top">
                  <el-icon style="margin-left: 4px; cursor: help; vertical-align: middle;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <template #default="{ row }">
                <div class="compare-cell">
                  <span class="current-val">{{ row.currentReorderQuantity != null ? row.currentReorderQuantity + (row.mainUnit ? row.mainUnit : '') : '-' }}</span>
                  <el-icon style="margin: 0 4px; color: #909399;"><Right /></el-icon>
                  <span class="recommend-val">{{ formatQuantity(row.recommendedReorderQuantity) }}<span class="unit-text">{{ row.mainUnit }}</span></span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  :loading="row._applying"
                  :disabled="!canApplyRecommendation(row)"
                  @click="applyOne(row)"
                >
                  采纳
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!smartLoading && smartList.length === 0" description="暂无推荐数据（需要有历史销售记录）" />
        </el-card>
      </el-tab-pane>

      <!-- 风险建议（只提示，不自动执行） -->
      <el-tab-pane label="风险建议" name="risks">
        <el-card shadow="never">
          <template #header>
            <div class="toolbar">
              <div class="toolbar-left">
                <el-select
                  v-model="riskStoreId"
                  placeholder="请选择门店"
                  clearable
                  style="width: 200px"
                  @change="loadRisks"
                >
                  <el-option
                    v-for="store in stores"
                    :key="store.id"
                    :label="store.name"
                    :value="store.id"
                  />
                </el-select>
                <el-button
                  type="primary"
                  style="margin-left: 12px"
                  :disabled="!riskStoreId"
                  :loading="riskLoading"
                  @click="loadRisks"
                >
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
              <div class="toolbar-right">
                <el-tag type="info" effect="plain">建议仅供参考，不自动执行任何动作</el-tag>
              </div>
            </div>
          </template>

          <el-alert
            v-if="!riskStoreId"
            type="info"
            :closable="false"
            show-icon
            title="请先选择门店"
            description="风险建议按门店查询，选择门店后展示该门店的日期与库存风险提示。"
          />

          <template v-else>
            <el-table :data="riskList" v-loading="riskLoading" stripe border style="width: 100%">
              <el-table-column label="风险类型" width="130">
                <template #default="{ row }">
                  <el-tag :type="riskKindTag(row.kind)" size="small" effect="plain">
                    {{ riskKindText(row.kind) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="商品/批次" min-width="200">
                <template #default="{ row }">
                  <div>{{ row.productName || '-' }}</div>
                  <div class="risk-sub-text">
                    {{ row.productCode || '' }}
                    <span v-if="row.internalReceiptBatchNo">｜批次 {{ row.internalReceiptBatchNo }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="可用库存" width="110" align="right">
                <template #default="{ row }">
                  {{ formatQuantity(row.currentStock) }} <span class="unit-text">{{ row.unit }}</span>
                </template>
              </el-table-column>
              <el-table-column label="日期情况" width="180">
                <template #default="{ row }">
                  <div>{{ riskDateText(row) }}</div>
                  <div v-if="row.daysToExpiry != null" class="risk-sub-text">
                    距有效期 {{ row.daysToExpiry }} 天
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="观测窗口" width="110" align="right">
                <template #default="{ row }">
                  {{ row.observationWindowDays != null ? row.observationWindowDays + ' 天' : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="置信度" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.confidence === 'SUFFICIENT_DATA'" type="success" size="small">数据充分</el-tag>
                  <el-tag v-else-if="row.confidence === 'INSUFFICIENT_DATA'" type="info" size="small">数据不足</el-tag>
                  <span v-else>{{ row.confidence || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="message" label="建议说明" min-width="220">
                <template #default="{ row }">
                  {{ row.message || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button
                    type="primary"
                    link
                    size="small"
                    :disabled="!riskEntityId(row)"
                    @click="openDecisionDialog(row)"
                  >
                    记录处理决策
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-if="!riskLoading && riskList.length === 0" description="暂无风险建议" />
          </template>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 记录处理决策（仅追加留痕，不触发任何自动动作） -->
    <el-dialog v-model="decisionVisible" title="记录处理决策" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅记录人工决策留痕"
        description="保存后不会自动执行任何动作，请按实际情况线下处理。"
        class="decision-alert"
      />
      <el-descriptions v-if="decisionRisk" :column="1" border size="small" class="decision-summary">
        <el-descriptions-item label="风险类型">
          {{ riskKindText(decisionRisk.kind) }}
        </el-descriptions-item>
        <el-descriptions-item label="商品">
          {{ decisionRisk.productName || '-' }}（{{ decisionRisk.productCode || '-' }}）
        </el-descriptions-item>
        <el-descriptions-item label="建议说明">
          {{ decisionRisk.message || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="决策动作" required>
          <el-select v-model="decisionForm.decisionAction" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="option in decisionActions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="决策原因" required>
          <el-input
            v-model="decisionForm.decisionReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写处理原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decisionVisible = false">取消</el-button>
        <el-button type="primary" :loading="decisionSubmitting" @click="submitDecision">
          保存决策
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { formatQuantity } from '@/utils/number'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, QuestionFilled, Right } from '@element-plus/icons-vue'
import {
  getInventoryRisks,
  getLowStockWarnings,
  getOverstockWarnings,
  recordInventoryDecision,
  type LowStockWarningItem,
  type OverstockWarningItem
} from '@/api/inventory-warning'
import { getSmartThresholdRecommendations } from '@/api/inventory'
import { updateProduct } from '@/api/product'
import { getStoreList } from '@/api/store'
import type { StoreDetailVO } from '@/types/store'
import type { InventoryDecisionRequest, InventoryRiskVO } from '@/types/inventory'

interface SmartRecommendation {
  productId: number
  productName: string
  productCode: string
  mainUnit?: string
  currentStock: number | null
  avgDailySales: number | null
  totalSold: number | null
  orderCount: number
  recommendedMinStock: number | null
  recommendedMaxStock: number | null
  recommendedReorderQuantity: number | null
  currentMinStock: number | null
  currentMaxStock: number | null
  currentReorderQuantity: number | null
  status: 'LOW' | 'OVER' | 'NORMAL' | 'DATA_INSUFFICIENT'
  confidence: 'SUFFICIENT_DATA' | 'INSUFFICIENT_DATA'
  _applying?: boolean
}

const activeTab = ref<'low-stock' | 'overstock' | 'smart' | 'risks'>('low-stock')
const storeId = ref<number | undefined>(undefined)
const stores = ref<StoreDetailVO[]>([])

// 低库存
const lowStockList = ref<LowStockWarningItem[]>([])
const lowStockLoading = ref(false)

// 积压库存
const overstockList = ref<OverstockWarningItem[]>([])
const overstockLoading = ref(false)

// 智能推荐
const smartStoreId = ref<number | undefined>(undefined)
const smartDays = ref(90)
const smartList = ref<SmartRecommendation[]>([])
let smartGeneration = 0
let smartAlive = true
onBeforeUnmount(() => { smartAlive = false; smartGeneration++; smartList.value = []; selectedRows.value = [] })
const smartLoading = ref(false)
const applyLoading = ref(false)
const selectedRows = ref<SmartRecommendation[]>([])
const smartTableRef = ref()

// 风险建议（只提示，不自动执行）
const riskStoreId = ref<number | undefined>(undefined)
const riskList = ref<InventoryRiskVO[]>([])
const riskLoading = ref(false)

interface DecisionFormState {
  riskKind: string
  entityType: 'PRODUCT' | 'BATCH'
  entityId: number
  decisionAction: string
  decisionReason: string
}

const decisionVisible = ref(false)
const decisionRisk = ref<InventoryRiskVO | null>(null)
const decisionSubmitting = ref(false)
const decisionForm = ref<DecisionFormState>({
  riskKind: '',
  entityType: 'PRODUCT',
  entityId: 0,
  decisionAction: '',
  decisionReason: ''
})

const decisionActions: Array<{ label: string; value: string }> = [
  { label: '继续销售', value: 'CONTINUE_SALE' },
  { label: '检查处理', value: 'INSPECT' },
  { label: '补货', value: 'REPLENISH' },
  { label: '调拨', value: 'TRANSFER' },
  { label: '退货给供应商', value: 'RETURN_TO_VENDOR' },
  { label: '报损', value: 'WRITE_OFF' },
  { label: '暂停补货', value: 'PAUSE_REPLENISHMENT' },
  { label: '暂不处理', value: 'NO_ACTION' }
]

const RISK_KIND_TEXT: Record<string, string> = {
  DATE_UNKNOWN: '日期未知',
  EXPIRING: '临期',
  EXPIRED: '已过期',
  LOW_STOCK: '低库存',
  OVERSTOCK: '积压',
  LOW_MARGIN: '低毛利',
  DATA_INSUFFICIENT: '数据不足'
}

const riskKindText = (kind: string): string => RISK_KIND_TEXT[kind] || kind || '-'

const riskKindTag = (kind: string): 'danger' | 'warning' | 'info' | 'success' => {
  switch (kind) {
    case 'EXPIRED': return 'danger'
    case 'EXPIRING':
    case 'LOW_STOCK':
    case 'OVERSTOCK':
      return 'warning'
    case 'DATE_UNKNOWN':
    case 'DATA_INSUFFICIENT':
    default:
      return 'info'
  }
}

const riskDateText = (row: InventoryRiskVO): string => {
  const parts: string[] = []
  if (row.productionDate) parts.push(`生产 ${row.productionDate}`)
  if (row.expiryDate) parts.push(`到期 ${row.expiryDate}`)
  if (parts.length > 0) return parts.join(' / ')
  if (row.batchId != null || row.internalReceiptBatchNo) return '日期未知'
  return '-'
}

const riskEntityId = (row: InventoryRiskVO): number | null => {
  if (row.batchId != null) return row.batchId
  return row.productId ?? null
}

const loadRisks = async () => {
  if (!riskStoreId.value) {
    // 风险建议必须按门店口径查询，未选择门店时不请求、不展示汇总数据
    riskList.value = []
    return
  }
  riskLoading.value = true
  try {
    const data = await getInventoryRisks(riskStoreId.value)
    riskList.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载风险建议失败')
    riskList.value = []
  } finally {
    riskLoading.value = false
  }
}

const openDecisionDialog = (row: InventoryRiskVO) => {
  const entityId = riskEntityId(row)
  if (entityId == null) {
    ElMessage.warning('该风险缺少可留痕的实体信息')
    return
  }
  decisionRisk.value = row
  decisionForm.value = {
    riskKind: row.kind,
    entityType: row.batchId != null ? 'BATCH' : 'PRODUCT',
    entityId,
    decisionAction: '',
    decisionReason: ''
  }
  decisionVisible.value = true
}

const submitDecision = async () => {
  if (!decisionRisk.value) return
  const reason = (decisionForm.value.decisionReason || '').trim()
  if (!decisionForm.value.decisionAction) {
    ElMessage.warning('请选择决策动作')
    return
  }
  if (!reason) {
    ElMessage.warning('请填写决策原因')
    return
  }
  decisionSubmitting.value = true
  try {
    await recordInventoryDecision({
      riskKind: decisionForm.value.riskKind,
      entityType: decisionForm.value.entityType,
      entityId: decisionForm.value.entityId,
      decisionAction: decisionForm.value.decisionAction,
      decisionReason: reason
    })
    ElMessage.success('决策已记录（仅留痕，不自动执行任何动作）')
    decisionVisible.value = false
    decisionRisk.value = null
  } catch (e: any) {
    ElMessage.error(e?.message || '保存决策失败')
  } finally {
    decisionSubmitting.value = false
  }
}

const loadStores = async () => {
  try {
    const res = await getStoreList({ pageNum: 1, pageSize: 100 })
    stores.value = res.records || []
  } catch (e: any) {
    console.error('加载门店列表失败:', e)
  }
}

const loadLowStock = async () => {
  lowStockLoading.value = true
  try {
    const data = await getLowStockWarnings(storeId.value)
    lowStockList.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载低库存预警失败')
    lowStockList.value = []
  } finally {
    lowStockLoading.value = false
  }
}

const loadOverstock = async () => {
  overstockLoading.value = true
  try {
    const data = await getOverstockWarnings(storeId.value)
    overstockList.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载积压库存预警失败')
    overstockList.value = []
  } finally {
    overstockLoading.value = false
  }
}

const loadSmartRecommendations = async () => {
  const ticket = ++smartGeneration
  smartLoading.value = true
  smartList.value = []
  selectedRows.value = []
  const params = { storeId: smartStoreId.value, days: smartDays.value }
  try {
    const data = await getSmartThresholdRecommendations(params)
    if (!smartAlive || ticket !== smartGeneration) return
    smartList.value = (Array.isArray(data) ? data : []).map((item: any) => ({ ...item, _applying: false }))
  } catch (e: any) {
    if (!smartAlive || ticket !== smartGeneration) return
    ElMessage.error(e?.message || '加载智能推荐失败')
    smartList.value = []
  } finally {
    if (smartAlive && ticket === smartGeneration) smartLoading.value = false
  }
}

type AdoptableRecommendation = SmartRecommendation & { recommendedMinStock: number; recommendedMaxStock: number; recommendedReorderQuantity: number }
const canApplyRecommendation = (row: SmartRecommendation): row is AdoptableRecommendation => {
  const known = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0
  return row.confidence === 'SUFFICIENT_DATA' && ['LOW', 'OVER', 'NORMAL'].includes(row.status)
    && known(row.currentStock) && known(row.avgDailySales) && row.avgDailySales > 0
    && known(row.recommendedMinStock) && known(row.recommendedMaxStock) && known(row.recommendedReorderQuantity)
    && row.recommendedMinStock <= row.recommendedMaxStock
}

const currentSmartRow = (row: SmartRecommendation, ticket: number) =>
  smartAlive && ticket === smartGeneration && !smartLoading.value && smartList.value.includes(row)
const canSelectRecommendation = (row: SmartRecommendation) => !applyLoading.value && !row._applying && canApplyRecommendation(row)

const handleSelectionChange = (rows: SmartRecommendation[]) => {
  if (!applyLoading.value) selectedRows.value = rows.filter(canApplyRecommendation)
}

const statusLabel = (status: string) => {
  switch (status) {
    case 'LOW': return '偏低'
    case 'OVER': return '积压'
    case 'NORMAL': return '区间内'
    case 'DATA_INSUFFICIENT': return '数据不足'
    default: return '待核对'
  }
}

const statusTagType = (status: string) => {
  switch (status) {
    case 'LOW': return 'danger'
    case 'OVER': return 'warning'
    case 'NORMAL': return 'success'
    default: return 'info'
  }
}

const applyOne = async (row: SmartRecommendation) => {
  const ticket = smartGeneration
  if (applyLoading.value || row._applying || !currentSmartRow(row, ticket) || !canApplyRecommendation(row)) { ElMessage.warning('证据不足或推荐已变化，请重新查询核对。'); return }
  const target = row.productId, unit = row.mainUnit || ''
  const data = { minStock: row.recommendedMinStock, maxStock: row.recommendedMaxStock, reorderQuantity: row.recommendedReorderQuantity }
  const unchanged = () => currentSmartRow(row, ticket) && canApplyRecommendation(row) && row.productId === target && (row.mainUnit || '') === unit
    && row.recommendedMinStock === data.minStock && row.recommendedMaxStock === data.maxStock && row.recommendedReorderQuantity === data.reorderQuantity
  try {
    await ElMessageBox.confirm(
      `将「${row.productName}」的库存阈值更新为推荐值？\n\n最低库存: ${row.currentMinStock ?? '未设'} ${unit} → ${data.minStock} ${unit}\n最高库存: ${row.currentMaxStock ?? '未设'} ${unit} → ${data.maxStock} ${unit}\n补货量: ${row.currentReorderQuantity ?? '未设'} ${unit} → ${data.reorderQuantity} ${unit}`,
      '采纳推荐', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' })
  } catch { return }
  if (!unchanged()) { if (smartAlive) ElMessage.warning('推荐已变化，请重新查询核对。'); return }
  row._applying = true
  try {
    await updateProduct(target, data)
    if (!unchanged()) { if (smartAlive) ElMessage.warning('推荐列表已更新，原修改请到商品档案核对。'); return }
    row.currentMinStock = data.minStock; row.currentMaxStock = data.maxStock; row.currentReorderQuantity = data.reorderQuantity
    ElMessage.success(`${row.productName} 阈值已更新`)
  } catch (e: any) { if (smartAlive) ElMessage.error(e?.message || '阈值修改结果请到商品档案核对') }
  finally { row._applying = false }
}

const applySelected = async () => {
  if (applyLoading.value || selectedRows.value.length === 0) return
  const ticket = smartGeneration
  const rows = selectedRows.value.filter(canApplyRecommendation)
  if (rows.length !== selectedRows.value.length || rows.some(row => !currentSmartRow(row, ticket))) { ElMessage.warning('选中推荐已变化，请重新选择。'); return }
  const proposals = rows.map(row => ({ row, target: row.productId, unit: row.mainUnit,
    data: { minStock: row.recommendedMinStock, maxStock: row.recommendedMaxStock, reorderQuantity: row.recommendedReorderQuantity } }))
  const unchanged = (p: typeof proposals[number]) => currentSmartRow(p.row, ticket) && canApplyRecommendation(p.row) && p.row.productId === p.target && p.row.mainUnit === p.unit
    && p.row.recommendedMinStock === p.data.minStock && p.row.recommendedMaxStock === p.data.maxStock && p.row.recommendedReorderQuantity === p.data.reorderQuantity
  try { await ElMessageBox.confirm(`将选中的 ${proposals.length} 个商品的库存阈值更新为推荐值？`, '批量采纳', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }) }
  catch { return }
  if (!proposals.every(unchanged)) { if (smartAlive) ElMessage.warning('推荐已变化，请重新选择。'); return }
  applyLoading.value = true
  let successCount = 0, failCount = 0, interrupted = false
  for (const p of proposals) {
    if (!unchanged(p)) { interrupted = true; break }
    p.row._applying = true
    try {
      await updateProduct(p.target, p.data); successCount++
      if (!unchanged(p)) { interrupted = true; break }
      p.row.currentMinStock = p.data.minStock; p.row.currentMaxStock = p.data.maxStock; p.row.currentReorderQuantity = p.data.reorderQuantity
    } catch { failCount++ }
    finally { p.row._applying = false }
  }
  applyLoading.value = false
  if (!smartAlive) return
  if (interrupted || ticket !== smartGeneration) { ElMessage.warning('推荐列表已更新，已停止后续采纳；已发送项请到商品档案核对。'); return }
  if (failCount === 0) ElMessage.success(`全部 ${successCount} 个商品阈值已更新`)
  else ElMessage.warning(`已更新 ${successCount} 个，未完成 ${failCount} 个，请查询核对`)
  smartTableRef.value?.clearSelection()
}

watch(activeTab, (tab) => {
  if (tab !== 'smart') { smartGeneration++; smartList.value = []; selectedRows.value = [] }
  if (tab === 'low-stock') {
    loadLowStock()
  } else if (tab === 'overstock') {
    loadOverstock()
  } else if (tab === 'smart') {
    loadSmartRecommendations()
  } else if (tab === 'risks' && riskStoreId.value) {
    loadRisks()
  }
})

onMounted(() => {
  loadStores()
  loadLowStock()
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left {
  display: flex;
  align-items: center;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.text-danger {
  color: #f56c6c;
  font-weight: 600;
}

.text-warning {
  color: #e6a23c;
  font-weight: 600;
}

.compare-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}

.current-val {
  color: #909399;
}

.recommend-val {
  color: #409eff;
  font-weight: 600;
}

.unit-text {
  color: #909399;
  font-size: 12px;
  margin-left: 2px;
  font-weight: normal;
}

.risk-sub-text {
  color: #909399;
  font-size: 12px;
}

.decision-alert {
  margin-bottom: 12px;
}

.decision-summary {
  margin-bottom: 16px;
}
</style>

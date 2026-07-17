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
                <span class="text-danger">{{ row.currentStock }}</span>
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
                <span class="text-warning">{{ row.currentStock }}</span>
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
                  :disabled="selectedRows.length === 0"
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
            <el-table-column type="selection" width="45" />
            <el-table-column prop="productCode" label="编码" width="120" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="currentStock" label="当前库存" width="110" align="right">
              <template #default="{ row }">
                {{ row.currentStock }} <span class="unit-text">{{ row.mainUnit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="avgDailySales" label="日均销量" width="110" align="right">
              <template #default="{ row }">
                {{ row.avgDailySales }} <span class="unit-text">{{ row.mainUnit }}</span>
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
                  <span class="recommend-val">{{ row.recommendedMinStock }}<span class="unit-text">{{ row.mainUnit }}</span></span>
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
                  <span class="recommend-val">{{ row.recommendedMaxStock }}<span class="unit-text">{{ row.mainUnit }}</span></span>
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
                  <span class="recommend-val">{{ row.recommendedReorderQuantity }}<span class="unit-text">{{ row.mainUnit }}</span></span>
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
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, QuestionFilled, Right } from '@element-plus/icons-vue'
import { getLowStockWarnings, getOverstockWarnings, type LowStockWarningItem, type OverstockWarningItem } from '@/api/inventory-warning'
import { getSmartThresholdRecommendations } from '@/api/inventory'
import { updateProduct } from '@/api/product'
import { getStoreList } from '@/api/store'
import type { StoreDetailVO } from '@/types/store'

interface SmartRecommendation {
  productId: number
  productName: string
  productCode: string
  mainUnit?: string
  currentStock: number
  avgDailySales: number
  totalSold: number
  orderCount: number
  recommendedMinStock: number
  recommendedMaxStock: number
  recommendedReorderQuantity: number
  currentMinStock: number | null
  currentMaxStock: number | null
  currentReorderQuantity: number | null
  status: 'LOW' | 'OVER' | 'NORMAL'
  _applying?: boolean
}

const activeTab = ref<'low-stock' | 'overstock' | 'smart'>('low-stock')
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
const smartLoading = ref(false)
const applyLoading = ref(false)
const selectedRows = ref<SmartRecommendation[]>([])
const smartTableRef = ref()

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
  smartLoading.value = true
  try {
    const data = await getSmartThresholdRecommendations({
      storeId: smartStoreId.value,
      days: smartDays.value
    })
    smartList.value = (Array.isArray(data) ? data : []).map((item: any) => ({
      ...item,
      _applying: false
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载智能推荐失败')
    smartList.value = []
  } finally {
    smartLoading.value = false
  }
}

const handleSelectionChange = (rows: SmartRecommendation[]) => {
  selectedRows.value = rows
}

const statusLabel = (status: string) => {
  switch (status) {
    case 'LOW': return '偏低'
    case 'OVER': return '积压'
    default: return '正常'
  }
}

const statusTagType = (status: string) => {
  switch (status) {
    case 'LOW': return 'danger'
    case 'OVER': return 'warning'
    default: return 'success'
  }
}

const applyOne = async (row: SmartRecommendation) => {
  const u = row.mainUnit || ''
  try {
    await ElMessageBox.confirm(
      `将「${row.productName}」的库存阈值更新为推荐值？\n\n最低库存: ${row.currentMinStock ?? '未设'}${u ? ' ' + u : ''} → ${row.recommendedMinStock} ${u}\n最高库存: ${row.currentMaxStock ?? '未设'}${u ? ' ' + u : ''} → ${row.recommendedMaxStock} ${u}\n补货量: ${row.currentReorderQuantity ?? '未设'}${u ? ' ' + u : ''} → ${row.recommendedReorderQuantity} ${u}`,
      '采纳推荐',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return // cancelled
  }

  row._applying = true
  try {
    await updateProduct(row.productId, {
      minStock: row.recommendedMinStock,
      maxStock: row.recommendedMaxStock,
      reorderQuantity: row.recommendedReorderQuantity
    })
    ElMessage.success(`${row.productName} 阈值已更新`)
    // 更新本地状态
    row.currentMinStock = row.recommendedMinStock
    row.currentMaxStock = row.recommendedMaxStock
    row.currentReorderQuantity = row.recommendedReorderQuantity
    row.status = 'NORMAL'
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  } finally {
    row._applying = false
  }
}

const applySelected = async () => {
  if (selectedRows.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `将选中的 ${selectedRows.value.length} 个商品的库存阈值更新为推荐值？`,
      '批量采纳',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  applyLoading.value = true
  let successCount = 0
  let failCount = 0
  for (const row of selectedRows.value) {
    row._applying = true
    try {
      await updateProduct(row.productId, {
        minStock: row.recommendedMinStock,
        maxStock: row.recommendedMaxStock,
        reorderQuantity: row.recommendedReorderQuantity
      })
      row.currentMinStock = row.recommendedMinStock
      row.currentMaxStock = row.recommendedMaxStock
      row.currentReorderQuantity = row.recommendedReorderQuantity
      row.status = 'NORMAL'
      successCount++
    } catch {
      failCount++
    } finally {
      row._applying = false
    }
  }
  applyLoading.value = false

  if (failCount === 0) {
    ElMessage.success(`全部 ${successCount} 个商品阈值已更新`)
  } else {
    ElMessage.warning(`成功 ${successCount} 个，失败 ${failCount} 个`)
  }

  // 清除选中
  smartTableRef.value?.clearSelection()
}

watch(activeTab, (tab) => {
  if (tab === 'low-stock') {
    loadLowStock()
  } else if (tab === 'overstock') {
    loadOverstock()
  } else if (tab === 'smart') {
    loadSmartRecommendations()
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
</style>

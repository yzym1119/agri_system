<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="门店">
          <el-select v-model="queryParams.storeId" placeholder="请选择门店" clearable style="width: 200px">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="queryParams.productName" placeholder="商品名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="变动类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
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
            style="width: 400px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="storeName" label="门店" width="150" />
        <el-table-column label="商品信息" min-width="250">
          <template #default="{ row }">
            <div>
              <div style="font-weight: 500">{{ row.productName }}</div>
              <div style="font-size: 12px; color: #909399">
                <span v-if="row.productSpec">规格：{{ row.productSpec }}</span>
                <span v-if="row.manufacturer" style="margin-left: 8px">厂家：{{ row.manufacturer }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="typeName" label="变动类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)">{{ row.typeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动数量" width="150" align="right">
          <template #default="{ row }">
            <el-tooltip v-if="getDisplayQuantity(row).showTooltip" :content="getDisplayQuantity(row).tooltipText" placement="top">
              <span :style="{ color: row.quantity && row.quantity >= 0 ? '#67c23a' : '#f56c6c' }">
                {{ getDisplayQuantity(row).displayText }}
              </span>
            </el-tooltip>
            <span v-else :style="{ color: row.quantity && row.quantity >= 0 ? '#67c23a' : '#f56c6c' }">
              {{ getDisplayQuantity(row).displayText }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结存" width="150" align="right">
          <template #default="{ row }">
            <div>
              <div style="font-weight: 500">
                {{ formatNumber(row.afterQuantity) }} {{ row.mainUnit || '' }}
              </div>
              <div v-if="row.auxUnit && row.mainToAuxRatio && row.mainToAuxRatio > 0" style="font-size: 12px; color: #909399">
                (折合 {{ formatNumber((row.afterQuantity || 0) * row.mainToAuxRatio) }} {{ row.auxUnit }})
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="referenceNo" label="关联单号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.referenceNo" link type="primary" @click="copyReferenceNo(row.referenceNo)">
              {{ row.referenceNo }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdByName" label="操作人" width="120" />
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
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
  </div>
</template>

<script setup lang="ts">
import {reactive, ref} from 'vue'
import {Refresh, Search} from '@element-plus/icons-vue'
import {getInventoryTransactions} from '@/api/inventory'
import {getAllStores} from '@/api/store'
import type {InventoryTransactionQuery, InventoryTransactionVO} from '@/types/inventory'
import type {StoreDetailVO} from '@/types/store'
import {ElMessage} from 'element-plus'

const queryParams = reactive<InventoryTransactionQuery & { timeRange?: string[] }>({
  pageNum: 1,
  pageSize: 10
})

const loading = ref(false)
const tableData = ref<InventoryTransactionVO[]>([])
const total = ref(0)
const storeOptions = ref<StoreDetailVO[]>([])

const typeOptions = [
  { label: '采购入库', value: 1 },
  { label: '线下销售', value: 2 },
  { label: '线上销售', value: 3 },
  { label: '售后退回', value: 4 },
  { label: '调拨出库', value: 5 },
  { label: '调拨入库', value: 6 },
  { label: '盘点盘盈', value: 7 },
  { label: '盘点盘亏', value: 8 }
]

const formatNumber = (val?: number) => (val ?? 0).toFixed(2)

const getTypeTagType = (type?: number) => {
  if (!type) return ''
  if (type === 1 || type === 4 || type === 6 || type === 7) return 'success' // 入库类：绿色
  if (type === 2 || type === 3 || type === 5 || type === 8) return 'danger' // 出库类：红色
  return 'info'
}

/**
 * 智能显示变动数量（优先显示辅单位，如果是整数则显示主单位）
 */
const getDisplayQuantity = (row: InventoryTransactionVO) => {
  const qty = row.quantity || 0
  const ratio = row.mainToAuxRatio
  const mainUnit = row.mainUnit || ''
  const auxUnit = row.auxUnit || ''
  
  // 如果是整数，直接显示主单位
  if (Number.isInteger(qty)) {
    return {
      displayText: `${qty >= 0 ? '+' : ''}${qty} ${mainUnit}`,
      showTooltip: false,
      tooltipText: ''
    }
  }
  
  // 如果是小数且有辅单位换算率，尝试转换为辅单位
  if (ratio && ratio > 0 && auxUnit) {
    const auxQty = qty * ratio
    // 如果转换后接近整数（误差小于0.01），显示辅单位
    const diff = Math.abs(auxQty - Math.round(auxQty))
    if (diff < 0.01) {
      const roundedAuxQty = Math.round(auxQty)
      return {
        displayText: `${roundedAuxQty >= 0 ? '+' : ''}${roundedAuxQty} ${auxUnit}`,
        showTooltip: true,
        tooltipText: `原始值: ${formatNumber(qty)} ${mainUnit}`
      }
    }
  }
  
  // 否则显示主单位（带小数）
  return {
    displayText: `${qty >= 0 ? '+' : ''}${formatNumber(qty)} ${mainUnit}`,
    showTooltip: false,
    tooltipText: ''
  }
}

const copyReferenceNo = (text: string) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

const loadOptions = async () => {
  try {
    storeOptions.value = await getAllStores()
  } catch (error) {
    console.error('加载门店选项失败:', error)
    storeOptions.value = []
    ElMessage.error('加载门店选项失败')
  }
}

const loadTable = async () => {
  loading.value = true
  try {
    const params: InventoryTransactionQuery = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      storeId: queryParams.storeId,
      productName: queryParams.productName,
      type: queryParams.type,
      startTime: queryParams.timeRange?.[0],
      endTime: queryParams.timeRange?.[1]
    }
    const res = await getInventoryTransactions(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载库存流水失败:', error)
    tableData.value = []
    total.value = 0
    ElMessage.error('加载库存流水失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  loadTable()
}

const handleReset = () => {
  queryParams.storeId = undefined
  queryParams.productName = ''
  queryParams.type = undefined
  queryParams.timeRange = undefined
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  loadTable()
}

const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  loadTable()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadTable()
}

loadOptions().finally(loadTable)
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}

.search-card {
  margin-bottom: 12px;
}

.table-card {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>

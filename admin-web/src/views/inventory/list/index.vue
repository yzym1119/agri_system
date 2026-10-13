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
        <el-form-item label="仅显示有库存">
          <el-switch v-model="queryParams.onlyHasStock" />
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
        <el-table-column label="商品信息" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            <div>
              <div style="font-weight: 500">{{ row.productName }}</div>
              <div style="font-size: 12px; color: #909399">
                <span v-if="row.specification">规格：{{ row.specification }}</span>
                <span v-if="row.manufacturer" style="margin-left: 8px">厂家：{{ row.manufacturer }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="storeName" label="门店" width="150" />
        <el-table-column prop="totalQuantity" label="总库存" width="150" align="right">
          <template #default="{ row }">
            <div>
              <div style="font-weight: 600">
                {{ formatNumber(row.totalQuantity) }} {{ row.mainUnit || row.unit || '' }}
              </div>
              <div v-if="row.auxUnit && row.mainToAuxRatio" style="font-size: 12px; color: #909399">
                (折合 {{ formatNumber((row.totalQuantity || 0) * row.mainToAuxRatio) }} {{ row.auxUnit }})
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewBatches(row)">查看批次</el-button>
            <el-button v-permission="'inventory:transfer'" link type="success" @click="handleTransfer(row)">调拨</el-button>
            <el-button v-permission="'inventory:adjust'" link type="warning" @click="handleAdjust(row)">盘点</el-button>
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

    <!-- 批次抽屉 -->
    <el-drawer v-model="batchVisible" title="批次详情" size="60%">
      <el-descriptions v-if="batchDetail" :column="2" border>
        <el-descriptions-item label="门店">{{ batchDetail.storeName || batchDetail.storeId }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ batchDetail.productName || batchDetail.productId }}</el-descriptions-item>
        <el-descriptions-item label="总库存">{{ formatNumber(batchDetail.quantity) }} {{ batchDetail.unit || '' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="batchDetail?.batches || []" border size="small" style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="batchNo" label="批次号" width="180" />
        <el-table-column prop="quantity" label="剩余数量" width="140" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="costPrice" label="成本价" width="120" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.costPrice) }}</template>
        </el-table-column>
        <el-table-column prop="productionDate" label="生产日期" width="120" />
        <el-table-column prop="expireDate" label="到期日" width="120" />
        <el-table-column prop="isActive" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive === 1 ? 'success' : 'info'">{{ row.isActive === 1 ? '有效' : '归档' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 调拨对话框 -->
    <el-dialog v-model="transferVisible" title="库存调拨" width="500px" :close-on-click-modal="false">
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="100px">
        <el-form-item label="调出门店">
          <el-select v-model="transferForm.fromStoreId" placeholder="请选择" disabled style="width: 100%">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调入门店" prop="toStoreId">
          <el-select v-model="transferForm.toStoreId" placeholder="请选择" style="width: 100%">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品">
          <el-input v-model="transferForm.productName" disabled />
        </el-form-item>
        <el-form-item label="调拨数量" prop="quantity">
          <el-input-number v-model="transferForm.quantity" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="transferForm.unit" style="width: 100%">
            <el-option v-if="transferForm.productMainUnit" :label="transferForm.productMainUnit" :value="transferForm.productMainUnit" />
            <el-option v-if="transferForm.productAuxUnit" :label="transferForm.productAuxUnit" :value="transferForm.productAuxUnit" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button v-permission="'inventory:transfer'" type="primary" :loading="transferLoading" @click="handleTransferSubmit">提交调拨</el-button>
      </template>
    </el-dialog>

    <!-- 盘点对话框 -->
    <el-dialog v-model="adjustVisible" title="库存盘点" width="500px" :close-on-click-modal="false">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="100px">
        <el-form-item label="门店">
          <el-input v-model="adjustForm.storeName" disabled />
        </el-form-item>
        <el-form-item label="商品">
          <el-input v-model="adjustForm.productName" disabled />
        </el-form-item>
        <el-form-item label="系统库存">
          <el-input v-model="adjustForm.systemQuantity" disabled />
        </el-form-item>
        <el-form-item label="实际库存" prop="actualQuantity">
          <el-input-number v-model="adjustForm.actualQuantity" :min="0" :precision="2" style="width: 100%" @change="calculateAdjustDiff" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="adjustForm.unit" style="width: 100%">
            <el-option v-if="adjustForm.productMainUnit" :label="adjustForm.productMainUnit" :value="adjustForm.productMainUnit" />
            <el-option v-if="adjustForm.productAuxUnit" :label="adjustForm.productAuxUnit" :value="adjustForm.productAuxUnit" />
          </el-select>
        </el-form-item>
        <el-form-item label="盈亏数量">
          <el-input v-model="adjustForm.quantityDiff" disabled>
            <template #prepend>{{ adjustForm.quantityDiff >= 0 ? '盘盈' : '盘亏' }}</template>
          </el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button v-permission="'inventory:adjust'" type="primary" :loading="adjustLoading" @click="handleAdjustSubmit">提交盘点</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {reactive, ref} from 'vue'
import {Refresh, Search} from '@element-plus/icons-vue'
import {adjustInventory, getInventoryDetail, getInventoryList, transferInventory} from '@/api/inventory'
import {getAllStores} from '@/api/store'
import type {
  InventoryAdjustRequest,
  InventoryDetailVO,
  InventoryPageVO,
  InventoryQuery,
  InventoryTransferRequest
} from '@/types/inventory'
import type {StoreDetailVO} from '@/types/store'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'

const queryParams = reactive<InventoryQuery>({
  pageNum: 1,
  pageSize: 10,
  onlyHasStock: true
})
const loading = ref(false)
const tableData = ref<InventoryPageVO[]>([])
const total = ref(0)
const storeOptions = ref<StoreDetailVO[]>([])

const batchVisible = ref(false)
const batchDetail = ref<InventoryDetailVO>()

// 调拨
const transferVisible = ref(false)
const transferFormRef = ref<FormInstance>()
const transferForm = reactive<InventoryTransferRequest & {
  productName?: string
  productMainUnit?: string
  productAuxUnit?: string
  productMainToAuxRatio?: number
  unit?: string
}>({
  requestKey: crypto.randomUUID(),
  fromStoreId: undefined as unknown as number,
  toStoreId: undefined as unknown as number,
  productId: undefined as unknown as number,
  quantity: 1,
  unit: undefined
})
const transferRules: FormRules = {
  toStoreId: [{ required: true, message: '请选择调入门店', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入调拨数量', trigger: 'blur' }]
}
const transferLoading = ref(false)

// 盘点
const adjustVisible = ref(false)
const adjustFormRef = ref<FormInstance>()
const adjustForm = reactive<{
  storeId?: number
  storeName?: string
  productId?: number
  productName?: string
  systemQuantity?: number
  actualQuantity?: number
  quantityDiff: number
  requestKey: string
  type?: number
  remark?: string
  productMainUnit?: string
  productAuxUnit?: string
  productMainToAuxRatio?: number
  unit?: string
}>({
  quantityDiff: 0,
  requestKey: crypto.randomUUID()
})
const adjustRules: FormRules = {
  actualQuantity: [{ required: true, message: '请输入实际库存', trigger: 'blur' }]
}
const adjustLoading = ref(false)

const formatMoney = (val?: number) => (val || 0).toFixed(2)
const formatNumber = (val?: number) => (val ?? 0).toFixed(2)

const loadOptions = async () => {
  storeOptions.value = await getAllStores()
}

const loadTable = async () => {
  loading.value = true
  try {
    const res = await getInventoryList(queryParams)
    tableData.value = res.records || []
    total.value = res.total || 0
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
  queryParams.onlyHasStock = true
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

const handleViewBatches = async (row: InventoryPageVO) => {
  if (!row.storeId || !row.productId) return
  const detail = await getInventoryDetail(row.storeId, row.productId)
  batchDetail.value = detail
  batchVisible.value = true
}

const handleTransfer = (row: InventoryPageVO) => {
  if (!row.storeId || !row.productId) return
  transferForm.fromStoreId = row.storeId as unknown as number
  transferForm.productId = row.productId as unknown as number
  transferForm.productName = row.productName
  transferForm.productMainUnit = row.mainUnit as any
  transferForm.productAuxUnit = row.auxUnit as any
  transferForm.productMainToAuxRatio = row.mainToAuxRatio as any
  transferForm.unit = row.mainUnit as any
  transferForm.toStoreId = undefined as unknown as number
  transferForm.requestKey = crypto.randomUUID()
  transferForm.quantity = 1
  transferVisible.value = true
}

const handleTransferSubmit = () => {
  transferFormRef.value?.validate(async valid => {
    if (!valid) return
    if (transferForm.fromStoreId === transferForm.toStoreId) {
      ElMessage.warning('调出门店和调入门店不能相同')
      return
    }
    transferLoading.value = true
    try {
      const payload: InventoryTransferRequest = { ...transferForm }
      await transferInventory(payload)
      ElMessage.success('调拨成功')
      transferVisible.value = false
      loadTable()
    } finally {
      transferLoading.value = false
    }
  })
}

const handleAdjust = (row: InventoryPageVO) => {
  if (!row.storeId || !row.productId) return
  adjustForm.storeId = row.storeId
  adjustForm.productId = row.productId
  adjustForm.productName = row.productName
  adjustForm.storeName = row.storeName
  adjustForm.systemQuantity = row.totalQuantity
  adjustForm.actualQuantity = row.totalQuantity
  adjustForm.quantityDiff = 0
  adjustForm.requestKey = crypto.randomUUID()
  adjustForm.remark = ''
  adjustForm.productMainUnit = row.mainUnit as any
  adjustForm.productAuxUnit = row.auxUnit as any
  adjustForm.productMainToAuxRatio = row.mainToAuxRatio as any
  adjustForm.unit = row.mainUnit as any
  adjustVisible.value = true
}

const calculateAdjustDiff = () => {
  if (adjustForm.systemQuantity === undefined || adjustForm.actualQuantity === undefined) return
  let actual = adjustForm.actualQuantity
  const ratio = adjustForm.productMainToAuxRatio
  if (adjustForm.unit === adjustForm.productAuxUnit && ratio && ratio > 0) {
    actual = Number((actual / ratio).toFixed(8))
  }
  adjustForm.quantityDiff = Number((actual - (adjustForm.systemQuantity || 0)).toFixed(8))
  adjustForm.type = adjustForm.quantityDiff >= 0 ? 1 : 2 // 1-盘盈，2-盘亏
}

const handleAdjustSubmit = () => {
  adjustFormRef.value?.validate(async valid => {
    if (!valid) return
    if (adjustForm.quantityDiff === 0) {
      ElMessage.warning('实际库存与系统库存相同，无需调整')
      return
    }
    adjustLoading.value = true
    try {
      const payload: InventoryAdjustRequest = {
        storeId: adjustForm.storeId!,
        productId: adjustForm.productId!,
        quantityDiff: adjustForm.quantityDiff!,
        requestKey: adjustForm.requestKey,
        expectedQuantity: adjustForm.systemQuantity!,
        actualQuantity: adjustForm.actualQuantity!,
        unit: adjustForm.unit,
        type: adjustForm.type!,
        remark: adjustForm.remark?.trim() || '门店实物盘点'
      }
      await adjustInventory(payload)
      ElMessage.success('盘点成功')
      adjustVisible.value = false
      loadTable()
    } finally {
      adjustLoading.value = false
    }
  })
}

loadOptions().then(loadTable)
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

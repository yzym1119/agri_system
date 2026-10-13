<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>库存调拨</h1>
      </div>
    </section>

    <div class="transfer-grid">
      <el-card shadow="never">
        <template #header>新建调拨</template>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-form-item label="调出门店" prop="fromStoreId">
            <el-select v-model="form.fromStoreId" placeholder="请选择" clearable filterable style="width: 100%">
              <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="调入门店" prop="toStoreId">
            <el-select v-model="form.toStoreId" placeholder="请选择" clearable filterable style="width: 100%">
              <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品" prop="productId">
            <div style="display: flex; gap: 8px; width: 100%">
              <el-input v-model="productDisplay" placeholder="请选择商品" disabled />
              <el-button type="primary" @click="productSelectorVisible = true">选择</el-button>
            </div>
          </el-form-item>
          <el-form-item label="调拨数量" prop="quantity">
            <el-input-number v-model="form.quantity" :min="0.01" :precision="2" style="width: 220px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">提交调拨</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>最近一次调拨结果</template>

        <div v-if="lastTransferResult" class="result-summary">
          <div class="summary-item">
            <span>调拨单号</span>
            <strong>{{ lastTransferResult.transferNo || '-' }}</strong>
          </div>
          <div class="summary-item">
            <span>调出门店</span>
            <strong>{{ getStoreName(lastTransferResult.fromStoreId) }}</strong>
          </div>
          <div class="summary-item">
            <span>调入门店</span>
            <strong>{{ getStoreName(lastTransferResult.toStoreId) }}</strong>
          </div>
          <div class="summary-item">
            <span>商品</span>
            <strong>{{ selectedProduct?.name || `商品#${lastTransferResult.productId}` }}</strong>
          </div>
          <div class="summary-item">
            <span>调拨数量</span>
            <strong>{{ formatNumber(lastTransferResult.quantity) }}</strong>
          </div>
        </div>

        <div v-if="lastTransferResult" style="display: flex; flex-direction: column; gap: 16px; margin-top: 16px">
          <div>
            <div style="font-weight: 600; margin-bottom: 8px">调出拆批</div>
            <el-table :data="lastTransferResult.batchDetails || []" border size="small">
              <el-table-column prop="batchNo" label="原批次" min-width="120" show-overflow-tooltip />
              <el-table-column label="数量" width="100" align="right">
                <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
              </el-table-column>
              <el-table-column label="成本价" width="110" align="right">
                <template #default="{ row }">¥{{ formatMoney(row.costPrice) }}</template>
              </el-table-column>
              <el-table-column prop="expireDate" label="到期日" width="120" />
            </el-table>
          </div>

          <div>
            <div style="font-weight: 600; margin-bottom: 8px">调入批次映射</div>
            <el-table :data="lastTransferResult.transferBatchDetails || []" border size="small">
              <el-table-column prop="fromBatchId" label="原批次ID" width="120" />
              <el-table-column prop="toBatchId" label="新批次ID" width="120" />
              <el-table-column label="数量" width="100" align="right">
                <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
              </el-table-column>
              <el-table-column label="成本价" width="110" align="right">
                <template #default="{ row }">¥{{ formatMoney(row.costPrice) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <el-empty v-else description="提交调拨后显示拆批结果" />
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>调拨记录</span>
          <el-button @click="loadHistory" :loading="historyLoading">刷新</el-button>
        </div>
      </template>

      <div class="toolbar">
        <el-select v-model="historyQuery.fromStoreId" clearable filterable placeholder="调出门店" style="width: 180px">
          <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
        <el-select v-model="historyQuery.toStoreId" clearable filterable placeholder="调入门店" style="width: 180px">
          <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
        <el-input v-model="historyQuery.productName" clearable placeholder="商品名称" style="width: 200px" />
        <el-date-picker
          v-model="historyQuery.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 360px"
        />
        <el-button type="primary" @click="handleSearchHistory" :loading="historyLoading">查询</el-button>
        <el-button @click="handleResetHistory">重置</el-button>
      </div>

      <el-table :data="historyRecords" v-loading="historyLoading" border>
        <el-table-column prop="transferNo" label="调拨单号" width="180" show-overflow-tooltip />
        <el-table-column label="门店流向" min-width="220">
          <template #default="{ row }">
            {{ row.fromStoreName || '-' }} → {{ row.toStoreName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.productName || `商品#${row.productId}` }}</div>
            <div class="cell-sub">{{ row.productSpec || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="拆批数" width="90" align="right">
          <template #default="{ row }">{{ row.batchCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">{{ row.createdByName || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="historyQuery.pageNum"
          v-model:page-size="historyQuery.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :total="historyTotal"
          @current-change="loadHistory"
          @size-change="handleHistorySizeChange"
        />
      </div>
    </el-card>

    <ProductSelector
      v-model="productSelectorVisible"
      :selected-ids="form.productId ? [form.productId] : []"
      @confirm="handleSelectProduct"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ProductSelector from '@/components/ProductSelector/index.vue'
import { getInventoryTransferRecords, transferInventory } from '@/api/inventory'
import { getAllStores } from '@/api/store'
import type {
  InventoryTransferRecordQuery,
  InventoryTransferRecordVO,
  InventoryTransferRequest,
  InventoryTransferResult
} from '@/types/inventory'
import type { ProductDetailVO } from '@/types/product'
import type { StoreDetailVO } from '@/types/store'

const formRef = ref<FormInstance>()
const submitting = ref(false)
const storeOptions = ref<StoreDetailVO[]>([])
const selectedProduct = ref<ProductDetailVO | null>(null)
const productSelectorVisible = ref(false)
const lastTransferResult = ref<InventoryTransferResult | null>(null)

const historyLoading = ref(false)
const historyRecords = ref<InventoryTransferRecordVO[]>([])
const historyTotal = ref(0)

const form = reactive<InventoryTransferRequest>({
  requestKey: crypto.randomUUID(),
  fromStoreId: undefined as unknown as number,
  toStoreId: undefined as unknown as number,
  productId: undefined as unknown as number,
  quantity: 1,
  remark: ''
})

const historyQuery = reactive<InventoryTransferRecordQuery & { timeRange?: string[] }>({
  fromStoreId: undefined,
  toStoreId: undefined,
  productName: '',
  pageNum: 1,
  pageSize: 10,
  timeRange: undefined
})

const rules: FormRules = {
  fromStoreId: [{ required: true, message: '请选择调出门店', trigger: 'change' }],
  toStoreId: [
    { required: true, message: '请选择调入门店', trigger: 'change' },
    {
      validator: (_, value, callback) => {
        if (value && value === form.fromStoreId) {
          callback(new Error('调出门店和调入门店不能相同'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
  quantity: [
    { required: true, message: '请输入调拨数量', trigger: 'blur' },
    {
      validator: (_, value, callback) => {
        if (!value || value <= 0) {
          callback(new Error('数量必须大于0'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const productDisplay = computed(() => {
  if (!selectedProduct.value) return ''
  return `${selectedProduct.value.name} (${selectedProduct.value.code})`
})

const formatNumber = (value?: number | null) => Number(value || 0).toFixed(2)
const formatMoney = (value?: number | null) => Number(value || 0).toFixed(2)

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

const getStoreName = (storeId?: number) => {
  if (!storeId) return '-'
  return storeOptions.value.find(item => item.id === storeId)?.name || `门店#${storeId}`
}

const loadStores = async () => {
  storeOptions.value = await getAllStores()
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const page = await getInventoryTransferRecords({
      fromStoreId: historyQuery.fromStoreId,
      toStoreId: historyQuery.toStoreId,
      productName: historyQuery.productName?.trim() || undefined,
      startTime: historyQuery.timeRange?.[0],
      endTime: historyQuery.timeRange?.[1],
      pageNum: historyQuery.pageNum,
      pageSize: historyQuery.pageSize
    })
    historyRecords.value = page.records || []
    historyTotal.value = page.total || 0
  } finally {
    historyLoading.value = false
  }
}

const handleSearchHistory = () => {
  historyQuery.pageNum = 1
  loadHistory()
}

const handleResetHistory = () => {
  historyQuery.fromStoreId = undefined
  historyQuery.toStoreId = undefined
  historyQuery.productName = ''
  historyQuery.timeRange = undefined
  historyQuery.pageNum = 1
  historyQuery.pageSize = 10
  loadHistory()
}

const handleHistorySizeChange = () => {
  historyQuery.pageNum = 1
  loadHistory()
}

const handleSelectProduct = (products: ProductDetailVO[]) => {
  const product = products[0]
  if (!product) return
  selectedProduct.value = product
  form.productId = product.id
}

const handleSubmit = () => {
  formRef.value?.validate(async valid => {
    if (!valid) return
    submitting.value = true
    try {
      lastTransferResult.value = await transferInventory({ ...form, unit: selectedProduct.value?.mainUnit })
      ElMessage.success('调拨成功')
      form.requestKey = crypto.randomUUID()
      loadHistory()
      form.remark = ''
    } finally {
      submitting.value = false
    }
  })
}

const handleReset = () => {
  form.requestKey = crypto.randomUUID()
  form.fromStoreId = undefined as unknown as number
  form.toStoreId = undefined as unknown as number
  form.productId = undefined as unknown as number
  form.quantity = 1
  form.remark = ''
  selectedProduct.value = null
}

onMounted(async () => {
  await loadStores()
  loadHistory()
})
</script>

<style scoped lang="scss">
.transfer-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.4fr);
  gap: 16px;
  margin-bottom: 16px;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;

  .summary-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px;
    border-radius: 8px;
    background: #f8f9fa;

    span { font-size: 12px; color: #909399; }
    strong { font-size: 15px; color: #303133; }
  }
}

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

@media (max-width: 960px) {
  .transfer-grid {
    grid-template-columns: 1fr;
  }
  .result-summary {
    grid-template-columns: 1fr;
  }
}
</style>

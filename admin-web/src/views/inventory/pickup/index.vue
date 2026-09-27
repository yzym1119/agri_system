<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>取货</h1>
        <p class="page-desc">
          库房取货待办：扫描内部批次标签或货位编码完成取货；商品编码不能用于取货确认。
        </p>
      </div>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-select
            v-model="storeId"
            placeholder="请选择门店"
            clearable
            style="width: 220px"
            @change="handleStoreChange"
          >
            <el-option
              v-for="store in stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
          <el-select
            v-model="statusFilter"
            placeholder="状态"
            style="width: 150px"
            @change="loadWorkItems"
          >
            <el-option label="待取货" value="PENDING" />
            <el-option label="已取货" value="FULFILLED" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="loadWorkItems">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button :disabled="!storeId" @click="openCreateDialog">
            登记取货请求
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="!storeId"
        type="info"
        :closable="false"
        show-icon
        title="请先选择门店"
        description="取货待办按门店隔离，选择门店后展示该门店的取货工作项。"
      />

      <template v-else>
        <el-table :data="workItems" v-loading="loading" stripe border style="width: 100%">
          <el-table-column prop="orderNo" label="订单号" width="180">
            <template #default="{ row }">
              {{ row.orderNo || (row.orderId ? '订单#' + row.orderId : '-') }}
            </template>
          </el-table-column>
          <el-table-column prop="productCode" label="商品编码" width="140" />
          <el-table-column prop="productName" label="商品名称" min-width="180" />
          <el-table-column prop="requestedBaseQuantity" label="请求数量(基准)" width="150" align="right" />
          <el-table-column prop="sourceLocationName" label="来源货位" min-width="140">
            <template #default="{ row }">
              {{ row.sourceLocationName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'" effect="plain">
                {{ row.status === 'PENDING' ? '待取货' : '已取货' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING'"
                type="primary"
                link
                @click="openFulfillDialog(row)"
              >
                确认取货
              </el-button>
              <span v-else class="muted-text">已完成</span>
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!loading && workItems.length === 0"
          description="暂无取货工作项"
        />
      </template>
    </el-card>

    <!-- 确认取货：扫描或显式确认批次/货位 -->
    <el-dialog v-model="fulfillVisible" title="确认取货" width="560px" @closed="resetFulfill">
      <el-descriptions v-if="currentItem" :column="1" border size="small" class="pickup-summary">
        <el-descriptions-item label="订单号">
          {{ currentItem.orderNo || (currentItem.orderId ? '订单#' + currentItem.orderId : '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="商品">
          {{ currentItem.productName }}（{{ currentItem.productCode }}）
        </el-descriptions-item>
        <el-descriptions-item label="请求数量(基准)">
          {{ currentItem.requestedBaseQuantity }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="90px" class="pickup-form">
        <el-form-item label="扫码确认">
          <el-input
            v-model="scanCode"
            placeholder="扫描内部批次标签或货位编码后回车"
            clearable
            @keyup.enter="handleScan"
          >
            <template #append>
              <el-button @click="handleScan">解析</el-button>
            </template>
          </el-input>
          <div v-if="scanResult" class="scan-result">
            <el-tag :type="scanResult.tagType" effect="plain" size="small">
              {{ scanResult.label }}
            </el-tag>
            <span class="scan-result-text">{{ scanResult.text }}</span>
          </div>
          <div v-else class="scan-hint">
            内部批次标签以 {{ BATCH_LABEL_PREFIX }} 开头；货位编码见「货位」页面。
          </div>
        </el-form-item>

        <el-form-item label="显式确认">
          <div class="explicit-confirm">
            <el-input-number
              v-model="explicitBatchId"
              :min="1"
              :controls="false"
              placeholder="批次ID"
              style="width: 160px"
            />
            <el-select
              v-model="explicitLocationId"
              placeholder="货位"
              clearable
              style="width: 220px"
            >
              <el-option
                v-for="loc in locations"
                :key="loc.id"
                :label="`${loc.code} ${loc.name || ''}`"
                :value="loc.id"
              />
            </el-select>
          </div>
          <div class="scan-hint">
            无法扫码时，可手动填写批次ID并选择货位，二者需同时提供。
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="fulfillVisible = false">取消</el-button>
        <el-button type="primary" :loading="fulfilling" :disabled="!canSubmit" @click="submitFulfill">
          确认取货
        </el-button>
      </template>
    </el-dialog>

    <!-- 登记取货请求 -->
    <el-dialog v-model="createVisible" title="登记取货请求" width="480px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅在收银端未自动生成取货请求时手动补登记"
        description="正常库房取货由收银开单时自动生成，请勿重复登记。"
        class="create-alert"
      />
      <el-form label-width="90px">
        <el-form-item label="订单ID">
          <el-input-number v-model="createForm.orderId" :min="1" :controls="false" style="width: 200px" />
        </el-form-item>
        <el-form-item label="明细ID">
          <el-input-number v-model="createForm.orderItemId" :min="1" :controls="false" style="width: 200px" />
        </el-form-item>
        <el-form-item label="来源货位">
          <el-select v-model="createForm.sourceLocationId" placeholder="可选" clearable style="width: 260px">
            <el-option
              v-for="loc in locations"
              :key="loc.id"
              :label="`${loc.code} ${loc.name || ''}`"
              :value="loc.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="!canCreate" @click="submitCreate">
          登记
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getStoreList } from '@/api/store'
import { getInventoryLocations } from '@/api/inventory'
import { createPickupRequest, fulfillPickupRequest, getPickupWorkItems } from '@/api/order'
import type { InventoryLocationVO } from '@/types/inventory'
import type { PickupWorkItemVO } from '@/types/order'

/** 内部批次标签前缀（与后端 INTERNAL-RECEIPT-<收货单号>-<批次号> 约定一致） */
const BATCH_LABEL_PREFIX = 'INTERNAL-RECEIPT-'

const stores = ref<any[]>([])
const storeId = ref<number | undefined>(undefined)
const statusFilter = ref('PENDING')
const workItems = ref<PickupWorkItemVO[]>([])
const loading = ref(false)
const locations = ref<InventoryLocationVO[]>([])

// 确认取货
const fulfillVisible = ref(false)
const fulfilling = ref(false)
const currentItem = ref<PickupWorkItemVO | null>(null)
const scanCode = ref('')
const scanResult = ref<{ type: 'BATCH' | 'LOCATION' | 'SKU'; label: string; text: string; tagType: 'success' | 'warning' | 'danger' } | null>(null)
const explicitBatchId = ref<number | undefined>(undefined)
const explicitLocationId = ref<number | undefined>(undefined)

// 登记取货请求
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref<{ orderId?: number; orderItemId?: number; sourceLocationId?: number }>({})

const canSubmit = computed(() => {
  if (scanResult.value && (scanResult.value.type === 'BATCH' || scanResult.value.type === 'LOCATION')) {
    return true
  }
  return !!explicitBatchId.value && !!explicitLocationId.value
})

const canCreate = computed(() => !!createForm.value.orderId && !!createForm.value.orderItemId)

async function loadStores() {
  try {
    const res: any = await getStoreList({ pageNum: 1, pageSize: 100 })
    stores.value = res?.list || res?.records || res?.data?.list || res?.data?.records || []
  } catch (e) {
    console.warn('加载门店列表失败', e)
  }
}

async function loadLocations() {
  if (!storeId.value) {
    locations.value = []
    return
  }
  try {
    locations.value = await getInventoryLocations(storeId.value)
  } catch (e) {
    console.warn('加载货位列表失败', e)
    locations.value = []
  }
}

async function loadWorkItems() {
  if (!storeId.value) {
    workItems.value = []
    return
  }
  loading.value = true
  try {
    workItems.value = await getPickupWorkItems(storeId.value, statusFilter.value || undefined)
  } catch (e) {
    console.warn('加载取货待办失败', e)
    workItems.value = []
  } finally {
    loading.value = false
  }
}

function handleStoreChange() {
  loadLocations()
  loadWorkItems()
}

/**
 * 解析扫码结果：批次标签 / 货位编码 / 商品编码。
 * 商品编码不会打开批次选择，仅提示需要扫描批次或货位。
 */
function handleScan() {
  const code = (scanCode.value || '').trim()
  if (!code) {
    scanResult.value = null
    return
  }
  if (code.toUpperCase().startsWith(BATCH_LABEL_PREFIX)) {
    scanResult.value = {
      type: 'BATCH',
      label: '批次标签',
      text: `识别为内部批次标签：${code}`,
      tagType: 'success'
    }
    return
  }
  const hit = locations.value.find(
    (loc) => (loc.code || '').toUpperCase() === code.toUpperCase()
  )
  if (hit) {
    scanResult.value = {
      type: 'LOCATION',
      label: '货位编码',
      text: `识别为货位：${hit.code} ${hit.name || ''}（该货位存在多个可用批次时，请改扫批次标签）`,
      tagType: 'success'
    }
    return
  }
  scanResult.value = {
    type: 'SKU',
    label: '商品编码',
    text: '识别为商品编码/条码：不能用于取货确认，请扫描内部批次标签或货位编码。',
    tagType: 'warning'
  }
}

function openFulfillDialog(row: PickupWorkItemVO) {
  currentItem.value = row
  resetFulfill()
  fulfillVisible.value = true
}

function resetFulfill() {
  scanCode.value = ''
  scanResult.value = null
  explicitBatchId.value = undefined
  explicitLocationId.value = undefined
}

async function submitFulfill() {
  if (!currentItem.value?.id) return
  const payload: { scannedCode?: string; batchId?: number; locationId?: number } = {}
  if (scanResult.value && (scanResult.value.type === 'BATCH' || scanResult.value.type === 'LOCATION')) {
    payload.scannedCode = scanCode.value.trim()
  } else if (explicitBatchId.value && explicitLocationId.value) {
    payload.batchId = explicitBatchId.value
    payload.locationId = explicitLocationId.value
  } else {
    ElMessage.warning('请先扫描或填写批次与货位')
    return
  }
  fulfilling.value = true
  try {
    await fulfillPickupRequest(currentItem.value.id, payload)
    ElMessage.success('取货完成')
    fulfillVisible.value = false
    await loadWorkItems()
  } catch (e) {
    // 请求拦截器已提示失败原因（如货位存在多个批次）
    console.warn('确认取货失败', e)
  } finally {
    fulfilling.value = false
  }
}

function openCreateDialog() {
  createForm.value = {}
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.value.orderId || !createForm.value.orderItemId) return
  creating.value = true
  try {
    await createPickupRequest(createForm.value.orderId, {
      orderItemId: createForm.value.orderItemId,
      sourceLocationId: createForm.value.sourceLocationId
    })
    ElMessage.success('取货请求已登记')
    createVisible.value = false
    await loadWorkItems()
  } catch (e) {
    console.warn('登记取货请求失败', e)
  } finally {
    creating.value = false
  }
}

onMounted(loadStores)
</script>

<style scoped>
.page-desc {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.muted-text {
  color: #909399;
  font-size: 13px;
}

.pickup-summary {
  margin-bottom: 16px;
}

.scan-result {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.scan-result-text {
  color: #606266;
  font-size: 13px;
}

.scan-hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.explicit-confirm {
  display: flex;
  gap: 8px;
}

.create-alert {
  margin-bottom: 16px;
}
</style>

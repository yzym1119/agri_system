<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>库房交货确认</h1>
        <p class="page-desc">
          收银开单选择库房取货后，在这里核对商品、存放位置和批次，再交给顾客。确认只记录交货，不会再次扣库存或收款。
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
            @change="handleStoreChange"
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
          <el-table-column prop="requestedBaseQuantity" label="原申请数量" width="150" align="right" />
          <el-table-column label="本次可交(基准)" width="150" align="right">
            <template #default="{ row }">{{ row.status === 'PENDING' ? (row.remainingBaseQuantity ?? '待核对') : '-' }}</template>
          </el-table-column>
          <el-table-column prop="sourceLocationName" label="来源货位" min-width="140">
            <template #default="{ row }">
              {{ row.sourceLocationName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="已出库批次" min-width="240">
            <template #default="{ row }">
              <div v-for="allocation in row.allocations" :key="allocation.id">
                {{ allocation.internalReceiptBatchNo || '批次#' + allocation.batchId }} ·
                {{ allocation.locationName || '货位#' + allocation.locationId }} ·
                原出库 {{ allocation.allocatedBaseQuantity }}（基准）
                <span v-if="row.status === 'PENDING'"> · 本次可交 {{ allocation.remainingBaseQuantity ?? '待核对' }}</span>
              </div>
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
                v-if="row.status === 'PENDING' && row.canFulfill === true"
                type="primary"
                link
                @click="openFulfillDialog(row)"
              >
                确认取货
              </el-button>
              <span v-else class="muted-text">{{ row.status === 'FULFILLED' ? '已完成' : row.fulfillmentBlockReason || '暂不可取货' }}</span>
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
    <el-dialog v-model="fulfillVisible" title="确认取货" width="min(880px, 96vw)"
      class="pickup-confirm-dialog" @closed="resetFulfill">
      <el-descriptions v-if="currentItem" :column="1" border size="small" class="pickup-summary">
        <el-descriptions-item label="订单号">
          {{ currentItem.orderNo || (currentItem.orderId ? '订单#' + currentItem.orderId : '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="商品">
          {{ currentItem.productName }}（{{ currentItem.productCode }}）
        </el-descriptions-item>
        <el-descriptions-item label="原申请数量">
          {{ formatQuantity(currentItem.requestedBaseQuantity) }} {{ currentItem.baseUnitName || '（记账单位待核对）' }}
        </el-descriptions-item>
        <el-descriptions-item label="本次需交数量">{{ formatQuantity(currentItem.remainingBaseQuantity) }} {{ currentItem.baseUnitName || '（记账单位待核对）' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert title="按下方位置和数量拣货，逐批勾选“已核对”。已退回的物料不再交货；单批也可扫描内部批次标签核验。" type="info" :closable="false" />
      <el-checkbox-group v-model="confirmedAllocationIds" class="pickup-form">
        <div v-for="(allocation, index) in remainingAllocations" :key="allocation.id" class="pickup-batch">
          <el-checkbox :label="allocation.id" :aria-label="`已核对第${index + 1}批商品、位置和数量`">已核对第 {{ index + 1 }} 批</el-checkbox>
          <div class="batch-facts">
            <div><span>存放位置</span><strong>{{ allocation.locationName || '位置待核对' }}</strong></div>
            <div><span>本次交付</span><strong>{{ formatQuantity(allocation.remainingBaseQuantity) }} {{ allocation.baseUnitName || '（记账单位待核对）' }}</strong></div>
            <div class="batch-code"><span>内部批次码</span><code>{{ allocation.internalReceiptBatchNo || '批次#' + allocation.batchId }}</code></div>
          </div>
        </div>
      </el-checkbox-group>
      <el-form v-if="remainingAllocations.length === 1" label-width="90px" class="pickup-form">
        <el-form-item label="单批扫码">
          <el-input v-model="scanCode" placeholder="扫描真实内部批次标签" clearable />
          <div class="scan-hint">请扫描下面显示的内部批次码；商品编码或位置编码不能代替批次核对。</div>
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
import {formatQuantity} from '@/utils/number'
import { ElMessage } from 'element-plus'
import { getStoreList } from '@/api/store'
import { getInventoryLocations } from '@/api/inventory'
import { createPickupRequest, fulfillPickupRequest, getPickupWorkItems } from '@/api/order'
import type { InventoryLocationVO } from '@/types/inventory'
import type { PickupWorkItemVO, PickupFulfillmentDTO } from '@/types/order'

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
const confirmedAllocationIds = ref<number[]>([])

// 登记取货请求
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref<{ orderId?: number; orderItemId?: number; sourceLocationId?: number }>({})

const remainingAllocations = computed(() => (currentItem.value?.allocations || [])
  .filter(allocation => Number(allocation.remainingBaseQuantity) > 0))
const canSubmit = computed(() => {
  if (!fulfillVisible.value || !fulfillmentContext || !contextMatches(fulfillmentContext)) return false
  if (currentItem.value?.canFulfill !== true || currentItem.value.status !== 'PENDING') return false
  const allocations = remainingAllocations.value
  const expectedIds = new Set(allocations.map(allocation => allocation.id))
  const confirmedIds = new Set(confirmedAllocationIds.value)
  return allocations.length > 0 && ((confirmedIds.size === allocations.length &&
    confirmedIds.size === confirmedAllocationIds.value.length && [...confirmedIds].every(id => expectedIds.has(id))) ||
    (allocations.length === 1 && !!scanCode.value.trim()))
})

// 查询与两个写入弹窗各自保留身份，旧请求的finally不能结束新会话的loading。
let pageSession = 0
let workQuery = 0
let locationQuery = 0
let fulfillmentSession = 0
let creationSession = 0
type WorkContext = { store: number; status: string; session: number }
let fulfillmentContext: WorkContext | null = null
let creationContext: WorkContext | null = null

function contextMatches(context: WorkContext) {
  return context.store === storeId.value && context.status === statusFilter.value && context.session === pageSession
}
function currentContext(): WorkContext | null {
  return storeId.value ? { store: storeId.value, status: statusFilter.value, session: pageSession } : null
}
function invalidateFulfillment() {
  fulfillmentSession++
  fulfillmentContext = null
  currentItem.value = null
  fulfillVisible.value = false
  fulfilling.value = false
  resetFulfill()
}

const canCreate = computed(() => createVisible.value && !!creationContext && contextMatches(creationContext) &&
  !!createForm.value.orderId && !!createForm.value.orderItemId)

async function loadStores() {
  try {
    const res: any = await getStoreList({ pageNum: 1, pageSize: 100 })
    stores.value = res?.list || res?.records || res?.data?.list || res?.data?.records || []
  } catch {
    console.warn('加载门店列表失败')
  }
}

async function loadLocations() {
  const query = ++locationQuery
  const context = currentContext()
  locations.value = []
  if (!context) return
  const active = () => query === locationQuery && contextMatches(context)
  try {
    const result = await getInventoryLocations(context.store)
    if (active()) locations.value = result
  } catch {
    if (!active()) return
    console.warn('加载货位列表失败')
    locations.value = []
  }
}

async function loadWorkItems() {
  const query = ++workQuery
  const context = currentContext()
  workItems.value = []
  if (!context) {
    loading.value = false
    return
  }
  const active = () => query === workQuery && contextMatches(context)
  loading.value = true
  try {
    const result = await getPickupWorkItems(context.store, context.status || undefined)
    if (active()) workItems.value = result
  } catch {
    if (!active()) return
    console.warn('加载取货待办失败')
    workItems.value = []
  } finally {
    if (active()) loading.value = false
  }
}

function handleStoreChange() {
  pageSession++
  invalidateFulfillment()
  creationSession++
  creationContext = null
  createVisible.value = false
  creating.value = false
  createForm.value = {}
  loadLocations()
  loadWorkItems()
}

function openFulfillDialog(row: PickupWorkItemVO) {
  const context = currentContext()
  // 只能打开当前查询中的工作项；切店后留存的旧行引用不可重新发起交货。
  const target = workItems.value.find(item => item.id === row.id)
  if (!context || context.status !== 'PENDING' || !target || target.status !== 'PENDING' || target.canFulfill !== true) {
    invalidateFulfillment()
    return
  }
  invalidateFulfillment()
  fulfillmentContext = context
  currentItem.value = target
  fulfillVisible.value = true
}

function resetFulfill() {
  scanCode.value = ''
  confirmedAllocationIds.value = []
}

async function submitFulfill() {
  if (fulfilling.value || !currentItem.value?.id || !canSubmit.value) return
  const target = currentItem.value
  const context = fulfillmentContext!
  const session = fulfillmentSession
  const active = () => session === fulfillmentSession && contextMatches(context) && currentItem.value === target
  const payload: PickupFulfillmentDTO = {}
  const allocations = remainingAllocations.value
  if (confirmedAllocationIds.value.length === allocations.length && allocations.length > 0) {
    payload.allocationIds = [...confirmedAllocationIds.value]
  } else if (allocations.length === 1 && scanCode.value.trim()) {
    payload.scannedCode = scanCode.value.trim()
  } else {
    ElMessage.warning('请逐项核对全部出库批次')
    return
  }
  fulfilling.value = true
  try {
    await fulfillPickupRequest(target.id, payload)
    if (!active()) return
    ElMessage.success('取货完成')
    fulfillVisible.value = false
    await loadWorkItems()
  } catch {
    // 请求拦截器已提示失败原因（如货位存在多个批次）
    if (!active()) return
    console.warn('确认取货失败')
    fulfillVisible.value = false
    await loadWorkItems()
  } finally {
    if (active()) fulfilling.value = false
  }
}

function openCreateDialog() {
  const context = currentContext()
  if (!context) return
  creationSession++
  creationContext = context
  creating.value = false
  createForm.value = {}
  createVisible.value = true
}

async function submitCreate() {
  if (creating.value || !canCreate.value) return
  const context = creationContext!
  const session = creationSession
  const active = () => session === creationSession && contextMatches(context)
  const orderId = createForm.value.orderId!
  const payload = {
    orderItemId: createForm.value.orderItemId!,
    sourceLocationId: createForm.value.sourceLocationId
  }
  creating.value = true
  try {
    await createPickupRequest(orderId, payload)
    if (!active()) return
    ElMessage.success('取货请求已登记')
    createVisible.value = false
    await loadWorkItems()
  } catch {
    if (!active()) return
    console.warn('登记取货请求失败')
  } finally {
    if (active()) creating.value = false
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

<style scoped>
.pickup-summary { overflow-wrap: anywhere; }
.pickup-batch { border: 1px solid #dce4e8; border-radius: 8px; padding: 12px 16px; margin-bottom: 10px; }
.pickup-batch :deep(.el-checkbox) { height: auto; min-height: 32px; }
.batch-facts { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 8px; font-size: 14px; line-height: 1.6; }
.batch-facts > div { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.batch-facts span { color: #626f7e; font-size: 13px; }
.batch-code { grid-column: 1 / -1; }
.batch-code code { overflow-wrap: anywhere; white-space: normal; font: inherit; }
.pickup-form { min-width: 0; }
.scan-hint { color: #626f7e; line-height: 1.5; }
@media(max-width: 600px) { .batch-facts { grid-template-columns: 1fr; } }
</style>

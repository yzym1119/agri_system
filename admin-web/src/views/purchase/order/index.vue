<template>
  <div class="page-container">
    <!-- 搜索 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="门店">
          <el-select v-model="queryParams.storeId" placeholder="请选择门店" clearable style="width: 200px">
            <el-option
              v-for="store in storeOptions"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="单号">
          <el-input v-model="queryParams.orderNo" placeholder="采购单号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="queryParams.createdRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'purchase:create'" type="success" :icon="Plus" @click="handleOpenCreate">新建采购单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border style="width: 100%">
        <el-table-column prop="orderNo" label="采购单 / 时间" width="190">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">{{ row.orderNo }}</el-button>
            <div class="cell-secondary">{{ row.createdAt || '时间待核对' }}</div>
            <div class="cell-secondary">{{ row.storeName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
          <template #default="{ row }">¥{{ (row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="handleView(row)">详情</el-button>
              <el-button
                v-if="getRowActions(row)[0]"
                v-permission="getRowActions(row)[0].permission"
                link
                :type="getRowActions(row)[0].key === 'cancel' ? 'danger' : 'primary'"
                :loading="actionLoading[row.id]"
                @click="handleRowAction(getRowActions(row)[0].key, row)"
              >{{ getRowActions(row)[0].label }}</el-button>
              <el-dropdown v-if="getRowActions(row).length > 1" trigger="click" @command="(command: string) => handleRowAction(command, row)">
                <el-button link :aria-label="`采购单 ${row.orderNo} 的更多操作`">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="action in getRowActions(row).slice(1)" :key="action.key" v-permission="action.permission" :command="action.key" :disabled="actionLoading[row.id]">
                      {{ action.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
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
          :page-count="Math.ceil(total / (queryParams.pageSize || 1))"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 创建采购单 -->
    <el-drawer v-model="createVisible" title="新建采购单" size="70%" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="门店" prop="storeId">
              <el-select v-model="createForm.storeId" placeholder="请选择门店" clearable filterable>
                <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商" prop="supplierId">
              <el-select
                v-model="createForm.supplierId"
                placeholder="请选择供应商"
                clearable
                filterable
                @change="handleSupplierChange"
              >
                <el-option
                  v-for="sup in supplierOptions"
                  :key="sup.id"
                  :label="sup.name"
                  :value="sup.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="item-toolbar">
          <div>
            <el-button type="primary" :loading="productsLoading" :disabled="submitLoading" @click="openProductSelector">添加商品</el-button>
            <el-button v-permission="'product:create'" type="success" @click="openQuickCreateProduct">+ 新建商品</el-button>
          </div>
          <span class="total-amount">合计：¥{{ totalAmount.toFixed(2) }}</span>
        </div>

        <el-table :data="createForm.items" border size="small">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="productName" label="商品名称" width="150" show-overflow-tooltip />
          <el-table-column prop="specification" label="规格" width="100" show-overflow-tooltip />
          <el-table-column prop="manufacturer" label="厂家" width="120" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="160" align="center">
            <template #default="{ row }">
              <el-input-number 
                v-model="row.qty" 
                :min="0.01" 
                :precision="2" 
                :controls="true"
                :controls-position="'right'"
                style="width: 100%"
                @change="recalcTotal" 
              />
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="采购单位" width="180" align="center">
            <template #default="{ row }">
              <el-select v-model="row.unit" style="width: 100%" @change="handleUnitChange(row)">
                <el-option v-for="unit in row.unitOptions" :key="unit.id" :label="unit.unitName" :value="unit.unitName" />
              </el-select>
              <div class="cell-secondary">{{ purchaseUnitHint(row) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="price" label="实际进价（元/采购单位）" width="190" align="center">
            <template #default="{ row }">
              <el-input-number 
                v-model="row.price"
                placeholder="填写实际进价"
                :min="0.01"
                :precision="2" 
                :controls="true"
                :controls-position="'right'"
                style="width: 100%"
                @change="recalcTotal" 
              />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">
              ¥{{ (row.qty && row.price ? row.qty * row.price : 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      
      <template #footer>
        <div style="text-align: right; padding: 20px">
          <el-button @click="createVisible = false">取消</el-button>
          <el-button v-permission="'purchase:create'" type="primary" :loading="submitLoading" :disabled="productsLoading" @click="handleCreate">提交</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="采购单详情" size="min(1120px, 96vw)" class="purchase-detail-drawer">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="采购单号">{{ detail?.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ detail?.storeName }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail?.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail?.status)">{{ statusText(detail?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ (detail?.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail?.createdAt }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail?.items || []" border style="margin-top: 16px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="productName" label="商品 / 规格" min-width="200">
          <template #default="{ row }">
            <div>{{ row.productName }}</div>
            <div class="cell-secondary">{{ row.specification || '未填规格' }} · {{ row.productCode || '未填编码' }}</div>
            <div v-if="row.manufacturer" class="cell-secondary">{{ row.manufacturer }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="采购数量" width="120" align="right" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column prop="price" label="进货单价" width="140" align="right">
          <template #default="{ row }">¥{{ (row.price || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="150" align="right">
          <template #default="{ row }">¥{{ (row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 商品选择器 -->
    <ProductSelector v-model="productSelectorVisible" :selected-ids="selectedProductIds" @confirm="handleProductSelect" />

    <!-- 快捷新建商品 -->
    <QuickProductForm v-model="quickProductFormVisible" @success="handleQuickProductCreated" />
  </div>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {usePermissionStore} from '@/store/permission'
import {hasPermission} from '@/utils/permission'
import {Plus, Refresh, Search} from '@element-plus/icons-vue'
import {
  cancelPurchase,
  createPurchase,
  getPurchaseDetail,
  getPurchaseList,
  submitPurchase
} from '@/api/purchase'
import {getAllStores} from '@/api/store'
import {getAllSuppliers} from '@/api/supplier'
import type {
  PurchaseCreateRequest,
  PurchaseDetailVO,
  PurchasePageQuery,
  PurchasePageVO
} from '@/types/purchase'
import type {SupplierDetailVO} from '@/types/supplier'
import type {StoreDetailVO} from '@/types/store'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'
import ProductSelector from '@/components/ProductSelector/index.vue'
import QuickProductForm from '@/components/QuickProductForm/index.vue'
import type {ProductDetailVO, ProductUnitVO} from '@/types/product'
import {getProductDetail} from '@/api/product'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const permissionStore = usePermissionStore()

// 状态映射
const statusOptions = [
  { label: '待提交', value: 1 },
  { label: '待入库', value: 2 },
  { label: '已入库', value: 3 },
  { label: '已取消', value: 4 }
]
const statusText = (val?: number) => statusOptions.find(i => i.value === val)?.label || '-'
const statusTag = (val?: number) => {
  if (val === 3) return 'success'
  if (val === 2) return 'warning'
  if (val === 4) return 'info'
  return 'primary'
}

// 查询参数
const queryParams = reactive<PurchasePageQuery & { createdRange?: string[] }>({
  pageNum: 1,
  pageSize: 10
})

const loading = ref(false)
const total = ref(0)
const tableData = ref<PurchasePageVO[]>([])
const actionLoading = ref<Record<number, boolean>>({})
let listRequest = 0

const storeOptions = ref<StoreDetailVO[]>([])
const supplierOptions = ref<SupplierDetailVO[]>([])

// 创建表单
const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
type PurchaseDraftItem = {productId: number; qty: number; unit?: string; price?: number; productName: string; specification?: string; manufacturer?: string; unitOptions: ProductUnitVO[]; baseUnitName: string}
const createForm = reactive<Omit<PurchaseCreateRequest, 'items'> & {items: PurchaseDraftItem[]}>({
  storeId: undefined as unknown as number,
  supplierId: undefined,
  supplierName: '',
  items: [],
  remark: ''
})
const createRules: FormRules = {
  storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  items: [
    {
      validator: (_, val, callback) => {
        if (!val || !val.length) callback(new Error('请添加至少一条商品明细'))
        else callback()
      },
      trigger: 'change'
    }
  ]
}
const submitLoading = ref(false)
const pendingProductReads = ref(0)
const productsLoading = computed(() => pendingProductReads.value > 0)
let createSession = 0
watch(createVisible, visible => {
  if (!visible) {
    createSession++
    productSelectorVisible.value = false
    quickProductFormVisible.value = false
    pendingProductReads.value = 0
  }
}, {flush: 'sync'})

// 详情
const detailVisible = ref(false)
const detail = ref<PurchaseDetailVO>()

// 商品选择器
const productSelectorVisible = ref(false)
const selectedProductIds = computed(() => createForm.items.map(i => i.productId))

// 快捷新建商品
const quickProductFormVisible = ref(false)

const totalAmount = computed(() =>
  createForm.items.reduce((sum, item) => {
    const amount = (item.qty || 0) * (item.price || 0)
    return sum + amount
  }, 0)
)

// 加载数据
const loadOptions = async () => {
  try {
    ;[storeOptions.value, supplierOptions.value] = await Promise.all([getAllStores(), getAllSuppliers()])
  } catch (error) {
    console.error('加载采购页选项失败:', error)
    storeOptions.value = []
    supplierOptions.value = []
    ElMessage.error('加载门店或供应商选项失败')
  }
}

const loadTable = async () => {
  const requestId = ++listRequest
  loading.value = true
  try {
    const params: PurchasePageQuery = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      storeId: queryParams.storeId,
      supplierName: queryParams.supplierName,
      orderNo: queryParams.orderNo,
      status: queryParams.status,
      createdStartTime: queryParams.createdRange?.[0],
      createdEndTime: queryParams.createdRange?.[1]
    }
    const res = await getPurchaseList(params)
    if (requestId !== listRequest) return
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    if (requestId !== listRequest) return
    console.error('加载采购单列表失败:', error)
    tableData.value = []
    total.value = 0
    ElMessage.error('加载采购单列表失败')
  } finally {
    if (requestId === listRequest) loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  loadTable()
}
const handleReset = () => {
  queryParams.storeId = undefined
  queryParams.supplierName = ''
  queryParams.orderNo = ''
  queryParams.status = undefined
  queryParams.createdRange = undefined
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

type RowAction = {key: string; label: string; permission: string}
const getRowActions = (row: PurchasePageVO): RowAction[] => {
  const actions: RowAction[] = []
  if (row.status === 1) actions.push({key: 'submit', label: '提交', permission: 'purchase:submit'})
  if (row.status === 2) actions.push({key: 'inbound', label: '收货', permission: 'purchase:inbound'})
  if (row.status === 1 || row.status === 2) actions.push({key: 'cancel', label: '取消', permission: 'purchase:cancel'})
  return actions.filter(action => hasPermission(action.permission, permissionStore.permissions))
}
const handleRowAction = (command: string, row: PurchasePageVO) => {
  if (actionLoading.value[row.id] || !getRowActions(row).some(action => action.key === command)) return
  if (command === 'submit') handleSubmit(row)
  else if (command === 'inbound') handleOpenInbound(row)
  else if (command === 'cancel') handleCancel(row)
}

const handleOpenCreate = () => {
  resetCreateForm()
  createVisible.value = true
}

const resetCreateForm = () => {
  createSession++
  pendingProductReads.value = 0
  createForm.storeId = undefined as unknown as number
  createForm.supplierId = undefined
  createForm.supplierName = ''
  createForm.items = []
  createForm.remark = ''
}

const handleSupplierChange = (val?: number) => {
  const sup = supplierOptions.value.find(s => s.id === val)
  createForm.supplierName = sup?.name || ''
}

const openProductSelector = () => {
  productSelectorVisible.value = true
}

const purchaseUnits = (product: ProductDetailVO) => (product.units || []).filter(unit =>
  unit.id != null && unit.status === 1 && unit.allowPurchase === 1 &&
  Number.isFinite(Number(unit.baseQuantityPerUnit)) && Number(unit.baseQuantityPerUnit) > 0
)

const purchaseUnitHint = (row: PurchaseDraftItem) => {
  const unit = row.unitOptions.find(unit => unit.unitName === row.unit)
  return unit ? `1${unit.unitName} = ${unit.baseQuantityPerUnit}${row.baseUnitName}` : '请选择有效采购单位'
}

const handleProductSelect = async (products: ProductDetailVO[]) => {
  if (!createVisible.value) return
  const session = createSession
  pendingProductReads.value++
  try {
    const details = await Promise.all(products.map(product => getProductDetail(product.id)))
    if (!createVisible.value || session !== createSession) return
    for (const product of details) {
      if (createForm.items.some(item => item.productId === product.id)) continue
      const units = purchaseUnits(product)
      const baseUnit = (product.units || []).find(unit => unit.id != null && unit.isBaseUnit === 1 && Number(unit.baseQuantityPerUnit) === 1)
      if (!units.length || !baseUnit) {
        ElMessage.warning(`商品【${product.name}】没有有效采购单位，请先核对商品单位档案`)
        continue
      }
      const defaultUnit = units.find(unit => unit.unitName === product.mainUnit) || units[0]
      createForm.items.push({productId: product.id, productName: product.name,
        specification: product.specification, manufacturer: product.manufacturer,
        qty: 1, unit: defaultUnit.unitName, price: undefined,
        unitOptions: units, baseUnitName: baseUnit.unitName})
    }
  } catch {
    if (createVisible.value && session === createSession) ElMessage.error('读取采购单位失败，请重新选择商品')
  } finally {
    if (session === createSession) pendingProductReads.value--
  }
}

const handleUnitChange = (row: PurchaseDraftItem) => {
  // 不沿用另一采购单位的成本，也不从历史加权成本猜测本次进价。
  row.price = undefined
}

const openQuickCreateProduct = () => {
  quickProductFormVisible.value = true
}

const handleQuickProductCreated = async (product: ProductDetailVO) => {
  await handleProductSelect([product])
}

const removeItem = (idx: number) => {
  createForm.items.splice(idx, 1)
  recalcTotal()
}

const recalcTotal = () => {
  // 触发视图更新（computed 总额已处理，这里仅确保响应式）
  createForm.items = [...createForm.items]
}

const handleCreate = async () => {
  if (submitLoading.value || productsLoading.value || !createVisible.value) return
  const session = createSession
  submitLoading.value = true
  try {
    const valid = await createFormRef.value?.validate().catch(() => false)
    if (!valid || !createVisible.value || session !== createSession) return
    const draft = createForm.items.map(item => ({...item, unitOptions: [...item.unitOptions]}))
    if (!draft.length || draft.some(item => !Number.isFinite(item.qty) || item.qty <= 0 || !Number.isFinite(item.price) || Number(item.price) <= 0)) {
      ElMessage.warning('请填写每项商品的有效数量和实际进价')
      return
    }
    const payload: PurchaseCreateRequest = {
      storeId: createForm.storeId, supplierId: createForm.supplierId,
      supplierName: createForm.supplierName, remark: createForm.remark,
      items: draft.map(item => ({productId: item.productId, qty: item.qty, unit: item.unit, price: item.price!}))
    }
    const currentProducts = await Promise.all(draft.map(item => getProductDetail(item.productId)))
    if (!createVisible.value || session !== createSession) return
    const invalid = draft.find((item, index) => {
      const original = item.unitOptions.find(unit => unit.unitName === item.unit)
      const current = purchaseUnits(currentProducts[index]).find(unit => unit.unitName === item.unit)
      return !original || !current || current.id !== original.id || Number(current.baseQuantityPerUnit) !== Number(original.baseQuantityPerUnit)
    })
    if (invalid) {
      ElMessage.warning(`商品【${invalid.productName}】采购单位已失效或变化，请删除后重新添加`)
      return
    }
    await createPurchase(payload)
    if (!createVisible.value || session !== createSession) return
    ElMessage.success('创建成功')
    createVisible.value = false
    loadTable()
  } catch {
    if (createVisible.value && session === createSession) ElMessage.error('创建采购单失败，请核对列表后再操作')
  } finally {
    submitLoading.value = false
  }
}

const handleSubmit = async (row: PurchasePageVO) => {
  actionLoading.value[row.id] = true
  try {
    await ElMessageBox.confirm('确认提交该采购单吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await submitPurchase({ orderId: row.id })
    ElMessage.success('提交成功')
    loadTable()
  } catch (e) {
    // 忽略取消
  } finally {
    actionLoading.value[row.id] = false
  }
}

const handleOpenInbound = (row: PurchasePageVO) => {
  router.push({ path: '/purchase/receipt', query: { purchaseOrderId: String(row.id) } })
}

const handleView = async (row: PurchasePageVO) => {
  detail.value = await getPurchaseDetail(row.id)
  detailVisible.value = true
}

const handleCancel = async (row: PurchasePageVO) => {
  actionLoading.value[row.id] = true
  try {
    await ElMessageBox.confirm('确认取消该采购单吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await cancelPurchase({ orderId: row.id })
    ElMessage.success('取消成功')
    loadTable()
  } catch (e) {
    // 忽略取消
  } finally {
    actionLoading.value[row.id] = false
  }
}

const applyRouteQuery = (query: Record<string, unknown>) => {
  const status = typeof query.status === 'string' ? Number(query.status) : NaN
  queryParams.status = [1, 2, 3, 4].includes(status) ? status : undefined
  queryParams.pageNum = 1
}
applyRouteQuery(route.query)
watch(() => route.query, query => {
  applyRouteQuery(query)
  loadTable()
}, {deep: true})
loadOptions().then(loadTable)
</script>

<style scoped lang="scss">
.row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  .el-button + .el-button { margin-left: 0; }
}
.cell-secondary { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
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

.item-toolbar {
  margin: 12px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.total-amount {
  font-weight: 600;
  color: #f56c6c;
}
</style>

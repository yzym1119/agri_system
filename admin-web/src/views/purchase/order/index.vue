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
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="orderNo" label="采购单号" width="180" />
        <el-table-column prop="storeName" label="门店" width="140" />
        <el-table-column prop="supplierName" label="供应商" width="160" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
          <template #default="{ row }">¥{{ (row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button
              v-if="row.status === 1"
              v-permission="'purchase:submit'"
              link
              type="success"
              :loading="actionLoading[row.id]"
              @click="handleSubmit(row)"
            >提交</el-button>
            <el-button
              v-if="row.status === 2"
              v-permission="'purchase:inbound'"
              link
              type="warning"
              :loading="actionLoading[row.id]"
              @click="handleOpenInbound(row)"
            >入库</el-button>
            <el-button
              v-if="row.status === 1 || row.status === 2"
              v-permission="'purchase:cancel'"
              link
              type="danger"
              :loading="actionLoading[row.id]"
              @click="handleCancel(row)"
            >取消</el-button>
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
            <el-button type="primary" @click="openProductSelector">添加商品</el-button>
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
          <el-table-column prop="unit" label="单位" width="120" align="center">
            <template #default="{ row }">
              <el-select v-model="row.unit" style="width: 100%" @change="handleUnitChange(row)">
                <el-option v-if="row.productMainUnit" :label="row.productMainUnit" :value="row.productMainUnit" />
                <el-option v-if="row.productAuxUnit" :label="row.productAuxUnit" :value="row.productAuxUnit" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="price" label="进货单价" width="160" align="center">
            <template #default="{ row }">
              <el-input-number 
                v-model="row.price" 
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
          <el-button v-permission="'purchase:create'" type="primary" :loading="submitLoading" @click="handleCreate">提交</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="采购单详情" size="60%">
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
      <el-table :data="detail?.items || []" border size="small" style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="productCode" label="商品编码" width="120" />
        <el-table-column prop="specification" label="规格" width="100" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂家" width="120" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column prop="price" label="单价" width="120" align="right">
          <template #default="{ row }">¥{{ (row.price || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">¥{{ (row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 入库弹窗 -->
    <el-dialog v-model="inboundVisible" title="采购入库" width="70%" :close-on-click-modal="false">
      <el-descriptions :column="2" border style="margin-bottom: 16px">
        <el-descriptions-item label="入库门店">{{ inboundStoreName }}</el-descriptions-item>
        <el-descriptions-item label="采购单号">{{ inboundOrderNo }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="inboundItems" border size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格" width="100" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂家" width="120" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column prop="qty" label="采购数量" width="120" align="right" />
        <el-table-column label="实收数量" width="160" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0.01" :precision="2" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="实际成本" width="160" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.costPrice" :min="0.01" :precision="2" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="生产日期" width="160" align="center">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.productionDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
              @change="() => handleDateChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="保质期" width="140" align="center">
          <template #default="{ row }">
            <el-select
              v-model="row._shelfLife"
              placeholder="请选择"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
              @change="() => handleDateChange(row)"
            >
              <el-option v-for="opt in shelfLifeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="160" align="center">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.expireDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="自动计算"
              :input-style="{ color: '#606266', fontWeight: 'bold' }"
              style="width: 100%"
              @change="(val: string | null) => handleExpireChange(row, val)"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="inboundVisible = false">取消</el-button>
        <el-button v-permission="'purchase:inbound'" type="primary" :loading="submitLoading" @click="handleInbound">确认入库</el-button>
      </template>
    </el-dialog>

    <!-- 商品选择器 -->
    <ProductSelector v-model="productSelectorVisible" :selected-ids="selectedProductIds" @confirm="handleProductSelect" />

    <!-- 快捷新建商品 -->
    <QuickProductForm v-model="quickProductFormVisible" @success="handleQuickProductCreated" />
  </div>
</template>

<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import {Plus, Refresh, Search} from '@element-plus/icons-vue'
import {
  cancelPurchase,
  createPurchase,
  getPurchaseDetail,
  getPurchaseList,
  inboundPurchase,
  submitPurchase
} from '@/api/purchase'
import {getAllStores} from '@/api/store'
import {getAllSuppliers} from '@/api/supplier'
import type {
  PurchaseCreateRequest,
  PurchaseDetailVO,
  PurchaseInboundRequest,
  PurchasePageQuery,
  PurchasePageVO
} from '@/types/purchase'
import type {SupplierDetailVO} from '@/types/supplier'
import type {StoreDetailVO} from '@/types/store'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'
import ProductSelector from '@/components/ProductSelector/index.vue'
import QuickProductForm from '@/components/QuickProductForm/index.vue'
import type {ProductDetailVO} from '@/types/product'
import {getProductDetail} from '@/api/product'
import dayjs from 'dayjs'

const shelfLifeOptions = [
  { label: '1年 (12个月)', value: 12 },
  { label: '2年 (24个月)', value: 24 },
  { label: '3年 (36个月)', value: 36 },
  { label: '5年 (60个月)', value: 60 }
]

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

const storeOptions = ref<StoreDetailVO[]>([])
const supplierOptions = ref<SupplierDetailVO[]>([])

// 创建表单
const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<PurchaseCreateRequest>({
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

// 详情
const detailVisible = ref(false)
const detail = ref<PurchaseDetailVO>()

// 入库
const inboundVisible = ref(false)
const inboundOrderId = ref<number>()
const inboundStoreName = ref<string>('')
const inboundOrderNo = ref<string>('')
type InboundRow = {
  productId: number
  productName?: string
  specification?: string
  manufacturer?: string
  unit?: string
  qty?: number
  quantity: number
  costPrice: number
  productionDate?: string
  expireDate?: string
  _shelfLife?: number | null
}

const inboundItems = ref<InboundRow[]>([])

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
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载采购单列表失败:', error)
    tableData.value = []
    total.value = 0
    ElMessage.error('加载采购单列表失败')
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

const handleOpenCreate = () => {
  resetCreateForm()
  createVisible.value = true
}

const resetCreateForm = () => {
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

const handleProductSelect = (products: ProductDetailVO[]) => {
  products.forEach(p => {
    if (!createForm.items.find(i => i.productId === p.id)) {
      // 默认使用主单位，单价用加权成本
      const defaultUnit = p.mainUnit || p.auxUnit
      const avgCost = p.avgCostPrice || 0
      let unitPrice = avgCost
      // 如果默认选了辅单位且有比例，则换算
      if (defaultUnit === p.auxUnit && p.mainToAuxRatio) {
        if (p.mainToAuxRatio > 0) {
          unitPrice = Number((avgCost / p.mainToAuxRatio).toFixed(2))
        } else {
          ElMessage.warning(`商品【${p.name}】未设置有效换算比例`)
          unitPrice = avgCost
        }
      }
      
      createForm.items.push({
        productId: p.id,
        qty: 1,
        unit: defaultUnit,
        price: unitPrice,
        avgCostPrice: avgCost,
        productName: p.name as any, // for display only
        specification: p.specification as any,
        manufacturer: p.manufacturer as any,
        productMainUnit: p.mainUnit as any,
        productAuxUnit: p.auxUnit as any,
        productMainToAuxRatio: p.mainToAuxRatio as any
      } as any)
    }
  })
  recalcTotal()
}

const handleUnitChange = (row: any) => {
  const ratio = row.productMainToAuxRatio
  const mainUnit = row.productMainUnit
  const auxUnit = row.productAuxUnit
  const avgCost = row.avgCostPrice || 0

  if (row.unit === auxUnit) {
    if (ratio && ratio > 0) {
      row.price = Number((avgCost / ratio).toFixed(2))
    } else {
      ElMessage.warning('该商品未设置换算比例，已切回主单位')
      row.unit = mainUnit
      row.price = avgCost
    }
  } else {
    // 主单位
    row.price = avgCost
  }
  recalcTotal()
}

const openQuickCreateProduct = () => {
  quickProductFormVisible.value = true
}

const handleQuickProductCreated = async (product: ProductDetailVO) => {
  // 获取完整的商品信息
  if (product.id) {
    try {
      const fullProduct = await getProductDetail(product.id)
      // 自动将新创建的商品添加到采购列表
      handleProductSelect([fullProduct])
      ElMessage.success('商品已添加到采购列表')
    } catch (error: any) {
      ElMessage.error('获取商品详情失败：' + (error.message || '未知错误'))
    }
  }
}

const removeItem = (idx: number) => {
  createForm.items.splice(idx, 1)
  recalcTotal()
}

const recalcTotal = () => {
  // 触发视图更新（computed 总额已处理，这里仅确保响应式）
  createForm.items = [...createForm.items]
}

const handleCreate = () => {
  createFormRef.value?.validate(async valid => {
    if (!valid) return
    
    // 检查是否有商品的进货单价为 0.01（默认值），提示用户确认
    const lowPriceItems = createForm.items.filter(item => {
      const price = item.price || 0
      return price <= 0.01
    })
    
    if (lowPriceItems.length > 0) {
      const itemNames = lowPriceItems.map(item => (item as any).productName || `商品ID:${item.productId}`).join('、')
      try {
        await ElMessageBox.confirm(
          `以下商品的进货单价为 0.01 元或更低，请确认是否已正确填写进货单价：\n${itemNames}\n\n是否继续提交？`,
          '进货单价提醒',
          {
            type: 'warning',
            confirmButtonText: '继续提交',
            cancelButtonText: '返回修改',
            dangerouslyUseHTMLString: false
          }
        )
      } catch {
        // 用户取消，不提交
        return
      }
    }
    
    submitLoading.value = true
    try {
      // 保证 supplierName 有值
      if (createForm.supplierId && !createForm.supplierName) {
        const sup = supplierOptions.value.find(s => s.id === createForm.supplierId)
        createForm.supplierName = sup?.name
      }
      const payload: PurchaseCreateRequest = {
        storeId: createForm.storeId,
        supplierId: createForm.supplierId,
        supplierName: createForm.supplierName,
        remark: createForm.remark,
        items: createForm.items.map(i => ({
          productId: i.productId,
          qty: i.qty,
          unit: i.unit,
          price: i.price
        }))
      }
      await createPurchase(payload)
      ElMessage.success('创建成功')
      createVisible.value = false
      loadTable()
    } finally {
      submitLoading.value = false
    }
  })
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

const handleOpenInbound = async (row: PurchasePageVO) => {
  inboundOrderId.value = row.id
  const detailData = await getPurchaseDetail(row.id)
  inboundStoreName.value = detailData.storeName || '-'
  inboundOrderNo.value = detailData.orderNo || '-'
  inboundItems.value =
    detailData.items?.map(it => ({
      productId: it.productId,
      productName: it.productName,
      specification: it.specification,
      manufacturer: it.manufacturer,
      unit: it.unit,
      qty: it.qty,
      quantity: it.qty,
      costPrice: it.price,
      _shelfLife: 24
    })) || []
  inboundVisible.value = true
}

const handleDateChange = (row: InboundRow) => {
  if (row.productionDate && row._shelfLife) {
    row.expireDate = dayjs(row.productionDate)
      .add(Number(row._shelfLife), 'month')
      .format('YYYY-MM-DD')
  }
}

const handleExpireChange = (row: InboundRow, val: string | null) => {
  row.expireDate = val || undefined
}

const handleInbound = async () => {
  if (!inboundOrderId.value) return

  // 前端校验
  if (!inboundItems.value.length) {
    ElMessage.warning('入库明细不能为空')
    return
  }

  for (const item of inboundItems.value) {
    if (!item.quantity || item.quantity <= 0) {
      ElMessage.warning(`请填写商品【${item.productName || item.productId}】的实收数量`)
      return
    }
    if (!item.costPrice || item.costPrice <= 0) {
      ElMessage.warning(`请填写商品【${item.productName || item.productId}】的实际成本`)
      return
    }
    // 若生产日期和保质期都有，则再次计算有效期，避免未计算的情况
    if (item.productionDate && item._shelfLife && !item.expireDate) {
      item.expireDate = dayjs(item.productionDate)
        .add(Number(item._shelfLife), 'month')
        .format('YYYY-MM-DD')
    }
  }

  submitLoading.value = true
  try {
    const cleanedItems = inboundItems.value.map(i => ({
      productId: i.productId,
      quantity: Number(i.quantity),
      costPrice: Number(i.costPrice),
      productionDate: i.productionDate,
      expireDate: i.expireDate
    }))

    const payload: PurchaseInboundRequest = {
      orderId: inboundOrderId.value,
      items: cleanedItems
    }
    await inboundPurchase(payload)
    ElMessage.success('入库成功')
    inboundVisible.value = false
    loadTable()
  } finally {
    submitLoading.value = false
  }
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

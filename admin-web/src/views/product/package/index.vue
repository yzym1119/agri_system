<template>
  <div class="package-container">
    <el-card>
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="queryParams" class="search-form">
          <el-form-item label="套餐编码">
            <el-input
              v-model="queryParams.code"
              placeholder="请输入套餐编码"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="套餐名称">
            <el-input
              v-model="queryParams.name"
              placeholder="请输入套餐名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="queryParams.status"
              placeholder="请选择状态"
              clearable
              style="width: 150px"
            >
              <el-option label="上架" :value="1" />
              <el-option label="下架" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增套餐</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="packageList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="code" label="套餐编码" width="150" />
        <el-table-column prop="name" label="套餐名称" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              :content="getPackageItemsTooltip(row)"
              placement="top"
              :disabled="!row.items || row.items.length === 0"
            >
              <span>{{ row.name }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="suggestedRetailPrice" label="零售价" width="100" align="right">
          <template #default="{ row }">
            ¥{{ row.suggestedRetailPrice?.toFixed(2) || '0.00' }}
          </template>
        </el-table-column>
        <el-table-column prop="vipPrice" label="VIP价" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.vipPrice">¥{{ row.vipPrice.toFixed(2) }}</span>
            <span v-else class="text-gray">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="minPrice" label="最低售价" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.minPrice">¥{{ row.minPrice.toFixed(2) }}</span>
            <span v-else class="text-gray">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-else class="text-gray">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button
              type="warning"
              link
              :icon="row.status === 1 ? 'Bottom' : 'Top'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="900px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <!-- 主信息 -->
        <el-divider content-position="left">主信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="套餐名称" prop="name">
              <el-input v-model="formData.name" placeholder="请输入套餐名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="零售价" prop="suggestedRetailPrice">
              <el-input-number
                v-model="formData.suggestedRetailPrice"
                :min="0"
                :precision="2"
                placeholder="请输入零售价"
                style="width: 100%"
                @change="handleTotalPriceChange"
              />
              <span class="form-tip">自动计算或手动输入（手动输入后将暂停自动计算）</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="VIP价" prop="vipPrice">
              <el-input-number
                v-model="formData.vipPrice"
                :min="0"
                :precision="2"
                placeholder="请输入VIP价（可选）"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最低售价" prop="minPrice">
              <el-input-number
                v-model="formData.minPrice"
                :min="0"
                :precision="2"
                placeholder="请输入最低售价"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 明细表格 -->
        <el-divider content-position="left">套餐明细</el-divider>
        <div class="item-table-toolbar">
          <el-button type="primary" :icon="Plus" @click="handleAddItem">添加商品</el-button>
        </div>
        <el-table
          :data="formData.items"
          border
          stripe
          style="width: 100%; margin-top: 10px"
          show-summary
          :summary-method="getSummaries"
        >
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip />
          <el-table-column label="规格" width="120">
            <template #default="{ row }">
              {{ row.productSpecification || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120" align="center">
            <template #default="{ row, $index }">
              <el-input-number
                v-model="row.quantity"
                :min="1"
                :step="1"
                :precision="0"
                style="width: 100%"
                @change="handleQuantityChange($index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="120" align="center">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.unit"
                style="width: 100%"
                @change="handleUnitChange($index)"
              >
                <el-option
                  v-if="row.productMainUnit"
                  :label="row.productMainUnit"
                  :value="row.productMainUnit"
                />
                <el-option
                  v-if="row.productAuxUnit"
                  :label="row.productAuxUnit"
                  :value="row.productAuxUnit"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="单价（元）" width="120" align="right">
            <template #default="{ row }">
              ¥{{ (row.currentUnitPrice || 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="小计（元）" width="120" align="right">
            <template #default="{ row }">
              ¥{{ (row.subtotal || 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ $index }">
              <el-button
                type="danger"
                link
                :icon="Delete"
                @click="handleRemoveItem($index)"
              >
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 商品选择器 -->
    <ProductSelector
      v-model="productSelectorVisible"
      :selected-ids="formData.items.map(item => item.productId)"
      @confirm="handleProductSelect"
    />
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit, Plus, Refresh, Search} from '@element-plus/icons-vue'
import {
  createPackage,
  deletePackage,
  getPackageDetail,
  getPackageList,
  updatePackage,
  updatePackageStatus
} from '@/api/package'
import ProductSelector from '@/components/ProductSelector/index.vue'
import {getProductDetail} from '@/api/product'
import type {PageResponse, ProductDetailVO} from '@/types/product'
import type {
  PackageItemDTO,
  ProductPackageCreateRequest,
  ProductPackageDetailVO,
  ProductPackageItemVO,
  ProductPackageQueryParams,
  ProductPackageUpdateRequest
} from '@/types/package'

// 加载状态
const loading = ref(false)
const submitLoading = ref(false)

// 套餐列表
const packageList = ref<ProductPackageDetailVO[]>([])
const total = ref(0)

// 查询参数
const queryParams = reactive<ProductPackageQueryParams>({
  code: '',
  name: '',
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<{
  id?: number
  name: string
  suggestedRetailPrice: number
  vipPrice?: number
  minPrice?: number
  items: ProductPackageItemVO[]
  remark: string
}>({
  name: '',
  suggestedRetailPrice: 0,
  vipPrice: undefined,
  minPrice: undefined,
  items: [],
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  suggestedRetailPrice: [{ required: true, message: '请输入零售价', trigger: 'blur' }]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑套餐' : '新增套餐'
})

// 商品选择器
const productSelectorVisible = ref(false)

// 是否自动计算总价（用户手动修改总价后暂停自动计算）
const autoCalculateTotal = ref(true)

// 加载套餐列表
const loadPackageList = async () => {
  loading.value = true
  try {
    const response: PageResponse<ProductPackageDetailVO> = await getPackageList(queryParams)
    packageList.value = response.records
    total.value = response.total
  } catch (error) {
    console.error('加载套餐列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 查询
const handleSearch = () => {
  queryParams.pageNum = 1
  loadPackageList()
}

// 重置
const handleReset = () => {
  queryParams.code = ''
  queryParams.name = ''
  queryParams.status = undefined
  queryParams.pageNum = 1
  loadPackageList()
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: ProductPackageDetailVO) => {
  isEdit.value = true
  resetForm()
  try {
    const detail = await getPackageDetail(row.id)
    
    // 检查数据是否有效
    if (!detail || !detail.id) {
      ElMessage.error('加载套餐详情失败：数据格式错误')
      console.error('套餐详情数据:', detail)
      return
    }
    
    formData.id = detail.id
    formData.name = detail.name
    formData.suggestedRetailPrice = detail.suggestedRetailPrice
    formData.vipPrice = detail.vipPrice
    formData.minPrice = detail.minPrice
    formData.remark = detail.remark || ''
    
    // 需要获取商品详情来填充价格计算所需的信息
    formData.items = (detail.items || []).map(item => ({
      productId: item.productId,
      productName: item.productName,
      productSpecification: item.productSpecification || '',
      quantity: item.quantity,
      unit: item.unit || '',
      sortOrder: item.sortOrder,
      // 这些字段需要从商品详情获取，暂时留空
      productMainUnit: undefined,
      productAuxUnit: undefined,
      productMainToAuxRatio: undefined,
      productSuggestedRetailPrice: undefined
    })) as ProductPackageItemVO[]
    
    // 加载商品详情以获取价格信息
    await loadProductDetailsForItems()
    
    dialogVisible.value = true
  } catch (error: any) {
    console.error('加载套餐详情失败:', error)
    const errorMessage = error?.response?.data?.message || error?.message || '加载套餐详情失败，请稍后再试'
    ElMessage.error(errorMessage)
  }
}

// 加载商品详情（用于编辑时获取价格信息）
const loadProductDetailsForItems = async () => {
  for (const item of formData.items) {
    try {
      const productDetail = await getProductDetail(item.productId)
      // 填充商品详细信息
      item.productMainUnit = productDetail.mainUnit
      item.productAuxUnit = productDetail.auxUnit || undefined
      item.productMainToAuxRatio = productDetail.mainToAuxRatio || undefined
      item.productSuggestedRetailPrice = productDetail.suggestedRetailPrice || 0
      // 更新价格
      updateItemPrice(item)
    } catch (error) {
      console.error(`加载商品 ${item.productId} 详情失败:`, error)
    }
  }
  // 更新总价
  updateAllPrices()
}

// 总价手动修改
const handleTotalPriceChange = () => {
  // 用户手动修改总价后，暂停自动计算
  autoCalculateTotal.value = false
}

// 删除
const handleDelete = async (row: ProductPackageDetailVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除套餐"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deletePackage(row.id)
    ElMessage.success('删除成功')
    await loadPackageList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

// 上架/下架
const handleToggleStatus = async (row: ProductPackageDetailVO) => {
  try {
    const newStatus = row.status === 1 ? 0 : 1
    await updatePackageStatus(row.id, newStatus)
    ElMessage.success('操作成功')
    await loadPackageList()
  } catch (error) {
    console.error('操作失败:', error)
  }
}

// 添加商品
const handleAddItem = () => {
  productSelectorVisible.value = true
}

// 计算当前单位单价
const calculateUnitPrice = (item: ProductPackageItemVO): number => {
  if (!item.productSuggestedRetailPrice) return 0
  
  // 如果选择的是辅单位（最小单位），直接返回商品零售价
  if (item.unit === item.productAuxUnit) {
    return item.productSuggestedRetailPrice
  }
  
  // 如果选择的是主单位，需要乘以换算比例
  if (item.unit === item.productMainUnit && item.productMainToAuxRatio) {
    return item.productSuggestedRetailPrice * item.productMainToAuxRatio
  }
  
  // 默认返回商品零售价
  return item.productSuggestedRetailPrice
}

// 计算小计
const calculateSubtotal = (item: ProductPackageItemVO): number => {
  return (item.currentUnitPrice || 0) * (item.quantity || 0)
}

// 计算总价
const calculateTotalPrice = (): number => {
  return formData.items.reduce((sum, item) => {
    return sum + (item.subtotal || 0)
  }, 0)
}

// 更新明细项的价格
const updateItemPrice = (item: ProductPackageItemVO) => {
  item.currentUnitPrice = calculateUnitPrice(item)
  item.subtotal = calculateSubtotal(item)
}

// 更新所有明细价格并计算总价
const updateAllPrices = () => {
  formData.items.forEach(item => {
    updateItemPrice(item)
  })
  
  // 自动计算总价
  if (autoCalculateTotal.value) {
    formData.suggestedRetailPrice = calculateTotalPrice()
  }
}

// 商品选择确认
const handleProductSelect = (products: ProductDetailVO[]) => {
  products.forEach(product => {
    // 检查是否已存在
    const exists = formData.items.find(item => item.productId === product.id)
    if (!exists) {
      // 优先使用辅单位，如果没有辅单位则使用主单位
      const defaultUnit = product.auxUnit || product.mainUnit
      
      const newItem: ProductPackageItemVO = {
        productId: product.id,
        productName: product.name,
        productSpecification: product.specification || '',
        quantity: 1,
        unit: defaultUnit,
        sortOrder: formData.items.length,
        // 保存商品详细信息用于价格计算
        productMainUnit: product.mainUnit,
        productAuxUnit: product.auxUnit || undefined,
        productMainToAuxRatio: product.mainToAuxRatio || undefined,
        productSuggestedRetailPrice: product.suggestedRetailPrice || 0
      }
      
      // 计算价格
      updateItemPrice(newItem)
      
      formData.items.push(newItem)
    }
  })
  
  // 更新总价
  updateAllPrices()
}

// 移除商品
const handleRemoveItem = (index: number) => {
  formData.items.splice(index, 1)
  // 重新排序
  formData.items.forEach((item, idx) => {
    item.sortOrder = idx
  })
  // 更新总价
  updateAllPrices()
}

// 数量改变
const handleQuantityChange = (index: number) => {
  const item = formData.items[index]
  if (item) {
    updateItemPrice(item)
    updateAllPrices()
  }
}

// 单位改变
const handleUnitChange = (index: number) => {
  const item = formData.items[index]
  if (item) {
    updateItemPrice(item)
    updateAllPrices()
  }
}

// 表格合计行
const getSummaries = (param: any) => {
  const { columns } = param
  const sums: string[] = []
  const total = calculateTotalPrice()
  
  columns.forEach((column: any, index: number) => {
    if (index === 0) {
      sums[index] = '合计'
      return
    }
    // 根据列的 label 判断是否为小计列
    if (column.label === '小计（元）') {
      sums[index] = `¥${total.toFixed(2)}`
    } else {
      sums[index] = ''
    }
  })
  return sums
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  // 验证明细
  if (formData.items.length === 0) {
    ElMessage.warning('请至少添加一个商品')
    return
  }

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      // 转换明细数据
      const items: PackageItemDTO[] = formData.items.map(item => ({
        productId: item.productId,
        quantity: item.quantity,
        unit: item.unit || '',
        sortOrder: item.sortOrder
      }))

      if (isEdit.value) {
        const updateData: ProductPackageUpdateRequest = {
          id: formData.id!,
          name: formData.name,
          suggestedRetailPrice: formData.suggestedRetailPrice,
          vipPrice: formData.vipPrice,
          minPrice: formData.minPrice,
          items,
          remark: formData.remark
        }
        await updatePackage(formData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: ProductPackageCreateRequest = {
          name: formData.name,
          suggestedRetailPrice: formData.suggestedRetailPrice,
          vipPrice: formData.vipPrice,
          minPrice: formData.minPrice,
          items,
          remark: formData.remark
        }
        await createPackage(createData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      await loadPackageList()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  formData.id = undefined
  formData.name = ''
  formData.suggestedRetailPrice = 0
  formData.vipPrice = undefined
  formData.minPrice = undefined
  formData.items = []
  formData.remark = ''
  autoCalculateTotal.value = true
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

// 分页大小改变
const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadPackageList()
}

// 当前页改变
const handleCurrentChange = (page: number) => {
  queryParams.pageNum = page
  loadPackageList()
}

// 获取套餐明细提示文本
const getPackageItemsTooltip = (row: ProductPackageDetailVO): string => {
  if (!row.items || row.items.length === 0) {
    return '暂无明细'
  }
  const itemsText = row.items.map((item, index) => {
    return `${index + 1}. ${item.productName || '未知商品'} × ${item.quantity}${item.unit || ''}`
  }).join('\n')
  return `套餐明细：\n${itemsText}`
}

// 监听明细变化，自动计算总价
watch(
  () => formData.items,
  () => {
    if (autoCalculateTotal.value) {
      updateAllPrices()
    }
  },
  { deep: true }
)

// 初始化
onMounted(() => {
  loadPackageList()
})
</script>

<style scoped lang="scss">
.package-container {
  padding: 20px;
}

.search-bar {
  margin-bottom: 20px;
}

.search-form {
  .el-form-item {
    margin-bottom: 0;
  }
}

.toolbar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.item-table-toolbar {
  margin-bottom: 10px;
}

.text-gray {
  color: #999;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 10px;
}
</style>


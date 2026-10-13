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
        <el-button v-permission="'package:create'" type="primary" :icon="Plus" @click="handleAdd">新增套餐</el-button>
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
        <el-table-column prop="vipPrice" label="会员价" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.vipPrice">¥{{ row.vipPrice.toFixed(2) }}</span>
            <span v-else class="text-gray">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="minPrice" label="最低价提醒" width="100" align="right">
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
            <el-button v-permission="'package:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'package:update'"
              type="warning"
              link
              :icon="row.status === 1 ? 'Bottom' : 'Top'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button v-permission="'package:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
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
        <el-divider content-position="left">套餐价格</el-divider>
        <el-alert title="价格均为元/套，独立维护；物料组成不自动改写套餐价。会员价和最低价填0表示不启用。" type="info" :closable="false" style="margin-bottom: 16px" />
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
                :min="0.01"
                :precision="2"
                placeholder="请输入零售价"
                style="width: 100%"
              />

            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="会员价" prop="vipPrice">
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
            <el-form-item label="最低价提醒" prop="minPrice">
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
          <el-button v-permission="isEdit ? 'package:update' : 'package:create'" type="primary" :icon="Plus" @click="handleAddItem">添加商品</el-button>
        </div>
        <el-table
          :data="formData.items"
          border
          stripe
          style="width: 100%; margin-top: 10px"
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
              />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="120" align="center">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.unit"
                style="width: 100%"
              >
                <el-option v-for="unit in row.saleUnits" :key="unit.unitName"
                  :label="unit.unitName" :value="unit.unitName" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ $index }">
              <el-button
                v-permission="isEdit ? 'package:update' : 'package:create'"
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
        <el-button v-permission="isEdit ? 'package:update' : 'package:create'" type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
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
import {computed, onMounted, reactive, ref} from 'vue'
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

// 加载套餐列表
const loadPackageList = async () => {
  loading.value = true
  try {
    const response: PageResponse<ProductPackageDetailVO> = await getPackageList(queryParams)
    packageList.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error('加载套餐列表失败:', error)
    packageList.value = []
    total.value = 0
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
    
    // 物料单位使用商品的持久化可销售单位，不计算物料价格
    formData.items = (detail.items || []).map(item => ({
      productId: item.productId,
      productName: item.productName,
      productSpecification: item.productSpecification || '',
      quantity: item.quantity,
      unit: item.unit || '',
      sortOrder: item.sortOrder,
      saleUnits: []
    })) as ProductPackageItemVO[]
    
    // 加载物料可销售单位，保留已保存套餐价
    await loadProductDetailsForItems()
    
    dialogVisible.value = true
  } catch (error: any) {
    console.error('加载套餐详情失败:', error)
    const errorMessage = error?.response?.data?.message || error?.message || '加载套餐详情失败，请稍后再试'
    ElMessage.error(errorMessage)
  }
}

// 加载物料可销售单位，不能覆盖套餐独立定价
const loadProductDetailsForItems = async () => {
  const failedProducts: string[] = []
  await Promise.allSettled(formData.items.map(async item => {
    try {
      const productDetail = await getProductDetail(item.productId)
      item.saleUnits = productDetail.units?.filter(unit => unit.status === 1 && unit.allowSale === 1) || []
    } catch (error) {
      console.error(`加载商品 ${item.productId} 详情失败:`, error)
      failedProducts.push(item.productName || `商品#${item.productId}`)
    }
  }))
  if (failedProducts.length > 0) {
    ElMessage.warning(`部分商品详情加载失败，单位可能不完整：${failedProducts.join('、')}`)
  }
}

// 删除
const handleDelete = async (row: ProductPackageDetailVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除套餐"${row.name}"吗？上架中的套餐需先下架，已被历史订单或启用中套餐提成规则引用的套餐不能删除。`, '提示', {
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
      ElMessage.error(error?.response?.data?.message || error?.message || '删除失败，请稍后再试')
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
  } catch (error: any) {
    console.error('操作失败:', error)
    ElMessage.error(error?.response?.data?.message || error?.message || '操作失败，请稍后再试')
  }
}

// 添加商品
const handleAddItem = () => {
  productSelectorVisible.value = true
}

// 商品选择确认
const handleProductSelect = (products: ProductDetailVO[]) => {
  products.forEach(product => {
    // 检查是否已存在
    const exists = formData.items.find(item => item.productId === product.id)
    if (!exists) {
      const saleUnits = product.units?.filter(unit => unit.status === 1 && unit.allowSale === 1) || []
      const defaultUnit = saleUnits.find(unit => unit.isBaseUnit === 1) || saleUnits[0]
      if (!defaultUnit) {
        ElMessage.warning(`${product.name}未配置可销售单位，不能加入套餐`)
        return
      }
      const newItem: ProductPackageItemVO = {
        productId: product.id, productName: product.name,
        productSpecification: product.specification || '', quantity: 1,
        unit: defaultUnit.unitName, sortOrder: formData.items.length, saleUnits
      }
      formData.items.push(newItem)
    }
  })
}

// 移除商品
const handleRemoveItem = (index: number) => {
  formData.items.splice(index, 1)
  // 重新排序
  formData.items.forEach((item, idx) => {
    item.sortOrder = idx
  })
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
    } catch (error: any) {
      console.error('提交失败:', error)
      ElMessage.error(error?.response?.data?.message || error?.message || '提交失败，请稍后再试')
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

// 初始化
onMounted(() => {
  loadPackageList()
})
</script>

<style scoped lang="scss">
.item-table-toolbar {
  margin-bottom: 10px;
}

.text-gray {
  color: #9ca3af;
}
</style>

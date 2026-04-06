<template>
  <div class="product-container">
    <el-card>
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="queryParams" class="search-form">
          <el-form-item label="关键词">
            <el-input
              v-model="queryParams.name"
              placeholder="商品名称/编码/拼音"
              clearable
              style="width: 250px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="分类">
            <el-cascader
              v-model="queryParams.categoryId"
              :options="categoryTreeOptions"
              :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true }"
              placeholder="请选择分类"
              clearable
              style="width: 250px"
            />
          </el-form-item>
          <el-form-item label="上架状态">
            <el-select
              v-model="queryParams.isOnline"
              placeholder="请选择状态"
              clearable
              style="width: 150px"
            >
              <el-option label="已上架" :value="1" />
              <el-option label="未上架" :value="0" />
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
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增商品</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="productList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column label="图片" width="80" align="center" fixed="left">
          <template #default="{ row }">
            <el-image
              :src="row.imageUrl || defaultImage"
              :preview-src-list="row.imageUrl ? [row.imageUrl] : []"
              :preview-teleported="true"
              :hide-on-click-modal="true"
              fit="cover"
              style="width: 60px; height: 60px; border-radius: 4px; cursor: pointer; display: block; background: #f5f7fa"
            >
              <template #error>
                <div class="no-image">无图片</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="商品编码" width="120" fixed="left" />
        <el-table-column prop="name" label="商品名称" width="200" show-overflow-tooltip fixed="left" />
        <el-table-column prop="categoryName" label="分类" width="100" align="center"/>
        <el-table-column prop="specification" label="规格" width="100" align="center"/>
        <el-table-column prop="suggestedRetailPrice" label="零售价" width="100" align="center">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; justify-content: flex-end; gap: 6px;">
              <span>¥{{ row.suggestedRetailPrice?.toFixed(2) || '0.00' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="dosageForm" label="剂型" width="100" align="center" />
        <el-table-column prop="manufacturer" label="生产厂家" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="mainUnit" label="主单位" width="80" align="center" />
        <el-table-column prop="auxUnit" label="辅单位" width="80" align="center" />
        <el-table-column prop="mainToAuxRatio" label="主辅换算比例" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.mainToAuxRatio">{{ row.mainToAuxRatio }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="avgCostPrice" label="加权平均成本" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.avgCostPrice">¥{{ row.avgCostPrice?.toFixed(2) }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="detailText" label="详情文字" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.detailText">{{ row.detailText }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="baseShippingFee" label="基础运费" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.baseShippingFee">¥{{ row.baseShippingFee?.toFixed(2) }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="允许专用金" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.allowSpecialFund === 1 ? 'success' : 'info'">
              {{ row.allowSpecialFund === 1 ? '允许' : '不允许' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="实时库存" width="120" align="right">
          <template #default="{ row }">
            <span v-if="stockMap[row.id] !== undefined">
              {{ stockMap[row.id]?.toFixed(2) || '0.00' }} {{ row.mainUnit }}
            </span>
            <span v-else class="loading-stock">加载中...</span>
          </template>
        </el-table-column>
        <el-table-column label="上架状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isOnline === 1 ? 'success' : 'info'">
              {{ row.isOnline === 1 ? '已上架' : '未上架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="400" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
              <!-- 价格策略 (带 Badge) -->
              <el-badge
                v-if="row.strategyCount && row.strategyCount > 0"
                :value="row.strategyCount"
                type="warning"
                :offset="[8, 7]"
                class="strategy-badge"
              >
                <el-button link type="warning" :icon="Money" @click="handlePriceStrategy(row)">
                  已配策略
                </el-button>
              </el-badge>

              <!-- 价格策略 (无 Badge) -->
              <el-button
                v-else
                link
                type="primary"
                :icon="Money"
                @click="handlePriceStrategy(row)"
              >
                配置策略
              </el-button>

              <!-- 上下架 -->
              <el-button
                type="warning"
                link
                :icon="row.isOnline === 1 ? 'Bottom' : 'Top'"
                @click="handleToggleOnline(row)"
              >
                {{ row.isOnline === 1 ? '下架' : '上架' }}
              </el-button>

              <!-- 专用金开关 (加 wrapper) -->
              <el-tooltip content="允许使用专用金" placement="top">
                <div class="switch-wrapper">
                  <el-switch
                    :model-value="row.allowSpecialFund"
                    :active-value="1"
                    :inactive-value="0"
                    @change="handleToggleSpecialFund(row)"
                  />
                </div>
              </el-tooltip>
              <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 价格策略弹窗 -->
      <PriceStrategyDialog
        v-model="priceStrategyVisible"
        :product-id="currentProductId"
        :product-name="currentProductName"
        @success="loadProductList"
      />

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

    <!-- 新增/编辑抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="800px"
      @close="handleDrawerClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <!-- 基础信息 -->
        <el-divider content-position="left">基础信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="商品分类" prop="categoryId">
              <el-cascader
                v-model="formData.categoryId"
                :options="categoryTreeOptions"
                :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true }"
                placeholder="请选择分类"
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="formData.name" placeholder="请输入商品名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <!-- 商品编码由后台自动生成，前端不显示 -->
          <el-col :span="12">
            <el-form-item label="生产厂家" prop="manufacturer">
              <el-select
                v-model="formData.manufacturer"
                placeholder="请选择生产厂家"
                filterable
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="item in manufacturerOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商品主图" prop="imageUrl">
          <div style="display: flex; flex-direction: column; gap: 10px">
            <el-image
              v-if="formData.imageUrl"
              :src="formData.imageUrl"
              style="width: 150px; height: 150px; border-radius: 4px"
              fit="cover"
              :preview-src-list="[formData.imageUrl]"
            />
            <el-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              :data="uploadData"
              name="file"
              :show-file-list="false"
              :on-success="handleImageSuccess"
              :on-error="handleImageError"
              :before-upload="beforeImageUpload"
              accept="image/*"
            >
              <el-button type="primary" :icon="Plus">
                {{ formData.imageUrl ? '重新上传' : '上传图片' }}
              </el-button>
            </el-upload>
          </div>
        </el-form-item>

        <!-- 规格与单位 -->
        <el-divider content-position="left">规格与单位</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规格" prop="specification">
              <el-input v-model="formData.specification" placeholder="如：500ml" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="剂型" prop="dosageForm">
              <el-select
                v-model="formData.dosageForm"
                placeholder="请选择剂型"
                filterable
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="item in dosageFormOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="主单位" prop="mainUnit">
              <el-select
                v-model="formData.mainUnit"
                placeholder="请选择主单位"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="item in unitOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="辅单位" prop="auxUnit">
              <el-select
                v-model="formData.auxUnit"
                placeholder="请选择辅单位（可选）"
                filterable
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="item in unitOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item
          label="换算比例"
          prop="mainToAuxRatio"
          :required="!!formData.auxUnit"
        >
          <el-input-number
            v-model="formData.mainToAuxRatio"
            :min="1"
            :step="1"
            :precision="0"
            :disabled="!formData.auxUnit"
            placeholder="1主单位=？辅单位"
            style="width: 100%"
          />
          <span class="form-tip">例如：1箱=20袋，则输入20</span>
        </el-form-item>

        <!-- 价格体系 -->
        <el-divider content-position="left">价格体系</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="零售价" prop="suggestedRetailPrice">
              <el-input-number
                v-model="formData.suggestedRetailPrice"
                :min="0"
                :precision="2"
                placeholder="请输入零售价"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="VIP价" prop="vipPrice">
              <el-input-number
                v-model="formData.vipPrice"
                :min="0"
                :precision="2"
                placeholder="请输入VIP价"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最低限价" prop="minPrice">
              <el-input-number
                v-model="formData.minPrice"
                :min="0"
                :precision="2"
                placeholder="请输入最低限价"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成本价" prop="avgCostPrice">
              <el-input
                v-model="formData.avgCostPrice"
                disabled
                placeholder="由采购计算，不可手动修改"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 设置开关 -->
        <el-divider content-position="left">设置开关</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="上架小程序" prop="isOnline">
              <el-switch
                v-model="formData.isOnline"
                :active-value="1"
                :inactive-value="0"
                active-text="上架"
                inactive-text="下架"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="允许专用金" prop="allowSpecialFund">
              <el-switch
                v-model="formData.allowSpecialFund"
                :active-value="1"
                :inactive-value="0"
                active-text="允许"
                inactive-text="不允许"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="线上价" prop="onlinePrice" v-if="formData.isOnline === 1">
          <el-input-number
            v-model="formData.onlinePrice"
            :min="0"
            :precision="2"
            placeholder="为空则取零售价"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="基础运费" prop="baseShippingFee">
          <el-input-number
            v-model="formData.baseShippingFee"
            :min="0"
            :precision="2"
            placeholder="请输入基础运费"
            style="width: 100%"
          />
        </el-form-item>

        <!-- 详情 -->
        <el-divider content-position="left">商品详情</el-divider>
        <el-form-item label="详情文字" prop="detailText">
          <el-input
            v-model="formData.detailText"
            type="textarea"
            :rows="6"
            placeholder="请输入商品详情（纯文字，用于小程序展示）"
          />
        </el-form-item>
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
        <div style="text-align: right; padding: 20px">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit, Money, Plus, Refresh, Search} from '@element-plus/icons-vue'
import PriceStrategyDialog from '@/components/PriceStrategyDialog/index.vue'
import {
  createProduct,
  deleteProduct,
  getCategoryTree,
  getProductDetail,
  getProductList,
  updateProduct,
  updateProductOnlineStatus
} from '@/api/product'
import {getDictDataByCode} from '@/api/system'
import {getAllStores} from '@/api/store'
import type {
  CategoryTreeVO,
  PageResponse,
  ProductCreateRequest,
  ProductDetailVO,
  ProductQueryParams,
  ProductUpdateRequest
} from '@/types/product'
import type {DictDataVO} from '@/types/dict'
import {useUserStore} from '@/store/user'

// 无图占位
const defaultImage =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120" viewBox="0 0 120 120"><rect width="120" height="120" fill="%23f2f3f5"/><text x="50%" y="50%" font-size="14" fill="%239099a0" text-anchor="middle" dominant-baseline="middle">无图片</text></svg>'

// 加载状态
const loading = ref(false)
const submitLoading = ref(false)
const toggleSpecialFundLoading = ref<Record<number, boolean>>({})

// 商品列表
const productList = ref<ProductDetailVO[]>([])
const total = ref(0)
const stockMap = ref<Record<number, number>>({})

// 查询参数
const queryParams = reactive<ProductQueryParams>({
  name: '',
  categoryId: undefined,
  isOnline: undefined,
  pageNum: 1,
  pageSize: 10
})

// 抽屉
const drawerVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 价格策略弹窗
const priceStrategyVisible = ref(false)
const currentProductId = ref<number>(0)
const currentProductName = ref<string>('')

// 表单数据
const formData = reactive<ProductCreateRequest & { id?: number; avgCostPrice?: number }>({
  name: '',
  categoryId: undefined,
  specification: '',
  dosageForm: '',
  manufacturer: '',
  mainUnit: '',
  auxUnit: '',
  mainToAuxRatio: undefined,
  suggestedRetailPrice: 0,
  vipPrice: undefined,
  minPrice: undefined,
  imageUrl: '',
  detailText: '',
  isOnline: 0,
  onlinePrice: undefined,
  baseShippingFee: 0,
  allowSpecialFund: 0,
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  mainUnit: [{ required: true, message: '请选择主单位', trigger: 'change' }],
  suggestedRetailPrice: [{ required: true, message: '请输入零售价', trigger: 'blur' }],
  mainToAuxRatio: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (formData.auxUnit && (!value || value < 1)) {
          callback(new Error('选择了辅单位时，换算比例必填且必须大于等于1'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 抽屉标题
const drawerTitle = computed(() => {
  return isEdit.value ? '编辑商品' : '新增商品'
})

// 分类树选项
const categoryTreeOptions = ref<CategoryTreeVO[]>([])

// 字典选项
const unitOptions = ref<DictDataVO[]>([])
const manufacturerOptions = ref<DictDataVO[]>([])
const dosageFormOptions = ref<DictDataVO[]>([])

// 门店列表（用于查询库存）
const storeList = ref<Array<{ id: number }>>([])

// 图片上传
const uploadAction = computed(() => {
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  return `${baseURL}/api/admin/products/image/upload`
})

const uploadHeaders = computed(() => {
  const userStore = useUserStore()
  return {
    Authorization: userStore.token || ''
  }
})

const uploadData = {}

// 加载分类树
const loadCategoryTree = async () => {
  try {
    categoryTreeOptions.value = await getCategoryTree()
  } catch (error) {
    console.error('加载分类树失败:', error)
  }
}

// 加载字典数据
const loadDictData = async () => {
  try {
    const [units, manufacturers, dosageForms] = await Promise.all([
      getDictDataByCode('product_unit'),
      getDictDataByCode('product_manufacturer'),
      getDictDataByCode('dosage_form')
    ])
    unitOptions.value = units
    manufacturerOptions.value = manufacturers
    dosageFormOptions.value = dosageForms
  } catch (error) {
    console.error('加载字典数据失败:', error)
  }
}

// 加载门店列表
const loadStoreList = async () => {
  try {
    const stores = await getAllStores()
    storeList.value = stores.map(s => ({ id: s.id }))
  } catch (error) {
    console.error('加载门店列表失败:', error)
  }
}

// 加载商品列表
const loadProductList = async () => {
  loading.value = true
  try {
    // 处理级联选择器的值（可能是数组）
    const categoryId = Array.isArray(queryParams.categoryId)
      ? queryParams.categoryId[queryParams.categoryId.length - 1]
      : queryParams.categoryId

    const response: PageResponse<ProductDetailVO> = await getProductList({
      ...queryParams,
      categoryId
    })
    productList.value = response.records
    total.value = response.total

    // 加载库存（简化版：暂时不显示，后续可优化）
    // loadStockForProducts(response.records)
  } catch (error) {
    console.error('加载商品列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载库存（简化实现，后续可优化为批量查询）
const loadStockForProducts = async (products: ProductDetailVO[]) => {
  // TODO: 实现批量查询库存的逻辑
  // 目前先留空，后续可以调用后端接口批量获取
}

// 查询
const handleSearch = () => {
  queryParams.pageNum = 1
  loadProductList()
}

// 重置
const handleReset = () => {
  queryParams.name = ''
  queryParams.categoryId = undefined
  queryParams.isOnline = undefined
  queryParams.pageNum = 1
  loadProductList()
}

// 新增
const handleAdd = async () => {
  isEdit.value = false
  resetForm()
  await Promise.all([loadCategoryTree(), loadDictData()])
  drawerVisible.value = true
}

// 编辑
const handleEdit = async (row: ProductDetailVO) => {
  isEdit.value = true
  resetForm()
  await Promise.all([loadCategoryTree(), loadDictData()])

  try {
    const detail = await getProductDetail(row.id)
    console.log('商品详情:', detail)
    if (!detail || !detail.id) {
      console.error('商品详情数据格式错误:', detail)
      ElMessage.error('加载商品详情失败：数据格式错误')
      return
    }
    formData.id = detail.id
    formData.name = detail.name
    formData.categoryId = detail.categoryId
    formData.specification = detail.specification
    formData.dosageForm = detail.dosageForm
    formData.manufacturer = detail.manufacturer
    formData.mainUnit = detail.mainUnit
    formData.auxUnit = detail.auxUnit
    formData.mainToAuxRatio = detail.mainToAuxRatio
    formData.suggestedRetailPrice = detail.suggestedRetailPrice
    formData.vipPrice = detail.vipPrice
    formData.minPrice = detail.minPrice
    formData.avgCostPrice = detail.avgCostPrice
    formData.imageUrl = detail.imageUrl || ''
    formData.detailText = detail.detailText
    formData.isOnline = detail.isOnline
    formData.onlinePrice = detail.onlinePrice
    formData.baseShippingFee = detail.baseShippingFee || 0
    formData.allowSpecialFund = detail.allowSpecialFund || 0
    formData.remark = detail.remark

    drawerVisible.value = true
  } catch (error) {
    console.error('加载商品详情失败:', error)
  }
}

// 价格策略
const handlePriceStrategy = (row: ProductDetailVO) => {
  currentProductId.value = row.id
  currentProductName.value = row.name
  priceStrategyVisible.value = true
}

// 删除
const handleDelete = async (row: ProductDetailVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除商品"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteProduct(row.id)
    ElMessage.success('删除成功')
    await loadProductList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      const errorMessage = error?.response?.data?.message || error?.message || '删除失败，请稍后再试'
      ElMessage.error(errorMessage)
    }
  }
}

// 上架/下架
const handleToggleOnline = async (row: ProductDetailVO) => {
  try {
    const newStatus = row.isOnline === 1 ? 0 : 1
    await updateProductOnlineStatus(row.id, newStatus)
    ElMessage.success('操作成功')
    await loadProductList()
  } catch (error) {
    console.error('操作失败:', error)
  }
}

// 切换专用金开关
const handleToggleSpecialFund = async (row: ProductDetailVO) => {
  // 防止重复点击
  if (toggleSpecialFundLoading.value[row.id]) {
    return
  }
  
  const originalValue = row.allowSpecialFund
  const newValue = originalValue === 1 ? 0 : 1
  
  // 先更新 UI 状态
  row.allowSpecialFund = newValue
  toggleSpecialFundLoading.value[row.id] = true
  
  try {
    // 只更新专用金字段，避免传递不必要的字段
    const updateData = {
      id: row.id,
      name: row.name,
      mainUnit: row.mainUnit,
      suggestedRetailPrice: row.suggestedRetailPrice,
      allowSpecialFund: newValue
    }
    console.log('更新专用金字段:', { originalValue, newValue, updateData })
    await updateProduct(row.id, updateData)
    ElMessage.success('操作成功')
    // 重新加载列表以确保数据同步
    await loadProductList()
  } catch (error: any) {
    console.error('操作失败:', error)
    // 恢复原值
    row.allowSpecialFund = originalValue
    const errorMessage = error?.response?.data?.message || error?.message || '操作失败，请稍后再试'
    ElMessage.error(errorMessage)
  } finally {
    toggleSpecialFundLoading.value[row.id] = false
  }
}

// 图片上传成功
const handleImageSuccess = (response: any) => {
  console.log('上传响应:', response)
  // 处理不同的响应格式
  // 后端返回格式: { code: "200", message: "上传成功", data: { url: "..." } }
  let imageUrl = ''
  
  if (response && response.data) {
    if (typeof response.data === 'string') {
      imageUrl = response.data
    } else if (response.data.url) {
      imageUrl = response.data.url
    } else if (response.data.data && response.data.data.url) {
      imageUrl = response.data.data.url
    }
  } else if (response && response.url) {
    imageUrl = response.url
  }
  
  if (imageUrl) {
    formData.imageUrl = imageUrl
    ElMessage.success('图片上传成功')
  } else {
    console.error('无法解析图片URL:', response)
    ElMessage.error('图片上传失败：无法获取图片地址')
  }
}

// 图片上传失败
const handleImageError = (error: any) => {
  console.error('图片上传失败:', error)
  ElMessage.error('图片上传失败，请重试')
}

// 图片上传前验证
const beforeImageUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      // 处理级联选择器的值（checkStrictly模式下可能返回数组或单个值）
      let categoryId: number | undefined
      if (Array.isArray(formData.categoryId)) {
        // 如果是数组，取最后一个值（选中的节点）
        categoryId = formData.categoryId.length > 0 
          ? (formData.categoryId[formData.categoryId.length - 1] as number)
          : undefined
      } else {
        categoryId = formData.categoryId as number | undefined
      }
      
      // 验证分类是否已选择
      if (!categoryId) {
        ElMessage.warning('请选择商品分类')
        submitLoading.value = false
        return
      }

      if (isEdit.value) {
        const updateData: ProductUpdateRequest = {
          id: formData.id!,
          name: formData.name,
          categoryId,
          specification: formData.specification,
          dosageForm: formData.dosageForm,
          manufacturer: formData.manufacturer,
          mainUnit: formData.mainUnit,
          auxUnit: formData.auxUnit || undefined,
          mainToAuxRatio: formData.auxUnit ? formData.mainToAuxRatio : undefined,
          suggestedRetailPrice: formData.suggestedRetailPrice,
          vipPrice: formData.vipPrice,
          minPrice: formData.minPrice,
          imageUrl: formData.imageUrl,
          detailText: formData.detailText,
          isOnline: formData.isOnline,
          onlinePrice: formData.onlinePrice,
          baseShippingFee: formData.baseShippingFee,
          allowSpecialFund: formData.allowSpecialFund,
          remark: formData.remark
        }
        await updateProduct(formData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: ProductCreateRequest = {
          // code 字段不传，由后端自动生成
          name: formData.name,
          categoryId,
          specification: formData.specification,
          dosageForm: formData.dosageForm,
          manufacturer: formData.manufacturer,
          mainUnit: formData.mainUnit,
          auxUnit: formData.auxUnit,
          mainToAuxRatio: formData.mainToAuxRatio,
          suggestedRetailPrice: formData.suggestedRetailPrice,
          vipPrice: formData.vipPrice,
          minPrice: formData.minPrice,
          imageUrl: formData.imageUrl,
          detailText: formData.detailText,
          isOnline: formData.isOnline,
          onlinePrice: formData.onlinePrice,
          baseShippingFee: formData.baseShippingFee,
          allowSpecialFund: formData.allowSpecialFund,
          remark: formData.remark
        }
        await createProduct(createData)
        ElMessage.success('新增成功')
      }
      drawerVisible.value = false
      await loadProductList()
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
  formData.categoryId = undefined
  formData.specification = ''
  formData.dosageForm = ''
  formData.manufacturer = ''
  formData.mainUnit = ''
  formData.auxUnit = ''
  formData.mainToAuxRatio = undefined
  formData.suggestedRetailPrice = 0
  formData.vipPrice = undefined
  formData.minPrice = undefined
  formData.avgCostPrice = undefined
  formData.imageUrl = ''
  formData.detailText = ''
  formData.isOnline = 0
  formData.onlinePrice = undefined
  formData.baseShippingFee = 0
  formData.allowSpecialFund = 0
  formData.remark = ''
}

// 监听辅单位变化，清空辅单位时清空换算比例
watch(() => formData.auxUnit, (newVal) => {
  if (!newVal) {
    formData.mainToAuxRatio = undefined
  }
})

// 抽屉关闭
const handleDrawerClose = () => {
  resetForm()
}

// 分页大小改变
const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadProductList()
}

// 当前页改变
const handleCurrentChange = (page: number) => {
  queryParams.pageNum = page
  loadProductList()
}

// 初始化
onMounted(async () => {
  await Promise.all([loadCategoryTree(), loadDictData(), loadStoreList()])
  await loadProductList()
})
</script>

<style scoped lang="scss">
.product-container {
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

.no-image {
  color: #999;
  font-size: 12px;
}

.loading-stock {
  color: #999;
  font-size: 12px;
}

.form-tip {
  margin-left: 10px;
  color: #999;
  font-size: 12px;
}

/* 1. 强制 Flex 居中布局 */
.action-cell {
  display: flex;
  align-items: center;      /* 垂直居中 */
  justify-content: center;  /* 水平居中 */
  gap: 12px;                /* 统一间距 */
  width: 100%;
}

/* 2. 重置 Element Plus 按钮默认间距 */
.action-cell .el-button + .el-button {
  margin-left: 0;
}

/* 3. 修复 Badge 对齐 (核心黑魔法) */
/* 将 Badge 变为 Flex 容器，强制内部 Button 垂直居中 */
.action-cell :deep(.el-badge) {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

/* 4. 修复 Badge 内部按钮的垂直对齐 */
.action-cell :deep(.el-badge .el-button) {
  vertical-align: inherit;
}

/* 5. Switch 容器高度对齐 */
.switch-wrapper {
  display: flex;
  align-items: center;
  height: 24px;
}
</style>


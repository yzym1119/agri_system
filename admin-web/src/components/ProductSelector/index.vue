<template>
  <el-dialog
    v-model="visible"
    title="选择商品"
    width="900px"
    @close="handleClose"
  >
    <div class="product-selector">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchParams">
          <el-form-item label="关键词">
            <el-input
              v-model="searchParams.keyword"
              placeholder="商品名称/编码"
              clearable
              style="width: 250px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="分类">
            <el-cascader
              v-model="searchParams.categoryId"
              :options="categoryTreeOptions"
              :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true }"
              placeholder="请选择分类"
              clearable
              style="width: 250px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 商品列表 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="productList"
        border
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column label="图片" width="80" align="center" class-name="image-column">
          <template #default="{ row }">
            <div style="display: flex; justify-content: center; align-items: center; width: 100%; height: 60px;">
              <el-image
                v-if="row.imageUrl"
                :src="row.imageUrl"
                fit="cover"
                style="width: 60px; height: 60px; border-radius: 4px; display: block"
                :hide-on-click-modal="true"
                :preview-teleported="true"
              />
              <span v-else class="no-image">无</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="商品编码" width="120" />
        <el-table-column prop="name" label="商品名称" width="200" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="生产厂家" width="150" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="150" />
        <el-table-column prop="specification" label="规格" width="120" />
        <el-table-column prop="mainUnit" label="单位" width="100" align="center" />
        <el-table-column prop="suggestedRetailPrice" label="零售价" width="100" align="right">
          <template #default="{ row }">
            ¥{{ row.suggestedRetailPrice?.toFixed(2) || '0.00' }}
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="searchParams.pageNum"
          v-model:page-size="searchParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, nextTick, reactive, ref, watch} from 'vue'
import {Refresh, Search} from '@element-plus/icons-vue'
import type {ElTable} from 'element-plus'
import {getCategoryTree, getProductList} from '@/api/product'
import type {CategoryTreeVO, PageResponse, ProductDetailVO, ProductQueryParams} from '@/types/product'

// Props
interface Props {
  modelValue: boolean
  selectedIds?: number[] // 已选中的商品ID列表
}

const props = withDefaults(defineProps<Props>(), {
  selectedIds: () => []
})

// Emits
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'confirm': [products: ProductDetailVO[]]
}>()

// 对话框显示状态
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 加载状态
const loading = ref(false)

// 商品列表
const productList = ref<ProductDetailVO[]>([])
const total = ref(0)

// 搜索参数
const searchParams = reactive<
  ProductQueryParams & { keyword?: string }
>({
  keyword: '',
  name: '',
  categoryId: undefined,
  pageNum: 1,
  pageSize: 10
})

// 分类树选项
const categoryTreeOptions = ref<CategoryTreeVO[]>([])

// 选中的商品
const selectedProducts = ref<ProductDetailVO[]>([])

// 表格引用
const tableRef = ref<InstanceType<typeof ElTable>>()

// 加载分类树
const loadCategoryTree = async () => {
  try {
    categoryTreeOptions.value = await getCategoryTree()
  } catch (error) {
    console.error('加载分类树失败:', error)
  }
}

// 加载商品列表
const loadProductList = async () => {
  loading.value = true
  try {
    // 处理级联选择器的值
    const categoryId = Array.isArray(searchParams.categoryId)
      ? searchParams.categoryId[searchParams.categoryId.length - 1]
      : searchParams.categoryId

    const response: PageResponse<ProductDetailVO> = await getProductList({
      ...searchParams,
      categoryId,
      // 关键词同时匹配名称和编码
      name: searchParams.keyword || searchParams.name || undefined,
      code: searchParams.keyword || undefined
    })
    productList.value = response.records
    total.value = response.total

    // 设置已选中的商品（回显）
    await nextTick()
    if (props.selectedIds && props.selectedIds.length > 0 && tableRef.value) {
      // 先清空之前的选中状态
      tableRef.value.clearSelection()
      
      // 设置新的选中状态
      productList.value.forEach(product => {
        if (props.selectedIds.includes(product.id)) {
          tableRef.value?.toggleRowSelection(product, true)
        }
      })
      
      // 更新已选商品列表（合并之前已选的和新选中的）
      const newlySelected = productList.value.filter(p => props.selectedIds.includes(p.id))
      // 保留不在当前列表中的已选商品（跨页选择的情况）
      const existingSelected = selectedProducts.value.filter(p => !productList.value.some(pl => pl.id === p.id))
      selectedProducts.value = [...existingSelected, ...newlySelected]
    } else {
      // 如果没有传入 selectedIds 或为空，清空选中状态
      selectedProducts.value = []
      if (tableRef.value) {
        tableRef.value.clearSelection()
      }
    }
  } catch (error) {
    console.error('加载商品列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 查询
const handleSearch = () => {
  searchParams.pageNum = 1
  loadProductList()
}

// 重置
const handleReset = () => {
  searchParams.keyword = ''
  searchParams.name = ''
  searchParams.categoryId = undefined
  searchParams.pageNum = 1
  loadProductList()
}

// 选择改变
const handleSelectionChange = (selection: ProductDetailVO[]) => {
  selectedProducts.value = selection
}

// 确认
const handleConfirm = () => {
  emit('confirm', selectedProducts.value)
  // 不清空选中状态，保持用于回显
  handleClose()
}

// 关闭
const handleClose = () => {
  visible.value = false
  // 不清空 selectedProducts，保持选中状态用于回显
}

// 分页大小改变
const handleSizeChange = (size: number) => {
  searchParams.pageSize = size
  searchParams.pageNum = 1
  loadProductList()
}

// 当前页改变
const handleCurrentChange = (page: number) => {
  searchParams.pageNum = page
  loadProductList()
}

// 监听对话框打开
watch(visible, (val) => {
  if (val) {
    loadCategoryTree()
    // 加载商品列表时会自动处理回显
    loadProductList()
  }
})
</script>

<style scoped lang="scss">
.product-selector {
  .search-bar {
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
}
</style>


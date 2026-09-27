<template>
  <div class="modern-goods-panel">
    <!-- 顶部搜索和Tab -->
    <div class="goods-header">
      <div class="scan-field">
        <span class="scan-field-label">商品编码（SKU）</span>
        <el-input
          ref="searchInputRef"
          v-model="searchKeyword"
          placeholder="扫描/搜索商品编码（SKU），F1聚焦"
          clearable
          size="large"
          @keyup.enter="handleSearch"
          @input="handleKeywordInput"
          @clear="handleSearch"
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <!-- 扫码解析结果：说明识别到的编码类型，商品编码不会打开批次选择 -->
        <div v-if="scanResult" class="scan-result" :class="`scan-result-${scanResult.type.toLowerCase()}`">
          <span class="scan-result-type">{{ scanResult.typeText }}</span>
          <span class="scan-result-text">{{ scanResult.text }}</span>
        </div>
      </div>

      <div class="goods-tabs">
        <div
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-item"
          :class="{ active: activeTab === tab.value }"
          @click="handleTabChange(tab.value)"
        >
          <span class="tab-label">{{ tab.label }}</span>
          <span class="tab-count">({{ getTabCount(tab.value) }})</span>
        </div>
      </div>
    </div>

    <!-- 商品网格 -->
    <div class="goods-grid" v-loading="loading">
      <div
        v-for="item in filteredGoods"
        :key="`${item.type}_${item.id}`"
        class="goods-card"
        :class="{
          'out-of-stock': (item.type === 'product' || item.type === 'package') && (item.stock || 0) <= 0,
          'is-package': item.type === 'package'
        }"
        @click="handleCardClick(item)"
      >
        <!-- 商品图片 -->
        <div class="card-image-wrapper">
          <img
            v-if="item.imageUrl && item.type === 'product'"
            :src="item.imageUrl"
            :alt="item.name"
            class="card-image"
            @error="handleImageError"
          />
          <div v-else class="card-image-placeholder">
            <el-icon v-if="item.type === 'package'" :size="48"><Box /></el-icon>
            <el-icon v-else :size="48"><Picture /></el-icon>
          </div>
          <!-- 套餐标签 -->
          <div v-if="item.type === 'package'" class="package-badge">套餐</div>
          <!-- 缺货蒙层 -->
          <div v-if="(item.type === 'product' || item.type === 'package') && (item.stock || 0) <= 0" class="out-of-stock-overlay">
            <span>缺货</span>
          </div>
        </div>

        <!-- 商品信息 -->
        <div class="card-info">
          <div class="card-name">{{ item.name }}</div>
          
          <!-- 商品详情 -->
          <div v-if="item.type === 'product'" class="card-product-details">
            <div class="card-spec-line">
              <span class="card-label">规格：</span>
              <span class="card-spec">{{ item.specification || '暂无' }}</span>
            </div>
            <div class="card-manufacturer-line">
              <span class="card-label">厂家：</span>
              <span class="card-manufacturer">{{ item.manufacturer || '暂无' }}</span>
            </div>
            <div class="card-stock-line">
              <span class="card-label">库存：</span>
              <span :class="{ 'stock-zero': (item.stock || 0) <= 0, 'stock-value': true }">
                {{ formatStock(item.stock) || 0 }} {{ item.product?.mainUnit || '个' }}
              </span>
            </div>
          </div>
        
          <div v-if="item.type === 'package' && item.package?.items?.length" class="card-package-items">
            <div
              v-for="(pkg, idx) in item.package.items.slice(0, 4)"
              :key="idx"
              class="package-item-line"
            >
              {{ pkg.productName }} × {{ pkg.quantity }}{{ pkg.unit || '' }}
            </div>
            <div v-if="item.package.items.length > 4" class="package-more">+{{ item.package.items.length - 2 }}</div>
              <div class="card-stock-line">
              <span class="card-label">库存：</span>
              <span :class="{ 'stock-zero': (item.stock || 0) <= 0, 'stock-value': true }">
                {{ formatStock(item.stock) || 0 }} 套
              </span>
            </div>
          </div>
          
          <!-- 价格 -->
          <div class="card-footer">
            <div v-if="item.price != null && (typeof item.price === 'number' || typeof item.price === 'string') && Number(item.price) > 0" class="card-price">¥{{ Number(item.price).toFixed(2) }}</div>
            <div v-else class="card-price no-price">价格待定</div>
          </div>
        </div>
      </div>

      <div v-if="filteredGoods.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无商品/套餐" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {Box, Picture, Search} from '@element-plus/icons-vue'
import {getProductList} from '@/api/product'
import {getPackageList} from '@/api/package'
import {getInventoryList} from '@/api/inventory'
import type {ProductDetailVO} from '@/types/product'
import type {ProductPackageDetailVO} from '@/types/package'
import type {InventoryPageVO} from '@/types/inventory'
import type {CartItem, GoodsCard, POSCustomerInfo} from '@/types/pos'
import { formatStock } from '@/utils/number'

const props = defineProps<{
  storeId: number | null
  selectedCustomer?: POSCustomerInfo | null
  selectedCategoryId?: number | null
  categoryTree?: any[] // 分类树，用于多级分类匹配
}>()

const emit = defineEmits<{
  addToCart: [item: CartItem]
  tabChange: [tab: string]
  categorySelect: [categoryId: number | null]
}>()

const searchInputRef = ref<InstanceType<typeof HTMLInputElement> | null>(null)
const searchKeyword = ref('')
const activeTab = ref('all')
const loading = ref(false)

// Tab配置
const tabs = [
  { label: '全部', value: 'all' },
  { label: '单品', value: 'product' },
  { label: '套餐', value: 'package' }
]

// 数据
const products = ref<ProductDetailVO[]>([])
const packages = ref<ProductPackageDetailVO[]>([])
const inventoryMap = ref<Record<number, number>>({})

// 合并后的商品/套餐列表
const allGoods = computed<GoodsCard[]>(() => {
  const goods: GoodsCard[] = []

  // 商品
  products.value.forEach((product) => {
    const stock = inventoryMap.value[product.id] || 0
    // 处理价格：优先使用 suggestedRetailPrice，支持字符串和数字类型
    let price: number = 0
    const parsePrice = (value: any): number => {
      if (value == null) return 0
      if (typeof value === 'number') return value
      if (typeof value === 'string') {
        const parsed = parseFloat(value)
        return isNaN(parsed) ? 0 : parsed
      }
      return 0
    }
    
    const suggestedPrice = parsePrice(product.suggestedRetailPrice)
    if (suggestedPrice > 0) {
      price = suggestedPrice
    } else {
      const vipPrice = parsePrice(product.vipPrice)
      const minPrice = parsePrice(product.minPrice)
      const onlinePrice = parsePrice(product.onlinePrice)
      price = vipPrice || minPrice || onlinePrice || 0
    }
    goods.push({
      id: product.id,
      type: 'product',
      name: product.name,
      imageUrl: product.imageUrl,
      specification: product.specification,
      manufacturer: product.manufacturer,
      price: price,
      stock,
      product
    })
  })

  // 套餐
  packages.value.forEach((pkg) => {
    const parsePrice = (value: any): number => {
      if (value == null) return 0
      if (typeof value === 'number') return value
      if (typeof value === 'string') {
        const parsed = parseFloat(value)
        return isNaN(parsed) ? 0 : parsed
      }
      return 0
    }
    
    let price: number = 0
    const suggestedPrice = parsePrice(pkg.suggestedRetailPrice)
    if (suggestedPrice > 0) {
      price = suggestedPrice
    } else {
      const vipPrice = parsePrice(pkg.vipPrice)
      const minPrice = parsePrice(pkg.minPrice)
      price = vipPrice || minPrice || 0
    }
    
    goods.push({
      id: pkg.id,
      type: 'package',
      name: pkg.name || '未命名套餐',
      stock: pkg.stock || 0, // 套餐库存（由后端计算）
      price: price,
      package: pkg
    })
  })

  return goods
})

// 获取Tab数量
const getTabCount = (tabValue: string) => {
  if (tabValue === 'all') {
    // 全部：显示数据库中所有商品+套餐的总数（不受过滤影响）
    return products.value.length + packages.value.length
  } else if (tabValue === 'package') {
    // 套餐：显示数据库中所有套餐的总数（不受过滤影响）
    return packages.value.length
  } else if (tabValue === 'product') {
    // 单品：如果选择了分类，显示该分类下的单品数量；否则显示所有单品数量
    if (props.selectedCategoryId) {
      // 计算分类下的单品数量
      const getAllCategoryIds = (categories: any[], targetId: number): number[] => {
        const result: number[] = [targetId]
        const findCategory = (cats: any[], id: number): any => {
          for (const cat of cats) {
            if (cat.id === id) return cat
            if (cat.children) {
              const found = findCategory(cat.children, id)
              if (found) return found
            }
          }
          return null
        }
        const category = findCategory(categories, targetId)
        if (category && category.children) {
          const collectIds = (cats: any[]) => {
            cats.forEach(cat => {
              result.push(cat.id)
              if (cat.children) collectIds(cat.children)
            })
          }
          collectIds(category.children)
        }
        return result
      }
      
      let categoryIds: number[] = [props.selectedCategoryId]
      if (props.categoryTree && props.categoryTree.length > 0) {
        categoryIds = getAllCategoryIds(props.categoryTree, props.selectedCategoryId)
      }
      
      return products.value.filter(product => 
        product.categoryId && categoryIds.includes(product.categoryId)
      ).length
    } else {
      // 未选择分类，显示所有单品数量
      return products.value.length
    }
  }
  return 0
}

// 过滤后的列表
const filteredGoods = computed(() => {
  let filtered = allGoods.value

  // 按分类过滤（支持多级分类）
  if (props.selectedCategoryId) {
    // 获取选中分类及其所有子分类的ID列表
    const getAllCategoryIds = (categories: any[], targetId: number): number[] => {
      const result: number[] = [targetId]
      const findCategory = (cats: any[], id: number): any => {
        for (const cat of cats) {
          if (cat.id === id) return cat
          if (cat.children) {
            const found = findCategory(cat.children, id)
            if (found) return found
          }
        }
        return null
      }
      const category = findCategory(categories, targetId)
      if (category && category.children) {
        const collectIds = (cats: any[]) => {
          cats.forEach(cat => {
            result.push(cat.id)
            if (cat.children) collectIds(cat.children)
          })
        }
        collectIds(category.children)
      }
      return result
    }
    
    // 如果有分类树，使用多级匹配；否则只匹配当前分类ID
    let categoryIds: number[] = [props.selectedCategoryId]
    if (props.categoryTree && props.categoryTree.length > 0) {
      categoryIds = getAllCategoryIds(props.categoryTree, props.selectedCategoryId)
    }
    
    filtered = filtered.filter((item) => {
      if (item.type === 'product' && item.product && item.product.categoryId) {
        // 匹配分类ID或其子分类ID
        return categoryIds.includes(item.product.categoryId)
      }
      // 套餐没有分类，在分类模式下不显示
      return false
    })
  }

  // 按类型过滤
  if (activeTab.value === 'product') {
    filtered = filtered.filter((item) => item.type === 'product')
  } else if (activeTab.value === 'package') {
    filtered = filtered.filter((item) => item.type === 'package')
  }

  // 按关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    filtered = filtered.filter((item) => {
      return (
        item.name.toLowerCase().includes(keyword) ||
        (item.type === 'product' && item.product?.code?.toLowerCase().includes(keyword)) ||
        (item.type === 'package' && item.package?.code?.toLowerCase().includes(keyword))
      )
    })
  }

  // 库存优先排序：先按是否有库存，其次按名称
  filtered = [...filtered].sort((a, b) => {
    const stockA = (a.stock || 0)
    const stockB = (b.stock || 0)

    if (stockA > 0 && stockB <= 0) return -1
    if (stockA <= 0 && stockB > 0) return 1

    return a.name.localeCompare(b.name)
  })

  return filtered
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const [productRes, packageRes] = await Promise.all([
      getProductList({
        status: 1,
        pageNum: 1,
        pageSize: 1000
        // 线下 POS：展示正常商品，不受商城上架状态限制
      }),
      getPackageList({
        status: 1,
        pageNum: 1,
        pageSize: 1000,
        storeId: props.storeId || undefined
        // 线下 POS：只展示已上架套餐
        // 传递 storeId 用于计算套餐库存
      })
    ])

    products.value = productRes.records || []
    packages.value = packageRes.records || []

    // 如果有门店，则加载库存
    if (props.storeId) {
      await loadInventory()
    }
  } catch (error) {
    console.error('加载商品/套餐失败:', error)
    ElMessage.error('加载商品/套餐失败')
  } finally {
    loading.value = false
  }
}

// 加载库存
const loadInventory = async () => {
  if (!props.storeId) return

  try {
    const inventoryRes = await getInventoryList({
      storeId: props.storeId,
      pageNum: 1,
      pageSize: 10000
    })

    const map: Record<number, number> = {}
    inventoryRes.records.forEach((item: InventoryPageVO) => {
      if (item.productId) {
        map[item.productId] = (item.totalQuantity || 0) as number
      }
    })
    inventoryMap.value = map
  } catch (error) {
    console.error('加载库存失败:', error)
  }
}

// 扫码/搜索解析结果（编码类型：商品编码 / 内部批次标签 / 货位编码）
const BATCH_LABEL_PREFIX = 'INTERNAL-RECEIPT-'
const scanResult = ref<{
  type: 'SKU' | 'BATCH' | 'LOCATION'
  typeText: string
  text: string
} | null>(null)

const handleKeywordInput = () => {
  // 手动修改关键词后清除上一次扫码解析结果
  if (scanResult.value) {
    scanResult.value = null
  }
}

// 搜索/扫码：SKU 优先，商品编码不会打开批次选择
const handleSearch = () => {
  const code = (searchKeyword.value || '').trim()
  if (!code) {
    scanResult.value = null
    return
  }

  // 内部批次标签（后仓/取货使用）：POS 按商品编码销售，不做批次选择
  if (code.toUpperCase().startsWith(BATCH_LABEL_PREFIX)) {
    scanResult.value = {
      type: 'BATCH',
      typeText: '批次标签',
      text: '内部批次标签在 POS 不作为销售编码，请扫描商品编码（SKU）；批次/货位请在「取货」工作台确认。'
    }
    searchKeyword.value = ''
    ElMessage.warning('识别为内部批次标签：POS 按商品编码销售，请扫描商品编码')
    return
  }

  const matched = filteredGoods.value
  if (matched.length === 1) {
    scanResult.value = {
      type: 'SKU',
      typeText: '商品编码（SKU）',
      text: `已定位：${matched[0].name}，点击商品卡片加入购物车（无需选择批次）`
    }
    return
  }
  if (matched.length === 0) {
    scanResult.value = {
      type: 'SKU',
      typeText: '商品编码（SKU）',
      text: '未匹配到商品，请核对编码或改用商品名称搜索。'
    }
    ElMessage.warning('未找到匹配的商品编码')
    return
  }
  scanResult.value = {
    type: 'SKU',
    typeText: '商品编码（SKU）',
    text: `匹配到 ${matched.length} 个商品，请选择商品卡片加入购物车。`
  }
}

// Tab 切换
const handleTabChange = (tabValue: string) => {
  activeTab.value = tabValue
  emit('tabChange', tabValue)
  
  // 如果切换到"全部"或"套餐"，自动取消分类选择（因为套餐不需要分类）
  if (tabValue === 'all' || tabValue === 'package') {
    if (props.selectedCategoryId !== null) {
      emit('categorySelect', null)
    }
  }
}

// 切换到单品Tab（供外部调用）
const switchToProductTab = () => {
  if (activeTab.value === 'all' || activeTab.value === 'package') {
    activeTab.value = 'product'
    emit('tabChange', 'product')
  }
}

// 卡片点击
const handleCardClick = (item: GoodsCard) => {
  // 商品/套餐库存为0时不可点击
  if ((item.type === 'product' || item.type === 'package') && (item.stock || 0) <= 0) {
    ElMessage.warning(item.type === 'package' ? '该套餐库存不足' : '该商品库存不足')
    return
  }

  // 添加到购物车
  if (item.type === 'product' && item.product) {
    // 单品：直接添加
    const cartItem: CartItem = {
      id: '',
      itemType: 1,
      productId: item.product.id,
      name: item.product.name,
      specification: item.product.specification,
      manufacturer: item.product.manufacturer,
      imageUrl: item.product.imageUrl,
      mainUnit: item.product.mainUnit,
      auxUnit: item.product.auxUnit,
      mainToAuxRatio: item.product.mainToAuxRatio,
      allowSpecialFund: item.product.allowSpecialFund,
      currentUnit: item.product.auxUnit || item.product.mainUnit || '个',
      unitPrice: item.price, // 初始价格，会在 handleAddToCart 中根据客户类型重新计算
      quantity: 1,
      subtotal: item.price,
      stock: item.stock
    }
    // 保存商品信息以便后续价格计算（使用类型断言）
    ;(cartItem as any)._product = item.product
    emit('addToCart', cartItem)
  } else if (item.type === 'package' && item.package) {
    // 套餐：炸开为多个商品行
    explodePackageToCart(item)
  }
}

/**
 * 将套餐"炸开"为多个商品行添加到购物车
 * 核心逻辑：
 * 1. 遍历套餐的所有子商品
 * 2. 为每个子商品创建独立的购物车行
 * 3. 数量 = 套餐数量(1) × 子商品数量
 * 4. 单位 = 套餐配置的单位（保持原样，如"瓶"）
 * 5. 价格 = 按比例分摊套餐总价
 */
const explodePackageToCart = (packageItem: GoodsCard) => {
  if (!packageItem.package || !packageItem.package.items || packageItem.package.items.length === 0) {
    ElMessage.warning('套餐配置异常，无法添加到购物车')
    return
  }

  const packageDetail = packageItem.package
  const packagePrice = packageItem.price // 套餐总价
  const packageItems = packageDetail.items
  
  // 计算套餐原总价（用于价格分摊）
  let originalTotalPrice = 0
  const productPrices: Record<number, number> = {}
  
  packageItems.forEach(item => {
    const product = products.value.find(p => p.id === item.productId)
    if (product) {
      // 获取商品单价（优先使用建议零售价）
      const productPrice = product.suggestedRetailPrice || product.vipPrice || product.minPrice || 0
      productPrices[item.productId] = productPrice
      originalTotalPrice += productPrice * (item.quantity || 0)
    }
  })
  
  // 如果无法计算原总价，则按商品数量平均分摊
  const useAveragePrice = originalTotalPrice === 0
  const averagePrice = useAveragePrice ? packagePrice / packageItems.length : 0
  
  // 遍历套餐子商品，为每个创建购物车行
  let successCount = 0
  packageItems.forEach(item => {
    const product = products.value.find(p => p.id === item.productId)
    if (!product) {
      console.warn(`套餐中的商品 ${item.productId} 不存在，跳过`)
      return
    }
    
    // 计算分摊价格
    let allocatedPrice: number
    if (useAveragePrice) {
      // 平均分摊
      allocatedPrice = averagePrice
    } else {
      // 按原价比例分摊
      const productOriginalPrice = productPrices[item.productId] * (item.quantity || 0)
      allocatedPrice = (productOriginalPrice / originalTotalPrice) * packagePrice
    }
    
    // 计算单价（分摊价格 / 数量）
    const unitPrice = (item.quantity || 0) > 0 ? allocatedPrice / (item.quantity || 1) : allocatedPrice
    
    // 创建购物车行
    const cartItem: CartItem = {
      id: '',
      itemType: 1, // 重要：现在都是商品类型
      productId: product.id,
      packageId: packageDetail.id,
      name: product.name,
      specification: product.specification,
      manufacturer: product.manufacturer,
      imageUrl: product.imageUrl,
      mainUnit: product.mainUnit,
      auxUnit: product.auxUnit,
      mainToAuxRatio: product.mainToAuxRatio,
      allowSpecialFund: product.allowSpecialFund,
      currentUnit: item.unit || product.mainUnit || '个', // 使用套餐配置的单位
      unitPrice: unitPrice,
      quantity: item.quantity || 1, // 套餐中配置的数量
      subtotal: allocatedPrice,
      stock: inventoryMap.value[product.id] || 0,
      // 标记这是来自套餐的商品（可选，用于UI显示）
      fromPackage: packageDetail.name || '套餐'
    }
    
    // 保存商品信息以便后续价格计算
    ;(cartItem as any)._product = product
    
    emit('addToCart', cartItem)
    successCount++
  })
  
  if (successCount > 0) {
    ElMessage.success(`已将套餐"${packageDetail.name || '套餐'}"的 ${successCount} 个商品添加到购物车`)
  } else {
    ElMessage.error('套餐商品添加失败')
  }
}

// 图片加载错误
const handleImageError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

// 聚焦搜索框
const focusSearch = () => {
  searchInputRef.value?.focus()
}

// 刷新
const refresh = () => {
  loadData()
}

watch(
  () => props.storeId,
  (val, oldVal) => {
    if (val && val !== oldVal) {
      // storeId变化时，重新加载所有数据（包括套餐库存）
      loadData()
    }
  }
)

onMounted(() => {
  // 首次加载数据
  loadData()
})

// 根据ID获取商品信息（供外部调用）
const getProductById = (productId: number) => {
  return products.value.find(p => p.id === productId)
}

defineExpose({
  focusSearch,
  refresh,
  switchToProductTab,
  getProductById
})
</script>

<style scoped lang="scss">
.modern-goods-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f8fafc;
}

.goods-header {
  background: #fff;
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;

  .scan-field-label {
    display: block;
    margin-bottom: 4px;
    font-size: 12px;
    color: #6b7280;
  }

  .search-input {
    margin-bottom: 12px;
  }

  .scan-result {
    display: flex;
    gap: 8px;
    margin: -4px 0 12px;
    font-size: 12px;
    line-height: 1.5;
    color: #6b7280;

    .scan-result-type {
      flex-shrink: 0;
      font-weight: 600;
      color: #409eff;
    }

    &.scan-result-batch .scan-result-type {
      color: #e6a23c;
    }
  }
  
  .goods-tabs {
    display: flex;
    gap: 8px;
    
    .tab-item {
      padding: 8px 16px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
      font-size: 14px;
      color: #6b7280;
      
      &:hover {
        background: #f3f4f6;
        color: #2563eb;
      }
      
      &.active {
        background: #eff6ff;
        color: #2563eb;
        font-weight: 500;
      }
      
      .tab-count {
        font-size: 12px;
        opacity: 0.7;
      }
    }
  }
}

.goods-grid {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
  align-content: start;
}

.goods-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden; // 保持 hidden 以维持圆角效果
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  min-height: 320px; // 增加最小高度，确保有足够空间显示价格
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }
  
  &.out-of-stock {
    opacity: 0.6;
    cursor: not-allowed;
    
    &:hover {
      transform: none;
    }
  }
  
  &.is-package {
    border: 2px solid #f59e0b;
  }
}

.card-image-wrapper {
  position: relative;
  width: 100%;
  padding-top: 75%; // 4:3 比例
  background: #f3f4f6;
  overflow: hidden;
  
  .card-image {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  
  .card-image-placeholder {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #9ca3af;
  }
  
  .package-badge {
    position: absolute;
    top: 8px;
    right: 8px;
    background: #f59e0b;
    color: #fff;
    font-size: 11px;
    padding: 4px 8px;
    border-radius: 4px;
    font-weight: 500;
  }
  
  .out-of-stock-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 14px;
    font-weight: 500;
  }
}

.card-info {
  padding: 12px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0; // 确保flex子元素可以收缩
  
  .card-name {
    font-size: 14px;
    font-weight: 500;
    color: #1f2937;
    margin-bottom: 8px;
    line-height: 1.4;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    flex-shrink: 0;
  }
  
  .card-product-details {
    margin-bottom: 8px;
    font-size: 12px;
    line-height: 1.6;
    flex-shrink: 0;
    
    .card-spec-line,
    .card-manufacturer-line,
    .card-stock-line {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-bottom: 4px;
      
      &:last-child {
        margin-bottom: 0;
      }
    }
    
    .card-label {
      color: #9ca3af;
      flex-shrink: 0;
    }
    
    .card-spec {
      color: #6b7280;
    }
    
    .card-manufacturer {
      color: #6b7280;
    }
    
    .stock-value {
      color: #6b7280;
      
      &.stock-zero {
        color: #ef4444;
        font-weight: 500;
      }
    }
  }
  
  .card-package-items {
    margin-bottom: 8px;
    font-size: 12px;
    color: #6b7280;
    flex-shrink: 0;
    
    .package-item-line {
      line-height: 1.5;
    }
    
    .package-more {
      color: #9ca3af;
      margin-top: 4px;
    }
  }
  
  .card-footer {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    margin-top: auto; // 使用 auto 将价格推到底部
    padding-top: 8px;
    border-top: 1px solid #e5e7eb;
    flex-shrink: 0; // 防止价格区域被压缩
    
    .card-price {
      font-size: 16px;
      font-weight: 600;
      color: #2563eb;
      white-space: nowrap; // 防止价格换行
      
      &.no-price {
        font-size: 13px;
        color: #9ca3af;
        font-weight: 400;
      }
    }
  }
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
</style>

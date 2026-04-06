<template>
  <div class="goods-panel">
    <!-- 顶部栏 -->
    <div class="goods-header">
      <el-input
        ref="searchInputRef"
        v-model="searchKeyword"
        placeholder="搜索商品/套餐（支持拼音、名称、条码）"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="商品" name="product" />
        <el-tab-pane label="套餐" name="package" />
      </el-tabs>
    </div>

    <!-- 商品/套餐网格 -->
    <div class="goods-grid" v-loading="loading">
      <div
        v-for="item in filteredGoods"
        :key="`${item.type}_${item.id}`"
        class="goods-card"
        :class="{ disabled: item.type === 'product' && (item.stock || 0) <= 0 }"
        @click="handleCardClick(item)"
      >
        <!-- 商品卡片 -->
        <template v-if="item.type === 'product'">
          <div class="card-image">
            <img
              v-if="item.imageUrl"
              :src="item.imageUrl"
              :alt="item.name"
              @error="handleImageError"
            />
            <div v-else class="no-image">无图片</div>
          </div>
          <div class="card-info">
            <div class="card-name">{{ item.name }}</div>
            <div class="card-spec" v-if="item.specification">
              规格：{{ item.specification }}
            </div>
            <div class="card-manufacturer" v-if="item.manufacturer">
              厂家：{{ item.manufacturer }}
            </div>
            <div class="card-stock">
              库存：<span :class="{ 'stock-zero': (item.stock || 0) <= 0 }">
                {{ formatStock(item.stock) || 0 }} {{ item.product?.mainUnit || '' }}
              </span>
            </div>
            <div class="card-price">¥{{ item.price.toFixed(2) }}</div>
          </div>
        </template>

        <!-- 套餐卡片 -->
        <template v-else>
          <div class="card-icon">📦</div>
          <div class="card-info">
            <div class="card-name">{{ item.name }}</div>
            <div class="card-spec pkg-spec" v-if="item.package?.items?.length">
              <div class="pkg-line" v-for="pkg in item.package.items.slice(0, 3)" :key="pkg.productId">
                {{ pkg.productName || '' }} × {{ pkg.quantity }}{{ pkg.unit || '' }}
              </div>
              <div v-if="item.package.items.length > 3" class="pkg-more">…</div>
            </div>
            <div class="card-price">¥{{ item.price.toFixed(2) }}</div>
          </div>
        </template>
      </div>

      <div v-if="filteredGoods.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无商品/套餐" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import { formatStock } from '@/utils/number'
import {ElMessage} from 'element-plus'
import {Search} from '@element-plus/icons-vue'
import {getProductList} from '@/api/product'
import {getPackageList} from '@/api/package'
import {getInventoryList} from '@/api/inventory'
import type {ProductDetailVO} from '@/types/product'
import type {ProductPackageDetailVO} from '@/types/package'
import type {InventoryPageVO} from '@/types/inventory'
import type {CartItem, GoodsCard, POSCustomerInfo} from '@/types/pos'

const props = defineProps<{
  storeId: number | null
  selectedCustomer?: POSCustomerInfo | null
}>()

const emit = defineEmits<{
  addToCart: [item: CartItem]
}>()

const searchInputRef = ref<InstanceType<typeof HTMLInputElement> | null>(null)
const searchKeyword = ref('')
const activeTab = ref('all')
const loading = ref(false)

// 数据
const products = ref<ProductDetailVO[]>([])
const packages = ref<ProductPackageDetailVO[]>([])
const inventoryMap = ref<Record<number, number>>({}) // productId -> quantity

// 合并后的商品/套餐列表
const allGoods = computed<GoodsCard[]>(() => {
  const goods: GoodsCard[] = []

  // 商品
  products.value.forEach((product) => {
    const stock = inventoryMap.value[product.id] || 0
    goods.push({
      id: product.id,
      type: 'product',
      name: product.name,
      imageUrl: product.imageUrl,
      specification: product.specification,
      manufacturer: product.manufacturer,
      price: product.suggestedRetailPrice || 0,
      stock,
      product
    })
  })

  // 套餐
  packages.value.forEach((pkg) => {
    goods.push({
      id: pkg.id,
      type: 'package',
      name: pkg.name,
      price: pkg.suggestedRetailPrice || 0,
      package: pkg
    })
  })

  return goods
})

// 过滤后的列表
const filteredGoods = computed(() => {
  let filtered = allGoods.value

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
    const stockA = a.type === 'product' ? (a.stock || 0) : 0
    const stockB = b.type === 'product' ? (b.stock || 0) : 0

    // 有库存的排前面
    if (stockA > 0 && stockB <= 0) return -1
    if (stockA <= 0 && stockB > 0) return 1

    // 都有库存或都无库存时按名称排序
    return a.name.localeCompare(b.name)
  })

  return filtered
})

// 加载数据
const loadData = async () => {
  // 没有门店时也加载商品/套餐，库存为空；门店就绪后再拉库存
  loading.value = true
  try {
    const [productRes, packageRes] = await Promise.all([
      getProductList({
        pageNum: 1,
        pageSize: 1000
        // 线下 POS：展示所有商品，不受上架状态限制
      }),
      getPackageList({
        pageNum: 1,
        pageSize: 1000
        // 线下 POS：展示所有套餐，不受状态限制
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

// 搜索
const handleSearch = () => {
  // 搜索逻辑已在 computed 中实现
}

// Tab 切换
const handleTabChange = () => {
  // Tab 切换逻辑已在 computed 中实现
}

// 卡片点击
const handleCardClick = (item: GoodsCard) => {
  // 商品库存为0时不可点击
  if (item.type === 'product' && (item.stock || 0) <= 0) {
    ElMessage.warning('该商品库存不足')
    return
  }

  // 添加到购物车
  if (item.type === 'product' && item.product) {
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
      unitPrice: item.price,
      quantity: 1,
      subtotal: item.price,
      stock: item.stock
    }
    emit('addToCart', cartItem)
  } else if (item.type === 'package' && item.package) {
    const cartItem: CartItem = {
      id: '',
      itemType: 2,
      packageId: item.package.id,
      name: item.package.name,
      currentUnit: '套',
      unitPrice: item.price,
      quantity: 1,
      subtotal: item.price,
      packagePrice: item.package.suggestedRetailPrice,
      vipPrice: item.package.vipPrice,
      packageItems: item.package.items || []
    }
    emit('addToCart', cartItem)
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

// 监听客户变化，重新计算价格（TODO: 实现价格策略）
watch(
  () => props.selectedCustomer,
  () => {
    // 当客户变化时，可以重新计算价格
    // 这里暂时不实现，由父组件处理
  }
)

watch(
  () => props.storeId,
  (val, oldVal) => {
    if (val && val !== oldVal) {
      loadInventory()
    }
  }
)

onMounted(() => {
  loadData()
})

defineExpose({
  focusSearch,
  refresh
})
</script>

<style scoped lang="scss">
.goods-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.goods-header {
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  gap: 12px;

  .el-input {
    width: 100%;
  }
}

.goods-grid {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  align-content: start; /* 关键：内容靠上对齐，避免拉伸 */
}

.goods-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
  height: 260px; /* 固定高度，避免不均匀拉伸 */

  &:hover:not(.disabled) {
    border-color: #409eff;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
  }

  &.disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .card-image {
    width: 100%;
    height: 120px;
    background: #f5f5f5;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 8px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .no-image {
      color: #999;
      font-size: 12px;
    }
  }

  .card-icon {
    font-size: 48px;
    text-align: center;
    margin-bottom: 8px;
  }

  .card-info {
    .card-name {
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-spec,
    .card-manufacturer {
      font-size: 12px;
      color: #666;
      margin-bottom: 2px;
    }

    .card-stock {
      font-size: 12px;
      color: #666;
      margin-bottom: 4px;

      .stock-zero {
        color: #f56c6c;
      }
    }

    .card-price {
      font-size: 16px;
      font-weight: 600;
      color: #f56c6c;
      margin-top: 4px;
    }

    .pkg-spec {
      margin-top: 6px;
      color: #666;
      font-size: 12px;
      line-height: 16px;
      max-height: 48px;
      overflow: hidden;

      .pkg-line {
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
      }

      .pkg-more {
        color: #999;
      }
    }
  }
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}
</style>


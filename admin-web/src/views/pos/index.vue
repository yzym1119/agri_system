<template>
  <div class="pos-page">
    <div class="pos-container">
      <!-- 分类栏 -->
      <CategorySidebar
        ref="categorySidebarRef"
        @category-change="handleCategoryChange"
        @category-tree-change="categoryTree = $event"
      />

      <!-- 中间：商品网格 -->
      <div class="pos-goods-panel">
        <ModernGoodsPanel
          ref="goodsPanelRef"
          :store-id="storeId"
          :selected-customer="selectedCustomer"
          :selected-category-id="selectedCategoryId"
          :category-tree="categoryTree"
          @add-to-cart="handleAddToCart"
          @tab-change="handleTabChange"
          @category-select="handleCategorySelect"
        />
      </div>

      <!-- 右侧：购物车和结算 -->
      <div class="pos-cart-panel">
        <div class="cart-wrapper">
          <ModernCartList
            ref="cartListRef"
            :cart-items="cartItems"
            :selected-customer="selectedCustomer"
            :store-id="storeId"
            @update="handleCartUpdate"
            @customer-change="handleCustomerChange"
          />
        </div>
        
        <div class="settlement-wrapper">
          <ModernSettlementBar
            ref="settlementBarRef"
            :cart-items="cartItems"
            :total-amount="totalAmount"
            :customer="selectedCustomer"
            @checkout="handleCheckout"
            @clear-cart="handleClearCart"
            @restore-cart="handleRestoreCart"
          />
        </div>
      </div>
    </div>

    <!-- 结算弹窗 -->
    <CheckoutDialog
      v-model="checkoutVisible"
      :total-amount="totalAmount"
      :discount-amount="checkoutData.discountAmount"
      :checkout-data="checkoutData"
      :cart-items="cartItems"
      :customer="selectedCustomer"
      :submitting="submittingOrder"
      @submit="handleSubmitOrder"
    />
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import { getProductPriceRules } from '@/api/product'
import {useUserStore} from '@/store/user'
import CategorySidebar from './components/CategorySidebar.vue'
import ModernGoodsPanel from './components/ModernGoodsPanel.vue'
import ModernCartList from './components/ModernCartList.vue'
import ModernSettlementBar from './components/ModernSettlementBar.vue'
import CheckoutDialog from './components/CheckoutDialog.vue'
import type {CartItem, POSCustomerInfo} from '@/types/pos'
import type { ProductDetailVO, ProductPriceRuleVO } from '@/types/product'
import { resolvePackageUnitPrice, resolveProductCartUnitPrice } from '@/utils/posPricing'

const userStore = useUserStore()

// 基础数据 - 立即从用户信息中获取
const getUserStoreId = () => {
  if (userStore.userInfo) {
    const extra = userStore.userInfo.extra as any
    return extra?.storeId || null
  }
  return null
}

const storeId = ref<number | null>(getUserStoreId())
const staffId = ref<number | null>(userStore.userInfo?.id || null)
const selectedCustomer = ref<POSCustomerInfo | null>(null)

// 购物车
const cartItems = ref<CartItem[]>([])

// 组件引用
const goodsPanelRef = ref<InstanceType<typeof ModernGoodsPanel> | null>(null)
const categorySidebarRef = ref<InstanceType<typeof CategorySidebar> | null>(null)
const cartListRef = ref<InstanceType<typeof ModernCartList> | null>(null)
const settlementBarRef = ref<InstanceType<typeof ModernSettlementBar> | null>(null)
const productPriceRuleCache = new Map<number, ProductPriceRuleVO[]>()

// 选中的分类
const selectedCategoryId = ref<number | null>(null)
const categoryTree = ref<any[]>([])

// 结算弹窗
const checkoutVisible = ref(false)
const submittingOrder = ref(false)
const checkoutData = ref<{
  paymentType: number
  discountAmount: number
  walletAuthCode?: string
  useSpecialFund?: boolean
}>({
  paymentType: 1,
  discountAmount: 0
})

// 计算总金额
const totalAmount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + item.subtotal, 0)
})

// 初始化
onMounted(async () => {
  if (!storeId.value || !staffId.value) {
    ElMessage.error('未获取到门店或员工信息，请重新登录')
    return
  }
})

// 快捷键支持
const isTypingTarget = (target: EventTarget | null) => {
  const element = target as HTMLElement | null
  if (!element) return false
  const tagName = element.tagName
  return element.isContentEditable || tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT'
}

const handleKeydown = (e: KeyboardEvent) => {
  const typingTarget = isTypingTarget(e.target)

  // F1: 聚焦搜索
  if (e.key === 'F1') {
    e.preventDefault()
    goodsPanelRef.value?.focusSearch()
  }
  // Space: 加入购物车（在商品选择区时）
  if (e.key === ' ' && !typingTarget) {
    e.preventDefault()
    // 由 GoodsPanel 内部处理
  }
  // Ctrl+Enter: 结算
  if (e.key === 'Enter' && e.ctrlKey && !typingTarget) {
    e.preventDefault()
    if (cartItems.value.length > 0) {
      // 触发结算栏的收款按钮
      settlementBarRef.value?.triggerCheckout()
    }
  }
  // F2: 客户选择（预留）
  if (e.key === 'F2') {
    e.preventDefault()
    cartListRef.value?.focusCustomerSearch()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})

const getProductSnapshot = (item: CartItem): ProductDetailVO | null => {
  if (!item.productId) return null
  return (item as any)._product || goodsPanelRef.value?.getProductById?.(item.productId) || null
}

const getCachedProductPriceRules = async (productId: number): Promise<ProductPriceRuleVO[]> => {
  if (productPriceRuleCache.has(productId)) {
    return productPriceRuleCache.get(productId) || []
  }
  try {
    const rules = await getProductPriceRules(productId)
    productPriceRuleCache.set(productId, rules || [])
    return rules || []
  } catch (error) {
    console.error(`获取商品 ${productId} 价格规则失败:`, error)
    return []
  }
}

const resolveCartItemUnitPrice = async (item: CartItem): Promise<number | null> => {
  if (item.itemType === 1 && item.productId) {
    return resolveProductCartUnitPrice({
      product: getProductSnapshot(item),
      rules: await getCachedProductPriceRules(item.productId),
      storeId: storeId.value,
      customerType: selectedCustomer.value?.type,
      vipLevel: selectedCustomer.value?.vipLevel,
      currentUnit: item.currentUnit,
      fallbackPrice: item.unitPrice
    })
  }

  if (item.itemType === 2) {
    return resolvePackageUnitPrice({
      customer: selectedCustomer.value,
      packagePrice: item.packagePrice,
      vipPrice: item.vipPrice,
      fallbackPrice: item.unitPrice
    })
  }

  return item.unitPrice
}

// 添加商品到购物车
const handleAddToCart = async (item: CartItem) => {
  const resolvedPrice = await resolveCartItemUnitPrice(item)
  if (resolvedPrice !== null) {
    item.unitPrice = resolvedPrice
    item.subtotal = item.quantity * item.unitPrice
  }

  // 检查是否已存在
  const existingIndex = cartItems.value.findIndex(
    (cartItem) =>
      cartItem.itemType === item.itemType &&
      ((item.itemType === 1 && cartItem.productId === item.productId) ||
        (item.itemType === 2 && cartItem.packageId === item.packageId)) &&
      cartItem.currentUnit === item.currentUnit
  )

  if (existingIndex >= 0) {
    // 已存在，增加数量
    cartItems.value[existingIndex].quantity += item.quantity
    cartItems.value[existingIndex].subtotal =
      cartItems.value[existingIndex].quantity * cartItems.value[existingIndex].unitPrice
  } else {
    // 新增
    cartItems.value.push({
      ...item,
      id: `${item.itemType}_${item.productId || item.packageId}_${Date.now()}`
    })
  }
}

// 重新计算购物车中所有商品的价格
const recalculateCartPrices = async () => {
  if (!cartItems.value || cartItems.value.length === 0) return
  
  const updatedItems = [...cartItems.value]
  let hasChanges = false

  for (const item of updatedItems) {
    const newPrice = await resolveCartItemUnitPrice(item)
    if (newPrice !== null && Math.abs(newPrice - item.unitPrice) > 0.01) {
      item.unitPrice = newPrice
      item.subtotal = item.quantity * item.unitPrice
      hasChanges = true
    }
  }

  if (hasChanges) {
    cartItems.value = updatedItems
  }
}

// 客户变更
const handleCustomerChange = async (customer: POSCustomerInfo | null) => {
  selectedCustomer.value = customer
  // 重新计算购物车中所有商品的价格
  await recalculateCartPrices()
}

// 购物车更新
const handleCartUpdate = (items: CartItem[]) => {
  cartItems.value = items
}

// 分类变更
const handleCategoryChange = (categoryId: number | null) => {
  selectedCategoryId.value = categoryId
  // 如果选择了分类，且当前Tab是"全部"或"套餐"，自动切换到"单品"
  if (categoryId !== null) {
    // 通过事件通知 ModernGoodsPanel 切换Tab
    goodsPanelRef.value?.switchToProductTab()
  }
}

// Tab变更
const handleTabChange = (tab: string) => {
  // 如果切换到"全部"或"套餐"，自动取消分类选择
  if (tab === 'all' || tab === 'package') {
    if (selectedCategoryId.value !== null) {
      selectedCategoryId.value = null
      categorySidebarRef.value?.clearCategory()
    }
  }
}

// 分类选择（从ModernGoodsPanel触发）
const handleCategorySelect = (categoryId: number | null) => {
  selectedCategoryId.value = categoryId
}

// 恢复挂单
const handleRestoreCart = (payload: { cartItems: CartItem[]; customer: POSCustomerInfo | null; discountAmount: number }) => {
  cartItems.value = payload.cartItems
  selectedCustomer.value = payload.customer
  // 设置优惠金额
  settlementBarRef.value?.setDiscountAmount(payload.discountAmount)
}

// 清空购物车（挂单后）
const handleClearCart = () => {
  cartItems.value = []
  selectedCustomer.value = null
}

// 结算
const handleCheckout = (data: {
  paymentType: number
  discountAmount: number
  walletAuthCode?: string
  useSpecialFund?: boolean
}) => {
  if (cartItems.value.length === 0) {
    ElMessage.warning('购物车为空')
    return
  }
  checkoutData.value = data
  checkoutVisible.value = true
}

// 提交订单
const handleSubmitOrder = async (orderData: any) => {
  if (submittingOrder.value) {
    return // 防止重复提交
  }

  submittingOrder.value = true

  try {
    const { createPOSOrder } = await import('@/api/order')
    await createPOSOrder({
      ...orderData,
      storeId: storeId.value!,
      staffId: staffId.value!
    })

    ElMessage.success('订单创建成功')
    
    // 先关闭弹窗，避免状态混乱
    checkoutVisible.value = false
    
    // 清空购物车
    cartItems.value = []
    selectedCustomer.value = null

    // 刷新商品列表（更新库存）
    goodsPanelRef.value?.refresh()
  } catch (error: any) {
    console.error('订单创建失败:', error)
    const errorMessage = error?.response?.data?.message || error?.message || '未知错误'
    ElMessage.error('订单创建失败：' + errorMessage)
    // 失败时不关闭弹窗，让用户重试
  } finally {
    // 确保状态重置，即使出错也要重置
    submittingOrder.value = false
  }
}
</script>

<style scoped lang="scss">
.pos-page {
  height: calc(100vh - 100px);
  padding: 0;
  box-sizing: border-box;
  overflow: hidden; // 整个页面不滚动
  background: #f8fafc;
  display: flex;
  flex-direction: column;
}

.pos-container {
  height: 100%;
  display: flex;
  overflow: hidden;
}


.pos-goods-panel {
  flex: 1;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pos-cart-panel {
  width: 320px;
  background: #f8fafc;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}

.cart-wrapper {
  flex: 1;
  min-height: 0; // 允许flex子元素收缩
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.settlement-wrapper {
  flex-shrink: 0; // 防止结算栏被压缩
  background: #fff;
  border-top: 1px solid #e5e7eb;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  z-index: 10;
}
</style>

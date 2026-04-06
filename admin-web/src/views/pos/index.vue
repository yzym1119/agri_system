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
import {useUserStore} from '@/store/user'
import CategorySidebar from './components/CategorySidebar.vue'
import ModernGoodsPanel from './components/ModernGoodsPanel.vue'
import ModernCartList from './components/ModernCartList.vue'
import ModernSettlementBar from './components/ModernSettlementBar.vue'
import CheckoutDialog from './components/CheckoutDialog.vue'
import type {CartItem, POSCustomerInfo} from '@/types/pos'

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
const settlementBarRef = ref<InstanceType<typeof ModernSettlementBar> | null>(null)

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
const handleKeydown = (e: KeyboardEvent) => {
  // F1: 聚焦搜索
  if (e.key === 'F1') {
    e.preventDefault()
    goodsPanelRef.value?.focusSearch()
  }
  // Space: 加入购物车（在商品选择区时）
  if (e.key === ' ' && !e.target || (e.target as HTMLElement).tagName !== 'INPUT') {
    e.preventDefault()
    // 由 GoodsPanel 内部处理
  }
  // Ctrl+Enter: 结算
  if (e.key === 'Enter' && e.ctrlKey) {
    e.preventDefault()
    if (cartItems.value.length > 0) {
      // 触发结算栏的收款按钮
      settlementBarRef.value?.triggerCheckout()
    }
  }
  // F2: 客户选择（预留）
  if (e.key === 'F2') {
    e.preventDefault()
    // TODO: 聚焦客户搜索框
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})

// 添加商品到购物车
const handleAddToCart = async (item: CartItem) => {
  // 如果是商品，需要根据客户类型和VIP等级获取正确的价格
  if (item.itemType === 1 && item.productId) {
    let finalPrice: number | null = null
    
    // 1. 优先从价格规则中获取价格
    if (selectedCustomer.value) {
      try {
        const { getProductPriceRules } = await import('@/api/product')
        const priceRules = await getProductPriceRules(item.productId)
        console.log(`[价格计算] 商品ID: ${item.productId}, 价格规则数量: ${priceRules?.length || 0}`, priceRules)
        
        const matchedPrice = matchPrice(
          priceRules,
          storeId.value,
          selectedCustomer.value.type,
          selectedCustomer.value.vipLevel || null
        )
        
        console.log(`[价格计算] 匹配到的价格: ${matchedPrice}, 门店ID: ${storeId.value}, 客户类型: ${selectedCustomer.value.type}, VIP等级: ${selectedCustomer.value.vipLevel}`)
        
        if (matchedPrice !== null) {
          finalPrice = matchedPrice
        }
      } catch (error) {
        console.error('获取商品价格规则失败:', error)
      }
    }
    
    // 2. 如果价格规则没有匹配到，回退到商品表的VIP价格（如果是VIP客户）
    if (finalPrice === null && selectedCustomer.value?.type === 2) {
      // 尝试从 item 的临时字段获取
      const product = (item as any)._product || goodsPanelRef.value?.getProductById?.(item.productId)
      if (product?.vipPrice) {
        const vipPrice = typeof product.vipPrice === 'number' ? product.vipPrice : parseFloat(String(product.vipPrice))
        if (!isNaN(vipPrice) && vipPrice > 0) {
          finalPrice = vipPrice
        }
      }
    }
    
    // 3. 如果还是没有价格，使用传入的 item.price（零售价）
    if (finalPrice === null) {
      finalPrice = item.unitPrice
    }
    
    // 应用价格（考虑单位转换）
    // 价格存储规则：
    // 1. 有辅单位的商品：零售价是辅单位的价格（最小单位）
    // 2. 没有辅单位的商品：零售价是主单位的价格
    if (finalPrice !== null) {
      // 如果商品有辅单位，说明零售价是辅单位价格
      if (item.auxUnit && item.mainToAuxRatio && item.mainToAuxRatio > 0) {
        if (item.currentUnit === item.auxUnit) {
          // 当前选择辅单位：直接使用价格（零售价就是辅单位价格）
          item.unitPrice = finalPrice
        } else {
          // 当前选择主单位：主单位价格 = 辅单位价格 × 比例
          // 例如：辅单位10元/瓶，比例3，主单位价格 = 10 × 3 = 30元/箱
          item.unitPrice = finalPrice * item.mainToAuxRatio
        }
      } else {
        // 没有辅单位：零售价就是主单位价格，直接使用
        item.unitPrice = finalPrice
      }
      item.subtotal = item.quantity * item.unitPrice
      console.log(`[价格计算] 最终价格: ${item.unitPrice}, 单位: ${item.currentUnit}, 数量: ${item.quantity}, 小计: ${item.subtotal}`)
    }
  }
  
  // 如果是套餐，根据客户类型选择VIP价格或普通价格
  if (item.itemType === 2) {
    if (selectedCustomer.value?.type === 2 && item.vipPrice) {
      // VIP客户使用VIP价格
      item.unitPrice = item.vipPrice
      item.subtotal = item.quantity * item.unitPrice
    } else if (item.packagePrice) {
      // 普通客户使用套餐价格
      item.unitPrice = item.packagePrice
      item.subtotal = item.quantity * item.unitPrice
    }
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

// 根据价格规则匹配最适合的价格
// 优先级：门店+客户类型+VIP等级 > 门店+客户类型 > 门店 > 全局
// 价格类型：1-建议零售价，2-VIP价，3-最低限价
const matchPrice = (
  rules: any[],
  storeId: number | null,
  customerType: number | null,
  vipLevel: number | null
): number | null => {
  if (!rules || rules.length === 0) return null

  const now = new Date()
  
  // 过滤有效规则
  const validRules = rules.filter((rule) => {
    if (rule.status !== 1) return false
    if (rule.effectiveDate && new Date(rule.effectiveDate) > now) return false
    if (rule.expiryDate && new Date(rule.expiryDate) < now) return false
    return true
  })

  // 确定需要的价格类型
  // VIP客户优先使用VIP价(2)，如果没有则使用建议零售价(1)
  // 普通客户使用建议零售价(1)
  const targetPriceTypes = customerType === 2 ? [2, 1] : [1]

  // 优先级匹配
  // 注意：规则中的 null 表示全局/所有客户/所有VIP等级
  // 匹配规则：规则中的字段必须与查询条件匹配，或者规则中的字段为 null（表示匹配所有）
  
  // 辅助函数：检查规则是否匹配条件
  const matchesRule = (rule: any, sId: number | null, cType: number | null, vLevel: number | null): boolean => {
    // storeId 匹配：规则为 null（全局）或精确匹配
    const storeMatch = rule.storeId === null || rule.storeId === sId
    // customerType 匹配：规则为 null（所有客户）或精确匹配
    const customerMatch = rule.customerType === null || rule.customerType === cType
    // vipLevel 匹配：规则为 null（所有VIP等级）或精确匹配
    const vipMatch = rule.vipLevel === null || rule.vipLevel === vLevel
    
    return storeMatch && customerMatch && vipMatch
  }
  
  // 按优先级匹配：门店+客户类型+VIP等级 > 门店+客户类型 > 门店 > 全局
  // 1. 门店+客户类型+VIP等级（最精确匹配）
  if (storeId && customerType && vipLevel) {
    for (const priceType of targetPriceTypes) {
      const rule = validRules.find(
        (r) => matchesRule(r, storeId, customerType, vipLevel) && r.priceType === priceType
      )
      if (rule) {
        console.log(`[价格匹配] 匹配到规则(门店+客户类型+VIP等级):`, rule)
        return rule.price
      }
    }
  }

  // 2. 门店+客户类型（不限制VIP等级）
  if (storeId && customerType) {
    for (const priceType of targetPriceTypes) {
      const rule = validRules.find(
        (r) => matchesRule(r, storeId, customerType, null) && r.priceType === priceType
      )
      if (rule) {
        console.log(`[价格匹配] 匹配到规则(门店+客户类型):`, rule)
        return rule.price
      }
    }
  }

  // 3. 门店（不限制客户类型和VIP等级）
  if (storeId) {
    for (const priceType of targetPriceTypes) {
      const rule = validRules.find(
        (r) => matchesRule(r, storeId, null, null) && r.priceType === priceType
      )
      if (rule) {
        console.log(`[价格匹配] 匹配到规则(门店):`, rule)
        return rule.price
      }
    }
  }

  // 4. 全局（不限制门店、客户类型和VIP等级）
  for (const priceType of targetPriceTypes) {
    const rule = validRules.find(
      (r) => matchesRule(r, null, null, null) && r.priceType === priceType
    )
    if (rule) {
      console.log(`[价格匹配] 匹配到规则(全局):`, rule)
      return rule.price
    }
  }
  
  console.log(`[价格匹配] 未匹配到任何规则，有效规则数量: ${validRules.length}`, validRules)

  return null
}

// 重新计算购物车中所有商品的价格
const recalculateCartPrices = async () => {
  if (!cartItems.value || cartItems.value.length === 0) return
  
  const { getProductPriceRules } = await import('@/api/product')
  const updatedItems = [...cartItems.value]
  let hasChanges = false

  for (const item of updatedItems) {
    // 处理商品价格
    if (item.itemType === 1 && item.productId) {
      let finalPrice: number | null = null
      
      // 1. 优先从价格规则中获取价格
      if (selectedCustomer.value) {
        try {
          const priceRules = await getProductPriceRules(item.productId)
          console.log(`[价格重算] 商品ID: ${item.productId}, 价格规则数量: ${priceRules?.length || 0}`, priceRules)
          
          const matchedPrice = matchPrice(
            priceRules,
            storeId.value,
            selectedCustomer.value.type,
            selectedCustomer.value.vipLevel || null
          )
          
          console.log(`[价格重算] 匹配到的价格: ${matchedPrice}, 门店ID: ${storeId.value}, 客户类型: ${selectedCustomer.value.type}, VIP等级: ${selectedCustomer.value.vipLevel}`)
          
          if (matchedPrice !== null) {
            finalPrice = matchedPrice
          }
        } catch (error) {
          console.error(`获取商品 ${item.productId} 价格规则失败:`, error)
        }
      }
      
      // 2. 如果价格规则没有匹配到，回退到商品表的VIP价格（如果是VIP客户）
      if (finalPrice === null && selectedCustomer.value?.type === 2) {
        // 尝试从 item 的临时字段获取
        const product = (item as any)._product || goodsPanelRef.value?.getProductById?.(item.productId)
        if (product?.vipPrice) {
          const vipPrice = typeof product.vipPrice === 'number' ? product.vipPrice : parseFloat(String(product.vipPrice))
          if (!isNaN(vipPrice) && vipPrice > 0) {
            finalPrice = vipPrice
          }
        }
      }
      
      // 3. 如果还是没有价格，保持原价
      if (finalPrice === null) {
        continue // 不更新价格
      }
      
      // 应用价格（考虑单位转换）
      // 价格存储规则：
      // 1. 有辅单位的商品：零售价是辅单位的价格（最小单位）
      // 2. 没有辅单位的商品：零售价是主单位的价格
      let newPrice = finalPrice
      if (item.auxUnit && item.mainToAuxRatio && item.mainToAuxRatio > 0) {
        // 有辅单位：零售价是辅单位价格
        if (item.currentUnit === item.auxUnit) {
          // 当前选择辅单位：直接使用价格（零售价就是辅单位价格）
          newPrice = finalPrice
        } else {
          // 当前选择主单位：主单位价格 = 辅单位价格 × 比例
          // 例如：辅单位10元/瓶，比例3，主单位价格 = 10 × 3 = 30元/箱
          newPrice = finalPrice * item.mainToAuxRatio
        }
      } else {
        // 没有辅单位：零售价就是主单位价格，直接使用
        newPrice = finalPrice
      }
      
      if (Math.abs(newPrice - item.unitPrice) > 0.01) { // 允许小的浮点误差
        item.unitPrice = newPrice
        item.subtotal = item.quantity * item.unitPrice
        hasChanges = true
      }
    }
    
    // 处理套餐价格
    if (item.itemType === 2) {
      if (selectedCustomer.value?.type === 2 && item.vipPrice) {
        // VIP客户使用VIP价格
        if (Math.abs(item.unitPrice - item.vipPrice) > 0.01) {
          item.unitPrice = item.vipPrice
          item.subtotal = item.quantity * item.unitPrice
          hasChanges = true
        }
      } else if (item.packagePrice) {
        // 普通客户使用套餐价格
        if (Math.abs(item.unitPrice - item.packagePrice) > 0.01) {
          item.unitPrice = item.packagePrice
          item.subtotal = item.quantity * item.unitPrice
          hasChanges = true
        }
      }
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


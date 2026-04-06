<template>
  <div class="cashier-panel">
    <!-- 客户识别栏 -->
    <CustomerBar
      :store-id="storeId"
      :selected-customer="props.selectedCustomer"
      @customer-change="handleCustomerChange"
    />

    <!-- 购物车列表 -->
    <CartList
      :cart-items="cartItems"
      :selected-customer="props.selectedCustomer"
      @update="handleCartUpdate"
    />

    <!-- 结算栏 -->
    <SettlementBar
      ref="settlementBarRef"
      :cart-items="cartItems"
      :total-amount="totalAmount"
      :customer="props.selectedCustomer"
      @checkout="handleCheckout"
      @clear-cart="handleClearCart"
      @restore-cart="handleRestoreCart"
    />
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {getProductPriceRules} from '@/api/product'
import CustomerBar from './CustomerBar.vue'
import CartList from './CartList.vue'
import SettlementBar from './SettlementBar.vue'
import type {CartItem, POSCustomerInfo} from '@/types/pos'
import type {ProductPriceRuleVO} from '@/types/product'

const props = defineProps<{
  storeId: number | null
  staffId: number | null
  cartItems: CartItem[]
  selectedCustomer: POSCustomerInfo | null
}>()

const emit = defineEmits<{
  customerChange: [customer: POSCustomerInfo | null]
  cartUpdate: [items: CartItem[]]
  checkout: [data: {
    paymentType: number
    discountAmount: number
    walletAuthCode?: string
    useSpecialFund?: boolean
  }]
  clearCart: []
}>()

// SettlementBar 引用
const settlementBarRef = ref<InstanceType<typeof SettlementBar> | null>(null)

// 计算总金额
const totalAmount = computed(() => {
  return props.cartItems.reduce((sum, item) => sum + item.subtotal, 0)
})

/**
 * 根据价格规则匹配最适合的价格
 * 优先级：门店+客户类型+VIP等级 > 门店+客户类型 > 门店 > 全局
 */
const matchPrice = (
  rules: ProductPriceRuleVO[],
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

  // 优先级匹配
  // 1. 门店+客户类型+VIP等级
  if (storeId && customerType && vipLevel) {
    const rule = validRules.find(
      (r) =>
        r.storeId === storeId &&
        r.customerType === customerType &&
        r.vipLevel === vipLevel &&
        r.priceType === 2 // VIP价
    )
    if (rule) return rule.price
  }

  // 2. 门店+客户类型
  if (storeId && customerType) {
    const rule = validRules.find(
      (r) =>
        r.storeId === storeId &&
        r.customerType === customerType &&
        !r.vipLevel &&
        r.priceType === (customerType === 2 ? 2 : 1) // VIP客户用VIP价，普通客户用建议零售价
    )
    if (rule) return rule.price
  }

  // 3. 门店
  if (storeId) {
    const rule = validRules.find(
      (r) => r.storeId === storeId && !r.customerType && r.priceType === 1
    )
    if (rule) return rule.price
  }

  // 4. 全局
  const rule = validRules.find(
    (r) => !r.storeId && !r.customerType && r.priceType === 1
  )
  if (rule) return rule.price

  return null
}

/**
 * 重新计算购物车商品价格
 */
const recalculatePrices = async () => {
  if (!props.cartItems || props.cartItems.length === 0) return

  const updatedItems = [...props.cartItems]
  let hasChanges = false

  for (const item of updatedItems) {
    // 处理商品价格
    if (item.itemType === 1 && item.productId) {
      try {
        const priceRules = await getProductPriceRules(item.productId)
        const customerType = props.selectedCustomer?.type || null
        const vipLevel = props.selectedCustomer?.vipLevel || null
        const matchedPrice = matchPrice(
          priceRules,
          props.storeId,
          customerType,
          vipLevel
        )

        if (matchedPrice !== null && matchedPrice !== item.unitPrice) {
          // 需要根据单位转换价格
          const newPrice = matchedPrice

          // 如果当前是辅单位，需要转换
          if (
            item.currentUnit === item.auxUnit &&
            item.mainToAuxRatio &&
            item.mainToAuxRatio > 0
          ) {
            item.unitPrice = newPrice / item.mainToAuxRatio
          } else {
            item.unitPrice = newPrice
          }

          // 重新计算小计
          item.subtotal = item.quantity * item.unitPrice
          hasChanges = true
        }
      } catch (error) {
        console.error(`获取商品 ${item.productId} 价格规则失败:`, error)
      }
    }

    // 处理套餐价格（使用套餐的 VIP 价格）
    if (item.itemType === 2 && item.packageId) {
      if (props.selectedCustomer?.type === 2 && item.vipPrice) {
        // VIP 客户使用 VIP 价格
        if (item.unitPrice !== item.vipPrice) {
          item.unitPrice = item.vipPrice
          item.subtotal = item.quantity * item.unitPrice
          hasChanges = true
        }
      } else if (item.packagePrice) {
        // 普通客户使用套餐价格
        if (item.unitPrice !== item.packagePrice) {
          item.unitPrice = item.packagePrice
          item.subtotal = item.quantity * item.unitPrice
          hasChanges = true
        }
      }
    }
  }

  if (hasChanges) {
    emit('cartUpdate', updatedItems)
  }
}

// 客户变更
const handleCustomerChange = (customer: POSCustomerInfo | null) => {
  emit('customerChange', customer)
  
  // 重新计算价格（根据 VIP 等级）
  recalculatePrices()
}

// 监听客户变化，重新计算价格
watch(
  () => props.selectedCustomer,
  () => {
    if (props.cartItems.length > 0) {
      recalculatePrices()
    }
  },
  { deep: true }
)

// 购物车更新
const handleCartUpdate = (items: CartItem[]) => {
  emit('cartUpdate', items)
}

// 恢复挂单
const handleRestoreCart = (payload: { cartItems: CartItem[]; customer: POSCustomerInfo | null }) => {
  emit('cartUpdate', payload.cartItems)
  emit('customerChange', payload.customer)
}

// 结算
const handleCheckout = (data: {
  paymentType: number
  discountAmount: number
  walletAuthCode?: string
  useSpecialFund?: boolean
}) => {
  emit('checkout', data)
}

// 清空购物车
const handleClearCart = () => {
  emit('clearCart')
}

// 触发结算（供外部调用）
const triggerCheckout = () => {
  settlementBarRef.value?.triggerCheckout()
}

defineExpose({
  triggerCheckout
})
</script>

<style scoped lang="scss">
.cashier-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>


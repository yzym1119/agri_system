<template>
  <div class="modern-settlement-bar">
    <!-- 第一行：数量、优惠金额、清空按钮 -->
    <div class="summary-section">
      <div class="summary-left">
        <div class="summary-row">
          <span class="summary-label">数量:</span>
          <span class="summary-value">{{ totalQuantity }}</span>
        </div>
        <div class="summary-row">
          <span class="summary-label">优惠</span>
          <el-input-number
            v-model="discountAmount"
            :precision="2"
            :min="0"
            :max="totalAmount"
            size="small"
            controls-position="right"
            class="discount-input"
          >
            <template #prefix>¥</template>
          </el-input-number>
        </div>
      </div>
      <el-button
        text
        @click="handleClear"
        class="clear-btn"
      >
        清空
      </el-button>
    </div>

    <!-- 第二行：应收合计和金额 -->
    <div class="total-section">
      <span class="total-label">合计：</span>
      <span class="total-amount">¥{{ actualAmount.toFixed(2) }}</span>
    </div>

    <!-- 第三行：挂单和确认收款按钮 -->
    <div class="footer-right">
      <div class="hold-btn-wrapper">
        <el-popover
          v-if="holdOrders.length > 0"
          placement="top"
          trigger="hover"
          width="360"
          popper-class="hold-popover"
        >
          <template #reference>
            <el-button class="hold-btn" @click="handleHold">
              挂单
              <span class="hold-count">({{ holdOrders.length }})</span>
            </el-button>
          </template>
          <div class="hold-list">
            <div
              v-for="order in holdOrders"
              :key="order.id"
              class="hold-item"
            >
              <div class="hold-info">
                <div class="hold-title">
                  {{ order.customer?.name || '散客' }}
                  <span class="hold-amount">¥{{ order.totalAmount.toFixed(2) }}</span>
                </div>
                <div class="hold-meta">
                  <span class="hold-time">{{ formatHoldTime(order.timestamp) }}</span>
                  <span class="hold-count-text">商品数：{{ order.cartItems.length }}</span>
                </div>
              </div>
              <div class="hold-actions">
                <el-button type="primary" link @click="handleRestore(order.id)">恢复</el-button>
                <el-button type="danger" link @click="handleDelete(order.id)">删除</el-button>
              </div>
            </div>
          </div>
        </el-popover>
        <el-button v-else class="hold-btn" @click="handleHold">
          挂单
        </el-button>
      </div>
        
      <el-button
        type="primary"
        class="checkout-btn"
        @click="handleCheckout"
      >
        确认收款
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'
import type {CartItem, POSCustomerInfo} from '@/types/pos'

interface HoldOrder {
  id: string
  cartItems: CartItem[]
  customer: POSCustomerInfo | null
  timestamp: string
  totalAmount: number
  discountAmount: number
}

const props = defineProps<{
  cartItems: CartItem[]
  totalAmount: number
  customer?: POSCustomerInfo | null
}>()

const emit = defineEmits<{
  checkout: [data: {
    paymentType: number
    discountAmount: number
    walletAuthCode?: string
    useSpecialFund?: boolean
  }]
  clearCart: []
  restoreCart: [payload: { cartItems: CartItem[]; customer: POSCustomerInfo | null; discountAmount: number }]
}>()

const discountAmount = ref(0)
const holdOrders = ref<HoldOrder[]>([])

// 初始化挂单列表
try {
  const stored = JSON.parse(localStorage.getItem('pos_hold_orders') || '[]')
  holdOrders.value = Array.isArray(stored) ? stored : []
} catch {
  holdOrders.value = []
}

// 计算实收金额
const actualAmount = computed(() => {
  return Math.max(0, props.totalAmount - discountAmount.value)
})

const totalQuantity = computed(() => {
  return props.cartItems.reduce((sum, item) => sum + item.quantity, 0)
})

// 挂单
const handleHold = () => {
  if (props.cartItems.length === 0) {
    ElMessage.warning('购物车为空，无法挂单')
    return
  }
  
  const holdOrder: HoldOrder = {
    id: `hold_${Date.now()}`,
    cartItems: JSON.parse(JSON.stringify(props.cartItems)),
    customer: props.customer ? JSON.parse(JSON.stringify(props.customer)) : null,
    timestamp: new Date().toISOString(),
    totalAmount: props.totalAmount,
    discountAmount: discountAmount.value
  }
  
  try {
    const list = [...holdOrders.value, holdOrder]
    if (list.length > 10) {
      list.shift()
    }
    holdOrders.value = list
    localStorage.setItem('pos_hold_orders', JSON.stringify(list))
    ElMessage.success(`挂单成功，共${list.length}个挂单`)
    
    emit('clearCart')
    discountAmount.value = 0
  } catch (error) {
    console.error('挂单失败:', error)
    ElMessage.error('挂单失败')
  }
}

// 恢复挂单
const handleRestore = (orderId: string) => {
  const order = holdOrders.value.find(o => o.id === orderId)
  if (!order) return
  
  discountAmount.value = order.discountAmount
  
  emit('restoreCart', {
    cartItems: order.cartItems,
    customer: order.customer,
    discountAmount: order.discountAmount
  })
  
  // 从列表中删除
  holdOrders.value = holdOrders.value.filter(o => o.id !== orderId)
  localStorage.setItem('pos_hold_orders', JSON.stringify(holdOrders.value))
  
  ElMessage.success('挂单已恢复')
}

// 删除挂单
const handleDelete = (orderId: string) => {
  holdOrders.value = holdOrders.value.filter(o => o.id !== orderId)
  localStorage.setItem('pos_hold_orders', JSON.stringify(holdOrders.value))
  ElMessage.success('挂单已删除')
}

// 格式化挂单时间
const formatHoldTime = (timestamp: string) => {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  return date.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

// 清空购物车
const handleClear = () => {
  emit('clearCart')
  discountAmount.value = 0
}

// 结算
const handleCheckout = () => {
  const checkoutData = {
    paymentType: 1,
    discountAmount: discountAmount.value,
    walletAuthCode: undefined,
    useSpecialFund: undefined
  }
  emit('checkout', checkoutData)
}

// 设置优惠金额（供外部调用）
const setDiscountAmount = (amount: number) => {
  discountAmount.value = amount
}

// 触发结算（供外部调用）
const triggerCheckout = () => {
  handleCheckout()
}

defineExpose({
  triggerCheckout,
  setDiscountAmount
})
</script>

<style scoped lang="scss">
.modern-settlement-bar {
  background: #fff;
  border-top: 1px solid #e5e7eb;
  box-shadow: 0 -4px 6px rgba(0, 0, 0, 0.05);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
  
  .summary-left {
    display: flex;
    align-items: center;
    gap: 16px; // 数量和优惠金额之间的距离
    
    .summary-row {
      display: flex;
      align-items: center;
      gap: 8px; // 标签和值之间的距离
      
      .summary-label {
        font-size: 12px;
        color: #6b7280;
      }
      
      .summary-value {
        font-size: 12px;
        font-weight: 500;
        color: #1f2937;
      }
      
      .discount-input {
        width: 90px;
      }
    }
  }
  
  .clear-btn {
    color: #2563eb;
    font-size: 12px;
    padding: 0;
    
    &:hover {
      color: #1d4ed8;
    }
  }
}

.total-section {
  display: flex;
  align-items: center;
  
  gap: 90px;
  padding: 1px 0;
  border-bottom: 1px solid #e5e7eb;
  
  .total-label {
    font-size: 16px;
    color: #232324;
  }
  
  .total-amount {
    font-size: 28px;
    font-weight: 700;
    color: #ef4444;
  }
}

.footer-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  
  .hold-btn-wrapper {
    // 固定宽度，防止按钮宽度变化影响确认收款按钮位置
    min-width: 100px;
    display: flex;
    align-items: center;
  }
  
  .hold-btn {
    padding: 12px 24px;
    font-size: 14px;
    width: 100%;
    
    .hold-count {
      margin-left: 4px;
    }
  }
  
  .checkout-btn {
    padding: 12px 55px;
    font-size: 16px;
    font-weight: 600;
    background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
    border: none;
    flex-shrink: 0; // 防止按钮被压缩
    
    &:hover {
      background: linear-gradient(135deg, #1d4ed8 0%, #1e40af 100%);
    }
  }
}

// 全局样式，因为 popover 渲染到 body
</style>

<style>
.hold-popover {
  padding: 0 !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
}

.hold-popover .hold-list {
  max-height: 400px;
  overflow-y: auto;
  padding: 8px 0;
}

.hold-popover .hold-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  transition: background-color 0.2s;
}

.hold-popover .hold-item:hover {
  background-color: #f9fafb;
}

.hold-popover .hold-item:last-child {
  border-bottom: none;
}

.hold-popover .hold-info {
  flex: 1;
  min-width: 0;
}

.hold-popover .hold-title {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.hold-popover .hold-amount {
  color: #ef4444;
  font-weight: 600;
  flex-shrink: 0;
}

.hold-popover .hold-meta {
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 12px;
}

.hold-popover .hold-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 12px;
}
</style>


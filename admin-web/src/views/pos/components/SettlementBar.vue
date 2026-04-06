<template>
  <div class="settlement-bar">
    <!-- 优惠金额 -->
    <div class="discount-section">
      <div class="discount-row">
        <span class="discount-label">优惠金额</span>
        <div class="discount-input-wrapper">
          <el-input-number
            v-model="discountAmount"
            :precision="2"
            :min="0"
            :max="totalAmount"
            :controls="true"
            controls-position="right"
            size="small"
          >
            <template #prefix>
              <span class="currency-prefix">¥</span>
            </template>
          </el-input-number>
        </div>
      </div>
    </div>

    <!-- 汇总信息 -->
    <div class="summary">
      <div class="summary-item">
        <span class="label">商品数量</span>
        <span class="value">{{ totalQuantity }}</span>
      </div>
      <div class="summary-item">
        <span class="label">合计金额</span>
        <span class="value">¥{{ totalAmount.toFixed(2) }}</span>
      </div>
      <div class="summary-item final">
        <span class="label">应收金额</span>
        <span class="value total">¥{{ actualAmount.toFixed(2) }}</span>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="actions">
      <el-popover
        v-if="holdOrders.length > 0"
        placement="top"
        trigger="hover"
        width="360"
        popper-class="hold-popover"
      >
        <template #reference>
          <el-button @click="handleHold" class="hold-btn">
            挂单
            <span v-if="holdOrders.length" class="hold-count">({{ holdOrders.length }})</span>
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
      <el-button v-else @click="handleHold" class="hold-btn">
        挂单
      </el-button>

      <el-button type="primary" @click="handleCheckout" class="checkout-btn">
        收款 <span class="shortcut">(Ctrl+Enter)</span>
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
  restoreCart: [payload: { cartItems: CartItem[]; customer: POSCustomerInfo | null }]
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


const handleHold = () => {
  if (props.cartItems.length === 0) {
    ElMessage.warning('购物车为空，无法挂单')
    return
  }
  
  // 挂单：保存当前购物车状态到本地存储
  const holdOrder: HoldOrder = {
    id: `hold_${Date.now()}`,
    cartItems: JSON.parse(JSON.stringify(props.cartItems)), // 深拷贝
    customer: props.customer ? JSON.parse(JSON.stringify(props.customer)) : null,
    timestamp: new Date().toISOString(),
    totalAmount: props.totalAmount,
    discountAmount: discountAmount.value
  }
  
  try {
    const list = [...holdOrders.value, holdOrder]
    // 最多保存10个挂单
    while (list.length > 10) {
      list.shift()
    }
    holdOrders.value = list
    localStorage.setItem('pos_hold_orders', JSON.stringify(list))
    ElMessage.success(`挂单成功，共${list.length}个挂单`)

    // 清空购物车
    emit('clearCart')
  } catch (error) {
    console.error('挂单失败:', error)
    ElMessage.error('挂单失败')
  }
}

const handleRestore = (id: string) => {
  const order = holdOrders.value.find((o) => o.id === id)
  if (!order) return

  // 恢复优惠金额
  discountAmount.value = order.discountAmount || 0

  // 通知父组件恢复购物车和客户
  emit('restoreCart', {
    cartItems: order.cartItems,
    customer: order.customer
  })

  // 从列表中移除
  const list = holdOrders.value.filter((o) => o.id !== id)
  holdOrders.value = list
  localStorage.setItem('pos_hold_orders', JSON.stringify(list))
  ElMessage.success('已恢复挂单')
}

const handleDelete = (id: string) => {
  const list = holdOrders.value.filter((o) => o.id !== id)
  holdOrders.value = list
  localStorage.setItem('pos_hold_orders', JSON.stringify(list))
  ElMessage.success('已删除挂单')
}

const formatHoldTime = (timestamp: string) => {
  try {
    const d = new Date(timestamp)
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${m}-${day} ${hh}:${mm}`
  } catch {
    return timestamp
  }
}

const handleCheckout = () => {
  // 只传递优惠金额，支付方式在弹窗中选择
  const checkoutData = {
    paymentType: 1, // 默认现金，实际在弹窗中选择
    discountAmount: discountAmount.value,
    walletAuthCode: undefined,
    useSpecialFund: undefined
  }
  emit('checkout', checkoutData)
}

// 触发结算（供外部调用）
const triggerCheckout = () => {
  handleCheckout()
}

defineExpose({
  triggerCheckout
})
</script>

<style scoped lang="scss">
.settlement-bar {
  padding: 16px;
  border-top: 1px solid #e0e0e0;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.discount-section {
  .discount-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;

    .discount-label {
      font-size: 14px;
      color: #646262;
      font-weight: 500;
    }

    .discount-input-wrapper {
      display: flex;
      align-items: center;
      justify-content: flex-end;
    }
  }
}

.summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: #f5f5f5;
  border-radius: 8px;

  .summary-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 14px;

    .label {
      color: #666;
    }

    .value {
      font-weight: 500;
      color: #333;

      &.total {
        font-size: 24px;
        font-weight: 600;
        color: #f56c6c;
      }
    }

    &.final {
      padding-top: 12px;
      border-top: 1px solid #e0e0e0;
      margin-top: 4px;
    }
  }
}

.actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.hold-btn {
  min-width: 80px;
}

.hold-count {
  margin-left: 4px;
  font-size: 12px;
}

.hold-popover {
  .hold-list {
    max-height: 320px;
    overflow-y: auto;
  }

  .hold-empty {
    text-align: center;
    color: #999;
    font-size: 13px;
    padding: 8px 0;
  }

  .hold-item {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 8px 0;
    border-bottom: 1px solid #f0f0f0;

    &:last-child {
      border-bottom: none;
    }
  }

  .hold-info {
    flex: 1;
    min-width: 0;
  }

  .hold-title {
    font-size: 13px;
    font-weight: 500;
    color: #333;
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }

  .hold-amount {
    color: #f56c6c;
  }

  .hold-meta {
    margin-top: 4px;
    font-size: 12px;
    color: #999;
    display: flex;
    justify-content: space-between;
  }

  .hold-actions {
    display: flex;
    flex-direction: column;
    align-items: flex-end;

    .el-button {
      
      font-size: 12px;
    }
  }
}

.actions {
  display: flex;
  gap: 12px;

  .hold-btn {
    flex: 1;
    border: 1px solid #dcdfe6;
    background: #fff;
    color: #333;
    height: 48px;
    &:hover {
      border-color: #409eff;
      color: #409eff;
    }
  }

  .checkout-btn {
    flex: 2;
    height: 48px;
    font-size: 16px;
    font-weight: 500;

    .shortcut {
      font-size: 12px;
      opacity: 0.8;
      margin-left: 4px;
    }
  }
}
</style>


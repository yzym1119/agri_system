<template>
  <div class="modern-cart-list">
    <!-- 客户栏 -->
    <div class="cart-header">
      <CustomerBar
        ref="customerBarRef"
        :store-id="storeId"
        :selected-customer="selectedCustomer ?? null"
        @customer-change="$emit('customerChange', $event)"
      />
    </div>

    <!-- 购物车卡片列表 -->
    <div class="cart-items-container">
      <div v-if="cartItems.length === 0" class="cart-empty">
        <el-empty description="购物车为空" :image-size="80" />
      </div>
      
      <div
        v-for="(item, index) in cartItems"
        :key="item.id"
        class="cart-item-card"
        :class="{ 'from-package': item.fromPackage }"
      >
        <!-- 套餐标签（如果来自套餐） -->
        <div v-if="item.fromPackage" class="package-badge-tag">
          <el-icon><Box /></el-icon>
          <span>{{ item.fromPackage }}</span>
        </div>
        
        <!-- 第一行：品名 + 小计 -->
        <div class="cart-item-header">
          <div class="item-name">{{ item.name }}</div>
          <div class="item-subtotal">¥{{ item.subtotal.toFixed(2) }}</div>
        </div>
        
        <!-- 第二行：规格 + 厂家 -->
        <div class="cart-item-meta">
          <span v-if="item.specification" class="item-spec">{{ item.specification }}</span>
          <span v-if="item.manufacturer" class="item-manufacturer">{{ item.manufacturer }}</span>
        </div>

        <div v-if="item.itemType === 2" class="cart-item-meta" style="display: block">
          <div v-for="(material, materialIndex) in item.packageItems" :key="`${material.productId}_${materialIndex}`">
            {{ material.productName }} × {{ material.quantity }}{{ material.unit }}
          </div>
        </div>

        <!-- 服务端报价提示：低于参考下限 / 本地价降级；库房取货标记 -->
        <div class="cart-item-flags">
          <el-tag v-if="item.belowReference" type="danger" size="small" effect="plain">
            低于参考下限{{ item.referenceFloor ? ` ¥${item.referenceFloor.toFixed(2)}` : '' }}
          </el-tag>
          <el-tag v-else-if="item.priceFallback" type="warning" size="small" effect="plain">
            报价失败
          </el-tag>
          <el-checkbox
            :model-value="!!item.warehousePickup"
            size="small"
            @change="(checked: any) => handlePickupChange(item, !!checked)"
          >
            库房取货
          </el-checkbox>
        </div>

        <!-- 低于参考下限时记录人工原因（选填，随单留痕） -->
        <div v-if="item.belowReference || item.unitPrice !== item.suggestedUnitPrice" class="cart-item-reason">
          <el-input
            v-model="item.priceChangeReason"
            size="small"
            placeholder="议价原因（必填，最多255字）"
            :maxlength="255"
            @change="emit('update', [...props.cartItems])"
          />
        </div>
        
        <!-- 第三行：单价 + 数量控制器 + 单位 + 删除 -->
        <div class="cart-item-footer">
          <div class="item-price-unit">
            <el-input-number v-if="canChangePrice" :model-value="item.unitPrice"
              :min="0.01" :precision="2" :controls="false" :disabled="item.pricePending || item.priceFallback"
              size="small" :aria-label="item.itemType === 2 ? '每套成交价' : '成交单价'" style="width: 100px" @change="(value: number | undefined) => handlePriceChange(item, value)" />
            <span v-else class="item-price">¥{{ item.unitPrice.toFixed(2) }}</span>
            <el-select
              v-if="item.itemType === 1 && (item.saleUnits?.length || 0) > 1"
              :model-value="item.currentUnit"
              size="small"
              class="unit-select"
              @change="(newUnit: string) => handleUnitChange(item, newUnit)"
            >
              <el-option v-for="unit in item.saleUnits" :key="unit.unitName" :label="unit.unitName" :value="unit.unitName" />
            </el-select>
            <span v-else class="unit-text">{{ item.currentUnit }}</span>
          </div>
          
          <div class="item-quantity-control">
            <el-button
              :icon="Minus"
              size="small"
              circle
              @click="handleQuantityChange(item, item.quantity - 1)"
            />
            <span class="quantity-value">{{ item.quantity }}</span>
            <el-button
              :icon="Plus"
              size="small"
              circle
              @click="handleQuantityChange(item, item.quantity + 1)"
            />
          </div>
          
          <el-button
            :icon="Delete"
            type="danger"
            text
            size="small"
            @click="handleDelete(index)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { usePermissionStore } from '@/store/permission'
import {computed, ref} from 'vue'
import {Box, Delete, Minus, Plus} from '@element-plus/icons-vue'
import CustomerBar from './CustomerBar.vue'
import type {CartItem, POSCustomerInfo} from '@/types/pos'

const props = defineProps<{
  cartItems: CartItem[]
  selectedCustomer?: POSCustomerInfo | null
  storeId: number | null
}>()

const emit = defineEmits<{
  update: [items: CartItem[]]
  customerChange: [customer: POSCustomerInfo | null]
  unitChange: [payload: { id: string; oldUnit: string; newUnit: string }]
  packageChange: [payload: { id: string }]
}>()

const permissionStore = usePermissionStore()
const canChangePrice = computed(() => permissionStore.permissions.includes('*') || permissionStore.permissions.includes('order:price:override'))
const handlePriceChange = (item: CartItem, value?: number) => {
  if (!canChangePrice.value || value == null || value <= 0) return
  item.unitPrice = value
  item.subtotal = Math.round(item.quantity * value * 100) / 100
  item.belowReference = item.referenceFloor != null && value < item.referenceFloor
  if (item.itemType === 2) {
    item.pricePending = true
    emit('packageChange', { id: item.id })
  }
  emit('update', [...props.cartItems])
}

const customerBarRef = ref<InstanceType<typeof CustomerBar> | null>(null)

// 切换单位后由父组件重新取服务端报价，前端不进行价格换算。
const handleUnitChange = (item: CartItem, newUnit: string) => {
  if (item.currentUnit === newUnit) return
  const oldUnit = item.currentUnit
  item.currentUnit = newUnit
  item.quoteId = null
  item.pricePending = true
  emit('unitChange', { id: item.id, oldUnit, newUnit })
  emit('update', [...props.cartItems])
}

// 库房取货标记（后仓拣货，下单后由后端生成取货请求）
const handlePickupChange = (item: CartItem, checked: boolean) => {
  item.warehousePickup = checked
  emit('update', [...props.cartItems])
}

// 数量变化
const handleQuantityChange = (item: CartItem, newQuantity: number) => {
  if (newQuantity < 1 || (item.itemType === 2 && !Number.isInteger(newQuantity))) return
  item.quantity = newQuantity
  item.subtotal = Math.round(item.quantity * item.unitPrice * 100) / 100
  if (item.itemType === 2) {
    item.pricePending = true
    emit('packageChange', { id: item.id })
  }
  emit('update', [...props.cartItems])
}

// 删除
const handleDelete = (index: number) => {
  const newItems = [...props.cartItems]
  newItems.splice(index, 1)
  emit('update', newItems)
}

defineExpose({
  focusCustomerSearch: () => customerBarRef.value?.focusSearch()
})
</script>

<style scoped lang="scss">
.modern-cart-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f8fafc;
  overflow: hidden;
}

.cart-header {
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.cart-items-container {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cart-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cart-item-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.2s;
  position: relative;
  
  &:hover {
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  }
  
  // 来自套餐的商品样式
  &.from-package {
    border-left: 3px solid #f59e0b;
  }
}

.package-badge-tag {
  position: absolute;
  top: 44px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: #fef3c7;
  color: #f59e0b;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  
  .el-icon {
    font-size: 12px;
  }
}

.cart-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
  
  .item-name {
    font-size: 14px;
    font-weight: 600;
    color: #1f2937;
    flex: 1;
    line-height: 1.4;
  }
  
  .item-subtotal {
    font-size: 16px;
    font-weight: 600;
    color: #ef4444;
    margin-left: 12px;
  }
}

.cart-item-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 12px;
  
  .item-spec {
    color: #6b7280;
  }
  
  .item-manufacturer {
    color: #9ca3af;
  }
}

.cart-item-flags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.cart-item-reason {
  margin-bottom: 8px;
}

.cart-item-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.item-price-unit {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .item-price {
    font-size: 13px;
    color: #6b7280;
  }
  
  .unit-select {
    width: 60px;
  }
  
  .unit-text {
    font-size: 12px;
    color: #9ca3af;
  }
}

.item-quantity-control {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .quantity-value {
    min-width: 32px;
    text-align: center;
    font-size: 14px;
    font-weight: 500;
    color: #1f2937;
  }
}
</style>

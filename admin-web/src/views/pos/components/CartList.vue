<template>
  <div class="cart-list">
    <el-table
      :data="cartItems"
      style="width: 100%"
      height="100%"
      :row-key="(row: CartItem) => row.id"
      border
    >
      <el-table-column prop="name" label="品名" min-width="110" align="center" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip
            v-if="row.itemType === 2 && row.packageItems?.length"
            :content="packageTooltipContent(row)"
            placement="top"
            effect="dark"
            :disabled="!row.packageItems?.length"
          >
            <span class="package-name">{{ row.name }}</span>
          </el-tooltip>
          <span v-else>{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="specification" label="规格" width="80" align="center" show-overflow-tooltip />
      <el-table-column prop="manufacturer" label="厂家" width="110" align="center"show-overflow-tooltip />
      <el-table-column label="单位" width="70" align="center">
        <template #default="{ row }">
          <el-select
            v-if="row.itemType === 1 && row.auxUnit"
            v-model="row.currentUnit"
            size="small"
            @change="handleUnitChange(row)"
          >
            <el-option :label="row.mainUnit" :value="row.mainUnit" />
            <el-option :label="row.auxUnit" :value="row.auxUnit" />
          </el-select>
          <span v-else>{{ row.currentUnit }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单价" width="80" align="center">
        <template #default="{ row }">
          <span>¥{{ row.unitPrice.toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="85" align="center" fixed="right">
        <template #default="{ row }">
          <el-input-number
            v-model="row.quantity"
            :precision="0"
            :min="1"
            :controls-position="'right'"
            size="small"
            style="width: 100%"
            @change="handleQuantityChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="小计" width="85" align="center" fixed="right">
        <template #default="{ row }">
          <span class="subtotal">¥{{ row.subtotal.toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column  width="45" fixed="right">
        <template #default="{ $index }">
          <el-button
            type="danger"
            
            link :icon="Delete"
            @click="handleDelete($index)"
          />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue'
import {Delete} from '@element-plus/icons-vue'
import type {CartItem, POSCustomerInfo} from '@/types/pos'

const props = defineProps<{
  cartItems: CartItem[]
  selectedCustomer?: POSCustomerInfo | null
}>()

const emit = defineEmits<{
  update: [items: CartItem[]]
}>()

const cartItems = ref<CartItem[]>([...props.cartItems])

// 生成套餐tooltip内容
const packageTooltipContent = (row: CartItem) => {
  if (!row.packageItems || row.packageItems.length === 0) return ''
  return row.packageItems
    .map((item) => `${item.productName} × ${item.quantity}${item.unit || ''}`)
    .join('\n')
}

// 监听外部变化
watch(
  () => props.cartItems,
  (newItems) => {
    cartItems.value = [...newItems]
  },
  { deep: true }
)

// 单位切换
const handleUnitChange = (row: CartItem) => {
  if (row.itemType !== 1 || !row.mainUnit || !row.auxUnit || !row.mainToAuxRatio) {
    return
  }

  // 转换单价
  if (row.currentUnit === row.mainUnit) {
    // 切换到主单位：单价 = 原单价 * 比例
    row.unitPrice = row.unitPrice * (row.mainToAuxRatio || 1)
  } else {
    // 切换到辅单位：单价 = 原单价 / 比例
    row.unitPrice = row.unitPrice / (row.mainToAuxRatio || 1)
  }

  // 重新计算小计
  row.subtotal = row.quantity * row.unitPrice
  emit('update', [...cartItems.value])
}

// 单价不可调整，移除 handlePriceChange

// 数量变化
const handleQuantityChange = (row: CartItem) => {
  row.subtotal = row.quantity * row.unitPrice
  emit('update', [...cartItems.value])
}

// 删除
const handleDelete = (index: number) => {
  cartItems.value.splice(index, 1)
  emit('update', [...cartItems.value])
}
</script>

<style scoped lang="scss">
.cart-list {
  flex: 1;
  overflow: hidden;
}

.package-name {
  cursor: help;
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 2px;
}

.subtotal {
  font-weight: 600;
  color: #f56c6c;
}
</style>


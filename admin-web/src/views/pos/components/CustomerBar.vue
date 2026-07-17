<template>
  <div class="customer-bar">
    <el-autocomplete
      ref="autocompleteRef"
      v-if="!selectedCustomer"
      v-model="searchText"
      :fetch-suggestions="searchCustomers"
      placeholder="搜索客户（手机号/姓名）"
      clearable
      @select="handleSelect"
      @clear="handleClear"
      style="width: 100%"
    >
      <template #default="{ item }">
        <div class="customer-option">
          <div class="customer-name">{{ item.name }}</div>
          <div class="customer-phone">{{ item.phone }}</div>
          <div class="customer-level" v-if="item.type === 2">
            VIP{{ item.vipLevel }}
          </div>
        </div>
      </template>
    </el-autocomplete>

    <!-- 选中客户后的紧凑显示 -->
    <div v-if="selectedCustomer" class="customer-card">
      <div class="customer-avatar">
        <el-icon :size="24"><User /></el-icon>
      </div>
      <div class="customer-content">
        <div class="customer-header">
          <span class="customer-name">{{ selectedCustomer.name }}</span>
          <el-tag v-if="selectedCustomer.type === 2" type="warning" size="small" class="vip-badge">
            VIP{{ selectedCustomer.vipLevel }}
          </el-tag>
        </div>
        <div class="customer-finance">
          <span class="finance-item">
            <span class="finance-label">余额：</span>
            <span class="finance-value balance">¥{{ selectedCustomer.balance.toFixed(2) }}</span>
          </span>
          <span class="finance-item">
            <span class="finance-label">欠款：</span>
            <span class="finance-value debt" :class="{ 'debt-warning': (selectedCustomer.totalDebt || 0) > 0 }">
              ¥{{ (selectedCustomer.totalDebt || 0).toFixed(2) }}
            </span>
          </span>
        </div>
      </div>
      <div class="customer-action" @click="handleClear">
        <el-icon :size="18"><Close /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, ref} from 'vue'
import {Close, User} from '@element-plus/icons-vue'
import {getCustomerDetail, getCustomerList} from '@/api/customer'
import type {CustomerListVO} from '@/types/customer'
import type {POSCustomerInfo} from '@/types/pos'

const props = defineProps<{
  storeId: number | null
  selectedCustomer: POSCustomerInfo | null
}>()

const emit = defineEmits<{
  customerChange: [customer: POSCustomerInfo | null]
}>()

const searchText = ref('')
const autocompleteRef = ref<any>(null)
const selectedCustomer = computed({
  get: () => props.selectedCustomer,
  set: (val) => emit('customerChange', val)
})

// 搜索客户
const searchCustomers = async (queryString: string, cb: (suggestions: any[]) => void) => {
  if (!queryString) {
    cb([])
    return
  }

  try {
    const trimmedQuery = queryString.trim()
    // 判断是手机号还是姓名：
    // 1. 纯数字且长度>=4认为是手机号（支持尾号搜索）
    // 2. 包含中文或字母认为是姓名
    const isPhone = /^\d{4,}$/.test(trimmedQuery)
    const isName = /[a-zA-Z\u4e00-\u9fa5]/.test(trimmedQuery)
    
    // 构建查询参数：同时搜索姓名和手机号（后端支持模糊匹配）
    const queryParams: any = {
      pageNum: 1,
      pageSize: 20
    }
    
    if (isPhone) {
      // 纯数字，优先用手机号搜索（支持尾号）
      queryParams.phone = trimmedQuery
    } else if (isName) {
      // 包含中文或字母，用姓名搜索
      queryParams.name = trimmedQuery
    } else {
      // 其他情况，同时搜索姓名和手机号
      queryParams.name = trimmedQuery
      if (/^\d+$/.test(trimmedQuery)) {
        queryParams.phone = trimmedQuery
      }
    }
    
    const res = await getCustomerList(queryParams)

    const suggestions = (res.records || []).map((customer: CustomerListVO) => ({
      value: customer.name,
      name: customer.name,
      phone: customer.phone,
      id: customer.id,
      type: customer.type,
      vipLevel: customer.vipLevel
    }))

    cb(suggestions)
  } catch (error) {
    console.error('搜索客户失败:', error)
    cb([])
  }
}

// 选择客户
const handleSelect = async (item: any) => {
  try {
    const detail = await getCustomerDetail(item.id)
    const customer: POSCustomerInfo = {
      id: detail.id,
      name: detail.name,
      phone: detail.phone || '',
      type: detail.type,
      vipLevel: detail.vipLevel,
      balance: detail.balance || 0,
      specialFund: detail.specialFundBalance || 0,
      creditLimit: detail.creditLimit,
      totalDebt: detail.totalDebt || 0
    }
    emit('customerChange', customer)
  } catch (error) {
    console.error('获取客户详情失败:', error)
  }
}

// 清除客户
const handleClear = () => {
  searchText.value = ''
  emit('customerChange', null)
}

const focusSearch = () => {
  if (selectedCustomer.value) {
    handleClear()
  }
  nextTick(() => {
    const input = autocompleteRef.value?.inputRef?.input as HTMLInputElement | undefined
    input?.focus()
    input?.select?.()
  })
}

defineExpose({
  focusSearch
})
</script>

<style scoped lang="scss">
.customer-bar {
  padding: 12px 16px;
  border-bottom: 1px solid #e0e0e0;
}

.customer-option {
  .customer-name {
    font-weight: 500;
  }

  .customer-phone {
    font-size: 12px;
    color: #666;
  }

  .customer-level {
    font-size: 12px;
    color: #f56c6c;
  }
}

.customer-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #f0f7ff;
  border-radius: 8px;
  border: 1px solid #e0e8f5;

  .customer-avatar {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: #409eff;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .customer-content {
    flex: 1;
    min-width: 0;

    .customer-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;

      .customer-name {
        font-size: 14px;
        font-weight: 500;
        color: #333;
      }

      .vip-badge {
        font-size: 11px;
        padding: 2px 6px;
        height: auto;
        line-height: 1.2;
      }
    }

    .customer-finance {
      display: flex;
      gap: 10px;
      font-size: 12px;

      .finance-item {
        display: flex;
        align-items: center;
        gap: 1px;

        .finance-label {
          color: #666;
        }

        .finance-value {
          font-weight: 500;

          &.balance {
            color: #ff9800;
          }

          &.debt {
            color: #f56c6c;

            &.debt-warning {
              color: #f56c6c;
            }
          }
        }
      }
    }
  }

  .customer-action {
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #999;
    border-radius: 4px;
    transition: all 0.2s;
    flex-shrink: 0;

    &:hover {
      background: #fff;
      color: #f56c6c;
    }
  }
}
</style>

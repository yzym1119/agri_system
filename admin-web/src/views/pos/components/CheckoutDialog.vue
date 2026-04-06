<template>
  <el-dialog
    v-model="visible"
    title="收银结算"
    width="900px"
    :close-on-click-modal="!props.submitting"
    :close-on-press-escape="!props.submitting"
    :show-close="!props.submitting"
    @close="handleClose"
    class="checkout-dialog"
    destroy-on-close
  >
    <div class="checkout-content">
      <!-- 左侧：金额和客户信息 (保持不变) -->
      <div class="left-panel">
        <div class="amount-section">
          <div class="amount-label">应收金额</div>
          <div class="amount-value">¥{{ actualAmount.toFixed(2) }}</div>
        </div>

        <div class="customer-section" v-if="customer">
          <div class="customer-label">客户信息</div>
          <div class="customer-info-box">
            <div class="customer-name">{{ customer.name }}</div>
            <div class="customer-phone" v-if="customer.phone">电话：{{ customer.phone }}</div>
            <div class="customer-detail">
              <div class="detail-row">
                <span class="detail-label">本金余额：</span>
                <span class="detail-value balance">¥{{ customer.balance.toFixed(2) }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">专用金：</span>
                <span class="detail-value special-fund">¥{{ (customer.specialFund || 0).toFixed(2) }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">欠款：</span>
                <span class="detail-value debt">¥{{ (customer.totalDebt || 0).toFixed(2) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="change-section">
          <div class="change-label">找零</div>
          <div class="change-value">¥{{ changeAmount.toFixed(2) }}</div>
        </div>

        <!-- 备注 -->
        <div class="remark-section">
          <div class="section-label">备注</div>
          <el-input
            type="textarea"
            v-model="confirmForm.remark"
            placeholder="订单备注（可选）"
            :rows="2"
            resize="none"
          />
        </div>

        <!-- 优惠券选择 -->
        <div class="coupon-section" v-if="customer && paymentType !== 6">
          <div class="section-label">优惠券</div>
          <el-select
            v-model="selectedCouponId"
            placeholder="选择优惠券（可选）"
            clearable
            class="coupon-select"
            @change="handleCouponChange"
          >
            <el-option
              v-for="coupon in availableCoupons"
              :key="coupon.id"
              :label="`${coupon.name} - ¥${coupon.amount}（满¥${coupon.minOrderAmount}可用）`"
              :value="coupon.id"
              :disabled="!isCouponUsable(coupon)"
            />
          </el-select>
          <div v-if="selectedCoupon" class="coupon-info">
            <span class="coupon-name">{{ selectedCoupon.name }}</span>
            <span class="coupon-amount">-¥{{ selectedCoupon.amount.toFixed(2) }}</span>
          </div>
        </div>

        <!-- 单一支付方式下的验证码输入 (混合支付在右侧单独处理) -->
        <div class="wallet-section" v-if="paymentType === 4">
          <div class="section-label">钱包验证码</div>
          <el-input
            v-model="walletAuthCode"
            placeholder="请输入6位验证码"
            maxlength="6"
            class="wallet-input"
            ref="singleAuthInputRef"
          />
        </div>
      </div>
      

      <!-- 右侧：支付方式 -->
      <div class="right-panel">
        <!-- 标题栏：支持返回 -->
        <div class="payment-header">
          <div class="payment-title" v-if="paymentType !== 10">支付方式</div>
          <div class="payment-title back-btn" v-else @click="resetToSinglePayment">
            <el-icon><ArrowLeft /></el-icon> 返回单一支付
          </div>
          
          <div v-if="paymentType === 10" class="mixed-header-actions">
             <el-link type="primary" :underline="false" @click="resetMixed">重置</el-link>
          </div>
        </div>

        <!-- 单一支付方式网格 (非混合模式时显示) -->
        <div class="payment-grid" v-if="paymentType !== 10">
          <div
            v-for="payment in paymentOptions"
            :key="payment.value"
            class="payment-item"
            :class="{ active: paymentType === payment.value, disabled: payment.disabled }"
            @click="!payment.disabled && handlePaymentSelect(payment.value)"
          >
            <el-icon :size="32" class="payment-icon">
              <component :is="payment.icon" />
            </el-icon>
            <span class="payment-name">{{ payment.label }}</span>
          </div>
        </div>

        <!-- 混合支付列表 (混合模式时显示) -->
        <div class="mixed-panel" v-else>
          <div class="mixed-desc">请分配支付金额：</div>
          
          <div class="mixed-list">
            <div 
              v-for="item in mixedOptions" 
              :key="item.key" 
              class="mixed-row"
              :class="{ 'is-active': mixedDetails[item.key] > 0, 'is-disabled': item.disabled }"
            >
              <!-- 左侧信息 -->
              <div class="row-left">
                <div class="icon-wrapper" :style="{ backgroundColor: item.bgColor, color: item.color }">
                  <el-icon><component :is="item.icon" /></el-icon>
                </div>
                <div class="row-info">
                  <div class="row-label">{{ item.label }}</div>
                  <div class="row-sub" v-if="item.subLabel">{{ item.subLabel }}</div>
                </div>
              </div>

              <!-- 右侧输入 -->
              <div class="row-right">
                <el-input-number 
                  v-model="mixedDetails[item.key]" 
                  :min="0" 
                  :max="9999999"
                  :precision="2"
                  :controls="false"
                  :disabled="item.disabled"
                  placeholder="0.00"
                  class="mixed-amount-input"
                  @focus="autoFillMixed(item.key)"
                />
                <el-icon 
                  v-if="mixedDetails[item.key] > 0" 
                  class="clear-icon" 
                  @click="mixedDetails[item.key] = 0"
                ><CircleCloseFilled /></el-icon>
              </div>
            </div>
            
            <!-- 混合支付下的验证码特殊处理 -->
            <div class="mixed-auth-code" v-if="needsAuthCodeInMixed">
               <el-input 
                 v-model="walletAuthCode" 
                 placeholder="使用了余额/专用金，请输入验证码" 
                 maxlength="6"
               >
                 <template #prefix><el-icon><Lock /></el-icon></template>
               </el-input>
            </div>
          </div>

          <!-- 底部进度条 -->
          <div class="progress-section">
            <el-progress 
              :percentage="progressPercentage" 
              :status="remainingAmount === 0 ? 'success' : ''"
              :show-text="false"
              :stroke-width="6"
            />
            <div class="progress-text">
              <span>已分配: <span class="text-blue">¥{{ allocatedAmount.toFixed(2) }}</span></span>
              <span v-if="remainingAmount > 0" class="text-red">剩余待付: ¥{{ remainingAmount.toFixed(2) }}</span>
              <span v-else class="text-success">分配完成</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部固定栏 (Footer Slot) -->
    <template #footer>
      <div class="dialog-footer-bar">
        <div class="footer-left">
          <div class="footer-label">实收金额：</div>
          <el-input-number
            v-model="confirmForm.receiveAmount"
            :precision="2"
            :min="0.00"
            :max="99999999"
            :controls="false"
            class="footer-amount-input"
            :disabled="paymentType !== 1" 
            ref="footerAmountRef"
            @keyup.enter="handleConfirm"
          >
            <!-- 非现金支付时禁用实收输入（应自动等于应收），混合支付时也禁用（由上方计算） -->
            <template #prefix>¥</template>
          </el-input-number>
        </div>
        
        <div class="footer-right">
          <el-button @click="handleClose" class="cancel-btn">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleConfirm" 
            :disabled="!canConfirm || props.submitting" 
            :loading="props.submitting" 
            class="confirm-btn"
          >
            确认收款 <span class="shortcut">(Enter)</span>
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  ChatLineRound, CreditCard, Document, Money, Wallet, 
  ArrowLeft, CircleCloseFilled, Lock
} from '@element-plus/icons-vue'
import { getCustomerCoupons } from '@/api/customer'
import type { CartItem, POSCustomerInfo } from '@/types/pos'
import type { CustomerCouponVO } from '@/types/customer'

const props = defineProps<{
  modelValue: boolean
  totalAmount: number
  discountAmount: number
  checkoutData: {
    paymentType: number
    discountAmount: number
    walletAuthCode?: string
    useSpecialFund?: boolean
  }
  cartItems: CartItem[]
  customer: POSCustomerInfo | null
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [data: any]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const footerAmountRef = ref<InstanceType<typeof HTMLInputElement> | null>(null)
const singleAuthInputRef = ref<InstanceType<typeof HTMLInputElement> | null>(null)

// 支付方式
const paymentType = ref(props.checkoutData.paymentType || 1)
const walletAuthCode = ref(props.checkoutData.walletAuthCode || '')

// 优惠券
const availableCoupons = ref<CustomerCouponVO[]>([])
const selectedCouponId = ref<number | null>(null)
const selectedCoupon = computed(() => {
  return availableCoupons.value.find(c => c.id === selectedCouponId.value) || null
})

// 订单金额（不含优惠券）
const baseOrderAmount = computed(() => {
  return Math.max(0, props.totalAmount - props.discountAmount)
})

// 确认表单
const confirmForm = ref({
  receiveAmount: props.totalAmount - props.discountAmount,
  remark: '' 
})

// ==========================================
// 混合支付逻辑重构
// ==========================================
// 支付方式映射配置
const PAYMENT_TYPE_MAP = {
  CASH: 1,
  WECHAT: 2,
  ALIPAY: 3,
  BALANCE: 4,
  CREDIT: 6,
  MIXED: 10
}

// 混合支付详情状态
const mixedDetails = reactive<Record<string, number>>({
  balance: 0,
  wechat: 0,
  alipay: 0,
  cash: 0,
  credit: 0
})

// 混合支付选项配置
const mixedOptions = computed(() => [
  { 
    key: 'balance', 
    label: '余额', 
    subLabel: `可用: ¥${(props.customer?.balance || 0).toFixed(2)}`,
    icon: Wallet, 
    color: '#409eff', 
    bgColor: '#ecf5ff',
    disabled: !props.customer || (props.customer.balance || 0) <= 0
  },
  { key: 'wechat', label: '微信', icon: ChatLineRound, color: '#07c160', bgColor: '#e8f8f0' },
  { key: 'alipay', label: '支付宝', icon: CreditCard, color: '#1677ff', bgColor: '#e6f4ff' },
  { key: 'cash', label: '现金', icon: Money, color: '#67c23a', bgColor: '#f0f9eb' },
  { 
    key: 'credit', 
    label: '赊销/挂账', 
    subLabel: `剩余额度: ¥${((props.customer?.creditLimit || 0) - (props.customer?.totalDebt || 0)).toFixed(2)}`,
    icon: Document, 
    color: '#f56c6c', 
    bgColor: '#fef0f0',
    disabled: !canCredit.value 
  }
])

// 混合支付 - 已分配金额
const allocatedAmount = computed(() => {
  return Object.values(mixedDetails).reduce((sum, val) => sum + (Number(val) || 0), 0)
})

// 混合支付 - 剩余待付
const remainingAmount = computed(() => {
  return Math.max(0, actualAmount.value - allocatedAmount.value)
})

// 进度条百分比
const progressPercentage = computed(() => {
  if (actualAmount.value === 0) return 100
  return Math.min(100, (allocatedAmount.value / actualAmount.value) * 100)
})

// 混合支付是否需要验证码（仅当使用余额时需要）
const needsAuthCodeInMixed = computed(() => {
  return mixedDetails.balance > 0
})

// 自动填充混合支付输入框
const autoFillMixed = (key: string) => {
  if (mixedDetails[key] === 0 && remainingAmount.value > 0) {
    // 如果是余额或专用金，不能超过可用余额
    let fillAmount = remainingAmount.value
    if (key === 'special') {
      fillAmount = Math.min(fillAmount, props.customer?.specialFund || 0)
    } else if (key === 'balance') {
      fillAmount = Math.min(fillAmount, props.customer?.balance || 0)
    }
    // 如果是赊销，校验额度（可选）
    
    mixedDetails[key] = Number(fillAmount.toFixed(2))
  }
}

// 重置混合支付
const resetMixed = () => {
  Object.keys(mixedDetails).forEach(key => mixedDetails[key] = 0)
  walletAuthCode.value = ''
}

// 切换回单一支付
const resetToSinglePayment = () => {
  paymentType.value = 1
  resetMixed()
}

// 监听已分配金额，同步到底部实收金额
watch(allocatedAmount, (val) => {
  if (paymentType.value === PAYMENT_TYPE_MAP.MIXED) {
    confirmForm.value.receiveAmount = val
  }
})

// ==========================================
// 核心逻辑
// ==========================================

// 计算优惠券抵扣金额
const couponDiscount = computed(() => {
  if (selectedCoupon.value) {
    return Math.min(selectedCoupon.value.amount, baseOrderAmount.value)
  }
  return 0
})

// 计算实收金额（扣除优惠金额和优惠券）
const actualAmount = computed(() => {
  return Math.max(0, props.totalAmount - props.discountAmount - couponDiscount.value)
})

// 计算找零
const changeAmount = computed(() => {
  if (paymentType.value === 1) {
    // 现金支付才需要找零
    return Math.max(0, confirmForm.value.receiveAmount - actualAmount.value)
  }
  return 0
})

// 是否有支持专用金的商品
const hasSpecialFundProducts = computed(() => {
  return props.cartItems.some((item) => item.itemType === 1 && item.allowSpecialFund === 1)
})

// 当前优惠券是否满足使用门槛
const isCouponUsable = (coupon: CustomerCouponVO) => {
  return baseOrderAmount.value >= (coupon.minOrderAmount || 0)
}

// 是否可以赊销
const canCredit = computed(() => {
  if (!props.customer) return false
  const creditLimit = props.customer.creditLimit || 0
  const totalDebt = props.customer.totalDebt || 0
  // 简单校验：额度 >= 当前欠款 + 本单金额
  // 实际后端会有更严格校验，前端做基础拦截
  return creditLimit >= totalDebt
})

// 支付方式选项 (网格显示)
const paymentOptions = computed(() => [
  { value: 1, label: '现金', icon: Money, disabled: false },
  { value: 2, label: '微信', icon: ChatLineRound, disabled: false },
  { value: 3, label: '支付宝', icon: CreditCard, disabled: false },
  { value: 4, label: '余额', icon: Wallet, disabled: !props.customer },
  { value: 6, label: '赊销', icon: Document, disabled: !canCredit.value },
  { value: 10, label: '组合/混合支付', icon: Document, disabled: false }
])

// 是否可以确认
const canConfirm = computed(() => {
  // 1. 实收金额必须 > 0 (除非应收本身就是0)
  if (actualAmount.value > 0 && confirmForm.value.receiveAmount <= 0) return false
  
  // 2. 混合支付校验
  if (paymentType.value === PAYMENT_TYPE_MAP.MIXED) {
    // 分配金额必须等于应收金额
    if (Math.abs(allocatedAmount.value - actualAmount.value) > 0.01) return false
    // 如果用了余额，必须有验证码
    if (needsAuthCodeInMixed.value && (!walletAuthCode.value || walletAuthCode.value.length !== 6)) return false
    return true
  }

  // 3. 单一余额支付校验
  if (paymentType.value === 4 && (!walletAuthCode.value || walletAuthCode.value.length !== 6)) {
    return false
  }

  return true
})

// 监听实收金额变化
watch(
  () => actualAmount.value,
  (newAmount) => {
    // 切换优惠券导致应收变化时，同步更新实收
    if (paymentType.value !== 1) { // 现金允许修改，不强行覆盖
       confirmForm.value.receiveAmount = newAmount
    } else if (paymentType.value === 1 && confirmForm.value.receiveAmount < newAmount) {
       // 如果是现金且输入金额小于应收，自动补齐方便操作
       confirmForm.value.receiveAmount = newAmount
    }
  },
  { immediate: true }
)

// 加载客户优惠券
const loadCustomerCoupons = async () => {
  if (!props.customer?.id) {
    availableCoupons.value = []
    return
  }
  try {
    const response = await getCustomerCoupons(props.customer.id, 1, 1, 100)
    availableCoupons.value = response.records || []
  } catch (error) {
    console.error('加载优惠券失败:', error)
    availableCoupons.value = []
  }
}

// 优惠券变化
const handleCouponChange = () => {
  if (selectedCoupon.value && !isCouponUsable(selectedCoupon.value)) {
    ElMessage.warning('当前订单金额未满足该优惠券的使用门槛')
    selectedCouponId.value = null
    return
  }
  // 切换优惠券后，非现金支付方式应自动重置实收金额
  if (paymentType.value !== 1) {
    confirmForm.value.receiveAmount = actualAmount.value
  }
}

// 处理支付方式选择
const handlePaymentSelect = (type: number) => {
  paymentType.value = type
  
  // 如果切到混合支付，初始化
  if (type === 10) {
    resetMixed()
  } else if (type === 4) {
    // 余额支付
    walletAuthCode.value = ''
    nextTick(() => singleAuthInputRef.value?.focus())
  } else {
    // 其他支付
    walletAuthCode.value = ''
  }

  // 非现金支付，实收金额自动锁定为应付金额
  if (type !== 1) {
    confirmForm.value.receiveAmount = actualAmount.value
  }
}

// 监听客户变化
watch(
  () => props.customer?.id,
  () => {
    if (visible.value && props.customer?.id) {
      loadCustomerCoupons()
    }
  }
)

// 弹窗打开初始化
watch(visible, (val) => {
  if (val) {
    paymentType.value = props.checkoutData.paymentType || 1
    walletAuthCode.value = props.checkoutData.walletAuthCode || ''
    selectedCouponId.value = null
    confirmForm.value.receiveAmount = actualAmount.value
    confirmForm.value.remark = ''
    resetMixed()
    
    if (props.customer?.id) {
      loadCustomerCoupons()
    }
    
    nextTick(() => {
      // 默认聚焦底部金额框(现金模式)
      if (paymentType.value === 1) {
        footerAmountRef.value?.focus()
      }
    })
  }
})

// 确认提交
const handleConfirm = () => {
  if (!canConfirm.value) {
    if (paymentType.value === 10) {
      if (Math.abs(allocatedAmount.value - actualAmount.value) > 0.01) {
        return ElMessage.warning(`分配金额与应收不符，差额: ¥${(actualAmount.value - allocatedAmount.value).toFixed(2)}`)
      }
      if (needsAuthCodeInMixed.value && !walletAuthCode.value) {
        return ElMessage.warning('使用了余额，请输入验证码')
      }
    }
    return ElMessage.warning('请检查输入信息')
  }

  if (props.submitting) return

  // 构造基础订单数据
  const orderData: any = {
    customerId: props.customer?.id,
    items: props.cartItems.map((item) => ({
      productId: item.productId,
      packageId: item.packageId,
      itemType: item.itemType,
      quantity: item.quantity,
      unit: item.currentUnit,
      unitPrice: item.unitPrice
    })),
    // 默认支付类型
    paymentType: paymentType.value,
    receiveAmount: confirmForm.value.receiveAmount,
    discountAmount: props.discountAmount || 0,
    walletAuthCode: undefined,
    useSpecialFund: undefined,
    couponId: selectedCouponId.value || undefined,
    remark: confirmForm.value.remark?.trim()
  }

  // 处理单一余额支付
  if (paymentType.value === 4) {
    orderData.walletAuthCode = walletAuthCode.value
    // 单一支付模式下，由后端自动判断是否使用专用金（前端不显式传递 user choice）
    orderData.useSpecialFund = hasSpecialFundProducts.value && (props.customer?.specialFund || 0) > 0
  }

  // 处理混合支付
  if (paymentType.value === 10) {
    orderData.walletAuthCode = needsAuthCodeInMixed.value ? walletAuthCode.value : undefined
    
    // 构造 payments 列表
    const paymentsList = []
    if (mixedDetails.cash > 0) paymentsList.push({ paymentType: 1, amount: mixedDetails.cash })
    if (mixedDetails.wechat > 0) paymentsList.push({ paymentType: 2, amount: mixedDetails.wechat })
    if (mixedDetails.alipay > 0) paymentsList.push({ paymentType: 3, amount: mixedDetails.alipay })
    if (mixedDetails.balance > 0) paymentsList.push({ paymentType: 4, amount: mixedDetails.balance, authCode: walletAuthCode.value || undefined })
    if (mixedDetails.credit > 0) paymentsList.push({ paymentType: 6, amount: mixedDetails.credit })

    orderData.payments = paymentsList

    // 混合模式下，由后端自动判断是否使用专用金（前端不显式传递 user choice）
    orderData.useSpecialFund = mixedDetails.balance > 0 && hasSpecialFundProducts.value
  }

  emit('submit', orderData)
}

const handleClose = () => {
  if (props.submitting) return
  visible.value = false
}
</script>

<style scoped lang="scss">
.checkout-dialog {
  :deep(.el-dialog__body) {
    padding: 20px;
    height: 500px;
    overflow-y: auto;
  }
  :deep(.el-dialog__header) {
    margin-right: 0;
    border-bottom: 1px solid #f0f0f0;
    padding: 15px 20px;
  }
  :deep(.el-dialog__footer) {
    padding: 0;
    border-top: 1px solid #f0f0f0;
  }
}

.checkout-content {
  display: flex;
  gap: 20px;
  height: 100%;
}

/* 左侧面板样式 */
.left-panel {
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding-right: 20px;
  border-right: 1px solid #f0f0f0;

  .section-label {
    font-size: 13px;
    color: #606266;
    margin-bottom: 8px;
  }
  .coupon-info {
      margin-top: 8px;
      padding: 6px 8px;
      background: #f0f7ff;
      border-radius: 4px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 12px;

      .coupon-name {
        color: #333;
      }

      .coupon-amount {
        color: #67c23a;
        font-weight: 500;
      }
    }
  .amount-section {
    .amount-label { font-size: 13px; color: #606266; }
    .amount-value { font-size: 32px; font-weight: bold; color: #409eff; line-height: 1.2; margin-top: 4px; }
  }

  .customer-info-box {
    background: #f8f9fa;
    border-radius: 8px;
    padding: 12px;
    border: 1px solid #ebeef5;

    .customer-name { font-weight: bold; font-size: 15px; color: #303133; display: flex; align-items: center; justify-content: space-between; }
    .customer-phone { font-size: 12px; color: #909399; margin: 4px 0 8px; }
    
    .customer-detail {
      .detail-row {
        display: flex;
        justify-content: space-between;
        font-size: 12px;
        line-height: 1.8;
        .detail-label { color: #606266; }
        .balance { color: #409eff; font-weight: 500; }
        .special-fund { color: #e6a23c; font-weight: 500; }
        .debt { color: #f56c6c; font-weight: 500; }
      }
    }
  }

  .change-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #f0f9eb;
    padding: 10px;
    border-radius: 6px;
    .change-label { color: #67c23a; font-weight: 500; }
    .change-value { font-size: 18px; font-weight: bold; color: #67c23a; }
  }
}

/* 右侧面板样式 */
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;

  .payment-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
    height: 24px;

    .payment-title {
      font-size: 16px;
      font-weight: bold;
      color: #303133;
      
      &.back-btn {
        cursor: pointer;
        display: flex;
        align-items: center;
        gap: 4px;
        color: #606266;
        font-size: 14px;
        &:hover { color: #409eff; }
      }
    }
  }

  /* 支付方式网格 */
  .payment-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;

    .payment-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 10px;
      height: 100px;
      border: 1px solid #dcdfe6;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
      background: #fff;

      &:hover:not(.disabled) {
        border-color: #409eff;
        background-color: #ecf5ff;
        transform: translateY(-2px);
        box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
      }

      &.active {
        border-color: #409eff;
        background-color: #ecf5ff;
        color: #409eff;
        border-width: 2px;
      }

      &.disabled {
        opacity: 0.6;
        cursor: not-allowed;
        background-color: #f5f7fa;
      }

      .payment-name { font-size: 14px; font-weight: 500; }
    }
  }

  /* 混合支付面板 */
  .mixed-panel {
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;

    .mixed-desc {
      font-size: 12px;
      color: #909399;
      margin-bottom: 10px;
    }

    .mixed-list {
      flex: 1;
      overflow-y: auto;
      padding-right: 5px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .mixed-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 15px;
      border: 1px solid #ebeef5;
      border-radius: 8px;
      transition: all 0.2s;

      &:hover { border-color: #c0c4cc; }
      &.is-active { border-color: #409eff; background-color: #fbfdff; }
      &.is-disabled { opacity: 0.6; pointer-events: none; background: #f5f7fa; }

      .row-left {
        display: flex;
        align-items: center;
        gap: 12px;
        
        .icon-wrapper {
          width: 36px;
          height: 36px;
          border-radius: 8px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 20px;
        }

        .row-info {
          display: flex;
          flex-direction: column;
          .row-label { font-weight: 500; font-size: 14px; color: #303133; }
          .row-sub { font-size: 12px; color: #909399; margin-top: 2px; }
        }
      }

      .row-right {
        display: flex;
        align-items: center;
        gap: 8px;
        
        .mixed-amount-input {
          width: 130px;
          :deep(.el-input__wrapper) { box-shadow: none; background: transparent; text-align: right; }
          :deep(.el-input__inner) { text-align: right; font-weight: bold; font-size: 15px; }
        }

        .clear-icon {
          color: #c0c4cc;
          cursor: pointer;
          &:hover { color: #f56c6c; }
        }
      }
    }

    .mixed-auth-code {
      margin-top: 10px;
      padding: 0 2px;
    }

    .progress-section {
      margin-top: 15px;
      padding-top: 15px;
      border-top: 1px dashed #ebeef5;

      .progress-text {
        display: flex;
        justify-content: space-between;
        margin-top: 8px;
        font-size: 13px;
        font-weight: 500;
        
        .text-blue { color: #409eff; font-weight: bold; }
        .text-red { color: #f56c6c; font-weight: bold; }
        .text-success { color: #67c23a; font-weight: bold; }
      }
    }
  }
}

/* 底部固定栏样式 */
.dialog-footer-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 25px;
  background-color: #fff;

  .footer-left {
    display: flex;
    align-items: center;
    .footer-label { font-size: 16px; color: #303133; font-weight: 500; }
    
    .footer-amount-input {
      width: 180px;
      margin-left: 10px;
      
      :deep(.el-input__wrapper) {
        box-shadow: none !important;
        background: transparent;
        padding: 0;
      }
      :deep(.el-input__inner) {
        font-size: 28px;
        font-weight: bold;
        color: #303133;
        text-align: left;
        padding-left: 5px;
      }
    }
  }

  .footer-right {
    display: flex;
    gap: 12px;

    .cancel-btn {
      height: 44px;
      font-size: 15px;
      width: 120px;
    }

    .confirm-btn {
      height: 44px;
      font-size: 16px;
      width: 400px;
      font-weight: 500;
      
      .shortcut {
        font-size: 12px;
        opacity: 0.8;
        margin-left: 4px;
      }
    }
  }
}
</style>
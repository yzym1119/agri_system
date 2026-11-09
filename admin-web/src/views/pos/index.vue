<template>
  <div class="pos-page">
    <el-alert v-if="checkingStock" title="正在核对整单物料库存，请稍候" type="info" :closable="false" />
    <section v-if="pendingSale || saleStorageError" class="sale-pending" aria-live="polite">
      <el-alert type="warning" :closable="false" show-icon title="本单正在提交或结果待核对，请勿再次收款" />
      <template v-if="pendingSale">
        <p>提交时间：{{ new Date(pendingSale.submittedAt).toLocaleString() }} · 门店 {{ pendingSale.storeId }} · 员工 {{ pendingSale.staffId }}</p>
        <p>商品金额 ¥{{ pendingSale.totalAmount.toFixed(2) }} · 净应结算 ¥{{ pendingSale.netAmount.toFixed(2) }} · {{ pendingSale.lines.length }} 条物料</p>
        <p>客户：{{ pendingSale.customerId == null ? '散客' : `客户 ${pendingSale.customerId}` }}</p>
        <ul><li v-for="(line, index) in pendingSale.lines" :key="index">商品 {{ line.productId }}{{ line.packageId ? `（套餐 ${line.packageId}）` : '' }}：{{ line.quantity }} {{ line.unit }}，¥{{ line.totalPrice.toFixed(2) }}</li></ul>
        <template v-if="pendingSale.requestKey">
          <p class="request-number">收银请求号：{{ pendingSale.requestKey }}</p>
          <p>连接中断或登录过期不能证明未成交。先核对本次请求，不要再次向客户收款。</p>
          <el-button :loading="recoveryLoading" :disabled="submittingOrder" @click="checkPendingSubmission">核对本次收银结果</el-button>
          <div v-if="recoveryResult" class="recovery-result">
            <template v-if="recoveryResult.status === 'COMPLETED'">
              <p>本次请求已成交，订单ID {{ recoveryResult.orderId }}。核对后结束本单，不会再次收款或扣库存。</p>
              <el-button @click="router.push({ name: 'OrderList' })">查看订单中心</el-button>
              <el-checkbox v-model="recoveryConfirmed" :disabled="recoveryLoading">我已确认本次订单已成交</el-checkbox>
              <el-button type="primary" :disabled="!recoveryConfirmed || submittingOrder" :loading="recoveryLoading" @click="finishCompletedSubmission">结束已成交单据</el-button>
            </template>
            <p v-else-if="recoveryResult.status === 'CANCELLED'">后台已结束本次请求，迟到的原请求不能再成交。确认下方事项后恢复收银。</p>
            <p v-else>尚未取得已提交的成交结果，原请求可能仍在处理中，当前不能直接重新收款。</p>
          </div>
          <template v-if="recoveryResult?.status !== 'COMPLETED' && !submittingOrder">
            <p>需要重新开单时，先由后台结束本次未成交请求。若原请求已成交，会返回原订单供你核对。</p>
            <el-checkbox v-model="recoveryEndConfirmed" :disabled="recoveryLoading">我已核对客户实际交款；结束系统请求不等于退回现金或外部支付</el-checkbox>
            <el-button :disabled="!recoveryEndConfirmed" :loading="recoveryLoading" @click="endPendingSubmission">结束未成交请求并恢复收银</el-button>
          </template>
        </template>
        <template v-else>
          <p>这是旧版未记录请求号的单据，只能人工核对已成交原单，不能自动判定未成交。</p>
        <p>连接中断不能证明未成交。请先到订单中心核对；未找到也不能直接重收。</p>
        <el-button @click="router.push({ name: 'OrderList' })">去订单中心核对</el-button>
        <template v-if="!submittingOrder">
          <p>找到原单后，粘贴订单中心可复制的完整订单号，只读核对原单事实。</p>
          <el-input v-model="recoveryOrderId" placeholder="已找到的完整订单号" aria-label="已找到的原订单号" @input="recoveryCandidate = null; recoveryConfirmed = false" />
          <el-button :loading="recoveryLoading" @click="verifyRecoveredSale">读取并核对原单</el-button>
          <template v-if="recoveryCandidate">
            <p>已核对订单 {{ recoveryCandidate.orderNo }}，门店、员工、客户、金额及物料一致。相同内容的另一笔订单仍不能代替本次成交单。</p>
            <el-checkbox v-model="recoveryConfirmed">我已人工确认这是本次收款产生的原订单</el-checkbox>
            <el-button type="primary" :disabled="!recoveryConfirmed" :loading="recoveryLoading" @click="confirmRecoveredSale">确认已成交，结束本单</el-button>
          </template>
        </template>
        </template>
      </template>
      <p v-else>无法读取本机待核对记录，已阻止新收款。请保留当前页面并联系管理员核对，不要清除浏览器记录。</p>
    </section>
    <div v-else class="pos-container">
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
            @unit-change="handleUnitChange"
            @customer-change="handleCustomerChange"
            @package-change="handlePackageQuoteChange"
          />
        </div>
        
        <!-- 服务端报价状态：报价失败阻止收款 -->
        <div v-if="hasPriceFallback" class="price-fallback-hint">
          <el-icon><WarningFilled /></el-icon>
          <span>部分商品尚未取得有效报价，请重新报价后收款。</span>
          <el-button size="small" @click="recalculateCartPrices(true)">重新报价</el-button>
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

    <!-- 库房取货待确认（后仓拣货，不即时出库） -->
    <el-dialog v-model="pickupVisible" title="库房取货" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="本单含库房取货商品"
        description="已随订单生成取货请求，请在「取货」工作台扫描批次标签或货位编码确认。若工作台未见该订单的取货请求，可点击补登记。"
        class="pickup-alert"
      />
      <div v-for="item in pickupItems" :key="item.productId" class="pickup-row">
        <span class="pickup-name">{{ item.name }}</span>
        <el-button
          size="small"
          :loading="pickupSubmittingId === item.productId"
          @click="handleRegisterPickup(item)"
        >
          补登记取货请求
        </el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="pickupVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { createPickupRequest, createPOSOrder, getOrderDetail, getOrderList, getPOSSubmission, cancelPOSSubmission } from '@/api/order'
import {useUserStore} from '@/store/user'
import {checkPosStock} from '@/api/pos-catalog'
import CategorySidebar from './components/CategorySidebar.vue'
import ModernGoodsPanel from './components/ModernGoodsPanel.vue'
import ModernCartList from './components/ModernCartList.vue'
import ModernSettlementBar from './components/ModernSettlementBar.vue'
import CheckoutDialog from './components/CheckoutDialog.vue'
import type {CartItem, POSCustomerInfo} from '@/types/pos'
import { fetchServerPriceQuote, fetchServerPackageQuote } from '@/utils/posPricing'
import { subtotalAmount } from '@/utils/money'
import type { ServerPriceQuote, ServerPackageQuote } from '@/utils/posPricing'

const userStore = useUserStore()
const router = useRouter()

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
let quoteRequestSequence = 0
let cartGeneration = 0

// 选中的分类
const selectedCategoryId = ref<number | null>(null)
const categoryTree = ref<any[]>([])

// 结算弹窗
const checkoutVisible = ref(false)
const checkingStock = ref(false)
const submittingOrder = ref(false)
type SaleLine = { productId: number; packageId: number | null; unit: string; quantity: number; totalPrice: number }
type PendingSale = { version: 1; requestKey?: string; storeId: number; staffId: number; customerId: number | null;
  submittedAt: number; totalAmount: number; netAmount: number; discountAmount: number; paymentType: number;
  payments: { paymentType: number; amount: number }[]; lines: SaleLine[] }
const pendingSale = ref<PendingSale | null>(null)
const saleStorageError = ref(false)
const recoveryOrderId = ref('')
const recoveryCandidate = ref<any>(null)
const recoveryConfirmed = ref(false)
const recoveryLoading = ref(false)
const recoveryResult = ref<{requestKey: string; status: string; orderId: number | null} | null>(null)
const recoveryEndConfirmed = ref(false)
const actorKey = () => `pos_pending_sale_v1_${userStore.userInfo?.id || 0}_${getUserStoreId() || 0}`
const actorSession = () => `${userStore.token || ''}|${actorKey()}`
const saleLocked = () => !!pendingSale.value || saleStorageError.value || submittingOrder.value || checkingStock.value
const loadPendingSale = () => {
  pendingSale.value = null
  saleStorageError.value = false
  try {
    const raw = sessionStorage.getItem(actorKey())
    if (raw) {
      const saved = JSON.parse(raw)
      if (saved.version !== 1 || saved.staffId !== staffId.value || saved.storeId !== storeId.value
        || !Array.isArray(saved.lines) || !saved.lines.length || saved.lines.length > 500
        || ![saved.netAmount, saved.totalAmount, saved.discountAmount, saved.submittedAt].every(Number.isFinite)
        || saved.lines.some((line: SaleLine) => !Number.isFinite(line.totalPrice))) throw new Error('待核对记录不完整')
      pendingSale.value = saved
    }
  } catch { saleStorageError.value = true }
}
loadPendingSale()
watch(() => actorSession(), () => {
  cartGeneration++
  storeId.value = getUserStoreId()
  staffId.value = userStore.userInfo?.id || null
  cartItems.value = []
  selectedCustomer.value = null
  submittingOrder.value = false
  checkingStock.value = false
  checkoutVisible.value = false
  pickupVisible.value = false
  recoveryCandidate.value = null
  recoveryConfirmed.value = false
  recoveryOrderId.value = ''
  recoveryLoading.value = false
  recoveryResult.value = null
  recoveryEndConfirmed.value = false
  loadPendingSale()
}, { flush: 'sync' })

const definitelyRejectedSale = (error: any) => {
  const status = Number(error?.response?.status)
  if (status >= 500 || status === 408) return false
  const code = Number(error?.apiBusinessCode)
  return [400, 401, 403, 404, 409, 422].includes(status)
    || (Number.isFinite(code) && code > 0 && ![10002, 10003].includes(code))
}
// 保存有限、无验证码/地址/客户姓名的核对摘要，不保存可重发的支付请求。
const saleLines = (items: CartItem[]): SaleLine[] => items.flatMap(item => item.itemType === 2
  ? (item.packageItems || []).map(material => ({ productId: material.productId, packageId: item.packageId || null,
    unit: material.unit || '', quantity: Number(material.quantity), totalPrice: Number(material.totalPrice) }))
  : [{ productId: item.productId!, packageId: null, unit: item.currentUnit,
    quantity: item.quantity, totalPrice: item.subtotal }])
const removePendingRecord = (key: string, saved: string) => {
  if (sessionStorage.getItem(key) !== saved) throw Error('本机收银记录已变化，请保留待核对状态')
  sessionStorage.removeItem(key)
  if (sessionStorage.getItem(key) != null) throw Error('本机收银记录未能结束，请重新核对')
}
const groupedSaleLines = (lines: SaleLine[]) => {
  const groups: Record<string, { quantity: number; cents: number }> = {}
  for (const line of lines) {
    if (!line.productId || !Number.isFinite(line.quantity) || line.quantity <= 0 || !Number.isFinite(line.totalPrice)) return null
    const key = JSON.stringify([line.productId, line.packageId || null, line.unit || ''])
    const group = groups[key] ||= { quantity: 0, cents: 0 }
    group.quantity += Math.round(line.quantity * 10000)
    group.cents += Math.round(line.totalPrice * 100)
  }
  return JSON.stringify(Object.entries(groups).sort(([a], [b]) => a.localeCompare(b)))
}
const matchesPendingSale = (detail: any, snapshot: PendingSale) => {
  const sameMoney = (a: any, b: number) => a != null && Number.isFinite(Number(a)) && Math.round(Number(a) * 100) === Math.round(b * 100)
  const expectedLines = groupedSaleLines(snapshot.lines)
  const createdAt = typeof detail?.createdAt === 'string' ? Date.parse(detail.createdAt) : NaN
  const groupedPayments = (payments: any[]) => {
    const grouped: Record<string, number> = {}
    for (const payment of payments) {
      const type = Number(payment.paymentType), amount = Number(payment.amount)
      if (![1, 2, 3, 4, 5, 6, 8].includes(type) || !Number.isFinite(amount) || amount < 0) return null
      if (type === 5 || amount === 0) continue // 券额已由原总额、优惠和净额共同核对。
      const key = String(type === 8 ? 4 : type) // 钱包实际拆为本金/专用金，合计须等于本次钱包支付。
      grouped[key] = (grouped[key] || 0) + Math.round(amount * 100)
    }
    return JSON.stringify(Object.entries(grouped).sort(([a], [b]) => a.localeCompare(b)))
  }
  const expectedPayments = groupedPayments(snapshot.payments)
  const requestedTypes = [...new Set(snapshot.payments.filter(payment => payment.amount > 0).map(payment => payment.paymentType))]
  const expectedMethod = snapshot.paymentType === 10 && requestedTypes.length === 1 ? requestedTypes[0] : snapshot.paymentType
  return detail?.orderType === 1 && [2, 3, 4, 5, 6, 8, 9, 10].includes(detail.orderStatus)
    && Number.isFinite(createdAt) && createdAt >= snapshot.submittedAt - 5000 && createdAt <= Date.now()
    && detail.paymentMethod === expectedMethod
    && expectedPayments !== null && Array.isArray(detail.payments) && expectedPayments === groupedPayments(detail.payments)
    && Number(detail.storeId) === snapshot.storeId && Number(detail.operatorStaffId) === snapshot.staffId
    && (detail.customerId ?? null) === snapshot.customerId
    && sameMoney(detail.totalAmount, snapshot.totalAmount) && sameMoney(detail.payAmount, snapshot.netAmount)
    && sameMoney(detail.discountAmount ?? 0, snapshot.discountAmount)
    && expectedLines !== null && expectedLines === groupedSaleLines((detail.items || []).map((item: any) => ({
      productId: item.productId, packageId: item.packageId ?? null, unit: item.unit || '', quantity: Number(item.quantity), totalPrice: Number(item.totalPrice)
    })))
}
const verifyRecoveredSale = async () => {
  recoveryCandidate.value = null
  recoveryConfirmed.value = false
  if (submittingOrder.value || recoveryLoading.value || !pendingSale.value) return
  if (!recoveryOrderId.value.trim() || recoveryOrderId.value.trim().length > 50) {
    ElMessage.warning('请粘贴订单中心已找到的完整订单号')
    return
  }
  const session = actorSession(), snapshot = pendingSale.value, id = recoveryOrderId.value
  recoveryLoading.value = true
  try {
    const page = await getOrderList({ keyword: id.trim(), pageNum: 1, pageSize: 20 })
    if (session !== actorSession() || snapshot !== pendingSale.value || id !== recoveryOrderId.value) return
    const matches = page.records.filter(row => row.orderNo === id.trim())
    if (matches.length !== 1) {
      ElMessage.warning('未找到唯一的完整订单号，保留待核对状态；请继续人工核对')
      return
    }
    const detail = await getOrderDetail(matches[0].id)
    if (session !== actorSession() || snapshot !== pendingSale.value || id !== recoveryOrderId.value) return
    if (detail.id !== matches[0].id || detail.orderNo !== id.trim() || !matchesPendingSale(detail, snapshot)) {
      ElMessage.warning('原单门店、员工、客户、金额或物料不一致，不能解除待核对状态')
      return
    }
    recoveryCandidate.value = detail
  } catch {
    if (session === actorSession()) ElMessage.warning('未能确认原订单；未找到不代表未成交，请继续人工核对')
  } finally { if (session === actorSession()) recoveryLoading.value = false }
}
const confirmRecoveredSale = async () => {
  if (!recoveryConfirmed.value || !recoveryCandidate.value || !pendingSale.value || recoveryLoading.value) return
  const snapshot = pendingSale.value, session = actorSession(), key = actorKey()
  const candidateId = recoveryCandidate.value.id
  recoveryLoading.value = true
  try {
    const detail = await getOrderDetail(candidateId)
    if (session !== actorSession() || snapshot !== pendingSale.value) return
    if (detail.id !== candidateId || detail.orderNo !== recoveryOrderId.value.trim() || !matchesPendingSale(detail, snapshot)) {
      recoveryCandidate.value = null
      recoveryConfirmed.value = false
      ElMessage.warning('原单核验已变化，请保留待核对状态')
      return
    }
    removePendingRecord(key, JSON.stringify(snapshot))
    pendingSale.value = null
    handleClearCart()
    recoveryCandidate.value = null
    recoveryConfirmed.value = false
    ElMessage.success('已确认原单成交，本单已结束；未再次收款')
  } catch { if (session === actorSession()) ElMessage.warning('核验未完成，保留待核对状态') }
  finally { if (session === actorSession()) recoveryLoading.value = false }
}

// 请求结果由后台同一请求行锁决定；GET未找到从不解除冻结。
const readSubmissionResult = async (action: 'read' | 'cancel' | 'finish') => {
  const snapshot = pendingSale.value
  if (!snapshot?.requestKey || submittingOrder.value || recoveryLoading.value) return
  if (action === 'cancel' && !recoveryEndConfirmed.value) return
  if (action === 'finish' && (!recoveryConfirmed.value || recoveryResult.value?.status !== 'COMPLETED')) return
  const session = actorSession(), key = actorKey(), expectedOrder = recoveryResult.value?.orderId
  const current = () => session === actorSession() && pendingSale.value === snapshot
  recoveryLoading.value = true
  try {
    const result = action === 'cancel' ? await cancelPOSSubmission(snapshot.requestKey) : await getPOSSubmission(snapshot.requestKey)
    if (!current()) return
    if (!result || result.requestKey !== snapshot.requestKey || !['UNKNOWN','PENDING','COMPLETED','CANCELLED'].includes(result.status)
      || (result.status === 'COMPLETED' && (!Number.isSafeInteger(result.orderId) || Number(result.orderId) <= 0))) throw Error('请求核对结果不完整，请重新核对')
    recoveryResult.value = result
    if (action === 'read') { recoveryConfirmed.value = false; return }
    if (action === 'finish' && (result.status !== 'COMPLETED' || result.orderId !== expectedOrder)) {
      recoveryConfirmed.value = false; ElMessage.warning('成交结果已变化，请重新核对'); return
    }
    if (action === 'cancel' && result.status !== 'CANCELLED') {
      recoveryConfirmed.value = false; return
    }
    removePendingRecord(key, JSON.stringify(snapshot))
    pendingSale.value = null
    checkoutVisible.value = false
    recoveryResult.value = null
    recoveryConfirmed.value = false
    recoveryEndConfirmed.value = false
    if (action === 'finish') handleClearCart()
    ElMessage.success(action === 'finish' ? '已结束原成交单据，未再次收款' : '原请求已结束，可以继续收银；请先核对实际交款')
    goodsPanelRef.value?.refresh()
  } catch (error: any) {
    if (current()) ElMessage.warning(error?.message || '本次请求尚未核对完成，请保留待核对状态')
  } finally { if (session === actorSession()) recoveryLoading.value = false }
}
const checkPendingSubmission = () => readSubmissionResult('read')
const endPendingSubmission = () => readSubmissionResult('cancel')
const finishCompletedSubmission = () => readSubmissionResult('finish')

// 库房取货（后仓拣货）待确认状态
const pickupVisible = ref(false)
const pickupOrderId = ref<number | null>(null)
const pickupItems = ref<{ productId: number; name: string }[]>([])
const pickupSubmittingId = ref<number | null>(null)
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
  return Math.round(cartItems.value.reduce((sum, item) => sum + item.subtotal, 0) * 100) / 100
})

// 是否存在未完成的报价（报价失败阻止收款）
const hasPriceFallback = computed(() => cartItems.value.some((item) => item.priceFallback || item.pricePending || !item.quoteId))

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
  if (saleLocked()) return
  const typingTarget = isTypingTarget(e.target)

  // F1: 聚焦搜索
  if (e.key === 'F1') {
    e.preventDefault()
    goodsPanelRef.value?.focusSearch()
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
  cartGeneration++
  window.removeEventListener('keydown', handleKeydown)
})

/** 记录服务端报价结果（单据审计依据），报价失败时阻止结算 */
const applyServerQuote = (item: CartItem, quote: ServerPriceQuote | ServerPackageQuote) => {
  item.quoteId = quote.quoteId ?? null
  item.belowReference = !!quote.belowReference
  item.referenceFloor = quote.referenceFloor ?? null
  item.priceFallback = false
  item.suggestedUnitPrice = quote.suggestedUnitPrice
  item.pricePending = false
}

const markPriceFallback = (item: CartItem) => {
  item.quoteId = null
  item.belowReference = false
  item.referenceFloor = null
  item.priceFallback = true
  item.pricePending = false
}

/**
 * 解析购物车行单价：
 * 商品与套餐均取服务端报价；套餐的物料、金额和专用金额度不在前端拆算。
 */
const resolveCartItemUnitPrice = async (item: CartItem, preserveNegotiation = false): Promise<number | null> => {
  if (saleLocked()) return null
  if ((item.itemType === 1 && item.productId) || (item.itemType === 2 && item.packageId)) {
    const isPackage = item.itemType === 2
    const previousSuggested = item.suggestedUnitPrice
    const previousDeal = item.unitPrice
    const previousReason = item.priceChangeReason
    const previousQuoteId = item.quoteId
    const hadNegotiation = previousSuggested != null && previousDeal !== previousSuggested
      && (isPackage || !!previousReason?.trim())
    const requestId = ++quoteRequestSequence
    item.quoteRequestId = requestId
    item.pricePending = true
    item.quoteId = null
    const customerId = selectedCustomer.value?.id
    const saleUnit = item.currentUnit
    const quantity = item.quantity
    const requestIsCurrent = () => !saleLocked() && item.quoteRequestId === requestId
      && selectedCustomer.value?.id === customerId && item.currentUnit === saleUnit
      && (!isPackage || item.quantity === quantity)
    try {
      let quote: ServerPriceQuote | ServerPackageQuote = isPackage
        ? await fetchServerPackageQuote({ packageId: item.packageId!, storeId: storeId.value, customerId, quantity,
          dealUnitPrice: preserveNegotiation && hadNegotiation ? previousDeal : undefined })
        : await fetchServerPriceQuote({ productId: item.productId!, storeId: storeId.value,
          channel: 'POS', customerId, saleUnit })
      if (!requestIsCurrent()) return null
      if (!(Number(quote.suggestedUnitPrice) > 0) || !quote.quoteId) throw new Error('未取得有效售价')
      const keepNegotiation = preserveNegotiation && hadNegotiation
        && previousSuggested === Number(quote.suggestedUnitPrice)
        && (!isPackage || previousQuoteId === quote.quoteId)
      if (isPackage && preserveNegotiation && hadNegotiation && !keepNegotiation) {
        ElMessage.warning(`${item.name}价格或组成已变化，原议价请重新确认`)
        quote = await fetchServerPackageQuote({ packageId: item.packageId!, storeId: storeId.value, customerId, quantity })
        if (!requestIsCurrent()) return null
      } else if (isPackage && preserveNegotiation && previousQuoteId && previousQuoteId !== quote.quoteId) {
        ElMessage.warning(`${item.name}价格或组成已变化，请核对套餐物料`)
      }
      applyServerQuote(item, quote)
      if (isPackage && 'items' in quote) {
        item.packageItems = quote.items
        item.specialFundEligibleAmount = Number(quote.specialFundEligibleAmount)
        item.unitPrice = Number(quote.dealUnitPrice)
        item.subtotal = Number(quote.totalAmount)
        // 预览期间收银员可能已填写原因，同版本议价不能用请求开始时的旧值覆盖。
        if (!keepNegotiation) item.priceChangeReason = undefined
        return item.unitPrice
      }
      if (keepNegotiation) {
        item.belowReference = quote.referenceFloor != null && previousDeal < quote.referenceFloor
        return previousDeal
      }
      if (preserveNegotiation && hadNegotiation) ElMessage.warning(`${item.name}报价已变化，原议价请重新确认`)
      item.priceChangeReason = undefined
      return Number(quote.suggestedUnitPrice)
    } catch (error) {
      if (item.quoteRequestId === requestId) markPriceFallback(item)
      console.warn('商品报价失败，结算已阻止:', error)
      return null
    }
  }

  markPriceFallback(item)
  return null
}

// 添加商品到购物车
const handleAddToCart = async (item: CartItem) => {
  if (saleLocked()) return
  const generation = cartGeneration
  const resolvedPrice = await resolveCartItemUnitPrice(item)
  if (generation !== cartGeneration || saleLocked()) return
  if (resolvedPrice !== null) {
    item.unitPrice = resolvedPrice
    if (item.itemType === 1) item.subtotal = subtotalAmount(item.quantity, item.unitPrice)
  }

  if (resolvedPrice === null) {
    ElMessage.error('商品报价失败，请检查商品价格与单位后重试')
    return
  }

  // 检查是否已存在
  const existingIndex = cartItems.value.findIndex(
    (cartItem) =>
      cartItem.itemType === item.itemType &&
      ((item.itemType === 1 && cartItem.productId === item.productId) ||
        (item.itemType === 2 && cartItem.packageId === item.packageId)) &&
      cartItem.currentUnit === item.currentUnit &&
      (item.itemType === 1 || (cartItem.unitPrice === item.unitPrice
        && cartItem.quoteId === item.quoteId && cartItem.priceChangeReason === item.priceChangeReason))
  )

  if (existingIndex >= 0) {
    // 已存在，增加数量
    const existing = cartItems.value[existingIndex]
    existing.quantity = (Math.round(existing.quantity * 10000) + Math.round(item.quantity * 10000)) / 10000
    if (existing.itemType === 2) await resolveCartItemUnitPrice(existing, true)
    else existing.subtotal = subtotalAmount(existing.quantity, existing.unitPrice)
  } else {
    // 新增
    cartItems.value.push({
      ...item,
      id: `${item.itemType}_${item.productId || item.packageId}_${Date.now()}_${item.quoteRequestId}`
    })
  }
}

// 重新计算购物车中所有商品的价格
const recalculateCartPrices = async (preserveNegotiation = false) => {
  if (saleLocked()) return
  if (!cartItems.value || cartItems.value.length === 0) return
  
  const updatedItems = [...cartItems.value]

  for (const item of updatedItems) {
    const newPrice = await resolveCartItemUnitPrice(item, preserveNegotiation)
    if (newPrice !== null && newPrice !== item.unitPrice) {
      item.unitPrice = newPrice
      item.subtotal = subtotalAmount(item.quantity, item.unitPrice)
    }
  }

  // 异步报价期间可能删除或清空购物车，只刷新当前集合，不能恢复旧快照。
  cartItems.value = [...cartItems.value]
}

// 客户变更
const handleCustomerChange = async (customer: POSCustomerInfo | null) => {
  if (saleLocked()) return
  selectedCustomer.value = customer
  // 重新计算购物车中所有商品的价格
  await recalculateCartPrices()
}

// 购物车更新：数量/删除/库房取货等本地变更，仅同步数组引用
const handleCartUpdate = (items: CartItem[]) => {
  if (saleLocked()) return
  cartItems.value = items
}

// 单位切换：按新的销售单位重新取服务端报价，并刷新行内标记（低于参考下限/报价失败）
const handleUnitChange = async (payload: { id: string; oldUnit: string; newUnit: string }) => {
  if (saleLocked()) return
  const item = cartItems.value.find((cartItem) => cartItem.id === payload.id)
  if (item && item.itemType === 1 && item.productId) {
    const newPrice = await resolveCartItemUnitPrice(item)
    if (newPrice !== null && newPrice !== item.unitPrice) {
      item.unitPrice = newPrice
      item.subtotal = subtotalAmount(item.quantity, item.unitPrice)
    }
  }
  // 无论报价结果如何都重新赋值，确保 belowReference/priceFallback 标签按最新报价结果刷新
  cartItems.value = [...cartItems.value]
}

// 套餐数量或成交单价改变后重新取权威物料金额和专用金额度。
const handlePackageQuoteChange = async ({ id }: { id: string }) => {
  if (saleLocked()) return
  const item = cartItems.value.find(line => line.id === id && line.itemType === 2)
  if (item) await resolveCartItemUnitPrice(item, true)
  cartItems.value = [...cartItems.value]
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
const handleRestoreCart = async (payload: { cartItems: CartItem[]; customer: POSCustomerInfo | null; discountAmount: number }) => {
  if (saleLocked()) return
  cartGeneration++
  cartItems.value = payload.cartItems
  selectedCustomer.value = payload.customer
  // 设置优惠金额
  settlementBarRef.value?.setDiscountAmount(payload.discountAmount)
  await recalculateCartPrices(true)
}

// 清空购物车（挂单后）
const handleClearCart = () => {
  if (saleLocked()) return
  cartGeneration++
  cartItems.value = []
  selectedCustomer.value = null
}

// 结算
const handleCheckout = async (data: {
  paymentType: number
  discountAmount: number
  walletAuthCode?: string
  useSpecialFund?: boolean
}) => {
  if (saleLocked()) return
  if (cartItems.value.length === 0) {
    ElMessage.warning('购物车为空')
    return
  }
  if (cartItems.value.some(item => item.itemType === 1 && (item.packageId != null || item.fromPackage))) {
    ElMessage.error('挂单含旧套餐拆分行，请移除后重新选择套餐')
    return
  }
  if (hasPriceFallback.value) {
    ElMessage.error('请等待报价完成或重新报价后结算')
    return
  }
  if (cartItems.value.some(item => (item.unitPrice !== item.suggestedUnitPrice || item.belowReference) && !item.priceChangeReason?.trim())) {
    ElMessage.error('请填写议价或低于最低价的原因')
    return
  }
  if (!storeId.value) { ElMessage.warning('请先确认当前门店'); return }
  const session=actorSession(), generation=cartGeneration
  const state=()=>JSON.stringify({cart:cartItems.value,customer:selectedCustomer.value?.id||null,store:storeId.value})
  const snapshot=state(), options=JSON.parse(JSON.stringify(data))
  checkingStock.value=true
  try {
    const result=await checkPosStock({storeId:storeId.value,customerId:selectedCustomer.value?.id||null,
      items:cartItems.value.map(item=>item.itemType===2
        ? {itemType:2,packageId:item.packageId,quantity:item.quantity,packageQuoteId:item.quoteId}
        : {itemType:1,productId:item.productId,quantity:item.quantity,unit:item.currentUnit})})
    if(session!==actorSession()||generation!==cartGeneration)return
    if(snapshot!==state()||hasPriceFallback.value) {ElMessage.warning('库存核对期间清单或客户发生变化，请重新核对');return}
    if(!result.sufficient) {ElMessage.warning(result.issues.slice(0,3).join('；'));return}
    checkoutData.value=options
    checkoutVisible.value=true
  } catch {
    if(session===actorSession()&&generation===cartGeneration)ElMessage.warning('整单库存核对未完成，请刷新商品后重试；尚未提交收款')
  } finally {if(session===actorSession())checkingStock.value=false}
}

// 提交订单
const handleSubmitOrder = async (orderData: any, submittedNetAmount?: number) => {
  if (saleLocked()) {
    ElMessage.warning('本单正在提交或结果待核对，请勿再次收款')
    return
  }

  if (hasPriceFallback.value) {
    ElMessage.error('商品报价未完成，暂不能提交订单')
    return
  }
  if (!storeId.value || !staffId.value || !cartItems.value.length) return
  const session = actorSession(), key = actorKey()
  // 支付请求固定为提交瞬间副本；摘要不保留验证码、客户联系方式或可重发数据。
  let requestKey: string
  try { requestKey = crypto.randomUUID() }
  catch { ElMessage.error('无法建立安全收银请求号，暂不能收款'); return }
  const request = JSON.parse(JSON.stringify({ ...orderData, requestKey, storeId: storeId.value, staffId: staffId.value }))
  const snapshot: PendingSale = { version: 1, requestKey, storeId: storeId.value, staffId: staffId.value,
    customerId: orderData.customerId ?? null, submittedAt: Date.now(), totalAmount: totalAmount.value,
    netAmount: submittedNetAmount ?? (orderData.couponId ? NaN : totalAmount.value - Number(orderData.discountAmount || 0)),
    discountAmount: Number(orderData.discountAmount || 0), paymentType: orderData.paymentType,
    payments: orderData.paymentType === 10 ? (orderData.payments || []).map((payment: any) => ({ paymentType: payment.paymentType, amount: payment.amount }))
      : [{ paymentType: orderData.paymentType, amount: submittedNetAmount ?? totalAmount.value - Number(orderData.discountAmount || 0) }],
    lines: saleLines(cartItems.value) }
  if (![snapshot.netAmount, snapshot.totalAmount, snapshot.discountAmount].every(Number.isFinite)
    || !snapshot.lines.length || snapshot.lines.length > 500 || !groupedSaleLines(snapshot.lines)) {
    ElMessage.error('未取得完整结算快照，请重新核对商品报价后收款')
    return
  }
  const saved = JSON.stringify(snapshot)
  try { sessionStorage.setItem(key, saved) }
  catch { ElMessage.error('无法保存本次收款核对记录，暂不能收款'); return }
  pendingSale.value = snapshot
  submittingOrder.value = true

  // 提交前收集库房取货商品（订单创建后购物车会被清空）
  const pickupFlaggedItems = cartItems.value
    .filter((item) => item.warehousePickup)
    .flatMap(item => item.itemType === 2
      ? (item.packageItems || []).map(material => ({ productId: material.productId, name: material.productName || item.name }))
      : [{ productId: item.productId as number, name: item.name }])

  try {
    const createdOrderId = await createPOSOrder(request)
    if (!Number.isSafeInteger(Number(createdOrderId)) || Number(createdOrderId) <= 0) throw new Error('未收到有效订单号，结果待核对')
    removePendingRecord(key, saved)
    if (session !== actorSession()) return
    pendingSale.value = null
    submittingOrder.value = false

    ElMessage.success(orderData.deliveryType === 2 ? '订单创建成功，待配送' : '订单创建成功')
    
    // 先关闭弹窗，避免状态混乱
    checkoutVisible.value = false
    
    // 清空购物车
    handleClearCart()

    // 刷新商品列表（更新库存）
    goodsPanelRef.value?.refresh()

    // 库房取货提示：后端已随订单自动生成取货请求，此处仅提示并提供补登记入口
    if (orderData.deliveryType !== 2 && pickupFlaggedItems.length > 0 && createdOrderId) {
      pickupOrderId.value = Number(createdOrderId)
      pickupItems.value = pickupFlaggedItems
      pickupVisible.value = true
    }
  } catch (error: any) {
    if (session !== actorSession()) {
      // 只清理明确拒绝的原记录，不触碰另一员工的界面或核对草稿。
      if (!snapshot.requestKey && definitelyRejectedSale(error)) {
        try { removePendingRecord(key, saved) } catch { /* 保留原记录，待原员工核对 */ }
      }
      return
    }
    if (!definitelyRejectedSale(error)) {
      checkoutVisible.value = false
      ElMessage.warning('本次收款结果未知，已冻结原单。请先去订单中心核对，勿再次收款；未找到也不能直接重收。')
      return
    }
    // 明确拒绝也先建立后台取消屏障：某个重复请求被拒绝不能证明原请求没成交。
    if (snapshot.requestKey) {
      try {
        const result = await cancelPOSSubmission(snapshot.requestKey)
        if (session !== actorSession() || pendingSale.value?.requestKey !== snapshot.requestKey) return
        if (!result || result.requestKey !== snapshot.requestKey || result.status !== 'CANCELLED') {
          if (result?.requestKey === snapshot.requestKey && result.status === 'COMPLETED' && Number.isSafeInteger(result.orderId) && Number(result.orderId) > 0) recoveryResult.value = result
          checkoutVisible.value = false
          ElMessage.warning('原请求仍需核对，不能按本次错误再次收款')
          return
        }
      } catch {
        if (session === actorSession()) { checkoutVisible.value = false; ElMessage.warning('尚未确认原请求已结束，请核对后再收款') }
        return
      }
    }
    try { removePendingRecord(key, saved) }
    catch { saleStorageError.value = true; return }
    pendingSale.value = null
    submittingOrder.value = false
    console.error('订单创建失败:', error)
    const errorMessage = error?.response?.data?.message || error?.message || '未知错误'
    ElMessage.error('订单创建失败：' + errorMessage)
    if (typeof errorMessage === 'string' && errorMessage.includes('报价')) {
      checkoutVisible.value = false
      await recalculateCartPrices(true)
    }
    // 其他收款/库存错误保留弹窗，报价变化则返回购物车核对新价格与组成。
  } finally {
    if (session === actorSession()) submittingOrder.value = false
  }
}

/**
 * 补登记取货请求：正常情况后端已自动生成，仅在工作台未见请求时使用。
 * 重复登记会被后端拒绝（已有未完成请求），按提示信息处理即可。
 */
const handleRegisterPickup = async (item: { productId: number; name: string }) => {
  if (!pickupOrderId.value) return
  pickupSubmittingId.value = item.productId
  try {
    const detail: any = await getOrderDetail(pickupOrderId.value)
    const orderItem = (detail?.items || []).find((row: any) => row.productId === item.productId)
    if (!orderItem?.id) {
      ElMessage.warning(`未找到「${item.name}」的订单明细，无法登记取货请求`)
      return
    }
    await createPickupRequest(pickupOrderId.value, { orderItemId: orderItem.id })
    ElMessage.success('取货请求已登记')
  } catch (error) {
    // 请求拦截器已提示失败原因（如已有未完成的库房取货请求）
    console.warn('补登记取货请求失败:', error)
  } finally {
    pickupSubmittingId.value = null
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

.sale-pending {
  overflow: auto;
  padding: 20px;
  background: var(--el-bg-color);
  font-size: 14px;

  .el-input { max-width: 300px; margin-right: 8px; }
  p { line-height: 1.7; }
}


.pos-goods-panel {
  flex: 1;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pos-cart-panel {
  width: 368px;
  flex-shrink: 0;
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

.price-fallback-hint {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #fdf6ec;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.4;
  border-top: 1px solid #faecd8;
}

.pickup-alert {
  margin-bottom: 12px;
}

.pickup-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;

  &:last-child {
    border-bottom: none;
  }
}

.pickup-name {
  font-size: 14px;
  color: #303133;
}
</style>

<style scoped>
.request-number { overflow-wrap: anywhere; }
.recovery-result { margin: 16px 0; padding: 12px 16px; background: var(--el-fill-color-light); border-radius: 6px; }
.sale-pending .el-checkbox { display: flex; height: auto; white-space: normal; margin: 12px 0; }
.sale-pending :deep(.el-checkbox__label) { white-space: normal; line-height: 1.6; }
</style>

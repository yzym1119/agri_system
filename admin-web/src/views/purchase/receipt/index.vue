<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>采购收货</h1>
        <p class="page-subtitle">
          供应商送货后，在这里核对实际到货并入库。先选择采购单；同一商品有不同批号或日期时再拆成多批。
        </p>
      </div>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-select
            v-model="purchaseOrderId"
            :disabled="formLocked" :loading="ordersLoading" aria-label="待收货采购单"
            placeholder="选择供应商送货对应的采购单"
            filterable
            clearable
            style="width: 320px"
            @change="loadDetail"
          >
            <el-option
              v-for="order in purchaseOrders"
              :key="order.id"
              :label="`${order.orderNo}${order.supplierName ? ' / ' + order.supplierName : ''}`"
              :value="order.id"
            />
          </el-select>
          <el-button :loading="ordersLoading" :disabled="formLocked" @click="loadOrders">刷新采购单</el-button>
          <span v-if="detail" class="unit-text">门店：{{ detail.storeName || detail.storeId }}</span>
        </div>
      </template>

      <el-alert v-if="ordersError" :title="ordersError" type="error" :closable="false" show-icon class="receipt-alert" />
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="receipt-alert">
        <el-button text type="primary" :disabled="formLocked" @click="loadDetail">重新读取当前采购单</el-button>
      </el-alert>
      <p v-if="lineRows.length" class="entry-guidance">首批数量与进价来自原采购单，请按本次实收核对，分批到货不要照搬原采购总数。不同批号或日期再添加批次；填写的是收货单位的数量与每单位进价。</p>
      <el-table v-if="detailLoading || lineRows.length" :data="lineRows" row-key="purchaseOrderItemId" default-expand-all v-loading="detailLoading" stripe border style="width: 100%">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="batch-block">
              <div class="batch-toolbar">
                <span class="batch-title">收货批次（{{ row.batches.length }}）</span>
                <el-button size="small" :disabled="formLocked" @click="addBatch(row)">+ 添加批次</el-button>
              </div>
              <el-form :disabled="formLocked" class="batch-entry-form">
                <section v-for="(batch, index) in row.batches" :key="index" class="batch-entry" :aria-label="`${row.productName}第${index + 1}批`">
                  <div class="batch-entry-heading">
                    <strong>第 {{ index + 1 }} 批</strong>
                    <el-button type="danger" link size="small" :disabled="formLocked || row.batches.length <= 1" @click="removeBatch(row, index)">删除此批</el-button>
                  </div>
                  <div class="batch-fields">
                    <div class="batch-field"><label>收货单位</label><el-input v-model="batch.originalUnit" :aria-label="`第${index + 1}批收货单位`" :placeholder="row.unit || '商品已配置的采购单位'" /></div>
                    <div class="batch-field"><label>实际到货数量（{{ batch.originalUnit || '请先填单位' }}）</label><el-input v-model="batch.originalQuantity" :aria-label="`第${index + 1}批实际到货数量`" inputmode="decimal" placeholder="大于0，最多8位小数" /></div>
                    <div class="batch-field"><label>进价（元 / {{ batch.originalUnit || '单位' }}）</label><el-input v-model="batch.originalUnitCost" :aria-label="`第${index + 1}批每单位进价`" inputmode="decimal" placeholder="填写实际进价，不可留空" /></div>
                    <div class="batch-field"><label>供应商批号</label><el-input v-model="batch.supplierBatchNo" :aria-label="`第${index + 1}批供应商批号`" placeholder="包装未标注可留空" /></div>
                    <div class="batch-field"><label>生产日期</label><el-date-picker v-model="batch.productionDate" type="date" value-format="YYYY-MM-DD" :disabled="formLocked || batch.dateUnknown" :aria-label="`第${index + 1}批生产日期`" placeholder="未标注可留空" /></div>
                    <div class="batch-field"><label>有效期至</label><el-date-picker v-model="batch.expiryDate" type="date" value-format="YYYY-MM-DD" :disabled="formLocked || batch.dateUnknown" :aria-label="`第${index + 1}批有效期至`" placeholder="按包装标注填写" /></div>
                  </div>
                  <el-checkbox v-model="batch.dateUnknown">本批日期均未知</el-checkbox>
                  <p class="date-guidance">只有有效期时只填有效期，不需要补造生产日期；两项都无法核实时勾选日期未知。</p>
                </section>
              </el-form>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }"><strong>{{ row.productName }}</strong><div class="unit-text">{{ row.productCode || '-' }} · {{ row.specification || '未填规格' }}</div></template>
        </el-table-column>
        <el-table-column label="原采购数量" width="110" align="right">
          <template #default="{ row }">
            {{ formatQuantity(row.qty) }} <span class="unit-text">{{ row.unit || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批次摘要" min-width="180">
          <template #default="{ row }">
            <span v-for="(batch, index) in row.batches" :key="index" class="batch-chip">
              {{ batch.originalQuantity === '' ? '待填数量' : batch.originalQuantity }} {{ batch.originalUnit || '?' }}
              <template v-if="batch.dateUnknown"> · 日期未知</template>
              <template v-else-if="batch.productionDate || batch.expiryDate">
                · {{ batch.productionDate || '?' }} ~ {{ batch.expiryDate || '?' }}
              </template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="行备注" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.remark" :disabled="formLocked" size="small" placeholder="商品收货备注（可空）" />
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!detailLoading && !detailError && !ordersError && lineRows.length === 0" :description="purchaseOrderId ? '当前采购单没有可收货明细，请返回采购管理核对' : '请先选择采购单；没有待收货单时，请先在采购管理新建并提交采购单'" />
    </el-card>

    <el-card v-if="lineRows.length > 0" shadow="never" class="submit-card">
      <el-alert v-if="submitError" :title="submitError" :type="pendingReceipt ? 'warning' : 'error'" :closable="false" show-icon class="receipt-alert" />
      <div class="submit-form">
        <el-input v-model="exceptionReason" :disabled="formLocked" aria-label="收货例外说明" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="例外说明（如数量短缺、批次异常，可空）" />
        <el-input v-model="remark" :disabled="formLocked" aria-label="收货备注" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="收货备注（可空）" />
        <div class="print-opt">
          <el-checkbox v-model="printLabels" :disabled="formLocked">收货后打印标签</el-checkbox>
          <el-select v-model="labelType" size="small" style="width: 140px" :disabled="!printLabels || formLocked">
            <el-option label="批次标签" value="BATCH" />
            <el-option label="外箱标签" value="CASE" />
            <el-option label="周转箱标签" value="TOTE" />
          </el-select>
          <span class="unit-text">仅供贴签作业使用，打印由操作人主动触发</span>
        </div>
      </div>
      <div class="submit-actions">
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">{{ pendingReceipt ? '按原请求重试' : '确认收货入库' }}</el-button>
      </div>
    </el-card>

    <!-- 标签打印预览（操作人主动触发 window.print） -->
    <el-dialog v-model="printVisible" title="标签打印预览" width="640px">
      <div class="label-print-area">
        <div v-for="(label, index) in labels" :key="index" class="label-card">
          <div class="label-type">{{ labelTypeText }}</div>
          <div class="label-name">{{ label.productName }}</div>
          <div class="label-line">编码：{{ label.productCode || '-' }}</div>
          <div class="label-line">内部批次码：{{ label.internalReceiptBatchNo }}</div>
          <div class="label-line">批号：{{ label.supplierBatchNo || '-' }}</div>
          <div class="label-line">数量：{{ label.quantity }} {{ label.unit }}</div>
          <div class="label-line">
            日期：<template v-if="label.dateUnknown">日期未知</template>
            <template v-else>{{ label.productionDate || '?' }} ~ {{ label.expiryDate || '?' }}</template>
          </div>
          <div class="label-line">门店：{{ label.storeName }}</div>
          <div class="label-line label-foot">收货 {{ label.receivedAt }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="printVisible = false">关闭</el-button>
        <el-button type="primary" @click="doPrint">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useUserStore } from '@/store/user'
import { formatQuantity } from '@/utils/number'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPurchaseDetail, getPurchaseList, receivePurchase } from '@/api/purchase'
import type { PurchaseDetailVO, PurchasePageVO, ReceiptBatchCommand, ReceivePurchaseRequest } from '@/types/purchase'

interface BatchRow extends Omit<ReceiptBatchCommand, 'originalQuantity' | 'originalUnitCost'> {
  originalQuantity: number | string
  originalUnitCost: number | string
  dateUnknown: boolean
}

interface LineRow {
  purchaseOrderItemId: number
  productId: number
  productCode?: string
  productName?: string
  specification?: string
  qty: number
  unit?: string
  remark?: string
  batches: BatchRow[]
}

interface LabelInfo {
  productName: string
  productCode?: string
  internalReceiptBatchNo: string
  supplierBatchNo?: string | null
  quantity: number
  unit: string
  productionDate?: string | null
  expiryDate?: string | null
  dateUnknown: boolean
  storeName: string
  receivedAt: string
}

const route = useRoute()
const userStore = useUserStore()
let detailVersion = 0
let ordersVersion = 0
let routeVersion = 0
let alive = true
const detailError = ref('')
const detailSession = ref('')
const ordersError = ref('')
const submitError = ref('')
interface ReceiptSnapshot {
  data: ReceivePurchaseRequest
  detail: PurchaseDetailVO
  lines: LineRow[]
  printLabels: boolean
  labelType: 'BATCH' | 'CASE' | 'TOTE'
  session: string
}
const pendingReceipt = ref<ReceiptSnapshot | null>(null)
const formLocked = computed(() => submitting.value || !!pendingReceipt.value)
const canSubmit = computed(() => pendingReceipt.value
  ? pendingReceipt.value.session === userStore.token && pendingReceipt.value.data.purchaseOrderId === purchaseOrderId.value
  : (!detailLoading.value && !detailError.value && detailSession.value === userStore.token
    && detail.value?.id === purchaseOrderId.value && detail.value?.status === 2 && lineRows.value.length > 0))

const purchaseOrders = ref<PurchasePageVO[]>([])
const ordersLoading = ref(false)
const receiptRequestKey = ref(crypto.randomUUID())
const purchaseOrderId = ref<number | undefined>(undefined)
const detail = ref<PurchaseDetailVO | null>(null)
const detailLoading = ref(false)
const lineRows = ref<LineRow[]>([])

const exceptionReason = ref('')
const remark = ref('')
const submitting = ref(false)
const printLabels = ref(false)
const labelType = ref<'BATCH' | 'CASE' | 'TOTE'>('BATCH')
const printVisible = ref(false)
const labels = ref<LabelInfo[]>([])

const labelTypeText = computed(() => {
  if (labelType.value === 'CASE') return '外箱标签'
  if (labelType.value === 'TOTE') return '周转箱标签'
  return '批次标签'
})

const loadOrders = async () => {
  const version = ++ordersVersion
  const session = userStore.token
  const active = () => alive && version === ordersVersion && session === userStore.token
  ordersLoading.value = true
  ordersError.value = ''
  try {
    const res = await getPurchaseList({ status: 2, pageNum: 1, pageSize: 50 })
    if (active()) purchaseOrders.value = res.records || []
  } catch {
    if (!active()) return
    ordersError.value = '采购单列表加载失败，请重试；当前不能据此判断是否有待收货单。'
    purchaseOrders.value = []
  } finally {
    if (active()) ordersLoading.value = false
  }
}

const newBatch = (unit?: string): BatchRow => ({
  supplierBatchNo: '',
  originalUnit: unit || '',
  originalQuantity: '',
  originalUnitCost: '',
  productionDate: null,
  expiryDate: null,
  inspectionReference: null,
  dateUnknown: false
})

const loadDetail = async () => {
  if (formLocked.value) return
  const version = ++detailVersion
  const id = purchaseOrderId.value
  const session = userStore.token
  const active = () => alive && version === detailVersion && session === userStore.token && id === purchaseOrderId.value
  detail.value = null
  detailSession.value = ''
  lineRows.value = []
  detailError.value = ''
  submitError.value = ''
  exceptionReason.value = ''
  remark.value = ''
  receiptRequestKey.value = crypto.randomUUID()
  detailLoading.value = !!id
  if (!id) return
  try {
    const res = await getPurchaseDetail(id)
    if (!active()) return
    if (res.id !== id || res.status !== 2) {
      detailError.value = '该采购单不是待收货状态，请返回采购管理核对。'
      return
    }
    detail.value = res
    detailSession.value = session
    lineRows.value = (res.items || []).map(item => ({
      purchaseOrderItemId: item.id as number,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      specification: item.specification,
      qty: Number(item.qty ?? 0),
      unit: item.unit,
      remark: '',
      batches: [{ ...newBatch(item.unit),
        originalQuantity: Number(item.qty) > 0 ? item.qty : '',
        originalUnitCost: Number(item.price) > 0 ? item.price : '' }]
    }))
  } catch {
    if (active()) detailError.value = '采购单详情加载失败，请重新读取后再收货。'
  } finally {
    if (active()) detailLoading.value = false
  }
}

const addBatch = (line: LineRow) => {
  if (formLocked.value) return
  line.batches.push(newBatch(line.unit))
}

const removeBatch = (line: LineRow, index: number) => {
  if (formLocked.value || line.batches.length <= 1) return
  line.batches.splice(index, 1)
}

const validPositiveDecimal = (value: number | string) => {
  const raw = String(value).trim()
  if (!/^(?:\d+(?:\.\d{1,8})?|\.\d{1,8})$/.test(raw) || !Number.isFinite(Number(raw)) || Number(raw) <= 0) return false
  const normalize = (text: string) => {
    const [integer, fraction = ''] = text.split('.')
    return `${integer.replace(/^0+/, '') || '0'}.${fraction.replace(/0+$/, '')}`
  }
  // 现有接口接收数字；不能把原文的有效尾数悄悄丢在Number序列化之前。
  return normalize(raw) === normalize(Number(raw).toFixed(8))
}
const definitelyRejected = (error: any) => {
  const code = Number(error?.apiBusinessCode)
  return code === 400 || (code >= 40000 && code < 50000)
}
const submit = async () => {
  if (submitting.value) return
  const retryingUnknown = !!pendingReceipt.value
  let snapshot = pendingReceipt.value
  if (snapshot && (snapshot.session !== userStore.token || snapshot.data.purchaseOrderId !== purchaseOrderId.value)) {
    ElMessage.warning('请返回原采购单、由原操作会话核对未确认的收货结果')
    return
  }
  if (!snapshot) {
    if (!canSubmit.value || !detail.value) {
      ElMessage.warning('请等待当前采购单详情加载成功，再核对收货行')
      return
    }
    for (const line of lineRows.value) {
      if (line.batches.length === 0) {
        ElMessage.warning(`商品「${line.productName || line.productId}」至少需要一个收货批次`)
        return
      }
      for (const batch of line.batches) {
        if (!String(batch.originalUnit || '').trim()) {
          ElMessage.warning(`商品「${line.productName || line.productId}」的批次缺少原始单位`)
          return
        }
        if (!validPositiveDecimal(batch.originalQuantity) || !validPositiveDecimal(batch.originalUnitCost)) {
          ElMessage.warning(`商品「${line.productName || line.productId}」的数量与进价须为大于0、最多8位小数的普通数字；数值过大无法精确保留时请先核对`)
          return
        }
        if (!batch.dateUnknown && batch.productionDate && batch.expiryDate &&
            String(batch.expiryDate) < String(batch.productionDate)) {
          ElMessage.warning(`商品「${line.productName || line.productId}」的有效期早于生产日期`)
          return
        }
      }
    }


    snapshot = {
      data: {
        requestKey: receiptRequestKey.value,
        purchaseOrderId: purchaseOrderId.value!,
        exceptionReason: exceptionReason.value || null,
        remark: remark.value || null,
        lines: lineRows.value.map(line => ({
          purchaseOrderItemId: line.purchaseOrderItemId, productId: line.productId, remark: line.remark || null,
          batches: line.batches.map(batch => ({
            supplierBatchNo: batch.supplierBatchNo || null,
            originalUnit: String(batch.originalUnit).trim(),
            originalQuantity: Number(batch.originalQuantity), originalUnitCost: Number(batch.originalUnitCost),
            productionDate: batch.dateUnknown ? null : batch.productionDate || null,
            expiryDate: batch.dateUnknown ? null : batch.expiryDate || null,
            inspectionReference: batch.inspectionReference || null
          }))
        }))
      },
      detail: JSON.parse(JSON.stringify(detail.value)), lines: JSON.parse(JSON.stringify(lineRows.value)),
      printLabels: printLabels.value, labelType: labelType.value, session: userStore.token
    }
  }
  const request = snapshot
  const active = () => alive && request.session === userStore.token && request.data.purchaseOrderId === purchaseOrderId.value
  submitting.value = true
  submitError.value = ''
  pendingReceipt.value = request
  try {
    const received = await receivePurchase(request.data)
    if (!active()) return
    ElMessage.success('收货成功')
    pendingReceipt.value = null
    receiptRequestKey.value = crypto.randomUUID()
    if (request.printLabels) {
      const storeName = request.detail.storeName || String(request.detail.storeId)
      const now = new Date().toLocaleString()
      labels.value = received.batches.map(batch => {
        const line = request.lines.find(row => row.productId === batch.productId)
        return {
          productName: line?.productName || String(batch.productId), productCode: line?.productCode,
          internalReceiptBatchNo: batch.internalReceiptBatchNo, supplierBatchNo: batch.supplierBatchNo,
          quantity: Number(batch.originalQuantity), unit: batch.originalUnitName,
          productionDate: batch.productionDate, expiryDate: batch.expiryDate,
          dateUnknown: batch.dateStatus === 'UNKNOWN', storeName, receivedAt: now
        }
      })
      labelType.value = request.labelType
      printVisible.value = true
    }
    ++detailVersion
    purchaseOrderId.value = undefined
    detail.value = null
    lineRows.value = []
    exceptionReason.value = ''
    remark.value = ''
    printLabels.value = false
    await loadOrders()
  } catch (error: any) {
    if (!active()) return
    if (!retryingUnknown && definitelyRejected(error)) {
      pendingReceipt.value = null
      receiptRequestKey.value = crypto.randomUUID()
      submitError.value = error?.message || '收货被拒绝，请修正后再提交。'
    } else {
      submitError.value = '收货结果尚未确认，当前内容已锁定。可用原请求重试；仍无法确认时，请在采购与库存记录中核对，勿重复登记。'
    }
  } finally {
    if (active()) submitting.value = false
    // 成功后当前采购单已清空，仍须结束本次提交；旧会话不改新页面状态。
    else if (alive && request.session === userStore.token && !pendingReceipt.value) submitting.value = false
  }
}

const doPrint = () => {
  // 打印期间临时挂 body class：全局打印样式仅在本页打印时生效，避免影响其他页面后续打印
  document.body.classList.add('label-printing')
  window.addEventListener('afterprint', () => document.body.classList.remove('label-printing'), { once: true })
  window.print()
}

watch(() => route.query.purchaseOrderId, async value => {
  const version = ++routeVersion
  const session = userStore.token
  if (formLocked.value) return
  const id = Number(value)
  purchaseOrderId.value = Number.isSafeInteger(id) && id > 0 ? id : undefined
  const orders = loadOrders()
  await loadDetail()
  await orders
  if (!alive || version !== routeVersion || session !== userStore.token || formLocked.value) return
  if (detail.value?.id === id && purchaseOrderId.value === id && !purchaseOrders.value.some(order => order.id === id)) {
    purchaseOrders.value.push({ id, orderNo: detail.value.orderNo } as PurchasePageVO)
  }
}, { immediate: true })
onBeforeUnmount(() => { alive = false; ++detailVersion; ++ordersVersion; ++routeVersion })
</script>

<style scoped>
.page-subtitle {
  margin: 6px 0 0;
  color: #626f7e;
  font-size: 13px;
}
.unit-text {
  color: #626f7e;
  font-size: 12px;
}
.batch-block { padding: 12px 20px; }
.receipt-alert { margin-bottom: 16px; }
.entry-guidance, .date-guidance { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; margin: 0 0 12px; }
.date-guidance { margin: 0; }
.batch-entry { padding: 16px 0; border-top: 1px solid var(--el-border-color-lighter); }
.batch-entry-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.batch-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 12px; }
.batch-field { min-width: 0; }
.batch-field label { display: block; font-size: 13px; line-height: 1.6; margin-bottom: 6px; color: var(--el-text-color-regular); }
.batch-field :deep(.el-date-editor.el-input) { width: 100%; }
@media (max-width: 1100px) { .batch-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .batch-fields { grid-template-columns: minmax(0, 1fr); } }

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.batch-title {
  font-weight: 600;
  font-size: 13px;
}
.batch-chip {
  display: inline-block;
  margin: 2px 6px 2px 0;
  padding: 1px 8px;
  border-radius: 10px;
  background: #f0f2f5;
  font-size: 12px;
}
.submit-card {
  margin-top: 12px;
}
.submit-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.print-opt {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.submit-actions {
  margin-top: 12px;
  text-align: right;
}
.label-print-area {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.label-card {
  width: 280px;
  padding: 10px 12px;
  border: 1px dashed #666;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.7;
}
.label-type {
  font-weight: 700;
  font-size: 13px;
  border-bottom: 1px solid #999;
  margin-bottom: 4px;
  padding-bottom: 2px;
}
.label-name {
  overflow-wrap: anywhere;
  font-weight: 600;
}
.label-line { overflow-wrap: anywhere; }
.label-foot {
  color: #666;
}
</style>

<!--
  打印样式必须放在非 scoped 块：标签区域位于 el-dialog 内，弹窗被传送到 body，
  scoped 选择器无法命中；用 body.label-printing 限定，仅在收货页触发打印时生效。
-->
<style>
@media print {
  body.label-printing * {
    visibility: hidden;
  }
  body.label-printing .label-print-area,
  body.label-printing .label-print-area * {
    visibility: visible;
  }
  body.label-printing .label-print-area {
    position: absolute;
    left: 0;
    top: 0;
  }
}
</style>

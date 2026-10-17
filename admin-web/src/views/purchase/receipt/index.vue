<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>收货</h1>
        <p class="page-subtitle">
          按批次登记实际收货事实：每个收货行可包含多个批次，各自记录单位、数量与成本；生产/有效日期未知时必须显式勾选“日期未知”。
        </p>
      </div>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-select
            v-model="purchaseOrderId"
            placeholder="选择待入库采购单"
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
          <el-button :loading="ordersLoading" @click="loadOrders">刷新采购单</el-button>
          <span v-if="detail" class="unit-text">门店：{{ detail.storeName || detail.storeId }}</span>
        </div>
      </template>

      <el-table :data="lineRows" row-key="purchaseOrderItemId" v-loading="detailLoading" stripe border style="width: 100%">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="batch-block">
              <div class="batch-toolbar">
                <span class="batch-title">收货批次（{{ row.batches.length }}）</span>
                <el-button size="small" @click="addBatch(row)">+ 添加批次</el-button>
              </div>
              <el-table :data="row.batches" size="small" border style="width: 100%">
                <el-table-column label="供应商批号" min-width="130">
                  <template #default="{ row: batch }">
                    <el-input v-model="batch.supplierBatchNo" size="small" placeholder="可空" />
                  </template>
                </el-table-column>
                <el-table-column label="原始单位" width="110">
                  <template #default="{ row: batch }">
                    <el-input v-model="batch.originalUnit" size="small" :placeholder="row.unit || '如 箱'" />
                  </template>
                </el-table-column>
                <el-table-column label="数量" width="130">
                  <template #default="{ row: batch }">
                    <el-input-number v-model="batch.originalQuantity" size="small" :min="0.00000001" :precision="6" :controls="false" style="width: 100%" />
                  </template>
                </el-table-column>
                <el-table-column label="单位单价" width="130">
                  <template #default="{ row: batch }">
                    <el-input-number v-model="batch.originalUnitCost" size="small" :min="0.00000001" :precision="6" :controls="false" style="width: 100%" />
                  </template>
                </el-table-column>
                <el-table-column label="生产日期" width="160">
                  <template #default="{ row: batch }">
                    <el-date-picker
                      v-model="batch.productionDate"
                      type="date"
                      size="small"
                      value-format="YYYY-MM-DD"
                      :disabled="batch.dateUnknown"
                      placeholder="选择日期"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="有效期至" width="160">
                  <template #default="{ row: batch }">
                    <el-date-picker
                      v-model="batch.expiryDate"
                      type="date"
                      size="small"
                      value-format="YYYY-MM-DD"
                      :disabled="batch.dateUnknown"
                      placeholder="选择日期"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="日期未知" width="90" align="center">
                  <template #default="{ row: batch }">
                    <el-checkbox v-model="batch.dateUnknown">未知</el-checkbox>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template #default="{ $index }">
                    <el-button type="danger" link size="small" :disabled="row.batches.length <= 1" @click="removeBatch(row, $index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="productCode" label="商品编码" width="140" />
        <el-table-column prop="productName" label="商品名称" min-width="180" />
        <el-table-column prop="specification" label="规格" width="140" />
        <el-table-column label="采购数量" width="130" align="right">
          <template #default="{ row }">
            {{ row.qty }} <span class="unit-text">{{ row.unit || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批次摘要" min-width="220">
          <template #default="{ row }">
            <span v-for="(batch, index) in row.batches" :key="index" class="batch-chip">
              {{ batch.originalQuantity || '?' }} {{ batch.originalUnit || '?' }}
              <template v-if="batch.dateUnknown"> · 日期未知</template>
              <template v-else-if="batch.productionDate || batch.expiryDate">
                · {{ batch.productionDate || '?' }} ~ {{ batch.expiryDate || '?' }}
              </template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="行备注" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" placeholder="可空" />
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!detailLoading && lineRows.length === 0" description="请选择待入库采购单" />
    </el-card>

    <el-card v-if="lineRows.length > 0" shadow="never" class="submit-card">
      <div class="submit-form">
        <el-input v-model="exceptionReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="例外说明（如数量短缺、批次异常，可空）" />
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="收货备注（可空）" />
        <div class="print-opt">
          <el-checkbox v-model="printLabels">收货后打印标签</el-checkbox>
          <el-select v-model="labelType" size="small" style="width: 140px" :disabled="!printLabels">
            <el-option label="批次标签" value="BATCH" />
            <el-option label="外箱标签" value="CASE" />
            <el-option label="周转箱标签" value="TOTE" />
          </el-select>
          <span class="unit-text">仅供贴签作业使用，打印由操作人主动触发</span>
        </div>
      </div>
      <div class="submit-actions">
        <el-button type="primary" :loading="submitting" @click="submit">确认收货</el-button>
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
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPurchaseDetail, getPurchaseList, receivePurchase } from '@/api/purchase'
import type { PurchaseDetailVO, PurchasePageVO, ReceiptBatchCommand } from '@/types/purchase'

interface BatchRow extends ReceiptBatchCommand {
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
  ordersLoading.value = true
  try {
    const res = await getPurchaseList({ status: 2, pageNum: 1, pageSize: 50 })
    purchaseOrders.value = res.records || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载采购单失败')
    purchaseOrders.value = []
  } finally {
    ordersLoading.value = false
  }
}

const newBatch = (unit?: string): BatchRow => ({
  supplierBatchNo: '',
  originalUnit: unit || '',
  originalQuantity: 1,
  originalUnitCost: 0.00000001,
  productionDate: null,
  expiryDate: null,
  inspectionReference: null,
  dateUnknown: false
})

const loadDetail = async () => {
  if (!purchaseOrderId.value) {
    detail.value = null
    lineRows.value = []
    return
  }
  detailLoading.value = true
  try {
    const res = await getPurchaseDetail(purchaseOrderId.value)
    detail.value = res
    lineRows.value = (res.items || []).map(item => ({
      purchaseOrderItemId: item.id as number,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      specification: item.specification,
      qty: Number(item.qty ?? 0),
      unit: item.unit,
      remark: '',
      batches: [{ ...newBatch(item.unit), originalQuantity: Number(item.qty ?? 0), originalUnitCost: Number(item.price ?? 0) }]
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载采购单详情失败')
    detail.value = null
    lineRows.value = []
  } finally {
    detailLoading.value = false
  }
}

const addBatch = (line: LineRow) => {
  line.batches.push(newBatch(line.unit))
}

const removeBatch = (line: LineRow, index: number) => {
  if (line.batches.length <= 1) return
  line.batches.splice(index, 1)
}

const submit = async () => {
  if (!purchaseOrderId.value || lineRows.value.length === 0) {
    ElMessage.warning('请选择待入库采购单并确认收货行')
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
      if (!(Number(batch.originalQuantity) > 0) || !(Number(batch.originalUnitCost) > 0)) {
        ElMessage.warning(`商品「${line.productName || line.productId}」的批次数量与单价必须大于 0`)
        return
      }
      if (!batch.dateUnknown && batch.productionDate && batch.expiryDate &&
          String(batch.expiryDate) < String(batch.productionDate)) {
        ElMessage.warning(`商品「${line.productName || line.productId}」的有效期早于生产日期`)
        return
      }
    }
  }

  submitting.value = true
  try {
    const received = await receivePurchase({
      requestKey: receiptRequestKey.value,
      purchaseOrderId: purchaseOrderId.value,
      exceptionReason: exceptionReason.value || null,
      remark: remark.value || null,
      lines: lineRows.value.map(line => ({
        purchaseOrderItemId: line.purchaseOrderItemId,
        productId: line.productId,
        remark: line.remark || null,
        batches: line.batches.map(batch => ({
          supplierBatchNo: batch.supplierBatchNo || null,
          originalUnit: String(batch.originalUnit).trim(),
          originalQuantity: Number(batch.originalQuantity),
          originalUnitCost: Number(batch.originalUnitCost),
          productionDate: batch.dateUnknown ? null : batch.productionDate || null,
          expiryDate: batch.dateUnknown ? null : batch.expiryDate || null,
          inspectionReference: batch.inspectionReference || null
        }))
      }))
    })
    ElMessage.success('收货成功')
    receiptRequestKey.value = crypto.randomUUID()

    if (printLabels.value) {
      const storeName = detail.value?.storeName || String(detail.value?.storeId ?? '')
      const now = new Date().toLocaleString()
      labels.value = received.batches.map(batch => {
        const line = lineRows.value.find(row => row.productId === batch.productId)
        return {
          productName: line?.productName || String(batch.productId),
          productCode: line?.productCode,
          internalReceiptBatchNo: batch.internalReceiptBatchNo,
          supplierBatchNo: batch.supplierBatchNo,
          quantity: Number(batch.originalQuantity),
          unit: batch.originalUnitName,
          productionDate: batch.productionDate,
          expiryDate: batch.expiryDate,
          dateUnknown: batch.dateStatus === 'UNKNOWN',
          storeName,
          receivedAt: now
        }
      })
      printVisible.value = true
    }

    purchaseOrderId.value = undefined
    detail.value = null
    lineRows.value = []
    exceptionReason.value = ''
    remark.value = ''
    printLabels.value = false
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(e?.message || '收货失败')
  } finally {
    submitting.value = false
  }
}

const doPrint = () => {
  // 打印期间临时挂 body class：全局打印样式仅在本页打印时生效，避免影响其他页面后续打印
  document.body.classList.add('label-printing')
  window.addEventListener('afterprint', () => document.body.classList.remove('label-printing'), { once: true })
  window.print()
}

watch(() => route.query.purchaseOrderId, async value => {
  await loadOrders()
  const id = Number(value)
  if (!Number.isSafeInteger(id) || id <= 0) return
  purchaseOrderId.value = id
  await loadDetail()
  if (detail.value && !purchaseOrders.value.some(order => order.id === id)) {
    purchaseOrders.value.push({ id, orderNo: detail.value.orderNo } as PurchasePageVO)
  }
}, { immediate: true })
</script>

<style scoped>
.page-subtitle {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}
.unit-text {
  color: #909399;
  font-size: 12px;
}
.batch-block {
  padding: 8px 12px;
  background: #fafafa;
}
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
  font-weight: 600;
}
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

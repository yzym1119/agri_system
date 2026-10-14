<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            clearable
            placeholder="售后单号/订单号/客户名/手机号"
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="门店">
          <el-select v-model="queryParams.storeId" clearable filterable placeholder="全部门店" style="width: 180px">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" clearable placeholder="全部类型" style="width: 150px">
            <el-option label="客户取消" :value="1" />
            <el-option label="管理端退货" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option label="已申请" :value="10" />
            <el-option label="待退款" :value="40" />
            <el-option label="已完成" :value="50" />
            <el-option label="已驳回" :value="90" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryParams.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column label="售后单号" min-width="170">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">
              {{ row.afterSalesNo || '-' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="订单号" min-width="170">
          <template #default="{ row }">{{ row.orderNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="客户" min-width="130">
          <template #default="{ row }">
            <div>{{ row.customerName || '散客' }}</div>
            <div class="cell-sub">{{ row.customerPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="storeName" label="门店" width="120" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'warning' : 'info'">
              {{ getTypeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成交冲销" width="130" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column label="冲欠款" width="120" align="right">
          <template #default="{ row }">{{ row.creditReductionAmount == null ? '待核对' : '¥' + formatMoney(row.creditReductionAmount) }}</template>
        </el-table-column>
        <el-table-column label="退已收款" width="120" align="right">
          <template #default="{ row }">{{ row.cashRefundAmount == null ? '待核对' : '¥' + formatMoney(row.cashRefundAmount) }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="审核/退款" min-width="180">
          <template #default="{ row }">
            <div>审核：{{ row.auditByName || '-' }}</div>
            <div class="cell-sub">退款：{{ row.refundByName || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
            <template v-if="row.status === 10">
              <el-button
                v-permission="'afterSales:audit'"
                link
                type="success"
                @click="openAuditDialog(row, true)"
              >
                通过
              </el-button>
              <el-button
                v-permission="'afterSales:audit'"
                link
                type="danger"
                @click="openAuditDialog(row, false)"
              >
                驳回
              </el-button>
            </template>
            <el-button
              v-else-if="row.status === 40"
              v-permission="'afterSales:refund'"
              link
              type="warning"
              @click="openRefundDialog(row)"
            >
              确认退款
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="售后详情" size="760px">
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="售后单号">{{ detailData.afterSalesNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detailData.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detailData.customerName || '散客' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detailData.customerPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="门店">{{ detailData.storeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="成交冲销">¥{{ formatMoney(detailData.refundAmount) }}</el-descriptions-item>
          <el-descriptions-item label="冲欠款">{{ detailData.creditReductionAmount == null ? '待核对' : '¥' + formatMoney(detailData.creditReductionAmount) }}</el-descriptions-item>
          <el-descriptions-item label="退已收款">{{ detailData.cashRefundAmount == null ? '待核对' : '¥' + formatMoney(detailData.cashRefundAmount) }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ getTypeText(detailData.type) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ getStatusText(detailData.status) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detailData.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatDateTime(detailData.auditTime) }}</el-descriptions-item>
          <el-descriptions-item label="退款时间">{{ formatDateTime(detailData.refundTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核备注">{{ detailData.auditRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="售后原因" :span="2">{{ detailData.reason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">售后明细</div>
        <el-table :data="detailData.items || []" border>
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="productName" label="商品名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="90" align="center" />
          <el-table-column label="数量" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="退款金额" width="140" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.refundAmount) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog
      v-model="auditDialogVisible"
      :title="auditForm.approved ? '审核通过' : '审核驳回'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="100px">
        <el-form-item label="售后单号">
          <el-input :model-value="currentRow?.afterSalesNo || ''" disabled />
        </el-form-item>
        <el-form-item label="审核备注" prop="remark">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="auditForm.approved ? '选填：审核说明' : '请输入驳回原因'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditSubmitting" @click="submitAudit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="refundDialogVisible"
      title="确认退货资金处理"
      width="520px"
      :close-on-press-escape="!refundSubmitting"
      :show-close="!refundSubmitting"
      :close-on-click-modal="false"
    >
      <el-form ref="refundFormRef" :model="refundForm" :rules="refundRules" label-width="100px">
        <el-form-item label="售后单号">
          <el-input :model-value="currentRow?.afterSalesNo || ''" disabled />
        </el-form-item>
        <el-alert v-if="pendingRefundRequest" title="本次资金处理结果尚未确认，请按原请求核对，不会重新取报价再次退款。" type="warning" :closable="false" style="margin-bottom: 16px" />
        <div v-loading="refundPreviewLoading">
          <el-alert v-if="refundPreviewError" :title="refundPreviewError" type="error" :closable="false" />
          <el-descriptions v-if="refundPreview" :column="1" border>
            <el-descriptions-item label="本次成交冲销">¥{{ formatMoney(refundPreview.refundAmount) }}</el-descriptions-item>
            <el-descriptions-item label="本次冲欠款">¥{{ formatMoney(refundPreview.creditReductionAmount) }}</el-descriptions-item>
            <el-descriptions-item label="本次退已收款">¥{{ formatMoney(refundPreview.cashRefundAmount) }}</el-descriptions-item>
            <el-descriptions-item label="原单当前欠款">¥{{ formatMoney(refundPreview.remainingDebt) }}</el-descriptions-item>
          </el-descriptions>
          <el-button v-if="refundPreviewError" text type="primary" :loading="refundPreviewLoading" @click="refreshAfterSalesPreview">重新核对</el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button :disabled="refundSubmitting" @click="refundDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="refundSubmitting" :disabled="refundPreviewLoading || !refundPreview" @click="submitRefund">{{ pendingRefundRequest ? '核对本次处理' : '确定' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useUserStore } from '@/store/user'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { auditAfterSales, getAfterSalesDetail, getAfterSalesPage, refundAfterSales } from '@/api/afterSales'
import { getRefundPreview } from '@/api/order'
import type { RefundPreview } from '@/types/order'
import { getAllStores } from '@/api/store'
import type {
  AfterSalesDetail,
  AfterSalesPageItem,
  AfterSalesPageQuery,
  PageResult
} from '@/types/afterSales'
import type { StoreDetailVO } from '@/types/store'

const loading = ref(false)
const total = ref(0)
const tableData = ref<AfterSalesPageItem[]>([])
const storeOptions = ref<StoreDetailVO[]>([])

const detailVisible = ref(false)
const detailData = ref<AfterSalesDetail>()
const currentRow = ref<AfterSalesPageItem>()

const auditDialogVisible = ref(false)
const auditSubmitting = ref(false)
const auditValidating = ref(false)
let auditSessionVersion = 0
const auditFormRef = ref<FormInstance>()
const auditForm = reactive({
  approved: true,
  remark: ''
})
const auditRules: FormRules = {
  remark: [
    {
      validator: (_rule, value, callback) => {
        if (!auditForm.approved && !String(value || '').trim()) {
          callback(new Error('请输入驳回原因'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const refundDialogVisible = ref(false)
const refundSubmitting = ref(false)
const refundFormRef = ref<FormInstance>()
const refundForm = reactive({
  refundAmount: 0
})
const refundRules: FormRules = {}
const refundPreview = ref<RefundPreview | null>(null)
const userStore = useUserStore()
interface AfterSalesSubmission {afterSalesId: number; quoteId: string; refundAmount: number; operatorId?: number; row: AfterSalesPageItem; preview: RefundPreview}
const pendingRefundRequest = ref<AfterSalesSubmission | null>(null)
const definiteRefundRejection = (error: any) => {
  const code = Number(error?.apiBusinessCode)
  return code === 10001 || (code >= 400 && code < 500) || String(error?.message || '').startsWith('REFUND_QUOTE_STALE')
}
const refundPreviewLoading = ref(false)
const refundPreviewError = ref('')
let refundPreviewVersion = 0
watch(refundDialogVisible, value => {
  if (!value) {
    ++refundPreviewVersion
    if (!pendingRefundRequest.value) refundPreview.value = null
    refundPreviewLoading.value = false
  }
})

const queryParams = reactive<AfterSalesPageQuery & { timeRange?: string[] }>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  storeId: undefined,
  type: undefined,
  status: undefined,
  timeRange: undefined
})

const formatMoney = (value?: number | null) => Number(value || 0).toFixed(2)
const formatNumber = (value?: number | null) => Number(value || 0).toFixed(2)

const formatDateTime = (value?: string | null) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

const getTypeText = (type?: number) => {
  if (type === 1) return '客户取消'
  if (type === 2) return '管理端退货'
  return '-'
}

const getStatusText = (status?: number) => {
  if (status === 10) return '已申请'
  if (status === 40) return '待退款'
  if (status === 50) return '已完成'
  if (status === 90) return '已驳回'
  return '-'
}

const getStatusTagType = (status?: number) => {
  if (status === 10) return 'warning'
  if (status === 40) return 'primary'
  if (status === 50) return 'success'
  if (status === 90) return 'danger'
  return 'info'
}

const loadOptions = async () => {
  try {
    storeOptions.value = await getAllStores()
  } catch (error) {
    console.error('加载售后页门店选项失败:', error)
    storeOptions.value = []
  }
}

const loadTable = async () => {
  loading.value = true
  try {
    const params: AfterSalesPageQuery = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword?.trim() || undefined,
      storeId: queryParams.storeId,
      type: queryParams.type,
      status: queryParams.status,
      startTime: queryParams.timeRange?.[0],
      endTime: queryParams.timeRange?.[1]
    }
    const page: PageResult<AfterSalesPageItem> = await getAfterSalesPage(params)
    tableData.value = page.records || []
    total.value = page.totalRow || page.total || 0
  } catch (error) {
    console.error('加载售后列表失败:', error)
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  loadTable()
}

const handleReset = () => {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.keyword = ''
  queryParams.storeId = undefined
  queryParams.type = undefined
  queryParams.status = undefined
  queryParams.timeRange = undefined
  loadTable()
}

const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  loadTable()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadTable()
}

const handleViewDetail = async (row: AfterSalesPageItem) => {
  currentRow.value = row
  detailVisible.value = true
  try {
    detailData.value = await getAfterSalesDetail(row.id)
  } catch (error) {
    console.error('加载售后详情失败:', error)
    detailData.value = undefined
    ElMessage.error('加载售后详情失败')
  }
}

const openAuditDialog = (row: AfterSalesPageItem, approved: boolean) => {
  ++auditSessionVersion
  currentRow.value = row
  auditForm.approved = approved
  auditForm.remark = ''
  auditDialogVisible.value = true
}

const submitAudit = async () => {
  if (!auditFormRef.value || !currentRow.value || !auditDialogVisible.value || auditSubmitting.value || auditValidating.value) return
  const afterSalesId = currentRow.value.id
  const version = auditSessionVersion
  const data = {approved: auditForm.approved, remark: auditForm.remark?.trim() || undefined}
  auditValidating.value = true
  try {
    await auditFormRef.value.validate(async valid => {
      if (!valid || !auditDialogVisible.value || currentRow.value?.id !== afterSalesId || version !== auditSessionVersion
          || auditForm.approved !== data.approved || (auditForm.remark?.trim() || undefined) !== data.remark) return
      auditSubmitting.value = true
      try {
        await auditAfterSales(afterSalesId, data)
        if (version !== auditSessionVersion || currentRow.value?.id !== afterSalesId || !auditDialogVisible.value) return
        ElMessage.success(data.approved ? '审核通过' : '已驳回')
        auditDialogVisible.value = false
        void loadTable()
        if (detailVisible.value && detailData.value?.id === afterSalesId) {
          const detail = await getAfterSalesDetail(afterSalesId)
          if (detailVisible.value && detailData.value?.id === afterSalesId) detailData.value = detail
        }
      } catch (error) {
        console.error('审核售后失败:', error)
      } finally {
        auditSubmitting.value = false
      }
    })
  } finally {
    auditValidating.value = false
  }
}

const refreshAfterSalesPreview = async () => {
  if (pendingRefundRequest.value) return
  const afterSalesId = currentRow.value?.id
  if (!afterSalesId) return
  const version = ++refundPreviewVersion
  refundPreview.value = null
  refundPreviewError.value = ''
  refundPreviewLoading.value = true
  try {
    const detail = await getAfterSalesDetail(afterSalesId)
    if (version !== refundPreviewVersion || !refundDialogVisible.value) return
    if (!detail.orderId || !detail.items?.length || detail.items.some(item => !item.orderItemId || item.quantity == null)) {
      throw new Error('售后缺少原订单或退货明细，需核对后处理')
    }
    const result = await getRefundPreview(detail.orderId, {afterSalesId, items: detail.items.map(item => ({
      orderItemId: item.orderItemId!, quantity: Number(item.quantity), refundAmount: item.refundAmount
    }))})
    if (version !== refundPreviewVersion || !refundDialogVisible.value) return
    if (!result.quoteId || result.orderId !== detail.orderId) throw new Error('退货资金预览无效，请重新核对')
    refundForm.refundAmount = Number(result.refundAmount)
    refundPreview.value = result
  } catch (error: any) {
    if (version === refundPreviewVersion) refundPreviewError.value = error?.message || '退货资金核对失败'
  } finally {
    if (version === refundPreviewVersion) refundPreviewLoading.value = false
  }
}
const openRefundDialog = async (row: AfterSalesPageItem) => {
  if (pendingRefundRequest.value) {
    if (pendingRefundRequest.value.afterSalesId !== row.id) return ElMessage.warning('请先核对上一笔售后处理结果')
    currentRow.value = pendingRefundRequest.value.row
    refundPreview.value = pendingRefundRequest.value.preview
    refundForm.refundAmount = pendingRefundRequest.value.refundAmount
    refundDialogVisible.value = true
    return
  }
  currentRow.value = row
  refundForm.refundAmount = 0
  refundDialogVisible.value = true
  await refreshAfterSalesPreview()
}

const executeAfterSalesRefund = async (request: AfterSalesSubmission) => {
  if (refundSubmitting.value) return
  if (request.operatorId !== userStore.userInfo?.id) return ElMessage.warning('本次退款需由原操作员工核对')
  refundSubmitting.value = true
  pendingRefundRequest.value = request
  try {
    await refundAfterSales(request.afterSalesId, {refundAmount: request.refundAmount, quoteId: request.quoteId})
    pendingRefundRequest.value = null
    ElMessage.success('退款已确认')
    refundDialogVisible.value = false
    void loadTable()
    if (detailVisible.value && detailData.value?.id === request.afterSalesId) {
      void getAfterSalesDetail(request.afterSalesId).then(detail => { detailData.value = detail })
        .catch(error => ElMessage.error(error?.message || '退款已完成，详情刷新失败'))
    }
  } catch (error: any) {
    console.error('确认售后退款失败:', error)
    ElMessage.error(error?.message || '本次退款结果未确认，请按原请求核对')
    if (definiteRefundRejection(error)) {
      pendingRefundRequest.value = null
      await refreshAfterSalesPreview()
      void loadTable()
    }
  } finally {
    refundSubmitting.value = false
  }
}
const submitRefund = async () => {
  if (refundSubmitting.value || !refundFormRef.value) return
  if (pendingRefundRequest.value) {
    await executeAfterSalesRefund(pendingRefundRequest.value)
    return
  }
  if (refundPreviewLoading.value || !refundPreview.value || !currentRow.value) return
  const afterSalesId = currentRow.value.id
  const quoteId = refundPreview.value.quoteId
  const version = refundPreviewVersion
  await refundFormRef.value.validate(async valid => {
    if (!valid || !currentRow.value) return
    if (refundPreviewLoading.value || !refundPreview.value || currentRow.value.id !== afterSalesId
        || refundPreview.value.quoteId !== quoteId || refundPreviewVersion !== version) {
      ElMessage.warning('售后单或资金预览已变化，请重新核对后确认')
      return
    }
    await executeAfterSalesRefund({afterSalesId, quoteId, refundAmount: refundForm.refundAmount,
      operatorId: userStore.userInfo?.id, row: {...currentRow.value}, preview: refundPreview.value})
  })
}

onMounted(async () => {
  await loadOptions()
  loadTable()
})
</script>

<style scoped lang="scss">
.section-title {
  margin: 18px 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
</style>

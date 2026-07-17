<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>操作日志</h1>
      </div>
    </section>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="queryParams.staffId" clearable filterable placeholder="操作员工" style="width: 220px">
          <el-option v-for="staff in staffOptions" :key="staff.id" :label="staff.name" :value="staff.id" />
        </el-select>
        <el-input v-model="queryParams.module" clearable placeholder="模块，如 order/customer" style="width: 220px" />
        <el-button type="primary" @click="handleQuery" :loading="loading">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="员工" min-width="120">
          <template #default="{ row }">{{ getStaffName(row.staffId) }}</template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="action" label="动作" min-width="140" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" width="80" align="center" />
        <el-table-column prop="requestUri" label="请求路径" min-width="220" show-overflow-tooltip />
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ `${row.costTime ?? 0} ms` }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="traceId" label="TraceId" min-width="180" show-overflow-tooltip />
        <el-table-column label="请求参数" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestParams || '-' }}</template>
        </el-table-column>
        <el-table-column label="异常信息" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMsg || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[20, 50, 100]"
          :total="total"
          @current-change="loadTable"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOperationLogs } from '@/api/log'
import { getStaffList } from '@/api/staff'
import type { OperationLogItem, OperationLogQueryParams } from '@/types/log'
import type { StaffListVO } from '@/types/staff'

const loading = ref(false)
const total = ref(0)
const tableData = ref<OperationLogItem[]>([])
const staffOptions = ref<StaffListVO[]>([])

const queryParams = reactive<OperationLogQueryParams>({
  staffId: undefined,
  module: '',
  pageNum: 1,
  pageSize: 20
})

const formatDateTime = (value?: string) => {
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

const getStaffName = (staffId?: number) => {
  if (!staffId) return '-'
  return staffOptions.value.find(item => item.id === staffId)?.name || `员工#${staffId}`
}

const loadStaffOptions = async () => {
  const page = await getStaffList({ pageNum: 1, pageSize: 1000 })
  staffOptions.value = page.records || []
}

const loadTable = async () => {
  loading.value = true
  try {
    const page = await getOperationLogs({
      staffId: queryParams.staffId,
      module: queryParams.module?.trim() || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    tableData.value = page.records || []
    total.value = page.total || 0
  } catch (error) {
    console.error('加载操作日志失败:', error)
    tableData.value = []
    total.value = 0
    ElMessage.error('加载操作日志失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  loadTable()
}

const handleReset = () => {
  queryParams.staffId = undefined
  queryParams.module = ''
  queryParams.pageNum = 1
  queryParams.pageSize = 20
  loadTable()
}

const handleSizeChange = () => {
  queryParams.pageNum = 1
  loadTable()
}

onMounted(async () => {
  try {
    await loadStaffOptions()
  } catch (error) {
    console.error('加载员工选项失败:', error)
  }
  loadTable()
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

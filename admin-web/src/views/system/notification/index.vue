<template>
  <div class="notification-page">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="queryParams.type" clearable placeholder="通知类型" style="width: 160px">
            <el-option v-for="(label, key) in TYPE_LABELS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="queryParams.isRead" clearable placeholder="阅读状态" style="width: 140px">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
          <el-button type="primary" @click="handleQuery" :loading="loading">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" plain @click="handleMarkAllRead" :disabled="records.length === 0">
            全部标记已读
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)" size="small">
              {{ TYPE_LABELS[row.type] || row.type || '系统通知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isRead ? 'info' : 'danger'" size="small">
              {{ row.isRead ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.isRead" link type="primary" size="small" @click="handleMarkRead(row.id)">
              标记已读
            </el-button>
            <span v-else class="text-muted">已读</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleQuery"
          @current-change="handleQuery"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyNotifications, markAsRead, markAllAsRead, type NotificationItem } from '@/api/notification'

const TYPE_LABELS: Record<string, string> = {
  stock_warning: '库存预警',
  commission: '提成结算',
  order_event: '订单事件',
  approval: '审批',
  system: '系统通知'
}

const TYPE_TAG_MAP: Record<string, string> = {
  stock_warning: 'warning',
  commission: 'success',
  order_event: '',
  approval: 'primary',
  system: 'info'
}

const loading = ref(false)
const records = ref<NotificationItem[]>([])
const total = ref(0)

const queryParams = reactive({
  type: undefined as string | undefined,
  isRead: undefined as number | undefined,
  pageNum: 1,
  pageSize: 20
})

function getTypeTagType(type?: string): string {
  if (!type) return 'info'
  return TYPE_TAG_MAP[type] ?? 'info'
}

function formatDateTime(dt?: string): string {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getMyNotifications(queryParams)
    records.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载通知失败')
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  loadData()
}

function handleReset() {
  queryParams.type = undefined
  queryParams.isRead = undefined
  queryParams.pageNum = 1
  loadData()
}

async function handleMarkRead(id: number) {
  try {
    await markAsRead([id])
    ElMessage.success('已标记为已读')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleMarkAllRead() {
  try {
    await markAllAsRead()
    ElMessage.success('已全部标记为已读')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.text-muted {
  color: #9ca3af;
  font-size: 12px;
}
</style>

<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>客户跟进</h1>
      </div>
      <div class="header-controls">
        <el-button @click="loadAlerts" :loading="loading">刷新</el-button>
      </div>
    </section>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select
          v-model="staffId"
          placeholder="全部员工"
          clearable
          filterable
          style="width: 200px"
        >
          <el-option
            v-for="staff in staffList"
            :key="staff.id"
            :label="staff.name"
            :value="staff.id"
          />
        </el-select>
      </div>

      <el-table
        :data="alerts"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="customerName" label="客户姓名" width="140" />
        <el-table-column prop="customerPhone" label="联系电话" width="140" />
        <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="lastPurchaseDate" label="上次购买" width="130" align="center" />
        <el-table-column prop="expectedRepurchaseDate" label="预计复购" width="130" align="center" />
        <el-table-column label="紧迫度" width="140" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getUrgencyType(row.daysUntilRepurchase)"
              size="default"
            >
              {{ getUrgencyText(row.daysUntilRepurchase) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归属员工" width="140">
          <template #default="{ row }">
            {{ getStaffName(row.bindStaffId) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleContact(row)"
            >
              联系
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && alerts.length === 0" description="暂无需要跟进的客户" />
    </el-card>

    <!-- 联系客户弹窗 -->
    <el-dialog
      v-model="contactDialogVisible"
      title="联系客户"
      width="400px"
    >
      <div v-if="currentCustomer" class="contact-info">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="客户姓名">
            {{ currentCustomer.customerName }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">
            <a :href="`tel:${currentCustomer.customerPhone}`" class="phone-link">
              {{ currentCustomer.customerPhone }}
            </a>
          </el-descriptions-item>
          <el-descriptions-item label="商品">
            {{ currentCustomer.productName }}
          </el-descriptions-item>
          <el-descriptions-item label="预计复购日期">
            {{ currentCustomer.expectedRepurchaseDate }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="contactDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleCall">
          拨打电话
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getRepurchaseAlerts, type RepurchaseAlertItem } from '@/api/follow-up'
import { getStaffList } from '@/api/staff'
import type { StaffListVO } from '@/types/staff'

const staffId = ref<number | undefined>(undefined)
const staffList = ref<StaffListVO[]>([])
const alerts = ref<RepurchaseAlertItem[]>([])
const loading = ref(false)

const contactDialogVisible = ref(false)
const currentCustomer = ref<RepurchaseAlertItem | null>(null)

const loadStaffList = async () => {
  try {
    const res = await getStaffList({ pageNum: 1, pageSize: 100 })
    staffList.value = res.records || []
  } catch (e: any) {
    console.error('加载员工列表失败:', e)
  }
}

const loadAlerts = async () => {
  loading.value = true
  try {
    const data = await getRepurchaseAlerts({
      staffId: staffId.value,
      limit: 100
    })
    alerts.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载复购提醒失败')
    alerts.value = []
  } finally {
    loading.value = false
  }
}

const getStaffName = (id?: number) => {
  if (!id) return ''
  const staff = staffList.value.find(s => s.id === id)
  return staff?.name || ''
}

const getUrgencyType = (days: number): 'danger' | 'warning' | 'success' => {
  if (days <= 0) return 'danger'
  if (days <= 7) return 'warning'
  return 'success'
}

const getUrgencyText = (days: number): string => {
  if (days <= 0) return '已逾期'
  if (days <= 3) return '紧急'
  if (days <= 7) return '待跟进'
  return '提前提醒'
}

const handleContact = (row: RepurchaseAlertItem) => {
  currentCustomer.value = row
  contactDialogVisible.value = true
}

const handleCall = () => {
  if (currentCustomer.value?.customerPhone) {
    window.location.href = `tel:${currentCustomer.value.customerPhone}`
  }
}

watch(staffId, () => {
  loadAlerts()
})

onMounted(() => {
  loadStaffList()
  loadAlerts()
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.contact-info {
  .phone-link {
    color: #409eff;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>

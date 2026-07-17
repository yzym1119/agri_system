<template>
  <div class="coupon-template-selector coupon-selector-dialog" @click.stop @mousedown.stop>
    <div v-if="selectedTemplate" class="selected-template" @click.stop="handleOpenDialog" @mousedown.stop>
      <el-tag closable @close.stop="handleClear" @mousedown.stop type="primary">{{ selectedTemplate.name }}</el-tag>
    </div>
    <el-button v-else @click.stop="handleOpenDialog" @mousedown.stop>选择优惠券模板</el-button>

    <el-dialog 
      v-model="visible" 
      title="选择优惠券模板" 
      width="900px" 
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @close="handleClose"
    >
      <div v-if="visible" class="selector-content" @click.stop @mousedown.stop>
        <!-- 搜索栏 -->
        <div class="search-bar">
          <el-form :inline="true" :model="searchParams">
            <el-form-item label="模板名称">
              <el-input
                v-model="searchParams.name"
                placeholder="请输入模板名称"
                clearable
                style="width: 200px"
                @keyup.enter="handleSearch"
              />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="searchParams.type" placeholder="全部" clearable style="width: 150px">
                <el-option label="满减券" :value="1" />
                <el-option label="折扣券" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 模板列表 -->
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="templateList"
          border
          highlight-current-row
          :row-key="(row: CouponTemplateVO) => row.id"
          @current-change="handleCurrentChange"
          @click.stop
          @mousedown.stop
        >
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="name" label="模板名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.type === 1 ? 'success' : 'warning'">
                {{ row.type === 1 ? '满减券' : '折扣券' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="面额" width="120" align="right">
            <template #default="{ row }">
              <span v-if="row.type === 1">¥{{ row.amount.toFixed(2) }}</span>
              <span v-else>{{ row.amount }}折</span>
            </template>
          </el-table-column>
          <el-table-column prop="minOrderAmount" label="使用门槛" width="120" align="right">
            <template #default="{ row }">
              <span v-if="row.minOrderAmount && row.minOrderAmount > 0">
                ¥{{ row.minOrderAmount.toFixed(2) }}
              </span>
              <span v-else style="color: #909399">无门槛</span>
            </template>
          </el-table-column>
          <el-table-column prop="validityType" label="有效期" width="180" align="center">
            <template #default="{ row }">
              <span v-if="row.validityType === 1">
                {{ formatDateRange(row.validStartTime, row.validEndTime) }}
              </span>
              <span v-else-if="row.validityType === 2 && row.validDays">
                领取后{{ row.validDays }}天
              </span>
              <span v-else style="color: #909399">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination" @click.stop @mousedown.stop>
          <el-pagination
            v-model:current-page="searchParams.pageNum"
            v-model:page-size="searchParams.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            :total="total"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
            @click.stop
            @mousedown.stop
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!currentRow" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {nextTick, reactive, ref, watch} from 'vue'
import {Refresh, Search} from '@element-plus/icons-vue'
import {getCouponTemplateDetail, getCouponTemplateList} from '@/api/marketing'
import type {CouponTemplateQuery, CouponTemplateVO} from '@/types/marketing'

const props = defineProps<{
  modelValue?: number
  showButton?: boolean // 是否显示按钮（默认true）
}>()

const showButton = props.showButton !== false // 默认为true

const emit = defineEmits<{
  'update:modelValue': [value?: number]
  'change': [value?: number, template?: CouponTemplateVO]
}>()

const visible = ref(false)
const loading = ref(false)
const templateList = ref<CouponTemplateVO[]>([])
const total = ref(0)
const currentRow = ref<CouponTemplateVO>()
const selectedTemplate = ref<CouponTemplateVO>()
const tableRef = ref()

const searchParams = reactive<CouponTemplateQuery>({
  name: '',
  type: undefined,
  status: 1, // 强制只显示启用的模板
  pageNum: 1,
  pageSize: 10
})

// 监听 modelValue 变化，加载已选择的模板
watch(() => props.modelValue, async (newValue) => {
  if (newValue) {
    try {
      const template = await getCouponTemplateDetail(newValue)
      selectedTemplate.value = template
    } catch (error) {
      console.error('加载已选择的优惠券模板失败:', error)
      selectedTemplate.value = undefined
    }
  } else {
    selectedTemplate.value = undefined
  }
}, { immediate: true })

// 加载模板列表
const loadTemplateList = async () => {
  loading.value = true
  try {
    const response = await getCouponTemplateList(searchParams)
    templateList.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error('加载优惠券模板列表失败:', error)
    templateList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 监听对话框打开
watch(visible, async val => {
  if (val) {
    await loadTemplateList()
    // 如果有已选择的模板，高亮显示对应的行
    if (props.modelValue && templateList.value.length > 0) {
      await nextTick()
      const selectedRow = templateList.value.find(row => row.id === props.modelValue)
      if (selectedRow && tableRef.value) {
        tableRef.value.setCurrentRow(selectedRow)
        currentRow.value = selectedRow
      }
    }
  }
})

// 搜索
const handleSearch = () => {
  searchParams.pageNum = 1
  loadTemplateList()
}

// 重置
const handleReset = () => {
  searchParams.name = ''
  searchParams.type = undefined
  searchParams.pageNum = 1
  loadTemplateList()
}

// 分页变化
const handlePageChange = (page: number) => {
  searchParams.pageNum = page
  loadTemplateList()
}

const handleSizeChange = (size: number) => {
  searchParams.pageSize = size
  searchParams.pageNum = 1
  loadTemplateList()
}

// 表格行选择
const handleCurrentChange = (row: CouponTemplateVO) => {
  currentRow.value = row
}

// 打开对话框
const handleOpenDialog = () => {
  visible.value = true
}

// 清除选择
const handleClear = () => {
  selectedTemplate.value = undefined
  emit('update:modelValue', undefined)
  emit('change', undefined, undefined)
}

// 确认选择
const handleConfirm = () => {
  if (currentRow.value) {
    selectedTemplate.value = currentRow.value
    emit('update:modelValue', currentRow.value.id)
    emit('change', currentRow.value.id, currentRow.value)
    visible.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  currentRow.value = undefined
}

// 格式化日期范围
const formatDateRange = (start?: string, end?: string) => {
  if (!start || !end) return '-'
  try {
    const startDate = new Date(start)
    const endDate = new Date(end)
    const format = (date: Date) => {
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      return `${year}-${month}-${day}`
    }
    return `${format(startDate)} 至 ${format(endDate)}`
  } catch {
    return `${start} 至 ${end}`
  }
}
</script>

<style scoped lang="scss">
.coupon-template-selector {
  display: inline-block;
  width: 100%;
  
  .selected-template {
    display: inline-block;
    
    .el-tag {
      cursor: pointer;
    }
  }
}

.selector-content {
  .search-bar {
    margin-bottom: 16px;
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>

<style lang="scss">
// 限制分页器样式，防止污染全局
.coupon-selector-dialog {
  .el-pagination {
    .el-select {
      .el-select__wrapper {
        position: relative !important;
      }
    }
  }
  
  // 确保分页下拉框只在对话框内显示
  .el-select-dropdown {
    z-index: 3000 !important;
  }
}
</style>

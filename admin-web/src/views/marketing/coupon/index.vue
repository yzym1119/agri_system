<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="模板名称">
          <el-input v-model="queryParams.name" placeholder="请输入模板名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 150px">
            <el-option label="满减券" :value="1" />
            <el-option label="折扣券" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'marketing:coupon:create'" type="success" :icon="Plus" @click="handleAdd">新增模板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border>
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
            <span v-else>{{ Number(row.amount) }}折</span>
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
        <el-table-column prop="limitCategoryName" label="限制分类" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.limitCategoryName">{{ row.limitCategoryName }}</span>
            <span v-else style="color: #909399">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期规则" min-width="200">
          <template #default="{ row }">
            <div v-if="row.validityType === 1">
              <div>{{ formatDate(row.validStartTime) }} 至</div>
              <div>{{ formatDate(row.validEndTime) }}</div>
            </div>
            <div v-else-if="row.validityType === 2">
              领取后 {{ row.validDays }} 天有效
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="发行总量" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.totalCount === -1">不限</span>
            <span v-else>{{ row.totalCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-permission="'marketing:coupon:update'"
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'marketing:coupon:update'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'marketing:coupon:delete'" link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑优惠券模板' : '新增优惠券模板'"
      width="700px"
      :close-on-click-modal="false"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio :label="1">满减券</el-radio>
            <el-radio :label="2">折扣券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="面额/折扣率" prop="amount">
          <el-input-number
            v-model="formData.amount"
            :min="formData.type === 1 ? 0.01 : 0.1"
            :max="formData.type === 1 ? undefined : 9.9"
            :precision="formData.type === 1 ? 2 : 1"
            :placeholder="formData.type === 1 ? '请输入面额（元）' : '请输入折扣（如：8.5表示85折）'"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            <span v-if="formData.type === 1">满减券：直接减免的金额（元），必须小于使用门槛</span>
            <span v-else>折扣券：填写0.1~9.9之间的数值，如8表示8折（原价×80%），8.5表示8.5折（原价×85%）</span>
          </div>
        </el-form-item>
        <el-form-item label="使用门槛" prop="minOrderAmount">
          <el-input-number
            v-model="formData.minOrderAmount"
            :min="0"
            :precision="2"
            placeholder="0表示无门槛"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">订单金额达到此金额才可使用，0表示无门槛</div>
        </el-form-item>
        <el-form-item label="限制分类" prop="limitCategoryId">
          <el-tree-select
            v-model="formData.limitCategoryId"
            :data="categoryTreeOptions"
            :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true }"
            placeholder="不选择则全场通用"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="有效期类型" prop="validityType">
          <el-radio-group v-model="formData.validityType" @change="handleValidityTypeChange">
            <el-radio :label="1">固定日期范围</el-radio>
            <el-radio :label="2">领取后有效天数</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="formData.validityType === 1"
          label="有效期"
          prop="validDateRange"
        >
          <el-date-picker
            v-model="formData.validDateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          v-if="formData.validityType === 2"
          label="有效天数"
          prop="validDays"
        >
          <el-input-number
            v-model="formData.validDays"
            :min="1"
            :precision="0"
            placeholder="请输入有效天数"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="发行总量" prop="totalCount">
          <el-input-number
            v-model="formData.totalCount"
            :min="-1"
            :precision="0"
            placeholder="-1表示不限"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">-1表示不限，大于0表示限制发放数量</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="isEdit ? 'marketing:coupon:update' : 'marketing:coupon:create'" type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {reactive, ref} from 'vue'
import {Plus, Refresh, Search} from '@element-plus/icons-vue'
import {
  createCouponTemplate,
  deleteCouponTemplate,
  getCouponTemplateList,
  toggleCouponTemplateStatus,
  updateCouponTemplate
} from '@/api/marketing'
import {getCategoryTree} from '@/api/product'
import type {
  CouponTemplateCreateRequest,
  CouponTemplateQuery,
  CouponTemplateUpdateRequest,
  CouponTemplateVO
} from '@/types/marketing'
import type {CategoryTreeVO} from '@/types/product'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'

const queryParams = reactive<CouponTemplateQuery>({
  pageNum: 1,
  pageSize: 10
})

const loading = ref(false)
const tableData = ref<CouponTemplateVO[]>([])
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const categoryTreeOptions = ref<CategoryTreeVO[]>([])

const formData = reactive<{
  id?: number
  name: string
  type: number
  amount: number
  minOrderAmount: number
  limitCategoryId?: number
  validityType: number
  validDateRange?: string[]
  validStartTime?: string
  validEndTime?: string
  validDays?: number
  totalCount: number
  remark?: string
}>({
  name: '',
  type: 1,
  amount: 0,
  minOrderAmount: 0,
  limitCategoryId: undefined,
  validityType: 1,
  validDateRange: undefined,
  validStartTime: undefined,
  validEndTime: undefined,
  validDays: 30,
  totalCount: -1,
  remark: ''
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入面额/折扣率', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (formData.type === 2) {
          if (value < 0.1 || value > 9.9) {
            callback(new Error('折扣必须在0.1~9.9之间（如8表示8折）'))
          } else {
            callback()
          }
        } else if (formData.type === 1) {
          if (value <= 0) {
            callback(new Error('面额必须大于0'))
          } else if (formData.minOrderAmount > 0 && value >= formData.minOrderAmount) {
            callback(new Error('面额必须小于使用门槛金额'))
          } else {
            callback()
          }
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  minOrderAmount: [{ required: true, message: '请输入使用门槛', trigger: 'blur' }],
  validityType: [{ required: true, message: '请选择有效期类型', trigger: 'change' }],
  validDateRange: [
    {
      validator: (rule, value, callback) => {
        if (formData.validityType === 1 && (!value || value.length !== 2)) {
          callback(new Error('请选择有效期范围'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  validDays: [
    {
      validator: (rule, value, callback) => {
        if (formData.validityType === 2 && (!value || value <= 0)) {
          callback(new Error('请输入有效天数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  totalCount: [{ required: true, message: '请输入发行总量', trigger: 'blur' }]
}

// 加载分类树
const loadCategoryTree = async () => {
  try {
    categoryTreeOptions.value = await getCategoryTree()
  } catch (error) {
    console.error('加载分类树失败:', error)
  }
}

// 加载列表
const loadTable = async () => {
  loading.value = true
  try {
    const response = await getCouponTemplateList(queryParams)
    tableData.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error('加载优惠券模板列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  queryParams.pageNum = 1
  loadTable()
}

// 重置
const handleReset = () => {
  queryParams.name = ''
  queryParams.type = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  loadTable()
}

// 分页
const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  loadTable()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadTable()
}

// 新增
const handleAdd = async () => {
  isEdit.value = false
  resetForm()
  await loadCategoryTree()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: CouponTemplateVO) => {
  isEdit.value = true
  resetForm()
  await loadCategoryTree()
  
  formData.id = row.id
  formData.name = row.name
  formData.type = row.type
  formData.amount = row.amount
  formData.minOrderAmount = row.minOrderAmount || 0
  formData.limitCategoryId = row.limitCategoryId
  formData.validityType = row.validityType
  formData.totalCount = row.totalCount
  formData.remark = row.remark || ''
  
  if (row.validityType === 1) {
    formData.validDateRange = row.validStartTime && row.validEndTime
      ? [row.validStartTime, row.validEndTime]
      : undefined
  } else {
    formData.validDays = row.validDays || 30
  }
  
  dialogVisible.value = true
}

// 删除
const handleDelete = (row: CouponTemplateVO) => {
  ElMessageBox.confirm(`确定要删除模板"${row.name}"吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        await deleteCouponTemplate(row.id)
        ElMessage.success('删除成功')
        loadTable()
      } catch (error: any) {
        ElMessage.error(error.message || '删除失败')
      }
    })
    .catch(() => {})
}

// 状态切换
const handleStatusChange = async (row: CouponTemplateVO) => {
  try {
    await toggleCouponTemplateStatus(row.id, row.status)
    ElMessage.success(row.status === 1 ? '发布成功' : '下架成功')
    loadTable()
  } catch (error: any) {
    // 恢复原状态
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error(error.message || '操作失败')
  }
}

// 有效期类型变化
const handleValidityTypeChange = () => {
  formData.validDateRange = undefined
  formData.validDays = 30
}

// 提交
const handleSubmit = () => {
  formRef.value?.validate(async valid => {
    if (!valid) return
    
    submitLoading.value = true
    try {
      if (formData.validityType === 1) {
        if (!formData.validDateRange || formData.validDateRange.length !== 2) {
          ElMessage.error('请选择有效期范围')
          submitLoading.value = false
          return
        }
        formData.validStartTime = formData.validDateRange[0]
        formData.validEndTime = formData.validDateRange[1]
      }
      
      if (isEdit.value) {
        const updateData: CouponTemplateUpdateRequest = {
          id: formData.id!,
          name: formData.name,
          type: formData.type,
          amount: formData.amount,
          minOrderAmount: formData.minOrderAmount,
          limitCategoryId: formData.limitCategoryId,
          validityType: formData.validityType,
          validStartTime: formData.validStartTime,
          validEndTime: formData.validEndTime,
          validDays: formData.validDays,
          totalCount: formData.totalCount,
          remark: formData.remark
        }
        await updateCouponTemplate(formData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: CouponTemplateCreateRequest = {
          name: formData.name,
          type: formData.type,
          amount: formData.amount,
          minOrderAmount: formData.minOrderAmount,
          limitCategoryId: formData.limitCategoryId,
          validityType: formData.validityType,
          validStartTime: formData.validStartTime,
          validEndTime: formData.validEndTime,
          validDays: formData.validDays,
          totalCount: formData.totalCount,
          remark: formData.remark
        }
        await createCouponTemplate(createData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadTable()
    } catch (error: any) {
      // 如果是coupon-5003错误，显示详细错误信息
      const errorMsg = error.message || '操作失败'
      if (errorMsg.includes('coupon-5003') || errorMsg.includes('发行总量不能小于已发放数量')) {
        ElMessage.error(errorMsg)
      } else {
        ElMessage.error(errorMsg)
      }
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.id = undefined
  formData.name = ''
  formData.type = 1
  formData.amount = 0
  formData.minOrderAmount = 0
  formData.limitCategoryId = undefined
  formData.validityType = 1
  formData.validDateRange = undefined
  formData.validStartTime = undefined
  formData.validEndTime = undefined
  formData.validDays = 30
  formData.totalCount = -1
  formData.remark = ''
  formRef.value?.clearValidate()
}

// 关闭对话框
const handleDialogClose = () => {
  resetForm()
}

// 格式化日期
const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 初始化
loadCategoryTree()
loadTable()
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}

.search-card {
  margin-bottom: 12px;
}

.table-card {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>

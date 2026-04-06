<template>
  <div class="supplier-container">
    <el-card>
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="queryParams" class="search-form">
          <el-form-item label="供应商编码">
            <el-input
              v-model="queryParams.code"
              placeholder="请输入供应商编码"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="供应商名称">
            <el-input
              v-model="queryParams.name"
              placeholder="请输入供应商名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="queryParams.status"
              placeholder="请选择状态"
              clearable
              style="width: 150px"
            >
              <el-option label="正常" :value="1" />
              <el-option label="停用" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增供应商</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="supplierList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="code" label="供应商编码" width="150" />
        <el-table-column prop="name" label="供应商名称" width="200" />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="contactPhone" label="联系电话" width="150" />
        <el-table-column prop="bankName" label="开户银行" width="150" show-overflow-tooltip />
        <el-table-column prop="bankAccount" label="银行账户" width="180" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <el-form-item label="供应商名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="formData.contactName" placeholder="请输入联系人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="开户银行" prop="bankName">
          <el-input v-model="formData.bankName" placeholder="请输入开户银行" />
        </el-form-item>
        <el-form-item label="银行账户" prop="bankAccount">
          <el-input v-model="formData.bankAccount" placeholder="请输入银行账户" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input
            v-model="formData.address"
            type="textarea"
            :rows="2"
            placeholder="请输入供应商地址"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status" v-if="isEdit">
          <el-switch
            v-model="formData.status"
            :active-value="1"
            :inactive-value="2"
            active-text="正常"
            inactive-text="停用"
          />
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
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit, Plus, Refresh, Search} from '@element-plus/icons-vue'
import {createSupplier, deleteSupplier, getSupplierList, updateSupplier} from '@/api/supplier'
import type {
  PageResponse,
  SupplierCreateRequest,
  SupplierDetailVO,
  SupplierQueryParams,
  SupplierUpdateRequest
} from '@/types/supplier'

// 加载状态
const loading = ref(false)
const submitLoading = ref(false)

// 供应商列表
const supplierList = ref<SupplierDetailVO[]>([])
const total = ref(0)

// 查询参数
const queryParams = reactive<SupplierQueryParams>({
  code: '',
  name: '',
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<SupplierCreateRequest & { id?: number }>({
  name: '',
  contactName: '',
  contactPhone: '',
  bankName: '',
  bankAccount: '',
  address: '',
  status: 1,
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  contactPhone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑供应商' : '新增供应商'
})

// 加载供应商列表
const loadSupplierList = async () => {
  loading.value = true
  try {
    const response: PageResponse<SupplierDetailVO> = await getSupplierList(queryParams)
    supplierList.value = response.records
    total.value = response.total
  } catch (error) {
    console.error('加载供应商列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 查询
const handleSearch = () => {
  queryParams.pageNum = 1
  loadSupplierList()
}

// 重置
const handleReset = () => {
  queryParams.code = ''
  queryParams.name = ''
  queryParams.status = undefined
  queryParams.pageNum = 1
  loadSupplierList()
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: SupplierDetailVO) => {
  isEdit.value = true
  resetForm()
  formData.id = row.id
  formData.name = row.name
  formData.contactName = row.contactName
  formData.contactPhone = row.contactPhone
  formData.bankName = row.bankName
  formData.bankAccount = row.bankAccount
  formData.address = row.address
  formData.status = row.status
  formData.remark = row.remark
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row: SupplierDetailVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除供应商"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteSupplier(row.id)
    ElMessage.success('删除成功')
    await loadSupplierList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (isEdit.value) {
        const updateData: SupplierUpdateRequest = {
          id: formData.id!,
          name: formData.name,
          contactName: formData.contactName,
          contactPhone: formData.contactPhone,
          bankName: formData.bankName,
          bankAccount: formData.bankAccount,
          address: formData.address,
          status: formData.status,
          remark: formData.remark
        }
        await updateSupplier(formData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: SupplierCreateRequest = {
          name: formData.name,
          contactName: formData.contactName,
          contactPhone: formData.contactPhone,
          bankName: formData.bankName,
          bankAccount: formData.bankAccount,
          address: formData.address,
          status: formData.status,
          remark: formData.remark
        }
        await createSupplier(createData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      await loadSupplierList()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  formData.id = undefined
  formData.name = ''
  formData.contactName = ''
  formData.contactPhone = ''
  formData.bankName = ''
  formData.bankAccount = ''
  formData.address = ''
  formData.status = 1
  formData.remark = ''
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

// 分页大小改变
const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadSupplierList()
}

// 当前页改变
const handleCurrentChange = (page: number) => {
  queryParams.pageNum = page
  loadSupplierList()
}

// 初始化
onMounted(() => {
  loadSupplierList()
})
</script>

<style scoped lang="scss">
.supplier-container {
  padding: 20px;
}

.search-bar {
  margin-bottom: 20px;
}

.search-form {
  .el-form-item {
    margin-bottom: 0;
  }
}

.toolbar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>


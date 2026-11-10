<template>
  <div class="store-container">
    <el-card>
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="queryParams" class="search-form">
          <el-form-item label="门店编码">
            <el-input
              v-model="queryParams.code"
              placeholder="请输入门店编码"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="门店名称">
            <el-input
              v-model="queryParams.name"
              placeholder="请输入门店名称"
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
        <el-button v-permission="'store:create'" type="primary" :icon="Plus" @click="handleAdd">新增门店</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="storeList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="code" label="门店编码" width="120" />
        <el-table-column prop="name" label="门店名称" width="200" />
        <el-table-column prop="phone" label="联系电话" width="150" />
        <el-table-column prop="address" label="门店地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="managerName" label="店长" width="120" />
        <el-table-column label="是否自提" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.supportPickup === 1 ? 'success' : 'info'">
              {{ row.supportPickup === 1 ? '支持' : '不支持' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'store:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'store:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
            <el-button v-if="canPublish" type="primary" link @click="openPublication(row)">公开资料</el-button>
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

    <StorePublicationDialog v-model="publicationOpen" :store-id="publicationStoreId" />
    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="门店名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入门店名称" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="门店地址" prop="address">
          <el-input
            v-model="formData.address"
            type="textarea"
            :rows="3"
            placeholder="请输入门店地址"
          />
        </el-form-item>
        <el-form-item label="店长" prop="managerId">
          <el-select
            v-model="formData.managerId"
            placeholder="请选择店长（可选）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="staff in staffList"
              :key="staff.id"
              :label="`${staff.name} (${staff.code})`"
              :value="staff.id"
            />
          </el-select>
          <div class="form-tip">提示：店长需从员工列表中选择</div>
        </el-form-item>
        <el-form-item label="是否自提" prop="supportPickup">
          <el-switch
            v-model="formData.supportPickup"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="isEdit" v-permission="'store:update'" type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
        <el-button v-else v-permission="'store:create'" type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit, Plus, Refresh, Search} from '@element-plus/icons-vue'
import {createStore, deleteStore, getStoreDetail, getStoreList, updateStore} from '@/api/store'
import type {StoreCreateRequest, StoreDetailVO, StoreQueryParams, StoreUpdateRequest} from '@/types/store'
import StorePublicationDialog from './StorePublicationDialog.vue'
import {usePermissionStore} from '@/store/permission'
const permissions=usePermissionStore(),publicationOpen=ref(false),publicationStoreId=ref('')
const canPublish=computed(()=>permissions.permissions.includes('store:view')&&permissions.permissions.includes('store:publish'))
function openPublication(row:StoreDetailVO){if(typeof row.id==='number'&&!Number.isSafeInteger(row.id))return;publicationStoreId.value=String(row.id);publicationOpen.value=true}

// 加载状态
const loading = ref(false)
const submitLoading = ref(false)

// 查询参数
const queryParams = reactive<StoreQueryParams>({
  code: '',
  name: '',
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

// 门店列表
const storeList = ref<StoreDetailVO[]>([])
const total = ref(0)

// 员工列表（用于选择店长）
const staffList = ref<Array<{ id: number; name: string; code: string }>>([])

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<StoreCreateRequest & { id?: number }>({
  name: '',
  phone: '',
  address: '',
  managerId: undefined,
  supportPickup: 0,
  status: 1,
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入门店名称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑门店' : '新增门店'
})

// 加载门店列表
const loadStoreList = async () => {
  loading.value = true
  try {
    const response = await getStoreList(queryParams)
    storeList.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error('加载门店列表失败:', error)
    storeList.value = []
    total.value = 0
    ElMessage.error('加载门店列表失败')
  } finally {
    loading.value = false
  }
}

// 加载员工列表（用于选择店长）
const loadStaffList = async () => {
  try {
    const { getStaffList } = await import('@/api/staff')
    const response = await getStaffList({ pageNum: 1, pageSize: 1000 })
    staffList.value = (response.records || []).map(staff => ({
      id: staff.id,
      name: staff.name,
      code: staff.code
    }))
  } catch (error) {
    console.error('加载员工列表失败:', error)
    ElMessage.error('加载员工列表失败')
  staffList.value = []
  }
}

// 搜索
const handleSearch = () => {
  queryParams.pageNum = 1
  loadStoreList()
}

// 重置
const handleReset = () => {
  queryParams.code = ''
  queryParams.name = ''
  queryParams.status = undefined
  queryParams.pageNum = 1
  loadStoreList()
}

// 新增
const handleAdd = async () => {
  isEdit.value = false
  resetForm()
  await loadStaffList()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: StoreDetailVO) => {
  isEdit.value = true
  resetForm()
  await loadStaffList()
  
  try {
    const detail = await getStoreDetail(row.id)
    formData.id = detail.id
    formData.name = detail.name
    formData.phone = detail.phone || ''
    formData.address = detail.address || ''
    formData.managerId = detail.managerId
    formData.supportPickup = detail.supportPickup
    formData.status = detail.status
    formData.remark = detail.remark || ''
    dialogVisible.value = true
  } catch (error) {
    console.error('加载门店详情失败:', error)
    ElMessage.error('加载门店详情失败')
  }
}

// 删除
const handleDelete = async (row: StoreDetailVO) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除门店"${row.name}"吗？删除后门店编码将被占用，无法再次使用。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteStore(row.id)
    ElMessage.success('删除成功')
    await loadStoreList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error(error?.message || '删除失败')
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
        const updateData: StoreUpdateRequest = {
          id: formData.id!, // 必须包含ID
          name: formData.name,
          phone: formData.phone,
          address: formData.address,
          managerId: formData.managerId,
          supportPickup: formData.supportPickup,
          remark: formData.remark
        }
        await updateStore(formData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: StoreCreateRequest = {
          name: formData.name,
          phone: formData.phone,
          address: formData.address,
          managerId: formData.managerId,
          supportPickup: formData.supportPickup,
          status: formData.status,
          remark: formData.remark
          // code 留空，后端自动生成
        }
        await createStore(createData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      await loadStoreList()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.id = undefined
  formData.name = ''
  formData.phone = ''
  formData.address = ''
  formData.managerId = undefined
  formData.supportPickup = 0
  formData.status = 1
  formData.remark = ''
  formRef.value?.resetFields()
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

// 分页
const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadStoreList()
}

const handleCurrentChange = (page: number) => {
  queryParams.pageNum = page
  loadStoreList()
}

// 初始化
onMounted(() => {
  loadStoreList()
})
</script>


<style scoped lang="scss">
.store-container {
  padding: 20px;
}

.search-bar {
  margin-bottom: 20px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;

  .search-form {
    margin: 0;
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

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>

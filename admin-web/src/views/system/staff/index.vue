<template>
  <div class="staff-container">
    <el-card>
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="queryParams" class="search-form">
          <el-form-item label="员工编码">
            <el-input
              v-model="queryParams.code"
              placeholder="请输入员工编码"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="员工姓名">
            <el-input
              v-model="queryParams.name"
              placeholder="请输入员工姓名"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="门店">
            <el-select
              v-model="queryParams.storeId"
              placeholder="请选择门店"
              clearable
              style="width: 200px"
            >
              <el-option
                v-for="store in storeList"
                :key="store.id"
                :label="store.name"
                :value="store.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="queryParams.status"
              placeholder="请选择状态"
              clearable
              style="width: 150px"
            >
              <el-option label="在职" :value="1" />
              <el-option label="离职" :value="2" />
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
        <el-button v-permission="'staff:create'" type="primary" :icon="Plus" @click="handleAdd">新增员工</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="staffList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="code" label="员工编码" width="120" />
        <el-table-column prop="name" label="员工姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column prop="storeName" label="所属门店" width="150" />
        <el-table-column label="角色" width="200">
          <template #default="{ row }">
            <el-tag
              v-if="row.roles && row.roles.length > 0"
              v-for="role in row.roles"
              :key="role.id"
              size="small"
              style="margin-right: 5px"
            >
              {{ role.name }}
            </el-tag>
            <span v-else class="text-muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '在职' : '离职' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'staff:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status === 1"
              v-permission="'staff:disable'"
              type="warning"
              link
              :icon="Lock"
              @click="handleDisable(row)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              v-permission="'staff:enable'"
              type="success"
              link
              :icon="Unlock"
              @click="handleEnable(row)"
            >
              启用
            </el-button>
            <el-button v-permission="'staff:resetPassword'" type="info" link :icon="Key" @click="handleResetPassword(row)">
              重置密码
            </el-button>
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
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="员工姓名" prop="name">
          <el-input v-model="formData.name" placeholder="请输入员工姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="所属门店" prop="storeId">
          <el-select
            v-model="formData.storeId"
            placeholder="请选择门店"
            style="width: 100%"
          >
            <el-option
              v-for="store in storeList"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select
            v-model="formData.roleId"
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in roleList"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="isEdit ? 'staff:update' : 'staff:create'" type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="重置密码"
      width="400px"
      @close="handlePasswordDialogClose"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button v-permission="'staff:resetPassword'" type="primary" :loading="passwordSubmitLoading" @click="handlePasswordSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Edit, Key, Lock, Plus, Refresh, Search, Unlock} from '@element-plus/icons-vue'
import {
  createStaff,
  disableStaff,
  enableStaff,
  getStaffList,
  getStaffProfile,
  resetStaffPassword,
  updateStaff
} from '@/api/staff'
import {getAllStores} from '@/api/store'
import {getRoleList} from '@/api/role'
import type {
  StaffCreateRequest,
  StaffListVO,
  StaffQueryParams,
  StaffResetPasswordRequest,
  StaffUpdateRequest
} from '@/types/staff'
import type {StoreDetailVO} from '@/types/store'
import type {RoleVO} from '@/types/role'

// 加载状态
const loading = ref(false)
const submitLoading = ref(false)
const passwordSubmitLoading = ref(false)

// 查询参数
const queryParams = reactive<StaffQueryParams>({
  code: '',
  name: '',
  storeId: undefined,
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

// 员工列表
const staffList = ref<StaffListVO[]>([])
const total = ref(0)

// 门店列表（用于下拉选择）
const storeList = ref<StoreDetailVO[]>([])

// 角色列表（用于下拉选择）
const roleList = ref<RoleVO[]>([])

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<StaffCreateRequest & { id?: number }>({
  code: '',
  name: '',
  phone: '',
  password: '',
  storeId: undefined as any, // 初始化为undefined，避免显示0
  roleId: undefined as any // 单选，初始化为undefined
})

// 表单验证规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑员工' : '新增员工'
})

// 重置密码对话框
const passwordDialogVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  staffId: 0,
  newPassword: '',
  confirmPassword: ''
})

// 密码验证规则
const passwordRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 加载员工列表
const loadStaffList = async () => {
  loading.value = true
  try {
    const response = await getStaffList(queryParams)
    staffList.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error('加载员工列表失败:', error)
    staffList.value = []
    total.value = 0
    ElMessage.error('加载员工列表失败')
  } finally {
    loading.value = false
  }
}

// 加载门店列表
const loadStoreList = async () => {
  try {
    storeList.value = await getAllStores()
  } catch (error) {
    console.error('加载门店列表失败:', error)
    ElMessage.error('加载门店列表失败')
  }
}

// 加载角色列表
const loadRoleList = async () => {
  try {
    roleList.value = await getRoleList()
  } catch (error) {
    console.error('加载角色列表失败:', error)
    ElMessage.error('加载角色列表失败')
  }
}

// 搜索
const handleSearch = () => {
  queryParams.pageNum = 1
  loadStaffList()
}

// 重置
const handleReset = () => {
  queryParams.code = ''
  queryParams.name = ''
  queryParams.storeId = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  loadStaffList()
}

// 新增
const handleAdd = async () => {
  isEdit.value = false
  resetForm()
  await Promise.all([loadStoreList(), loadRoleList()])
  // 使用nextTick确保表单完全重置后再打开对话框
  await nextTick()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: StaffListVO) => {
  isEdit.value = true
  resetForm()
  await Promise.all([loadStoreList(), loadRoleList()])

      try {
        const profile = await getStaffProfile(row.id)
        formData.id = profile.id
        formData.name = profile.name
    formData.phone = profile.phone || ''
    formData.storeId = profile.storeId
        const roleIds = profile.roleIds || []
        formData.roleId = roleIds.length > 0 ? roleIds[0] : (undefined as any)
    dialogVisible.value = true
  } catch (error) {
    console.error('加载员工详情失败:', error)
    ElMessage.error('加载员工详情失败')
  }
}

// 禁用
const handleDisable = async (row: StaffListVO) => {
  try {
    await ElMessageBox.confirm(
      `确定要禁用员工"${row.name}"吗？禁用后该员工将无法登录系统。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await disableStaff(row.id)
    ElMessage.success('禁用成功')
    await loadStaffList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('禁用失败:', error)
      ElMessage.error('禁用失败')
    }
  }
}

// 启用
const handleEnable = async (row: StaffListVO) => {
  try {
    await enableStaff(row.id)
    ElMessage.success('启用成功')
    await loadStaffList()
  } catch (error) {
    console.error('启用失败:', error)
    ElMessage.error('启用失败')
  }
}

// 重置密码
const handleResetPassword = (row: StaffListVO) => {
  passwordForm.staffId = row.id
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (isEdit.value) {
        const updateData: StaffUpdateRequest = {
          id: formData.id!, // 必须包含ID
          name: formData.name,
          phone: formData.phone!,
          storeId: formData.storeId,
          roleId: formData.roleId!
        }
        await updateStaff(formData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: StaffCreateRequest = {
          name: formData.name,
          phone: formData.phone!,
          password: formData.password,
          storeId: formData.storeId,
          roleId: formData.roleId!
          // code 留空，后端自动生成
        }
        await createStaff(createData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      await loadStaffList()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
}

// 提交重置密码
const handlePasswordSubmit = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return

    passwordSubmitLoading.value = true
    try {
      const resetData: StaffResetPasswordRequest = {
        staffId: passwordForm.staffId,
        newPassword: passwordForm.newPassword
      }
      await resetStaffPassword(resetData)
      ElMessage.success('密码重置成功')
      passwordDialogVisible.value = false
    } catch (error) {
      console.error('重置密码失败:', error)
    } finally {
      passwordSubmitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  // 先重置表单验证状态
  formRef.value?.resetFields()
  // 然后清空所有字段值，确保没有残留数据
  formData.id = undefined
  formData.code = ''
  formData.name = ''
  formData.phone = ''
  formData.password = ''
  formData.storeId = undefined as any
  formData.roleId = undefined as any
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

const handlePasswordDialogClose = () => {
  passwordForm.staffId = 0
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.resetFields()
}

// 分页
const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadStaffList()
}

const handleCurrentChange = (page: number) => {
  queryParams.pageNum = page
  loadStaffList()
}

// 初始化
onMounted(async () => {
  await Promise.all([loadStoreList(), loadRoleList(), loadStaffList()])
})
</script>

<style scoped lang="scss">
.text-muted {
  color: #9ca3af;
  font-size: 12px;
}
</style>

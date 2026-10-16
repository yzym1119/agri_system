<template>
  <div class="role-container">
    <el-card>
      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button v-permission="'role:save'" type="primary" :icon="Plus" @click="handleAdd">新增角色</el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="roleList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="code" label="角色编码" width="150" />
        <el-table-column prop="name" label="角色名称" width="200" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'role:save'" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'role:assign'" type="warning" link :icon="Setting" @click="handleAssignMenus(row)">
              分配权限
            </el-button>
            <el-button v-permission="'role:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="'role:save'" type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog
      v-model="menuDialogVisible"
      title="分配权限"
      width="600px"
      @close="handleMenuDialogClose"
    >
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ children: 'children', label: 'name' }"
        show-checkbox
        node-key="id"
        :default-expand-all="true"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button v-permission="'role:assign'" type="primary" :loading="menuSubmitLoading" @click="handleMenuSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, reactive, ref} from 'vue'
import type {ElTree} from 'element-plus'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit, Plus, Setting} from '@element-plus/icons-vue'
import {assignRoleMenus, createRole, deleteRole, getMenuTree, getRoleList, getRoleMenus, updateRole} from '@/api/role'
import type {MenuTreeNode, RoleCreateRequest, RoleUpdateRequest, RoleVO} from '@/types/role'

// 加载状态
const loading = ref(false)
const submitLoading = ref(false)
const menuSubmitLoading = ref(false)

// 角色列表
const roleList = ref<RoleVO[]>([])

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
let formSession = 0

// 表单数据
const formData = reactive<RoleCreateRequest & { id?: number }>({
  name: '',
  status: 1
})

// 表单验证规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择角色状态', trigger: 'change' }]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑角色' : '新增角色'
})

// 权限分配对话框
const menuDialogVisible = ref(false)
const menuTreeRef = ref<InstanceType<typeof ElTree>>()
const menuTree = ref<MenuTreeNode[]>([])
const checkedMenuIds = ref<number[]>([])
const currentRoleId = ref<number>()

// 加载角色列表
const loadRoleList = async () => {
  loading.value = true
  try {
    roleList.value = await getRoleList()
  } catch (error) {
    console.error('加载角色列表失败:', (error as any)?.response?.status, (error as any)?.code)
    ElMessage.error('加载角色列表失败')
  } finally {
    loading.value = false
  }
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: RoleVO) => {
  isEdit.value = true
  resetForm()
  formData.id = row.id
  formData.name = row.name
  formData.status = row.status ?? 1
  // code 在编辑时不可修改，不设置
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row: RoleVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除角色"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteRole(row.id)
    ElMessage.success('删除成功')
    await loadRoleList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', (error as any)?.response?.status, (error as any)?.code)
      ElMessage.error('删除失败')
    }
  }
}

// 分配权限
const handleAssignMenus = async (row: RoleVO) => {
  currentRoleId.value = row.id
  checkedMenuIds.value = []
  menuDialogVisible.value = true

  try {
    // 加载菜单树
    menuTree.value = await getMenuTree()
    
    // 加载角色已分配的菜单ID
    const roleMenuIds = await getRoleMenus(row.id)
    checkedMenuIds.value = roleMenuIds
    
    // 使用nextTick确保树组件已渲染后再设置选中状态
    await nextTick()
    if (menuTreeRef.value) {
      menuTreeRef.value.setCheckedKeys(checkedMenuIds.value, false)
    }
  } catch (error) {
    console.error('加载菜单树失败:', (error as any)?.response?.status, (error as any)?.code)
    ElMessage.error('加载菜单树失败')
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value || submitLoading.value || !dialogVisible.value) return
  const session = formSession
  const editing = isEdit.value
  const snapshot = {...formData}
  const fields = JSON.stringify(snapshot)
  const isCurrent = () => session === formSession && dialogVisible.value && isEdit.value === editing &&
    formData.id === snapshot.id && JSON.stringify(formData) === fields
  submitLoading.value = true
  try {
    await formRef.value.validate(async (valid) => {
      if (!valid || !isCurrent()) return
      const data: RoleCreateRequest | RoleUpdateRequest = {name: snapshot.name, status: snapshot.status}
      if (editing) await updateRole(snapshot.id!, data)
      else await createRole(data)
      if (!isCurrent()) return
      ElMessage.success(editing ? '更新成功' : '新增成功')
      dialogVisible.value = false
      await loadRoleList()
    })
  } catch (error) {
    if (isCurrent()) console.error('提交失败:', (error as any)?.response?.status, (error as any)?.code)
  } finally {
    if (session === formSession) submitLoading.value = false
  }
}

// 提交权限分配
const handleMenuSubmit = async () => {
  if (!menuTreeRef.value || !currentRoleId.value) return

  menuSubmitLoading.value = true
  try {
    const checkedKeys = menuTreeRef.value.getCheckedKeys() as number[]
    const halfCheckedKeys = menuTreeRef.value.getHalfCheckedKeys() as number[]
    const menuIds = [...checkedKeys, ...halfCheckedKeys]

    await assignRoleMenus(currentRoleId.value, menuIds)
    ElMessage.success('权限分配成功')
    menuDialogVisible.value = false
  } catch (error) {
    console.error('分配权限失败:', (error as any)?.response?.status, (error as any)?.code)
    ElMessage.error('分配权限失败')
  } finally {
    menuSubmitLoading.value = false
  }
}

// 重置表单
const resetForm = () => {
  formSession++
  submitLoading.value = false
  formData.id = undefined
  formData.name = ''
  formData.status = 1
  formRef.value?.resetFields()
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

const handleMenuDialogClose = () => {
  currentRoleId.value = undefined
  checkedMenuIds.value = []
  menuTree.value = []
}

// 初始化
onMounted(() => {
  loadRoleList()
})
</script>

<style scoped lang="scss">
.role-container {
  padding: 20px;
}

.toolbar {
  margin-bottom: 20px;
}
</style>

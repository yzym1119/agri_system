<template>
  <div class="category-container">
    <el-card>
      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button v-permission="'product:category:create'" type="primary" :icon="Plus" @click="handleAdd">新增分类</el-button>
      </div>

      <!-- 树形表格 -->
      <el-table
        v-loading="loading"
        :data="categoryList"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        border
        stripe
        style="width: 100%"
        default-expand-all
        :indent="24"
      >
        <el-table-column prop="code" label="分类编码" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.code }}
          </template>
        </el-table-column>
        <el-table-column prop="name" label="分类名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="父分类" width="150">
          <template #default="{ row }">
            <span v-if="row.parentId && row.parentName">{{ row.parentName }}</span>
            <span v-else style="color: #909399">顶级分类</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'product:category:update'" type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'product:category:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-form-item label="父分类" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="categoryTreeOptions"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            placeholder="请选择父分类（不选则为顶级分类）"
            check-strictly
            clearable
            style="width: 100%"
            @clear="handleParentIdClear"
          />
        </el-form-item>
        <!-- 分类编码由后台自动生成，前端不显示 -->
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number
            v-model="formData.sortOrder"
            :min="0"
            :max="9999"
            placeholder="数字越小越靠前"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status" v-if="isEdit">
          <el-switch
            v-model="formData.status"
            :active-value="1"
            :inactive-value="2"
            active-text="启用"
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
        <el-button v-permission="isEdit ? 'product:category:update' : 'product:category:create'" type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit, Plus} from '@element-plus/icons-vue'
import {createCategory, deleteCategory, getCategoryTree, updateCategory} from '@/api/product'
import type {CategoryCreateRequest, CategoryTreeVO, CategoryUpdateRequest} from '@/types/product'

// 加载状态
const loading = ref(false)
const submitLoading = ref(false)

// 分类列表（树形数据）
const categoryList = ref<CategoryTreeVO[]>([])

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<CategoryCreateRequest & { id?: number; status?: number }>({
  code: '',
  name: '',
  parentId: undefined,
  sortOrder: 0,
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  // code 可选，后台自动生成
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑分类' : '新增分类'
})

// 分类树选项（用于父分类选择）
const categoryTreeOptions = computed(() => {
  const buildOptions = (categories: CategoryTreeVO[], excludeId?: number): any[] => {
    return categories
      .filter(cat => cat.id !== excludeId)
      .map(cat => ({
        id: cat.id,
        name: cat.name,
        children: cat.children ? buildOptions(cat.children, excludeId) : []
      }))
  }
  return buildOptions(categoryList.value, formData.id)
})

// 加载分类树
const loadCategoryTree = async () => {
  loading.value = true
  try {
    categoryList.value = await getCategoryTree()
  } catch (error) {
    console.error('加载分类树失败:', error)
    ElMessage.error('加载分类树失败')
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
const handleEdit = async (row: CategoryTreeVO) => {
  isEdit.value = true
  resetForm()
  formData.id = row.id
  formData.name = row.name
  // 明确处理：如果有 parentId 则使用，否则设为 null（表示顶级分类）
  formData.parentId = row.parentId && row.parentId > 0 ? row.parentId : null
  formData.sortOrder = row.sortOrder || 0
  formData.status = row.status
  formData.remark = row.remark || ''
  // 等待DOM更新后再打开对话框，确保tree-select能正确回显
  await nextTick()
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row: CategoryTreeVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除分类"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    await loadCategoryTree()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      const errorMessage =
        error?.response?.data?.message ||
        error?.response?.data?.data?.message ||
        error?.message ||
        '删除失败，请稍后再试'
      ElMessage.error(errorMessage)
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
        const updateData: CategoryUpdateRequest = {
          id: formData.id!,
          name: formData.name,
          // 关键修复：当 parentId 为 null、undefined 或 0 时，都明确设置为 null
          // 这样后端才能识别到要清空父分类
          parentId: formData.parentId === null || formData.parentId === undefined || formData.parentId === 0 ? null : formData.parentId,
          sortOrder: formData.sortOrder || 0,
          status: formData.status,
          remark: formData.remark
        }
        await updateCategory(formData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: CategoryCreateRequest = {
          code: formData.code || undefined, // 留空则后台自动生成
          name: formData.name,
          parentId: formData.parentId,
          sortOrder: formData.sortOrder || 0,
          remark: formData.remark
        }
        await createCategory(createData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      await loadCategoryTree()
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
  formData.code = ''
  formData.name = ''
  formData.parentId = null // 明确设置为 null，表示顶级分类
  formData.sortOrder = 0
  formData.status = 1
  formData.remark = ''
}

// 清空父分类时的处理
const handleParentIdClear = () => {
  // 当用户点击清空按钮时，确保 parentId 被设置为 null
  formData.parentId = null
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

// 初始化
onMounted(() => {
  loadCategoryTree()
})
</script>

<style scoped lang="scss">
:deep(.el-table__body-wrapper) {
  .el-table__row {
    height: auto;
    min-height: 48px;

    .el-table__cell {
      padding: 12px 0;

      .cell {
        line-height: 1.5;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }
}
</style>

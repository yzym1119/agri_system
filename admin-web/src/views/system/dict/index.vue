<template>
  <div class="dict-container">
    <el-card>
      <el-row :gutter="20">
        <!-- 左侧：字典类型列表 -->
        <el-col :span="6">
          <div class="dict-type-list">
            <div class="dict-type-header">
              <h3>字典类型</h3>
            </div>
            <el-scrollbar height="calc(100vh - 200px)">
              <el-menu
                :default-active="activeTypeCode"
                @select="handleTypeSelect"
                class="dict-type-menu"
              >
                <el-menu-item
                  v-for="type in dictTypes"
                  :key="type.code"
                  :index="type.code"
                >
                  <span>{{ type.name }}</span>
                  <el-tag v-if="type.remark" size="small" type="info" class="ml-2">
                    {{ type.remark }}
                  </el-tag>
                </el-menu-item>
              </el-menu>
            </el-scrollbar>
          </div>
        </el-col>

        <!-- 右侧：字典数据列表 -->
        <el-col :span="18">
          <div class="dict-data-list">
            <div class="dict-data-header">
              <h3>{{ currentTypeName || '请选择字典类型' }}</h3>
              <el-button
                v-if="activeTypeCode"
                type="primary"
                :icon="Plus"
                @click="handleAdd"
              >
                新增
              </el-button>
            </div>

            <el-table
              v-if="activeTypeCode"
              v-loading="loading"
              :data="dictDataList"
              border
              stripe
              style="width: 100%"
            >
              <el-table-column prop="label" label="名称" width="250" />
              <el-table-column prop="sortOrder" label="排序" width="100" align="center" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="默认" width="100" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.isDefault === 1" type="warning" size="small">
                    默认
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button
                    type="primary"
                    link
                    :icon="Edit"
                    @click="handleEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    :icon="Delete"
                    @click="handleDelete(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-else description="请从左侧选择字典类型" />
          </div>
        </el-col>
      </el-row>
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
        <el-form-item label="字典类型" prop="typeCode">
          <el-input v-model="currentTypeName" disabled />
        </el-form-item>
        <el-form-item label="名称" prop="label">
          <el-input 
            v-model="formData.label" 
            placeholder="请输入名称（将自动同步到存储值）" 
            @input="handleLabelChange"
          />
        </el-form-item>
        <el-form-item label="存储值" prop="value" style="display: none;">
          <el-input v-model="formData.value" placeholder="自动同步" />
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
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否默认" prop="isDefault">
          <el-radio-group v-model="formData.isDefault">
            <el-radio :label="1">是</el-radio>
            <el-radio :label="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit, Plus} from '@element-plus/icons-vue'
import {createDictData, deleteDictData, getDictDataList, updateDictData} from '@/api/system'
import type {DictDataCreateRequest, DictDataManageVO, DictDataUpdateRequest, DictType} from '@/types/dict'

// 字典类型列表（硬编码，因为后端没有提供接口）
const dictTypes: DictType[] = [
  { code: 'product_unit', name: '商品单位', remark: '商品包装单位' },
  { code: 'dosage_form', name: '农药剂型', remark: '农药剂型选项' },
  { code: 'product_manufacturer', name: '生产厂家', remark: '预置常见厂家' },
  { code: 'target_village', name: '常用村镇', remark: '地址辅助村镇' },
  { code: 'logistics_company', name: '物流公司', remark: '物流/配送公司' },
  { code: 'refund_reason', name: '售后原因', remark: '售后原因列表' }
]

// 当前选中的字典类型编码
const activeTypeCode = ref<string>('')
// 字典数据列表
const dictDataList = ref<DictDataManageVO[]>([])
// 加载状态
const loading = ref(false)
// 对话框显示状态
const dialogVisible = ref(false)
// 提交加载状态
const submitLoading = ref(false)
// 表单引用
const formRef = ref<FormInstance>()
// 是否为编辑模式
const isEdit = ref(false)

// 当前字典类型名称
const currentTypeName = computed(() => {
  const type = dictTypes.find(t => t.code === activeTypeCode.value)
  return type?.name || ''
})

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑字典数据' : '新增字典数据'
})

// 表单数据
const formData = reactive<DictDataCreateRequest & { id?: number }>({
  typeCode: '',
  label: '',
  value: '',
  sortOrder: 0,
  status: 1,
  isDefault: 0
})

// 表单验证规则
const formRules: FormRules = {
  label: [
    { required: true, message: '请输入名称', trigger: 'blur' }
  ],
  value: [
    { required: true, message: '存储值不能为空', trigger: 'blur' }
  ],
  sortOrder: [
    { required: true, message: '请输入排序', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ],
  isDefault: [
    { required: true, message: '请选择是否默认', trigger: 'change' }
  ]
}

// 选择字典类型
const handleTypeSelect = async (typeCode: string) => {
  activeTypeCode.value = typeCode
  await loadDictData()
}

// 加载字典数据
const loadDictData = async () => {
  if (!activeTypeCode.value) return

  loading.value = true
  try {
    const data = await getDictDataList(activeTypeCode.value)
    dictDataList.value = data
  } catch (error) {
    console.error('加载字典数据失败:', error)
    ElMessage.error('加载字典数据失败')
  } finally {
    loading.value = false
  }
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  resetForm()
  formData.typeCode = activeTypeCode.value
  originalValue.value = ''
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: DictDataManageVO) => {
  isEdit.value = true
  resetForm()
  formData.id = row.id
  formData.typeCode = activeTypeCode.value
  formData.label = row.label
  formData.value = row.value
  formData.sortOrder = row.sortOrder
  formData.status = row.status
  formData.isDefault = row.isDefault
  // 记录初始值，用于判断是否被手动修改
  originalValue.value = row.value
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row: DictDataManageVO) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除字典数据"${row.label}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteDictData(row.id, activeTypeCode.value)
    ElMessage.success('删除成功')
    await loadDictData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
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
        const updateData: DictDataUpdateRequest = {
          id: formData.id!,
          typeCode: formData.typeCode,
          label: formData.label,
          value: formData.value,
          sortOrder: formData.sortOrder!,
          status: formData.status!,
          isDefault: formData.isDefault!
        }
        await updateDictData(updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: DictDataCreateRequest = {
          typeCode: formData.typeCode,
          label: formData.label,
          value: formData.value,
          sortOrder: formData.sortOrder,
          status: formData.status,
          isDefault: formData.isDefault
        }
        await createDictData(createData)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      await loadDictData()
    } catch (error) {
      console.error('提交失败:', error)
      // 错误信息已在 request.ts 拦截器中处理
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.id = undefined
  formData.typeCode = ''
  formData.label = ''
  formData.value = ''
  formData.sortOrder = 0
  formData.status = 1
  formData.isDefault = 0
  originalValue.value = ''
  formRef.value?.resetFields()
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

// 记录初始存储值，用于判断是否被手动修改过
const originalValue = ref<string>('')

// 监听显示名称变化，自动同步到存储值
const handleLabelChange = (value: string) => {
  // 如果存储值等于原始值（未被手动修改），则自动同步
  if (formData.value === originalValue.value) {
    formData.value = value
    originalValue.value = value
  }
}

// 监听表单数据变化，确保新增时自动同步
watch(() => formData.label, (newLabel) => {
  // 如果存储值等于原始值（未被手动修改），则自动同步
  if (newLabel && formData.value === originalValue.value) {
    formData.value = newLabel
    originalValue.value = newLabel
  }
})

// 初始化：默认选择第一个字典类型
onMounted(() => {
  if (dictTypes.length > 0) {
    activeTypeCode.value = dictTypes[0].code
    loadDictData()
  }
})
</script>

<style scoped lang="scss">
.dict-container {
  padding: 20px;
}

.dict-type-list {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;

  .dict-type-header {
    padding: 15px;
    border-bottom: 1px solid #e4e7ed;
    background: #f5f7fa;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 500;
    }
  }

  .dict-type-menu {
    border: none;

    :deep(.el-menu-item) {
      height: 50px;
      line-height: 50px;
      display: flex;
      align-items: center;
      justify-content: space-between;

      .ml-2 {
        margin-left: 8px;
      }
    }
  }
}

.dict-data-list {
  .dict-data-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 500;
    }
  }
}
</style>


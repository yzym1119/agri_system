<template>
  <el-dialog
    v-model="visible"
    :title="`价格策略 - ${productName || ''}`"
    width="900px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-loading="loading">
      <!-- 价格规则列表 -->
      <el-table :data="priceRuleList" border style="margin-bottom: 20px">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="storeName" label="门店" width="120">
          <template #default="{ row }">
            <span v-if="row.storeId">
              {{
                row.storeName ||
                  storeOptions.find((s) => s.id === row.storeId)?.name ||
                  `门店ID ${row.storeId}`
              }}
            </span>
            <span v-else style="color: #909399">全局</span>
          </template>
        </el-table-column>
        <el-table-column prop="vipLevel" label="适用对象" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.vipLevel" type="warning" size="small">VIP{{ row.vipLevel }}</el-tag>
            <el-tag v-else type="info" size="small">所有客户</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="120" align="right">
          <template #default="{ row }">¥{{ row.price.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="有效期" width="180">
          <template #default="{ row }">
            <span v-if="row.effectiveDate && row.expiryDate">
              {{ formatDateRange(row.effectiveDate, row.expiryDate) }}
            </span>
            <span v-else style="color: #909399">永久有效</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 新增/编辑表单 -->
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
        style="margin-top: 20px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="门店" prop="storeId">
              <el-select
                v-model="formData.storeId"
                placeholder="请选择门店（留空为全局）"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="store in storeOptions"
                  :key="store.id"
                  :label="store.name"
                  :value="store.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="VIP等级" prop="vipLevel">
              <el-select
                v-model="formData.vipLevel"
                placeholder="请选择VIP等级（可留空表示所有客户）"
                clearable
                style="width: 100%"
              >
                <el-option label="VIP1" :value="1" />
                <el-option label="VIP2" :value="2" />
                <el-option label="VIP3" :value="3" />
                <el-option label="VIP4" :value="4" />
                <el-option label="VIP5" :value="5" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="priceLabel" prop="price">
              <el-input-number
                v-model="formData.price"
                :min="0"
                :precision="2"
                :step="0.01"
                style="width: 100%"
                placeholder="请输入价格"
              >
                <template #prefix>¥</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期" prop="validTime">
              <el-date-picker
                v-model="validTimeRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                @change="handleValidTimeChange"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {ElMessage, ElMessageBox, type FormInstance, type FormRules} from 'element-plus'
import {Delete, Edit} from '@element-plus/icons-vue'
import {
  createProductPriceRule,
  deleteProductPriceRule,
  getProductPriceRules,
  updateProductPriceRule
} from '@/api/product'
import {getAllStores} from '@/api/store'
import type {ProductPriceRuleCreateRequest, ProductPriceRuleUpdateRequest, ProductPriceRuleVO} from '@/types/product'
import type {StoreDetailVO} from '@/types/store'

interface Props {
  modelValue: boolean
  productId: number
  productName?: string
}

const props = withDefaults(defineProps<Props>(), {
  productName: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'success': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const submitLoading = ref(false)
const priceRuleList = ref<ProductPriceRuleVO[]>([])
const storeOptions = ref<StoreDetailVO[]>([])
const formRef = ref<FormInstance>()

const formData = reactive<Partial<ProductPriceRuleCreateRequest>>({
  storeId: null,
  customerType: null,
  vipLevel: null,
  priceType: 1,
  price: 0,
  effectiveDate: undefined,
  expiryDate: undefined
})

const validTimeRange = ref<[string, string] | null>(null)
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  price: [
    { required: true, message: '请输入价格', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '价格必须大于0', trigger: 'blur' }
  ]
}

const priceLabel = computed(() => {
  if (formData.vipLevel) return '等级特价'
  return '门店零售价'
})

// 监听弹窗打开
watch(visible, (val) => {
  if (val) {
    loadPriceRules()
    loadStores()
    resetForm()
  }
})

// 加载价格规则列表
const loadPriceRules = async () => {
  if (!props.productId) return
  loading.value = true
  try {
    priceRuleList.value = await getProductPriceRules(props.productId)
  } catch (error: any) {
    ElMessage.error('加载价格规则失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 加载门店列表
const loadStores = async () => {
  try {
    storeOptions.value = await getAllStores()
  } catch (error: any) {
    console.error('加载门店列表失败:', error)
  }
}

// 重置表单
const resetForm = () => {
  editingId.value = null
  formData.storeId = null
  formData.vipLevel = null
  formData.priceType = 1
  formData.price = 0
  formData.effectiveDate = undefined
  formData.expiryDate = undefined
  validTimeRange.value = null
  formRef.value?.clearValidate()
}

// 编辑
const handleEdit = (row: ProductPriceRuleVO) => {
  editingId.value = row.id
  formData.storeId = row.storeId ?? null
  formData.vipLevel = row.vipLevel ?? null
  formData.price = row.price
  if (row.effectiveDate && row.expiryDate) {
    validTimeRange.value = [row.effectiveDate, row.expiryDate]
    formData.effectiveDate = row.effectiveDate
    formData.expiryDate = row.expiryDate
  } else {
    validTimeRange.value = null
    formData.effectiveDate = undefined
    formData.expiryDate = undefined
  }
}

// 删除
const handleDelete = async (row: ProductPriceRuleVO) => {
  try {
    await ElMessageBox.confirm('确定要删除该价格规则吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteProductPriceRule(row.id)
    ElMessage.success('删除成功')
    loadPriceRules()
    emit('success')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }
}

// 有效期变化
const handleValidTimeChange = (val: [string, string] | null) => {
  if (val && val.length === 2) {
    formData.effectiveDate = val[0]
    formData.expiryDate = val[1]
  } else {
    formData.effectiveDate = undefined
    formData.expiryDate = undefined
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 基础校验：门店与等级不能同时为空（否则规则无意义）
    if (!formData.storeId && !formData.vipLevel) {
      ElMessage.error('请至少选择门店或VIP等级')
      return
    }

    // 校验同一维度不重复（门店 + VIP等级）
    const duplicate = priceRuleList.value.find(
      (rule) =>
        rule.id !== editingId.value &&
        rule.storeId === (formData.storeId ?? null) &&
        rule.vipLevel === (formData.vipLevel ?? null)
    )
    if (duplicate) {
      ElMessage.warning('该维度已存在价格规则，请勿重复添加')
      return
    }

    submitLoading.value = true
    try {
      const isVipLevel = !!formData.vipLevel
      const customerType = isVipLevel ? 2 : 1
      const priceType = isVipLevel ? 2 : 1

      if (editingId.value) {
        // 更新
        const updateData: ProductPriceRuleUpdateRequest = {
          id: editingId.value,
          price: formData.price!,
          effectiveDate: formData.effectiveDate,
          expiryDate: formData.expiryDate
        }
        await updateProductPriceRule(editingId.value, updateData)
        ElMessage.success('更新成功')
      } else {
        // 新增
        const createData: ProductPriceRuleCreateRequest = {
          productId: props.productId,
          storeId: formData.storeId ?? null,
          customerType,
          vipLevel: formData.vipLevel ?? null,
          priceType,
          price: formData.price!,
          effectiveDate: formData.effectiveDate,
          expiryDate: formData.expiryDate
        }
        await createProductPriceRule(createData)
        ElMessage.success('创建成功')
      }
      resetForm()
      loadPriceRules()
      emit('success')
    } catch (error: any) {
      ElMessage.error('操作失败：' + (error.message || '未知错误'))
    } finally {
      submitLoading.value = false
    }
  })
}

// 关闭弹窗
const handleClose = () => {
  visible.value = false
  resetForm()
}

// 格式化日期范围
const formatDateRange = (start: string, end: string) => {
  return `${start} 至 ${end}`
}

// 获取价格类型名称
const getPriceTypeName = (type: number) => {
  const map: Record<number, string> = {
    1: '建议零售价',
    2: 'VIP价',
    3: '最低限价'
  }
  return map[type] || '未知'
}

// 获取价格类型标签类型
const getPriceTypeTagType = (type: number) => {
  const map: Record<number, string> = {
    1: 'success',
    2: 'warning',
    3: 'danger'
  }
  return map[type] || ''
}
</script>

<style scoped lang="scss">
:deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>


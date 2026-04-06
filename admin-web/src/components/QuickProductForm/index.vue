<template>
  <el-dialog v-model="visible" title="快捷新建商品" width="700px" :close-on-click-modal="false" @close="handleClose">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="商品分类" prop="categoryId">
            <el-tree-select
              v-model="formData.categoryId"
              :data="categoryTreeOptions"
              :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true }"
              placeholder="请选择分类（必填）"
              check-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="商品名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入商品名称" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="规格" prop="specification">
            <el-input v-model="formData.specification" placeholder="如：500g/袋（可选）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="生产厂家" prop="manufacturer">
            <el-select
              v-model="formData.manufacturer"
              placeholder="请选择生产厂家"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="item in manufacturerOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="主单位" prop="mainUnit">
            <el-select v-model="formData.mainUnit" placeholder="请选择主单位" filterable style="width: 100%">
              <el-option v-for="item in unitOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="零售价" prop="suggestedRetailPrice">
            <el-input-number
              v-model="formData.suggestedRetailPrice"
              :min="0"
              :precision="2"
              placeholder="请输入零售价"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref, watch} from 'vue'
import {createProduct, getCategoryTree, getProductDetail} from '@/api/product'
import {getDictDataByCode} from '@/api/system'
import type {CategoryTreeVO, ProductCreateRequest, ProductDetailVO} from '@/types/product'
import type {DictDataVO} from '@/types/dict'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'success': [product: ProductDetailVO]
}>()

const visible = ref(false)
const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive<Partial<ProductCreateRequest>>({
  categoryId: undefined,
  name: '',
  specification: '',
  manufacturer: '',
  mainUnit: '',
  suggestedRetailPrice: 0
})

const formRules: FormRules = {
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  mainUnit: [{ required: true, message: '请选择主单位', trigger: 'change' }],
  suggestedRetailPrice: [{ required: true, message: '请输入零售价', trigger: 'blur' }]
}

const categoryTreeOptions = ref<CategoryTreeVO[]>([])
const manufacturerOptions = ref<DictDataVO[]>([])

const unitOptions = [
  { label: '袋', value: '袋' },
  { label: '瓶', value: '瓶' },
  { label: '箱', value: '箱' },
  { label: '桶', value: '桶' },
  { label: '盒', value: '盒' },
  { label: '包', value: '包' },
  { label: '件', value: '件' },
  { label: '个', value: '个' },
  { label: '支', value: '支' },
  { label: 'kg', value: 'kg' },
  { label: 'g', value: 'g' },
  { label: 'L', value: 'L' },
  { label: 'ml', value: 'ml' }
]

// 加载分类树
const loadCategoryTree = async () => {
  try {
    categoryTreeOptions.value = await getCategoryTree()
  } catch (error) {
    console.error('加载分类树失败:', error)
  }
}

// 加载厂家字典
const loadManufacturerOptions = async () => {
  try {
    manufacturerOptions.value = await getDictDataByCode('product_manufacturer')
  } catch (error) {
    console.error('加载厂家字典失败:', error)
    // 如果字典不存在，使用空数组
    manufacturerOptions.value = []
  }
}

watch(
  () => props.modelValue,
  val => {
    visible.value = val
    if (val) {
      resetForm()
      loadCategoryTree()
      loadManufacturerOptions()
    }
  }
)

watch(visible, val => {
  emit('update:modelValue', val)
})

onMounted(() => {
  loadCategoryTree()
  loadManufacturerOptions()
})

const resetForm = () => {
  formData.categoryId = undefined
  formData.name = ''
  formData.specification = ''
  formData.manufacturer = ''
  formData.mainUnit = ''
  formData.suggestedRetailPrice = 0
  formRef.value?.clearValidate()
}

const handleClose = () => {
  visible.value = false
}

const handleSubmit = () => {
  formRef.value?.validate(async valid => {
    if (!valid) return
    
    loading.value = true
    try {
      // 处理分类ID（如果是数组，取最后一个）
      let categoryId: number | undefined
      if (Array.isArray(formData.categoryId)) {
        categoryId = formData.categoryId.length > 0 ? formData.categoryId[formData.categoryId.length - 1] : undefined
      } else {
        categoryId = formData.categoryId
      }
      
      const result = await createProduct({
        categoryId,
        name: formData.name!,
        specification: formData.specification,
        manufacturer: formData.manufacturer,
        mainUnit: formData.mainUnit!,
        suggestedRetailPrice: formData.suggestedRetailPrice!
      })
      
      // 调试日志
      if (import.meta.env.DEV) {
        console.log('创建商品返回结果:', result, '类型:', typeof result)
      }
      
      // 取返回的ID
      // 注意：request.ts 的响应拦截器已经提取了 data，所以 result 应该是 { id: number }
      const newId = result?.id
      
      if (!newId || isNaN(newId)) {
        console.error('无法从返回结果中提取商品ID，返回结果:', result)
        ElMessage.warning('创建成功，但未获取到商品ID，请刷新列表查看')
        emit('success', {} as ProductDetailVO)
      } else {
        if (import.meta.env.DEV) {
          console.log('成功获取商品ID:', newId)
        }
        ElMessage.success('商品创建成功')
        // 拉取完整详情，回传给父组件
        try {
          const detail = await getProductDetail(newId)
          emit('success', detail as ProductDetailVO)
        } catch (err) {
          console.error('获取商品详情失败:', err)
          // 即使获取详情失败，也传递 ID，让父组件可以刷新列表
          emit('success', { id: newId } as ProductDetailVO)
        }
      }
      handleClose()
    } catch (error: any) {
      ElMessage.error(error.message || '创建失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
</style>

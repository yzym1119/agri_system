<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" style="max-width: 720px">
        <el-form-item label="调出门店" prop="fromStoreId">
          <el-select v-model="form.fromStoreId" placeholder="请选择" clearable filterable>
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调入门店" prop="toStoreId">
          <el-select v-model="form.toStoreId" placeholder="请选择" clearable filterable>
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品" prop="productId">
          <div class="product-selector-row">
            <el-input v-model="productDisplay" placeholder="请选择商品" disabled />
            <el-button type="primary" @click="productSelectorVisible = true">选择</el-button>
          </div>
        </el-form-item>
        <el-form-item label="调拨数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="0.01" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">提交调拨</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <ProductSelector v-model="productSelectorVisible" :selected-ids="form.productId ? [form.productId] : []" @confirm="handleSelectProduct" />
  </div>
</template>

<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import {transferInventory} from '@/api/inventory'
import {getAllStores} from '@/api/store'
import type {InventoryTransferRequest} from '@/types/inventory'
import type {StoreDetailVO} from '@/types/store'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'
import ProductSelector from '@/components/ProductSelector/index.vue'
import type {ProductDetailVO} from '@/types/product'

const formRef = ref<FormInstance>()
const loading = ref(false)
const storeOptions = ref<StoreDetailVO[]>([])
const form = reactive<InventoryTransferRequest>({
  fromStoreId: undefined as unknown as number,
  toStoreId: undefined as unknown as number,
  productId: undefined as unknown as number,
  quantity: 1
})

const rules: FormRules = {
  fromStoreId: [{ required: true, message: '请选择调出门店', trigger: 'change' }],
  toStoreId: [{ required: true, message: '请选择调入门店', trigger: 'change' }],
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
  quantity: [
    { required: true, message: '请输入调拨数量', trigger: 'blur' },
    {
      validator: (_, val, cb) => {
        if (!val || val <= 0) cb(new Error('数量必须大于0'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const productSelectorVisible = ref(false)
const selectedProduct = ref<ProductDetailVO | null>(null)
const productDisplay = computed(() => selectedProduct.value ? `${selectedProduct.value.name} (${selectedProduct.value.code})` : '')

const loadOptions = async () => {
  storeOptions.value = await getAllStores()
}

const handleSelectProduct = (products: ProductDetailVO[]) => {
  const p = products[0]
  if (!p) return
  selectedProduct.value = p
  form.productId = p.id
}

const handleSubmit = () => {
  formRef.value?.validate(async valid => {
    if (!valid) return
    loading.value = true
    try {
      await transferInventory(form)
      ElMessage.success('调拨成功')
      handleReset()
    } finally {
      loading.value = false
    }
  })
}

const handleReset = () => {
  form.fromStoreId = undefined as unknown as number
  form.toStoreId = undefined as unknown as number
  form.productId = undefined as unknown as number
  form.quantity = 1
  selectedProduct.value = null
}

loadOptions()
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}

.product-selector-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>











<template>
  <el-select
    v-model="selectedValue"
    :placeholder="placeholder"
    filterable
    allow-create
    default-first-option
    :reserve-keyword="false"
    style="width: 100%"
    @change="handleChange"
    @visible-change="handleVisibleChange"
  >
    <el-option
      v-for="item in addressOptions"
      :key="item.value"
      :label="item.label"
      :value="item.value"
    />
  </el-select>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {createDictData, getDictDataByCode} from '@/api/system'
import type {DictDataVO} from '@/types/dict'
import {ElMessage} from 'element-plus'

const props = defineProps<{
  modelValue?: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value?: string]
  'change': [value?: string]
}>()

const selectedValue = ref<string | undefined>(props.modelValue)
const addressOptions = ref<DictDataVO[]>([])
const loading = ref(false)

// 加载地址字典
const loadAddressDict = async () => {
  loading.value = true
  try {
    const data = await getDictDataByCode('target_village')
    addressOptions.value = data || []
  } catch (error) {
    console.error('加载地址字典失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理选择变化
const handleChange = async (value: string | number) => {
  const strValue = String(value)
  selectedValue.value = strValue
  
  // 如果选择的值不在字典中，说明是用户新输入的，需要添加到字典
  const exists = addressOptions.value.some(item => item.value === strValue)
  if (!exists && strValue.trim()) {
    try {
      await createDictData({
        typeCode: 'target_village',
        label: strValue,
        value: strValue,
        sortOrder: 0,
        status: 1,
        isDefault: 0
      })
      // 重新加载字典
      await loadAddressDict()
      ElMessage.success('地址已添加到字典')
    } catch (error: any) {
      console.error('添加地址到字典失败:', error)
      // 如果添加失败，仍然允许使用该值
    }
  }
  
  emit('update:modelValue', strValue)
  emit('change', strValue)
}

// 下拉框显示/隐藏时加载字典
const handleVisibleChange = (visible: boolean) => {
  if (visible && addressOptions.value.length === 0) {
    loadAddressDict()
  }
}

// 监听外部值变化
watch(
  () => props.modelValue,
  val => {
    selectedValue.value = val
  },
  { immediate: true }
)

// 初始化加载
onMounted(() => {
  loadAddressDict()
})
</script>

<style scoped lang="scss">
</style>










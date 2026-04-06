<template>
  <el-select
    v-model="selectedId"
    :placeholder="placeholder"
    filterable
    :loading="loading"
    clearable
    style="width: 100%"
    @change="handleChange"
    @visible-change="handleVisibleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="`${item.name}${item.phone ? ` (${item.phone})` : ''}`"
      :value="item.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {getStaffList} from '@/api/staff'
import type {StaffListVO, StaffQueryParams} from '@/types/staff'

const props = defineProps<{
  modelValue?: number
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value?: number]
  'change': [value?: number, staff?: StaffListVO]
}>()

const selectedId = ref<number | undefined>(props.modelValue)
const options = ref<StaffListVO[]>([])
const loading = ref(false)
const loaded = ref(false) // 标记是否已加载过

// 加载员工列表
const loadStaffList = async () => {
  if (loaded.value && options.value.length > 0) {
    return // 已加载过，不再重复加载
  }
  
  loading.value = true
  try {
    const params: StaffQueryParams = {
      pageNum: 1,
      pageSize: 200 // 加载更多员工
    }
    const response = await getStaffList(params)
    options.value = response.records || []
    loaded.value = true
  } catch (error) {
    console.error('加载员工列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 下拉框显示/隐藏时加载列表
const handleVisibleChange = (visible: boolean) => {
  if (visible && !loaded.value) {
    loadStaffList()
  }
}

// 监听外部值变化
watch(
  () => props.modelValue,
  async (val) => {
    selectedId.value = val
    // 如果外部传入了值，但选项列表中没有，需要加载员工列表
    if (val && !options.value.find(item => item.id === val)) {
      await loadStaffList()
    }
  },
  { immediate: true }
)

// 处理选择变化
const handleChange = (value?: number) => {
  emit('update:modelValue', value)
  const staff = options.value.find(item => item.id === value)
  emit('change', value, staff)
}

// 初始化时，如果有默认值，加载员工列表
onMounted(async () => {
  if (props.modelValue) {
    await loadStaffList()
  }
})
</script>

<style scoped lang="scss">
</style>


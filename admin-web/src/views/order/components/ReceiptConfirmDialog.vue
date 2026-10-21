<template>
  <el-dialog :model-value="modelValue" title="核对商品并确认签收" width="min(680px, 94vw)" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-else-if="detail">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="订单号"><span class="order-number">{{ detail.orderNo }}</span></el-descriptions-item>
        <el-descriptions-item label="收货人">{{ detail.receiverName || '待核对' }} · {{ detail.receiverPhone || '电话待核对' }}</el-descriptions-item>
        <el-descriptions-item label="送货地址">{{ detail.receiverAddress || '地址待核对' }}</el-descriptions-item>
      </el-descriptions>
      <h3>本次签收商品</h3>
      <p class="explanation">数量已扣除原批次退货，按原库存记账单位展示。请确认这些商品已交给收货人。</p>
      <div v-for="(item, index) in detail.deliveryItems" :key="`${item.orderItemId}-${index}`" class="delivery-item">
        <span>{{ item.productName }}</span><strong>{{ formatQuantity(item.quantity) }} {{ item.unit || '单位待核对' }}</strong>
      </div>
      <el-alert v-if="!canConfirm" :title="detail.delivery?.deliveryStatus === 4 ? '该订单已签收，无需再次确认' : '当前不能签收，请重新读取并核对配送状态、售后和剩余商品'" type="warning" :closable="false" show-icon />
      <p class="explanation">确认只记录交货，不会再次扣库存或收款。信息发生变化时需要重新核对。</p>
    </template>
    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button :disabled="loading || submitting" @click="reload">重新读取</el-button>
      <el-button type="primary" :disabled="!canConfirm" :loading="submitting" @click="submit">确认已交给收货人</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import {computed, ref, watch, onUnmounted} from 'vue'
import {ElMessage} from 'element-plus'
import {getOrderDetail, confirmReceipt} from '@/api/order'
import {useUserStore} from '@/store/user'
import {usePermissionStore} from '@/store/permission'
import {hasPermission} from '@/utils/permission'
import type {OrderDetail} from '@/types/order'
const props = defineProps<{modelValue: boolean; orderId: number | null}>()
const emit = defineEmits(['update:modelValue', 'success'])
const user = useUserStore()
const permission = usePermissionStore()
const detail = ref<OrderDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
let generation = 0
let sessionChanged = false
const formatQuantity = (value: number) => String(Number(value))
const canConfirm = computed(() => props.modelValue && !sessionChanged && !loading.value && !submitting.value && !error.value
  && hasPermission('order:ship', permission.permissions) && detail.value?.id === props.orderId
  && detail.value?.canConfirmReceipt === true && !!detail.value?.deliveryConfirmationKey
  && !!detail.value?.deliveryItems?.length && detail.value.deliveryItems.every(item => Number.isFinite(Number(item.quantity)) && Number(item.quantity) > 0 && !!item.unit?.trim()))
const reload = async () => {
  const turn = ++generation, id = props.orderId, staff = user.userInfo?.id
  detail.value = null; error.value = ''; loading.value = false
  if (!props.modelValue || !id || sessionChanged) return
  loading.value = true
  const current = () => turn === generation && props.modelValue && id === props.orderId && staff === user.userInfo?.id && !sessionChanged
  try {
    const result = await getOrderDetail(id)
    if (!current()) return
    if (result.id !== id) throw Error('订单信息不匹配，请重新读取')
    detail.value = result
  } catch (failure: any) {
    if (current()) error.value = failure?.message || '商品清单读取失败，请重新读取'
  } finally { if (current()) loading.value = false }
}
const submit = async () => {
  if (!canConfirm.value || !detail.value?.deliveryConfirmationKey) return
  const turn = generation, id = props.orderId!, key = detail.value.deliveryConfirmationKey, staff = user.userInfo?.id
  submitting.value = true
  const current = () => turn === generation && props.modelValue && id === props.orderId && staff === user.userInfo?.id && !sessionChanged
  try {
    await confirmReceipt(id, key)
    if (!current()) return
    ElMessage.success('已确认签收'); emit('success'); emit('update:modelValue', false)
  } catch (failure: any) {
    if (current()) error.value = failure?.message || '签收结果未确认，请重新读取原订单状态'
  } finally { if (current()) submitting.value = false }
}
watch(() => [props.modelValue, props.orderId], () => { submitting.value = false; reload() }, {immediate: true})
watch(() => user.userInfo?.id, () => { sessionChanged = true; ++generation; detail.value = null; loading.value = false; submitting.value = false; emit('update:modelValue', false) })
onUnmounted(() => { ++generation })
</script>
<style scoped>
.order-number { overflow-wrap: anywhere; }
h3 { font-size: 15px; margin: 20px 0 8px; }
.explanation { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; }
.delivery-item { display: flex; justify-content: space-between; gap: 16px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.delivery-item > span { min-width: 0; overflow-wrap: anywhere; }
.delivery-item strong { flex-shrink: 0; }
</style>

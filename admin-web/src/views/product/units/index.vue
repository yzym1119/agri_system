<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>商品单位与包装换算</h1>
        <p class="page-subtitle">
          单袋销售只需一个单位；整箱进货时再添加“1箱＝12袋”这样的包装关系。不需要反复设置基准。
        </p>
      </div>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="商品名称 / 编码"
            clearable
            style="width: 240px"
            @keyup.enter="search"
          />
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        </div>
      </template>

      <el-table :data="products" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="code" label="商品编码" width="150" />
        <el-table-column prop="name" label="商品名称" min-width="200" />
        <el-table-column label="常用库存单位" width="130">
          <template #default="{ row }"><div>{{ row.mainUnit || '-' }}</div><div class="unit-text">记账：{{ row.baseUnitName || '待核对' }}</div></template>
        </el-table-column>
        <el-table-column label="条码" width="180">
          <template #default="{ row }">{{ row.barcode || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openUnits(row)">{{ canManageUnits ? '查看 / 维护' : '查看单位' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && products.length === 0" description="暂无商品" />
    </el-card>

    <!-- 单位设置弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="`单位设置 - ${currentProduct?.name || ''}`"
      width="min(1180px, 96vw)"
      @closed="closeUnits"
      destroy-on-close
      :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"
    >
      <div v-loading="unitsLoading">
        <div class="unit-hint">
          记账单位用于统一记录库存和成本。例如按“瓶”记账，1箱＝12瓶；销售和采购可分别使用瓶或箱。已有单位名称与换算已锁定，避免历史库存账改变。
        </div>

        <el-alert v-if="unitsError" :title="unitsError" type="error" :closable="false" />
        <el-checkbox v-model="showAdvanced" :disabled="saving" class="advanced-toggle">显示库存、调拨与条码设置</el-checkbox>
        <el-table :data="unitRows" border size="small" style="width: 100%">
          <el-table-column label="单位名称" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.unitName" size="small" :disabled="row.readOnly || !!row.id || !canManageUnits || saving" placeholder="如 箱" />
            </template>
          </el-table-column>
          <el-table-column label="每包装内数量" width="130" align="center">
            <template #default="{ row }">
              <span v-if="row.readOnly || !!row.id || row.isBaseUnit === 1 || !canManageUnits">{{ formatQuantity(row.baseQuantityPerUnit) }}</span>
              <el-input-number v-else
                v-model="row.baseQuantityPerUnit"
                size="small"
                :min="0.00000001"
                :precision="6"
                :controls="false"
                :disabled="row.readOnly || !!row.id || row.isBaseUnit === 1 || !canManageUnits || saving"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="直接换算" min-width="180">
            <template #default="{ row }">
              <span v-if="row.isBaseUnit === 1" class="unit-text">库存记账单位</span>
              <span v-else>
                1 {{ row.unitName || '?' }} = {{ row.baseQuantityPerUnit }} {{ baseUnitName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="记账单位" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.isBaseUnit === 1" class="unit-text">固定</span>
              <el-button
                v-else-if="!hasPersistedBase"
                type="primary"
                link
                size="small"
                :disabled="row.readOnly || !canManageUnits || saving"
                @click="setBaseUnit(row)"
              >用作记账单位</el-button>
            </template>
          </el-table-column>
          <el-table-column label="采购" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowPurchase === 1" :disabled="row.readOnly || !canManageUnits || saving" @change="(v: any) => row.allowPurchase = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column label="销售" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowSale === 1" :disabled="row.readOnly || !canManageUnits || saving" @change="(v: any) => row.allowSale = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column v-if="showAdvanced" label="库存" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowInventory === 1" :disabled="row.readOnly || !canManageUnits || saving" @change="(v: any) => row.allowInventory = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column v-if="showAdvanced" label="调拨" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowTransfer === 1" :disabled="row.readOnly || !canManageUnits || saving" @change="(v: any) => row.allowTransfer = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column v-if="showAdvanced" label="条码" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.barcode" size="small" :disabled="row.readOnly || !canManageUnits || saving" placeholder="可扫描" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.readOnly" class="unit-text" :title="row.projectionStatus || ''">历史单位</span>
              <el-button v-else-if="row.isBaseUnit !== 1 && row.unitName !== currentProduct?.mainUnit" type="danger" link size="small" :disabled="!canManageUnits" @click="removeRow(row)">{{ row.id ? '停用' : '移除' }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-button v-if="canManageUnits" class="add-row" :disabled="unitsLoading || !!unitsError || saving" size="small" @click="addRow">+ 新增单位</el-button>
      </div>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button v-if="canManageUnits" type="primary" :disabled="unitsLoading || !!unitsError || saving" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {usePermissionStore} from '@/store/permission'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductList, getProductUnits, replaceProductUnits } from '@/api/product'
import { formatQuantity } from '@/utils/number'
import type { ProductDetailVO, ProductUnitUpsertCommand } from '@/types/product'

const permissionStore = usePermissionStore()
const canManageUnits = computed(() => permissionStore.permissions.includes('*') || permissionStore.permissions.includes('product:unit:create'))
const showAdvanced = ref(false)
const hasPersistedBase = ref(false)
const unitsError = ref('')
let unitsVersion = 0
const closeUnits = () => { ++unitsVersion; saving.value = false; unitsLoading.value = false; unitRows.value = [] }
const keyword = ref('')
const loading = ref(false)
const products = ref<ProductDetailVO[]>([])

const dialogVisible = ref(false)
const unitsLoading = ref(false)
const saving = ref(false)
const currentProduct = ref<ProductDetailVO | null>(null)
const unitRows = ref<(ProductUnitUpsertCommand & { readOnly?: boolean })[]>([])

const baseUnitName = computed(
  () => unitRows.value.find(row => row.isBaseUnit === 1)?.unitName || '基准单位'
)

const search = async () => {
  loading.value = true
  try {
    const res = await getProductList({
      keyword: keyword.value || undefined,
      pageNum: 1,
      pageSize: 20
    })
    products.value = res.records || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载商品失败')
    products.value = []
  } finally {
    loading.value = false
  }
}

const openUnits = async (product: ProductDetailVO) => {
  const version = ++unitsVersion
  saving.value = false
  unitsError.value = ''
  showAdvanced.value = false
  unitRows.value = []
  hasPersistedBase.value = false
  currentProduct.value = product
  dialogVisible.value = true
  unitsLoading.value = true
  try {
    const units = await getProductUnits(product.id)
    if (version !== unitsVersion || !dialogVisible.value || currentProduct.value?.id !== product.id) return
    hasPersistedBase.value = (units || []).some(unit => !!unit.id && unit.isBaseUnit === 1)
    unitRows.value = (units || []).map(unit => ({
      id: unit.id ?? null,
      unitName: unit.unitName,
      baseQuantityPerUnit: Number(unit.baseQuantityPerUnit ?? 1),
      isBaseUnit: unit.isBaseUnit ?? 0,
      allowPurchase: unit.allowPurchase ?? 1,
      allowSale: unit.allowSale ?? 1,
      allowInventory: unit.allowInventory ?? 1,
      allowTransfer: unit.allowTransfer ?? 1,
      barcode: unit.barcode ?? '',
      status: unit.status ?? 1,
      readOnly: unit.readOnly === true,
      projectionStatus: unit.projectionStatus
    } as any))
    if (unitRows.value.length === 0) {
      // 无单位记录时给出基准单位占位行，便于直接维护
      unitRows.value = [{id: null, unitName: product.mainUnit || '', isBaseUnit: 1, baseQuantityPerUnit: 1, status: 1, allowPurchase: 1, allowSale: 1, allowInventory: 1, allowTransfer: 1}]
    }
  } catch (e: any) {
    if (version !== unitsVersion) return
    unitsError.value = e?.message || '加载单位失败，请重新打开后重试'
    ElMessage.error(unitsError.value)
    unitRows.value = []
  } finally {
    if (version === unitsVersion) unitsLoading.value = false
  }
}

const setBaseUnit = (row: any) => {
  if (!canManageUnits.value || hasPersistedBase.value || unitRows.value.some(item => !!item.id && item.isBaseUnit === 1)) return
  unitRows.value.forEach(item => {
    item.isBaseUnit = item === row ? 1 : 0
  })
  row.baseQuantityPerUnit = 1
}

const addRow = () => {
  if (!canManageUnits.value) return
  unitRows.value.push({
    id: null,
    unitName: '',
    baseQuantityPerUnit: 1,
    isBaseUnit: unitRows.value.length === 0 ? 1 : 0,
    allowPurchase: 1,
    allowSale: 1,
    allowInventory: 1,
    allowTransfer: 1,
    barcode: '',
    status: 1
  })
}

const removeRow = (row: any) => {
  if (!canManageUnits.value || row.readOnly || row.isBaseUnit === 1 || row.unitName === currentProduct.value?.mainUnit) return
  unitRows.value = unitRows.value.filter(item => item !== row)
  if (unitRows.value.length > 0 && !unitRows.value.some(item => item.isBaseUnit === 1)) {
    unitRows.value[0].isBaseUnit = 1
    unitRows.value[0].baseQuantityPerUnit = 1
  }
}

const save = async () => {
  if (!currentProduct.value || !canManageUnits.value || unitsLoading.value || saving.value || !!unitsError.value) return
  const rows = unitRows.value
  if (rows.length === 0) {
    ElMessage.warning('至少保留一个单位')
    return
  }
  if (rows.some(row => !String(row.unitName || '').trim())) {
    ElMessage.warning('单位名称不能为空')
    return
  }
  if (rows.filter(row => row.isBaseUnit === 1).length !== 1) {
    ElMessage.warning('必须且只能有一个基准单位')
    return
  }
  if (rows.some(row => !(Number(row.baseQuantityPerUnit) > 0))) {
    ElMessage.warning('换算系数必须大于 0')
    return
  }

  const version = unitsVersion, productId = currentProduct.value.id
  saving.value = true
  try {
    await replaceProductUnits(
      productId,
      rows.map(row => ({
        id: row.id ?? null,
        unitName: String(row.unitName).trim(),
        baseQuantityPerUnit: Number(row.baseQuantityPerUnit),
        isBaseUnit: row.isBaseUnit,
        allowPurchase: row.allowPurchase ?? 1,
        allowSale: row.allowSale ?? 1,
        allowInventory: row.allowInventory ?? 1,
        allowTransfer: row.allowTransfer ?? 1,
        barcode: row.barcode || null,
        status: row.status ?? 1
      }))
    )
    if (version !== unitsVersion || currentProduct.value?.id !== productId || !dialogVisible.value) return
    ElMessage.success('单位设置已保存')
    dialogVisible.value = false
  } catch (e: any) {
    if (version === unitsVersion && currentProduct.value?.id === productId && dialogVisible.value) ElMessage.error(e?.message || '保存失败')
  } finally {
    if (version === unitsVersion) saving.value = false
  }
}

search()
</script>

<style scoped>
.page-subtitle {
  margin: 6px 0 0;
  color: #626f7e;
  font-size: 13px;
}
.unit-hint {
  margin-bottom: 10px;
  color: #626f7e;
  font-size: 13px;
}
.add-row {
  margin-top: 10px;
}
.unit-text {
  color: #626f7e;
  font-size: 12px;
}
</style>

<style scoped>
.advanced-toggle { margin-bottom: 12px; }
.unit-hint { line-height: 1.7; color: #4b5563; }
</style>

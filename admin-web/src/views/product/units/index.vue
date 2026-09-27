<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>单位设置</h1>
        <p class="page-subtitle">
          直接换算：1 个采购/销售单位 = N 个基准单位。仅维护一跳直接换算，不暴露多级换算链。
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
        <el-table-column label="基准单位" width="120">
          <template #default="{ row }">{{ row.mainUnit || '-' }}</template>
        </el-table-column>
        <el-table-column label="条码" width="180">
          <template #default="{ row }">{{ row.barcode || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openUnits(row)">设置单位</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && products.length === 0" description="暂无商品" />
    </el-card>

    <!-- 单位设置弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="`单位设置 - ${currentProduct?.name || ''}`"
      width="880px"
      destroy-on-close
    >
      <div v-loading="unitsLoading">
        <div class="unit-hint">
          基准单位系数固定为 1；采购/销售/库存单位按直接换算填写（如 1 箱 = 12 袋）。
        </div>

        <el-table :data="unitRows" border size="small" style="width: 100%">
          <el-table-column label="单位名称" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.unitName" size="small" :disabled="row.readOnly" placeholder="如 箱" />
            </template>
          </el-table-column>
          <el-table-column label="换算系数" width="130" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.baseQuantityPerUnit"
                size="small"
                :min="0.00000001"
                :precision="6"
                :controls="false"
                :disabled="row.readOnly || row.isBaseUnit === 1"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="直接换算" min-width="180">
            <template #default="{ row }">
              <span v-if="row.isBaseUnit === 1" class="unit-text">基准单位（系数 1）</span>
              <span v-else>
                1 {{ row.unitName || '?' }} = {{ row.baseQuantityPerUnit }} {{ baseUnitName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="基准" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.isBaseUnit === 1" class="unit-text">基准</span>
              <el-button
                v-else
                type="primary"
                link
                size="small"
                :disabled="row.readOnly"
                @click="setBaseUnit(row)"
              >设为基准</el-button>
            </template>
          </el-table-column>
          <el-table-column label="采购" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowPurchase === 1" :disabled="row.readOnly" @change="(v: any) => row.allowPurchase = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column label="销售" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowSale === 1" :disabled="row.readOnly" @change="(v: any) => row.allowSale = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column label="库存" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowInventory === 1" :disabled="row.readOnly" @change="(v: any) => row.allowInventory = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column label="调拨" width="66" align="center">
            <template #default="{ row }">
              <el-checkbox :model-value="row.allowTransfer === 1" :disabled="row.readOnly" @change="(v: any) => row.allowTransfer = v ? 1 : 0" />
            </template>
          </el-table-column>
          <el-table-column label="条码" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.barcode" size="small" :disabled="row.readOnly" placeholder="可扫描" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.readOnly" class="unit-text" :title="row.projectionStatus || ''">历史单位</span>
              <el-button v-else type="danger" link size="small" @click="removeRow(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-button class="add-row" size="small" @click="addRow">+ 新增单位</el-button>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductList, getProductUnits, replaceProductUnits } from '@/api/product'
import type { ProductDetailVO, ProductUnitUpsertCommand } from '@/types/product'

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
  currentProduct.value = product
  dialogVisible.value = true
  unitsLoading.value = true
  try {
    const units = await getProductUnits(product.id)
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
      _projectionStatus: unit.projectionStatus
    } as any))
    if (unitRows.value.length === 0) {
      // 无单位记录时给出基准单位占位行，便于直接维护
      addRow()
      unitRows.value[0].unitName = product.mainUnit || ''
      unitRows.value[0].isBaseUnit = 1
      unitRows.value[0].baseQuantityPerUnit = 1
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载单位失败')
    unitRows.value = []
  } finally {
    unitsLoading.value = false
  }
}

const setBaseUnit = (row: any) => {
  unitRows.value.forEach(item => {
    item.isBaseUnit = item === row ? 1 : 0
  })
  row.baseQuantityPerUnit = 1
}

const addRow = () => {
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
  unitRows.value = unitRows.value.filter(item => item !== row)
  if (unitRows.value.length > 0 && !unitRows.value.some(item => item.isBaseUnit === 1)) {
    unitRows.value[0].isBaseUnit = 1
    unitRows.value[0].baseQuantityPerUnit = 1
  }
}

const save = async () => {
  if (!currentProduct.value) return
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

  saving.value = true
  try {
    await replaceProductUnits(
      currentProduct.value.id,
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
    ElMessage.success('单位设置已保存')
    dialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

search()
</script>

<style scoped>
.page-subtitle {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}
.unit-hint {
  margin-bottom: 10px;
  color: #909399;
  font-size: 13px;
}
.add-row {
  margin-top: 10px;
}
.unit-text {
  color: #909399;
  font-size: 12px;
}
</style>

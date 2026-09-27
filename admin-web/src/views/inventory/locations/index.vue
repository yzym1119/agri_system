<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>货位</h1>
        <p class="page-desc">门店货位清单，货位编码可在取货工作台中直接扫码使用。</p>
      </div>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-select
            v-model="storeId"
            placeholder="请选择门店"
            clearable
            style="width: 220px"
            @change="loadLocations"
          >
            <el-option
              v-for="store in stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
          <el-button type="primary" :loading="loading" @click="loadLocations">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="!storeId"
        type="info"
        :closable="false"
        show-icon
        title="请先选择门店"
        description="货位按门店隔离，选择门店后展示该门店全部货位（含停用）。"
      />

      <template v-else>
        <el-table :data="locations" v-loading="loading" stripe border style="width: 100%">
          <el-table-column prop="code" label="货位编码" width="200">
            <template #default="{ row }">
              <span class="location-code">{{ row.code }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="货位名称" min-width="200" />
          <el-table-column prop="locationType" label="货位类型" width="150">
            <template #default="{ row }">
              <el-tag :type="locationTypeTag(row.locationType)" effect="plain">
                {{ locationTypeText(row.locationType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
        </el-table>

        <el-empty
          v-if="!loading && locations.length === 0"
          description="该门店暂无货位，可联系管理员在库存基础资料中维护"
        />
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getStoreList } from '@/api/store'
import { getInventoryLocations } from '@/api/inventory'
import type { InventoryLocationVO } from '@/types/inventory'

const stores = ref<any[]>([])
const storeId = ref<number | undefined>(undefined)
const locations = ref<InventoryLocationVO[]>([])
const loading = ref(false)

const LOCATION_TYPE_TEXT: Record<string, string> = {
  SALES_FLOOR: '卖场',
  STORE_BACKROOM: '门店后仓',
  CENTRAL_WAREHOUSE: '中央仓',
  OTHER_STORE: '其他门店'
}

function locationTypeText(type?: string) {
  if (!type) return '-'
  return LOCATION_TYPE_TEXT[type] || type
}

function locationTypeTag(type?: string) {
  if (type === 'SALES_FLOOR') return 'success'
  if (type === 'STORE_BACKROOM') return 'warning'
  if (type === 'CENTRAL_WAREHOUSE') return 'primary' as any
  return 'info'
}

async function loadStores() {
  try {
    const res: any = await getStoreList({ pageNum: 1, pageSize: 100 })
    stores.value = res?.list || res?.records || res?.data?.list || res?.data?.records || []
  } catch (e) {
    console.warn('加载门店列表失败', e)
  }
}

async function loadLocations() {
  if (!storeId.value) {
    locations.value = []
    return
  }
  loading.value = true
  try {
    locations.value = await getInventoryLocations(storeId.value)
  } catch (e) {
    // 请求拦截器已提示错误信息
    console.warn('加载货位列表失败', e)
    locations.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadStores)
</script>

<style scoped>
.page-desc {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.location-code {
  font-family: monospace;
  font-weight: 600;
}
</style>

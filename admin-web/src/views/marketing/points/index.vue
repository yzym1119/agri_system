<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- Tab 1: 礼品管理 -->
        <el-tab-pane label="礼品管理" name="goods">
          <div class="tab-content">
            <div class="toolbar">
              <el-button type="primary" :icon="Plus" @click="handleAddGoods">新增礼品</el-button>
            </div>

            <el-table :data="goodsList" v-loading="goodsLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column label="图片" width="100" align="center">
                <template #default="{ row }">
                  <el-image
                    v-if="row.imageUrl"
                    :src="row.imageUrl"
                    :preview-src-list="[row.imageUrl]"
                    fit="cover"
                    style="width: 60px; height: 60px; border-radius: 4px"
                  />
                  <div v-else style="width: 60px; height: 60px; background: #f5f7fa; border-radius: 4px; display: flex; align-items: center; justify-content: center; color: #909399; font-size: 12px">
                    无图片
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="礼品名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="points" label="所需积分" width="120" align="right">
                <template #default="{ row }">{{ row.points }} 积分</template>
              </el-table-column>
              <el-table-column prop="stock" label="库存" width="100" align="right" />
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'info'">
                    {{ row.status === 1 ? '上架' : '下架' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleEditGoods(row)">编辑</el-button>
                  <el-button
                    v-if="row.status === 1"
                    link
                    type="warning"
                    @click="handleOfflineGoods(row)"
                  >下架</el-button>
                  <el-button
                    v-else
                    link
                    type="success"
                    @click="handleOnlineGoods(row)"
                  >上架</el-button>
                  <el-button link type="danger" @click="handleDeleteGoods(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 2: 兑换订单 -->
        <el-tab-pane label="兑换订单" name="orders">
          <div class="tab-content">
            <!-- 搜索栏 -->
            <div class="search-bar">
              <el-form :inline="true" :model="orderQuery">
                <el-form-item label="状态">
                  <el-select v-model="orderQuery.status" placeholder="全部" clearable style="width: 150px">
                    <el-option label="待审核" :value="10" />
                    <el-option label="备货中" :value="20" />
                    <el-option label="已拒绝" :value="30" />
                    <el-option label="待自提" :value="40" />
                    <el-option label="已完成" :value="50" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="orderLoading" :icon="Search" @click="handleOrderSearch">查询</el-button>
                  <el-button :icon="Refresh" @click="handleOrderReset">重置</el-button>
                </el-form-item>
              </el-form>
            </div>

            <el-table :data="orderList" v-loading="orderLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="exchangeNo" label="兑换单号" width="150" />
              <el-table-column label="礼品信息" min-width="200">
                <template #default="{ row }">
                  <div style="display: flex; align-items: center">
                    <el-image
                      v-if="row.goodsImage"
                      :src="row.goodsImage"
                      fit="cover"
                      style="width: 50px; height: 50px; border-radius: 4px; margin-right: 8px"
                    />
                    <div>
                      <div style="font-weight: 500">{{ row.goodsName }}</div>
                      <div style="font-size: 12px; color: #909399">
                        {{ row.points }} 积分 × {{ row.quantity }}
                      </div>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="statusDesc" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getStatusTagType(row.status)">
                    {{ row.statusDesc || getStatusName(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="pickupCode" label="取货码" width="120" align="center" />
              <el-table-column prop="receiverName" label="收货人" width="100" />
              <el-table-column prop="receiverPhone" label="联系电话" width="120" />
              <el-table-column prop="createdAt" label="兑换时间" width="170" />
              <el-table-column label="操作" width="250" fixed="right">
                <template #default="{ row }">
                  <template v-if="row.status === 10">
                    <el-button link type="success" @click="handleAudit(row, true)">通过</el-button>
                    <el-button link type="danger" @click="handleAudit(row, false)">拒绝</el-button>
                  </template>
                  <template v-else-if="row.status === 20">
                    <el-button link type="primary" @click="handleNotify(row)">通知取货</el-button>
                  </template>
                  <template v-else-if="row.status === 40">
                    <el-button link type="success" @click="handleVerify(row)">核销</el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination">
              <el-pagination
                v-model:current-page="orderQuery.pageNum"
                v-model:page-size="orderQuery.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="orderTotal"
                @current-change="handleOrderPageChange"
                @size-change="handleOrderSizeChange"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 礼品新增/编辑对话框 -->
    <el-dialog
      v-model="goodsDialogVisible"
      :title="goodsIsEdit ? '编辑积分礼品' : '新增积分礼品'"
      width="600px"
      :close-on-click-modal="false"
      @close="handleGoodsDialogClose"
    >
      <el-form ref="goodsFormRef" :model="goodsFormData" :rules="goodsFormRules" label-width="100px">
        <el-form-item label="礼品名称" prop="name">
          <el-input v-model="goodsFormData.name" placeholder="请输入礼品名称" />
        </el-form-item>
        <el-form-item label="所需积分" prop="points">
          <el-input-number
            v-model="goodsFormData.points"
            :min="1"
            :precision="0"
            placeholder="请输入所需积分"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number
            v-model="goodsFormData.stock"
            :min="0"
            :precision="0"
            placeholder="请输入库存"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="礼品图片" prop="imageUrl">
          <el-upload
            class="image-uploader"
            :action="uploadAction"
            :headers="uploadHeaders"
            name="file"
            :show-file-list="false"
            :on-success="handleImageSuccess"
            :on-error="handleImageError"
            :before-upload="beforeImageUpload"
            accept="image/*"
          >
            <el-image
              v-if="goodsFormData.imageUrl"
              :src="goodsFormData.imageUrl"
              fit="cover"
              style="width: 100px; height: 100px; border-radius: 4px"
            />
            <el-icon v-else class="image-uploader-icon"><Plus /></el-icon>
          </el-upload>
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            支持 JPG、PNG 格式，大小不超过 2MB
          </div>
        </el-form-item>
        <el-form-item label="礼品描述" prop="description">
          <el-input
            v-model="goodsFormData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入礼品描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="goodsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="goodsSubmitLoading" @click="handleGoodsSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 审核对话框 -->
    <el-dialog
      v-model="auditDialogVisible"
      :title="auditPass ? '审核通过' : '审核拒绝'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="auditFormRef" :model="auditFormData" :rules="auditFormRules" label-width="100px">
        <el-form-item label="兑换单号">
          <el-input v-model="auditFormData.exchangeNo" disabled />
        </el-form-item>
        <el-form-item v-if="!auditPass" label="拒绝原因" prop="remark">
          <el-input
            v-model="auditFormData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入拒绝原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditLoading" @click="handleAuditSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 通知取货对话框 -->
    <el-dialog
      v-model="notifyDialogVisible"
      title="通知客户取货"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="notifyFormRef" :model="notifyFormData" label-width="120px">
        <el-form-item label="兑换单号">
          <el-input v-model="notifyFormData.exchangeNo" disabled />
        </el-form-item>
        <el-form-item label="物流公司">
          <el-input v-model="notifyFormData.logisticsCompany" placeholder="选填" />
        </el-form-item>
        <el-form-item label="物流单号">
          <el-input v-model="notifyFormData.trackingNo" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="notifyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="notifyLoading" @click="handleNotifySubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 核销对话框 -->
    <el-dialog
      v-model="verifyDialogVisible"
      title="核销积分兑换单"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form ref="verifyFormRef" :model="verifyFormData" :rules="verifyFormRules" label-width="100px">
        <el-form-item label="取货码" prop="pickupCode">
          <el-input
            v-model="verifyFormData.pickupCode"
            placeholder="请输入6位取货码"
            maxlength="6"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="verifyLoading" @click="handleVerifySubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {Plus, Refresh, Search} from '@element-plus/icons-vue'
import {
  auditPointsOrder,
  createPointsGoods,
  deletePointsGoods,
  getPointsGoodsList,
  getPointsOrderList,
  notifyPointsPickup,
  offlinePointsGoods,
  onlinePointsGoods,
  updatePointsGoods,
  verifyPointsOrder
} from '@/api/points'
import type {
  PointsAuditRequest,
  PointsExchangeOrderVO,
  PointsGoodsSaveRequest,
  PointsGoodsVO,
  PointsNotifyRequest,
  PointsOrderQuery,
  PointsVerifyRequest
} from '@/types/points'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'
import {POINTS_ORDER_STATUS, POINTS_ORDER_STATUS_MAP} from '@/utils/dict'
import {useUserStore} from '@/store/user'

const activeTab = ref('goods')
const userStore = useUserStore()

// 礼品管理
const goodsLoading = ref(false)
const goodsList = ref<PointsGoodsVO[]>([])
const goodsDialogVisible = ref(false)
const goodsIsEdit = ref(false)
const goodsFormRef = ref<FormInstance>()
const goodsSubmitLoading = ref(false)

const goodsFormData = reactive<{
  id?: number
  name: string
  points: number
  stock: number
  imageUrl: string
  description: string
}>({
  name: '',
  points: 0,
  stock: 0,
  imageUrl: '',
  description: ''
})

const goodsFormRules: FormRules = {
  name: [{ required: true, message: '请输入礼品名称', trigger: 'blur' }],
  points: [
    { required: true, message: '请输入所需积分', trigger: 'blur' },
    { type: 'number', min: 1, message: '所需积分必须大于0', trigger: 'blur' }
  ],
  stock: [
    { required: true, message: '请输入库存', trigger: 'blur' },
    { type: 'number', min: 0, message: '库存不能小于0', trigger: 'blur' }
  ]
}

// 图片上传
const uploadAction = computed(() => {
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  return `${baseURL}/api/admin/products/image/upload`
})

const uploadHeaders = computed(() => {
  return {
    Authorization: userStore.token || ''
  }
})

// 兑换订单
const orderLoading = ref(false)
const orderList = ref<PointsExchangeOrderVO[]>([])
const orderTotal = ref(0)
const orderQuery = reactive<PointsOrderQuery>({
  pageNum: 1,
  pageSize: 10
})

// 审核
const auditDialogVisible = ref(false)
const auditPass = ref(true)
const auditFormRef = ref<FormInstance>()
const auditLoading = ref(false)
const auditFormData = reactive<{
  orderId?: number
  exchangeNo?: string
  remark: string
}>({
  orderId: undefined,
  exchangeNo: '',
  remark: ''
})

const auditFormRules: FormRules = {
  remark: [{ required: false, message: '请输入拒绝原因', trigger: 'blur' }]
}

// 通知取货
const notifyDialogVisible = ref(false)
const notifyFormRef = ref<FormInstance>()
const notifyLoading = ref(false)
const notifyFormData = reactive<{
  orderId?: number
  exchangeNo?: string
  logisticsCompany: string
  trackingNo: string
}>({
  orderId: undefined,
  exchangeNo: '',
  logisticsCompany: '',
  trackingNo: ''
})

// 核销
const verifyDialogVisible = ref(false)
const verifyFormRef = ref<FormInstance>()
const verifyLoading = ref(false)
const verifyFormData = reactive<{
  pickupCode: string
}>({
  pickupCode: ''
})

const verifyFormRules: FormRules = {
  pickupCode: [
    { required: true, message: '请输入取货码', trigger: 'blur' },
    { min: 6, max: 6, message: '取货码必须为6位', trigger: 'blur' }
  ]
}

// 加载礼品列表
const loadGoodsList = async () => {
  goodsLoading.value = true
  try {
    goodsList.value = await getPointsGoodsList()
  } catch (error) {
    console.error('加载礼品列表失败:', error)
    goodsList.value = []
  } finally {
    goodsLoading.value = false
  }
}

// 加载订单列表
const loadOrderList = async () => {
  orderLoading.value = true
  try {
    const response = await getPointsOrderList(orderQuery)
    orderList.value = response.records || []
    orderTotal.value = response.total || 0
  } catch (error) {
    console.error('加载订单列表失败:', error)
    orderList.value = []
  } finally {
    orderLoading.value = false
  }
}

// 新增礼品
const handleAddGoods = () => {
  goodsIsEdit.value = false
  resetGoodsForm()
  goodsDialogVisible.value = true
}

// 编辑礼品
const handleEditGoods = (row: PointsGoodsVO) => {
  goodsIsEdit.value = true
  resetGoodsForm()
  goodsFormData.id = row.id
  goodsFormData.name = row.name
  goodsFormData.points = row.points
  goodsFormData.stock = row.stock
  goodsFormData.imageUrl = row.imageUrl || ''
  goodsFormData.description = row.description || ''
  goodsDialogVisible.value = true
}

// 删除礼品
const handleDeleteGoods = (row: PointsGoodsVO) => {
  ElMessageBox.confirm(`确定要删除礼品"${row.name}"吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        await deletePointsGoods(row.id)
        ElMessage.success('删除成功')
        loadGoodsList()
      } catch (error: any) {
        ElMessage.error(error.message || '删除失败')
      }
    })
    .catch(() => {})
}

// 上架
const handleOnlineGoods = async (row: PointsGoodsVO) => {
  try {
    await onlinePointsGoods(row.id)
    ElMessage.success('上架成功')
    loadGoodsList()
  } catch (error: any) {
    ElMessage.error(error.message || '上架失败')
  }
}

// 下架
const handleOfflineGoods = async (row: PointsGoodsVO) => {
  try {
    await offlinePointsGoods(row.id)
    ElMessage.success('下架成功')
    loadGoodsList()
  } catch (error: any) {
    ElMessage.error(error.message || '下架失败')
  }
}

// 图片上传
const handleImageSuccess = (response: any) => {
  console.log('图片上传响应:', response)
  let imageUrl = ''
  
  // el-upload 使用原生 XMLHttpRequest，不会经过 Axios 拦截器
  // 后端返回格式: { code: "200", message: "上传成功", data: { url: "..." } }
  
  // 1. 如果响应是字符串（JSON字符串），先解析
  if (typeof response === 'string') {
    try {
      response = JSON.parse(response)
    } catch (e) {
      console.error('解析响应失败:', e)
    }
  }
  
  // 2. 处理不同的响应格式
  if (response) {
    // 格式1: { url: "..." } (已经被处理过)
    if (response.url) {
      imageUrl = response.url
    }
    // 格式2: { code: "200", data: { url: "..." } }
    else if (response.data) {
      if (typeof response.data === 'string') {
        imageUrl = response.data
      } else if (response.data.url) {
        imageUrl = response.data.url
      } else if (response.data.data && response.data.data.url) {
        imageUrl = response.data.data.url
      }
    }
    // 格式3: 直接是 URL 字符串
    else if (typeof response === 'string' && response.startsWith('http')) {
      imageUrl = response
    }
  }
  
  if (imageUrl) {
    goodsFormData.imageUrl = imageUrl
    ElMessage.success('图片上传成功')
  } else {
    console.error('无法解析图片URL，完整响应:', JSON.stringify(response, null, 2))
    ElMessage.error('图片上传失败：无法获取图片地址，请查看控制台日志')
  }
}

const handleImageError = () => {
  ElMessage.error('图片上传失败，请重试')
}

const beforeImageUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

// 提交礼品
const handleGoodsSubmit = () => {
  goodsFormRef.value?.validate(async valid => {
    if (!valid) return
    
    goodsSubmitLoading.value = true
    try {
      const saveData: PointsGoodsSaveRequest = {
        name: goodsFormData.name,
        points: goodsFormData.points,
        stock: goodsFormData.stock,
        imageUrl: goodsFormData.imageUrl || undefined,
        description: goodsFormData.description || undefined
      }
      if (goodsIsEdit.value) {
        await updatePointsGoods(goodsFormData.id!, saveData)
        ElMessage.success('更新成功')
      } else {
        await createPointsGoods(saveData)
        ElMessage.success('创建成功')
      }
      goodsDialogVisible.value = false
      loadGoodsList()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      goodsSubmitLoading.value = false
    }
  })
}

const resetGoodsForm = () => {
  goodsFormData.id = undefined
  goodsFormData.name = ''
  goodsFormData.points = 0
  goodsFormData.stock = 0
  goodsFormData.imageUrl = ''
  goodsFormData.description = ''
  goodsFormRef.value?.clearValidate()
}

const handleGoodsDialogClose = () => {
  resetGoodsForm()
}

// 订单搜索
const handleOrderSearch = () => {
  orderQuery.pageNum = 1
  loadOrderList()
}

const handleOrderReset = () => {
  orderQuery.status = undefined
  orderQuery.pageNum = 1
  orderQuery.pageSize = 10
  loadOrderList()
}

const handleOrderPageChange = (page: number) => {
  orderQuery.pageNum = page
  loadOrderList()
}

const handleOrderSizeChange = (size: number) => {
  orderQuery.pageSize = size
  orderQuery.pageNum = 1
  loadOrderList()
}

// 审核
const handleAudit = (row: PointsExchangeOrderVO, pass: boolean) => {
  auditPass.value = pass
  auditFormData.orderId = row.id
  auditFormData.exchangeNo = row.exchangeNo
  auditFormData.remark = ''
  auditDialogVisible.value = true
}

const handleAuditSubmit = () => {
  auditFormRef.value?.validate(async valid => {
    if (!valid) return
    
    auditLoading.value = true
    try {
      const auditData: PointsAuditRequest = {
        orderId: auditFormData.orderId!,
        pass: auditPass.value,
        remark: auditFormData.remark || undefined
      }
      await auditPointsOrder(auditData)
      ElMessage.success(auditPass.value ? '审核通过' : '审核拒绝')
      auditDialogVisible.value = false
      loadOrderList()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      auditLoading.value = false
    }
  })
}

// 通知取货
const handleNotify = (row: PointsExchangeOrderVO) => {
  notifyFormData.orderId = row.id
  notifyFormData.exchangeNo = row.exchangeNo
  notifyFormData.logisticsCompany = ''
  notifyFormData.trackingNo = ''
  notifyDialogVisible.value = true
}

const handleNotifySubmit = () => {
  notifyFormRef.value?.validate(async valid => {
    if (!valid) return
    
    notifyLoading.value = true
    try {
      const notifyData: PointsNotifyRequest = {
        orderId: notifyFormData.orderId!,
        logisticsCompany: notifyFormData.logisticsCompany || undefined,
        trackingNo: notifyFormData.trackingNo || undefined
      }
      await notifyPointsPickup(notifyData)
      ElMessage.success('通知成功')
      notifyDialogVisible.value = false
      loadOrderList()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      notifyLoading.value = false
    }
  })
}

// 核销
const handleVerify = (row: PointsExchangeOrderVO) => {
  verifyFormData.pickupCode = ''
  verifyDialogVisible.value = true
}

const handleVerifySubmit = () => {
  verifyFormRef.value?.validate(async valid => {
    if (!valid) return
    
    verifyLoading.value = true
    try {
      const verifyData: PointsVerifyRequest = {
        pickupCode: verifyFormData.pickupCode
      }
      await verifyPointsOrder(verifyData)
      ElMessage.success('核销成功')
      verifyDialogVisible.value = false
      loadOrderList()
    } catch (error: any) {
      ElMessage.error(error.message || '核销失败')
    } finally {
      verifyLoading.value = false
    }
  })
}

// 状态映射
const getStatusName = (status?: number) => {
  return status ? POINTS_ORDER_STATUS_MAP[status] || '未知' : '-'
}

const getStatusTagType = (status?: number) => {
  if (!status) return 'info'
  switch (status) {
    case POINTS_ORDER_STATUS.WAIT_AUDIT:
      return 'warning'
    case POINTS_ORDER_STATUS.AUDIT_PASSED:
      return 'success'
    case POINTS_ORDER_STATUS.AUDIT_REJECTED:
      return 'danger'
    case POINTS_ORDER_STATUS.NOTIFIED:
      return 'primary'
    case POINTS_ORDER_STATUS.FINISHED:
      return 'info'
    default:
      return 'info'
  }
}

// 初始化
onMounted(() => {
  loadGoodsList()
  loadOrderList()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}

.tab-content {
  padding: 16px 0;

  .toolbar {
    margin-bottom: 16px;
  }

  .search-bar {
    margin-bottom: 16px;
  }
}

.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.image-uploader {
  :deep(.el-upload) {
    border: 1px dashed #d9d9d9;
    border-radius: 4px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: all 0.3s;

    &:hover {
      border-color: #409eff;
    }
  }

  .image-uploader-icon {
    font-size: 28px;
    color: #8c939d;
    width: 100px;
    height: 100px;
    text-align: center;
    line-height: 100px;
  }
}
</style>


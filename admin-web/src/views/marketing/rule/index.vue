<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- Tab 1: 充值奖励规则 -->
        <el-tab-pane label="充值奖励规则" name="recharge">
          <div class="tab-content">
            <el-alert
              title="规则说明"
              type="info"
              :closable="false"
              style="margin-bottom: 16px"
            >
              <template #default>
                <div>规则说明：充值门槛为单次充值金额；有效期规则为"原有效期 + 赠送时长"；等级取"当前等级"与"目标等级"之高者。</div>
                <div style="margin-top: 8px; color: #909399;">
                  提示：若需赠送多种优惠券，可创建多条相同门槛的规则。系统充值时会自动叠加执行所有满足条件的规则。
                </div>
              </template>
            </el-alert>
            <div class="toolbar">
              <el-button type="primary" :icon="Plus" @click="handleAddRechargeRule">新增规则</el-button>
            </div>

            <el-table :data="rechargeRuleList" v-loading="rechargeLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="name" label="规则名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="minRechargeAmount" label="充值门槛" width="120" align="right">
                <template #default="{ row }">¥{{ row.minRechargeAmount.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="giftSpecialFund" label="赠送专用金" width="120" align="right">
                <template #default="{ row }">
                  <span v-if="row.giftSpecialFund && row.giftSpecialFund > 0">
                    ¥{{ row.giftSpecialFund.toFixed(2) }}
                  </span>
                  <span v-else style="color: #909399">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="giftTemplateName" label="赠送优惠券" width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.giftTemplateName">
                    <span :style="{ color: row.giftTemplateStatus === 0 ? '#f56c6c' : '' }">
                      {{ row.giftTemplateName }}{{ row.giftTemplateStatus === 0 ? ' (已停用)' : '' }}
                    </span>
                    <span> × {{ row.giftTemplateCount || 1 }}</span>
                  </span>
                  <span v-else style="color: #909399">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="targetVipLevel" label="赠送VIP时长" width="150">
                <template #default="{ row }">
                  <span v-if="row.targetVipLevel">
                    VIP{{ row.targetVipLevel }} ({{ row.giftDurationDays || 365 }}天)
                  </span>
                  <span v-else style="color: #909399">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-switch
                    v-model="row.status"
                    :active-value="1"
                    :inactive-value="0"
                    @change="handleRechargeRuleStatusChange(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleEditRechargeRule(row)">编辑</el-button>
                  <el-button link type="danger" @click="handleDeleteRechargeRule(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 2: VIP 月度权益 -->
        <el-tab-pane label="VIP 月度权益" name="vip">
          <div class="tab-content">
            <el-table :data="vipRightsList" v-loading="vipLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="vipLevel" label="VIP等级" width="120" align="center">
                <template #default="{ row }">VIP{{ row.vipLevel }}</template>
              </el-table-column>
              <el-table-column label="权益内容" min-width="300">
                <template #default="{ row }">
                  <div v-if="row.status === 0" style="color: #909399">
                    未配置
                  </div>
                  <div v-else>
                    <!-- 显示该等级的所有权益（从完整列表获取） -->
                    <template v-for="(right, idx) in getVipRightsForLevel(row.vipLevel)" :key="idx">
                      <div v-if="right.benefitType === 1 && right.templateName" style="margin-bottom: 4px">
                        <el-tag 
                          :type="right.templateStatus === 0 ? 'danger' : 'success'" 
                          size="small"
                        >
                          送券
                        </el-tag>
                        <span style="margin-left: 8px">
                          {{ right.templateName }} × {{ right.templateCount || 1 }}
                          <span v-if="right.templateStatus === 0" style="color: #f56c6c; margin-left: 4px">
                            (已停用)
                          </span>
                        </span>
                      </div>
                      <div v-else-if="right.benefitType === 2 && right.benefitValue" style="margin-bottom: 4px">
                        <el-tag type="warning" size="small">送专用金</el-tag>
                        <span style="margin-left: 8px">
                          ¥{{ right.benefitValue.toFixed(2) }}
                        </span>
                      </div>
                    </template>
                    <span v-if="getVipRightsForLevel(row.vipLevel).length === 0" style="color: #909399">未配置</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'info'">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </el-tag>
                  <el-tooltip content="权益配置的启用/停用状态，配置权益后自动启用" placement="top">
                    <el-icon style="margin-left: 4px; cursor: help; color: #909399; vertical-align: middle"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleConfigVipRights(row)">配置权益</el-button>
                  <el-button
                    v-if="row.status === 1"
                    link
                    type="warning"
                    @click="handleToggleVipRightsStatus(row, 0)"
                  >
                    停用
                  </el-button>
                  <el-button
                    v-if="row.status === 0"
                    link
                    type="success"
                    @click="handleToggleVipRightsStatus(row, 1)"
                  >
                    启用
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 充值奖励规则新增/编辑对话框 -->
    <el-dialog
      v-model="rechargeDialogVisible"
      :title="rechargeIsEdit ? '编辑充值奖励规则' : '新增充值奖励规则'"
      width="700px"
      :close-on-click-modal="false"
      @close="handleRechargeDialogClose"
    >
      <el-form ref="rechargeFormRef" :model="rechargeFormData" :rules="rechargeFormRules" label-width="140px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="rechargeFormData.name" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="充值门槛" prop="minRechargeAmount">
          <el-input-number
            v-model="rechargeFormData.minRechargeAmount"
            :min="0.01"
            :precision="2"
            placeholder="请输入充值门槛（元）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="升级目标" prop="targetVipLevel">
          <el-select v-model="rechargeFormData.targetVipLevel" placeholder="不选择则不升级VIP" clearable style="width: 100%">
            <el-option label="VIP1" :value="1" />
            <el-option label="VIP2" :value="2" />
            <el-option label="VIP3" :value="3" />
            <el-option label="VIP4" :value="4" />
            <el-option label="VIP5" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="rechargeFormData.targetVipLevel" label="VIP时长（天）" prop="giftDurationDays">
          <el-input-number
            v-model="rechargeFormData.giftDurationDays"
            :min="1"
            :precision="0"
            placeholder="默认365天"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="赠送专用金" prop="giftSpecialFund">
          <el-input-number
            v-model="rechargeFormData.giftSpecialFund"
            :min="0"
            :precision="2"
            placeholder="0表示不赠送"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="赠送优惠券" prop="giftTemplateId" @click.stop @mousedown.stop>
          <CouponTemplateSelector
            :model-value="rechargeFormData.giftTemplateId ?? undefined"
            @update:model-value="(val) => rechargeFormData.giftTemplateId = val ?? null"
            @change="handleCouponTemplateChange"
          />
        </el-form-item>
        <el-form-item
          v-if="rechargeFormData.giftTemplateId"
          label="赠送张数"
          prop="giftTemplateCount"
        >
          <el-input-number
            v-model="rechargeFormData.giftTemplateCount"
            :min="1"
            :precision="0"
            placeholder="请输入赠送张数"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rechargeSubmitLoading" @click="handleRechargeSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- VIP权益配置对话框 -->
    <el-dialog
      v-model="vipDialogVisible"
      :title="`配置 VIP${currentVipLevel} 月度权益`"
      width="800px"
      :close-on-click-modal="false"
      @close="handleVipDialogClose"
    >
      <el-table 
        :data="vipRightsTableData" 
        border 
        class="vip-rights-table"
        style="margin-bottom: 16px"
      >
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="类型" width="120">
          <template #default="{ row, $index }">
            <el-select 
              v-model="row.benefitType" 
              placeholder="请选择" 
              @change="handleTableBenefitTypeChange($index)"
            >
              <el-option label="送券" :value="1" />
              <el-option label="送专用金" :value="2" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="350">
          <template #default="{ row, $index }">
            <!-- 外层容器：Flex 布局，垂直居中，宽度 100% -->
            <div class="flex items-center w-full">
              <!-- 场景1：送券 -->
              <template v-if="row.benefitType === 1">
                <!-- 选择器：占据剩余空间 -->
                <div class="flex-1 mr-2" style="min-width: 0;">
                  <CouponTemplateSelector
                    :key="`coupon-${$index}`"
                    v-model="row.templateId"
                    @change="(id, template) => handleTableCouponChange($index, id, template)"
                  />
                </div>
                
                <!-- 数量区：强制不换行，固定宽度 -->
                <div v-if="row.templateId" class="flex items-center shrink-0" style="white-space: nowrap;">
                  <span class="text-xs text-gray-400 mx-1">×</span>
                  <el-input-number
                    v-model="row.templateCount"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    style="width: 60px;"
                    placeholder="张"
                  />
                </div>
              </template>
              <!-- 场景2：送专用金 -->
              <el-input-number
                v-else-if="row.benefitType === 2"
                v-model="row.benefitValue"
                :min="0.01"
                :precision="2"
                :controls="false"
                placeholder="输入金额"
                class="w-full"
              >
                <template #prefix>￥</template>
              </el-input-number>
              <!-- 场景3：未选 -->
              <span v-else class="text-gray-400 text-xs">请先选择左侧类型</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              :icon="Delete"
              :disabled="vipRightsTableData.length <= 1"
              @click="handleRemoveVipRightsRow($index)"
            />
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-bottom: 16px">
        <el-button type="primary" :icon="Plus" @click="handleAddVipRightsRow">添加权益</el-button>
      </div>
      <template #footer>
        <el-button @click="vipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="vipSubmitLoading" @click="handleVipSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {Delete, Plus, QuestionFilled} from '@element-plus/icons-vue'
import {
  createRechargeRule,
  deleteRechargeRule,
  getCouponTemplateDetail,
  getRechargeRuleList,
  getVipRightsByLevel,
  getVipRightsList,
  saveVipRights,
  saveVipRightsBatch,
  toggleRechargeRuleStatus,
  updateRechargeRule
} from '@/api/marketing'
import CouponTemplateSelector from '@/components/CouponTemplateSelector/index.vue'
import type {
  CouponTemplateVO,
  RechargeRuleCreateRequest,
  RechargeRuleUpdateRequest,
  RechargeRuleVO,
  VipRightsSaveRequest,
  VipRightsVO
} from '@/types/marketing'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'

const activeTab = ref('recharge')

// 充值奖励规则
const rechargeLoading = ref(false)
const rechargeRuleList = ref<RechargeRuleVO[]>([])
const rechargeDialogVisible = ref(false)
const rechargeIsEdit = ref(false)
const rechargeFormRef = ref<FormInstance>()
const rechargeSubmitLoading = ref(false)

const rechargeFormData = reactive<{
  id?: number
  name: string
  minRechargeAmount: number
  targetVipLevel?: number
  giftDurationDays?: number
  giftSpecialFund: number
  giftTemplateId?: number | null
  giftTemplateName?: string
  giftTemplateCount?: number
}>({
  name: '',
  minRechargeAmount: 0,
  targetVipLevel: undefined,
  giftDurationDays: 365,
  giftSpecialFund: 0,
  giftTemplateId: undefined,
  giftTemplateName: undefined,
  giftTemplateCount: 1
})

const rechargeFormRules: FormRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  minRechargeAmount: [
    { required: true, message: '请输入充值门槛', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '充值门槛必须大于0', trigger: 'blur' }
  ],
  giftSpecialFund: [{ type: 'number', min: 0, message: '赠送专用金不能小于0', trigger: 'blur' }],
  giftTemplateCount: [
    {
      validator: (_rule, value, callback) => {
        if (rechargeFormData.giftTemplateId && (!value || value < 1)) {
          callback(new Error('请输入赠送张数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// VIP月度权益
const vipLoading = ref(false)
const vipRightsList = ref<VipRightsVO[]>([])
const vipDialogVisible = ref(false)
const vipSubmitLoading = ref(false)
const currentVipLevel = ref(1)

// VIP权益配置表格数据
interface VipRightsTableItem {
  benefitType?: number | null // 1-送券, 2-送专用金
  templateId?: number | null
  templateName?: string
  templateCount?: number
  benefitValue?: number | null
}

const vipRightsTableData = ref<VipRightsTableItem[]>([])
const vipRightsFullList = ref<VipRightsVO[]>([]) // 存储所有权益（包括同一等级的多条）

// 获取指定等级的所有权益
const getVipRightsForLevel = (vipLevel: number): VipRightsVO[] => {
  return vipRightsFullList.value.filter(item => item.vipLevel === vipLevel && item.status === 1)
}

// 加载充值奖励规则列表
const loadRechargeRuleList = async () => {
  rechargeLoading.value = true
  try {
    rechargeRuleList.value = await getRechargeRuleList()
  } catch (error) {
    console.error('加载充值奖励规则列表失败:', error)
    rechargeRuleList.value = []
  } finally {
    rechargeLoading.value = false
  }
}

// 加载VIP月度权益列表
const loadVipRightsList = async () => {
  vipLoading.value = true
  try {
    const list = await getVipRightsList()
    vipRightsFullList.value = list // 保存完整列表
    
    // 按VIP等级分组，每个等级显示一条主记录（用于显示状态等）
    const levels = [1, 2, 3, 4, 5]
    const result: VipRightsVO[] = []
    levels.forEach(level => {
      const rightsForLevel = list.filter(item => item.vipLevel === level)
      if (rightsForLevel.length > 0) {
        // 取第一条作为主记录（用于显示状态）
        result.push({
          ...rightsForLevel[0],
        } as VipRightsVO)
      } else {
        // 创建占位项
        result.push({
          id: 0,
          vipLevel: level,
          benefitType: null,
          status: 0
        } as VipRightsVO)
      }
    })
    vipRightsList.value = result
  } catch (error) {
    console.error('加载VIP月度权益列表失败:', error)
    // 如果加载失败，至少显示5个等级的占位项
    vipRightsList.value = [1, 2, 3, 4, 5].map(level => ({
      id: 0,
      vipLevel: level,
      benefitType: null,
      status: 0
    } as VipRightsVO))
    vipRightsFullList.value = []
  } finally {
    vipLoading.value = false
  }
}

// 新增充值奖励规则
const handleAddRechargeRule = () => {
  rechargeIsEdit.value = false
  resetRechargeForm()
  rechargeDialogVisible.value = true
}

// 编辑充值奖励规则
const handleEditRechargeRule = async (row: RechargeRuleVO) => {
  rechargeIsEdit.value = true
  resetRechargeForm()
  
  rechargeFormData.id = row.id
  rechargeFormData.name = row.name
  rechargeFormData.minRechargeAmount = row.minRechargeAmount
  rechargeFormData.targetVipLevel = row.targetVipLevel
  rechargeFormData.giftDurationDays = row.giftDurationDays || 365
  rechargeFormData.giftSpecialFund = row.giftSpecialFund || 0
  rechargeFormData.giftTemplateId = row.giftTemplateId ?? null
  rechargeFormData.giftTemplateCount = row.giftTemplateCount || 1
  rechargeFormData.giftTemplateName = row.giftTemplateName || undefined
  
  // 如果后端没有返回名称但有ID，则调用详情接口获取
  if (rechargeFormData.giftTemplateId && !rechargeFormData.giftTemplateName) {
    try {
      const template = await getCouponTemplateDetail(rechargeFormData.giftTemplateId)
      rechargeFormData.giftTemplateName = template.name
    } catch (error) {
      console.error('获取优惠券模板详情失败:', error)
    }
  }
  
  rechargeDialogVisible.value = true
}

// 删除充值奖励规则
const handleDeleteRechargeRule = (row: RechargeRuleVO) => {
  ElMessageBox.confirm(`确定要删除规则"${row.name}"吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        await deleteRechargeRule(row.id)
        ElMessage.success('删除成功')
        loadRechargeRuleList()
      } catch (error: any) {
        ElMessage.error(error.message || '删除失败')
      }
    })
    .catch(() => {})
}

// 充值奖励规则状态切换
const handleRechargeRuleStatusChange = async (row: RechargeRuleVO) => {
  try {
    await toggleRechargeRuleStatus(row.id, row.status)
    ElMessage.success(row.status === 1 ? '启用成功' : '停用成功')
    loadRechargeRuleList()
  } catch (error: any) {
    // 恢复原状态
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error(error.message || '操作失败')
  }
}

// 优惠券模板选择变化
const handleCouponTemplateChange = (templateId?: number, template?: CouponTemplateVO) => {
  if (templateId && template) {
    rechargeFormData.giftTemplateName = template.name
  } else {
    rechargeFormData.giftTemplateName = undefined
    rechargeFormData.giftTemplateCount = 1
  }
}

// 提交充值奖励规则
const handleRechargeSubmit = () => {
  rechargeFormRef.value?.validate(async valid => {
    if (!valid) return
    
    rechargeSubmitLoading.value = true
    try {
      if (rechargeIsEdit.value) {
        const updateData: RechargeRuleUpdateRequest = {
          id: rechargeFormData.id!,
          name: rechargeFormData.name,
          minRechargeAmount: rechargeFormData.minRechargeAmount,
          targetVipLevel: rechargeFormData.targetVipLevel,
          giftDurationDays: rechargeFormData.giftDurationDays,
          giftSpecialFund: rechargeFormData.giftSpecialFund || 0,
          giftTemplateId: rechargeFormData.giftTemplateId ?? null, // 明确设置为 null 以删除
          giftTemplateCount: rechargeFormData.giftTemplateId ? rechargeFormData.giftTemplateCount : undefined
        }
        await updateRechargeRule(rechargeFormData.id!, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: RechargeRuleCreateRequest = {
          name: rechargeFormData.name,
          minRechargeAmount: rechargeFormData.minRechargeAmount,
          targetVipLevel: rechargeFormData.targetVipLevel,
          giftDurationDays: rechargeFormData.giftDurationDays,
          giftSpecialFund: rechargeFormData.giftSpecialFund || 0,
          giftTemplateId: rechargeFormData.giftTemplateId ?? null,
          giftTemplateCount: rechargeFormData.giftTemplateId ? rechargeFormData.giftTemplateCount : undefined
        }
        await createRechargeRule(createData)
        ElMessage.success('创建成功')
      }
      rechargeDialogVisible.value = false
      loadRechargeRuleList()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      rechargeSubmitLoading.value = false
    }
  })
}

// 重置充值奖励规则表单
const resetRechargeForm = () => {
  rechargeFormData.id = undefined
  rechargeFormData.name = ''
  rechargeFormData.minRechargeAmount = 0
  rechargeFormData.targetVipLevel = undefined
  rechargeFormData.giftDurationDays = 365
  rechargeFormData.giftSpecialFund = 0
  rechargeFormData.giftTemplateId = undefined
  rechargeFormData.giftTemplateCount = 1
  rechargeFormRef.value?.clearValidate()
}

// 关闭充值奖励规则对话框
const handleRechargeDialogClose = () => {
  resetRechargeForm()
}

// 配置VIP权益
const handleConfigVipRights = async (row: VipRightsVO) => {
  currentVipLevel.value = row.vipLevel
  vipRightsTableData.value = []
  
  // 加载该等级的所有权益配置
  try {
    const rightsList = await getVipRightsByLevel(row.vipLevel)
    if (rightsList && rightsList.length > 0) {
      // 转换为表格数据
      vipRightsTableData.value = rightsList.map(right => ({
        benefitType: right.benefitType || null,
        templateId: right.templateId || null,
        templateName: right.templateName,
        templateCount: right.templateCount || 1,
        benefitValue: right.benefitValue || null
      }))
    } else {
      // 如果没有配置，添加一行空数据
      vipRightsTableData.value = [{
        benefitType: null,
        templateId: null,
        templateName: undefined,
        templateCount: 1,
        benefitValue: null
      }]
    }
  } catch (error) {
    console.error('加载VIP权益配置失败:', error)
    // 如果加载失败，添加一行空数据
    vipRightsTableData.value = [{
      benefitType: null,
      templateId: null,
      templateName: undefined,
      templateCount: 1,
      benefitValue: null
    }]
  }
  
  vipDialogVisible.value = true
}

// 表格中权益类型变化
const handleTableBenefitTypeChange = (index: number) => {
  const row = vipRightsTableData.value[index]
  if (row.benefitType === 1) {
    // 送券：清空专用金
    row.benefitValue = null
  } else if (row.benefitType === 2) {
    // 送专用金：清空优惠券
    row.templateId = null
    row.templateName = undefined
    row.templateCount = 1
  }
}

// 表格中优惠券模板选择变化
const handleTableCouponChange = (index: number, templateId?: number, template?: CouponTemplateVO) => {
  const row = vipRightsTableData.value[index]
  if (templateId && template) {
    row.templateId = templateId
    row.templateName = template.name
    if (!row.templateCount || row.templateCount < 1) {
      row.templateCount = 1
    }
  } else {
    row.templateId = null
    row.templateName = undefined
    row.templateCount = 1
  }
}

// 添加权益行
const handleAddVipRightsRow = () => {
  vipRightsTableData.value.push({
    benefitType: null,
    templateId: null,
    templateName: undefined,
    templateCount: 1,
    benefitValue: null
  })
}

// 删除权益行
const handleRemoveVipRightsRow = (index: number) => {
  if (vipRightsTableData.value.length > 1) {
    vipRightsTableData.value.splice(index, 1)
  }
}

// 切换VIP权益状态
const handleToggleVipRightsStatus = async (row: VipRightsVO, status: number) => {
  try {
      const saveData: VipRightsSaveRequest = {
        id: row.id,
        vipLevel: row.vipLevel,
        benefitType: row.benefitType || null,
        benefitValue: row.benefitType === 2 ? row.benefitValue : null,
        templateId: row.benefitType === 1 ? row.templateId : null,
        templateCount: row.benefitType === 1 ? row.templateCount : null,
        status: status
      }
    await saveVipRights(saveData)
    ElMessage.success(status === 1 ? '启用成功' : '停用成功')
    loadVipRightsList()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}


// 提交VIP权益（批量保存）
const handleVipSubmit = async () => {
  // 验证表格数据
  const validRights: VipRightsSaveRequest[] = []
  let hasError = false
  
  for (let i = 0; i < vipRightsTableData.value.length; i++) {
    const row = vipRightsTableData.value[i]
    
    if (!row.benefitType) {
      ElMessage.error(`第${i + 1}行：请选择权益类型`)
      hasError = true
      break
    }
    
    if (row.benefitType === 1) {
      // 送券：必须选择优惠券模板
      if (!row.templateId) {
        ElMessage.error(`第${i + 1}行：请选择优惠券模板`)
        hasError = true
        break
      }
      if (!row.templateCount || row.templateCount < 1) {
        ElMessage.error(`第${i + 1}行：请输入赠送张数`)
        hasError = true
        break
      }
      validRights.push({
        vipLevel: currentVipLevel.value,
        benefitType: 1,
        templateId: row.templateId,
        templateCount: row.templateCount,
        benefitValue: null,
        status: 1
      })
    } else if (row.benefitType === 2) {
      // 送专用金：必须输入金额
      if (!row.benefitValue || row.benefitValue <= 0) {
        ElMessage.error(`第${i + 1}行：请输入专用金金额`)
        hasError = true
        break
      }
      validRights.push({
        vipLevel: currentVipLevel.value,
        benefitType: 2,
        benefitValue: row.benefitValue,
        templateId: null,
        templateCount: null,
        status: 1
      })
    }
  }
  
  if (hasError) {
    return
  }
  
  // 如果没有有效权益，设置为停用状态（但还是要保存一条空记录）
  if (validRights.length === 0) {
    validRights.push({
      vipLevel: currentVipLevel.value,
      benefitType: null,
      benefitValue: null,
      templateId: null,
      templateCount: null,
      status: 0
    })
  }
  
  vipSubmitLoading.value = true
  try {
    await saveVipRightsBatch(currentVipLevel.value, validRights)
    ElMessage.success('配置成功')
    vipDialogVisible.value = false
    loadVipRightsList()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    vipSubmitLoading.value = false
  }
}

// 关闭VIP权益对话框
const handleVipDialogClose = () => {
  vipRightsTableData.value = []
  currentVipLevel.value = 1
}

// 初始化
onMounted(() => {
  loadRechargeRuleList()
  loadVipRightsList()
})
</script>

<style scoped lang="scss">
.flex {
  display: flex;
}

.items-center {
  align-items: center;
}

.gap-2 {
  gap: 8px;
}

.flex-1 {
  flex: 1;
}

.w-24 {
  width: 96px;
}

.w-16 {
  width: 64px;
}

.shrink-0 {
  flex-shrink: 0;
}

.text-xs {
  font-size: 12px;
}

.text-gray-400 {
  color: #9ca3af;
}

.mr-1 {
  margin-right: 4px;
}

.w-full {
  width: 100%;
}

.mx-1 {
  margin-left: 4px;
  margin-right: 4px;
}

.mr-2 {
  margin-right: 8px;
}
</style>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}

.tab-content {
  padding: 16px 0;

  .toolbar {
    margin-bottom: 16px;
  }
}

// VIP权益配置表格样式
:deep(.vip-rights-table) {
  // 确保表格不会影响弹窗的层级
  position: relative;
  z-index: 1;
}
</style>


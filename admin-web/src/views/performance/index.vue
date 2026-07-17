<template>
  <div class="app-page">
    <section class="page-header">
      <div>
        <h1>绩效管理</h1>
      </div>
      <div class="header-controls">
        <el-button @click="refreshCurrentTab" :loading="rulesLoading || billsLoading || dashboardLoading || logsLoading">刷新</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="performance-tabs">
      <el-tab-pane label="提成规则" name="rules">
        <el-card shadow="never" class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="ruleQuery.ruleCategory" clearable placeholder="规则类别" style="width: 140px">
                <el-option v-for="item in RULE_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="ruleQuery.enabled" clearable placeholder="启用状态" style="width: 140px">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
              <el-select v-model="ruleQuery.staffId" clearable filterable placeholder="指定员工" style="width: 180px">
                <el-option v-for="staff in staffOptions" :key="staff.id" :label="staff.name" :value="staff.id" />
              </el-select>
              <el-button type="primary" plain @click="loadRules" :loading="rulesLoading">查询</el-button>
            </div>
            <el-button type="primary" v-permission="'performance:rule'" @click="openRuleDialog()">
              新增规则
            </el-button>
          </div>

          <el-table :data="ruleList" v-loading="rulesLoading" border>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column label="类别" width="120">
              <template #default="{ row }">{{ getRuleCategoryName(row.ruleCategory) }}</template>
            </el-table-column>
            <el-table-column label="目标对象" min-width="180">
              <template #default="{ row }">{{ getTargetDisplayName(row) }}</template>
            </el-table-column>
            <el-table-column label="指定员工" min-width="120">
              <template #default="{ row }">{{ getStaffName(row.staffId) }}</template>
            </el-table-column>
            <el-table-column label="计算模式" width="150">
              <template #default="{ row }">{{ getCalcModeName(row.calcMode) }}</template>
            </el-table-column>
            <el-table-column label="计算值" width="120" align="right">
              <template #default="{ row }">{{ formatCalcValue(row.calcMode, row.calcValue) }}</template>
            </el-table-column>
            <el-table-column label="封顶" min-width="180">
              <template #default="{ row }">
                <div>单项：{{ formatCurrency(row.maxPerItem) }}</div>
                <div>整单：{{ formatCurrency(row.maxPerOrder) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="90" align="right">
              <template #default="{ row }">{{ row.priority ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRuleDialog(row)">编辑</el-button>
                <el-button link type="danger" v-permission="'performance:rule'" @click="handleDeleteRule(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="月目标设置" name="goals">
        <el-card shadow="never" class="panel-card goal-card">
          <template #header>员工月目标</template>

          <el-form ref="goalFormRef" :model="goalForm" :rules="goalRules" label-width="110px" class="goal-form">
            <el-form-item label="员工" prop="staffId">
              <el-select v-model="goalForm.staffId" filterable placeholder="请选择员工" style="width: 100%">
                <el-option v-for="staff in staffOptions" :key="staff.id" :label="staff.name" :value="staff.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="月份" prop="month">
              <el-date-picker v-model="goalForm.month" type="month" value-format="YYYY-MM" style="width: 100%" />
            </el-form-item>
            <el-form-item label="销售目标额">
              <el-input-number v-model="goalForm.targetSalesAmount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="新客目标">
              <el-input-number v-model="goalForm.targetNewCustomer" :min="0" :precision="0" style="width: 100%" />
            </el-form-item>
            <el-form-item label="提成目标">
              <el-input-number v-model="goalForm.targetCommission" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="goalSubmitting" @click="submitGoal">保存目标</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="panel-card" style="margin-top: 16px">
          <template #header>历史目标</template>
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="goalHistoryQuery.staffId" filterable clearable placeholder="员工" style="width: 180px">
                <el-option v-for="s in staffOptions" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
              <el-date-picker v-model="goalHistoryQuery.monthFrom" type="month" value-format="YYYY-MM" placeholder="起始月" style="width: 150px" />
              <el-date-picker v-model="goalHistoryQuery.monthTo" type="month" value-format="YYYY-MM" placeholder="结束月" style="width: 150px" />
              <el-button type="primary" plain @click="loadGoalHistory" :loading="goalHistoryLoading">查询</el-button>
            </div>
          </div>
          <el-table :data="goalHistory" v-loading="goalHistoryLoading" border>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column label="员工" min-width="120">
              <template #default="{ row }">{{ row.staffName || getStaffName(row.staffId) }}</template>
            </el-table-column>
            <el-table-column prop="month" label="月份" width="120" />
            <el-table-column label="销售目标" width="130" align="right">
              <template #default="{ row }">{{ formatCurrency(row.targetSalesAmount) }}</template>
            </el-table-column>
            <el-table-column label="新客目标" width="100" align="right">
              <template #default="{ row }">{{ row.targetNewCustomer ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="提成目标" width="130" align="right">
              <template #default="{ row }">{{ formatCurrency(row.targetCommission) }}</template>
            </el-table-column>
            <el-table-column label="设置时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="goalHistoryTotal > goalHistoryQuery.size" class="pagination">
            <el-pagination
              layout="total, prev, pager, next"
              :total="goalHistoryTotal"
              :page-size="goalHistoryQuery.size"
              v-model:current-page="goalHistoryQuery.page"
              @current-change="loadGoalHistory"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="工资单" name="bills">
        <el-card shadow="never" class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-date-picker v-model="billQuery.month" type="month" value-format="YYYY-MM" placeholder="月份" style="width: 160px" />
              <el-select v-model="billQuery.status" clearable placeholder="状态" style="width: 140px">
                <el-option label="待确认" :value="0" />
                <el-option label="已确认" :value="1" />
              </el-select>
              <el-button type="primary" plain @click="loadBills" :loading="billsLoading">查询</el-button>
            </div>
          </div>

          <el-table :data="salaryBills" v-loading="billsLoading" border>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column label="员工" min-width="120">
              <template #default="{ row }">{{ getStaffName(row.staffId) }}</template>
            </el-table-column>
            <el-table-column prop="month" label="月份" width="120" />
            <el-table-column label="总销售额" width="130" align="right">
              <template #default="{ row }">{{ formatCurrency(row.totalSales) }}</template>
            </el-table-column>
            <el-table-column label="总提成" width="130" align="right">
              <template #default="{ row }">{{ formatCurrency(row.totalCommission) }}</template>
            </el-table-column>
            <el-table-column label="新客数" width="100" align="right">
              <template #default="{ row }">{{ row.newCustomerCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '已确认' : '待确认' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">
                <div>创建：{{ formatDateTime(row.createdAt) }}</div>
                <div>确认：{{ formatDateTime(row.confirmedAt) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleViewBillDetail(row.id)">详情</el-button>
                <el-button
                  v-if="row.status !== 1"
                  link
                  type="primary"
                  v-permission="'performance:salary:confirm'"
                  @click="handleConfirmBill(row.id)"
                >
                  确认
                </el-button>
                <span v-if="row.status === 1" style="color:#909399">已确认</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination
              v-model:current-page="billQuery.page"
              v-model:page-size="billQuery.size"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              :total="billTotal"
              @size-change="loadBills"
              @current-change="loadBills"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="员工绩效" name="staff">
        <div class="staff-grid">
          <el-card shadow="never" class="panel-card">
            <template #header>
              <div class="toolbar">
                <div class="toolbar-left">
                  <el-select v-model="staffViewQuery.staffId" filterable placeholder="员工" style="width: 180px">
                    <el-option v-for="staff in staffOptions" :key="staff.id" :label="staff.name" :value="staff.id" />
                  </el-select>
                  <el-date-picker v-model="staffViewQuery.month" type="month" value-format="YYYY-MM" placeholder="月份" style="width: 160px" />
                  <el-button type="primary" plain @click="loadStaffPanel" :loading="dashboardLoading || logsLoading">查询</el-button>
                </div>
              </div>
            </template>

            <div class="staff-metrics" v-loading="dashboardLoading">
              <div class="staff-metric">
                <span>本月销售</span>
                <strong>{{ formatCurrency(staffDashboard.monthSales) }}</strong>
              </div>
              <div class="staff-metric">
                <span>本月提成</span>
                <strong>{{ formatCurrency(staffDashboard.monthCommission) }}</strong>
              </div>
              <div class="staff-metric">
                <span>新客数量</span>
                <strong>{{ staffDashboard.newCustomerCount ?? 0 }}</strong>
              </div>
              <div class="staff-metric">
                <span>目标进度</span>
                <strong>{{ formatPercent(staffDashboard.goalProgress) }}</strong>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="panel-card">
            <template #header>绩效流水</template>

            <el-table :data="staffLogs" v-loading="logsLoading" border :max-height="520">
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="createdAt" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="业务类型" width="100" align="center">
                <template #default="{ row }">{{ getBizTypeName(row.bizType) }}</template>
              </el-table-column>
              <el-table-column label="业务ID" width="100" align="right">
                <template #default="{ row }">{{ row.bizId || '-' }}</template>
              </el-table-column>
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">{{ formatCurrency(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="复核" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.needReview === 1 ? 'warning' : 'success'">{{ row.needReview === 1 ? '待复核' : '已通过' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="calcDesc" label="计算说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="ruleDialogVisible"
      :title="ruleForm.id ? '编辑提成规则' : '新增提成规则'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="110px">
        <el-form-item label="规则类别" prop="ruleCategory">
          <el-select v-model="ruleForm.ruleCategory" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in RULE_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="shouldSelectProductTarget" label="目标商品" prop="targetId">
          <el-select v-model="ruleForm.targetId" clearable filterable placeholder="请选择商品" style="width: 100%">
            <el-option
              v-for="product in productOptions"
              :key="product.id"
              :label="getProductOptionLabel(product)"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="shouldSelectPackageTarget" label="目标套餐" prop="targetId">
          <el-select v-model="ruleForm.targetId" clearable filterable placeholder="请选择套餐" style="width: 100%">
            <el-option
              v-for="pkg in packageOptions"
              :key="pkg.id"
              :label="getPackageOptionLabel(pkg)"
              :value="pkg.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="isGlobalRuleCategory" label="目标对象">
          <div class="rule-target-hint">
            <strong>当前为全局类别</strong>
            <span>充值提成、拉新提成暂不区分商品或套餐目标，可按"指定员工"做定向生效；留空则按通用规则参与结算。</span>
          </div>
        </el-form-item>
        <el-form-item v-else-if="isReservedRuleCategory" label="目标对象">
          <div class="rule-target-hint warning">
            <strong>当前类别预留</strong>
            <span>品牌推广规则的结算事件链路尚未接通，暂不支持配置和启用。</span>
          </div>
        </el-form-item>
        <el-form-item label="指定员工">
          <el-select v-model="ruleForm.staffId" clearable filterable placeholder="为空表示通用" style="width: 100%">
            <el-option v-for="staff in staffOptions" :key="staff.id" :label="staff.name" :value="staff.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计算模式" prop="calcMode">
          <el-select v-model="ruleForm.calcMode" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in CALC_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="计算值" prop="calcValue">
          <el-input-number v-model="ruleForm.calcValue" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单项封顶">
          <el-input-number v-model="ruleForm.maxPerItem" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="整单封顶">
          <el-input-number v-model="ruleForm.maxPerOrder" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="ruleForm.priority" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitting" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="billDetailVisible" title="工资单详情" width="720px">
      <div v-loading="billDetailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="员工">{{ billDetail?.staffName || getStaffName(billDetail?.staffId) }}</el-descriptions-item>
          <el-descriptions-item label="月份">{{ billDetail?.month }}</el-descriptions-item>
          <el-descriptions-item label="总销售额">{{ formatCurrency(billDetail?.totalSales) }}</el-descriptions-item>
          <el-descriptions-item label="总提成">{{ formatCurrency(billDetail?.totalCommission) }}</el-descriptions-item>
          <el-descriptions-item label="新客数">{{ billDetail?.newCustomerCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="billDetail?.status === 1 ? 'success' : 'warning'">
              {{ billDetail?.status === 1 ? '已确认' : '待确认' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(billDetail?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatDateTime(billDetail?.confirmedAt) }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin: 16px 0 8px; font-size: 14px;">提成明细</h4>
        <el-table :data="billDetail?.lineItems || []" border size="small" max-height="400">
          <el-table-column prop="createdAt" label="时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">{{ getBizTypeName(row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">{{ formatCurrency(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="calcDesc" label="计算说明" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPackageList } from '@/api/package'
import { getProductList } from '@/api/product'
import { getStaffList } from '@/api/staff'
import {
  confirmSalaryBill,
  deleteCommissionRule,
  getBillDetail,
  getCommissionRules,
  getGoalHistory,
  getSalaryBills,
  getStaffPerformanceDashboard,
  getStaffPerformanceLogs,
  saveCommissionRule,
  setStaffMonthlyGoal
} from '@/api/performance'
import type { ProductPackageDetailVO } from '@/types/package'
import type { ProductDetailVO } from '@/types/product'
import type { StaffListVO } from '@/types/staff'
import type {
  CommissionRuleFormData,
  CommissionRuleItem,
  CommissionRuleQueryParams,
  GoalHistoryItem,
  GoalPageResult,
  SalaryBillDetail,
  StaffMonthlyGoalFormData,
  StaffPerformanceDashboard,
  StaffPerformanceLogItem,
  StaffSalaryBillItem
} from '@/types/performance'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const activeTab = ref('rules')

const RULE_CATEGORY_OPTIONS = [
  { value: 1, label: '销售提成' },
  { value: 2, label: '充值提成' },
  { value: 3, label: '拉新提成' },
  { value: 4, label: '品牌推广（预留）' },
  { value: 5, label: '套餐提成' }
]

const CALC_MODE_OPTIONS = [
  { value: 'FIXED', label: '固定金额' },
  { value: 'PROFIT_PERCENT', label: '毛利比例' },
  { value: 'SALES_PERCENT', label: '销售额比例' }
]

const staffOptions = ref<StaffListVO[]>([])
const productOptions = ref<ProductDetailVO[]>([])
const packageOptions = ref<ProductPackageDetailVO[]>([])

const ruleQuery = reactive<CommissionRuleQueryParams>({
  ruleCategory: undefined,
  staffId: undefined,
  enabled: undefined
})
const rulesLoading = ref(false)
const ruleList = ref<CommissionRuleItem[]>([])
const ruleDialogVisible = ref(false)
const ruleSubmitting = ref(false)
const ruleFormRef = ref<FormInstance>()
const ruleForm = reactive<CommissionRuleFormData>({
  ruleCategory: 1,
  calcMode: 'FIXED',
  calcValue: 0,
  enabled: true,
  priority: 0,
  targetId: undefined,
  staffId: undefined,
  maxPerItem: undefined,
  maxPerOrder: undefined,
  remark: ''
})
const shouldSelectProductTarget = computed(() => ruleForm.ruleCategory === 1)
const shouldSelectPackageTarget = computed(() => ruleForm.ruleCategory === 5)
const isGlobalRuleCategory = computed(() => ruleForm.ruleCategory === 2 || ruleForm.ruleCategory === 3)
const isReservedRuleCategory = computed(() => ruleForm.ruleCategory === 4)
const ruleRules: FormRules = {
  ruleCategory: [{ required: true, message: '请选择规则类别', trigger: 'change' }],
  targetId: [{
    validator: (_rule, value, callback) => {
      if ((shouldSelectProductTarget.value || shouldSelectPackageTarget.value) && !value) {
        callback(new Error(shouldSelectProductTarget.value ? '请选择目标商品' : '请选择目标套餐'))
        return
      }
      callback()
    },
    trigger: 'change'
  }],
  calcMode: [{ required: true, message: '请选择计算模式', trigger: 'change' }],
  calcValue: [{ required: true, message: '请输入计算值', trigger: 'blur' }]
}

const goalFormRef = ref<FormInstance>()
const goalSubmitting = ref(false)
const goalForm = reactive<StaffMonthlyGoalFormData>({
  staffId: 0,
  month: new Date().toISOString().slice(0, 7),
  targetSalesAmount: 0,
  targetNewCustomer: 0,
  targetCommission: 0
})
const goalRules: FormRules = {
  staffId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  month: [{ required: true, message: '请选择月份', trigger: 'change' }]
}

const billQuery = reactive<{
  month?: string
  status?: number
  page: number
  size: number
}>({
  month: new Date().toISOString().slice(0, 7),
  status: undefined,
  page: 1,
  size: 20
})
const billsLoading = ref(false)
const salaryBills = ref<StaffSalaryBillItem[]>([])
const billTotal = ref(0)

const goalHistoryQuery = reactive({
  staffId: undefined as number | undefined,
  monthFrom: undefined as string | undefined,
  monthTo: undefined as string | undefined,
  page: 1,
  size: 20
})
const goalHistoryLoading = ref(false)
const goalHistory = ref<GoalHistoryItem[]>([])
const goalHistoryTotal = ref(0)

const billDetailVisible = ref(false)
const billDetailLoading = ref(false)
const billDetail = ref<SalaryBillDetail | null>(null)

const staffViewQuery = reactive({
  staffId: 0,
  month: new Date().toISOString().slice(0, 7)
})
const dashboardLoading = ref(false)
const logsLoading = ref(false)
const staffDashboard = ref<StaffPerformanceDashboard>({})
const staffLogs = ref<StaffPerformanceLogItem[]>([])

const formatCurrency = (value?: number | null) => `¥${Number(value || 0).toFixed(2)}`
const formatPercent = (value?: number | null) => `${Number(value || 0).toFixed(2)}%`

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

const getStaffName = (staffId?: number) => {
  if (!staffId) return '通用规则'
  return staffOptions.value.find(item => item.id === staffId)?.name || `员工#${staffId}`
}

const getRuleCategoryName = (value?: number) => {
  return RULE_CATEGORY_OPTIONS.find(item => item.value === value)?.label || `类别${value}`
}

const getProductOptionLabel = (product: ProductDetailVO) => {
  const code = product.code ? `[${product.code}] ` : ''
  const spec = product.specification ? ` ${product.specification}` : ''
  return `${code}${product.name}${spec}`
}

const getPackageOptionLabel = (pkg: ProductPackageDetailVO) => {
  const status = pkg.status === 1 ? '上架' : '下架'
  return `[${status}] ${pkg.name}`
}

const getTargetDisplayName = (row: CommissionRuleItem) => {
  if (row.targetName) {
    return row.targetName
  }
  if (!row.targetId) {
    if (row.ruleCategory === 2 || row.ruleCategory === 3) {
      return '全局规则'
    }
    if (row.ruleCategory === 4) {
      return '预留类别'
    }
    return '-'
  }
  if (row.ruleCategory === 1) {
    return productOptions.value.find(item => item.id === row.targetId)?.name || `商品#${row.targetId}`
  }
  if (row.ruleCategory === 5) {
    return packageOptions.value.find(item => item.id === row.targetId)?.name || `套餐#${row.targetId}`
  }
  return `目标#${row.targetId}`
}

const getCalcModeName = (value?: string) => {
  return CALC_MODE_OPTIONS.find(item => item.value === value)?.label || value || '-'
}

const formatCalcValue = (mode?: string, value?: number) => {
  if (mode === 'PROFIT_PERCENT' || mode === 'SALES_PERCENT') {
    return formatPercent((Number(value || 0) * 100))
  }
  return formatCurrency(value)
}

const getBizTypeName = (value?: number) => {
  if (value === 1) return '销售'
  if (value === 2) return '充值'
  if (value === 3) return '拉新'
  if (value === 4) return '退款扣减'
  return value ? `类型${value}` : '-'
}

const resolveDefaultStaffId = () => {
  const currentStaffId = Number(userStore.userInfo?.id || 0)
  if (currentStaffId && staffOptions.value.some(item => item.id === currentStaffId)) {
    return currentStaffId
  }
  return staffOptions.value[0]?.id || 0
}

const resetRuleForm = () => {
  ruleForm.id = undefined
  ruleForm.ruleCategory = 1
  ruleForm.targetId = undefined
  ruleForm.staffId = undefined
  ruleForm.calcMode = 'FIXED'
  ruleForm.calcValue = 0
  ruleForm.maxPerItem = undefined
  ruleForm.maxPerOrder = undefined
  ruleForm.priority = 0
  ruleForm.enabled = true
  ruleForm.remark = ''
}

const openRuleDialog = (row?: CommissionRuleItem) => {
  resetRuleForm()
  if (row) {
    ruleForm.id = row.id
    ruleForm.ruleCategory = row.ruleCategory
    ruleForm.targetId = row.targetId
    ruleForm.staffId = row.staffId
    ruleForm.calcMode = row.calcMode
    ruleForm.calcValue = Number(row.calcValue || 0)
    ruleForm.maxPerItem = row.maxPerItem
    ruleForm.maxPerOrder = row.maxPerOrder
    ruleForm.priority = row.priority
    ruleForm.enabled = row.enabled === 1
    ruleForm.remark = row.remark || ''
  }
  ruleDialogVisible.value = true
}

const loadProductOptions = async () => {
  try {
    const response = await getProductList({
      status: 1,
      pageNum: 1,
      pageSize: 1000
    })
    productOptions.value = response.records || []
  } catch (error) {
    console.error('加载绩效规则商品选项失败:', error)
    productOptions.value = []
  }
}

const loadPackageOptions = async () => {
  try {
    const response = await getPackageList({
      pageNum: 1,
      pageSize: 1000
    })
    packageOptions.value = response.records || []
  } catch (error) {
    console.error('加载绩效规则套餐选项失败:', error)
    packageOptions.value = []
  }
}

const loadStaffOptions = async () => {
  try {
    const response = await getStaffList({ pageNum: 1, pageSize: 1000 })
    staffOptions.value = response.records || []
    if (!goalForm.staffId && staffOptions.value.length > 0) {
      goalForm.staffId = resolveDefaultStaffId()
    }
    if (!staffViewQuery.staffId) {
      staffViewQuery.staffId = resolveDefaultStaffId()
    }
  } catch (error) {
    console.error('加载绩效员工选项失败:', error)
    staffOptions.value = []
    ElMessage.error('加载员工选项失败')
  }
}

const loadRules = async () => {
  rulesLoading.value = true
  try {
    ruleList.value = await getCommissionRules(ruleQuery)
  } catch (error: any) {
    ruleList.value = []
    ElMessage.error(error?.message || '加载提成规则失败')
  } finally {
    rulesLoading.value = false
  }
}

const submitRule = () => {
  ruleFormRef.value?.validate(async valid => {
    if (!valid) return
    if (isReservedRuleCategory.value) {
      ElMessage.warning('品牌推广规则暂未接通结算链路，当前不支持配置')
      return
    }
    ruleSubmitting.value = true
    try {
      await saveCommissionRule(ruleForm)
      ElMessage.success(ruleForm.id ? '规则已更新' : '规则已创建')
      ruleDialogVisible.value = false
      loadRules()
    } catch (error: any) {
      ElMessage.error(error.message || '保存规则失败')
    } finally {
      ruleSubmitting.value = false
    }
  })
}

const submitGoal = () => {
  goalFormRef.value?.validate(async valid => {
    if (!valid) return
    goalSubmitting.value = true
    try {
      await setStaffMonthlyGoal(goalForm)
      ElMessage.success('月目标已保存')
    } catch (error: any) {
      ElMessage.error(error.message || '保存月目标失败')
    } finally {
      goalSubmitting.value = false
    }
  })
}

const loadBills = async () => {
  billsLoading.value = true
  try {
    salaryBills.value = await getSalaryBills(billQuery)
    billTotal.value = salaryBills.value.length
  } catch (error: any) {
    salaryBills.value = []
    billTotal.value = 0
    ElMessage.error(error?.message || '加载工资单失败')
  } finally {
    billsLoading.value = false
  }
}

const handleConfirmBill = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认后工资单将标记为已确认，是否继续？', '确认工资单', {
      type: 'warning'
    })
    await confirmSalaryBill(id)
    ElMessage.success('工资单已确认')
    loadBills()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}

const handleDeleteRule = async (id: number) => {
  try {
    await ElMessageBox.confirm('删除后规则将停用，是否继续？', '确认删除', { type: 'warning' })
    await deleteCommissionRule(id)
    ElMessage.success('规则已删除')
    loadRules()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

const loadGoalHistory = async () => {
  goalHistoryLoading.value = true
  try {
    const res = await getGoalHistory(goalHistoryQuery)
    goalHistory.value = res?.records || []
    goalHistoryTotal.value = res?.total ?? 0
  } catch (error: any) {
    goalHistory.value = []
    goalHistoryTotal.value = 0
    ElMessage.error(error?.message || '加载目标历史失败')
  } finally {
    goalHistoryLoading.value = false
  }
}

const handleViewBillDetail = async (id: number) => {
  billDetailVisible.value = true
  billDetailLoading.value = true
  billDetail.value = null
  try {
    billDetail.value = await getBillDetail(id)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载详情失败')
  } finally {
    billDetailLoading.value = false
  }
}

const loadStaffPanel = async () => {
  if (!staffViewQuery.staffId) return
  dashboardLoading.value = true
  logsLoading.value = true
  try {
    const [dashboard, logs] = await Promise.all([
      getStaffPerformanceDashboard(staffViewQuery.staffId),
      getStaffPerformanceLogs({
        staffId: staffViewQuery.staffId,
        month: staffViewQuery.month,
        page: 1,
        size: 20
      })
    ])
    staffDashboard.value = dashboard
    staffLogs.value = logs || []
  } catch (error: any) {
    console.error('加载员工绩效失败:', error)
    staffDashboard.value = {}
    staffLogs.value = []
    ElMessage.error(error?.message || '加载员工绩效失败')
  } finally {
    dashboardLoading.value = false
    logsLoading.value = false
  }
}

const refreshCurrentTab = () => {
  if (activeTab.value === 'rules') {
    loadRules()
    return
  }
  if (activeTab.value === 'goals') {
    loadGoalHistory()
    return
  }
  if (activeTab.value === 'bills') {
    loadBills()
    return
  }
  if (activeTab.value === 'staff') {
    loadStaffPanel()
  }
}

watch(activeTab, (tab) => {
  if (tab === 'rules' && ruleList.value.length === 0) loadRules()
  if (tab === 'goals' && goalHistory.value.length === 0) loadGoalHistory()
  if (tab === 'bills' && salaryBills.value.length === 0) loadBills()
  if (tab === 'staff' && staffLogs.value.length === 0) loadStaffPanel()
})

watch(() => ruleForm.ruleCategory, () => {
  ruleForm.targetId = undefined
  ruleFormRef.value?.clearValidate(['targetId'])
})

onMounted(async () => {
  try {
    await Promise.all([loadStaffOptions(), loadProductOptions(), loadPackageOptions()])
    await Promise.all([loadRules(), loadBills(), loadGoalHistory(), loadStaffPanel()])
  } catch (error) {
    console.error('Failed to initialize performance page:', error)
  }
})
</script>

<style scoped lang="scss">
.performance-tabs {
  margin-top: 0;
}

.panel-card {
  border: none;
  border-radius: 18px;
  box-shadow: 0 10px 24px rgba(40, 62, 92, 0.08);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}


.goal-card {
  max-width: 640px;
}

.goal-form {
  padding-top: 6px;
}

.rule-target-hint {
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  background: linear-gradient(180deg, #eef6ff 0%, #f6fbff 100%);
  border: 1px solid #cfe2f3;
  color: #36536b;
  line-height: 1.6;
}

.rule-target-hint strong {
  display: block;
  margin-bottom: 2px;
  font-size: 13px;
  color: #213547;
}

.rule-target-hint span {
  display: block;
  font-size: 12px;
}

.rule-target-hint.warning {
  background: linear-gradient(180deg, #fff7eb 0%, #fffaf4 100%);
  border-color: #f5d3a8;
  color: #8a4b12;
}

.rule-target-hint.warning strong {
  color: #7a3d0b;
}

.staff-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.4fr);
  gap: 16px;
}

.staff-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.staff-metric {
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f7fbff 0%, #edf4fb 100%);
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: #667085;
}

.staff-metric strong {
  font-size: 24px;
  line-height: 1.1;
  color: #1f2937;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .page-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .staff-grid {
    grid-template-columns: 1fr;
  }

  .staff-metrics {
    grid-template-columns: 1fr;
  }
}
</style>

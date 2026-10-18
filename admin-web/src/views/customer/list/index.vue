<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="客户姓名">
          <el-input v-model="queryParams.name" placeholder="请输入客户姓名" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 150px">
            <el-option label="普通" :value="1" />
            <el-option label="VIP" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属员工">
          <el-select v-model="queryParams.bindStaffId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="staff in staffOptions" :key="staff.id" :label="staff.name" :value="staff.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'customer:create'" type="success" :icon="Plus" @click="handleAdd">新增客户</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="客户信息" min-width="180">
          <template #default="{ row }">
            <div>
              <div style="font-weight: 500">{{ row.name }}</div>
              <div style="font-size: 12px; color: #626f7e">{{ row.phone || '-' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.type === 2" type="warning">
              VIP{{ row.vipLevel || 1 }}
            </el-tag>
            <el-tag v-else>普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本金余额" width="140" align="right">
          <template #default="{ row }">
            <el-tooltip :content="`专用金 ¥${(row.specialFundBalance || 0).toFixed(2)} · 冻结专用金 ¥${(row.frozenSpecialFund || 0).toFixed(2)} · 积分 ${row.points || 0}`">
              <span tabindex="0" :aria-label="`本金余额${row.balance || 0}元，专用金${row.specialFundBalance || 0}元，冻结专用金${row.frozenSpecialFund || 0}元，积分${row.points || 0}`">¥{{ (row.balance || 0).toFixed(2) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="VIP有效期" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.type === 2 && row.vipEndDate" :style="{ color: isVipExpiringSoon(row.vipEndDate) ? '#f56c6c' : '#606266' }">
              {{ formatDate(row.vipEndDate) }}
              <span v-if="isVipExpiringSoon(row.vipEndDate)" style="font-size: 12px; display: block; margin-top: 2px">即将过期</span>
            </span>
            <span v-else style="color: #626f7e">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="bindStaffName" label="归属员工" width="120" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">账款 / 订单</el-button>
            <el-dropdown v-if="hasAnyOwnedPermission(['customer:update', 'customer:delete', 'customer:recharge', 'customer:coupon:grant'])" trigger="click">
              <el-button link type="primary" :aria-label="`${row.name}更多操作`">更多</el-button>
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item v-permission="'customer:update'" @click="handleEdit(row)">编辑客户</el-dropdown-item>
                <el-dropdown-item v-permission="'customer:recharge'" @click="handleRecharge(row)">账户充值</el-dropdown-item>
                <el-dropdown-item v-permission="'customer:coupon:grant'" @click="handleGrantCoupon(row)">发放优惠券</el-dropdown-item>
                <el-dropdown-item v-permission="'customer:delete'" divided @click="handleDelete(row)">删除客户</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 充值对话框 -->
    <el-dialog
      v-model="rechargeVisible"
      title="客户充值"
      width="500px"
      :close-on-click-modal="false"
      @close="handleRechargeClose"
    >
      <el-form ref="rechargeFormRef" :model="rechargeFormData" :rules="rechargeFormRules" label-width="100px">
        <el-form-item label="客户姓名">
          <el-input v-model="rechargeFormData.customerName" disabled />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="rechargeFormData.customerPhone" disabled />
        </el-form-item>
        <el-form-item label="充值金额" prop="amount">
          <el-input-number
            v-model="rechargeFormData.amount"
            :min="0.01"
            :precision="2"
            placeholder="请输入充值金额"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="支付方式" prop="paymentMethod">
          <el-select v-model="rechargeFormData.paymentMethod" placeholder="请选择支付方式" style="width: 100%">
            <el-option label="现金" :value="1" />
            <el-option label="微信" :value="2" />
            <el-option label="支付宝" :value="3" />
          </el-select>
        </el-form-item>
        <el-alert
          title="提示"
          type="info"
          :closable="false"
          style="margin-bottom: 16px"
        >
          <template #default>
            <div>注：单次充值达到门槛即可升级/延期；若当前等级高于目标等级，将保留原等级并累加有效期。</div>
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button v-permission="'customer:recharge'" type="primary" :loading="rechargeLoading" @click="handleRechargeSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发券对话框 -->
    <el-dialog
      v-model="grantCouponVisible"
      title="手动发券"
      width="500px"
      :close-on-click-modal="false"
      @close="handleGrantCouponClose"
    >
      <el-form ref="grantCouponFormRef" :model="grantCouponFormData" :rules="grantCouponFormRules" label-width="100px">
        <el-form-item label="客户姓名">
          <el-input v-model="grantCouponFormData.customerName" disabled />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="grantCouponFormData.customerPhone" disabled />
        </el-form-item>
        <el-form-item label="优惠券模板" prop="templateId" @click.stop @mousedown.stop>
          <CouponTemplateSelector
            v-model="grantCouponFormData.templateId"
            @change="handleTemplateChange"
          />
        </el-form-item>
        <el-form-item label="发放数量" prop="count">
          <el-input-number
            v-model="grantCouponFormData.count"
            :min="1"
            :precision="0"
            placeholder="请输入发放数量"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantCouponVisible = false">取消</el-button>
        <el-button v-permission="'customer:coupon:grant'" type="primary" :loading="grantCouponLoading" @click="handleGrantCouponSubmit">确定</el-button>
      </template>
    </el-dialog>
    <!-- 还款弹窗 -->
    <RepaymentDialog
      v-model="repaymentVisible"
      :customerId="customerDetail?.id || 0"
      :customerName="customerDetail?.name || ''"
      :currentTotalDebt="customerDetail?.totalDebt || 0"
      @success="onRepaymentSuccess"
    />

    <!-- 详情抽屉：360° 客户全景 -->
    <el-drawer v-model="detailVisible" :title="customerDetail?.name ? `${customerDetail.name} · 客户账款与订单` : '加载客户详情'" size="min(1100px, 94vw)">
      <div v-if="customerDetail">
        <!-- 核心指标卡片 -->
        <el-row :gutter="12" class="metrics-row">
          <el-col :span="6">
            <el-card shadow="hover" class="metric-card metric-card-red">
              <div class="metric-header">
                <el-icon class="metric-icon"><Warning /></el-icon>
                <div class="metric-title">
                  客户欠款

                </div>

              </div>
              <div class="metric-value" :style="{ color: (customerDetail.totalDebt || 0) > 0 ? '#b42318' : '#303133' }">
                {{ formatAmount(customerDetail.totalDebt || 0) }}
              </div>
              <div class="metric-sub">
                赊销额度：{{ formatAmount(customerDetail.creditLimit || 0) }}
                <!-- 使用 flex 容器包裹两个按钮，align-items: center 实现垂直居中，gap 控制间距 -->
<div style="display: flex; align-items: center; gap: 8px; display: inline-flex;">

  <!-- 按钮 1：调额 -->
  <el-button
    link
    type="primary"
    size="small"
    v-permission="'credit:limit:update'"
    @click="handleEditLimit"
    style="padding: 0"
  >
    调整额度
  </el-button>

  <!-- 按钮 2：还款 (直接把 v-if 加在 button 上，去掉外层 div) -->
  <el-button
    v-if="customerDetail && customerDetail.totalDebt != null && customerDetail.totalDebt > 0"
    v-permission="'credit:repay'"
    link
    type="primary"
    size="small"
    style="padding: 0"
    @click="handleRepayClick"
  >
    办理还款
  </el-button>

</div>
              </div>

            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="metric-card metric-card-blue">
              <div class="metric-header">
                <el-icon class="metric-icon"><Wallet /></el-icon>
                <div class="metric-title">钱包余额</div>
              </div>
              <div class="metric-value">
                {{ formatAmount((customerDetail.balance || 0) + (customerDetail.specialFundBalance || 0)) }}
              </div>
              <div class="metric-sub">
                专用金：{{ formatAmount(customerDetail.specialFundBalance || 0) }}
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="metric-card metric-card-green">
              <div class="metric-header">
                <el-icon class="metric-icon"><TrendCharts /></el-icon>
                <div class="metric-title">累计消费</div>
              </div>
              <div class="metric-value">
                {{ formatAmount(customerDetail.totalOrderAmount || 0) }}
              </div>
              <div class="metric-sub">订单数：{{ customerDetail.totalOrderCount ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="metric-card metric-card-orange">
              <div class="metric-header">
                <el-icon class="metric-icon"><Star /></el-icon>
                <div class="metric-title">可用积分</div>
              </div>
              <div class="metric-value">
                {{ (customerDetail.points ?? 0).toFixed(0) }}
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 业务记录 Tabs -->
        <el-tabs v-model="detailActiveTab" class="detail-tabs">
          <el-tab-pane v-if="canViewCreditTab" label="赊销/还款" name="credit">
            <div class="credit-toolbar">
              <div class="credit-summary">
                <span>本店欠款：<strong>{{ currentCreditSummary ? formatAmount(currentCreditSummary.totalDebt) : '未读取' }}</strong></span>
                <span>赊销额度：{{ currentCreditSummary ? formatAmount(currentCreditSummary.creditLimit) : '未读取' }}</span>
                <span>剩余额度：{{ currentCreditSummary ? formatAmount(currentCreditSummary.remainingCredit) : '未读取' }}</span>
              </div>
              <div class="credit-actions">
                <el-select
                  v-model="creditQuery.status"
                  placeholder="全部状态"
                  clearable
                  style="width: 140px"
                  @change="handleCreditFilterChange"
                >
                  <el-option label="未还清" :value="1" />
                  <el-option label="已还清" :value="2" />
                  <el-option label="已逾期" :value="3" />
                </el-select>
                <el-button v-permission="['credit:account:view', 'credit:repayment:view']" @click="refreshCreditTab">刷新</el-button>
                <el-button
                  type="primary"
                  plain
                  v-permission="'credit:statement:generate'"
                  @click="handleOpenStatementDialog"
                >
                  生成对账单
                </el-button>
              </div>
            </div>

            <div class="credit-section-title">欠款单据</div>
            <el-table :data="creditList" v-loading="creditLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="orderId" label="关联订单" min-width="160">
                <template #default="{ row }">
                  <el-link v-if="row.orderId" type="primary" underline @click.prevent="openOrder(row.orderId)">
                    {{ row.orderNo || row.orderId }}
                  </el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="creditAmount" label="原赊销金额" width="140" align="right">
                <template #default="{ row }">{{ formatAmount(row.creditAmount || 0) }}</template>
              </el-table-column>
              <el-table-column label="累计实还" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.paidAmount) }}</template></el-table-column>
              <el-table-column label="退货冲欠款" width="130" align="right"><template #default="{ row }">{{ row.creditReductionAmount == null ? '待核对' : formatAmount(row.creditReductionAmount) }}</template></el-table-column>
              <el-table-column label="剩余欠款" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.remainingAmount) }}</template></el-table-column>
              <el-table-column label="已退还款" width="130" align="right"><template #default="{ row }">{{ row.refundedRepaymentAmount == null ? '待核对' : formatAmount(row.refundedRepaymentAmount) }}</template></el-table-column>
              <el-table-column prop="dueDate" label="到期日" width="140">
                <template #default="{ row }">{{ row.dueDate || '-' }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.status === 2 ? 'success' : row.status === 1 ? 'danger' : 'info'">
                    {{ CREDIT_ACCOUNT_STATUS_MAP[row.status] || row.status || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="挂账时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            </el-table>
            <div v-if="creditTotal > 0" class="pagination" style="margin-top:12px">
              <el-pagination
                v-model:current-page="creditQuery.pageNum"
                v-model:page-size="creditQuery.pageSize"
                :page-sizes="[5,10,20]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="creditTotal"
                @current-change="handleCreditPageChange"
                @size-change="handleCreditSizeChange"
              />
            </div>

            <div class="credit-section-title" style="margin-top: 20px">还款记录</div>
            <el-table
              :data="creditRepaymentList"
              v-loading="creditRepaymentLoading"
              border
              :max-height="320"
            >
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div style="padding: 12px 24px">
                    <el-table v-if="row.allocations?.length" :data="row.allocations" border size="small">
                      <el-table-column label="核销原销售单" min-width="170">
                        <template #default="{ row: allocation }">
                          <el-link v-if="allocation.orderId" v-permission="'order:query'" type="primary" @click="openOrder(allocation.orderId)">{{ allocation.orderNo || allocation.orderId }}</el-link>
                          <span v-else>待核对</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="本次核销" align="right"><template #default="{ row: allocation }">{{ formatAmount(allocation.amount) }}</template></el-table-column>
                      <el-table-column label="已退还款" align="right"><template #default="{ row: allocation }">{{ allocation.refundedAmount == null ? '待核对' : formatAmount(allocation.refundedAmount) }}</template></el-table-column>
                    </el-table>
                    <el-empty v-else description="历史还款无可核实的原单分配，需人工核对" :image-size="45" />
                  </div>
                </template>
              </el-table-column>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="repaymentNo" label="还款单号" min-width="180" />
              <el-table-column prop="createdAt" label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column prop="amount" label="还款金额" width="130" align="right">
                <template #default="{ row }">
                  <span style="color:#67c23a">{{ formatAmount(row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="累计已退" width="120" align="right">
                <template #default="{ row }">{{ row.refundedAmount == null ? '待核对' : formatAmount(row.refundedAmount) }}</template>
              </el-table-column>
              <el-table-column prop="paymentMethod" label="还款方式" width="120" align="center">
                <template #default="{ row }">{{ getCreditRepaymentMethodName(row.paymentMethod) }}</template>
              </el-table-column>
              <el-table-column prop="transactionId" label="外部单号" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.transactionId || '-' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="消费订单" name="orders">

            <el-table :data="ordersList" v-loading="ordersLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column label="订单号" min-width="160">
                <template #default="{ row }">
                  <el-link type="primary" underline :title="row.orderNo" class="customer-order-link" @click.prevent="openOrder(row.id)">
                    {{ row.orderNo }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column label="类型/门店" width="180">
                <template #default="{ row }">
                  <span style="color:#909399">[{{ row.orderType === 1 ? 'POS' : row.orderType === 2 ? '自提' : '配送' }}]</span>
                  <span style="margin-left:6px">{{ row.storeName || row.storeId || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="金额" width="170" align="right">
                <template #default="{ row }">
                  <div>应收：{{ formatAmount(getOrderNetReceivable(row)) }}</div>
                  <div style="color:#67c23a">开单已收：{{ formatAmount(getOrderReceivedAmount(row)) }}</div>
                  <div v-if="getOrderCreditAmount(row) > 0" style="color:#f56c6c">原赊销：{{ formatAmount(getOrderCreditAmount(row)) }}</div>
                  <div v-if="getOrderCreditAmount(row) > 0">累计实还：{{ row.repaidAmount == null ? '待核对' : formatAmount(row.repaidAmount) }}</div>
                  <div v-if="getOrderCreditAmount(row) > 0">当前欠款：{{ row.remainingCreditAmount == null ? '待核对' : formatAmount(row.remainingCreditAmount) }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="paymentMethod" label="支付方式" width="140">
                <template #default="{ row }">
                  <span v-if="row.paymentMethod || (row.payments && row.payments.length)">
                    {{ getOrderDisplayPaymentMethod(row) }}
                  </span>
                  <span v-else style="color:#909399">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="orderStatus" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="ORDER_STATUS_TAG_MAP[row.orderStatus] || 'info'" size="small">
                    {{ ORDER_STATUS_MAP[row.orderStatus] || row.orderStatus || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>
            <div v-if="ordersTotal > 0" class="pagination" style="margin-top:12px">
              <el-pagination
                v-model:current-page="ordersQuery.pageNum"
                v-model:page-size="ordersQuery.pageSize"
                :page-sizes="[10,20,50]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="ordersTotal"
                @current-change="handleOrdersPageChange"
                @size-change="handleOrdersSizeChange"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="资金流水" name="transactions">
            <el-table :data="transactionList" v-loading="transactionLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="createdAt" label="时间" width="170" />
              <el-table-column prop="walletType" label="钱包类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small">{{ getWalletTypeName(row.walletType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="transactionType" label="交易类型" width="100" align="center">
                <template #default="{ row }">
                  {{ getTransactionTypeName(row.transactionType) }}
                </template>
              </el-table-column>
              <el-table-column prop="paymentMethod" label="支付方式" width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.paymentMethod">
                    {{ getPaymentMethodName(row.paymentMethod) }}
                  </span>
                  <span v-else style="color: #626f7e">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="变动金额" width="120" align="right">
                <template #default="{ row }">
                  <span :style="{ color: row.amount >= 0 ? '#67c23a' : '#f56c6c' }">
                    {{ row.amount >= 0 ? '+' : '' }}{{ row.amount.toFixed(2) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="balanceAfter" label="余额" width="120" align="right">
                <template #default="{ row }">{{ row.balanceAfter.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="createdByName" label="操作人" width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.createdByName">{{ row.createdByName }}</span>
                  <span v-else style="color: #626f7e">系统</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" show-overflow-tooltip />
            </el-table>
            <div class="pagination" style="margin-top: 12px">
              <el-pagination
                v-model:current-page="transactionQuery.pageNum"
                v-model:page-size="transactionQuery.pageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                :total="transactionTotal"
                @current-change="handleTransactionPageChange"
                @size-change="handleTransactionSizeChange"
              />
            </div>
          </el-tab-pane>



          <el-tab-pane label="持有优惠券" name="coupons">
            <el-table :data="couponList" v-loading="couponLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="name" label="券名" min-width="160" show-overflow-tooltip />
              <el-table-column prop="amount" label="面额" width="120" align="right">
                <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="minOrderAmount" label="门槛" width="120" align="right">
                <template #default="{ row }">
                  <span v-if="row.minOrderAmount && row.minOrderAmount > 0">{{ formatAmount(row.minOrderAmount) }}</span>
                  <span v-else style="color: #626f7e">无门槛</span>
                </template>
              </el-table-column>
              <el-table-column prop="limitCategoryName" label="限制分类" width="120" align="center">
                <template #default="{ row }">
                  <span v-if="row.limitCategoryName">{{ row.limitCategoryName }}</span>
                  <span v-else style="color: #626f7e">不限</span>
                </template>
              </el-table-column>
              <el-table-column prop="endTime" label="有效期" width="170">
                <template #default="{ row }">
                  <span v-if="row.endTime">{{ formatDateTime(row.endTime) }}</span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getCouponStatusTagType(row.status)" size="small">
                    {{ getCouponStatusName(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination" style="margin-top: 12px">
              <el-pagination
                v-model:current-page="couponQuery.pageNum"
                v-model:page-size="couponQuery.pageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                :total="couponTotal"
                @size-change="handleCouponSizeChange"
                @current-change="handleCouponPageChange"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="权益记录" name="benefits">
            <el-table :data="benefitList" v-loading="benefitLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="createTime" label="发放时间" width="170" />
              <el-table-column prop="sourceType" label="类型" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="getSourceTypeTagType(row.sourceType)" size="small">
                    {{ getSourceTypeName(row.sourceType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="benefitContent" label="发放内容" show-overflow-tooltip />
              <el-table-column prop="createdByName" label="操作人" width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.createdByName">{{ row.createdByName }}</span>
                  <span v-else style="color: #626f7e">系统</span>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination" style="margin-top: 12px">
              <el-pagination
                v-model:current-page="benefitQuery.pageNum"
                v-model:page-size="benefitQuery.pageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                :total="benefitTotal"
                @current-change="handleBenefitPageChange"
                @size-change="handleBenefitSizeChange"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
        <el-collapse class="customer-profile"><el-collapse-item title="客户档案与联系方式" name="profile">

        <el-descriptions :column="2" border style="margin-top: 20px">
          <el-descriptions-item label="客户姓名">{{ customerDetail.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ customerDetail.phone || '-' }}</el-descriptions-item>

          <el-descriptions-item label="客户类型">
            <el-tag v-if="customerDetail.type === 2" type="warning">VIP{{ customerDetail.vipLevel || 1 }}</el-tag>
            <el-tag v-else>普通</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="VIP到期日">
            <span :style="{ color: isVipExpiringSoon(customerDetail.vipEndDate) ? '#b42318' : '#303133' }">
              {{ customerDetail.vipEndDate ? formatDate(customerDetail.vipEndDate) : '-' }}
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="归属员工">{{ customerDetail.bindStaffName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系地址">{{ customerDetail.address || '-' }}</el-descriptions-item>

          <el-descriptions-item label="身份证号">{{ customerDetail.idCard || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近消费时间">{{ customerDetail.lastOrderTime || '-' }}</el-descriptions-item>

          <el-descriptions-item label="备注" :span="2">{{ customerDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        </el-collapse-item></el-collapse>
      </div>
    </el-drawer>

    <!-- 调额对话框 -->
    <el-dialog
      v-model="limitDialogVisible"
      title="修改赊销额度"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form ref="limitFormRef" :model="limitFormData" :rules="limitFormRules" label-width="100px">
        <el-form-item label="当前额度">
          <el-input :value="formatAmount(customerDetail?.creditLimit || 0)" disabled />
        </el-form-item>
        <el-form-item label="新额度" prop="creditLimit">
          <el-input-number
            v-model="limitFormData.creditLimit"
            :min="0"
            :precision="2"
            placeholder="请输入新额度"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="limitDialogVisible = false">取消</el-button>
        <el-button v-permission="'credit:limit:update'" type="primary" :loading="limitLoading" @click="handleLimitSubmit">确定</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="statementDialogVisible"
      :title="statementResult ? '赊销对账结果' : '生成赊销对账单'"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!statementSubmitting"
      :show-close="!statementSubmitting"
    >
      <el-descriptions v-if="statementResult" :column="1" border>
        <el-descriptions-item label="对账单号">{{ statementResult.statementNo }}</el-descriptions-item>
        <el-descriptions-item label="账期">{{ statementResult.startDate }} 至 {{ statementResult.endDate }}</el-descriptions-item>
        <el-descriptions-item label="本期赊销">{{ formatAmount(statementResult.totalCreditAmount) }}</el-descriptions-item>
        <el-descriptions-item label="本期实际还款">{{ formatAmount(statementResult.totalPaidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="期末欠款">{{ formatAmount(statementResult.totalRemainingAmount) }}</el-descriptions-item>
        <el-descriptions-item label="核对说明">{{ statementResult.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form v-else ref="statementFormRef" :model="statementFormData" :rules="statementFormRules" label-width="100px">
        <el-form-item label="客户">
          <el-input :value="customerDetail?.name || '-'" disabled />
        </el-form-item>
        <el-form-item label="账期" prop="dateRange">
          <el-date-picker
            v-model="statementFormData.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :disabled="statementSubmitting || statementValidating"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="statementFormData.remark"
            type="textarea"
            :rows="2"
            placeholder="可选：对账说明"
            :disabled="statementSubmitting || statementValidating"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="statementSubmitting || statementValidating" @click="statementDialogVisible = false">{{ statementResult ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!statementResult" v-permission="'credit:statement:generate'" type="primary" :loading="statementSubmitting || statementValidating" @click="handleGenerateStatement">
          生成
        </el-button>
      </template>
    </el-dialog>
    <!-- 订单详情抽屉 -->
    <OrderDetail v-model="orderDetailVisible" :orderId="selectedOrderId" />

    <!-- 新增/编辑客户对话框 -->
    <el-dialog
      v-model="customerDialogVisible"
      :title="customerFormData.id ? '编辑客户' : '新增客户'"
      width="600px"
      :close-on-click-modal="false"
      @close="handleCustomerDialogClose"
    >
      <el-form ref="customerFormRef" :model="customerFormData" :rules="customerFormRules" label-width="100px">
        <el-form-item label="客户姓名" prop="name">
          <el-input v-model="customerFormData.name" placeholder="请输入客户姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="customerFormData.phone" placeholder="请输入手机号" :disabled="!!customerFormData.id" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="customerFormData.idCard" placeholder="请输入身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item label="地址">
          <AddressSelector v-model="customerFormData.address" placeholder="请选择或输入地址" />
        </el-form-item>
        <el-form-item label="归属员工">
          <StaffSelector v-model="customerFormData.bindStaffId" placeholder="请选择归属员工" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="customerFormData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="customerDialogVisible = false">取消</el-button>
        <el-button v-permission="customerFormData.id ? 'customer:update' : 'customer:create'" type="primary" :loading="customerSubmitLoading" @click="handleCustomerSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {Plus, Refresh, Search, Star, TrendCharts, Wallet, Warning} from '@element-plus/icons-vue'
import {
  createCustomer,
  deleteCustomer,
  getBenefitLogs,
  getCustomerCoupons,
  getCustomerDetail,
  getCustomerList,
  getWalletTransactions,
  grantCoupon,
  rechargeCustomer,
  updateCustomer
} from '@/api/customer'
import {getStaffList} from '@/api/staff'
import CouponTemplateSelector from '@/components/CouponTemplateSelector/index.vue'
import StaffSelector from '@/components/StaffSelector/index.vue'
import AddressSelector from '@/components/AddressSelector/index.vue'
import {useUserStore} from '@/store/user'
import type {
  CustomerBenefitLogVO,
  CustomerCouponVO,
  CustomerCreateRequest,
  CustomerDetailVO,
  CustomerListVO,
  CustomerQuery,
  CustomerUpdateRequest,
  VipMonthlyRecordQuery,
  WalletTransactionQuery,
  WalletTransactionVO
} from '@/types/customer'
import type {StaffListVO} from '@/types/staff'
import type {OrderPageItem} from '@/types/order'
import type {CreditAccountItem, CreditDebtSummary, CreditRepaymentItem, CreditStatementResult} from '@/types/credit'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'
import {PAYMENT_METHOD_MAP, TRANSACTION_TYPE_MAP, WALLET_TYPE_MAP, ORDER_STATUS_MAP, ORDER_STATUS_TAG_MAP } from '@/utils/dict'
import {getOrderDisplayPaymentMethod, getOrderPaymentSummary} from '@/utils/orderPayment'
import {getOrderList} from '@/api/order'
import {
  generateCreditStatement,
  getCreditAccounts,
  getCreditRepayments,
  getCreditStatistics,
  updateCreditLimit
} from '@/api/credit'
import RepaymentDialog from './components/RepaymentDialog.vue'
import OrderDetail from '@/views/order/components/OrderDetail.vue'

const queryParams = reactive<CustomerQuery>({
  pageNum: 1,
  pageSize: 10
})

const loading = ref(false)
const tableData = ref<CustomerListVO[]>([])
const total = ref(0)
const staffOptions = ref<StaffListVO[]>([])
const userStore = useUserStore()
const ownedPermissions = computed(() => userStore.userInfo?.permissions || [])

const hasAnyOwnedPermission = (required: string[]) => {
  return required.some(permission => ownedPermissions.value.includes(permission))
}

const canViewCreditTab = computed(() =>
  hasAnyOwnedPermission([
    'credit:account:view',
    'credit:repayment:view',
    'credit:statistics:view',
    'credit:limit:update',
    'credit:statement:generate',
    'credit:repay'
  ])
)

// 充值
const rechargeVisible = ref(false)
const rechargeFormRef = ref<FormInstance>()
const rechargeLoading = ref(false)
const rechargeFormData = reactive<{
  customerId?: number
  customerName?: string
  customerPhone?: string
  amount: number
  paymentMethod: number
}>({
  customerId: undefined,
  customerName: '',
  customerPhone: '',
  amount: 0,
  paymentMethod: 1
})

const rechargeFormRules: FormRules = {
  amount: [
    { required: true, message: '请输入充值金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '充值金额必须大于0', trigger: 'blur' }
  ],
  paymentMethod: [{ required: true, message: '请选择支付方式', trigger: 'change' }]
}

// 发券
const grantCouponVisible = ref(false)
const grantCouponFormRef = ref<FormInstance>()
const grantCouponLoading = ref(false)
const grantCouponFormData = reactive<{
  customerId?: number
  customerName?: string
  customerPhone?: string
  templateId?: number
  count: number
}>({
  customerId: undefined,
  customerName: '',
  customerPhone: '',
  templateId: undefined,
  count: 1
})

const grantCouponFormRules: FormRules = {
  templateId: [{ required: true, message: '请选择优惠券模板', trigger: 'change' }],
  count: [
    { required: true, message: '请输入发放数量', trigger: 'blur' },
    { type: 'number', min: 1, message: '发放数量必须大于0', trigger: 'blur' }
  ]
}

// 详情
const detailVisible = ref(false)
let detailRequestVersion = 0
const customerDetail = ref<CustomerDetailVO>()
const detailActiveTab = ref('orders')
const transactionList = ref<WalletTransactionVO[]>([])
const transactionLoading = ref(false)
const transactionTotal = ref(0)
const transactionQuery = reactive<WalletTransactionQuery>({
  customerId: 0,
  pageNum: 1,
  pageSize: 10
})

// Orders pagination state
const ordersQuery = reactive({
  pageNum: 1,
  pageSize: 10
})
const ordersTotal = ref(0)
// Order detail drawer state
const orderDetailVisible = ref(false)
const selectedOrderId = ref<number | null>(null)

const openOrder = (orderId: number | null) => {
  selectedOrderId.value = orderId
  orderDetailVisible.value = true
}

// 订单 / 赊销 / 优惠券占位数据
const ordersList = ref<OrderPageItem[]>([])
const ordersLoading = ref(false)
const creditList = ref<CreditAccountItem[]>([])
const creditLoading = ref(false)
const creditRepaymentList = ref<CreditRepaymentItem[]>([])
const creditRepaymentLoading = ref(false)
const creditSummary = ref<CreditDebtSummary | null>(null)
const creditSummaryCustomerId = ref<number>()
let creditSummaryVersion = 0
let creditRepaymentVersion = 0
const currentCreditSummary = computed(() => creditSummaryCustomerId.value === customerDetail.value?.id ? creditSummary.value : null)
const couponList = ref<CustomerCouponVO[]>([])
const couponLoading = ref(false)
const couponTotal = ref(0)
const couponQuery = reactive<{
  customerId?: number
  status?: number
  pageNum: number
  pageSize: number
}>({
  customerId: undefined,
  status: undefined, // 默认查询所有状态
  pageNum: 1,
  pageSize: 10
})

// 权益发放记录
const benefitList = ref<CustomerBenefitLogVO[]>([])
const benefitLoading = ref(false)
const benefitTotal = ref(0)
const benefitQuery = reactive<VipMonthlyRecordQuery>({
  customerId: 0,
  pageNum: 1,
  pageSize: 10
})

// 调额对话框
const limitDialogVisible = ref(false)
const limitFormRef = ref<FormInstance>()
const limitLoading = ref(false)
const limitFormData = reactive<{
  creditLimit: number
}>({
  creditLimit: 0
})

const limitFormRules: FormRules = {
  creditLimit: [
    { required: true, message: '请输入新额度', trigger: 'blur' },
    { type: 'number', min: 0, message: '额度必须大于等于0', trigger: 'blur' }
  ]
}

const statementDialogVisible = ref(false)
const statementFormRef = ref<FormInstance>()
const statementSubmitting = ref(false)
const statementValidating = ref(false)
const statementResult = ref<CreditStatementResult | null>(null)
let statementRequestVersion = 0
const statementFormData = reactive<{
  dateRange: string[]
  remark: string
}>({
  dateRange: [],
  remark: ''
})

const statementFormRules: FormRules = {
  dateRange: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (!Array.isArray(value) || value.length !== 2 || !value[0] || !value[1]) {
          callback(new Error('请选择账期范围'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
}

// 新增/编辑客户
const customerDialogVisible = ref(false)
const customerFormRef = ref<FormInstance>()
const customerSubmitLoading = ref(false)
const customerFormData = reactive<CustomerCreateRequest & { id?: number; idCard?: string }>({
  id: undefined,
  name: '',
  phone: '',
  idCard: '',
  address: '',
  bindStaffId: undefined,
  remark: ''
})

// 身份证号验证函数
const validateIdCard = (_rule: any, value: any, callback: any) => {
  if (!value) {
    // 非必填，可以为空
    callback()
    return
  }
  // 18位身份证号：前17位数字，最后一位可以是数字或X
  const idCardPattern = /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/
  if (idCardPattern.test(value)) {
    callback()
  } else {
    callback(new Error('请输入正确的身份证号格式（18位）'))
  }
}

const customerFormRules: FormRules = {
  name: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号格式（11位数字，以1开头）',
      trigger: 'blur'
    }
  ],
  idCard: [
    { validator: validateIdCard, trigger: 'blur' }
  ]
}

// 加载员工列表
const loadStaffOptions = async () => {
  try {
    const response = await getStaffList({ pageNum: 1, pageSize: 1000 })
    staffOptions.value = response.records || []
  } catch (error) {
    console.error('加载员工列表失败:', error)
  }
}

// 加载客户列表
const loadTable = async () => {
  loading.value = true
  try {
    const response = await getCustomerList(queryParams)
    tableData.value = response.records || []
    total.value = response.total || 0
  } catch (error) {
    console.error('加载客户列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  queryParams.pageNum = 1
  loadTable()
}

// 重置
const handleReset = () => {
  queryParams.name = ''
  queryParams.phone = ''
  queryParams.type = undefined
  queryParams.bindStaffId = undefined
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  loadTable()
}

// 分页
const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  loadTable()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadTable()
}


// 加载交易记录
const loadTransactions = async () => {
  if (!transactionQuery.customerId) return
  transactionLoading.value = true
  try {
    const response = await getWalletTransactions(transactionQuery)
    transactionList.value = response.records || []
    transactionTotal.value = response.total || 0
  } catch (error) {
    console.error('加载交易记录失败:', error)
  } finally {
    transactionLoading.value = false
  }
}

const handleTransactionPageChange = (page: number) => {
  transactionQuery.pageNum = page
  loadTransactions()
}

const handleTransactionSizeChange = (size: number) => {
  transactionQuery.pageSize = size
  transactionQuery.pageNum = 1
  loadTransactions()
}

// Orders pagination handlers
const handleOrdersPageChange = (page: number) => {
  ordersQuery.pageNum = page
  loadOrders()
}

const handleOrdersSizeChange = (size: number) => {
  ordersQuery.pageSize = size
  ordersQuery.pageNum = 1
  loadOrders()
}

// 充值
const handleRecharge = (row: CustomerListVO) => {
  rechargeFormData.customerId = row.id
  rechargeFormData.customerName = row.name
  rechargeFormData.customerPhone = row.phone || ''
  rechargeFormData.amount = 0
  rechargeFormData.paymentMethod = 1
  rechargeVisible.value = true
}

const handleRechargeSubmit = () => {
  rechargeFormRef.value?.validate(async valid => {
    if (!valid) return

    // 二次确认
    try {
      await ElMessageBox.confirm(
        `确认要为客户"${rechargeFormData.customerName}"（${rechargeFormData.customerPhone}）充值 ¥${rechargeFormData.amount.toFixed(2)} 吗？`,
        '确认充值',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return // 用户取消
    }

    rechargeLoading.value = true
    try {
      const result = await rechargeCustomer({
        customerId: rechargeFormData.customerId!,
        amount: rechargeFormData.amount,
        paymentMethod: rechargeFormData.paymentMethod
      })
      ElMessage.success('充值成功')
      if (result.coupons && result.coupons.length > 0) {
        ElMessage.info(`已赠送 ${result.coupons.length} 张优惠券`)
      }
      rechargeVisible.value = false
      loadTable()
    } catch (error: any) {
      ElMessage.error(error.message || '充值失败')
    } finally {
      rechargeLoading.value = false
    }
  })
}

const handleRechargeClose = () => {
  rechargeFormRef.value?.clearValidate()
}

// 发券
const handleGrantCoupon = (row: CustomerListVO) => {
  grantCouponFormData.customerId = row.id
  grantCouponFormData.customerName = row.name
  grantCouponFormData.customerPhone = row.phone || ''
  grantCouponFormData.templateId = undefined
  grantCouponFormData.count = 1
  grantCouponVisible.value = true
}

const handleTemplateChange = () => {
  // 模板选择变化时的处理
}

const handleGrantCouponSubmit = () => {
  grantCouponFormRef.value?.validate(async valid => {
    if (!valid) return

    // 二次确认
    try {
      await ElMessageBox.confirm(
        `确认要为客户"${grantCouponFormData.customerName}"（${grantCouponFormData.customerPhone}）发放 ${grantCouponFormData.count} 张优惠券吗？`,
        '确认发券',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return // 用户取消
    }

    grantCouponLoading.value = true
    try {
      await grantCoupon({
        customerId: grantCouponFormData.customerId!,
        templateId: grantCouponFormData.templateId!,
        count: grantCouponFormData.count
      })
      ElMessage.success('发券成功')
      grantCouponVisible.value = false
    } catch (error: any) {
      ElMessage.error(error.message || '发券失败')
    } finally {
      grantCouponLoading.value = false
    }
  })
}

const handleGrantCouponClose = () => {
  grantCouponFormRef.value?.clearValidate()
}

// 字典映射
const getWalletTypeName = (type?: number) => {
  return type ? WALLET_TYPE_MAP[type] || '未知' : '-'
}

const getTransactionTypeName = (type?: number) => {
  return type ? TRANSACTION_TYPE_MAP[type] || '未知' : '-'
}

const getPaymentMethodName = (method?: number | null) => {
  if (method != null && method !== 0) {
    return PAYMENT_METHOD_MAP[method] || '未知'
  }
  return '-'
}

const getOrderNetReceivable = (order: OrderPageItem) => getOrderPaymentSummary(order).netReceivable

const getOrderReceivedAmount = (order: OrderPageItem) => getOrderPaymentSummary(order).receivedAmount

const getOrderCreditAmount = (order: OrderPageItem) => getOrderPaymentSummary(order).creditAmount

// 日期格式化
const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-'
  try {
    const date = new Date(dateStr)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  } catch {
    return dateStr
  }
}

const formatDateTime = (dateStr?: string) => {
  if (!dateStr) return '-'
  try {
    const date = new Date(dateStr)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  } catch {
    return dateStr
  }
}

// 金额格式化
const formatAmount = (val?: number | null) => {
  const num = val ?? 0
  return `￥${Number(num).toFixed(2)}`
}

// 复制文本 - unused, removed to clean linter warnings

// 检查VIP是否即将过期（30天内）
const isVipExpiringSoon = (dateStr?: string) => {
  if (!dateStr) return false
  try {
    const endDate = new Date(dateStr)
    const now = new Date()
    const diffTime = endDate.getTime() - now.getTime()
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
    return diffDays >= 0 && diffDays < 30
  } catch {
    return false
  }
}

// 新增客户
const handleAdd = () => {
  customerFormData.id = undefined
  customerFormData.name = ''
  customerFormData.phone = ''
  customerFormData.idCard = ''
  customerFormData.address = ''
  // 默认设置为当前登录员工
  customerFormData.bindStaffId = userStore.userInfo?.id
  customerFormData.remark = ''
  customerDialogVisible.value = true
}

// 编辑客户
const handleEdit = async (row: CustomerListVO) => {
  try {
    const detail = await getCustomerDetail(row.id)
    if (!detail || !detail.id) {
      throw new Error('获取客户详情失败：数据格式错误')
    }
    customerFormData.id = detail.id
    customerFormData.name = detail.name
    customerFormData.phone = detail.phone || ''
    customerFormData.idCard = detail.idCard || ''
    customerFormData.address = detail.address || ''
    customerFormData.bindStaffId = detail.bindStaffId
    customerFormData.remark = detail.remark || ''
    customerDialogVisible.value = true
  } catch (error: any) {
    console.error('编辑客户失败:', error)
    ElMessage.error(error.message || '获取客户详情失败')
  }
}

// 提交客户表单
const handleCustomerSubmit = () => {
  customerFormRef.value?.validate(async valid => {
    if (!valid) return

    customerSubmitLoading.value = true
    try {
      if (customerFormData.id) {
        // 编辑
        const updateData: CustomerUpdateRequest = {
          name: customerFormData.name,
          idCard: customerFormData.idCard || undefined,
          address: customerFormData.address,
          bindStaffId: customerFormData.bindStaffId,
          remark: customerFormData.remark
        }
        await updateCustomer(customerFormData.id, updateData)
        ElMessage.success('更新成功')
      } else {
        // 新增
        const createData: CustomerCreateRequest = {
          name: customerFormData.name,
          phone: customerFormData.phone,
          idCard: customerFormData.idCard || undefined,
          address: customerFormData.address,
          bindStaffId: customerFormData.bindStaffId,
          remark: customerFormData.remark
        }
        await createCustomer(createData)
        ElMessage.success('创建成功')
      }
      customerDialogVisible.value = false
      loadTable()
    } catch (error: any) {
      console.error('提交客户表单失败:', error)
      ElMessage.error(error.message || (customerFormData.id ? '更新失败' : '创建失败'))
    } finally {
      customerSubmitLoading.value = false
    }
  })
}

const handleCustomerDialogClose = () => {
  customerFormRef.value?.clearValidate()
}

// 加载权益发放记录
const loadBenefits = async () => {
  if (!customerDetail.value?.id) return
  benefitLoading.value = true
  try {
    const response = await getBenefitLogs(
      customerDetail.value.id,
      benefitQuery.pageNum || 1,
      benefitQuery.pageSize || 10
    )
    benefitList.value = response.records || []
    benefitTotal.value = response.total || 0
  } catch (error) {
    console.error('加载权益发放记录失败:', error)
  } finally {
    benefitLoading.value = false
  }
}

const handleBenefitPageChange = (page: number) => {
  benefitQuery.pageNum = page
  loadBenefits()
}

const handleBenefitSizeChange = (size: number) => {
  benefitQuery.pageSize = size
  benefitQuery.pageNum = 1
  loadBenefits()
}

// 查看详情时加载权益发放记录
const handleViewDetail = async (row: CustomerListVO) => {
  const customerId = row.id
  const version = ++detailRequestVersion
  detailVisible.value = true
  customerDetail.value = undefined
  detailActiveTab.value = 'orders'
  transactionQuery.customerId = customerId
  transactionQuery.pageNum = 1
  transactionList.value = []
  transactionTotal.value = 0
  ordersQuery.pageNum = 1
  ordersList.value = []
  ordersTotal.value = 0
  creditQuery.pageNum = 1
  creditQuery.status = undefined
  creditList.value = []
  creditRepaymentList.value = []
  creditTotal.value = 0
  couponQuery.pageNum = 1
  couponQuery.status = undefined
  couponList.value = []
  couponTotal.value = 0
  benefitQuery.pageNum = 1
  benefitList.value = []
  benefitTotal.value = 0
  try {
    const detail = await getCustomerDetail(customerId)
    if (version !== detailRequestVersion || !detailVisible.value) return
    if (!detail || detail.id !== customerId) {
      throw new Error('获取客户详情失败：数据格式错误')
    }
    customerDetail.value = detail
    if (detailActiveTab.value === 'credit' && !canViewCreditTab.value) {
      detailActiveTab.value = 'transactions'
    }
    loadDetailTabData(detailActiveTab.value)
  } catch (error: any) {
    if (version !== detailRequestVersion || !detailVisible.value) return
    console.error('加载客户详情失败:', error)
    ElMessage.error(error.message || '加载客户详情失败')
    detailVisible.value = false
  }
}

// 删除客户
const handleDelete = async (row: CustomerListVO) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除客户"${row.name}"（${row.phone || ''}）吗？删除后无法恢复。若客户仍有余额、欠款或进行中的订单/售后/积分兑换将无法删除。`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteCustomer(row.id)
    ElMessage.success('删除成功')
    loadTable()
  } catch (error: any) {
    if (error !== 'cancel') {
      // 显示后端返回的错误消息
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 调额
const handleEditLimit = () => {
  if (!customerDetail.value) return
  limitFormData.creditLimit = customerDetail.value.creditLimit || 0
  limitDialogVisible.value = true
}

const handleLimitSubmit = () => {
  limitFormRef.value?.validate(async valid => {
    if (!valid || !customerDetail.value) return

    limitLoading.value = true
    try {
      await updateCreditLimit(customerDetail.value.id, limitFormData.creditLimit)
      ElMessage.success('额度调整成功')
      limitDialogVisible.value = false
      // 刷新客户详情
      const detail = await getCustomerDetail(customerDetail.value.id)
      if (detail && detail.id) {
        customerDetail.value = detail
      }
    } catch (error: any) {
      ElMessage.error(error.message || '额度调整失败')
    } finally {
      limitLoading.value = false
    }
  })
}

// 还款弹窗控制
const repaymentVisible = ref(false)
const handleRepayClick = () => {
  if (!customerDetail.value || !customerDetail.value.id) return
  repaymentVisible.value = true
}

const onRepaymentSuccess = async () => {
  if (!customerDetail.value || !customerDetail.value.id) return
  try {
    const detail = await getCustomerDetail(customerDetail.value.id)
    if (detail && detail.id) {
      customerDetail.value = detail
    }
  } catch (err) {
    console.error('刷新客户详情失败', err)
  }
  transactionQuery.customerId = customerDetail.value.id
  transactionQuery.pageNum = 1
  loadTransactions()
  refreshCreditTab()
}

const loadOrders = async () => {
  if (!customerDetail.value?.id) return
  ordersLoading.value = true
  try {
    const params = {
      pageNum: ordersQuery.pageNum,
      pageSize: ordersQuery.pageSize,
      customerId: customerDetail.value.id
    }
    const res = await getOrderList(params)
    const page = res && res.records ? res : null
    if (page) {
      ordersList.value = page.records || []
      ordersTotal.value = page.total ?? page.totalRow ?? 0
    } else {
      ordersList.value = []
      ordersTotal.value = 0
    }
  } catch (err) {
    console.error('加载消费订单失败', err)
    ordersList.value = []
  } finally {
    ordersLoading.value = false
  }
}

// 加载赊销/欠款单据（服务端分页）
const creditTotal = ref(0)
let creditAccountVersion = 0
const loadCredit = async () => {
  if (!customerDetail.value?.id) return
  const customerId = customerDetail.value.id
  const query = {...creditQuery}
  const version = ++creditAccountVersion
  const isCurrent = () => version === creditAccountVersion && customerDetail.value?.id === customerId
    && creditQuery.pageNum === query.pageNum && creditQuery.pageSize === query.pageSize && creditQuery.status === query.status
  creditLoading.value = true
  creditList.value = []
  creditTotal.value = 0
  try {
    const page = await getCreditAccounts(customerId, query.pageNum, query.pageSize, query.status)
    if (!isCurrent()) return
    creditList.value = page.records || []
    creditTotal.value = page.total ?? page.totalRow ?? 0
  } catch (error) {
    if (!isCurrent()) return
    console.error('加载赊销单据失败', error)
    creditList.value = []
    creditTotal.value = 0
  } finally {
    if (version === creditAccountVersion) creditLoading.value = false
  }
}

const loadCreditRepayments = async () => {
  if (!customerDetail.value?.id || !hasAnyOwnedPermission(['credit:repayment:view'])) return
  const customerId = customerDetail.value.id
  const version = ++creditRepaymentVersion
  creditRepaymentLoading.value = true
  creditRepaymentList.value = []
  try {
    const res = await getCreditRepayments(customerId)
    if (version !== creditRepaymentVersion || customerDetail.value?.id !== customerId) return
    creditRepaymentList.value = Array.isArray(res) ? res : []
  } catch (error) {
    if (version !== creditRepaymentVersion || customerDetail.value?.id !== customerId) return
    console.error('加载还款记录失败', error)
    creditRepaymentList.value = []
  } finally {
    if (version === creditRepaymentVersion) creditRepaymentLoading.value = false
  }
}
const loadCreditSummary = async () => {
  if (!customerDetail.value?.id || !hasAnyOwnedPermission(['credit:statistics:view', 'credit:repay'])) return
  const customerId = customerDetail.value.id
  const version = ++creditSummaryVersion
  creditSummary.value = null
  creditSummaryCustomerId.value = customerId
  try {
    const summary = await getCreditStatistics(customerId)
    if (version === creditSummaryVersion && customerDetail.value?.id === customerId) creditSummary.value = summary
  } catch (error: any) {
    if (version === creditSummaryVersion && customerDetail.value?.id === customerId) {
      ElMessage.error(error?.message || '读取本店赊销统计失败')
    }
  }
}

// credit account status mapping (domain: 1-未还清,2-已还清,3-已逾期)
const CREDIT_ACCOUNT_STATUS_MAP: Record<number, string> = {
  1: '未还清',
  2: '已还清',
  3: '已逾期'
}
const CREDIT_REPAYMENT_METHOD_MAP: Record<number, string> = {
  1: '现金',
  2: '微信',
  3: '支付宝',
  4: '银行转账'
}
// server-side pagination for creditList
const creditQuery = reactive<{ pageNum: number; pageSize: number; status?: number }>({
  pageNum: 1,
  pageSize: 10,
  status: undefined
})

const handleCreditPageChange = (page: number) => {
  creditQuery.pageNum = page
  loadCredit()
}

const handleCreditSizeChange = (size: number) => {
  creditQuery.pageSize = size
  creditQuery.pageNum = 1
  loadCredit()
}

const handleCreditFilterChange = () => {
  creditQuery.pageNum = 1
  loadCredit()
}

const refreshCreditTab = () => {
  if (hasAnyOwnedPermission(['credit:account:view'])) void loadCredit()
  void loadCreditRepayments()
  void loadCreditSummary()
}

const getCreditRepaymentMethodName = (method?: number) => {
  if (!method) return '-'
  return CREDIT_REPAYMENT_METHOD_MAP[method] || `方式${method}`
}

const loadCoupons = async () => {
  if (!customerDetail.value?.id) return
  couponLoading.value = true
  try {
    const response = await getCustomerCoupons(
      customerDetail.value.id,
      couponQuery.status,
      couponQuery.pageNum || 1,
      couponQuery.pageSize || 10
    )
    couponList.value = response.records || []
    couponTotal.value = response.total || 0
  } catch (error) {
    console.error('加载优惠券列表失败:', error)
  } finally {
    couponLoading.value = false
  }
}

const handleCouponPageChange = (page: number) => {
  couponQuery.pageNum = page
  loadCoupons()
}

const handleCouponSizeChange = (size: number) => {
  couponQuery.pageSize = size
  couponQuery.pageNum = 1
  loadCoupons()
}

const buildCurrentMonthRange = () => {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), 1)
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0)
  const format = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }
  return [format(start), format(end)]
}

const handleOpenStatementDialog = () => {
  if (!customerDetail.value?.id) return
  ++statementRequestVersion
  statementResult.value = null
  statementFormData.dateRange = buildCurrentMonthRange()
  statementFormData.remark = ''
  statementDialogVisible.value = true
}

const handleGenerateStatement = async () => {
  if (statementSubmitting.value || statementValidating.value || statementResult.value || !statementFormRef.value
      || !customerDetail.value?.id || !statementDialogVisible.value) return
  const data = {customerId: customerDetail.value.id, startDate: statementFormData.dateRange[0],
    endDate: statementFormData.dateRange[1], remark: statementFormData.remark?.trim() || undefined}
  const version = ++statementRequestVersion
  statementValidating.value = true
  try {
    await statementFormRef.value.validate(async (valid) => {
      if (!valid || statementSubmitting.value || version !== statementRequestVersion || !statementDialogVisible.value
          || customerDetail.value?.id !== data.customerId || statementFormData.dateRange[0] !== data.startDate
          || statementFormData.dateRange[1] !== data.endDate || (statementFormData.remark?.trim() || undefined) !== data.remark) return
      statementSubmitting.value = true
      try {
        const result = await generateCreditStatement(data)
        if (version !== statementRequestVersion || customerDetail.value?.id !== data.customerId || !statementDialogVisible.value) return
        statementResult.value = result
        ElMessage.success('对账单已生成')
      } catch (error: any) {
        if (version === statementRequestVersion && customerDetail.value?.id === data.customerId && statementDialogVisible.value) {
          ElMessage.error(error.message || '生成对账单失败')
        }
      } finally {
        if (version === statementRequestVersion) statementSubmitting.value = false
      }
    })
  } finally {
    statementValidating.value = false
  }
}

const loadDetailTabData = (tab: string) => {
  if (!customerDetail.value?.id) return
  if (tab === 'orders') {
    loadOrders()
    return
  }
  if (tab === 'credit') {
    refreshCreditTab()
    return
  }
  if (tab === 'coupons') {
    loadCoupons()
    return
  }
  if (tab === 'benefits') {
    loadBenefits()
  }
}

// 监听详情Tab切换
watch(detailActiveTab, (newTab) => {
  if (newTab === 'credit' && !canViewCreditTab.value) {
    detailActiveTab.value = 'transactions'
    return
  }
  loadDetailTabData(newTab)
})

// 权益流水来源类型映射
const BENEFIT_SOURCE_TYPE_MAP: Record<number, string> = {
  1: '每月权益',
  2: '充值奖励',
  3: '手动发放'
}

const getSourceTypeName = (type: number) => {
  return BENEFIT_SOURCE_TYPE_MAP[type] || '未知'
}

const getSourceTypeTagType = (type: number) => {
  if (type === 1) return 'success'
  if (type === 2) return 'warning'
  if (type === 3) return 'info'
  return ''
}

// 优惠券状态映射
const COUPON_STATUS_MAP: Record<number, string> = {
  1: '未使用',
  2: '已使用',
  3: '已过期',
  4: '已作废'
}

const getCouponStatusName = (status: number) => {
  return COUPON_STATUS_MAP[status] || '未知'
}

const getCouponStatusTagType = (status: number) => {
  if (status === 1) return 'success'
  if (status === 2) return 'info'
  if (status === 3) return 'warning'
  if (status === 4) return 'danger'
  return ''
}

// 初始化
loadStaffOptions()
loadTable()
  // ensure when order detail drawer closes we clear selected id
  watch(orderDetailVisible, (val) => {
    if (!val) {
      selectedOrderId.value = null
    }
  })
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}

.search-card {
  margin-bottom: 12px;
}

.table-card {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* 指标卡片样式 */
.metrics-row {
  margin-bottom: 20px;
}

.metric-card {
  padding: 16px;
  border-radius: 8px;
  transition: all 0.3s;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
}

.metric-card-blue {
  background: #fff;
}

.metric-card-red {
  background: #fff;
}

.metric-card-green {
  background: #fff;
}

.metric-card-orange {
  background: #fff;
}

.metric-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  width: 100%;
}

.metric-icon {
  font-size: 20px;
  margin-right: 8px;
  color: #606266;
}

.metric-title {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
  display: flex;
  align-items: center;
}

.metric-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
  line-height: 1.2;
  text-align: center;
  width: 100%;
}

.metric-sub {
  font-size: 12px;
  color: #626f7e;
  line-height: 1.5;
  text-align: center;
  width: 100%;
}

.detail-tabs {
  margin-top: 20px;
}

.credit-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.credit-summary {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #606266;
  font-size: 13px;
}

.credit-summary strong {
  color: #f56c6c;
}

.credit-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.credit-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

/* Prevent layout shift when vertical scrollbar appears/disappears in drawer */
.el-drawer__body {
  overflow-y: scroll; /* always show scrollbar to avoid layout shift */
  box-sizing: border-box;
}

/* Ensure metric cards and tables use full width and consistent box-sizing */
.metrics-row, .table-card, .metric-card {
  box-sizing: border-box;
  width: 100%;
}
</style>

<style scoped>
.metric-card { cursor: default; min-height: 100px; padding: 0; }
.metric-card :deep(.el-card__body) { width: 100%; box-sizing: border-box; }
.metric-card:hover { transform: none; }
.customer-profile { margin-top: 20px; }
</style>

<style scoped>
.customer-order-link { max-width: 160px; vertical-align: middle; }
.customer-order-link :deep(.el-link__inner) { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>

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
          <el-button type="success" :icon="Plus" @click="handleAdd">新增客户</el-button>
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
              <div style="font-size: 12px; color: #909399">{{ row.phone || '-' }}</div>
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
        <el-table-column label="三钱包资产" min-width="200">
          <template #default="{ row }">
            <div style="line-height: 1.8">
              <div>
                <span style="color: #606266">本金：</span>
                <span style="font-weight: 500">¥{{ (row.balance || 0).toFixed(2) }}</span>
              </div>
              <div>
                <span style="color: #606266">专用金：</span>
                <span style="font-weight: 500">¥{{ (row.specialFundBalance || 0).toFixed(2) }}</span>
                <span v-if="row.frozenSpecialFund && row.frozenSpecialFund > 0" style="color: #f56c6c; font-size: 12px; margin-left: 4px">
                  (冻结: ¥{{ row.frozenSpecialFund.toFixed(2) }})
                </span>
              </div>
              <div>
                <span style="color: #606266">积分：</span>
                <span style="font-weight: 500">{{ row.points || 0 }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="VIP有效期" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.type === 2 && row.vipEndDate" :style="{ color: isVipExpiringSoon(row.vipEndDate) ? '#f56c6c' : '#606266' }">
              {{ formatDate(row.vipEndDate) }}
              <span v-if="isVipExpiringSoon(row.vipEndDate)" style="font-size: 12px; display: block; margin-top: 2px">即将过期</span>
            </span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="bindStaffName" label="归属员工" width="120" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="handleRecharge(row)">充值</el-button>
            <el-button link type="warning" @click="handleGrantCoupon(row)">发券</el-button>
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" v-permission="'customer:delete'" @click="handleDelete(row)">删除</el-button>
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
        <el-button type="primary" :loading="rechargeLoading" @click="handleRechargeSubmit">确定</el-button>
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
        <el-button type="primary" :loading="grantCouponLoading" @click="handleGrantCouponSubmit">确定</el-button>
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
    <el-drawer v-model="detailVisible" :title="`客户详情${customerDetail?.code ? ' - ' + customerDetail.code : ''}`" size="60%">
      <div v-if="customerDetail">
        <!-- 核心指标卡片 -->
        <el-row :gutter="12" class="metrics-row">
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
            <el-card shadow="hover" class="metric-card metric-card-red">
              <div class="metric-header">
                <el-icon class="metric-icon"><Warning /></el-icon>
                <div class="metric-title">
                  当前欠款
                  
                </div>
               
              </div>
              <div class="metric-value" :style="{ color: (customerDetail.totalDebt || 0) > 0 ? '#f56c6c' : '#303133' }">
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
    [调额]
  </el-button>

  <!-- 按钮 2：还款 (直接把 v-if 加在 button 上，去掉外层 div) -->
  <el-button
    v-if="customerDetail && customerDetail.totalDebt != null && customerDetail.totalDebt > 0"
    link
    type="primary"
    size="small"
    style="padding: 0"
    @click="handleRepayClick"
  >
    [还款]
  </el-button>
  
</div>
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

        <!-- 详细档案信息 -->
        <el-descriptions :column="2" border style="margin-top: 20px">
          <el-descriptions-item label="客户姓名">{{ customerDetail.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ customerDetail.phone || '-' }}</el-descriptions-item>

          <el-descriptions-item label="客户类型">
            <el-tag v-if="customerDetail.type === 2" type="warning">VIP{{ customerDetail.vipLevel || 1 }}</el-tag>
            <el-tag v-else>普通</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="VIP到期日">
            <span :style="{ color: isVipExpiringSoon(customerDetail.vipEndDate) ? '#f56c6c' : '#303133' }">
              {{ customerDetail.vipEndDate ? formatDate(customerDetail.vipEndDate) : '-' }}
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="归属员工">{{ customerDetail.bindStaffName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系地址">{{ customerDetail.address || '-' }}</el-descriptions-item>

          <el-descriptions-item label="身份证号">{{ customerDetail.idCard || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近消费时间">{{ customerDetail.lastOrderTime || '-' }}</el-descriptions-item>

          <el-descriptions-item label="备注" :span="2">{{ customerDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 业务记录 Tabs -->
        <el-tabs v-model="detailActiveTab" class="detail-tabs">
          <el-tab-pane label="消费订单" name="orders">
      
            <el-table :data="ordersList" v-loading="ordersLoading" border>
              <el-table-column type="index" label="#" width="60" />
              <el-table-column label="订单号" min-width="160">
                <template #default="{ row }">
                  <el-link type="primary" underline @click.prevent="openOrder(row.id)">
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
              <el-table-column prop="payAmount" label="金额" width="120" align="right">
                <template #default="{ row }">{{ formatAmount(row.payAmount || row.totalAmount || 0) }}</template>
              </el-table-column>
              <el-table-column prop="paymentMethod" label="支付方式" width="140">
                <template #default="{ row }">
                  <span v-if="row.paymentMethod || (row.payments && row.payments.length)">
                    {{ getPaymentMethodName(row.paymentMethod ?? (row.payments && row.payments.length ? row.payments[0].paymentType : undefined)) }}
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
                  <span v-else style="color: #909399">-</span>
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
                  <span v-else style="color: #909399">系统</span>
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

          <el-tab-pane label="赊销/还款" name="credit">
            
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
              <el-table-column prop="creditAmount" label="欠款金额" width="140" align="right">
                <template #default="{ row }">{{ formatAmount(row.creditAmount || 0) }}</template>
              </el-table-column>
              <el-table-column label="还款进度" width="220">
                <template #default="{ row }">
                  已还: {{ formatAmount(row.paidAmount || 0) }} / 剩余: {{ formatAmount(row.remainingAmount || 0) }}
                </template>
              </el-table-column>
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
                  <span v-else style="color: #909399">无门槛</span>
                </template>
              </el-table-column>
              <el-table-column prop="limitCategoryName" label="限制分类" width="120" align="center">
                <template #default="{ row }">
                  <span v-if="row.limitCategoryName">{{ row.limitCategoryName }}</span>
                  <span v-else style="color: #909399">不限</span>
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
                  <span v-else style="color: #909399">系统</span>
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
        <el-button type="primary" :loading="limitLoading" @click="handleLimitSubmit">确定</el-button>
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
        <el-button type="primary" :loading="customerSubmitLoading" @click="handleCustomerSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {reactive, ref, watch} from 'vue'
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
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage, ElMessageBox} from 'element-plus'
import {PAYMENT_METHOD_MAP, TRANSACTION_TYPE_MAP, WALLET_TYPE_MAP, ORDER_STATUS_MAP, ORDER_STATUS_TAG_MAP } from '@/utils/dict'
import {updateCreditLimit} from '@/api/credit'
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
const customerDetail = ref<CustomerDetailVO>()
const detailActiveTab = ref('transactions')
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
const ordersList = ref<any[]>([])
const ordersLoading = ref(false)
const creditList = ref<any[]>([])
const creditLoading = ref(false)
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
        try {
          const result = await createCustomer(createData)
          // 如果返回结果有 id，说明创建成功
          if (result && result.id) {
            ElMessage.success('创建成功')
            customerDialogVisible.value = false
            loadTable()
          } else {
            // 如果返回结果没有 id，可能是响应格式问题，但数据可能已创建
            ElMessage.warning('客户可能已创建，请刷新列表查看')
            customerDialogVisible.value = false
            loadTable()
          }
        } catch (err: any) {
          // 如果错误消息包含"已存在"、"重复"或"请求失败"，说明可能是重复提交或响应格式问题
          if (err.message && (err.message.includes('已存在') || err.message.includes('重复') || err.message.includes('请求失败'))) {
            ElMessage.warning('客户可能已创建，请刷新列表查看')
            customerDialogVisible.value = false
            loadTable()
          } else {
            ElMessage.error(err.message || '创建失败')
          }
        }
      }
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
  detailVisible.value = true
  try {
    const detail = await getCustomerDetail(row.id)
    if (!detail || !detail.id) {
      throw new Error('获取客户详情失败：数据格式错误')
    }
    customerDetail.value = detail
    transactionQuery.customerId = row.id
    transactionQuery.pageNum = 1
    loadTransactions()
    // benefitQuery 不再需要，直接使用 customerDetail.value.id
    benefitQuery.pageNum = 1
    loadBenefits()
  } catch (error: any) {
    console.error('加载客户详情失败:', error)
    ElMessage.error(error.message || '加载客户详情失败')
    detailVisible.value = false
  }
}

// 删除客户
const handleDelete = async (row: CustomerListVO) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除客户"${row.name}"（${row.phone || ''}）吗？删除后无法恢复。若客户有余额或欠款将无法删除。`,
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
  // 刷新赊销 Tab（占位）
  loadCredit()
}

// 加载消费订单（对接后端分页接口）
import { getOrderList } from '@/api/order'
import { getCreditAccounts } from '@/api/credit'

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
    const page = (res && res.records) ? res : (res && res.data && res.data.records ? res.data : null)
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
const loadCredit = async () => {
  if (!customerDetail.value?.id) return
  creditLoading.value = true
  try {
    const res = await getCreditAccounts(customerDetail.value.id, creditQuery.pageNum, creditQuery.pageSize, undefined)
    const page = (res && res.records) ? res : (res && res.data && res.data.records ? res.data : null)
    if (page) {
      creditList.value = page.records || []
      creditTotal.value = page.total ?? page.totalRow ?? 0
    } else {
      creditList.value = []
      creditTotal.value = 0
    }
  } catch (err) {
    console.error('加载赊销单据失败', err)
    creditList.value = []
    creditTotal.value = 0
  } finally {
    creditLoading.value = false
  }
}

// credit account status mapping (domain: 1-未还清,2-已还清,3-已逾期)
const CREDIT_ACCOUNT_STATUS_MAP: Record<number, string> = {
  1: '未还清',
  2: '已还清',
  3: '已逾期'
}
// server-side pagination for creditList
const creditQuery = reactive({ pageNum: 1, pageSize: 10 })

const handleCreditPageChange = (page: number) => {
  creditQuery.pageNum = page
  loadCredit()
}

const handleCreditSizeChange = (size: number) => {
  creditQuery.pageSize = size
  creditQuery.pageNum = 1
  loadCredit()
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

// 监听详情Tab切换
watch(detailActiveTab, (newTab) => {
  if (newTab === 'benefits' && customerDetail.value?.id && benefitList.value.length === 0) {
    loadBenefits()
  }
  if (newTab === 'orders' && customerDetail.value?.id) {
    loadOrders()
  }
  if (newTab === 'credit' && customerDetail.value?.id) {
    loadCredit()
  }
  if (newTab === 'coupons' && customerDetail.value?.id) {
    loadCoupons()
  }
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
  background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
}

.metric-card-red {
  background: linear-gradient(135deg, #ffebee 0%, #ffcdd2 100%);
}

.metric-card-green {
  background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
}

.metric-card-orange {
  background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
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
  color: #909399;
  line-height: 1.5;
  text-align: center;
  width: 100%;
}

.detail-tabs {
  margin-top: 20px;
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


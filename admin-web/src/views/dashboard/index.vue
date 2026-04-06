<template>
  <div class="dashboard-container">
    <el-row :gutter="20">
      <el-col :span="6" v-for="stat in stats" :key="stat.title">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: stat.color }">
              <el-icon :size="30">
                <component :is="stat.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-title">{{ stat.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="welcome-card" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>欢迎使用农资管理系统</span>
        </div>
      </template>
      <div class="welcome-content">
        <p>当前登录用户：<strong>{{ userStore.userInfo?.name }}</strong></p>
        <p>员工ID：<strong>{{ userStore.userInfo?.id }}</strong></p>
        <p>角色：<strong>{{ userStore.userInfo?.role || '未分配' }}</strong></p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import {useUserStore} from '@/store/user'
import {Document, Money, ShoppingCart, User} from '@element-plus/icons-vue'

const userStore = useUserStore()

const stats = ref([
  {
    title: '今日订单',
    value: '0',
    icon: ShoppingCart,
    color: '#409eff'
  },
  {
    title: '今日客户',
    value: '0',
    icon: User,
    color: '#67c23a'
  },
  {
    title: '今日销售额',
    value: '¥0.00',
    icon: Money,
    color: '#e6a23c'
  },
  {
    title: '待处理事项',
    value: '0',
    icon: Document,
    color: '#f56c6c'
  }
])
</script>

<style lang="scss" scoped>
.dashboard-container {
  .stat-card {
    .stat-content {
      display: flex;
      align-items: center;
      gap: 20px;

      .stat-icon {
        width: 60px;
        height: 60px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
      }

      .stat-info {
        flex: 1;

        .stat-value {
          font-size: 28px;
          font-weight: 600;
          color: #303133;
          margin-bottom: 8px;
        }

        .stat-title {
          font-size: 14px;
          color: #909399;
        }
      }
    }
  }

  .welcome-card {
    .welcome-content {
      p {
        margin: 10px 0;
        font-size: 14px;
        color: #606266;

        strong {
          color: #303133;
        }
      }
    }
  }
}
</style>




<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '200px'" class="sidebar-container">
      <div class="logo">
        <span v-if="!isCollapse">农资管理系统</span>
        <span v-else>农资</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :unique-opened="true"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <el-menu-item
            v-if="!route.children || route.children.length === 0"
            :index="getFullPath(route)"
          >
            <el-icon v-if="getIcon(route.meta?.icon)">
              <component :is="getIcon(route.meta?.icon)" />
            </el-icon>
            <template #title>{{ route.meta?.title }}</template>
          </el-menu-item>
          
          <el-sub-menu
            v-else
            :index="getFullPath(route)"
          >
            <template #title>
              <el-icon v-if="getIcon(route.meta?.icon)">
                <component :is="getIcon(route.meta?.icon)" />
              </el-icon>
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in route.children"
              :key="child.path"
              :index="getFullPath(child, route)"
            >
              <el-icon v-if="getIcon(child.meta?.icon)">
                <component :is="getIcon(child.meta?.icon)" />
              </el-icon>
              <template #title>{{ child.meta?.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="navbar">
        <div class="navbar-left">
          <el-icon class="collapse-icon" @click="toggleCollapse">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
        </div>
        <div class="navbar-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span>{{ userStore.userInfo?.name || '管理员' }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessageBox} from 'element-plus'
import {
  ArrowDown,
  Avatar,
  Box,
  Connection,
  Document,
  Goods,
  Histogram,
  List,
  Menu,
  Money,
  Odometer,
  Promotion,
  Setting,
  Shop,
  ShoppingCart,
  Star,
  Ticket,
  User,
  UserFilled,
  Van
} from '@element-plus/icons-vue'
import {useUserStore} from '@/store/user'
import {usePermissionStore} from '@/store/permission'

// 图标映射表
const iconMap: Record<string, any> = {
  Odometer,
  Money,
  Goods,
  Menu,
  Box,
  Truck: Van, // Truck 图标不存在，使用 Van 替代
  Histogram,
  ShoppingCart,
  Document,
  Ticket,
  Promotion,
  Star,
  Setting,
  Shop,
  Avatar,
  UserFilled,
  List,
  User,
  Connection // 客户营销使用 Connection 图标
}

// 获取图标组件
const getIcon = (iconName: string | undefined) => {
  if (!iconName) return null
  return iconMap[iconName] || null
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const isCollapse = ref(false)

// 当前激活的菜单
const activeMenu = computed(() => route.path)

// 菜单路由列表（用于侧边栏渲染）
const menuRoutes = computed(() => {
  // 获取根路由（path 为 '/' 的路由）的子路由
  const rootRoute = router.getRoutes().find(r => r.path === '/')
  if (rootRoute && rootRoute.children) {
    return rootRoute.children.filter(r => !r.meta?.hidden)
  }
  return []
})

// 获取完整路径（确保以 / 开头）
const getFullPath = (route: any, parent?: any) => {
  // 如果路由路径已经以 / 开头，直接返回
  if (route.path && route.path.startsWith('/')) {
    return route.path
  }
  // 如果有父路由，拼接父路径和子路径
  if (parent) {
    // 如果父路由是根路径，直接拼接子路由
    if (parent.path === '/') {
      return `/${route.path}`
    }
    // 如果父路由不是根路径，需要拼接（注意：父路由可能已经有 / 前缀）
    const parentPath = parent.path.startsWith('/') ? parent.path : `/${parent.path}`
    return `${parentPath}/${route.path}`
  }
  // 没有父路由，直接返回（确保以 / 开头）
  return route.path && route.path.startsWith('/') ? route.path : `/${route.path}`
}

// 切换侧边栏折叠
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

// 处理下拉菜单命令
const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await userStore.logout()
    } catch (error) {
      // 用户取消
    }
  }
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
}

.sidebar-container {
  background-color: #304156;
  transition: width 0.28s;

  .logo {
    height: 60px;
    line-height: 60px;
    text-align: center;
    color: #fff;
    font-size: 18px;
    font-weight: 600;
    background-color: #2b3a4a;
  }

  .el-menu {
    border-right: none;
    height: calc(100vh - 60px);
    overflow-y: auto;
  }
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);

  .navbar-left {
    .collapse-icon {
      font-size: 20px;
      cursor: pointer;
      color: #5a5e66;
    }
  }

  .navbar-right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      color: #5a5e66;

      &:hover {
        color: #409eff;
      }
    }
  }
}

.app-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>


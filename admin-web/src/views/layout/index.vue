<template>
  <div class="layout-wrapper" :class="{ 'sidebar-collapsed': isCollapse }">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapse }">
      <!-- Logo -->
      <div class="sidebar-logo" @click="router.push('/dashboard')">
        <div class="logo-icon">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="logoGrad" x1="0" y1="0" x2="32" y2="32">
                <stop offset="0%" stop-color="#7ea76d" />
                <stop offset="100%" stop-color="#1d5d73" />
              </linearGradient>
            </defs>
            <rect width="32" height="32" rx="8" fill="url(#logoGrad)" />
            <path d="M16 7c-1.5 2-5 6-5 11a5 5 0 0 0 10 0c0-5-3.5-9-5-11z" fill="#fff" opacity="0.9"/>
            <path d="M10 17c2-3 5-5 8-4s5 4 5 4" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity="0.6" fill="none"/>
          </svg>
        </div>
        <transition name="fade-text">
          <span v-if="!isCollapse" class="logo-text">AgriSys</span>
        </transition>
      </div>

      <!-- 菜单 -->
      <div class="sidebar-menu-wrap">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="true"
          :unique-opened="false"
          :default-openeds="defaultOpeneds"
          router
          background-color="transparent"
          text-color="rgba(255,255,255,0.65)"
          active-text-color="#fff"
        >
          <template v-for="route in menuRoutes" :key="route.key">
            <el-menu-item
              v-if="!route.children || route.children.length === 0"
              :index="route.path || route.key"
            >
              <el-icon v-if="getIcon(route.icon)">
                <component :is="getIcon(route.icon)" />
              </el-icon>
              <template #title>{{ route.title }}</template>
            </el-menu-item>

            <el-sub-menu v-else :index="route.key">
              <template #title>
                <el-icon v-if="getIcon(route.icon)">
                  <component :is="getIcon(route.icon)" />
                </el-icon>
                <span>{{ route.title }}</span>
              </template>
              <el-menu-item
                v-for="child in route.children"
                :key="child.key"
                :index="child.path || child.key"
              >
                <el-icon v-if="getIcon(child.icon)">
                  <component :is="getIcon(child.icon)" />
                </el-icon>
                <template #title>{{ child.title }}</template>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </div>

      <!-- 折叠按钮（纯图标） -->
      <div class="sidebar-toggle" @click="toggleCollapse" :title="isCollapse ? '展开菜单' : '收起菜单'">
        <svg class="toggle-icon" :class="{ flipped: isCollapse }" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.5"/>
          <line x1="9" y1="4" x2="9" y2="20" stroke="currentColor" stroke-width="1.5"/>
          <path d="M14 10l-2 2 2 2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main-area">
      <!-- 顶栏 -->
      <header class="top-bar">
        <div class="top-bar-left">
          <!-- 面包屑占位 -->
        </div>
        <div class="top-bar-right">
          <el-tooltip v-if="canReadNotifications" content="站内信" placement="bottom" :show-after="400">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notification-badge" @click="goNotification">
              <div class="top-icon-btn">
                <el-icon :size="18"><Bell /></el-icon>
              </div>
            </el-badge>
          </el-tooltip>
          <el-dropdown @command="handleCommand" trigger="click">
            <span class="user-info">
              <el-avatar :size="30" class="user-avatar">
                {{ (userStore.userInfo?.name || '管')[0] }}
              </el-avatar>
              <span class="user-name">{{ userStore.userInfo?.name || '管理员' }}</span>
              <el-icon class="arrow-icon"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="myPerformance">
                  <el-icon><Trophy /></el-icon>
                  我的绩效
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><Switch /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 标签栏 -->
      <div class="tabs-bar" v-if="tabsStore.tabs.length > 0">
        <div class="tabs-scroll">
          <div
            v-for="tab in tabsStore.tabs"
            :key="tab.path"
            class="tab-item"
            :class="{ active: tabsStore.activeTab === tab.path }"
            @click="switchTab(tab.path)"
            @contextmenu.prevent="showTabMenu($event, tab)"
          >
            <span class="tab-title">{{ tab.title }}</span>
            <span
              v-if="!tab.affix"
              class="tab-close"
              @click.stop="tabsStore.removeTab(tab.path, router)"
            >
              <svg viewBox="0 0 12 12" fill="none"><path d="M3.5 3.5l5 5M8.5 3.5l-5 5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>
            </span>
          </div>
        </div>

        <!-- 右键菜单 -->
        <teleport to="body">
          <div
            v-if="tabMenuVisible"
            class="tab-context-menu"
            :style="{ left: tabMenuX + 'px', top: tabMenuY + 'px' }"
          >
            <div @click="closeCurrentTab">关闭</div>
            <div @click="closeOthersTab">关闭其他</div>
            <div @click="closeAllTabs">关闭所有</div>
          </div>
        </teleport>
      </div>

      <!-- 内容区 -->
      <main class="content-area">
        <router-view v-slot="{ Component }">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="$route.path" />
          </keep-alive>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Avatar,
  Bell,
  Box,
  ChatDotRound,
  Coin,
  Connection,
  CreditCard,
  DataAnalysis,
  Document,
  Folder,
  Goods,
  Grid,
  Histogram,
  List,
  Menu,
  Money,
  Odometer,
  Picture,
  Promotion,
  RefreshLeft,
  ScaleToOriginal,
  Setting,
  Shop,
  ShoppingCart,
  Star,
  Switch,
  Ticket,
  TrendCharts,
  Trophy,
  User,
  UserFilled,
  Van,
  Warning
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import { useTabsStore } from '@/store/tabs'
import { hasPermission } from '@/utils/permission'
import { buildSidebarMenus } from '@/utils/permission'
import { getUnreadCount } from '@/api/notification'

// 图标映射表
const iconMap: Record<string, any> = {
  Odometer, Money, Goods, Menu, Box,
  Truck: Van, Histogram, ShoppingCart, Document,
  Ticket, Promotion, Star, Setting, Shop,
  Avatar, UserFilled, List, User, Connection,
  RefreshLeft, CreditCard, DataAnalysis, Trophy,
  QrCode: Grid, Scale: ScaleToOriginal, Grid,
  ScaleToOriginal, Folder, Switch, Coin, Picture,
  Bell, Warning, ChatDotRound, TrendCharts
}

const getIcon = (iconName: unknown) => {
  if (typeof iconName !== 'string' || !iconName) return null
  return iconMap[iconName] || null
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()

const isCollapse = ref(false)
const unreadCount = ref(0)
const canReadNotifications = computed(() => hasPermission('notification:query', permissionStore.permissions))

const defaultOpeneds = computed(() => {
  return menuRoutes.value
    .filter(r => r.children && r.children.length > 0)
    .map(r => r.key)
})

const activeMenu = computed(() => route.path)

const menuRoutes = computed(() => {
  if (permissionStore.routes.length > 0) {
    return buildSidebarMenus(permissionStore.routes, permissionStore.permissions, permissionStore.menuTree)
  }
  return []
})

const cachedViews = computed(() => {
  return tabsStore.tabs
    .filter(t => t.name)
    .map(t => t.name!)
})

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

// 标签相关
const tabMenuVisible = ref(false)
const tabMenuX = ref(0)
const tabMenuY = ref(0)
const contextTab = ref<{ path: string } | null>(null)

const switchTab = (path: string) => {
  tabsStore.setActive(path)
  router.push(path)
}

const showTabMenu = (e: MouseEvent, tab: { path: string }) => {
  contextTab.value = tab
  tabMenuX.value = e.clientX
  tabMenuY.value = e.clientY
  tabMenuVisible.value = true
}

const closeCurrentTab = () => {
  if (contextTab.value) {
    tabsStore.removeTab(contextTab.value.path, router)
  }
  tabMenuVisible.value = false
}

const closeOthersTab = () => {
  if (contextTab.value) {
    tabsStore.closeOthers(contextTab.value.path)
    router.push(contextTab.value.path)
  }
  tabMenuVisible.value = false
}

const closeAllTabs = () => {
  tabsStore.closeAll(router)
  tabMenuVisible.value = false
}

const hideTabMenu = () => { tabMenuVisible.value = false }

watch(() => route.path, (path) => {
  tabsStore.addTab(path, route)
}, { immediate: true })

const goNotification = () => {
  if (!canReadNotifications.value) return
  router.push('/system/notification')
}

let pollTimer: ReturnType<typeof setInterval> | undefined
const loadUnreadCount = async () => {
  if (!canReadNotifications.value) { unreadCount.value = 0; return }
  try {
    const res = await getUnreadCount()
    if (!canReadNotifications.value) return
    unreadCount.value = (res as any)?.count ?? (typeof res === 'number' ? res : 0)
  } catch { /* 静默 */ }
}

watch(canReadNotifications, allowed => { if (!allowed) unreadCount.value = 0 })

onMounted(() => {
  loadUnreadCount()
  pollTimer = setInterval(loadUnreadCount, 60_000)
  document.addEventListener('click', hideTabMenu)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  document.removeEventListener('click', hideTabMenu)
})

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      tabsStore.reset()
      await userStore.logout()
    } catch { /* 取消 */ }
  } else if (command === 'myPerformance') {
    router.push('/my-performance')
  }
}
</script>

<style lang="scss" scoped>
$sidebar-w: 228px;
$sidebar-collapsed-w: 64px;
$topbar-h: 52px;
$tabs-h: 38px;
$brand: #1d5d73;
$accent: #7ea76d;
$bg-sidebar: #151e2b;
$bg-sidebar-hover: rgba(255,255,255,0.05);
$text-sidebar: rgba(255,255,255,0.6);
$text-sidebar-active: #fff;
$radius: 10px;
$radius-lg: 14px;
$transition: 0.25s cubic-bezier(0.4,0,0.2,1);

.layout-wrapper {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ━━━ 侧边栏 ━━━ */
.sidebar {
  display: flex;
  flex-direction: column;
  width: $sidebar-w;
  height: 100vh;
  background: $bg-sidebar;
  transition: width $transition;
  flex-shrink: 0;
  overflow: hidden;
  position: relative;

  // 顶部品牌色线条
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, $accent, $brand);
    z-index: 1;
  }

  &.collapsed {
    width: $sidebar-collapsed-w;
  }
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 11px;
  height: 56px;
  padding: 0 18px;
  cursor: pointer;
  flex-shrink: 0;

  .logo-icon {
    width: 32px;
    height: 32px;
    flex-shrink: 0;
    svg { width: 100%; height: 100%; }
  }

  .logo-text {
    font-size: 17px;
    font-weight: 700;
    color: #fff;
    white-space: nowrap;
    letter-spacing: 1px;
  }
}

.sidebar-menu-wrap {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px 0;

  &::-webkit-scrollbar { width: 3px; }
  &::-webkit-scrollbar-thumb {
    background: rgba(255,255,255,0.12);
    border-radius: 2px;
  }
}

.sidebar-menu-wrap :deep(.el-menu) {
  border-right: none;
  padding: 0 10px;
}

.sidebar-menu-wrap :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 0;
  border-radius: 8px;
  font-size: 13.5px;
  transition: all 0.15s ease;

  .el-icon {
    font-size: 17px;
    margin-right: 8px;
  }

  &:hover {
    background: $bg-sidebar-hover !important;
  }

  &.is-active {
    background: rgba(126,167,109,0.18) !important;
    color: $text-sidebar-active !important;
    font-weight: 600;

    // 左侧指示条
    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 20px;
      border-radius: 0 3px 3px 0;
      background: $accent;
    }
  }
}

.sidebar-menu-wrap :deep(.el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  margin: 2px 0;
  border-radius: 8px;
  font-size: 13.5px;
  transition: all 0.15s ease;

  .el-icon {
    font-size: 17px;
  }

  &:hover {
    background: $bg-sidebar-hover !important;
  }
}

.sidebar-menu-wrap :deep(.el-sub-menu .el-menu-item) {
  padding-left: 48px !important;
  min-width: auto;
  height: 36px;
  line-height: 36px;
  font-size: 13px;
}

.sidebar-menu-wrap :deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  color: $text-sidebar-active !important;
}

.sidebar-menu-wrap :deep(.el-sub-menu .el-sub-menu__icon-arrow) {
  color: rgba(255,255,255,0.3);
}

/* ━━━ 折叠按钮 ━━━ */
.sidebar-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  margin: 4px 10px 10px;
  cursor: pointer;
  border-radius: 8px;
  color: rgba(255,255,255,0.35);
  flex-shrink: 0;
  transition: all 0.15s ease;

  &:hover {
    background: rgba(255,255,255,0.06);
    color: rgba(255,255,255,0.8);
  }

  .toggle-icon {
    width: 20px;
    height: 20px;
    transition: transform 0.3s ease;

    &.flipped {
      transform: scaleX(-1);
    }
  }
}

/* ━━━ 主区域 ━━━ */
.main-area {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  background: #f5f6f8;
}

/* ━━━ 顶栏 ━━━ */
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: $topbar-h;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.top-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: #f3f4f6;
    color: $brand;
  }
}

.notification-badge {
  cursor: pointer;
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px 4px 4px;
  border-radius: 8px;
  transition: background 0.15s ease;

  &:hover {
    background: #f3f4f6;
  }
}

.user-avatar {
  background: linear-gradient(135deg, $accent, $brand);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  font-size: 13.5px;
  color: #374151;
  font-weight: 500;
}

.arrow-icon {
  font-size: 12px;
  color: #9ca3af;
}

/* ━━━ 标签栏 ━━━ */
.tabs-bar {
  display: flex;
  align-items: center;
  height: $tabs-h;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
  overflow: hidden;
}

.tabs-scroll {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  flex: 1;
  &::-webkit-scrollbar { display: none; }
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 14px;
  border-radius: 6px;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.15s ease;
  background: transparent;
  border: 1px solid transparent;

  &:hover {
    background: #f3f4f6;
    color: #374151;
  }

  &.active {
    background: rgba(29, 93, 115, 0.08);
    color: $brand;
    font-weight: 600;
    border-color: rgba(29, 93, 115, 0.12);
  }

  .tab-close {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    color: #9ca3af;
    transition: all 0.12s ease;

    svg { width: 10px; height: 10px; }

    &:hover {
      background: rgba(0,0,0,0.08);
      color: #ef4444;
    }
  }
}

/* ━━━ 右键菜单 ━━━ */
.tab-context-menu {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.12);
  padding: 4px 0;
  min-width: 120px;
  border: 1px solid #f0f0f0;

  div {
    padding: 8px 16px;
    font-size: 13px;
    color: #374151;
    cursor: pointer;
    transition: all 0.1s;

    &:hover {
      background: #f9fafb;
      color: $brand;
    }
  }
}

/* ━━━ 内容区 ━━━ */
.content-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* ━━━ 折叠状态 ━━━ */
.sidebar-collapsed {
  .sidebar-logo {
    justify-content: center;
    padding: 0;
  }

  .sidebar-menu-wrap :deep(.el-menu) {
    padding: 0 6px;
  }

  .sidebar-menu-wrap :deep(.el-sub-menu .el-menu-item) {
    padding-left: 0 !important;
    text-align: center;
  }
}

/* ━━━ 动画 ━━━ */
.fade-text-enter-active,
.fade-text-leave-active {
  transition: opacity 0.2s ease;
}
.fade-text-enter-from,
.fade-text-leave-to {
  opacity: 0;
}
</style>

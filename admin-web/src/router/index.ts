import type {RouteRecordRaw} from 'vue-router'
import {createRouter, createWebHistory} from 'vue-router'
import {useUserStore} from '@/store/user'
import {usePermissionStore} from '@/store/permission'
import {ElMessage} from 'element-plus'

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      hidden: true
    }
  },
  {
    path: '/',
    component: () => import('@/views/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      // 经营概况
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: {
          title: '工作台',
          icon: 'Odometer',
          group: '经营概况'
        }
      },
      {
        path: 'pos',
        name: 'POS',
        component: () => import('@/views/pos/index.vue'),
        meta: {
          title: 'POS收银台',
          icon: 'Money',
          group: '经营概况'
        }
      },
      {
        path: 'order',
        name: 'OrderList',
        component: () => import('@/views/order/list/index.vue'),
        meta: {
          title: '订单中心',
          icon: 'List'
        }
      },
      // 商品供应
      {
        path: 'product',
        redirect: '/product/list',
        meta: {
          title: '商品供应',
          icon: 'Goods'
        },
        children: [
          {
            path: 'category',
            name: 'ProductCategory',
            component: () => import('@/views/product/category/index.vue'),
            meta: {
              title: '商品分类',
              icon: 'Menu'
            }
          },
          {
            path: 'list',
            name: 'ProductList',
            component: () => import('@/views/product/list/index.vue'),
            meta: {
              title: '商品管理',
              icon: 'Goods'
            }
          },
          {
            path: 'package',
            name: 'ProductPackage',
            component: () => import('@/views/product/package/index.vue'),
            meta: {
              title: '套餐管理',
              icon: 'Box'
            }
          },
          {
            path: 'supplier',
            name: 'Supplier',
            component: () => import('@/views/product/supplier/index.vue'),
            meta: {
              title: '供应商管理',
              icon: 'Truck'
            }
          }
        ]
      },
      // 库存物流
      {
        path: 'inventory',
        redirect: '/inventory/list',
        meta: {
          title: '库存物流',
          icon: 'Histogram'
        },
        children: [
          {
            path: 'purchase/order',
            name: 'PurchaseOrder',
            component: () => import('@/views/purchase/order/index.vue'),
            meta: {
              title: '采购管理',
              icon: 'ShoppingCart'
            }
          },
          {
            path: 'list',
            name: 'InventoryList',
            component: () => import('@/views/inventory/list/index.vue'),
            meta: {
              title: '库存查询',
              icon: 'Histogram'
            }
          },
          
          {
            path: 'log',
            name: 'InventoryLog',
            component: () => import('@/views/inventory/log/index.vue'),
            meta: {
              title: '库存日志',
              icon: 'Document'
            }
          }
        ]
      },
      // 客户营销
      {
        path: 'marketing',
        redirect: '/marketing/customer/list',
        meta: {
          title: '客户营销',
          icon: 'Connection'
        },
        children: [
          {
            path: 'customer/list',
            name: 'CustomerList',
            component: () => import('@/views/customer/list/index.vue'),
            meta: {
              title: '客户管理',
              icon: 'User'
            }
          },
          {
            path: 'coupon',
            name: 'CouponTemplate',
            component: () => import('@/views/marketing/coupon/index.vue'),
            meta: {
              title: '优惠券管理',
              icon: 'Ticket'
            }
          },
          {
            path: 'rule',
            name: 'MarketingRule',
            component: () => import('@/views/marketing/rule/index.vue'),
            meta: {
              title: '营销规则',
              icon: 'Promotion'
            }
          },
          {
            path: 'points',
            name: 'PointsMall',
            component: () => import('@/views/marketing/points/index.vue'),
            meta: {
              title: '积分商城',
              icon: 'Star'
            }
          }
        ]
      },
      // 系统设置
      {
        path: 'system',
        redirect: '/system/store',
        meta: {
          title: '系统设置',
          icon: 'Setting'
        },
        children: [
          {
            path: 'store',
            name: 'Store',
            component: () => import('@/views/system/store/index.vue'),
            meta: {
              title: '门店管理',
              icon: 'Shop'
            }
          },
          {
            path: 'staff',
            name: 'Staff',
            component: () => import('@/views/system/staff/index.vue'),
            meta: {
              title: '员工管理',
              icon: 'Avatar'
            }
          },
          {
            path: 'role',
            name: 'Role',
            component: () => import('@/views/system/role/index.vue'),
            meta: {
              title: '角色管理',
              icon: 'UserFilled'
            }
          },
          {
            path: 'dict',
            name: 'Dict',
            component: () => import('@/views/system/dict/index.vue'),
            meta: {
              title: '字典管理',
              icon: 'List'
            }
          }
        ]
      }
    ]
  }
]

// 动态路由（预留，后续根据权限动态添加）
// 注意：目前暂时不添加动态路由，避免页面不存在导致错误
export const asyncRoutes: RouteRecordRaw[] = [
  // 预留：商品管理、订单管理、客户管理等模块路由
  // 后续根据权限动态加载
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  // 白名单
  const whiteList = ['/login']
  if (whiteList.includes(to.path)) {
    next()
    return
  }

  // 检查 token
  if (!userStore.token) {
    next('/login')
    return
  }

  // 检查用户信息
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      ElMessage.error('获取用户信息失败')
      next('/login')
      return
    }
  }

  // 检查是否已加载动态路由
  // 注意：如果 asyncRoutes 为空，不需要重复加载
  if (permissionStore.routes.length === 0 && asyncRoutes.length > 0) {
    // 这里预留动态路由加载逻辑
    // 目前先使用静态路由
    permissionStore.setRoutes(asyncRoutes)
    
    // 动态添加路由
    asyncRoutes.forEach(route => {
      router.addRoute(route)
    })
    
    // 重新跳转，确保路由已添加
    next({ ...to, replace: true })
  } else {
    // 如果 asyncRoutes 为空，标记为已加载，避免无限循环
    if (permissionStore.routes.length === 0) {
      permissionStore.setRoutes([])
    }
    next()
  }
})

export default router


import type { RouteRecordRaw } from 'vue-router'
import type { MenuTreeNode } from '@/types/role'

type RouteComponent = NonNullable<RouteRecordRaw['component']>

type ParentKey = 'product' | 'inventory' | 'marketing' | 'operation' | 'system'

interface RouteDefinition {
  name: string
  path: string
  component: RouteComponent
  permissions?: string[]
  title: string
  icon?: string
  menuPath?: string
  parentKey?: ParentKey
}

const parentGroups: Record<ParentKey, RouteRecordRaw> = {
  product: {
    path: 'product',
    redirect: '/product/list',
    meta: {
      title: '商品供应',
      icon: 'Goods',
      menuPath: '/groups/product'
    },
    children: []
  },
  inventory: {
    path: 'inventory',
    redirect: '/inventory/list',
    meta: {
      title: '库存物流',
      icon: 'Histogram',
      menuPath: '/groups/inventory'
    },
    children: []
  },
  marketing: {
    path: 'marketing',
    redirect: '/marketing/customer/list',
    meta: {
      title: '客户营销',
      icon: 'Connection',
      menuPath: '/groups/marketing'
    },
    children: []
  },
  operation: {
    path: 'operation',
    redirect: '/operation/report',
    meta: {
      title: '运营管理',
      icon: 'DataAnalysis',
      menuPath: '/groups/operation'
    },
    children: []
  },
  system: {
    path: 'system',
    redirect: '/system/store',
    meta: {
      title: '系统设置',
      icon: 'Setting',
      menuPath: '/groups/system'
    },
    children: []
  }
}

const routeDefinitions: RouteDefinition[] = [
  {
    name: 'Dashboard',
    path: 'dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    permissions: ['report:dashboard'],
    title: '工作台',
    icon: 'Odometer',
    menuPath: '/dashboard'
  },
  {
    name: 'POS',
    path: 'pos',
    component: () => import('@/views/pos/index.vue'),
    permissions: ['pos:order:create'],
    title: 'POS收银台',
    icon: 'Money',
    menuPath: '/pos'
  },
  {
    name: 'OrderList',
    path: 'order',
    component: () => import('@/views/order/list/index.vue'),
    permissions: ['order:query'],
    title: '订单中心',
    icon: 'List',
    menuPath: '/standalone/order'
  },
  {
    name: 'AfterSales',
    path: 'after-sales',
    component: () => import('@/views/order/after-sales/index.vue'),
    permissions: ['afterSales:query'],
    title: '售后管理',
    icon: 'RefreshLeft',
    menuPath: '/standalone/after-sales'
  },
  {
    name: 'Credit',
    path: 'credit',
    component: () => import('@/views/credit/index.vue'),
    permissions: [
      'credit:account:view',
      'credit:repayment:view',
      'credit:statistics:view',
      'credit:limit:update',
      'credit:statement:generate',
      'credit:repay'
    ],
    title: '赊销总览',
    icon: 'CreditCard',
    menuPath: '/standalone/credit'
  },
  {
    name: 'ProductCategory',
    path: 'category',
    component: () => import('@/views/product/category/index.vue'),
    permissions: ['product:category:query'],
    title: '商品分类',
    icon: 'Menu',
    parentKey: 'product',
    menuPath: '/product/category'
  },
  {
    name: 'ProductList',
    path: 'list',
    component: () => import('@/views/product/list/index.vue'),
    permissions: ['product:query'],
    title: '商品管理',
    icon: 'Goods',
    parentKey: 'product',
    menuPath: '/product/list'
  },
  {
    name: 'ProductPackage',
    path: 'package',
    component: () => import('@/views/product/package/index.vue'),
    permissions: ['package:query'],
    title: '套餐管理',
    icon: 'Box',
    parentKey: 'product',
    menuPath: '/product/package'
  },
  {
    name: 'Supplier',
    path: 'supplier',
    component: () => import('@/views/product/supplier/index.vue'),
    permissions: ['supplier:list'],
    title: '供应商管理',
    icon: 'Truck',
    parentKey: 'product',
    menuPath: '/product/supplier'
  },
  {
    name: 'ProductUnit',
    path: 'units',
    component: () => import('@/views/product/units/index.vue'),
    permissions: ['product:unit:query'],
    title: '单位设置',
    icon: 'ScaleToOriginal',
    parentKey: 'product',
    menuPath: '/product/units'
  },
  {
    name: 'PurchaseOrder',
    path: 'purchase/order',
    component: () => import('@/views/purchase/order/index.vue'),
    permissions: ['purchase:query'],
    title: '采购管理',
    icon: 'ShoppingCart',
    parentKey: 'inventory',
    menuPath: '/inventory/purchase/order'
  },
  {
    name: 'InventoryList',
    path: 'list',
    component: () => import('@/views/inventory/list/index.vue'),
    permissions: ['inventory:view'],
    title: '库存查询',
    icon: 'Histogram',
    parentKey: 'inventory',
    menuPath: '/inventory/list'
  },
  {
    name: 'InventoryLog',
    path: 'log',
    component: () => import('@/views/inventory/log/index.vue'),
    permissions: ['inventory:log:list'],
    title: '库存日志',
    icon: 'Document',
    parentKey: 'inventory',
    menuPath: '/inventory/log'
  },
  {
    name: 'InventoryTransfer',
    path: 'transfer',
    component: () => import('@/views/inventory/transfer/index.vue'),
    permissions: ['inventory:transfer'],
    title: '库存调拨',
    icon: 'Switch',
    parentKey: 'inventory',
    menuPath: '/inventory/transfer'
  },
  {
    name: 'InventoryWarning',
    path: 'warning',
    component: () => import('@/views/inventory/warning/index.vue'),
    permissions: ['inventory:warning:query'],
    title: '库存预警',
    icon: 'Warning',
    parentKey: 'inventory',
    menuPath: '/inventory/warning'
  },
  {
    name: 'PurchaseReceipt',
    path: 'purchase/receipt',
    component: () => import('@/views/purchase/receipt/index.vue'),
    permissions: ['purchase:inbound'],
    title: '收货',
    icon: 'Van',
    parentKey: 'inventory',
    menuPath: '/purchase/receipt'
  },
  {
    name: 'InventoryLocations',
    path: 'locations',
    component: () => import('@/views/inventory/locations/index.vue'),
    permissions: ['inventory:view'],
    title: '货位',
    icon: 'Grid',
    parentKey: 'inventory',
    menuPath: '/inventory/locations'
  },
  {
    name: 'InventoryPickup',
    path: 'pickup',
    component: () => import('@/views/inventory/pickup/index.vue'),
    permissions: ['order:pickup:verify'],
    title: '取货',
    icon: 'Box',
    parentKey: 'inventory',
    menuPath: '/inventory/pickup'
  },
  {
    name: 'CustomerList',
    path: 'customer/list',
    component: () => import('@/views/customer/list/index.vue'),
    permissions: ['customer:query'],
    title: '客户管理',
    icon: 'User',
    parentKey: 'marketing',
    menuPath: '/marketing/customer/list'
  },
  {
    name: 'CustomerFollowUp',
    path: 'customer/follow-up',
    component: () => import('@/views/customer/follow-up/index.vue'),
    permissions: ['customer:followup:manage'],
    title: '客户跟进',
    icon: 'ChatDotRound',
    parentKey: 'marketing',
    menuPath: '/marketing/customer/follow-up'
  },
  {
    name: 'CouponTemplate',
    path: 'coupon',
    component: () => import('@/views/marketing/coupon/index.vue'),
    permissions: ['marketing:coupon:query'],
    title: '优惠券管理',
    icon: 'Ticket',
    parentKey: 'marketing',
    menuPath: '/marketing/coupon'
  },
  {
    name: 'MarketingRule',
    path: 'rule',
    component: () => import('@/views/marketing/rule/index.vue'),
    permissions: ['marketing:rule:query'],
    title: '营销规则',
    icon: 'Promotion',
    parentKey: 'marketing',
    menuPath: '/marketing/rule'
  },
  {
    name: 'PointsMall',
    path: 'points',
    component: () => import('@/views/marketing/points/index.vue'),
    permissions: ['marketing:points:goods:query', 'marketing:points:order:query'],
    title: '积分商城',
    icon: 'Star',
    parentKey: 'marketing',
    menuPath: '/marketing/points'
  },
  {
    name: 'ReportCenter',
    path: 'report',
    component: () => import('@/views/report/index.vue'),
    permissions: ['report:dashboard', 'report:sales', 'report:customer', 'report:inventory'],
    title: '报表中心',
    icon: 'DataAnalysis',
    parentKey: 'operation',
    menuPath: '/operation/report'
  },
  {
    name: 'Performance',
    path: 'performance',
    component: () => import('@/views/performance/index.vue'),
    permissions: ['performance:rule', 'performance:goal:set', 'performance:salary:view', 'performance:staff:view'],
    title: '绩效管理',
    icon: 'Trophy',
    parentKey: 'operation',
    menuPath: '/operation/performance'
  },
  {
    name: 'StoreAnalytics',
    path: 'store-analytics',
    component: () => import('@/views/store-analytics/index.vue'),
    permissions: ['store:analytics:view'],
    title: '门店统计',
    icon: 'Shop',
    parentKey: 'operation',
    menuPath: '/operation/store-analytics'
  },
  {
    name: 'MyPerformance',
    path: 'my-performance',
    component: () => import('@/views/my-performance/index.vue'),
    title: '我的绩效',
    icon: 'TrendCharts',
    menuPath: '/my-performance'
  },
  {
    name: 'Store',
    path: 'store',
    component: () => import('@/views/system/store/index.vue'),
    permissions: ['store:list'],
    title: '门店管理',
    icon: 'Shop',
    parentKey: 'system',
    menuPath: '/system/store'
  },
  {
    name: 'Staff',
    path: 'staff',
    component: () => import('@/views/system/staff/index.vue'),
    permissions: ['staff:list'],
    title: '员工管理',
    icon: 'Avatar',
    parentKey: 'system',
    menuPath: '/system/staff'
  },
  {
    name: 'Role',
    path: 'role',
    component: () => import('@/views/system/role/index.vue'),
    permissions: ['role:view'],
    title: '角色管理',
    icon: 'UserFilled',
    parentKey: 'system',
    menuPath: '/system/role'
  },
  {
    name: 'Dict',
    path: 'dict',
    component: () => import('@/views/system/dict/index.vue'),
    permissions: ['dict:list'],
    title: '字典管理',
    icon: 'List',
    parentKey: 'system',
    menuPath: '/system/dict'
  },
  {
    name: 'SystemLog',
    path: 'log',
    component: () => import('@/views/system/log/index.vue'),
    permissions: ['log:view'],
    title: '操作日志',
    icon: 'Document',
    parentKey: 'system',
    menuPath: '/system/log'
  },
  {
    name: 'Notification',
    path: 'notification',
    component: () => import('@/views/system/notification/index.vue'),
    permissions: ['notification:query'],
    title: '站内信',
    icon: 'Bell',
    parentKey: 'system',
    menuPath: '/system/notification'
  }
]

export function buildAppChildRoutes(owned: string[], menuTree: MenuTreeNode[] = []): RouteRecordRaw[] {
  const menuPaths = new Set(collectMenuPaths(menuTree))
  const roots: RouteRecordRaw[] = []
  const groups = new Map<ParentKey, RouteRecordRaw>()

  routeDefinitions.forEach(definition => {
    if (!hasAnyPermission(definition.permissions, owned)) {
      return
    }

    if (menuPaths.size > 0 && !matchesAnyMenu(definition, menuPaths)) {
      return
    }

    const route: RouteRecordRaw = {
      path: definition.path,
      name: definition.name,
      component: definition.component,
      meta: {
        title: definition.title,
        icon: definition.icon,
        permissions: definition.permissions,
        menuPath: definition.menuPath
      }
    }

    if (definition.parentKey) {
      let parent = groups.get(definition.parentKey)
      if (!parent) {
        parent = cloneGroupRoute(parentGroups[definition.parentKey])
        groups.set(definition.parentKey, parent)
        roots.push(parent)
      }
      const children = Array.isArray(parent.children) ? parent.children : []
      children.push(route)
      parent.children = children
      parent.redirect = resolveChildRedirect(parent.path, children[0]?.path)
      return
    }

    roots.push(route)
  })

  return roots
}

function cloneGroupRoute(route: RouteRecordRaw): RouteRecordRaw {
  return {
    ...route,
    children: []
  }
}

function hasAnyPermission(required: string[] | undefined, owned: string[]): boolean {
  if (!required || required.length === 0) {
    return true
  }
  return required.some(permission => owned.includes(permission))
}

function collectMenuPaths(menuTree: MenuTreeNode[]): string[] {
  const result: string[] = []
  const visit = (nodes: MenuTreeNode[]) => {
    nodes.forEach(node => {
      if (node.path) {
        result.push(node.path)
      }
      if (node.children?.length) {
        visit(node.children)
      }
    })
  }
  visit(menuTree || [])
  return result
}

function matchesAnyMenu(definition: RouteDefinition, menuPaths: Set<string>): boolean {
  return Boolean(definition.menuPath && menuPaths.has(definition.menuPath))
}

function resolveChildRedirect(parentPath: string, childPath?: string): string | undefined {
  if (!childPath) {
    return undefined
  }
  const normalizedParent = parentPath.startsWith('/') ? parentPath : `/${parentPath}`
  return `${normalizedParent}/${childPath}`.replace(/\/+/g, '/')
}

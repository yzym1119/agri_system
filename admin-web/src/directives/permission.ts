import type { App, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/store/permission'
import { useUserStore } from '@/store/user'
import { hasPermission } from '@/utils/permission'

function resolvePermissions(): string[] {
  const permissionStore = usePermissionStore()
  const userStore = useUserStore()
  return permissionStore.permissions.length > 0
    ? permissionStore.permissions
    : (userStore.userInfo?.permissions || [])
}

function applyPermission(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const required = binding.value
  const owned = resolvePermissions()
  if (!hasPermission(required, owned)) {
    // 使用 display:none 而非 removeChild，避免 Vue 虚拟 DOM 与真实 DOM 失去同步
    // removeChild 会导致按钮被从 DOM 中物理移除，但 Vue 仍认为它存在，
    // 后续 patch 时无法正确重建元素，导致按钮点击无响应
    el.style.display = 'none'
  } else {
    // 关键修复：有权限时必须恢复显示，否则在权限异步加载场景下，
    // mounted 阶段权限未就绪会隐藏按钮，后续 updated 不会自动恢复
    if (el.style.display === 'none') {
      el.style.display = ''
    }
  }
}

export function registerPermissionDirective(app: App) {
  app.directive('permission', {
    mounted(el, binding) {
      applyPermission(el as HTMLElement, binding)
    },
    updated(el, binding) {
      applyPermission(el as HTMLElement, binding)
    }
  })
}

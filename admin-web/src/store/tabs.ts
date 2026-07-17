import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

export interface TabItem {
  path: string
  title: string
  name?: string
  icon?: string
  affix?: boolean // 固定标签（如工作台）不可关闭
}

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>([])
  const activeTab = ref('')

  /** 根据路由信息查找菜单标题 */
  const findTitle = (path: string, route: any): string => {
    return (route.meta?.title as string) || route.name?.toString() || path
  }

  const findIcon = (route: any): string | undefined => {
    return route.meta?.icon as string | undefined
  }

  /** 添加标签 */
  const addTab = (path: string, route: any) => {
    // 跳过不需要标签的路径
    if (!path || path === '/login' || path === '/') return

    const exists = tabs.value.find(t => t.path === path)
    if (!exists) {
      tabs.value.push({
        path,
        title: findTitle(path, route),
        name: route.name?.toString(),
        icon: findIcon(route)
      })
    }
    activeTab.value = path
  }

  /** 关闭标签 */
  const removeTab = (path: string, router: any) => {
    const idx = tabs.value.findIndex(t => t.path === path)
    if (idx === -1) return

    const tab = tabs.value[idx]
    if (tab.affix) return // 固定标签不可关闭

    tabs.value.splice(idx, 1)

    // 如果关闭的是当前激活标签，切换到相邻标签
    if (activeTab.value === path) {
      const next = tabs.value[idx] || tabs.value[idx - 1]
      if (next) {
        activeTab.value = next.path
        router.push(next.path)
      }
    }
  }

  /** 关闭其他标签 */
  const closeOthers = (path: string) => {
    tabs.value = tabs.value.filter(t => t.affix || t.path === path)
    activeTab.value = path
  }

  /** 关闭所有标签（保留固定标签） */
  const closeAll = (router: any) => {
    tabs.value = tabs.value.filter(t => t.affix)
    const affixTab = tabs.value[0]
    if (affixTab) {
      activeTab.value = affixTab.path
      router.push(affixTab.path)
    }
  }

  /** 设置激活标签 */
  const setActive = (path: string) => {
    activeTab.value = path
  }

  /** 重置 */
  const reset = () => {
    tabs.value = []
    activeTab.value = ''
  }

  return {
    tabs,
    activeTab,
    addTab,
    removeTab,
    closeOthers,
    closeAll,
    setActive,
    reset
  }
})

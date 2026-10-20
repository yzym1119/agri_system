<template>
  <div class="category-sidebar" :class="{ collapsed: isCollapsed }">
    <div class="category-header">
      <span v-if="!isCollapsed" class="category-title">商品分类</span>
      <el-button
        :icon="isCollapsed ? Expand : Fold"
        :aria-label="isCollapsed ? '展开商品分类' : '收起商品分类'"
        :aria-expanded="!isCollapsed"
        text
        @click="isCollapsed = !isCollapsed"
        class="collapse-btn"
      />
    </div>

    <div v-if="!isCollapsed" class="category-list">
      <button type="button" class="category-item all-category" :class="{ active: selectedCategoryId === null }" @click="handleCategorySelect(null)">全部商品</button>
      <!-- 树形分类 -->
      <div
        v-for="category in categories"
        :key="category.id"
        class="category-tree"
      >
        <!-- 父分类 -->
        <button
          type="button"
          class="category-item parent"
          :aria-pressed="selectedCategoryId === category.id"
          :aria-expanded="category.children?.length ? expandedCategories.includes(category.id) : undefined"
          :class="{
            active: selectedCategoryId === category.id,
            expanded: expandedCategories.includes(category.id)
          }"
          @click="handleParentClick(category)"
        >
          <el-icon
            v-if="category.children && category.children.length > 0"
            class="expand-icon"
            :class="{ rotated: expandedCategories.includes(category.id) }"
          >
            <ArrowRight />
          </el-icon>
          <span v-else class="expand-placeholder"></span>
          <span class="category-name">{{ category.name }}</span>
        </button>

        <!-- 递归显示子分类 -->
        <div
          v-if="expandedCategories.includes(category.id) && category.children && category.children.length > 0"
          class="category-children"
        >
          <CategoryTreeItem
            v-for="child in category.children"
            :key="child.id"
            :category="child"
            :selected-category-id="selectedCategoryId"
            :expanded-categories="expandedCategories"
            :level="1"
            @select="handleCategorySelect"
            @toggle="handleToggle"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {ArrowRight, Expand, Fold} from '@element-plus/icons-vue'
import {getPosCategoryTree as getCategoryTree} from '@/api/pos-catalog'
import type {CategoryTreeVO} from '@/types/product'
import CategoryTreeItem from './CategoryTreeItem.vue'

const emit = defineEmits<{
  categoryChange: [categoryId: number | null]
  categoryTreeChange: [tree: CategoryTreeVO[]]
}>()

const isCollapsed = ref(false)
const selectedCategoryId = ref<number | null>(null)
const categories = ref<CategoryTreeVO[]>([])
const expandedCategories = ref<number[]>([])

// 加载分类数据
const loadCategories = async () => {
  try {
    const tree = await getCategoryTree()
    categories.value = tree
    // 默认展开所有有子分类的父分类
    const expandAll = (nodes: CategoryTreeVO[]) => {
      nodes.forEach(node => {
        if (node.children && node.children.length > 0) {
          expandedCategories.value.push(node.id)
          expandAll(node.children)
        }
      })
    }
    expandAll(tree)
    // 通知父组件分类树已加载
    emit('categoryTreeChange', tree)
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

// 父分类点击（展开/折叠或选择）
const handleParentClick = (category: CategoryTreeVO) => {
  if (category.children && category.children.length > 0) {
    // 有子分类，切换展开/折叠
    const index = expandedCategories.value.indexOf(category.id)
    if (index > -1) {
      expandedCategories.value.splice(index, 1)
    } else {
      expandedCategories.value.push(category.id)
    }
  }
  // 无论是否有子分类，都可以选择父分类
  handleCategorySelect(category.id)
}

// 切换展开/折叠（供子组件调用）
const handleToggle = (categoryId: number) => {
  const index = expandedCategories.value.indexOf(categoryId)
  if (index > -1) {
    expandedCategories.value.splice(index, 1)
  } else {
    expandedCategories.value.push(categoryId)
  }
}

// 选择分类
const handleCategorySelect = (categoryId: number | null) => {
  selectedCategoryId.value = categoryId
  emit('categoryChange', categoryId)
}

onMounted(() => {
  loadCategories()
})

defineExpose({
  selectCategory: handleCategorySelect,
  clearCategory: () => handleCategorySelect(null)
})
</script>

<style scoped lang="scss">
.category-sidebar {
  width: 160px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;


  &.collapsed {
    width: 64px;
  }
}

.category-header {
  height: 56px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5e7eb;

  .category-title {
    font-size: 14px;
    font-weight: 600;
    color: #1f2937;
  }

  .collapse-btn {
    padding: 8px;
    min-width: 36px;
    min-height: 36px;
  }
}

.category-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.category-item {
  height: 40px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s;
  color: #6b7280;

  &:hover {
    background: #f3f4f6;
    color: var(--el-color-primary);
  }

  &.active {
    background: #edf5f7;
    color: var(--el-color-primary);
    border-right: 3px solid var(--el-color-primary);
    font-weight: 500;
  }

  .category-name {
    flex: 1;
    font-size: 14px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.category-tree {
  .category-item {
    &.parent {
      font-weight: 500;
      color: #1f2937;
    }

    &.child {
      padding-left: 40px;
      font-size: 13px;
      color: #6b7280;
    }

    .expand-icon {
      font-size: 14px;
      transition: transform 0.2s;
      flex-shrink: 0;
      color: #626f7e;
      width: 14px;

      &.rotated {
        transform: rotate(90deg);
      }
    }

    .expand-placeholder {
      width: 14px;
      flex-shrink: 0;
    }
  }

  .category-children {
    background: #f9fafb;
  }
}

.category-sidebar.collapsed {
  .category-name {
    display: none;
  }

  .category-item {
    justify-content: center;
    padding: 0;
  }
}
</style>


<style scoped>
.category-item { width: 100%; border: 0; background: transparent; font: inherit; text-align: left; }
.category-item:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.all-category { padding-left: 20px; }
</style>

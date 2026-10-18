<template>
  <div class="category-tree-item">
    <!-- 当前分类项 -->
    <button
      type="button"
      :aria-pressed="selectedCategoryId === category.id"
      :aria-expanded="category.children?.length ? expandedCategories.includes(category.id) : undefined"
      class="category-item"
      :class="{
        'child': level > 0,
        'active': selectedCategoryId === category.id
      }"
      :style="{ paddingLeft: `${16 + level * 24}px` }"
      @click="handleClick"
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
        :level="level + 1"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {ArrowRight} from '@element-plus/icons-vue'
import type {CategoryTreeVO} from '@/types/product'

interface Props {
  category: CategoryTreeVO
  selectedCategoryId: number | null
  expandedCategories: number[]
  level: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  select: [categoryId: number]
  toggle: [categoryId: number]
}>()

const handleClick = () => {
  if (props.category.children && props.category.children.length > 0) {
    // 有子分类，切换展开/折叠
    emit('toggle', props.category.id)
  }
  // 无论是否有子分类，都可以选择当前分类
  emit('select', props.category.id)
}
</script>

<style scoped lang="scss">
.category-tree-item {
  .category-item {
    height: 40px;
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
    
    &.child {
      font-size: 13px;
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
    
    .category-name {
      flex: 1;
      font-size: 14px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
  
  .category-children {
    background: #f9fafb;
  }
}
</style>










<style scoped>
.category-item { width: 100%; border: 0; background: transparent; font: inherit; text-align: left; }
.category-item:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.all-category { padding-left: 20px; }
</style>

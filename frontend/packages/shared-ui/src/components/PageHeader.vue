<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
}>();

defineEmits<{
  back: [];
}>();
</script>

<template>
  <div class="page-header">
    <div class="page-header__left">
      <el-button
        v-if="$attrs.onBack"
        type="default"
        :icon="'ArrowLeft'"
        circle
        size="small"
        class="page-header__back-btn"
        @click="$emit('back')"
      />
      <div>
        <h2 class="page-header__title">{{ title }}</h2>
        <p v-if="description" class="page-header__desc">{{ description }}</p>
      </div>
    </div>
    <div class="page-header__right">
      <slot name="extra" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-light);
}
.page-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-header__back-btn {
  flex-shrink: 0;
}
.page-header__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.page-header__desc {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.page-header__right {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

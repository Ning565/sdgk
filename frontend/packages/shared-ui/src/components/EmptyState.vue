<script setup lang="ts">
defineProps<{
  title?: string;
  description?: string;
  image?: string;
}>();
</script>

<template>
  <div class="empty-state">
    <el-empty :description="description || '暂无数据'">
      <template v-if="title" #title>
        <span>{{ title }}</span>
      </template>
      <slot name="action" />
    </el-empty>
  </div>
</template>

<style scoped>
.empty-state {
  padding: 60px 20px;
  text-align: center;
}
</style>

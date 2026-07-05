<script setup lang="ts">
import { computed } from 'vue';
import { RiskLevel } from '@gaokao/shared-types';

const props = defineProps<{
  level: RiskLevel;
}>();

const config = computed(() => {
  switch (props.level) {
    case RiskLevel.LOW:
      return { type: 'success' as const, label: '低风险' };
    case RiskLevel.MEDIUM:
      return { type: 'warning' as const, label: '中等风险' };
    case RiskLevel.HIGH:
      return { type: 'danger' as const, label: '高风险' };
    default:
      return { type: 'info' as const, label: '未知' };
  }
});
</script>

<template>
  <el-tag :type="config.type" size="small" effect="plain">
    {{ config.label }}
  </el-tag>
</template>

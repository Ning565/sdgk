<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { AdmissionPlanDTO } from '@gaokao/shared-types';
import { PageHeader } from '@gaokao/shared-ui';

const route = useRoute();
const router = useRouter();
const plan = ref<AdmissionPlanDTO | null>(null);

onMounted(async () => {
  // In production, fetch plan detail from API
  plan.value = {
    id: route.params.planId as string,
    schoolId: '',
    schoolName: '示例大学',
    majorName: '计算机科学与技术',
    majorCode: '080901',
    planCount: 120,
    batch: 'FIRST_BATCH' as any,
    subjectCategory: 'SCIENCE' as any,
    tuition: 5000,
    duration: 4,
    year: 2025,
  };
});
</script>

<template>
  <div class="plan-detail-page">
    <PageHeader title="专业详情" @back="router.back()" />

    <template v-if="plan">
      <el-card>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="专业名称">{{ plan.majorName }}</el-descriptions-item>
          <el-descriptions-item label="专业代码">{{ plan.majorCode }}</el-descriptions-item>
          <el-descriptions-item label="所属院校">{{ plan.schoolName }}</el-descriptions-item>
          <el-descriptions-item label="招生批次">{{ plan.batch }}</el-descriptions-item>
          <el-descriptions-item label="招生人数">{{ plan.planCount }} 人</el-descriptions-item>
          <el-descriptions-item label="科类要求">{{ plan.subjectCategory === 'SCIENCE' ? '理科' : plan.subjectCategory === 'LIBERAL_ARTS' ? '文科' : '综合' }}</el-descriptions-item>
          <el-descriptions-item label="学费">{{ plan.tuition }} 元/年</el-descriptions-item>
          <el-descriptions-item label="学制">{{ plan.duration }} 年</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </template>
    <el-empty v-else description="暂无专业信息" />
  </div>
</template>

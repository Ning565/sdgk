<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { SchoolDetailDTO } from '@gaokao/shared-types';
import { schoolApi } from '@gaokao/api-client';
import { PageHeader } from '@gaokao/shared-ui';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const school = ref<SchoolDetailDTO | null>(null);

onMounted(async () => {
  const schoolId = route.params.schoolId as string;
  loading.value = true;
  try {
    const res = await schoolApi.getDetail(schoolId);
    school.value = res.data;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="school-detail-page">
    <PageHeader
      :title="school?.name || '院校详情'"
      description="院校详细信息与历年录取数据"
      @back="router.back()"
    />

    <el-card v-loading="loading">
      <template v-if="school">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="院校代码">{{ school.code }}</el-descriptions-item>
          <el-descriptions-item label="所在省份">{{ school.province }}</el-descriptions-item>
          <el-descriptions-item label="所在城市">{{ school.city }}</el-descriptions-item>
          <el-descriptions-item label="办学类型">{{ school.type }}</el-descriptions-item>
          <el-descriptions-item label="院校层次">{{ school.level }}</el-descriptions-item>
          <el-descriptions-item label="是否为双一流">
            <el-tag :type="school.isDoubleFirstClass ? 'success' : 'info'">
              {{ school.isDoubleFirstClass ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="官网">
            <a :href="school.website" target="_blank">{{ school.website }}</a>
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ school.contactPhone }}</el-descriptions-item>
        </el-descriptions>

        <el-divider>院校简介</el-divider>
        <p class="school-detail-page__desc">{{ school.description }}</p>

        <el-divider>历年录取分数</el-divider>
        <el-table :data="school.historicalScores" border stripe>
          <el-table-column prop="year" label="年份" width="80" />
          <el-table-column prop="minScore" label="最低分" />
          <el-table-column prop="avgScore" label="平均分" />
          <el-table-column prop="maxScore" label="最高分" />
          <el-table-column prop="minRank" label="最低位次" />
          <el-table-column prop="batch" label="批次" />
        </el-table>

        <el-divider>本年招生计划</el-divider>
        <el-table :data="school.currentPlans" border stripe>
          <el-table-column prop="majorName" label="专业名称" />
          <el-table-column prop="majorCode" label="专业代码" width="100" />
          <el-table-column prop="planCount" label="招生人数" width="100" />
          <el-table-column prop="batch" label="批次" width="120" />
          <el-table-column prop="tuition" label="学费(元/年)" width="120" />
        </el-table>
      </template>

      <el-empty v-else description="暂无院校信息" />
    </el-card>
  </div>
</template>

<style scoped>
.school-detail-page__desc {
  line-height: 1.8;
  color: var(--el-text-color-regular);
}
</style>

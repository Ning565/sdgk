<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminApi } from '@gaokao/api-client';
import { PageHeader } from '@gaokao/shared-ui';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const batch = ref<any>(null);

onMounted(async () => {
  const batchId = route.params.batchId as string;
  loading.value = true;
  try {
    const res = await adminApi.getImportBatch(batchId);
    batch.value = res.data;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="batch-detail-page">
    <PageHeader
      title="导入批次详情"
      :description="`批次号: ${route.params.batchId}`"
      @back="router.back()"
    />

    <el-card v-loading="loading">
      <template v-if="batch">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="批次号">{{ batch.batchId }}</el-descriptions-item>
          <el-descriptions-item label="导入类型">{{ batch.type }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ batch.fileName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag
              :type="batch.status === 'SUCCESS' ? 'success' : batch.status === 'FAILED' ? 'danger' : 'warning'"
            >
              {{ batch.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总行数">{{ batch.totalRows }}</el-descriptions-item>
          <el-descriptions-item label="成功行数">{{ batch.successRows }}</el-descriptions-item>
          <el-descriptions-item label="失败行数">{{ batch.failedRows }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ batch.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ batch.completedAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ batch.createdBy }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else description="批次不存在或已过期" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { PageHeader } from '@gaokao/shared-ui';
import { ElMessage } from 'element-plus';

const versions = ref([
  { id: '1', version: '2025_v1', year: 2025, province: '广东', status: 'PUBLISHED', description: '2025年广东省招生数据第一版', createdAt: '2025-06-01', createdBy: 'admin' },
  { id: '2', version: '2025_v2', year: 2025, province: '广东', status: 'DRAFT', description: '2025年广东省招生数据更新版', createdAt: '2025-06-15', createdBy: 'admin' },
]);

const statusTagMap: Record<string, { type: 'danger' | 'warning' | 'success' | 'info' | 'primary'; label: string }> = {
  DRAFT: { type: 'info', label: '草稿' },
  PUBLISHED: { type: 'success', label: '已发布' },
  ARCHIVED: { type: 'warning', label: '已归档' },
};
</script>

<template>
  <div class="versions-page">
    <PageHeader title="数据版本" description="管理数据版本，控制前端展示的数据集" />

    <el-card>
      <el-table :data="versions" border stripe>
        <el-table-column prop="version" label="版本号" />
        <el-table-column prop="year" label="年份" width="80" />
        <el-table-column prop="province" label="省份" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status]?.type || 'info'">
              {{ statusTagMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              link
              size="small"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              type="warning"
              link
              size="small"
            >
              归档
            </el-button>
            <el-button type="primary" link size="small">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

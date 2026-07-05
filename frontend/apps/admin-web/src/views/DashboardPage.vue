<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { adminApi } from '@gaokao/api-client';
import { PageHeader } from '@gaokao/shared-ui';

const stats = ref({
  totalUsers: 0,
  totalForms: 0,
  totalSchools: 0,
  totalImports: 0,
  publishedVersions: 0,
});
const loading = ref(false);

const statCards = [
  { key: 'totalUsers', label: '用户总数', icon: 'User', color: '#409eff' },
  { key: 'totalForms', label: '志愿表总数', icon: 'Document', color: '#67c23a' },
  { key: 'totalSchools', label: '院校总数', icon: 'School', color: '#e6a23c' },
  { key: 'totalImports', label: '数据导入批次', icon: 'Upload', color: '#f56c6c' },
  { key: 'publishedVersions', label: '已发布版本', icon: 'Collection', color: '#909399' },
];

onMounted(async () => {
  loading.value = true;
  try {
    const res = await adminApi.getDashboardStats();
    stats.value = res.data as any;
  } catch {
    // Use placeholder data for demo
    stats.value = {
      totalUsers: 1280,
      totalForms: 856,
      totalSchools: 1024,
      totalImports: 45,
      publishedVersions: 3,
    };
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="dashboard-page">
    <PageHeader title="数据总览" description="系统核心数据概览" />

    <el-row :gutter="20" v-loading="loading">
      <el-col
        v-for="card in statCards"
        :key="card.key"
        :xs="24"
        :sm="12"
        :lg="8"
        :xl="4"
        style="margin-bottom: 20px"
      >
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__inner">
            <div class="stat-card__icon" :style="{ background: card.color }">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-card__info">
              <span class="stat-card__label">{{ card.label }}</span>
              <span class="stat-card__value">
                {{ (stats as any)[card.key]?.toLocaleString() || '--' }}
              </span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <span>欢迎使用高考志愿通管理后台</span>
          </template>
          <div class="dashboard-page__welcome">
            <p>您可以通过左侧菜单进行以下操作：</p>
            <ul>
              <li><strong>数据导入</strong>：上传一分一段表、招生计划、历史录取数据</li>
              <li><strong>外链管理</strong>：管理推荐页面展示的外部链接</li>
              <li><strong>数据版本</strong>：管理数据版本，发布/归档版本</li>
              <li><strong>审计日志</strong>：查看系统操作记录</li>
              <li><strong>用户角色</strong>：管理用户账号和角色权限</li>
            </ul>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-card__inner {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-card__icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.stat-card__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-card__label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.stat-card__value {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.dashboard-page__welcome {
  line-height: 2;
}
.dashboard-page__welcome ul {
  padding-left: 20px;
}
</style>

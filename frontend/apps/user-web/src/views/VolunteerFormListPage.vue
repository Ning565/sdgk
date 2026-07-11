<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useVolunteerStore } from '@/stores/volunteer';
import { FormStatus } from '@gaokao/shared-types';
import { PageHeader, EmptyState } from '@gaokao/shared-ui';
import { ElMessage } from 'element-plus';

const router = useRouter();
const store = useVolunteerStore();

type StatusTag = { type: 'danger' | 'warning' | 'success' | 'info' | 'primary'; label: string };

const statusTagMap: Record<string, StatusTag> = {
  [FormStatus.DRAFT]: { type: 'info', label: '草稿' },
  [FormStatus.SUBMITTED]: { type: 'warning', label: '已提交' },
  [FormStatus.CONFIRMED]: { type: 'success', label: '已确认' },
  ACTIVE: { type: 'success', label: '编辑中' },
  ARCHIVED: { type: 'info', label: '已归档' },
};

function filledCount(form: any) {
  return form.itemCount ?? form.filledChoices ?? 0;
}

function totalCount(form: any) {
  return form.maxItems != null ? form.maxItems : 96;
}

function capacityLabel(form: any) {
  return form.maxItems != null ? String(form.maxItems) : '不限';
}

function progress(form: any) {
  const max = form.maxItems;
  if (max == null) return 0; // 不限容量时不显示进度条
  const filled = filledCount(form);
  return Math.min(100, Math.round((filled / max) * 100));
}

onMounted(() => {
  store.fetchForms();
});

function goToDetail(formId: string) {
  router.push(`/volunteer-forms/${formId}`);
}

async function handleCreate() {
  try {
    const form = await store.createFormForYear(`志愿表 ${store.forms.length + 1}`, new Date().getFullYear());
    ElMessage.success('创建成功');
    goToDetail(String(form.id));
  } catch {
    ElMessage.error('创建失败');
  }
}
</script>

<template>
  <div class="form-list-page">
    <PageHeader title="我的志愿表" description="管理您的志愿填报方案">
      <template #extra>
        <el-button type="primary" @click="handleCreate">新建志愿表</el-button>
      </template>
    </PageHeader>

    <div v-loading="store.loading">
      <template v-if="store.forms.length === 0">
        <EmptyState
          title="还没有志愿表"
          description="点击上方按钮创建您的第一份志愿表"
        >
          <template #action>
            <el-button type="primary" @click="handleCreate">新建志愿表</el-button>
          </template>
        </EmptyState>
      </template>

      <div v-else class="form-list">
        <el-card
          v-for="form in store.forms"
          :key="form.id"
          class="form-card"
          shadow="hover"
          @click="goToDetail(String(form.id))"
        >
          <div class="form-card__header">
            <h3 class="form-card__name">{{ form.name }}</h3>
            <el-tag
              :type="(statusTagMap[form.status] || { type: 'info' }).type"
              size="small"
            >
              {{ (statusTagMap[form.status] || { label: '未知' }).label }}
            </el-tag>
          </div>
          <div class="form-card__body">
            <div class="form-card__stat">
              <span class="form-card__stat-label">已填志愿</span>
              <span class="form-card__stat-value">
                {{ filledCount(form) }} / {{ capacityLabel(form) }}
              </span>
            </div>
            <el-progress
              v-if="form.maxItems != null"
              :percentage="progress(form)"
              :status="filledCount(form) >= (form.maxItems ?? 0) ? 'success' : undefined"
            />
          </div>
          <div class="form-card__footer">
            <span class="form-card__time">更新于 {{ form.updatedAt }}</span>
            <el-button type="primary" link size="small">查看详情</el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}
.form-card {
  cursor: pointer;
  transition: transform 0.2s;
}
.form-card:hover {
  transform: translateY(-2px);
}
.form-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.form-card__name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.form-card__body {
  margin-bottom: 12px;
}
.form-card__stat {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.form-card__stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.form-card__stat-value {
  font-size: 14px;
  font-weight: 500;
}
.form-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-card__time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVolunteerStore } from '@/stores/volunteer';
import { volunteerApi } from '@gaokao/api-client';
import { RiskLabel, PageHeader, EmptyState } from '@gaokao/shared-ui';
import { ElMessage } from 'element-plus';
import { computed } from 'vue';

const route = useRoute();
const router = useRouter();
const store = useVolunteerStore();

onMounted(() => {
  const formId = route.params.formId as string;
  store.fetchFormDetail(formId);
});

const formItems = computed(() => store.currentForm?.items || store.currentForm?.choices || []);

function filledCount() {
  return store.currentForm?.itemCount ?? store.currentForm?.filledChoices ?? formItems.value.length;
}

function totalCount() {
  return store.currentForm?.totalChoices ?? 96;
}

function operationId(action: string, itemId: string | number) {
  return `${action}-${route.params.formId}-${itemId}-${Date.now()}`;
}

async function refreshDetail() {
  await store.fetchFormDetail(route.params.formId as string);
}

async function handleMove(itemId: string | number, targetPosition: number) {
  if (!store.currentForm?.version) return;
  try {
    await volunteerApi.moveItem(
      route.params.formId as string,
      String(itemId),
      targetPosition,
      store.currentForm.version,
      operationId('move', itemId)
    );
    await refreshDetail();
    ElMessage.success('顺序已更新');
  } catch {
    ElMessage.error('移动失败，请刷新后重试');
  }
}

async function handleDelete(itemId: string | number) {
  if (!store.currentForm?.version) return;
  try {
    await volunteerApi.deleteItem(
      route.params.formId as string,
      String(itemId),
      store.currentForm.version,
      operationId('delete', itemId)
    );
    await refreshDetail();
    ElMessage.success('已删除');
  } catch {
    ElMessage.error('删除失败，请刷新后重试');
  }
}

async function handleSubmit() {
  try {
    await volunteerApi.checkForm(route.params.formId as string);
    ElMessage.success('提交成功');
    refreshDetail();
  } catch {
    ElMessage.error('提交失败');
  }
}
</script>

<template>
  <div class="form-detail-page">
    <PageHeader
      :title="store.currentForm?.name || '志愿表详情'"
      @back="router.push('/volunteer-forms')"
    >
      <template #extra>
        <el-button
          v-if="store.currentForm?.status === 'DRAFT'"
          type="primary"
          @click="handleSubmit"
        >
          提交志愿表
        </el-button>
      </template>
    </PageHeader>

    <el-card v-loading="store.loading">
      <template v-if="store.currentForm">
        <div class="form-detail-page__header">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="状态">
              <el-tag :type="store.currentForm.status === 'DRAFT' ? 'info' : 'success'">
                {{ store.currentForm.status === 'DRAFT' ? '草稿' : store.currentForm.status === 'SUBMITTED' ? '已提交' : '已确认' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="志愿进度">
              {{ filledCount() }} / {{ totalCount() }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ store.currentForm.updatedAt }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <el-divider>志愿列表</el-divider>

        <template v-if="formItems.length === 0">
          <EmptyState title="暂未添加志愿" description="请从推荐列表中添加志愿" />
        </template>

        <div v-else class="choice-list">
          <el-card
            v-for="(choice, index) in formItems"
            :key="choice.id"
            class="choice-item"
            shadow="hover"
          >
            <div class="choice-item__order">{{ index + 1 }}</div>
            <div class="choice-item__content">
              <div class="choice-item__header">
                <span class="choice-item__school">{{ choice.schoolName }}</span>
                <el-tag v-if="choice.label" size="small" :type="choice.label === '冲' ? 'danger' : choice.label === '稳' ? 'warning' : 'success'">{{ choice.label }}</el-tag>
                <RiskLabel v-else-if="choice.riskLevel" :level="choice.riskLevel" />
              </div>
              <div class="choice-item__info">
                <span>{{ choice.majorName }}</span>
                <span v-if="choice.majorCode" class="choice-item__code">({{ choice.majorCode }})</span>
                <el-tag v-if="choice.batch" size="small" type="info">{{ choice.batch }}</el-tag>
                <el-tag v-if="choice.isObeyAdjustment" size="small" type="warning">服从调剂</el-tag>
                <el-tag v-if="choice.probability != null" size="small" type="primary">{{ choice.probability }}%</el-tag>
              </div>
              <div class="choice-item__scores">
                <span v-if="choice.planCount">招生 {{ choice.planCount }} 人</span>
                <span v-if="choice.tuition">学费 {{ choice.tuition }} 元/年</span>
                <span v-if="choice.lastYearMinRank">去年最低位次: {{ choice.lastYearMinRank }}</span>
                <span v-else-if="choice.historicalMinRank">往年最低位次: {{ choice.historicalMinRank }}</span>
              </div>
            </div>
            <div class="choice-item__actions">
              <el-button
                type="primary"
                link
                size="small"
                :disabled="index === 0"
                @click="handleMove(choice.id, index)"
              >
                上移
              </el-button>
              <el-button
                type="primary"
                link
                size="small"
                :disabled="index === formItems.length - 1"
                @click="handleMove(choice.id, index + 2)"
              >
                下移
              </el-button>
              <el-button type="danger" link size="small" @click="handleDelete(choice.id)">删除</el-button>
            </div>
          </el-card>
        </div>
      </template>

      <el-empty v-else description="志愿表不存在" />
    </el-card>
  </div>
</template>

<style scoped>
.form-detail-page__header {
  margin-bottom: 8px;
}
.choice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.choice-item {
  display: flex;
  align-items: center;
  gap: 16px;
}
.choice-item__order {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border-radius: 50%;
  font-weight: 700;
  flex-shrink: 0;
}
.choice-item__content {
  flex: 1;
}
.choice-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.choice-item__school {
  font-size: 16px;
  font-weight: 600;
}
.choice-item__info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}
.choice-item__code {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.choice-item__scores {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.choice-item__actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
</style>

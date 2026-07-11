<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVolunteerStore } from '@/stores/volunteer';
import { ApiError, volunteerApi } from '@gaokao/api-client';
import { RiskLabel, PageHeader, EmptyState } from '@gaokao/shared-ui';
import { ElMessage, ElMessageBox } from 'element-plus';

const route = useRoute();
const router = useRouter();
const store = useVolunteerStore();
const exportLoading = ref(false);
const selectedItemIds = ref<Set<string | number>>(new Set());
const draggingItemId = ref<string | number | null>(null);

onMounted(() => {
  const formId = route.params.formId as string;
  store.fetchFormDetail(formId);
});

const formItems = computed(() => store.currentForm?.items || store.currentForm?.choices || []);
const selectedCount = computed(() => selectedItemIds.value.size);
const allSelected = computed(() => formItems.value.length > 0 && formItems.value.every(item => selectedItemIds.value.has(item.id)));
const selectionIndeterminate = computed(() => selectedCount.value > 0 && !allSelected.value);
const labelStats = computed(() => {
  const stats = { chong: 0, wen: 0, bao: 0 };
  for (const item of formItems.value) {
    if (item.label === '冲') stats.chong += 1;
    else if (item.label === '稳') stats.wen += 1;
    else if (item.label === '保') stats.bao += 1;
  }
  return {
    chong: store.currentForm?.stats?.chongCount ?? stats.chong,
    wen: store.currentForm?.stats?.wenCount ?? stats.wen,
    bao: store.currentForm?.stats?.baoCount ?? stats.bao,
  };
});

function filledCount() {
  return store.currentForm?.itemCount ?? store.currentForm?.filledChoices ?? formItems.value.length;
}

const defaultMaxItems = 96;

function totalCount() {
  return store.currentForm?.maxItems ?? defaultMaxItems;
}

function capacityDisplay() {
  const max = store.currentForm?.maxItems;
  return max != null ? max : '不限';
}

async function handleUpdateMaxItems(maxItems: number | null) {
  if (!store.currentForm) return;
  try {
    const res = await volunteerApi.updateMaxItems(route.params.formId as string, maxItems);
    if (store.currentForm && res.data) {
      store.currentForm.maxItems = res.data.maxItems;
      store.currentForm.itemCount = res.data.itemCount;
    }
    ElMessage.success(maxItems != null ? `容量已设为 ${maxItems}` : '容量已设为不限');
  } catch {
    ElMessage.error('修改失败');
  }
}

async function handleSetCapacity() {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入志愿表容量（留空为不限）',
      '设置容量',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /^\d*$/,
        inputErrorMessage: '请输入数字或留空',
        inputValue: store.currentForm?.maxItems != null ? String(store.currentForm.maxItems) : '',
      }
    );
    const num = value && value.trim() ? Number(value.trim()) : null;
    if (num != null && (num < 1 || !Number.isFinite(num))) {
      ElMessage.warning('容量至少为 1');
      return;
    }
    await handleUpdateMaxItems(num);
  } catch {
    // 取消
  }
}

function operationId(action: string, itemId: string | number) {
  return `${action}-${route.params.formId}-${itemId}-${Date.now()}`;
}

function formatProbability(probability?: number) {
  if (probability == null) return '';
  return Number.isInteger(probability) ? `${probability}%` : `${probability.toFixed(1)}%`;
}

function formatPlanCount(planCount?: number) {
  return planCount != null ? `${planCount} 人` : '-';
}

function formatTuition(tuition?: number) {
  return tuition != null ? `${tuition} 元/年` : '-';
}

function downloadExportFile(fileUrl: string) {
  const link = document.createElement('a');
  link.href = fileUrl;
  link.download = '';
  document.body.appendChild(link);
  link.click();
  link.remove();
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

async function handleMoveTo(itemId: string | number) {
  if (!store.currentForm?.version) return;
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入目标位置（1-${formItems.value.length}）`,
      '移动到第 N 位',
      {
        confirmButtonText: '移动',
        cancelButtonText: '取消',
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入数字',
      }
    );
    const target = Math.max(1, Math.min(Number(value), formItems.value.length));
    await handleMove(itemId, target);
  } catch {
    // 用户取消移动时不提示错误
  }
}

function toggleItemSelection(itemId: string | number, checked: string | number | boolean) {
  const next = new Set(selectedItemIds.value);
  if (Boolean(checked)) {
    next.add(itemId);
  } else {
    next.delete(itemId);
  }
  selectedItemIds.value = next;
}

function toggleAllSelection(checked: string | number | boolean) {
  selectedItemIds.value = Boolean(checked)
    ? new Set(formItems.value.map(item => item.id))
    : new Set();
}

async function handleBatchDelete() {
  if (!store.currentForm?.version || selectedItemIds.value.size === 0) {
    ElMessage.warning('请先选择要删除的志愿');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedItemIds.value.size} 个志愿吗？删除后会自动重排序。`,
      '批量删除',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      }
    );
    await volunteerApi.batchDeleteItems(
      route.params.formId as string,
      Array.from(selectedItemIds.value).map(String),
      store.currentForm.version,
      operationId('batch-delete', selectedItemIds.value.size)
    );
    selectedItemIds.value = new Set();
    await refreshDetail();
    ElMessage.success('已批量删除');
  } catch {
    // 取消确认或删除失败统一静默/提示
  }
}

function handleDragStart(itemId: string | number) {
  draggingItemId.value = itemId;
}

async function handleDrop(targetIndex: number) {
  const itemId = draggingItemId.value;
  draggingItemId.value = null;
  if (itemId == null) return;
  const currentIndex = formItems.value.findIndex(item => item.id === itemId);
  if (currentIndex < 0 || currentIndex === targetIndex) return;
  await handleMove(itemId, targetIndex + 1);
}

function handleDragEnd() {
  draggingItemId.value = null;
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
    const selected = new Set(selectedItemIds.value);
    selected.delete(itemId);
    selectedItemIds.value = selected;
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

async function handleExport(confirmWithErrors = false) {
  exportLoading.value = true;
  const formId = route.params.formId as string;
  try {
    const response = await volunteerApi.exportForm(formId, {
      confirmWithErrors,
      clientOperationId: operationId('export', formId),
    });
    downloadExportFile(response.data.fileUrl);
    ElMessage.success('Excel 已生成，正在下载');
  } catch (error) {
    if (error instanceof ApiError && error.code === 8008 && !confirmWithErrors) {
      try {
        await ElMessageBox.confirm(
          '志愿表中存在错误级别问题，确认后仍可导出 Excel。是否继续导出？',
          '确认导出',
          {
            type: 'warning',
            confirmButtonText: '继续导出',
            cancelButtonText: '取消',
          }
        );
        await handleExport(true);
      } catch {
        // 用户取消确认时不提示错误
      }
      return;
    }
    ElMessage.error('导出失败，请稍后重试');
  } finally {
    exportLoading.value = false;
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
          :loading="exportLoading"
          @click="handleExport()"
        >
          导出 Excel
        </el-button>
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
              {{ filledCount() }} / {{ capacityDisplay() }}
              <el-button type="primary" link size="small" style="margin-left:6px" @click.stop="handleSetCapacity">修改容量</el-button>
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ store.currentForm.updatedAt }}</el-descriptions-item>
          </el-descriptions>
          <div class="form-structure">
            <div class="form-structure__main">
              <strong>已选 {{ filledCount() }} / {{ capacityDisplay() }}</strong>
              <span>山东平行志愿按当前顺序投档，请把更想去的专业排在前面。</span>
            </div>
            <div class="form-structure__stats">
              <span class="form-structure__stat form-structure__stat--rush">冲 {{ labelStats.chong }}</span>
              <span class="form-structure__stat form-structure__stat--stable">稳 {{ labelStats.wen }}</span>
              <span class="form-structure__stat form-structure__stat--safe">保 {{ labelStats.bao }}</span>
            </div>
          </div>
        </div>

        <el-divider>志愿列表</el-divider>

        <template v-if="formItems.length === 0">
          <EmptyState title="暂未添加志愿" description="请从推荐列表中添加志愿" />
        </template>

        <div v-else class="choice-list">
          <div class="choice-toolbar">
            <el-checkbox
              :model-value="allSelected"
              :indeterminate="selectionIndeterminate"
              @change="toggleAllSelection"
            >
              全选
            </el-checkbox>
            <span class="choice-toolbar__count">已选 {{ selectedCount }} 个</span>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="selectedCount === 0"
              @click="handleBatchDelete"
            >
              批量删除
            </el-button>
          </div>
          <div
            v-for="(choice, index) in formItems"
            :key="choice.id"
            class="choice-row"
            :class="{ 'choice-row--selected': selectedItemIds.has(choice.id), 'choice-row--dragging': draggingItemId === choice.id }"
            draggable="true"
            @dragstart="handleDragStart(choice.id)"
            @dragover.prevent
            @drop="handleDrop(index)"
            @dragend="handleDragEnd"
          >
            <el-checkbox
              class="choice-row__check"
              :model-value="selectedItemIds.has(choice.id)"
              @change="value => toggleItemSelection(choice.id, value)"
              @click.stop
            />
            <div class="choice-row__drag" title="拖拽排序">⋮⋮</div>
            <div class="choice-row__order">{{ index + 1 }}</div>
            <div class="choice-row__content">
              <div class="choice-row__header">
                <div class="choice-row__title">
                  <span class="choice-row__school">{{ choice.schoolName }}</span>
                  <span class="choice-row__major">{{ choice.majorName }}</span>
                </div>
                <el-tag v-if="choice.label" size="small" :type="choice.label === '冲' ? 'danger' : choice.label === '稳' ? 'warning' : 'success'">{{ choice.label }}</el-tag>
                <RiskLabel v-else-if="choice.riskLevel" :level="choice.riskLevel" />
                <el-tag v-if="choice.probability != null" size="small" type="primary">{{ formatProbability(choice.probability) }}</el-tag>
              </div>
              <div class="choice-row__meta">
                <div class="choice-row__meta-item">
                  <span>院校代号</span>
                  <strong>{{ choice.schoolCode || '-' }}</strong>
                </div>
                <div class="choice-row__meta-item">
                  <span>专业代码</span>
                  <strong>{{ choice.majorCode || '-' }}</strong>
                </div>
                <div class="choice-row__meta-item">
                  <span>选科要求</span>
                  <strong>{{ choice.subjectRequirementText || '不限' }}</strong>
                </div>
                <div class="choice-row__meta-item">
                  <span>计划人数</span>
                  <strong>{{ formatPlanCount(choice.planCount) }}</strong>
                </div>
                <div class="choice-row__meta-item">
                  <span>学费</span>
                  <strong>{{ formatTuition(choice.tuition) }}</strong>
                </div>
                <div class="choice-row__meta-item">
                  <span>专业类</span>
                  <strong>{{ choice.majorSubcategory || choice.majorCategory || '-' }}</strong>
                </div>
                <div v-if="choice.batch" class="choice-row__meta-item">
                  <span>批次</span>
                  <strong>{{ choice.batch }}</strong>
                </div>
                <div v-if="choice.enrollmentType" class="choice-row__meta-item">
                  <span>招生类型</span>
                  <strong>{{ choice.enrollmentType }}</strong>
                </div>
                <div v-if="choice.lastYearMinRank || choice.historicalMinRank" class="choice-row__meta-item">
                  <span>往年最低位次</span>
                  <strong>{{ choice.lastYearMinRank || choice.historicalMinRank }}</strong>
                </div>
              </div>
              <div v-if="choice.isObeyAdjustment || choice.note" class="choice-row__notes">
                <el-tag v-if="choice.isObeyAdjustment" size="small" type="warning">服从调剂</el-tag>
                <span v-if="choice.note">{{ choice.note }}</span>
              </div>
            </div>
            <div class="choice-row__actions">
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
              <el-button type="primary" link size="small" @click="handleMoveTo(choice.id)">移动到</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(choice.id)">删除</el-button>
            </div>
          </div>
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
.form-structure {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #f8fafc;
}
.form-structure__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.form-structure__main strong {
  font-size: 18px;
  color: var(--el-text-color-primary);
}
.form-structure__main span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.form-structure__stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.form-structure__stat {
  min-width: 58px;
  padding: 6px 10px;
  border-radius: 6px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
}
.form-structure__stat--rush {
  color: #b91c1c;
  background: #fef2f2;
}
.form-structure__stat--stable {
  color: #b45309;
  background: #fffbeb;
}
.form-structure__stat--safe {
  color: #047857;
  background: #ecfdf5;
}
.choice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.choice-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
}
.choice-toolbar__count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.choice-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  padding: 16px 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-sizing: border-box;
  transition: border-color .15s, background .15s, opacity .15s;
}
.choice-row--selected {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.choice-row--dragging {
  opacity: .55;
}
.choice-row__check {
  margin-top: 8px;
  flex-shrink: 0;
}
.choice-row__drag {
  width: 18px;
  margin-top: 5px;
  color: var(--el-text-color-placeholder);
  cursor: grab;
  user-select: none;
  line-height: 14px;
  letter-spacing: 0;
  flex-shrink: 0;
}
.choice-row__order {
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
.choice-row__content {
  flex: 1;
  min-width: 0;
}
.choice-row__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.choice-row__title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 240px;
  flex-wrap: wrap;
}
.choice-row__school {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.choice-row__major {
  font-size: 14px;
  color: var(--el-text-color-regular);
}
.choice-row__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px 18px;
}
.choice-row__meta-item {
  min-width: 0;
}
.choice-row__meta-item span {
  display: block;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.choice-row__meta-item strong {
  display: block;
  margin-top: 2px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}
.choice-row__notes {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.choice-row__actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  align-items: center;
}
@media (max-width: 768px) {
  .form-structure {
    align-items: flex-start;
    flex-direction: column;
  }
  .choice-row {
    flex-direction: column;
  }
  .choice-row__check,
  .choice-row__drag {
    margin-top: 0;
  }
  .choice-row__actions {
    align-self: flex-end;
  }
}
</style>

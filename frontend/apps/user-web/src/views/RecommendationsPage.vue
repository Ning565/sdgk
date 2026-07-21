<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useCandidateStore } from '@/stores/candidate';
import { useVolunteerStore, type PlanItem, type SchoolGroup } from '@/stores/volunteer';
import { ElMessage } from 'element-plus';

const router = useRouter();
const candidateStore = useCandidateStore();
const volunteerStore = useVolunteerStore();
const currentYear = new Date().getFullYear();

const keyword = ref('');
const levelFilter = ref('');
const resultView = ref<'recommended' | 'candidates'>('recommended');
const expandedSchools = ref<Set<number>>(new Set());
const addingPlanIds = ref<Set<number>>(new Set());
const selectedPlanIds = ref<Set<number>>(new Set());
const batchAddingSchoolIds = ref<Set<number>>(new Set());
const evidenceDrawerVisible = ref(false);
const evidencePlan = ref<PlanItem | null>(null);
const evidenceSchool = ref<SchoolGroup | null>(null);
const formPreviewOpen = ref(false);
const selectedFormId = ref<string>('');
const switchingForm = ref(false);
const recommendationSettings = ref({
  count: 96,
  rushPercent: 25,
  stablePercent: 33,
  safePercent: 42,
  rushProbabilityMin: 20,
});
const serverRecommendationLimit = 10000;
const ratioTotal = computed(() =>
  Number(recommendationSettings.value.rushPercent || 0) +
  Number(recommendationSettings.value.stablePercent || 0) +
  Number(recommendationSettings.value.safePercent || 0)
);
const ratioIsValid = computed(() => ratioTotal.value === 100);
const labelOrder: Record<string, number> = { '冲': 0, '稳': 1, '保': 2 };
const probabilityBands = computed(() => {
  const rushMin = Number(recommendationSettings.value.rushProbabilityMin || 0);
  return [
    { label: '冲', range: `${rushMin}% - 50%`, percent: recommendationSettings.value.rushPercent },
    { label: '稳', range: '50% - 80%', percent: recommendationSettings.value.stablePercent },
    { label: '保', range: '80% - 100%', percent: recommendationSettings.value.safePercent },
  ];
});
const maxRecommendationCount = computed(() => {
  const total = volunteerStore.recResult?.candidatePlanCount ?? volunteerStore.recResult?.totalPlans;
  if (typeof total === 'number' && total > 0) {
    return Math.min(total, serverRecommendationLimit);
  }
  return serverRecommendationLimit;
});
const specialistMajorCategories = new Set([
  '交通运输大类', '公共管理与服务大类', '公安与司法大类', '农林牧渔大类', '医药卫生大类',
  '土木建筑大类', '教育与体育大类', '文化艺术大类', '新闻传播大类', '旅游大类',
  '水利大类', '生物与化工大类', '电子与信息大类', '能源动力与材料大类', '装备制造大类',
  '财经商贸大类', '资源环境与安全大类', '轻工纺织大类', '食品药品与粮食大类',
]);

function schoolTypeFilter(value?: string) {
  if (value === 'PUBLIC') return ['公办'];
  if (value === 'PRIVATE') return ['民办'];
  return undefined;
}

function sortPlans(plans: SchoolGroup['plans']) {
  return [...plans].sort((a, b) => {
    if (resultView.value === 'recommended') {
      const aRank = a.recommend_rank ?? Number.POSITIVE_INFINITY;
      const bRank = b.recommend_rank ?? Number.POSITIVE_INFINITY;
      if (aRank !== bRank) return aRank - bRank;
    }
    const labelDiff = (labelOrder[a.label || ''] ?? 9) - (labelOrder[b.label || ''] ?? 9);
    if (labelDiff !== 0) return labelDiff;
    const aProb = a.probability ?? Number.POSITIVE_INFINITY;
    const bProb = b.probability ?? Number.POSITIVE_INFINITY;
    if (aProb !== bProb) return a.label === '保' ? bProb - aProb : aProb - bProb;
    return a.majorName.localeCompare(b.majorName, 'zh-CN');
  });
}

function sortGroups(groups: SchoolGroup[]) {
  return [...groups].sort((a, b) => {
    if (resultView.value === 'recommended') {
      const aRank = Math.min(...a.plans.map(p => p.recommend_rank ?? Number.POSITIVE_INFINITY));
      const bRank = Math.min(...b.plans.map(p => p.recommend_rank ?? Number.POSITIVE_INFINITY));
      if (aRank !== bRank) return aRank - bRank;
    }
    const aLabel = a.plans[0]?.label || '';
    const bLabel = b.plans[0]?.label || '';
    const labelDiff = (labelOrder[aLabel] ?? 9) - (labelOrder[bLabel] ?? 9);
    if (labelDiff !== 0) return labelDiff;
    const aProb = aLabel === '保'
      ? (a.maxProbability ?? Number.NEGATIVE_INFINITY)
      : (a.minProbability ?? Number.POSITIVE_INFINITY);
    const bProb = bLabel === '保'
      ? (b.maxProbability ?? Number.NEGATIVE_INFINITY)
      : (b.minProbability ?? Number.POSITIVE_INFINITY);
    if (aProb !== bProb) return aLabel === '保' ? bProb - aProb : aProb - bProb;
    return a.schoolName.localeCompare(b.schoolName, 'zh-CN');
  });
}

function splitMajorPrefs(values?: string[]) {
  const selected = values || [];
  return {
    categories: selected.filter(v => specialistMajorCategories.has(v)),
    subcategories: selected.filter(v => !specialistMajorCategories.has(v)),
  };
}

const schoolGroups = computed(() => {
  if (!volunteerStore.recResult) return [];
  let groups = volunteerStore.recResult.schoolGroups;
  const rushFloor = Number(recommendationSettings.value.rushProbabilityMin || 0);
  groups = groups
    .map(g => {
      const plans = sortPlans(g.plans.filter(p => p.label !== '冲' || p.probability == null || p.probability >= rushFloor));
      const probabilities = plans.map(p => p.probability).filter((p): p is number => p != null);
      return {
        ...g,
        plans,
        eligiblePlanCount: plans.length,
        minProbability: probabilities.length ? Math.min(...probabilities) : undefined,
        maxProbability: probabilities.length ? Math.max(...probabilities) : undefined,
      };
    })
    .filter(g => g.plans.length > 0);
  if (keyword.value) {
    const kw = keyword.value.toLowerCase();
    groups = groups
      .map(g => {
        const schoolMatched = g.schoolName.toLowerCase().includes(kw);
        const plans = schoolMatched ? g.plans : g.plans.filter(p => p.majorName.toLowerCase().includes(kw));
        return { ...g, plans, eligiblePlanCount: plans.length };
      })
      .filter(g => g.plans.length > 0);
  }
  if (levelFilter.value) {
    groups = groups
      .map(g => {
        const plans = g.plans.filter(p => p.label === levelFilter.value);
        const probabilities = plans.map(p => p.probability).filter((p): p is number => p != null);
        return {
          ...g,
          plans,
          eligiblePlanCount: plans.length,
          minProbability: probabilities.length ? Math.min(...probabilities) : undefined,
          maxProbability: probabilities.length ? Math.max(...probabilities) : undefined,
        };
      })
      .filter(g => g.plans.length > 0);
  }
  return sortGroups(groups);
});

const rawDisplayedCount = computed(() => {
  if (!volunteerStore.recResult) return 0;
  return volunteerStore.recResult.schoolGroups.reduce((sum, group) => sum + group.plans.length, 0);
});
const eligibleTotal = computed(() => volunteerStore.recResult?.eligiblePlanCount ?? volunteerStore.recResult?.totalPlans ?? 0);
const candidateTotal = computed(() => volunteerStore.recResult?.candidatePlanCount ?? volunteerStore.recResult?.totalPlans ?? 0);
const recommendedTotal = computed(() => volunteerStore.recResult?.recommendedPlanCount ?? rawDisplayedCount.value);

const currentFormItems = computed(() => volunteerStore.currentForm?.items || volunteerStore.currentForm?.choices || []);
const addedPlanIds = computed(() => new Set(currentFormItems.value.map(item => item.planId).filter(Boolean)));
const currentFormName = computed(() => volunteerStore.currentForm?.name || '当前志愿表');
const currentFormCount = computed(() => volunteerStore.currentForm?.itemCount ?? currentFormItems.value.length);
const previewFormItems = computed(() => currentFormItems.value.slice(0, 8));
const selectableForms = computed(() => {
  const year = candidateStore.candidateProfile?.year || currentYear;
  const sameYear = volunteerStore.forms.filter(form => !form.year || form.year === year);
  return sameYear.length > 0 ? sameYear : volunteerStore.forms;
});
const addedPositionMap = computed(() => {
  const map = new Map<number, number>();
  currentFormItems.value.forEach((item, index) => {
    if (item.planId != null) {
      map.set(Number(item.planId), item.sortOrder ?? item.order ?? index + 1);
    }
  });
  return map;
});

watch(
  () => recommendationSettings.value.count,
  value => {
    if (Number(value) > maxRecommendationCount.value) {
      recommendationSettings.value.count = maxRecommendationCount.value;
      ElMessage.warning(`当前筛选条件最多可推荐 ${formatCount(maxRecommendationCount.value)} 个专业`);
    }
  }
);

watch(
  () => volunteerStore.currentForm?.id,
  id => {
    if (id != null) selectedFormId.value = String(id);
  },
  { immediate: true }
);

function formatCount(value: number) {
  return value.toLocaleString('zh-CN');
}

function formatProbability(value?: number) {
  if (value == null) return '-';
  const numeric = Number(value);
  return Number.isInteger(numeric) ? `${numeric}%` : `${numeric.toFixed(2)}%`;
}

function formatRank(value?: number) {
  return value != null ? value.toLocaleString('zh-CN') : '-';
}

function formatPlanCount(value?: number) {
  return value != null ? `${value}人` : '-';
}

function formCount(form: { itemCount?: number; filledChoices?: number; totalChoices?: number }) {
  return form.itemCount ?? form.filledChoices ?? form.totalChoices ?? 0;
}

function formCapacity(form: { maxItems?: number | null }) {
  return form.maxItems != null ? form.maxItems : '不限';
}

async function selectVolunteerForm(formId: string | number) {
  if (!formId || String(volunteerStore.currentForm?.id) === String(formId)) return;
  switchingForm.value = true;
  try {
    await volunteerStore.selectForm(formId);
    selectedPlanIds.value = new Set();
    ElMessage.success(`已切换到“${volunteerStore.currentForm?.name || '志愿表'}”`);
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : '切换志愿表失败');
    selectedFormId.value = volunteerStore.currentForm?.id != null
      ? String(volunteerStore.currentForm.id)
      : '';
  } finally {
    switchingForm.value = false;
  }
}

function toggleFormPreview() {
  formPreviewOpen.value = !formPreviewOpen.value;
}

function addedPosition(planId: number) {
  return addedPositionMap.value.get(planId);
}

function addButtonText(planId: number) {
  const position = addedPosition(planId);
  return position ? `✓ 已加入 · 第${position}志愿` : '加入志愿表';
}

function errorMessage(err: unknown) {
  return err instanceof Error ? err.message : '刷新推荐失败';
}

function normalizedRecommendationCount(notify = false) {
  const value = Number(recommendationSettings.value.count || 0);
  if (!Number.isFinite(value) || value < 1) {
    if (notify) ElMessage.warning('推荐数量至少为 1');
    return null;
  }
  const count = Math.floor(value);
  const max = maxRecommendationCount.value;
  if (count > max) {
    recommendationSettings.value.count = max;
    if (notify) {
      ElMessage.warning(`当前筛选条件最多可推荐 ${formatCount(max)} 个专业`);
    }
    return null;
  }
  if (count !== value) {
    recommendationSettings.value.count = count;
  }
  return count;
}

function toggleSchool(schoolId: number) {
  const s = new Set(expandedSchools.value);
  if (s.has(schoolId)) s.delete(schoolId); else s.add(schoolId);
  expandedSchools.value = s;
}

function selectablePlans(school: SchoolGroup) {
  return school.plans.filter(plan => !addedPlanIds.value.has(plan.planId));
}

function selectedPlans(school: SchoolGroup) {
  return school.plans.filter(plan => selectedPlanIds.value.has(plan.planId) && !addedPlanIds.value.has(plan.planId));
}

function selectedSchoolCount(school: SchoolGroup) {
  return selectedPlans(school).length;
}

function isSchoolAllSelected(school: SchoolGroup) {
  const plans = selectablePlans(school);
  return plans.length > 0 && plans.every(plan => selectedPlanIds.value.has(plan.planId));
}

function isSchoolIndeterminate(school: SchoolGroup) {
  const count = selectedSchoolCount(school);
  return count > 0 && count < selectablePlans(school).length;
}

function togglePlanSelection(planId: number, checked: string | number | boolean) {
  const next = new Set(selectedPlanIds.value);
  if (checked) {
    next.add(planId);
  } else {
    next.delete(planId);
  }
  selectedPlanIds.value = next;
}

function toggleSchoolSelection(school: SchoolGroup, checked: string | number | boolean) {
  const next = new Set(selectedPlanIds.value);
  for (const plan of selectablePlans(school)) {
    if (checked) {
      next.add(plan.planId);
    } else {
      next.delete(plan.planId);
    }
  }
  selectedPlanIds.value = next;
}

function openEvidence(plan: PlanItem, school: SchoolGroup) {
  evidencePlan.value = plan;
  evidenceSchool.value = school;
  evidenceDrawerVisible.value = true;
}

function evidenceTrendRows(plan: PlanItem | null) {
  const year = candidateStore.candidateProfile?.year || currentYear;
  return [
    { year: year - 3, rank: plan?.threeYearMinRank },
    { year: year - 2, rank: plan?.twoYearMinRank },
    { year: year - 1, rank: plan?.lastYearMinRank },
  ].filter(row => row.rank != null);
}

async function runPrediction() {
  if (!volunteerStore.recResult) return;
  const allPlans = volunteerStore.recResult.schoolGroups.flatMap(g => g.plans);
  if (allPlans.length === 0) return;
  if (allPlans.every(p => p.probability != null && p.label)) return;
  try {
    const { volunteerApi } = await import('@gaokao/api-client');
    const p = candidateStore.candidateProfile;
    if (!p) return;
    // Build plan history data from lastYearMinRank
    const planData = allPlans.slice(0, 200).map(pl => ({
      planId: pl.planId,
      history: pl.lastYearMinRank ? [{ year: (p.year || currentYear) - 1, lowestRank: pl.lastYearMinRank }] : [],
    }));
    const res = await volunteerApi.batchPredict({
      candidate: { year: p.year || currentYear, score: p.score || 0, rank: p.rank || 0, subjects: p.subjects || [] },
      plans: planData,
    }) as unknown as { data?: { results?: Array<{ planId: number; probability?: number; label?: string }> } };
    // Apply predictions back to plans
    if (res.data?.results) {
      const predMap = new Map(res.data.results.map((r: { planId: number; probability?: number; label?: string }) => [r.planId, r]));
      for (const g of volunteerStore.recResult!.schoolGroups) {
        for (const pl of g.plans) {
          const pred = predMap.get(pl.planId);
          if (pred) {
            pl.probability = pred.probability;
            pl.label = pred.label;
            if (pred.probability != null && g.minProbability == null) g.minProbability = pred.probability;
            if (pred.probability != null && g.maxProbability == null) g.maxProbability = pred.probability;
            if (pred.probability != null && pred.probability < (g.minProbability ?? 100)) g.minProbability = pred.probability;
            if (pred.probability != null && pred.probability > (g.maxProbability ?? 0)) g.maxProbability = pred.probability;
          }
        }
      }
    }
  } catch { /* prediction optional */ }
}

async function fetchSmartRecommendations(includeAllCandidates = false) {
  await candidateStore.fetchProfile(currentYear);
  if (candidateStore.candidateProfile) {
    const p = candidateStore.candidateProfile;
    const recommendationCount = includeAllCandidates
      ? Math.min(candidateTotal.value || serverRecommendationLimit, serverRecommendationLimit)
      : normalizedRecommendationCount();
    if (!recommendationCount) return;
    await volunteerStore.ensureActiveForm(p.year || currentYear).catch(() => undefined);
    const preferred = splitMajorPrefs(p.preferredMajors);
    const excluded = splitMajorPrefs(p.excludedMajors);
    const schoolType = schoolTypeFilter(p.schoolNature);
    await volunteerStore.fetchRecommendations({
      year: p.year || currentYear,
      educationLevel: p.educationLevel,
      score: p.score,
      rank: p.rank,
      subjects: p.subjects || [],
      subjectComboIndex: p.subjectComboIndex,
      province: p.preferredRegions,
      majorCategory: preferred.categories,
      majorSubcategory: preferred.subcategories,
      excludeMajorCategory: excluded.categories,
      excludeMajorSubcategory: excluded.subcategories,
      tuitionMax: p.tuitionMax,
      schoolType,
      excludeSinoForeign: p.acceptJointProgram === false ? true : undefined,
      pageNo: 1,
      pageSize: recommendationCount,
      recommendationCount,
      rushRatio: recommendationSettings.value.rushPercent / 100,
      stableRatio: recommendationSettings.value.stablePercent / 100,
      safeRatio: recommendationSettings.value.safePercent / 100,
      rushProbabilityMin: recommendationSettings.value.rushProbabilityMin,
      includeAllCandidates,
      sortBy: 'probability',
      sortDir: 'asc',
    });
    await runPrediction();
  }
}

async function handleResultViewChange(value: string | number | boolean | undefined) {
  resultView.value = value === 'candidates' ? 'candidates' : 'recommended';
  try {
    await fetchSmartRecommendations(resultView.value === 'candidates');
  } catch (err: unknown) {
    ElMessage.error(errorMessage(err));
  }
}

async function applyRecommendationSettings() {
  if (!normalizedRecommendationCount(true)) {
    return;
  }
  if (!ratioIsValid.value) {
    ElMessage.warning('冲、稳、保比例相加必须等于 100%');
    return;
  }
  try {
    resultView.value = 'recommended';
    await fetchSmartRecommendations(false);
    ElMessage.success('已按新的推荐设置刷新');
  } catch (err: unknown) {
    ElMessage.error(errorMessage(err));
  }
}

onMounted(async () => {
  await fetchSmartRecommendations(false);
  if (volunteerStore.currentForm?.id != null) {
    selectedFormId.value = String(volunteerStore.currentForm.id);
  }
});

async function addToVolunteer(plan: PlanItem, school: SchoolGroup, showMessage = true) {
  if (addedPlanIds.value.has(plan.planId) || addingPlanIds.value.has(plan.planId)) return false;
  const next = new Set(addingPlanIds.value);
  next.add(plan.planId);
  addingPlanIds.value = next;
  try {
    await volunteerStore.addRecommendationToCurrentForm({
      ...plan,
      schoolId: school.schoolId,
      schoolName: school.schoolName,
      schoolCode: school.schoolCode,
      province: school.province,
      city: school.city,
      schoolType: school.schoolType,
    }, candidateStore.candidateProfile?.year || currentYear);
    const selected = new Set(selectedPlanIds.value);
    selected.delete(plan.planId);
    selectedPlanIds.value = selected;
    if (showMessage) {
      const position = addedPosition(plan.planId);
      ElMessage.success(position ? `已加入第 ${position} 志愿` : '已加入志愿表');
    }
    return true;
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : '加入失败';
    if (message.includes('已添加') || message.includes('重复')) {
      if (showMessage) ElMessage.warning('该专业已在志愿表中');
      await volunteerStore.ensureActiveForm(candidateStore.candidateProfile?.year || currentYear);
    } else {
      if (showMessage) ElMessage.error(message);
    }
    return false;
  } finally {
    const done = new Set(addingPlanIds.value);
    done.delete(plan.planId);
    addingPlanIds.value = done;
  }
}

async function batchAddSelected(school: SchoolGroup) {
  const plans = selectedPlans(school);
  if (plans.length === 0) {
    ElMessage.warning('请先勾选要加入的专业');
    return;
  }
  const next = new Set(batchAddingSchoolIds.value);
  next.add(school.schoolId);
  batchAddingSchoolIds.value = next;
  let success = 0;
  try {
    for (const plan of plans) {
      if (await addToVolunteer(plan, school, false)) {
        success += 1;
      }
    }
    if (success > 0) {
      ElMessage.success(`已加入 ${success} 个专业`);
    } else {
      ElMessage.warning('没有新增专业');
    }
  } finally {
    const done = new Set(batchAddingSchoolIds.value);
    done.delete(school.schoolId);
    batchAddingSchoolIds.value = done;
  }
}

function labelType(label?: string) {
  if (label === '冲') return 'danger';
  if (label === '稳') return 'warning';
  if (label === '保') return 'success';
  return 'info';
}
</script>

<template>
  <div class="rec-page">
    <!-- Top summary -->
    <div class="rec-hero">
      <div class="rec-hero__left">
        <h1 class="rec-hero__title">智能推荐</h1>
        <p class="rec-hero__sub" v-if="candidateStore.candidateProfile">
          分数 {{ candidateStore.candidateProfile.score }} ·
          位次 {{ candidateStore.candidateProfile.rank }} ·
          {{ candidateStore.candidateProfile.subjects?.join('、') || '未选科' }}
        </p>
      </div>
      <div class="rec-hero__stats" v-if="volunteerStore.recResult">
        <div class="hero-stat">
          <span class="hero-stat__num">{{ candidateTotal.toLocaleString() }}</span>
          <span class="hero-stat__label">候选专业</span>
        </div>
        <div class="hero-stat">
          <span class="hero-stat__num">{{ recommendedTotal.toLocaleString() }}</span>
          <span class="hero-stat__label">{{ resultView === 'recommended' ? '本次推荐' : '当前展示' }}</span>
        </div>
        <div class="hero-stat">
          <span class="hero-stat__num">{{ volunteerStore.recResult.totalSchools.toLocaleString() }}</span>
          <span class="hero-stat__label">所院校</span>
        </div>
      </div>
    </div>

    <!-- No profile warning -->
    <el-alert v-if="!candidateStore.candidateProfile" type="warning" title="请先填写考生信息"
      description="完善分数、位次和选科后，系统将为您精准推荐" show-icon :closable="false" class="rec-alert">
      <template #default>
        <el-button type="primary" size="small" style="margin-top:12px" @click="router.push('/candidate')">去填写考生信息</el-button>
      </template>
    </el-alert>

    <!-- Filters -->
    <div class="rec-toolbar" v-if="candidateStore.candidateProfile">
      <el-input v-model="keyword" placeholder="搜索院校或专业..." clearable size="large" class="rec-search">
        <template #prefix><span style="color:#a8abb2">🔍</span></template>
      </el-input>
      <el-radio-group v-model="levelFilter" size="default">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="冲">⚡ 冲刺</el-radio-button>
        <el-radio-button value="稳">🎯 稳妥</el-radio-button>
        <el-radio-button value="保">🛡 保底</el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="resultView" size="default" @change="handleResultViewChange">
        <el-radio-button value="recommended">本次推荐</el-radio-button>
        <el-radio-button value="candidates">全部候选</el-radio-button>
      </el-radio-group>
    </div>

    <div class="rec-settings" v-if="candidateStore.candidateProfile">
      <div class="rec-settings__field rec-settings__count">
        <span class="rec-settings__label">推荐数量</span>
        <el-input-number v-model="recommendationSettings.count" :min="1" :max="serverRecommendationLimit" :step="6" controls-position="right" />
        <span class="rec-settings__hint">最多 {{ formatCount(maxRecommendationCount) }}</span>
      </div>
      <div class="rec-settings__field">
        <span class="rec-settings__label">冲</span>
        <el-input-number v-model="recommendationSettings.rushPercent" :min="0" :max="100" :step="5" controls-position="right" />
        <span class="rec-settings__unit">%</span>
      </div>
      <div class="rec-settings__field">
        <span class="rec-settings__label">稳</span>
        <el-input-number v-model="recommendationSettings.stablePercent" :min="0" :max="100" :step="5" controls-position="right" />
        <span class="rec-settings__unit">%</span>
      </div>
      <div class="rec-settings__field">
        <span class="rec-settings__label">保</span>
        <el-input-number v-model="recommendationSettings.safePercent" :min="0" :max="100" :step="5" controls-position="right" />
        <span class="rec-settings__unit">%</span>
      </div>
      <div class="rec-settings__field rec-settings__rush-floor">
        <span class="rec-settings__label">冲刺最低概率</span>
        <el-input-number v-model="recommendationSettings.rushProbabilityMin" :min="0" :max="50" :step="5" controls-position="right" />
        <span class="rec-settings__unit">%</span>
      </div>
      <div class="rec-settings__total" :class="{ invalid: !ratioIsValid }">
        合计 {{ ratioTotal }}%
      </div>
      <el-button type="primary" :disabled="!ratioIsValid" :loading="volunteerStore.loading" @click="applyRecommendationSettings">刷新推荐</el-button>
      <div class="rec-bands">
        <span v-for="band in probabilityBands" :key="band.label" class="rec-band" :class="'rec-band--' + band.label">
          {{ band.label }} {{ band.range }} · {{ band.percent }}%
        </span>
      </div>
      <div class="rec-candidate-summary" v-if="volunteerStore.recResult">
        Eligible {{ eligibleTotal.toLocaleString() }} · Candidate {{ candidateTotal.toLocaleString() }} · Recommended {{ recommendedTotal.toLocaleString() }}
      </div>
    </div>

    <!-- Loading -->
    <div v-if="volunteerStore.loading" class="rec-loading">
      <div class="rec-loading__spinner" />
      <p>正在分析 {{ volunteerStore.recResult?.totalPlans.toLocaleString() || '...' }} 条招生计划...</p>
    </div>

    <!-- Empty -->
    <div v-if="!volunteerStore.loading && schoolGroups.length === 0 && candidateStore.candidateProfile" class="rec-empty">
      <div class="rec-empty__icon">📋</div>
      <h3>暂无匹配结果</h3>
      <p>试试调整筛选条件或放宽限制</p>
    </div>

    <!-- School Groups -->
    <div class="rec-groups" v-if="!volunteerStore.loading && schoolGroups.length > 0">
      <div v-for="school in schoolGroups" :key="school.schoolId" class="school-card">
        <!-- School header -->
        <div class="school-card__header" @click="toggleSchool(school.schoolId)">
          <div class="school-card__avatar">
            {{ school.schoolName.charAt(0) }}
          </div>
          <div class="school-card__info">
            <div class="school-card__name-row">
              <h3 class="school-card__name">{{ school.schoolName }}</h3>
              <el-tag v-if="school.schoolType" size="small" type="info" effect="plain">{{ school.schoolType }}</el-tag>
              <el-tag v-if="school.schoolTag" size="small" effect="plain">{{ school.schoolTag }}</el-tag>
            </div>
            <div class="school-card__meta">
              <span>{{ school.province }} {{ school.city }}</span>
              <span class="school-card__divider">·</span>
              <span>{{ school.eligiblePlanCount }} 个可报专业</span>
              <template v-if="school.minProbability != null">
                <span class="school-card__divider">·</span>
                <span class="school-card__prob">概率 {{ school.minProbability }}% – {{ school.maxProbability }}%</span>
              </template>
            </div>
          </div>
          <div class="school-card__arrow" :class="{ expanded: expandedSchools.has(school.schoolId) }">▾</div>
        </div>

        <!-- Major list -->
        <div class="school-card__plans" v-show="expandedSchools.has(school.schoolId)">
          <div class="plan-bulk">
            <el-checkbox
              :model-value="isSchoolAllSelected(school)"
              :indeterminate="isSchoolIndeterminate(school)"
              :disabled="selectablePlans(school).length === 0"
              @change="value => toggleSchoolSelection(school, value)"
            >
              勾选该院校多个专业
            </el-checkbox>
            <div class="plan-bulk__actions">
              <span class="plan-bulk__count">
                已选 {{ selectedSchoolCount(school) }} 个
              </span>
              <el-button
                size="small"
                type="primary"
                :disabled="selectedSchoolCount(school) === 0"
                :loading="batchAddingSchoolIds.has(school.schoolId)"
                @click.stop="batchAddSelected(school)"
              >
                批量加入志愿表
              </el-button>
            </div>
          </div>
          <div v-for="plan in school.plans" :key="plan.planId" class="plan-row">
            <el-checkbox
              class="plan-row__check"
              :model-value="selectedPlanIds.has(plan.planId)"
              :disabled="addedPlanIds.has(plan.planId)"
              @change="value => togglePlanSelection(plan.planId, value)"
              @click.stop
            />
            <div class="plan-row__left">
              <span class="plan-row__major">{{ plan.majorName }}</span>
              <span class="plan-row__meta">
                {{ plan.enrollmentType === 'NORMAL' ? '普通类' : plan.enrollmentType || '普通类' }}
                <span v-if="plan.campusName"> · {{ plan.campusName }}</span>
              </span>
            </div>
            <div class="plan-row__center">
              <span class="plan-row__stat">🎓 {{ plan.planCount }}人</span>
              <span class="plan-row__stat">📅 {{ plan.duration || 4 }}年</span>
              <span class="plan-row__stat">📖 {{ plan.subjectRequirementText || '不限' }}</span>
              <span class="plan-row__stat" v-if="plan.lastYearMinRank">
                📊 位次 {{ plan.lastYearMinRank }}{{ plan.twoYearMinRank ? '/' + plan.twoYearMinRank : '' }}{{ plan.threeYearMinRank ? '/' + plan.threeYearMinRank : '' }}
              </span>
            </div>
            <div class="plan-row__right">
              <el-button
                size="small"
                type="primary"
                :plain="!addedPlanIds.has(plan.planId)"
                :disabled="addedPlanIds.has(plan.planId)"
                :loading="addingPlanIds.has(plan.planId)"
                @click.stop="addToVolunteer(plan, school)"
              >
                {{ addButtonText(plan.planId) }}
              </el-button>
              <span class="plan-row__tuition" v-if="plan.tuition">{{ (plan.tuition / 10000).toFixed(1) }}万/年</span>
              <span class="plan-row__tuition" v-else>待确认</span>
              <el-tag v-if="plan.label" :type="labelType(plan.label)" size="small" effect="dark">
                {{ plan.label }}
              </el-tag>
              <button
                v-if="plan.probability != null"
                class="plan-row__prob"
                type="button"
                @click.stop="openEvidence(plan, school)"
              >
                {{ formatProbability(plan.probability) }}
              </button>
              <span v-if="plan.planChange && plan.planChange !== '持平'" class="plan-row__change"
                :class="{ up: plan.planChange === '增加' || plan.planChange === '新增', down: plan.planChange === '减少' }">
                {{ plan.planChange }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="evidenceDrawerVisible"
      title="数据依据"
      size="360px"
      append-to-body
    >
      <div v-if="evidencePlan" class="evidence">
        <div class="evidence__headline">
          <div>
            <span>预测机会</span>
            <strong>{{ formatProbability(evidencePlan.probability) }}</strong>
          </div>
          <el-tag :type="labelType(evidencePlan.label)" effect="dark">
            {{ evidencePlan.label || '-' }}
          </el-tag>
        </div>
        <div class="evidence__school">
          <strong>{{ evidenceSchool?.schoolName }}</strong>
          <span>{{ evidencePlan.majorName }}</span>
        </div>
        <div class="evidence__grid">
          <span>你的位次</span>
          <strong>{{ formatRank(candidateStore.candidateProfile?.rank) }}</strong>
          <span>去年最低位次</span>
          <strong>{{ formatRank(evidencePlan.lastYearMinRank) }}</strong>
          <span>今年计划</span>
          <strong>{{ formatPlanCount(evidencePlan.planCount) }}</strong>
          <span>去年计划</span>
          <strong>{{ formatPlanCount(evidencePlan.lastYearPlanCount) }}</strong>
        </div>
        <div class="evidence__section">
          <h3>近3年最低位次趋势</h3>
          <div v-if="evidenceTrendRows(evidencePlan).length" class="evidence__trend">
            <div v-for="row in evidenceTrendRows(evidencePlan)" :key="row.year">
              <span>{{ row.year }}</span>
              <strong>{{ formatRank(row.rank) }}</strong>
            </div>
          </div>
          <div v-else class="evidence__empty">暂无近三年位次数据</div>
        </div>
        <p class="evidence__note">预测结果仅供志愿筛选参考</p>
      </div>
    </el-drawer>

    <div v-if="volunteerStore.currentForm" class="volunteer-dock">
      <transition name="volunteer-panel">
        <section v-if="formPreviewOpen" class="volunteer-preview" data-testid="volunteer-preview" @click.stop>
          <div class="volunteer-preview__header">
            <div>
              <span>当前选择</span>
              <strong>加入到哪张志愿表</strong>
            </div>
            <button type="button" aria-label="收起志愿表" @click="formPreviewOpen = false">×</button>
          </div>
          <el-select
            v-model="selectedFormId"
            class="volunteer-preview__select"
            data-testid="volunteer-form-select"
            placeholder="请选择志愿表"
            :loading="switchingForm"
            @change="selectVolunteerForm"
          >
            <el-option
              v-for="(form, index) in selectableForms"
              :key="String(form.id)"
              :value="String(form.id)"
              :label="`${index + 1}. ${form.name}（${formCount(form)}/${formCapacity(form)}）`"
            />
          </el-select>
          <div class="volunteer-preview__summary">
            <div>
              <strong>{{ currentFormName }}</strong>
              <span>{{ currentFormCount }} / {{ formCapacity(volunteerStore.currentForm) }} 个志愿</span>
            </div>
            <el-tag size="small" type="info">{{ volunteerStore.currentForm.year || currentYear }}</el-tag>
          </div>
          <div v-if="previewFormItems.length" class="volunteer-preview__list">
            <div v-for="(item, index) in previewFormItems" :key="String(item.id || item.planId || index)" class="volunteer-preview__item">
              <span>{{ item.sortOrder ?? item.order ?? index + 1 }}</span>
              <div>
                <strong>{{ item.schoolName }}</strong>
                <small>{{ item.majorName }}</small>
              </div>
              <el-tag v-if="item.label" size="small" :type="labelType(item.label)">{{ item.label }}</el-tag>
            </div>
          </div>
          <div v-else class="volunteer-preview__empty">这张志愿表还没有添加专业</div>
          <div v-if="currentFormItems.length > previewFormItems.length" class="volunteer-preview__more">
            还有 {{ currentFormItems.length - previewFormItems.length }} 个志愿未展开
          </div>
        </section>
      </transition>
      <button
        type="button"
        class="volunteer-float"
        data-testid="volunteer-float-toggle"
        :aria-expanded="formPreviewOpen"
        aria-label="展开当前志愿表"
        @click="toggleFormPreview"
      >
        <div class="volunteer-float__count">{{ currentFormCount }}</div>
        <div class="volunteer-float__text">
          <div class="volunteer-float__title">{{ currentFormName }}</div>
          <div class="volunteer-float__sub">已加入 / {{ formCapacity(volunteerStore.currentForm) }}</div>
        </div>
        <span class="volunteer-float__arrow" :class="{ open: formPreviewOpen }">⌃</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.rec-page { max-width: 1100px; margin: 0 auto; padding: 24px 20px 60px; }

/* Hero */
.rec-hero { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 24px; }
.rec-hero__title { font-size: 28px; font-weight: 700; margin: 0; color: #1a1a2e; }
.rec-hero__sub { margin: 6px 0 0; color: #6b7280; font-size: 14px; }
.rec-hero__stats { display: flex; gap: 32px; }
.hero-stat { text-align: center; }
.hero-stat__num { display: block; font-size: 28px; font-weight: 700; color: #4f46e5; }
.hero-stat__label { font-size: 13px; color: #9ca3af; }
.rec-alert { margin-bottom: 20px; }

/* Toolbar */
.rec-toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; padding: 16px 20px; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.rec-search { width: 320px; }
.rec-settings { display: flex; align-items: center; gap: 12px; margin: -8px 0 20px; padding: 14px 20px; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.06); flex-wrap: wrap; }
.rec-settings__field { display: flex; align-items: center; gap: 6px; }
.rec-settings__label { font-size: 13px; color: #4b5563; white-space: nowrap; }
.rec-settings__unit { font-size: 13px; color: #9ca3af; }
.rec-settings__hint { font-size: 12px; color: #9ca3af; white-space: nowrap; }
.rec-settings__field :deep(.el-input-number) { width: 94px; }
.rec-settings__count :deep(.el-input-number) { width: 128px; }
.rec-settings__rush-floor :deep(.el-input-number) { width: 94px; }
.rec-settings__total { min-width: 78px; font-size: 13px; color: #059669; font-weight: 600; }
.rec-settings__total.invalid { color: #dc2626; }
.rec-bands { flex-basis: 100%; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rec-band { font-size: 12px; padding: 3px 8px; border-radius: 4px; border: 1px solid #e5e7eb; color: #4b5563; background: #f9fafb; }
.rec-band--冲 { color: #b91c1c; border-color: #fecaca; background: #fef2f2; }
.rec-band--稳 { color: #b45309; border-color: #fde68a; background: #fffbeb; }
.rec-band--保 { color: #047857; border-color: #a7f3d0; background: #ecfdf5; }
.rec-candidate-summary { flex-basis: 100%; font-size: 12px; color: #6b7280; }

/* Loading */
.rec-loading { text-align: center; padding: 80px 0; color: #9ca3af; }
.rec-loading__spinner { width: 40px; height: 40px; margin: 0 auto 16px; border: 3px solid #e5e7eb; border-top-color: #4f46e5; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.rec-empty { text-align: center; padding: 80px 0; color: #9ca3af; }
.rec-empty__icon { font-size: 48px; margin-bottom: 16px; }

/* School cards */
.rec-groups { display: flex; flex-direction: column; gap: 12px; }

.school-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.06); overflow: hidden; transition: box-shadow .2s; }
.school-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); }

.school-card__header { display: flex; align-items: center; padding: 16px 20px; cursor: pointer; user-select: none; gap: 14px; }
.school-card__avatar { width: 44px; height: 44px; border-radius: 10px; background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700; flex-shrink: 0; }
.school-card__info { flex: 1; min-width: 0; }
.school-card__name-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.school-card__name { margin: 0; font-size: 16px; font-weight: 600; color: #1f2937; }
.school-card__meta { font-size: 13px; color: #9ca3af; display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.school-card__divider { color: #d1d5db; }
.school-card__prob { color: #4f46e5; font-weight: 500; }
.school-card__arrow { color: #9ca3af; font-size: 18px; transition: transform .2s; }
.school-card__arrow.expanded { transform: rotate(180deg); }

/* Plans */
.school-card__plans { border-top: 1px solid #f3f4f6; }
.plan-bulk { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 20px 12px 78px; background: #f8fafc; border-bottom: 1px solid #eef2f7; }
.plan-bulk__actions { display: flex; align-items: center; gap: 10px; }
.plan-bulk__count { font-size: 13px; color: #6b7280; white-space: nowrap; }
.plan-row { display: flex; align-items: center; padding: 12px 20px 12px 78px; transition: background .15s; gap: 14px; border-bottom: 1px solid #f9fafb; }
.plan-row:last-child { border-bottom: none; }
.plan-row:hover { background: #f9fafb; }
.plan-row__check { flex-shrink: 0; }
.plan-row__left { flex: 1; min-width: 0; }
.plan-row__major { display: block; font-size: 14px; font-weight: 500; color: #1f2937; margin-bottom: 2px; }
.plan-row__meta { font-size: 12px; color: #9ca3af; }
.plan-row__center { display: flex; gap: 16px; flex-shrink: 0; }
.plan-row__stat { font-size: 12px; color: #6b7280; white-space: nowrap; }
.plan-row__right { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.plan-row__tuition { font-size: 13px; color: #6b7280; white-space: nowrap; }
.plan-row__prob { border: 0; background: transparent; padding: 0; font-size: 14px; font-weight: 600; color: #4f46e5; cursor: pointer; white-space: nowrap; }
.plan-row__prob:hover { color: #3730a3; text-decoration: underline; }
.plan-row__change { font-size: 11px; padding: 2px 6px; border-radius: 4px; }
.plan-row__change.up { color: #059669; background: #d1fae5; }
.plan-row__change.down { color: #dc2626; background: #fee2e2; }
.evidence { color: #1f2937; }
.evidence__headline { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 18px; border-bottom: 1px solid #eef2f7; }
.evidence__headline span { display: block; font-size: 13px; color: #6b7280; margin-bottom: 4px; }
.evidence__headline strong { font-size: 28px; line-height: 1; color: #111827; }
.evidence__school { display: flex; flex-direction: column; gap: 4px; margin: 18px 0; }
.evidence__school strong { font-size: 16px; }
.evidence__school span { font-size: 13px; color: #6b7280; }
.evidence__grid { display: grid; grid-template-columns: 1fr auto; gap: 12px 18px; padding: 16px 0; border-top: 1px solid #eef2f7; border-bottom: 1px solid #eef2f7; }
.evidence__grid span { color: #6b7280; font-size: 14px; }
.evidence__grid strong { font-size: 14px; font-weight: 600; }
.evidence__section { margin-top: 18px; }
.evidence__section h3 { margin: 0 0 12px; font-size: 15px; font-weight: 600; }
.evidence__trend { display: flex; flex-direction: column; gap: 8px; }
.evidence__trend div { display: flex; justify-content: space-between; padding: 8px 10px; background: #f8fafc; border-radius: 6px; font-size: 14px; }
.evidence__empty { color: #9ca3af; font-size: 13px; padding: 10px 0; }
.evidence__note { margin-top: 22px; color: #9ca3af; font-size: 12px; }
.volunteer-dock { position: fixed; right: 28px; bottom: 28px; z-index: 20; display: flex; flex-direction: column; align-items: flex-end; gap: 10px; }
.volunteer-float { border: 0; display: flex; align-items: center; gap: 12px; min-width: 188px; padding: 12px 14px; background: #111827; color: #fff; border-radius: 10px; box-shadow: 0 12px 30px rgba(0,0,0,.18); cursor: pointer; text-align: left; }
.volunteer-float:hover { background: #172033; }
.volunteer-float__count { width: 34px; height: 34px; border-radius: 50%; background: #4f46e5; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.volunteer-float__text { flex: 1; min-width: 0; }
.volunteer-float__title { font-size: 13px; font-weight: 600; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.volunteer-float__sub { font-size: 12px; color: #cbd5e1; }
.volunteer-float__arrow { color: #94a3b8; font-size: 16px; transition: transform .2s; }
.volunteer-float__arrow.open { transform: rotate(180deg); }
.volunteer-preview { width: 360px; overflow: hidden; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px; box-shadow: 0 20px 55px rgba(15,23,42,.22); }
.volunteer-preview__header { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px 12px; }
.volunteer-preview__header div { display: flex; flex-direction: column; gap: 2px; }
.volunteer-preview__header span { font-size: 11px; color: #9ca3af; }
.volunteer-preview__header strong { font-size: 15px; color: #111827; }
.volunteer-preview__header button { border: 0; background: #f3f4f6; color: #6b7280; width: 28px; height: 28px; border-radius: 8px; cursor: pointer; font-size: 18px; line-height: 1; }
.volunteer-preview__select { width: calc(100% - 36px); margin: 0 18px 14px; }
.volunteer-preview__summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 18px; background: linear-gradient(135deg,#f5f7ff,#f8fafc); border-top: 1px solid #eef2f7; border-bottom: 1px solid #eef2f7; }
.volunteer-preview__summary div { display: flex; flex-direction: column; min-width: 0; }
.volunteer-preview__summary strong { color: #1f2937; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.volunteer-preview__summary span { color: #6b7280; font-size: 12px; }
.volunteer-preview__list { max-height: 300px; overflow-y: auto; padding: 6px 0; }
.volunteer-preview__item { display: grid; grid-template-columns: 28px minmax(0,1fr) auto; align-items: center; gap: 10px; padding: 9px 18px; }
.volunteer-preview__item:hover { background: #f8fafc; }
.volunteer-preview__item > span { width: 26px; height: 26px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: #eef2ff; color: #4f46e5; font-size: 11px; font-weight: 700; }
.volunteer-preview__item div { min-width: 0; display: flex; flex-direction: column; }
.volunteer-preview__item strong { color: #374151; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.volunteer-preview__item small { color: #9ca3af; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.volunteer-preview__empty { padding: 28px 18px; text-align: center; color: #9ca3af; font-size: 13px; }
.volunteer-preview__more { padding: 9px 18px 12px; border-top: 1px solid #f3f4f6; color: #9ca3af; text-align: center; font-size: 11px; }
.volunteer-panel-enter-active,.volunteer-panel-leave-active { transition: opacity .18s ease, transform .18s ease; transform-origin: bottom right; }
.volunteer-panel-enter-from,.volunteer-panel-leave-to { opacity: 0; transform: translateY(8px) scale(.98); }
@media (max-width: 900px) {
  .plan-bulk,
  .plan-row { padding-left: 20px; }
  .plan-row { align-items: flex-start; flex-wrap: wrap; }
  .plan-row__center { flex-basis: 100%; flex-wrap: wrap; padding-left: 30px; }
  .plan-row__right { flex-basis: 100%; justify-content: flex-end; flex-wrap: wrap; }
  .volunteer-dock { right: 14px; bottom: 14px; }
  .volunteer-preview { width: min(360px, calc(100vw - 28px)); }
}
</style>

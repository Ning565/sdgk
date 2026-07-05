<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useCandidateStore } from '@/stores/candidate';
import { useVolunteerStore, type SchoolGroup } from '@/stores/volunteer';
import { ElMessage } from 'element-plus';

const router = useRouter();
const candidateStore = useCandidateStore();
const volunteerStore = useVolunteerStore();
const currentYear = new Date().getFullYear();

const keyword = ref('');
const levelFilter = ref('');
const expandedSchools = ref<Set<number>>(new Set());
const addingPlanIds = ref<Set<number>>(new Set());
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
const maxRecommendationCount = computed(() => {
  const total = volunteerStore.recResult?.totalPlans;
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
      const plans = g.plans.filter(p => p.label !== '冲' || p.probability == null || p.probability >= rushFloor);
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
  return groups;
});

const currentFormItems = computed(() => volunteerStore.currentForm?.items || volunteerStore.currentForm?.choices || []);
const addedPlanIds = computed(() => new Set(currentFormItems.value.map(item => item.planId).filter(Boolean)));
const currentFormName = computed(() => volunteerStore.currentForm?.name || '当前志愿表');
const currentFormCount = computed(() => volunteerStore.currentForm?.itemCount ?? currentFormItems.value.length);

watch(
  () => recommendationSettings.value.count,
  value => {
    if (Number(value) > maxRecommendationCount.value) {
      recommendationSettings.value.count = maxRecommendationCount.value;
      ElMessage.warning(`当前筛选条件最多可推荐 ${formatCount(maxRecommendationCount.value)} 个专业`);
    }
  }
);

function formatCount(value: number) {
  return value.toLocaleString('zh-CN');
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

async function fetchSmartRecommendations() {
  await candidateStore.fetchProfile(currentYear);
  if (candidateStore.candidateProfile) {
    const p = candidateStore.candidateProfile;
    const recommendationCount = normalizedRecommendationCount();
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
    });
    await runPrediction();
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
    await fetchSmartRecommendations();
    ElMessage.success('已按新的推荐设置刷新');
  } catch (err: unknown) {
    ElMessage.error(errorMessage(err));
  }
}

onMounted(async () => {
  await fetchSmartRecommendations();
});

async function addToVolunteer(plan: any, school: SchoolGroup) {
  if (addedPlanIds.value.has(plan.planId) || addingPlanIds.value.has(plan.planId)) return;
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
    ElMessage.success('已加入志愿表');
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : '加入失败';
    if (message.includes('已添加') || message.includes('重复')) {
      ElMessage.warning('该专业已在志愿表中');
      await volunteerStore.ensureActiveForm(candidateStore.candidateProfile?.year || currentYear);
    } else {
      ElMessage.error(message);
    }
  } finally {
    const done = new Set(addingPlanIds.value);
    done.delete(plan.planId);
    addingPlanIds.value = done;
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
          <span class="hero-stat__num">{{ volunteerStore.recResult.totalPlans.toLocaleString() }}</span>
          <span class="hero-stat__label">可选专业</span>
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
          <div v-for="plan in school.plans" :key="plan.planId" class="plan-row">
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
                {{ addedPlanIds.has(plan.planId) ? '已加入' : 'Add' }}
              </el-button>
              <span class="plan-row__tuition" v-if="plan.tuition">{{ (plan.tuition / 10000).toFixed(1) }}万/年</span>
              <span class="plan-row__tuition" v-else>待确认</span>
              <el-tag v-if="plan.label" :type="labelType(plan.label)" size="small" effect="dark">
                {{ plan.label }}
              </el-tag>
              <span v-if="plan.probability != null" class="plan-row__prob">{{ plan.probability }}%</span>
              <span v-if="plan.planChange && plan.planChange !== '持平'" class="plan-row__change"
                :class="{ up: plan.planChange === '增加' || plan.planChange === '新增', down: plan.planChange === '减少' }">
                {{ plan.planChange }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="volunteerStore.currentForm" class="volunteer-float" @click="router.push('/volunteer-forms/' + volunteerStore.currentForm.id)">
      <div class="volunteer-float__count">{{ currentFormCount }}</div>
      <div>
        <div class="volunteer-float__title">{{ currentFormName }}</div>
        <div class="volunteer-float__sub">已加入 / 96</div>
      </div>
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
.plan-row { display: flex; align-items: center; padding: 12px 20px 12px 78px; transition: background .15s; gap: 16px; border-bottom: 1px solid #f9fafb; }
.plan-row:last-child { border-bottom: none; }
.plan-row:hover { background: #f9fafb; }
.plan-row__left { flex: 1; min-width: 0; }
.plan-row__major { display: block; font-size: 14px; font-weight: 500; color: #1f2937; margin-bottom: 2px; }
.plan-row__meta { font-size: 12px; color: #9ca3af; }
.plan-row__center { display: flex; gap: 16px; flex-shrink: 0; }
.plan-row__stat { font-size: 12px; color: #6b7280; white-space: nowrap; }
.plan-row__right { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.plan-row__tuition { font-size: 13px; color: #6b7280; white-space: nowrap; }
.plan-row__prob { font-size: 14px; font-weight: 600; color: #4f46e5; }
.plan-row__change { font-size: 11px; padding: 2px 6px; border-radius: 4px; }
.plan-row__change.up { color: #059669; background: #d1fae5; }
.plan-row__change.down { color: #dc2626; background: #fee2e2; }
.volunteer-float { position: fixed; right: 28px; bottom: 28px; display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: #111827; color: #fff; border-radius: 8px; box-shadow: 0 12px 30px rgba(0,0,0,.18); cursor: pointer; z-index: 20; }
.volunteer-float__count { width: 34px; height: 34px; border-radius: 50%; background: #4f46e5; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.volunteer-float__title { font-size: 13px; font-weight: 600; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.volunteer-float__sub { font-size: 12px; color: #cbd5e1; }
</style>

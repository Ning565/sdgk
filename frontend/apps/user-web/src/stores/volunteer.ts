import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { VolunteerFormDTO, VolunteerFormDetailDTO, RecommendationRequest } from '@gaokao/shared-types';
import { volunteerApi } from '@gaokao/api-client';

export interface SchoolGroup {
  schoolId: number;
  schoolName: string;
  schoolCode: string;
  province?: string;
  city?: string;
  schoolType?: string;
  schoolTag?: string;
  eligiblePlanCount: number;
  minProbability?: number;
  maxProbability?: number;
  plans: PlanItem[];
}

export interface PlanItem {
  planId: number;
  schoolId: number;
  schoolName: string;
  schoolCode?: string;
  majorName: string;
  majorCode?: string;
  majorCategory?: string;
  majorSubcategory?: string;
  province?: string;
  city?: string;
  schoolType?: string;
  enrollmentType?: string;
  educationLevel?: string;
  planCount: number;
  tuition?: number;
  campusName?: string;
  duration?: number;
  subjectRequirementText?: string;
  planStatus?: string;
  planChange?: string;
  probability?: number;
  lastYearMinRank?: number;
  twoYearMinRank?: number;
  threeYearMinRank?: number;
  lastYearPlanCount?: number;
  label?: string;
  recommend_rank?: number;
  predictedRank?: number;
}

export interface RecommendationResult {
  schoolGroups: SchoolGroup[];
  totalPlans: number;
  eligiblePlanCount?: number;
  candidatePlanCount?: number;
  recommendedPlanCount?: number;
  totalSchools: number;
}

export const useVolunteerStore = defineStore('volunteer', () => {
  const forms = ref<VolunteerFormDTO[]>([]);
  const currentForm = ref<VolunteerFormDetailDTO | null>(null);
  const recResult = ref<RecommendationResult | null>(null);
  const loading = ref(false);

  async function fetchForms() {
    loading.value = true;
    try {
      const res = await volunteerApi.getForms();
      forms.value = res.data;
    } finally {
      loading.value = false;
    }
  }

  async function fetchFormDetail(formId: string) {
    loading.value = true;
    try {
      const res = await volunteerApi.getFormDetail(formId);
      currentForm.value = res.data;
    } finally {
      loading.value = false;
    }
  }

  async function createForm(name: string) {
    const res = await volunteerApi.createForm(name);
    forms.value.push(res.data);
    return res.data;
  }

  async function createFormForYear(name: string, year?: number) {
    const res = await volunteerApi.createForm(name, year);
    forms.value.push(res.data);
    return res.data;
  }

  async function ensureActiveForm(year?: number) {
    if (forms.value.length === 0) {
      await fetchForms();
    }
    const matched = forms.value.find(form => !year || form.year === year) || forms.value[0];
    if (matched) {
      await fetchFormDetail(String(matched.id));
      return matched;
    }
    const created = await createFormForYear('我的志愿表', year);
    await fetchFormDetail(String(created.id));
    return created;
  }

  async function addRecommendationToCurrentForm(plan: PlanItem, year?: number) {
    const form = currentForm.value || await ensureActiveForm(year).then(() => currentForm.value);
    if (!form) {
      throw new Error('请先创建志愿表');
    }
    const res = await volunteerApi.addItem(String(form.id), {
      planId: plan.planId,
      expectedVersion: form.version || 1,
      clientOperationId: `add-${form.id}-${plan.planId}`,
      schoolId: plan.schoolId,
      schoolName: plan.schoolName,
      schoolCode: plan.schoolCode,
      majorName: plan.majorName,
      majorCode: plan.majorCode,
      province: plan.province,
      city: plan.city,
      schoolType: plan.schoolType,
      enrollmentType: plan.enrollmentType,
      probability: plan.probability,
      label: plan.label,
      planCount: plan.planCount,
      tuition: plan.tuition,
      subjectRequirementText: plan.subjectRequirementText,
      planStatus: plan.planStatus,
      lastYearMinRank: plan.lastYearMinRank,
      predictedRank: plan.predictedRank,
    });
    currentForm.value = res.data;
    const i = forms.value.findIndex(f => String(f.id) === String(res.data.id));
    if (i >= 0) {
      forms.value[i] = { ...forms.value[i], ...res.data };
    }
    return res.data;
  }

  async function fetchRecommendations(data: RecommendationRequest) {
    loading.value = true;
    try {
      const res = await volunteerApi.searchRecommendations(data);
      recResult.value = res.data || null;
    } finally {
      loading.value = false;
    }
  }

  return {
    forms, currentForm, recResult, loading,
    fetchForms, fetchFormDetail, createForm, createFormForYear, ensureActiveForm,
    addRecommendationToCurrentForm, fetchRecommendations,
  };
});

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { useCandidateStore } from '@/stores/candidate';
import { PageHeader } from '@gaokao/shared-ui';
import { ElMessage } from 'element-plus';

const store = useCandidateStore();
const formRef = ref();
const isEditing = ref(false);

const currentYear = new Date().getFullYear();

const form = reactive({
  year: currentYear,
  score: 0,
  rank: undefined as number | undefined,
  rankSource: 'AUTO' as 'AUTO' | 'MANUAL',
  subjects: [] as string[],
  educationLevel: 'UNDERGRADUATE' as 'UNDERGRADUATE' | 'VOCATIONAL' | 'UNLIMITED',
  preferredRegions: [] as string[],
  preferredMajors: [] as string[],
  excludedMajors: [] as string[],
  tuitionMax: undefined as number | undefined,
  schoolNature: 'UNLIMITED' as 'PUBLIC' | 'PRIVATE' | 'UNLIMITED',
  acceptJointProgram: false,
});

const subjectOptions = [
  '物理', '化学', '生物', '历史', '地理', '政治',
];

const educationLevelOptions = [
  { label: '本科', value: 'UNDERGRADUATE' },
  { label: '专科', value: 'VOCATIONAL' },
  { label: '不限', value: 'UNLIMITED' },
];

const schoolNatureOptions = [
  { label: '公办', value: 'PUBLIC' },
  { label: '民办', value: 'PRIVATE' },
  { label: '不限', value: 'UNLIMITED' },
];

const regionOptions = [
  '北京', '天津', '河北', '山西', '内蒙古',
  '辽宁', '吉林', '黑龙江',
  '上海', '江苏', '浙江', '安徽', '福建', '江西', '山东',
  '河南', '湖北', '湖南', '广东', '广西', '海南',
  '重庆', '四川', '贵州', '云南', '西藏',
  '陕西', '甘肃', '青海', '宁夏', '新疆',
];

const majorCategoryOptions = [
  '交通运输大类', '公共管理与服务大类', '公安与司法大类', '农林牧渔大类', '医药卫生大类',
  '土木建筑大类', '教育与体育大类', '文化艺术大类', '新闻传播大类', '旅游大类',
  '水利大类', '生物与化工大类', '电子与信息大类', '能源动力与材料大类', '装备制造大类',
  '财经商贸大类', '资源环境与安全大类', '轻工纺织大类', '食品药品与粮食大类',
];

const majorSubcategoryOptions = [
  '中医药类', '临床医学类', '体育类', '健康管理与促进类', '公共事业类', '公共卫生与卫生管理类',
  '公共服务类', '公共管理类', '农业类', '包装类', '化工技术类', '医学技术类', '印刷类',
  '司法技术类', '土建施工类', '地质类', '城乡规划与管理类', '城市轨道交通类', '安全类',
  '安全防范类', '工商管理类', '市政工程类', '广播影视类', '康复治疗类', '建筑材料类',
  '建筑设备类', '建筑设计类', '建设工程管理类', '房地产类', '护理类', '教育类', '文化服务类',
  '文秘类', '新能源发电工程类', '新闻出版类', '旅游类', '有色金属材料类', '机械设计制造类',
  '机电设备类', '林业类', '民族文化艺术类', '气象类', '水上运输类', '水利工程与管理类',
  '水利水电设备类', '水土保持与水环境类', '水文水资源类', '汽车制造类', '法律实务类',
  '法律执行类', '测绘地理信息类', '渔业类', '热能与发电工程类', '煤炭类', '物流类',
  '环境保护类', '生物技术类', '电力技术类', '电子信息类', '电子商务类', '畜牧业类',
  '眼视光类', '石油与天然气类', '粮食类', '纺织服装类', '经济贸易类', '统计类',
  '自动化类', '航空装备类', '航空运输类', '船舶与海洋工程装备类', '艺术设计类',
  '药品与医疗器械类', '药学类', '表演艺术类', '计算机类', '语言类', '财务会计类',
  '财政税务类', '资源勘查类', '轨道装备类', '轻化工类', '通信类', '道路运输类',
  '邮政类', '金属与非金属矿类', '金融类', '铁道运输类', '集成电路类', '非金属材料类',
  '食品类', '餐饮类', '黑色金属材料类',
];

onMounted(async () => {
  await store.fetchProfile(currentYear);
  if (store.candidateProfile) {
    const p = store.candidateProfile;
    form.year = p.year;
    form.score = p.score ?? 0;
    form.rank = p.rank;
    form.rankSource = p.rankSource ?? 'AUTO';
    form.subjects = p.subjects ?? [];
    form.educationLevel = p.educationLevel ?? 'UNDERGRADUATE';
    form.preferredRegions = p.preferredRegions ?? [];
    form.preferredMajors = p.preferredMajors ?? [];
    form.excludedMajors = p.excludedMajors ?? [];
    form.tuitionMax = p.tuitionMax;
    form.schoolNature = p.schoolNature ?? 'UNLIMITED';
    form.acceptJointProgram = p.acceptJointProgram ?? false;
  }
});

async function handleResolveRank() {
  if (!form.score || form.score <= 0) return;
  try {
    const res = await store.resolveScoreRank(form.year, form.score);
    if (res.code === 0 && res.data) {
      form.rank = res.data.cumulativeCount;
      form.rankSource = 'AUTO';
      ElMessage.success(`位次已匹配：第 ${res.data.cumulativeCount} 名`);
    } else {
      ElMessage.warning(res.message || '未找到对应位次，请手动输入');
    }
  } catch {
    ElMessage.error('位次查询失败');
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  if (!form.rank || form.rank <= 0) {
    ElMessage.warning('请先输入分数并查询位次，或手动填写排名');
    return;
  }

  try {
    await store.updateProfile(form.year, form);
    ElMessage.success('保存成功');
    isEditing.value = false;
  } catch (err: unknown) {
    const msg = (err instanceof Error) ? err.message : '保存失败';
    ElMessage.error(msg);
  }
}
</script>

<template>
  <div class="candidate-page">
    <PageHeader title="考生信息" description="请如实填写您的考生信息，用于生成精准的志愿推荐" />

    <el-card v-loading="store.loading" class="candidate-card">
      <template v-if="store.candidateProfile && !isEditing">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="高考年份">{{ store.candidateProfile.year }}</el-descriptions-item>
          <el-descriptions-item label="总分">{{ store.candidateProfile.score }} 分</el-descriptions-item>
          <el-descriptions-item label="全省排名">
            第 {{ store.candidateProfile.rank }} 名
            <el-tag size="small" :type="store.candidateProfile.rankSource === 'AUTO' ? 'info' : 'warning'">
              {{ store.candidateProfile.rankSource === 'AUTO' ? '自动' : '手动' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="选考科目">{{ store.candidateProfile.subjects?.join('、') || '--' }}</el-descriptions-item>
          <el-descriptions-item label="学历层次">
            {{ store.candidateProfile.educationLevel === 'UNDERGRADUATE' ? '本科' : store.candidateProfile.educationLevel === 'VOCATIONAL' ? '专科' : '不限' }}
          </el-descriptions-item>
          <el-descriptions-item label="意向地区">{{ store.candidateProfile.preferredRegions?.join('、') || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="意向专业">{{ store.candidateProfile.preferredMajors?.join('、') || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="学费上限">{{ store.candidateProfile.tuitionMax ? store.candidateProfile.tuitionMax + '元' : '不限' }}</el-descriptions-item>
          <el-descriptions-item label="院校性质">
            {{ store.candidateProfile.schoolNature === 'PUBLIC' ? '公办' : store.candidateProfile.schoolNature === 'PRIVATE' ? '民办' : '不限' }}
          </el-descriptions-item>
          <el-descriptions-item label="中外合作">{{ store.candidateProfile.acceptJointProgram ? '接受' : '不接受' }}</el-descriptions-item>
        </el-descriptions>
        <div class="candidate-card__actions">
          <el-button type="primary" @click="isEditing = true">修改信息</el-button>
        </div>
      </template>

      <template v-else>
        <el-form ref="formRef" :model="form" label-width="100px" :disabled="store.loading">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="高考年份" prop="year" :rules="[{ required: true, message: '请选择年份' }]">
                <el-input-number v-model="form.year" :min="2020" :max="currentYear" class="w-full" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="总分" prop="score" :rules="[{ required: true, message: '请输入总分' }]">
                <div style="display:flex;gap:8px">
                  <el-input-number v-model="form.score" :min="0" :max="750" class="w-full" @change="handleResolveRank" />
                  <el-button type="primary" size="default" @click="handleResolveRank">查询位次</el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="全省排名" prop="rank">
                <el-input-number v-model="form.rank" :min="1" class="w-full" placeholder="可自动根据分数查询" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排名来源">
                <el-radio-group v-model="form.rankSource">
                  <el-radio value="AUTO">自动</el-radio>
                  <el-radio value="MANUAL">手动</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="选考科目（必选3门）" prop="subjects"
            :rules="[{ required: true, type: 'array', min: 3, max: 3, message: '必须且只能选择3门选考科目', trigger: 'change' }]">
            <el-checkbox-group v-model="form.subjects" :max="3">
              <el-checkbox v-for="s in subjectOptions" :key="s" :label="s" :value="s" :disabled="form.subjects.length >= 3 && !form.subjects.includes(s)" />
            </el-checkbox-group>
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="学历层次">
                <el-select v-model="form.educationLevel" class="w-full">
                  <el-option v-for="o in educationLevelOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="院校性质">
                <el-select v-model="form.schoolNature" class="w-full">
                  <el-option v-for="o in schoolNatureOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="学费上限">
                <el-input-number v-model="form.tuitionMax" :min="0" :step="1000" class="w-full" placeholder="不限" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="中外合作">
                <el-switch v-model="form.acceptJointProgram" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="意向地区">
            <el-select v-model="form.preferredRegions" multiple filterable placeholder="不限（可多选）" class="w-full">
              <el-option v-for="region in regionOptions" :key="region" :label="region" :value="region" />
            </el-select>
          </el-form-item>

          <el-form-item label="意向专业">
            <el-select v-model="form.preferredMajors" multiple filterable placeholder="不限（可多选）" class="w-full">
              <el-option-group label="专科大类">
                <el-option v-for="m in majorCategoryOptions" :key="m" :label="m" :value="m" />
              </el-option-group>
              <el-option-group label="专业类">
                <el-option v-for="m in majorSubcategoryOptions" :key="m" :label="m" :value="m" />
              </el-option-group>
            </el-select>
          </el-form-item>

          <el-form-item label="排除专业">
            <el-select v-model="form.excludedMajors" multiple filterable placeholder="无（可多选）" class="w-full">
              <el-option-group label="专科大类">
                <el-option v-for="m in majorCategoryOptions" :key="m" :label="m" :value="m" />
              </el-option-group>
              <el-option-group label="专业类">
                <el-option v-for="m in majorSubcategoryOptions" :key="m" :label="m" :value="m" />
              </el-option-group>
            </el-select>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleSave">保存</el-button>
            <el-button v-if="store.candidateProfile" @click="isEditing = false">取消</el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.candidate-page {
  padding-bottom: 40px;
}
.candidate-card {
  max-width: 800px;
}
.candidate-card__actions {
  margin-top: 20px;
}
.w-full {
  width: 100%;
}
</style>

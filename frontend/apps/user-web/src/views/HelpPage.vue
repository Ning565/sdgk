<script setup lang="ts">
import { PageHeader } from '@gaokao/shared-ui';

const guideSections = [
  {
    title: '当前范围',
    items: [
      '当前智能推荐只按专科数据使用，本科推荐暂不作为主要功能。',
      '推荐结果用于辅助筛选，正式填报前仍需以山东省教育招生考试院和院校招生章程为准。',
    ],
  },
  {
    title: '基本流程',
    items: [
      '先在“考生信息”填写分数、位次、选科、地区、学费、院校性质等偏好。',
      '进入“智能推荐”查看院校和专业，可按关键词、冲/稳/保筛选。',
      '确认想保留的专业后，点击 Add 加入当前志愿表。',
      '进入“我的志愿表”调整顺序，并使用志愿检查查看选科、学费、停招等风险提示。',
    ],
  },
  {
    title: '页面按钮',
    items: [
      'Add：把该专业加入当前志愿表。',
      '已添加：该专业已经在当前志愿表中，不能重复加入。',
      '刷新推荐：按当前推荐数量、冲稳保比例和最低冲刺概率重新生成推荐池。',
      '志愿检查：检查志愿表中可能不符合规则或偏好的项目。',
    ],
  },
];
</script>

<template>
  <div class="help-page">
    <PageHeader title="使用说明" description="当前平台的简要操作说明" />

    <section class="help-intro">
      <h2>一句话流程</h2>
      <p>填考生信息，看专科智能推荐，点 Add 加入志愿表，再到志愿表里排序和检查。</p>
    </section>

    <section class="help-section" v-for="section in guideSections" :key="section.title">
      <h2>{{ section.title }}</h2>
      <ul>
        <li v-for="item in section.items" :key="item">{{ item }}</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.help-page {
  display: grid;
  gap: 16px;
}

.help-intro,
.help-section {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.help-intro h2,
.help-section h2 {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.help-intro p {
  margin: 0;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}

.help-section ul {
  margin: 0;
  padding-left: 20px;
  color: var(--el-text-color-regular);
  line-height: 1.9;
}

.help-section li + li {
  margin-top: 4px;
}
</style>

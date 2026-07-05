<script setup lang="ts">
import { ref } from 'vue';
import { PageHeader } from '@gaokao/shared-ui';

const loading = ref(false);
const logs = ref<any[]>([]);

const queryParams = ref({
  page: 1,
  size: 20,
  action: '',
  username: '',
});

// Simulated data for placeholder
const totalCount = ref(0);
</script>

<template>
  <div class="audit-logs-page">
    <PageHeader title="审计日志" description="查看系统操作审计记录" />

    <el-card>
      <div class="audit-logs-page__filters">
        <el-input
          v-model="queryParams.username"
          placeholder="搜索用户名"
          clearable
          style="width: 200px"
        />
        <el-select
          v-model="queryParams.action"
          placeholder="操作类型"
          clearable
          style="width: 180px"
        >
          <el-option label="登录" value="LOGIN" />
          <el-option label="登出" value="LOGOUT" />
          <el-option label="创建" value="CREATE" />
          <el-option label="更新" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
          <el-option label="导入" value="IMPORT" />
          <el-option label="发布" value="PUBLISH" />
          <el-option label="导出" value="EXPORT" />
        </el-select>
        <el-button type="primary">查询</el-button>
      </div>

      <el-table :data="logs" border stripe style="margin-top: 16px">
        <el-table-column prop="id" label="ID" width="200" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="action" label="操作" width="100" />
        <el-table-column prop="resource" label="资源" width="120" />
        <el-table-column prop="detail" label="详情" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="traceId" label="Trace ID" width="200" />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>

      <el-empty v-if="logs.length === 0" description="暂无审计日志" />

      <div style="margin-top: 16px; text-align: right">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :total="totalCount"
          background
          layout="total, prev, pager, next"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.audit-logs-page__filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>

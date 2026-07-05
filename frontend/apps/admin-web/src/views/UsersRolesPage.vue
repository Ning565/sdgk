<script setup lang="ts">
import { ref } from 'vue';
import { PageHeader } from '@gaokao/shared-ui';
import { ElMessage } from 'element-plus';
import { UserRole } from '@gaokao/shared-types';

const users = ref([
  { id: '1', username: 'admin', nickname: '系统管理员', role: UserRole.SUPER_ADMIN, province: '--', isActive: true, lastLoginAt: '2025-06-15 10:30', createdAt: '2025-01-01' },
  { id: '2', username: 'user001', nickname: '张三', role: UserRole.CANDIDATE, province: '广东', isActive: true, lastLoginAt: '2025-06-14 15:20', createdAt: '2025-03-15' },
  { id: '3', username: 'user002', nickname: '李四', role: UserRole.CANDIDATE, province: '江苏', isActive: false, lastLoginAt: '2025-05-01 09:00', createdAt: '2025-02-20' },
]);

const roleOptions = [
  { label: '超级管理员', value: UserRole.SUPER_ADMIN },
  { label: '管理员', value: UserRole.ADMIN },
  { label: '考生', value: UserRole.CANDIDATE },
];

const roleTagMap: Record<string, string> = {
  [UserRole.SUPER_ADMIN]: 'danger',
  [UserRole.ADMIN]: 'warning',
  [UserRole.CANDIDATE]: 'info',
};

const roleLabelMap: Record<string, string> = {
  [UserRole.SUPER_ADMIN]: '超级管理员',
  [UserRole.ADMIN]: '管理员',
  [UserRole.CANDIDATE]: '考生',
};

function handleRoleChange(user: any, newRole: UserRole) {
  user.role = newRole;
  ElMessage.success('角色修改成功');
}

function handleToggleStatus(user: any) {
  user.isActive = !user.isActive;
  ElMessage.success(user.isActive ? '用户已启用' : '用户已禁用');
}
</script>

<template>
  <div class="users-roles-page">
    <PageHeader title="用户角色" description="管理系统用户和角色权限" />

    <el-card>
      <el-table :data="users" border stripe>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              @change="(val: UserRole) => handleRoleChange(row, val)"
            >
              <el-option
                v-for="opt in roleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="province" label="省份" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'">
              {{ row.isActive ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="160" />
        <el-table-column prop="createdAt" label="创建时间" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              :type="row.isActive ? 'danger' : 'success'"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.isActive ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

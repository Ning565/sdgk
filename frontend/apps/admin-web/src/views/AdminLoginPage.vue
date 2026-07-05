<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAdminAuthStore } from '@/stores/auth';
import { ElMessage } from 'element-plus';

const router = useRouter();
const route = useRoute();
const authStore = useAdminAuthStore();

const formRef = ref();
const loading = ref(false);
const loginForm = reactive({
  username: '',
  password: '',
});

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    await authStore.login(loginForm);
    ElMessage.success('登录成功');
    const redirect = (route.query.redirect as string) || '/admin/dashboard';
    router.push(redirect);
  } catch {
    ElMessage.error('账号或密码错误');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="admin-login-page">
    <div class="admin-login-card">
      <div class="admin-login-card__header">
        <div class="admin-login-card__logo">G</div>
        <h1 class="admin-login-card__title">管理后台</h1>
        <p class="admin-login-card__subtitle">高考志愿通数据管理系统</p>
      </div>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        label-position="top"
        size="large"
      >
        <el-form-item label="管理员账号" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入管理员账号" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="admin-login-card__btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.admin-login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1a1a2e;
}
.admin-login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.admin-login-card__header {
  text-align: center;
  margin-bottom: 32px;
}
.admin-login-card__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  background: #1a1a2e;
  color: #fff;
  border-radius: 14px;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 12px;
}
.admin-login-card__title {
  margin: 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
}
.admin-login-card__subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.admin-login-card__btn {
  width: 100%;
}
</style>

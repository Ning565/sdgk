<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { ElMessage } from 'element-plus';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const formRef = ref();
const loading = ref(false);
const isRegister = ref(false);

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
});

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度2-50个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (isRegister.value && value !== form.password) {
          callback(new Error('两次密码输入不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    if (isRegister.value) {
      await authStore.register({ username: form.username, password: form.password });
      ElMessage.success('注册成功');
    } else {
      await authStore.login({ username: form.username, password: form.password });
      ElMessage.success('登录成功');
    }
    const redirect = (route.query.redirect as string) || '/candidate';
    router.push(redirect);
  } catch (err: unknown) {
    const msg = (err instanceof Error) ? err.message : (isRegister.value ? '注册失败' : '用户名或密码错误');
    ElMessage.error(msg);
  } finally {
    loading.value = false;
  }
}

function toggleMode() {
  isRegister.value = !isRegister.value;
  form.username = '';
  form.password = '';
  form.confirmPassword = '';
  formRef.value?.resetFields();
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-card__header">
        <div class="login-card__logo">G</div>
        <h1 class="login-card__title">高考志愿通</h1>
        <p class="login-card__subtitle">{{ isRegister ? '注册新账号' : '智能志愿推荐系统' }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item v-if="isRegister" label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-card__btn"
            @click="handleSubmit"
          >
            {{ isRegister ? '注册' : '登录' }}
          </el-button>
        </el-form-item>

        <el-form-item>
          <el-button type="default" class="login-card__btn" @click="toggleMode">
            {{ isRegister ? '已有账号？返回登录' : '没有账号？立即注册' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}
.login-card__header {
  text-align: center;
  margin-bottom: 32px;
}
.login-card__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 14px;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 12px;
}
.login-card__title {
  margin: 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
}
.login-card__subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.login-card__btn {
  width: 100%;
}
</style>

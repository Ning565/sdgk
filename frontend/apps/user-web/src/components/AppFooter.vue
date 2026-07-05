<template>
  <div class="app-footer">
    <div class="app-footer__inner">
      <span>高考志愿通 &copy; {{ new Date().getFullYear() }}</span>
      <span class="app-footer__links">
        <a href="#" class="app-footer__link">关于我们</a>
        <a href="#" class="app-footer__link">帮助中心</a>
        <a href="#" class="app-footer__link">隐私政策</a>
      </span>
    </div>
  </div>
</template>

<style scoped>
.app-footer {
  background: #fff;
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 16px 24px;
}
.app-footer__inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.app-footer__links {
  display: flex;
  gap: 16px;
}
.app-footer__link {
  color: var(--el-text-color-secondary);
  text-decoration: none;
}
.app-footer__link:hover {
  color: var(--el-color-primary);
}
</style>

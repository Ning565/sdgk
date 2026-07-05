<script setup lang="ts">
import { ref } from 'vue';
import { PageHeader } from '@gaokao/shared-ui';
import { ElMessage, ElMessageBox } from 'element-plus';

const loading = ref(false);
const links = ref([
  { id: '1', name: '教育部阳光高考', url: 'https://gaokao.chsi.com.cn/', category: '官方平台', sortOrder: 1, isActive: true },
  { id: '2', name: '各省教育考试院', url: 'https://www.neea.edu.cn/', category: '官方平台', sortOrder: 2, isActive: true },
]);

const showDialog = ref(false);
const editingLink = ref<any>(null);
const linkForm = ref({
  name: '',
  url: '',
  category: '',
  sortOrder: 0,
});

function handleAdd() {
  editingLink.value = null;
  linkForm.value = { name: '', url: '', category: '', sortOrder: links.value.length + 1 };
  showDialog.value = true;
}

function handleEdit(link: any) {
  editingLink.value = link;
  linkForm.value = { ...link };
  showDialog.value = true;
}

function handleDelete(link: any) {
  ElMessageBox.confirm('确定要删除此外链吗？', '确认删除', { type: 'warning' })
    .then(() => {
      const idx = links.value.findIndex((l) => l.id === link.id);
      if (idx > -1) links.value.splice(idx, 1);
      ElMessage.success('删除成功');
    })
    .catch(() => {});
}

function handleSave() {
  if (editingLink.value) {
    Object.assign(editingLink.value, linkForm.value);
    ElMessage.success('修改成功');
  } else {
    links.value.push({
      id: String(Date.now()),
      ...linkForm.value,
      isActive: true,
    });
    ElMessage.success('添加成功');
  }
  showDialog.value = false;
}
</script>

<template>
  <div class="links-page">
    <PageHeader title="外链管理" description="管理推荐页面展示的外部链接">
      <template #extra>
        <el-button type="primary" @click="handleAdd">添加外链</el-button>
      </template>
    </PageHeader>

    <el-card>
      <el-table :data="links" border stripe>
        <el-table-column prop="sortOrder" label="排序" width="60" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="url" label="链接地址">
          <template #default="{ row }">
            <a :href="row.url" target="_blank">{{ row.url }}</a>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="isActive" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'">
              {{ row.isActive ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showDialog" :title="editingLink ? '编辑外链' : '添加外链'" width="500px">
      <el-form :model="linkForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="linkForm.name" />
        </el-form-item>
        <el-form-item label="链接地址">
          <el-input v-model="linkForm.url" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="linkForm.category" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="linkForm.sortOrder" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

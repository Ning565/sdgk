<script setup lang="ts">
import { ref } from 'vue';
import { PageHeader } from '@gaokao/shared-ui';
import { ElMessage } from 'element-plus';

const uploading = ref(false);
const fileList = ref<any[]>([]);

function handleUpload() {
  if (fileList.value.length === 0) {
    ElMessage.warning('请选择要上传的文件');
    return;
  }
  uploading.value = true;
  setTimeout(() => {
    uploading.value = false;
    fileList.value = [];
    ElMessage.success('上传成功，正在后台处理');
  }, 2000);
}
</script>

<template>
  <div class="import-page">
    <PageHeader
      title="招生计划导入"
      description="上传各院校招生计划数据，支持 Excel (.xlsx) 格式"
    />

    <el-card>
      <el-alert
        title="导入说明"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      >
        <p>请按照模板格式准备数据，包含以下列：院校代码、院校名称、专业代码、专业名称、招生人数、批次、科类、年份、省份</p>
      </el-alert>

      <el-upload
        v-model:file-list="fileList"
        drag
        :auto-upload="false"
        multiple
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            仅支持 .xlsx / .xls 格式文件，单文件不超过 10MB
          </div>
        </template>
      </el-upload>

      <div style="margin-top: 20px; text-align: right">
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          开始上传
        </el-button>
      </div>
    </el-card>
  </div>
</template>

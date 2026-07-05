<script setup lang="ts">
import { ref } from 'vue';
import { PageHeader } from '@gaokao/shared-ui';
import { ElMessage } from 'element-plus';
import { ImportType } from '@gaokao/shared-types';

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

function handleBeforeUpload(file: any) {
  const isExcel =
    file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
    file.type === 'application/vnd.ms-excel';
  if (!isExcel) {
    ElMessage.error('仅支持 Excel 文件');
    return false;
  }
  return true;
}
</script>

<template>
  <div class="import-page">
    <PageHeader
      title="一分一段导入"
      description="上传高考一分一段表数据，支持 Excel (.xlsx) 格式"
    />

    <el-card>
      <el-alert
        title="导入说明"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      >
        <p>请按照模板格式准备数据，包含以下列：分数、人数、累计人数、科类、年份、省份</p>
      </el-alert>

      <el-upload
        v-model:file-list="fileList"
        drag
        :auto-upload="false"
        :before-upload="handleBeforeUpload"
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

    <el-card style="margin-top: 20px">
      <template #header>
        <span>导入历史</span>
      </template>
      <el-table :data="[]" border stripe>
        <el-table-column prop="batchId" label="批次号" />
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="totalRows" label="总行数" />
        <el-table-column prop="successRows" label="成功" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="createdAt" label="上传时间" />
      </el-table>
      <el-empty description="暂无导入记录" />
    </el-card>
  </div>
</template>

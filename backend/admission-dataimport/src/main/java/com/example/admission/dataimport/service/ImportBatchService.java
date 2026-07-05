package com.example.admission.dataimport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.dataimport.entity.ImportBatch;
import com.example.admission.dataimport.entity.ImportFile;
import com.example.admission.dataimport.mapper.ImportBatchMapper;
import com.example.admission.dataimport.mapper.ImportFileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 导入批次管理服务.
 *
 * <p>负责导入批次的创建、状态管理、查询和取消，
 * 以及导入文件的上传存储（MVP阶段存本地目录）。</p>
 *
 * @author admission-system
 */
@Service
public class ImportBatchService {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchService.class);

    /** MVP阶段文件存储根目录 */
    private static final String IMPORT_ROOT_DIR = "data/import";

    private final ImportBatchMapper importBatchMapper;
    private final ImportFileMapper importFileMapper;

    public ImportBatchService(ImportBatchMapper importBatchMapper, ImportFileMapper importFileMapper) {
        this.importBatchMapper = importBatchMapper;
        this.importFileMapper = importFileMapper;
    }

    /**
     * 创建导入批次并保存上传文件.
     *
     * @param dataType  数据类型
     * @param year 年份      数据年份
     * @param fileName  原始文件名
     * @param fileSize  文件大小(字节)
     * @param fileBytes 文件内容
     * @param createdBy 创建人ID
     * @return 创建的批次实体
     */
    @Transactional
    public ImportBatch createBatch(String dataType, Integer year, String fileName,
                                    Long fileSize, byte[] fileBytes, Long createdBy) {
        // 创建批次记录
        ImportBatch batch = new ImportBatch();
        batch.setDataType(dataType);
        batch.setYear(year);
        batch.setFileName(fileName);
        batch.setFileSize(fileSize);
        batch.setStatus(ImportBatch.STATUS_UPLOADING);
        batch.setTotalRows(0);
        batch.setValidRows(0);
        batch.setErrorRows(0);
        batch.setCreatedBy(createdBy);
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());

        importBatchMapper.insert(batch);
        log.info("Created import batch: id={}, dataType={}, year={}, fileName={}",
                batch.getId(), dataType, year, fileName);

        // 保存文件到本地目录
        String batchDir = IMPORT_ROOT_DIR + "/" + batch.getId();
        Path dirPath = Path.of(batchDir);
        try {
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(fileName);
            Files.write(filePath, fileBytes);

            String fileUrl = filePath.toAbsolutePath().toString();
            batch.setFileUrl(fileUrl);
            importBatchMapper.updateById(batch);

            // 记录导入文件
            ImportFile importFile = new ImportFile();
            importFile.setBatchId(batch.getId());
            importFile.setFileType(ImportFile.FILE_TYPE_ORIGINAL);
            importFile.setFileUrl(fileUrl);
            importFile.setCreatedAt(LocalDateTime.now());
            importFileMapper.insert(importFile);

            log.info("Saved import file: batchId={}, path={}", batch.getId(), fileUrl);
        } catch (IOException e) {
            log.error("Failed to save upload file for batch: {}", batch.getId(), e);
            throw new BusinessException(ErrorCode.IMPORT_PARSE_ERROR, "文件保存失败: " + e.getMessage(), e);
        }

        return batch;
    }

    /**
     * 更新批次状态.
     *
     * @param batchId 批次ID
     * @param status  新状态
     */
    @Transactional
    public void updateStatus(Long batchId, String status) {
        LambdaUpdateWrapper<ImportBatch> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImportBatch::getId, batchId)
                .set(ImportBatch::getStatus, status)
                .set(ImportBatch::getUpdatedAt, LocalDateTime.now());
        importBatchMapper.update(null, wrapper);
        log.info("Updated batch status: batchId={}, status={}", batchId, status);
    }

    /**
     * 更新批次的行数统计.
     *
     * @param batchId   批次ID
     * @param totalRows 总行数
     * @param validRows 有效行数
     * @param errorRows 错误行数
     */
    @Transactional
    public void updateRowStats(Long batchId, int totalRows, int validRows, int errorRows) {
        LambdaUpdateWrapper<ImportBatch> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImportBatch::getId, batchId)
                .set(ImportBatch::getTotalRows, totalRows)
                .set(ImportBatch::getValidRows, validRows)
                .set(ImportBatch::getErrorRows, errorRows)
                .set(ImportBatch::getUpdatedAt, LocalDateTime.now());
        importBatchMapper.update(null, wrapper);
    }

    /**
     * 根据ID查询批次.
     *
     * @param batchId 批次ID
     * @return 批次实体，不存在时抛出异常
     */
    public ImportBatch getBatch(Long batchId) {
        return Optional.ofNullable(importBatchMapper.selectById(batchId))
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "导入批次不存在: " + batchId));
    }

    /**
     * 分页查询批次列表.
     *
     * @param page     分页参数
     * @param dataType 数据类型（可选过滤）
     * @param year 年份     年份（可选过滤）
     * @return 分页结果
     */
    public Page<ImportBatch> listBatches(com.example.admission.common.PageRequest page,
                                          String dataType, Integer year) {
        Page<ImportBatch> mpPage = new Page<>(page.getPage(), page.getSize());
        LambdaQueryWrapper<ImportBatch> wrapper = new LambdaQueryWrapper<>();
        if (dataType != null && !dataType.isBlank()) {
            wrapper.eq(ImportBatch::getDataType, dataType);
        }
        if (year != null) {
            wrapper.eq(ImportBatch::getYear, year);
        }
        wrapper.orderByDesc(ImportBatch::getCreatedAt);
        return importBatchMapper.selectPage(mpPage, wrapper);
    }

    /**
     * 取消批次.
     *
     * <p>仅允许取消状态为 READY 或 VALIDATION_FAILED 的批次。</p>
     *
     * @param batchId 批次ID
     */
    @Transactional
    public void cancel(Long batchId) {
        ImportBatch batch = getBatch(batchId);
        String currentStatus = batch.getStatus();
        if (!ImportBatch.STATUS_READY.equals(currentStatus)
                && !ImportBatch.STATUS_VALIDATION_FAILED.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED,
                    "当前状态不允许取消: " + currentStatus);
        }
        updateStatus(batchId, ImportBatch.STATUS_CANCELLED);
        log.info("Cancelled import batch: batchId={}", batchId);
    }

    /**
     * 获取批次的文件存储目录路径.
     *
     * @param batchId 批次ID
     * @return 目录绝对路径
     */
    public String getBatchDir(Long batchId) {
        return Path.of(IMPORT_ROOT_DIR, String.valueOf(batchId)).toAbsolutePath().toString();
    }

    /**
     * 生成唯一的导入文件存储名称.
     *
     * @param originalName 原始文件名
     * @return 带UUID前缀的唯一文件名
     */
    public static String generateUniqueFileName(String originalName) {
        String ext = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = originalName.substring(dotIndex);
        }
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }
}

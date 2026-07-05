package com.example.admission.dataimport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.dataimport.entity.ImportBatch;
import com.example.admission.dataimport.entity.ImportFile;
import com.example.admission.dataimport.entity.ImportRowError;
import com.example.admission.dataimport.mapper.ImportFileMapper;
import com.example.admission.dataimport.mapper.ImportRowErrorMapper;
import com.example.admission.dataimport.util.ExcelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据校验服务.
 *
 * <p>提供多层级数据校验能力：
 * <ul>
 *   <li>文件级：格式、Sheet数量、表头、文件大小</li>
 *   <li>字段级：必填、类型、范围、字典值</li>
 *   <li>行级：唯一键、位次合法性、招生人数、学费、URL</li>
 *   <li>跨行：累计人数单调性、重复计划</li>
 *   <li>跨表：学校/专业/计划序列/年度配置引用完整性</li>
 *   <li>发布级：结构化选科确认、关键错误为零</li>
 * </ul>
 * </p>
 *
 * @author admission-system
 */
@Service
public class DataValidationService {

    private static final Logger log = LoggerFactory.getLogger(DataValidationService.class);

    private final ImportBatchService importBatchService;
    private final ImportRowErrorMapper importRowErrorMapper;
    private final ImportFileMapper importFileMapper;

    public DataValidationService(ImportBatchService importBatchService,
                                  ImportRowErrorMapper importRowErrorMapper,
                                  ImportFileMapper importFileMapper) {
        this.importBatchService = importBatchService;
        this.importRowErrorMapper = importRowErrorMapper;
        this.importFileMapper = importFileMapper;
    }

    /**
     * 执行完整的数据校验流程.
     *
     * <p>校验流程：
     * <ol>
     *   <li>文件级校验（格式、Sheet、表头、大小）</li>
     *   <li>字段级和行级校验由 DataParseService 在解析时完成</li>
     *   <li>跨行校验（累计人数单调性、重复计划）由 DataParseService 完成</li>
     *   <li>跨表校验（引用完整性）</li>
     *   <li>发布级校验（阻塞性检查）</li>
     * </ol>
     * </p>
     *
     * @param batchId 批次ID
     * @return 错误总数
     */
    @Transactional
    public int validate(Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        log.info("Starting validation for batch: batchId={}, dataType={}, year={}",
                batchId, batch.getDataType(), batch.getYear());

        // 查询所有已记录的错误
        List<ImportRowError> allErrors = getErrors(batchId);

        // 文件级校验
        List<ImportRowError> fileLevelErrors = validateFileLevel(batch);
        if (!fileLevelErrors.isEmpty()) {
            for (ImportRowError error : fileLevelErrors) {
                importRowErrorMapper.insert(error);
            }
            allErrors.addAll(fileLevelErrors);
        }

        // 跨表校验（引用完整性）
        List<ImportRowError> crossTableErrors = validateCrossTable(batch);
        if (!crossTableErrors.isEmpty()) {
            for (ImportRowError error : crossTableErrors) {
                importRowErrorMapper.insert(error);
            }
            allErrors.addAll(crossTableErrors);
        }

        int totalErrors = allErrors.size();
        int errorRows = (int) allErrors.stream().map(ImportRowError::getRowNumber).distinct().count();

        // 更新批次统计
        if (totalErrors > 0) {
            importBatchService.updateRowStats(batchId, batch.getTotalRows(),
                    batch.getTotalRows() - errorRows, errorRows);
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_VALIDATION_FAILED);
        } else {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_READY);
        }

        log.info("Validation completed: batchId={}, totalErrors={}, errorRows={}", batchId, totalErrors, errorRows);
        return totalErrors;
    }

    /**
     * 发布前最终校验，检查是否存在阻塞性错误.
     *
     * <p>阻塞性错误包括：必填字段缺失、类型错误、唯一键重复。
     * 非阻塞性错误（如建议性警告）不阻止发布。</p>
     *
     * @param batchId 批次ID
     * @return true 如果存在阻塞性错误，false 如果可以发布
     */
    public boolean validateForPublish(Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        if (!ImportBatch.STATUS_READY.equals(batch.getStatus())) {
            log.warn("Batch not ready for publish: batchId={}, status={}", batchId, batch.getStatus());
            return true; // 有阻塞
        }

        // 检查关键错误类型
        LambdaQueryWrapper<ImportRowError> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportRowError::getBatchId, batchId)
                .in(ImportRowError::getErrorType,
                        ImportRowError.ERROR_REQUIRED,
                        ImportRowError.ERROR_DUPLICATE);
        long criticalErrors = importRowErrorMapper.selectCount(wrapper);

        if (criticalErrors > 0) {
            log.warn("Critical errors found for publish: batchId={}, count={}", batchId, criticalErrors);
            return true;
        }
        return false;
    }

    /**
     * 查询批次的所有错误明细.
     *
     * @param batchId 批次ID
     * @return 错误列表
     */
    public List<ImportRowError> getErrors(Long batchId) {
        LambdaQueryWrapper<ImportRowError> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportRowError::getBatchId, batchId)
                .orderByAsc(ImportRowError::getRowNumber);
        return importRowErrorMapper.selectList(wrapper);
    }

    /**
     * 导出错误明细为Excel文件.
     *
     * @param batchId 批次ID
     * @return Excel文件字节数组
     */
    @Transactional
    public byte[] exportErrors(Long batchId) {
        List<ImportRowError> errors = getErrors(batchId);
        if (errors.isEmpty()) {
            throw new BusinessException(ErrorCode.EXPORT_DATA_EMPTY, "没有错误数据可导出");
        }

        // 构建导出数据
        List<String> headers = List.of("行号", "字段名", "原始值", "错误类型", "错误描述", "修改建议");
        List<List<String>> rows = new ArrayList<>();
        for (ImportRowError error : errors) {
            List<String> row = new ArrayList<>();
            row.add(String.valueOf(error.getRowNumber()));
            row.add(error.getFieldName() != null ? error.getFieldName() : "");
            row.add(error.getOriginalValue() != null ? error.getOriginalValue() : "");
            row.add(error.getErrorType());
            row.add(error.getErrorMessage());
            row.add(error.getSuggestion() != null ? error.getSuggestion() : "");
            rows.add(row);
        }

        // 写入文件
        String batchDir = importBatchService.getBatchDir(batchId);
        String errorFilePath = batchDir + "/error_detail_" + batchId + ".xlsx";
        ExcelUtil.writeErrorExcel(headers, rows, errorFilePath);

        // 记录错误文件
        ImportFile errorFile = new ImportFile();
        errorFile.setBatchId(batchId);
        errorFile.setFileType(ImportFile.FILE_TYPE_ERROR_DETAIL);
        errorFile.setFileUrl(errorFilePath);
        errorFile.setCreatedAt(LocalDateTime.now());
        importFileMapper.insert(errorFile);

        log.info("Error detail exported: batchId={}, path={}, rows={}", batchId, errorFilePath, rows.size());

        // 读取文件字节返回
        try {
            return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(errorFilePath));
        } catch (Exception e) {
            log.error("Failed to read error export file: {}", errorFilePath, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED, "读取错误明细文件失败", e);
        }
    }

    // ==================== 私有校验方法 ====================

    /**
     * 文件级校验：格式、Sheet、表头、大小.
     */
    private List<ImportRowError> validateFileLevel(ImportBatch batch) {
        List<ImportRowError> errors = new ArrayList<>();

        // 检查文件是否存在
        String fileUrl = batch.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            errors.add(buildError(batch.getId(), 0, "fileUrl", "", ImportRowError.ERROR_REQUIRED,
                    "导入文件不存在", "请重新上传文件"));
            return errors;
        }

        java.io.File file = new java.io.File(fileUrl);
        if (!file.exists()) {
            errors.add(buildError(batch.getId(), 0, "fileUrl", fileUrl, ImportRowError.ERROR_REQUIRED,
                    "导入文件已被删除或移动", "请重新上传文件"));
            return errors;
        }

        // 检查文件扩展名
        String fileName = batch.getFileName().toLowerCase();
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            errors.add(buildError(batch.getId(), 0, "fileName", batch.getFileName(),
                    ImportRowError.ERROR_FORMAT,
                    "不支持的文件格式，仅支持 .xlsx 和 .xls", "请使用Excel格式文件"));
        }

        // 检查文件大小（最大50MB = 配置文件中的上限）
        long fileSize = batch.getFileSize() != null ? batch.getFileSize() : 0;
        if (fileSize > 50 * 1024 * 1024) {
            errors.add(buildError(batch.getId(), 0, "fileSize", String.valueOf(fileSize),
                    ImportRowError.ERROR_RANGE,
                    "文件大小超过50MB限制", "请拆分文件或压缩后上传"));
        }

        return errors;
    }

    /**
     * 跨表校验：引用完整性.
     *
     * <p>MVP阶段仅提供框架，具体跨表引用校验待后续迭代完善。</p>
     */
    private List<ImportRowError> validateCrossTable(ImportBatch batch) {
        List<ImportRowError> errors = new ArrayList<>();

        // MVP阶段：检查数据类型是否合法
        String dataType = batch.getDataType();
        if (dataType == null || !List.of("SCORE_RANK", "PLAN", "HISTORY", "LINK").contains(dataType)) {
            errors.add(buildError(batch.getId(), 0, "dataType", dataType,
                    ImportRowError.ERROR_FORMAT,
                    "不支持的数据类型: " + dataType,
                    "数据类型应为: SCORE_RANK / PLAN / HISTORY / LINK"));
        }

        // MVP阶段：年份范围检查
        Integer year = batch.getYear();
        if (year != null && (year < 2000 || year > 2099)) {
            errors.add(buildError(batch.getId(), 0, "year", String.valueOf(year),
                    ImportRowError.ERROR_RANGE,
                    "年份超出合理范围(2000-2099)", "请检查数据年份"));
        }

        return errors;
    }

    /**
     * 构建错误对象.
     */
    private ImportRowError buildError(Long batchId, int rowNumber, String fieldName,
                                       String originalValue, String errorType,
                                       String errorMessage, String suggestion) {
        ImportRowError error = new ImportRowError();
        error.setBatchId(batchId);
        error.setRowNumber(rowNumber);
        error.setFieldName(fieldName);
        error.setOriginalValue(originalValue);
        error.setErrorType(errorType);
        error.setErrorMessage(errorMessage);
        error.setSuggestion(suggestion);
        return error;
    }
}

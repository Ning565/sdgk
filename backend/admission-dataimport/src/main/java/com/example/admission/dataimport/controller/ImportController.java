package com.example.admission.dataimport.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.common.PageResponse;
import com.example.admission.dataimport.dto.*;
import com.example.admission.dataimport.entity.DataVersion;
import com.example.admission.dataimport.entity.ImportBatch;
import com.example.admission.dataimport.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 数据导入管理Controller.
 *
 * <p>路径前缀: /api/admin/v1。
 * 提供导入批次管理、数据解析、校验、发布和版本回滚的REST API。</p>
 *
 * @author admission-system
 */
@RestController
@RequestMapping("/api/admin/v1")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ImportBatchService importBatchService;
    private final DataParseService dataParseService;
    private final DataValidationService dataValidationService;
    private final DataPublishService dataPublishService;

    public ImportController(ImportBatchService importBatchService,
                             DataParseService dataParseService,
                             DataValidationService dataValidationService,
                             DataPublishService dataPublishService) {
        this.importBatchService = importBatchService;
        this.dataParseService = dataParseService;
        this.dataValidationService = dataValidationService;
        this.dataPublishService = dataPublishService;
    }

    // ==================== 批次管理 ====================

    /**
     * 创建导入批次并上传文件.
     *
     * <p>接收文件后立即保存到本地 data/import/{batchId}/ 目录，
     * 同时创建批次记录，初始状态为 UPLOADING。</p>
     *
     * @param file     上传的Excel文件
     * @param dataType 数据类型
     * @param year 年份     数据年份
     * @param createdBy 创建人ID（MVP阶段从请求参数传入）
     * @return 批次响应
     */
    @PostMapping("/import-batches")
    public ApiResponse<ImportBatchResponse> createBatch(
            @RequestParam("file") MultipartFile file,
            @RequestParam("dataType") String dataType,
            @RequestParam("year") Integer year,
            @RequestParam(value = "createdBy", required = false) Long createdBy) {

        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_EMPTY, "上传文件为空");
        }

        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            throw new BusinessException(ErrorCode.IMPORT_PARSE_ERROR, "读取上传文件失败", e);
        }

        ImportBatch batch = importBatchService.createBatch(dataType, year, fileName, fileSize, fileBytes, createdBy);
        log.info("Batch created: batchId={}, dataType={}, year={}", batch.getId(), dataType, year);

        return ApiResponse.success(ImportBatchResponse.from(batch));
    }

    /**
     * 分页查询批次列表.
     *
     * @param page     页码（默认1）
     * @param size     每页数量（默认20）
     * @param dataType 数据类型过滤（可选）
     * @param year 年份     年份过滤（可选）
     * @return 分页批次列表
     */
    @GetMapping("/import-batches")
    public ApiResponse<PageResponse<ImportBatchResponse>> listBatches(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "dataType", required = false) String dataType,
            @RequestParam(value = "year", required = false) Integer year) {

        com.example.admission.common.PageRequest pageRequest =
                new com.example.admission.common.PageRequest(page, size);
        var mpPage = importBatchService.listBatches(pageRequest, dataType, year);

        List<ImportBatchResponse> records = mpPage.getRecords().stream()
                .map(ImportBatchResponse::from)
                .collect(Collectors.toList());

        PageResponse<ImportBatchResponse> response =
                PageResponse.of(records, mpPage.getTotal(), page, size);
        return ApiResponse.success(response);
    }

    /**
     * 查询批次详情（含错误统计）.
     *
     * @param batchId 批次ID
     * @return 批次详情
     */
    @GetMapping("/import-batches/{batchId}")
    public ApiResponse<ImportBatchResponse> getBatch(@PathVariable("batchId") Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        return ApiResponse.success(ImportBatchResponse.from(batch));
    }

    /**
     * 触发数据解析.
     *
     * <p>根据批次的数据类型自动选择对应的解析器：
     * SCORE_RANK -> parseScoreRank, PLAN -> parseEnrollmentPlan,
     * HISTORY -> parseHistory, LINK -> parseLinks。</p>
     *
     * @param batchId 批次ID
     * @return 解析结果（错误数）
     */
    @PostMapping("/import-batches/{batchId}/parse")
    public ApiResponse<String> parse(@PathVariable("batchId") Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        int errorRows;

        switch (batch.getDataType()) {
            case "SCORE_RANK":
                errorRows = dataParseService.parseScoreRank(batchId);
                break;
            case "PLAN":
                errorRows = dataParseService.parseEnrollmentPlan(batchId);
                break;
            case "HISTORY":
                errorRows = dataParseService.parseHistory(batchId);
                break;
            case "LINK":
                errorRows = dataParseService.parseLinks(batchId);
                break;
            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "不支持的数据类型: " + batch.getDataType());
        }

        if (errorRows > 0) {
            return ApiResponse.success("解析完成，发现 " + errorRows + " 行存在错误，请查看错误明细");
        }
        return ApiResponse.success("解析完成，所有数据通过基础校验");
    }

    /**
     * 触发数据校验.
     *
     * <p>执行完整的校验流程（文件级、跨表、引用完整性），
     * 校验结果影响批次状态。</p>
     *
     * @param batchId 批次ID
     * @return 校验结果
     */
    @PostMapping("/import-batches/{batchId}/validate")
    public ApiResponse<String> validate(@PathVariable("batchId") Long batchId) {
        int totalErrors = dataValidationService.validate(batchId);
        if (totalErrors > 0) {
            return ApiResponse.success("校验完成，发现 " + totalErrors + " 个错误");
        }
        return ApiResponse.success("校验通过，数据可以发布");
    }

    /**
     * 发布数据版本.
     *
     * <p>要求批次状态为 READY 且确认发布。发布成功后，
     * 对应数据类型和年份的数据将对外生效。</p>
     *
     * @param batchId  批次ID
     * @param req 请求对象uest HTTP 请求  发布请求（含确认标志）
     * @param adminUserId 操作人ID（MVP阶段从头信息或参数获取）
     * @return 发布后的数据版本
     */
    @PostMapping("/import-batches/{batchId}/publish")
    public ApiResponse<DataVersionResponse> publish(
            @PathVariable("batchId") Long batchId,
            @Valid @RequestBody PublishRequest request,
            @RequestParam(value = "adminUserId", required = false) Long adminUserId) {

        if (!request.isConfirmed()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请确认发布操作");
        }

        // 发布前校验
        boolean hasBlockingErrors = dataValidationService.validateForPublish(batchId);
        if (hasBlockingErrors) {
            throw new BusinessException(ErrorCode.IMPORT_PUBLISH_FAILED,
                    "数据存在阻塞性错误，无法发布。请先处理错误后重试");
        }

        Long publisherId = Optional.ofNullable(adminUserId).orElse(0L);
        DataVersion version = dataPublishService.publish(batchId, publisherId);

        log.info("Data published: batchId={}, versionId={}, publishedBy={}",
                batchId, version.getId(), publisherId);

        return ApiResponse.success(DataVersionResponse.from(version));
    }

    /**
     * 取消导入批次.
     *
     * <p>仅 READY 或 VALIDATION_FAILED 状态的批次可以取消。
     * 取消后的批次不可恢复。</p>
     *
     * @param batchId 批次ID
     * @return 操作结果
     */
    @PostMapping("/import-batches/{batchId}/cancel")
    public ApiResponse<String> cancel(@PathVariable("batchId") Long batchId) {
        importBatchService.cancel(batchId);
        return ApiResponse.success("批次已取消");
    }

    /**
     * 导出错误明细为Excel文件.
     *
     * @param batchId 批次ID
     * @return Excel文件下载
     */
    @GetMapping("/import-batches/{batchId}/errors/export")
    public ResponseEntity<byte[]> exportErrors(@PathVariable("batchId") Long batchId) {
        byte[] excelBytes = dataValidationService.exportErrors(batchId);

        ImportBatch batch = importBatchService.getBatch(batchId);
        String downloadFileName = "error_detail_" + batch.getDataType() + "_" + batch.getYear() + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", downloadFileName);
        headers.setContentLength(excelBytes.length);

        return ResponseEntity.ok().headers(headers).body(excelBytes);
    }

    /**
     * 查询数据版本列表.
     *
     * @param dataType 数据类型过滤（可选）
     * @param year 年份     年份过滤（可选）
     * @return 版本列表
     */
    @GetMapping("/data-versions")
    public ApiResponse<List<DataVersionResponse>> listVersions(
            @RequestParam(value = "dataType", required = false) String dataType,
            @RequestParam(value = "year", required = false) Integer year) {

        List<DataVersion> versions = dataPublishService.listVersions(dataType, year);
        List<DataVersionResponse> response = versions.stream()
                .map(DataVersionResponse::from)
                .collect(Collectors.toList());

        return ApiResponse.success(response);
    }

    /**
     * 回滚到指定版本.
     *
     * <p>回滚会创建一个新版本，数据复制自目标旧版本。
     * 新版本自动成为当前生效版本。</p>
     *
     * @param versionId   目标回滚版本ID
     * @param adminUserId 操作人ID
     * @return 新创建的回滚版本
     */
    @PostMapping("/data-versions/{versionId}/rollback")
    public ApiResponse<DataVersionResponse> rollback(
            @PathVariable("versionId") Long versionId,
            @RequestParam(value = "adminUserId", required = false) Long adminUserId) {

        Long operatorId = Optional.ofNullable(adminUserId).orElse(0L);
        DataVersion rollbackVersion = dataPublishService.rollback(versionId, operatorId);

        log.info("Version rolled back: targetVersionId={}, newVersionId={}, operator={}",
                versionId, rollbackVersion.getId(), operatorId);

        return ApiResponse.success(DataVersionResponse.from(rollbackVersion));
    }

}

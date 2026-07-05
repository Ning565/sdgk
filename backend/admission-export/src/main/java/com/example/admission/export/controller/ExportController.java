package com.example.admission.export.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.export.dto.ExportRequest;
import com.example.admission.export.dto.ExportResponse;
import com.example.admission.export.entity.ExportRecord;
import com.example.admission.export.service.ExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 导出接口.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /** 导出志愿表 */
    @PostMapping("/volunteer-forms/{formId}/export")
    public ApiResponse<ExportResponse> export(@PathVariable("formId") Long formId,
                                               @Valid @RequestBody ExportRequest request) {
        return ApiResponse.success(exportService.export(formId, request));
    }

    /** 下载导出文件 */
    @GetMapping("/exports/{exportRecordId}")
    public ResponseEntity<Resource> download(@PathVariable("exportRecordId") Long exportRecordId) {
        ExportRecord record = exportService.getExportRecord(exportRecordId);
        File file = new File(record.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String encodedFileName = URLEncoder.encode(record.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

    /** 导出记录列表 */
    @GetMapping("/volunteer-forms/{formId}/exports")
    public ApiResponse<List<ExportResponse>> listExports(@PathVariable("formId") Long formId) {
        return ApiResponse.success(exportService.listExports(formId));
    }
}

package com.example.admission.volunteer.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.volunteer.dto.*;
import com.example.admission.volunteer.service.VolunteerFormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 志愿表管理接口.
 */
@RestController
@RequestMapping("/api/v1/volunteer-forms")
@RequiredArgsConstructor
public class VolunteerController {

    private final VolunteerFormService volunteerFormService;

    /** 志愿表列表 */
    @GetMapping
    public ApiResponse<List<VolunteerFormResponse>> listForms(@RequestParam(value = "year", required = false) Integer year) {
        return ApiResponse.success(volunteerFormService.listForms(year));
    }

    /** 创建志愿表 */
    @PostMapping
    public ApiResponse<VolunteerFormResponse> createForm(@RequestBody Map<String, Object> body) {
        Integer year = (Integer) body.get("year");
        String name = (String) body.get("name");
        return ApiResponse.success(volunteerFormService.createForm(year, name));
    }

    /** 志愿表详情 */
    @GetMapping("/{formId}")
    public ApiResponse<VolunteerFormDetailResponse> getFormDetail(@PathVariable("formId") Long formId) {
        return ApiResponse.success(volunteerFormService.getFormDetail(formId));
    }

    /** 改名 */
    @PatchMapping("/{formId}/name")
    public ApiResponse<Void> renameForm(@PathVariable("formId") Long formId, @RequestBody Map<String, String> body) {
        volunteerFormService.renameForm(formId, body.get("name"));
        return ApiResponse.success();
    }

    /** 复制 */
    @PostMapping("/{formId}/copy")
    public ApiResponse<VolunteerFormResponse> copyForm(@PathVariable("formId") Long formId,
                                                        @Valid @RequestBody CopyFormRequest request) {
        return ApiResponse.success(volunteerFormService.copyForm(formId, request));
    }

    /** 删除 */
    @DeleteMapping("/{formId}")
    public ApiResponse<Void> deleteForm(@PathVariable("formId") Long formId) {
        volunteerFormService.deleteForm(formId);
        return ApiResponse.success();
    }

    /** 添加志愿 */
    @PostMapping("/{formId}/items")
    public ApiResponse<VolunteerFormDetailResponse> addItem(@PathVariable("formId") Long formId,
                                                             @Valid @RequestBody AddVolunteerItemRequest request) {
        return ApiResponse.success(volunteerFormService.addItem(formId, request));
    }

    /** 删除志愿 */
    @DeleteMapping("/{formId}/items/{itemId}")
    public ApiResponse<Void> removeItem(@PathVariable("formId") Long formId,
                                         @PathVariable("itemId") Long itemId,
                                         @RequestParam("expectedVersion") Integer expectedVersion,
                                         @RequestParam(value = "clientOperationId", required = false) String clientOperationId) {
        volunteerFormService.removeItem(formId, itemId, expectedVersion, clientOperationId);
        return ApiResponse.success();
    }

    /** 批量删除 */
    @PostMapping("/{formId}/items/batch-delete")
    public ApiResponse<Void> batchRemoveItems(@PathVariable("formId") Long formId,
                                               @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawItemIds = (List<Object>) body.get("itemIds");
        List<Long> itemIds = rawItemIds.stream()
                .map(id -> id instanceof Number ? ((Number) id).longValue() : Long.valueOf(String.valueOf(id)))
                .toList();
        Integer expectedVersion = (Integer) body.get("expectedVersion");
        String clientOperationId = (String) body.get("clientOperationId");
        volunteerFormService.batchRemoveItems(formId, itemIds, expectedVersion, clientOperationId);
        return ApiResponse.success();
    }

    /** 移动排序 */
    @PostMapping("/{formId}/items/{itemId}/move")
    public ApiResponse<Void> moveItem(@PathVariable("formId") Long formId,
                                       @PathVariable("itemId") Long itemId,
                                       @Valid @RequestBody MoveItemRequest request) {
        volunteerFormService.moveItem(formId, itemId,
                request.getTargetPosition(), request.getExpectedVersion(), request.getClientOperationId());
        return ApiResponse.success();
    }

    /** 更新备注 */
    @PatchMapping("/{formId}/items/{itemId}/note")
    public ApiResponse<Void> updateNote(@PathVariable("formId") Long formId,
                                         @PathVariable("itemId") Long itemId,
                                         @Valid @RequestBody UpdateNoteRequest request) {
        volunteerFormService.updateNote(formId, itemId,
                request.getNote(), request.getExpectedVersion(), request.getClientOperationId());
        return ApiResponse.success();
    }
}

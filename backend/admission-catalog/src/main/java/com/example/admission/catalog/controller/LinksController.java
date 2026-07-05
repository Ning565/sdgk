package com.example.admission.catalog.controller;

import com.example.admission.catalog.entity.MajorLink;
import com.example.admission.catalog.entity.SchoolLink;
import com.example.admission.catalog.mapper.MajorLinkMapper;
import com.example.admission.catalog.mapper.SchoolLinkMapper;
import com.example.admission.common.ApiResponse;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 外链管理 REST 控制器。基础路径：{@code /api/admin/v1}
 *
 * <p>统一管理 {@code school_link} 和 {@code major_link} 两张表的外链数据。
 * 返回结果通过 {@code type} 字段（"school" / "major"）区分来源。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/v1")
@RequiredArgsConstructor
public class LinksController {

    private final SchoolLinkMapper schoolLinkMapper;
    private final MajorLinkMapper majorLinkMapper;

    private static final String TYPE_SCHOOL = "school";
    private static final String TYPE_MAJOR = "major";

    /**
     * 查询全部外链，合并学校外链和专业外链为统一列表。
     *
     * @return 统一外链列表，包含 type 字段
     */
    @GetMapping("/links")
    public ApiResponse<List<AdminLinkResponse>> listAllLinks() {
        List<AdminLinkResponse> result = new ArrayList<>();

        List<SchoolLink> schoolLinks = schoolLinkMapper.selectList(null);
        for (SchoolLink sl : schoolLinks) {
            result.add(AdminLinkResponse.builder()
                    .id(sl.getId())
                    .type(TYPE_SCHOOL)
                    .targetId(sl.getSchoolId())
                    .linkType(sl.getLinkType())
                    .title(sl.getTitle())
                    .url(sl.getUrl())
                    .sortOrder(sl.getSortOrder())
                    .createdAt(sl.getCreatedAt())
                    .updatedAt(sl.getUpdatedAt())
                    .build());
        }

        List<MajorLink> majorLinks = majorLinkMapper.selectList(null);
        for (MajorLink ml : majorLinks) {
            result.add(AdminLinkResponse.builder()
                    .id(ml.getId())
                    .type(TYPE_MAJOR)
                    .targetId(ml.getMajorId())
                    .linkType(ml.getLinkType())
                    .title(ml.getTitle())
                    .url(ml.getUrl())
                    .sortOrder(ml.getSortOrder())
                    .createdAt(ml.getCreatedAt())
                    .updatedAt(ml.getUpdatedAt())
                    .build());
        }

        log.debug("Listed all links: school={}, major={}, total={}",
                schoolLinks.size(), majorLinks.size(), result.size());
        return ApiResponse.success(result);
    }

    /**
     * 创建外链。
     *
     * @param req 创建请求，包含 type 字段指定 school 或 major
     * @return 创建的外链
     */
    @PostMapping("/links")
    public ApiResponse<AdminLinkResponse> createLink(@Valid @RequestBody AdminLinkRequest req) {
        log.info("Creating link: type={}, targetId={}, linkType={}", req.getType(), req.getTargetId(), req.getLinkType());

        if (TYPE_MAJOR.equals(req.getType())) {
            MajorLink entity = new MajorLink();
            entity.setMajorId(req.getTargetId());
            entity.setLinkType(req.getLinkType());
            entity.setTitle(req.getTitle());
            entity.setUrl(req.getUrl());
            entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            majorLinkMapper.insert(entity);
            return ApiResponse.success(toResponse(TYPE_MAJOR, entity.getId(), entity, req.getTargetId()));
        }

        // 默认创建学校外链
        if (!TYPE_SCHOOL.equals(req.getType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "type 必须为 school 或 major");
        }

        SchoolLink entity = new SchoolLink();
        entity.setSchoolId(req.getTargetId());
        entity.setLinkType(req.getLinkType());
        entity.setTitle(req.getTitle());
        entity.setUrl(req.getUrl());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        schoolLinkMapper.insert(entity);
        return ApiResponse.success(toResponse(TYPE_SCHOOL, entity.getId(), entity, req.getTargetId()));
    }

    /**
     * 更新外链。
     *
     * @param id   外链 ID
     * @param type 外链类型（school 或 major）
     * @param req  更新请求
     * @return 更新后的外链
     */
    @PutMapping("/links/{id}")
    public ApiResponse<AdminLinkResponse> updateLink(@PathVariable("id") Long id,
                                                      @RequestParam("type") String type,
                                                      @Valid @RequestBody AdminLinkRequest req) {
        log.info("Updating link: id={}, type={}", id, type);

        if (TYPE_MAJOR.equals(type)) {
            MajorLink entity = majorLinkMapper.selectById(id);
            if (entity == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "外链不存在");
            }
            if (req.getTargetId() != null) entity.setMajorId(req.getTargetId());
            if (req.getLinkType() != null) entity.setLinkType(req.getLinkType());
            if (req.getTitle() != null) entity.setTitle(req.getTitle());
            if (req.getUrl() != null) entity.setUrl(req.getUrl());
            if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
            entity.setUpdatedAt(LocalDateTime.now());
            majorLinkMapper.updateById(entity);
            return ApiResponse.success(toResponse(TYPE_MAJOR, entity.getId(), entity, entity.getMajorId()));
        }

        if (TYPE_SCHOOL.equals(type)) {
            SchoolLink entity = schoolLinkMapper.selectById(id);
            if (entity == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "外链不存在");
            }
            if (req.getTargetId() != null) entity.setSchoolId(req.getTargetId());
            if (req.getLinkType() != null) entity.setLinkType(req.getLinkType());
            if (req.getTitle() != null) entity.setTitle(req.getTitle());
            if (req.getUrl() != null) entity.setUrl(req.getUrl());
            if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
            entity.setUpdatedAt(LocalDateTime.now());
            schoolLinkMapper.updateById(entity);
            return ApiResponse.success(toResponse(TYPE_SCHOOL, entity.getId(), entity, entity.getSchoolId()));
        }

        throw new BusinessException(ErrorCode.PARAM_ERROR, "type 必须为 school 或 major");
    }

    /**
     * 删除外链。
     *
     * @param id   外链 ID
     * @param type 外链类型（school 或 major）
     * @return 空响应
     */
    @DeleteMapping("/links/{id}")
    public ApiResponse<Void> deleteLink(@PathVariable("id") Long id,
                                         @RequestParam("type") String type) {
        log.info("Deleting link: id={}, type={}", id, type);

        if (TYPE_MAJOR.equals(type)) {
            MajorLink entity = majorLinkMapper.selectById(id);
            if (entity == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "外链不存在");
            }
            majorLinkMapper.deleteById(id);
            return ApiResponse.success();
        }

        if (TYPE_SCHOOL.equals(type)) {
            SchoolLink entity = schoolLinkMapper.selectById(id);
            if (entity == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "外链不存在");
            }
            schoolLinkMapper.deleteById(id);
            return ApiResponse.success();
        }

        throw new BusinessException(ErrorCode.PARAM_ERROR, "type 必须为 school 或 major");
    }

    // --- Helper ---

    private static AdminLinkResponse toResponse(String type, Long id, Object entity, Long targetId) {
        String linkType = null;
        String title = null;
        String url = null;
        Integer sortOrder = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        if (entity instanceof SchoolLink sl) {
            linkType = sl.getLinkType();
            title = sl.getTitle();
            url = sl.getUrl();
            sortOrder = sl.getSortOrder();
            createdAt = sl.getCreatedAt();
            updatedAt = sl.getUpdatedAt();
        } else if (entity instanceof MajorLink ml) {
            linkType = ml.getLinkType();
            title = ml.getTitle();
            url = ml.getUrl();
            sortOrder = ml.getSortOrder();
            createdAt = ml.getCreatedAt();
            updatedAt = ml.getUpdatedAt();
        }

        return AdminLinkResponse.builder()
                .id(id)
                .type(type)
                .targetId(targetId)
                .linkType(linkType)
                .title(title)
                .url(url)
                .sortOrder(sortOrder)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLinkRequest {
        @NotBlank(message = "外链类型不能为空")
        private String type; // "school" or "major"

        @NotNull(message = "目标ID不能为空")
        private Long targetId; // schoolId or majorId

        private String linkType;

        @NotBlank(message = "标题不能为空")
        private String title;

        @NotBlank(message = "链接地址不能为空")
        private String url;

        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdminLinkResponse {
        private Long id;
        private String type; // "school" or "major"
        private Long targetId;
        private String linkType;
        private String title;
        private String url;
        private Integer sortOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

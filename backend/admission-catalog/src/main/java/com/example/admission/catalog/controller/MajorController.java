package com.example.admission.catalog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admission.catalog.dto.MajorCategoryResponse;
import com.example.admission.catalog.dto.MajorResponse;
import com.example.admission.catalog.entity.StandardMajor;
import com.example.admission.catalog.mapper.StandardMajorMapper;
import com.example.admission.common.ApiResponse;
import com.example.admission.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标准专业查询控制器.
 *
 * @author admission-system
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/majors")
@RequiredArgsConstructor
public class MajorController {

    private final StandardMajorMapper standardMajorMapper;

    /**
     * 专业门类和专业类树.
     */
    @GetMapping("/categories")
    public ApiResponse<List<MajorCategoryResponse>> getCategories() {
        List<StandardMajor> allMajors = standardMajorMapper.selectList(
                new LambdaQueryWrapper<StandardMajor>()
                        .orderByAsc(StandardMajor::getCategoryCode)
                        .orderByAsc(StandardMajor::getSubcategoryCode)
                        .orderByAsc(StandardMajor::getMajorCode)
        );

        // 按门类分组 (保持插入顺序)
        Map<String, MajorCategoryResponse> categoryMap = new LinkedHashMap<>();
        for (StandardMajor major : allMajors) {
            final String catCode = major.getCategoryCode() != null ? major.getCategoryCode() : "—";
            final String catName = major.getCategoryName() != null ? major.getCategoryName() : "—";

            MajorCategoryResponse category = categoryMap.computeIfAbsent(catCode, k -> {
                MajorCategoryResponse c = new MajorCategoryResponse();
                c.setCategoryCode(catCode);
                c.setCategoryName(catName);
                c.setSubcategories(new ArrayList<>());
                return c;
            });

            // 按专业类分组
            final String subCode = major.getSubcategoryCode() != null ? major.getSubcategoryCode() : "—";
            final String subName = major.getSubcategoryName() != null ? major.getSubcategoryName() : "—";

            MajorCategoryResponse.MajorSubcategory subcategory = category.getSubcategories().stream()
                    .filter(s -> s.getSubcategoryCode().equals(subCode))
                    .findFirst()
                    .orElseGet(() -> {
                        MajorCategoryResponse.MajorSubcategory sc = new MajorCategoryResponse.MajorSubcategory();
                        sc.setSubcategoryCode(subCode);
                        sc.setSubcategoryName(subName);
                        sc.setMajors(new ArrayList<>());
                        category.getSubcategories().add(sc);
                        return sc;
                    });

            subcategory.getMajors().add(toMajorResponse(major));
        }

        return ApiResponse.success(new ArrayList<>(categoryMap.values()));
    }

    /**
     * 标准专业列表查询 (分页).
     */
    @GetMapping
    public ApiResponse<PageResponse<MajorResponse>> listMajors(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryCode", required = false) String categoryCode,
            @RequestParam(value = "educationLevel", required = false) String educationLevel,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        LambdaQueryWrapper<StandardMajor> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(StandardMajor::getMajorName, keyword)
                    .or()
                    .like(StandardMajor::getMajorCode, keyword)
            );
        }
        if (StringUtils.hasText(categoryCode)) {
            wrapper.eq(StandardMajor::getCategoryCode, categoryCode);
        }
        if (StringUtils.hasText(educationLevel)) {
            wrapper.eq(StandardMajor::getEducationLevel, educationLevel);
        }

        wrapper.orderByAsc(StandardMajor::getMajorCode);

        Page<StandardMajor> entityPage = standardMajorMapper.selectPage(new Page<>(page, size), wrapper);
        List<MajorResponse> records = entityPage.getRecords().stream()
                .map(this::toMajorResponse)
                .collect(Collectors.toList());

        PageResponse<MajorResponse> pageResponse = PageResponse.of(records, entityPage.getTotal(), page, size);
        return ApiResponse.success(pageResponse);
    }

    // ==================== 实体 -> DTO 转换 ====================

    private MajorResponse toMajorResponse(StandardMajor major) {
        MajorResponse response = new MajorResponse();
        response.setId(major.getId());
        response.setCode(major.getMajorCode());
        response.setName(major.getMajorName());
        response.setCategoryCode(major.getCategoryCode());
        response.setCategoryName(major.getCategoryName());
        response.setSubcategoryCode(major.getSubcategoryCode());
        response.setSubcategoryName(major.getSubcategoryName());
        response.setEducationLevel(major.getEducationLevel());
        return response;
    }
}

package com.example.admission.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 专业门类树响应.
 *
 * @author admission-system
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MajorCategoryResponse {

    /** 专业门类代码 */
    private String categoryCode;

    /** 专业门类名称 */
    private String categoryName;

    /** 下属专业类列表 */
    private List<MajorSubcategory> subcategories;

    /**
     * 专业类节点.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MajorSubcategory {

        /** 专业类代码 */
        private String subcategoryCode;

        /** 专业类名称 */
        private String subcategoryName;

        /** 该专业类下的标准专业列表 */
        private List<MajorResponse> majors;
    }
}

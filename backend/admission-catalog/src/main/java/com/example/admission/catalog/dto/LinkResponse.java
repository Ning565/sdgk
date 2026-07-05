package com.example.admission.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外链响应.
 *
 * @author admission-system
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkResponse {

    /** 链接类型: SUNSHINE_MAJOR / SCHOOL_MAJOR / SCHOOL_ADMISSION / SCHOOL_OFFICIAL */
    private String linkType;

    /** 链接标题 */
    private String title;

    /** 链接地址 */
    private String url;
}

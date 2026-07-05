package com.example.admission.catalog.dto;

import lombok.Data;

/**
 * 院校基本信息响应.
 *
 * @author admission-system
 */
@Data
public class SchoolResponse {

    /** 院校ID */
    private Long id;

    /** 院校代码 */
    private String code;

    /** 院校名称 */
    private String name;

    /** 院校简称 */
    private String shortName;

    /** 所在省份 */
    private String province;

    /** 所在城市 */
    private String city;

    /** 院校类型 */
    private String schoolType;

    /** 院校标签 (985/211/双一流 等) */
    private String schoolTag;

    /** 官网地址 */
    private String website;

    /** 校徽Logo URL */
    private String logoUrl;
}

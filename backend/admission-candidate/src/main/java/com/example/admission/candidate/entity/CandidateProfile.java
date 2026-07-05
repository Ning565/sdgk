package com.example.admission.candidate.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.admission.candidate.util.SubjectComboUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 考生档案实体，对应 candidate_profile 表.
 *
 * <p>存储考生的高考分数、位次、选科组合、地域/专业偏好等信息。
 * subjects 字段在数据库中以 JSON 字符串存储，通过 get/set 方法实现 List 互转。</p>
 *
 * <p><b>选科组合编码规则：</b>山东6选3共20种组合，索引 0-19，
 * 具体映射参见 {@link SubjectComboUtil}。</p>
 */
@Data
@TableName("candidate_profile")
public class CandidateProfile {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfile.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 高考年份 */
    private Integer year;

    /** 高考分数 */
    private Integer score;

    /** 省排名位次 */
    @TableField("`rank`") private Integer rank;

    /** 位次来源: AUTO(自动匹配一分一段表) / MANUAL(手动填写) */
    private String rankSource;

    /** 选科组合索引（0-19），由 subjectComboUtil 计算 */
    private Integer subjectComboIndex;

    /** 选科科目 JSON 字符串（数据库存储字段） */
    @TableField("subjects_json")
    private String subjectsJson;

    /** 学历层次: UNDERGRADUATE / VOCATIONAL / UNLIMITED */
    private String educationLevel;

    /** 意向地域 JSON 字符串 */
    @TableField(value = "preferred_regions_json", updateStrategy = FieldStrategy.ALWAYS)
    private String preferredRegionsJson;

    /** 意向专业 JSON 字符串 */
    @TableField(value = "preferred_majors_json", updateStrategy = FieldStrategy.ALWAYS)
    private String preferredMajorsJson;

    /** 排除专业 JSON 字符串 */
    @TableField(value = "excluded_majors_json", updateStrategy = FieldStrategy.ALWAYS)
    private String excludedMajorsJson;

    /** 学费上限（元/年） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer tuitionMax;

    /** 学校性质偏好: PUBLIC / PRIVATE / UNLIMITED */
    private String schoolNature;

    /** 是否接受中外合作办学 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean acceptJointProgram;

    /** 合作类型偏好 JSON 字符串 */
    @TableField(value = "cooperation_types_json", updateStrategy = FieldStrategy.ALWAYS)
    private String cooperationTypesJson;

    /** 备注说明 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // --- JSON 与 List 互转方法 ---

    /**
     * 获取选科科目列表（从 JSON 反序列化）.
     *
     * @return 科目名称列表，解析失败时返回空列表
     */
    public List<String> getSubjects() {
        return parseJsonList(subjectsJson);
    }

    /**
     * 设置选科科目列表（序列化为 JSON），同时计算并更新 subjectComboIndex.
     *
     * @param subjects 科目名称列表
     */
    public void setSubjects(List<String> subjects) {
        this.subjectsJson = toJson(subjects);
        if (subjects != null && subjects.size() == 3) {
            this.subjectComboIndex = SubjectComboUtil.getComboIndex(subjects);
        }
    }

    /** 获取意向地域列表 */
    public List<String> getPreferredRegions() {
        return parseJsonList(preferredRegionsJson);
    }

    /** 设置意向地域列表 */
    public void setPreferredRegions(List<String> preferredRegions) {
        this.preferredRegionsJson = toJson(preferredRegions);
    }

    /** 获取意向专业列表 */
    public List<String> getPreferredMajors() {
        return parseJsonList(preferredMajorsJson);
    }

    /** 设置意向专业列表 */
    public void setPreferredMajors(List<String> preferredMajors) {
        this.preferredMajorsJson = toJson(preferredMajors);
    }

    /** 获取排除专业列表 */
    public List<String> getExcludedMajors() {
        return parseJsonList(excludedMajorsJson);
    }

    /** 设置排除专业列表 */
    public void setExcludedMajors(List<String> excludedMajors) {
        this.excludedMajorsJson = toJson(excludedMajors);
    }

    /** 获取合作类型偏好列表 */
    public List<String> getCooperationTypes() {
        return parseJsonList(cooperationTypesJson);
    }

    /** 设置合作类型偏好列表 */
    public void setCooperationTypes(List<String> cooperationTypes) {
        this.cooperationTypesJson = toJson(cooperationTypes);
    }

    // --- 位次来源常量 ---

    /** 自动匹配一分一段表 */
    public static final String RANK_SOURCE_AUTO = "AUTO";

    /** 手动填写位次 */
    public static final String RANK_SOURCE_MANUAL = "MANUAL";

    // --- 学历层次常量 ---

    public static final String EDU_UNDERGRADUATE = "UNDERGRADUATE";
    public static final String EDU_VOCATIONAL = "VOCATIONAL";
    public static final String EDU_UNLIMITED = "UNLIMITED";

    // --- 学校性质常量 ---

    public static final String SCHOOL_PUBLIC = "PUBLIC";
    public static final String SCHOOL_PRIVATE = "PRIVATE";
    public static final String SCHOOL_UNLIMITED = "UNLIMITED";

    // --- Private helpers ---

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON list: {}", json, e);
            return Collections.emptyList();
        }
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list to JSON: {}", list, e);
            return null;
        }
    }
}

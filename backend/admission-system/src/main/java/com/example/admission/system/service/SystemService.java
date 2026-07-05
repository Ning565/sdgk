package com.example.admission.system.service;

import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.system.dto.DictionaryResponse;
import com.example.admission.system.dto.YearConfigResponse;
import com.example.admission.system.entity.YearConfig;
import com.example.admission.system.mapper.YearConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统配置服务.
 *
 * <p>提供年度配置查询和字典数据查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final YearConfigMapper yearConfigMapper;

    /**
     * 获取指定年度的配置.
     *
     * @param year 年份 招生年度
     * @return 年度配置响应
     * @throws BusinessException 业务异常 如果年度配置不存在
     */
    public YearConfigResponse getYearConfig(Integer year) {
        YearConfig config = yearConfigMapper.selectByYear(year);
        if (config == null) {
            throw new BusinessException(ErrorCode.YEAR_CONFIG_NOT_FOUND,
                    "年度 " + year + " 的配置不存在");
        }
        return toResponse(config);
    }

    /**
     * 获取所有活跃年度列表.
     *
     * @return 活跃年度配置列表，按年度降序排列
     */
    public List<YearConfigResponse> listActiveYears() {
        List<YearConfig> configs = yearConfigMapper.selectActiveYears();
        return configs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类型的字典项.
     *
     * <p>支持的字典类型:
     * <ul>
     *   <li>education_level — 教育层次</li>
     *   <li>school_type — 院校性质</li>
     *   <li>school_tag — 院校标签</li>
     *   <li>enrollment_type — 招生类型</li>
     *   <li>plan_status — 计划状态</li>
     *   <li>subject — 选考科目</li>
     *   <li>volunteer_label — 志愿标签（冲/稳/保）</li>
     * </ul>
     *
     * @param type 字典类型
     * @return 字典项列表
     */
    public List<DictionaryResponse> getDictionaries(String type) {
        if (type == null || type.isBlank()) {
            return getAllDictionaries();
        }
        return DICTIONARY_MAP.getOrDefault(type, Collections.emptyList());
    }

    /**
     * 获取所有字典类型.
     */
    private List<DictionaryResponse> getAllDictionaries() {
        List<DictionaryResponse> all = new ArrayList<>();
        DICTIONARY_MAP.forEach((type, items) -> all.addAll(items));
        return all;
    }

    // --- Helper ---

    private YearConfigResponse toResponse(YearConfig config) {
        return YearConfigResponse.builder()
                .year(config.getYear())
                .scoreMin(config.getScoreMin())
                .scoreMax(config.getScoreMax())
                .volunteerLimit(config.getVolunteerLimit())
                .isOpen(config.getIsOpen() != null && config.getIsOpen() == 1)
                .remark(config.getRemark())
                .build();
    }

    // --- 内置字典数据 ---

    private static final Map<String, List<DictionaryResponse>> DICTIONARY_MAP = new LinkedHashMap<>();

    static {
        DICTIONARY_MAP.put("education_level", Arrays.asList(
                dict("UNDERGRADUATE", "本科", 1),
                dict("VOCATIONAL", "专科", 2),
                dict("UNLIMITED", "不限", 3)
        ));

        DICTIONARY_MAP.put("school_type", Arrays.asList(
                dict("PUBLIC", "公办", 1),
                dict("PRIVATE", "民办", 2),
                dict("SINO_FOREIGN", "中外合作办学", 3),
                dict("INDEPENDENT", "独立学院", 4)
        ));

        DICTIONARY_MAP.put("school_tag", Arrays.asList(
                dict("985", "985工程", 1),
                dict("211", "211工程", 2),
                dict("DOUBLE_FIRST_CLASS", "双一流", 3),
                dict("C9", "九校联盟", 4),
                dict("211_PLATFORM", "211平台", 5)
        ));

        DICTIONARY_MAP.put("enrollment_type", Arrays.asList(
                dict("NORMAL", "普通类", 1),
                dict("SINO_FOREIGN", "中外合作办学", 2),
                dict("SCHOOL_ENTERPRISE", "校企合作", 3),
                dict("ARTS", "艺术类", 4),
                dict("SPORTS", "体育类", 5),
                dict("MILITARY", "军事类", 6)
        ));

        DICTIONARY_MAP.put("plan_status", Arrays.asList(
                dict("ACTIVE", "在招", 1),
                dict("NEW", "新增", 2),
                dict("STOPPED", "停招", 3),
                dict("REVOKED", "撤销", 4)
        ));

        DICTIONARY_MAP.put("subject", Arrays.asList(
                dict("PHYSICS", "物理", 1),
                dict("CHEMISTRY", "化学", 2),
                dict("BIOLOGY", "生物", 3),
                dict("HISTORY", "历史", 4),
                dict("GEOGRAPHY", "地理", 5),
                dict("POLITICS", "政治", 6)
        ));

        DICTIONARY_MAP.put("volunteer_label", Arrays.asList(
                dict("CHONG", "冲", 1),
                dict("WEN", "稳", 2),
                dict("BAO", "保", 3)
        ));

        DICTIONARY_MAP.put("confidence", Arrays.asList(
                dict("LOW", "低", 1),
                dict("MEDIUM", "中", 2),
                dict("HIGH", "高", 3)
        ));
    }

    private static DictionaryResponse dict(String code, String name, int sortOrder) {
        return DictionaryResponse.builder()
                .code(code)
                .name(name)
                .sortOrder(sortOrder)
                .build();
    }
}

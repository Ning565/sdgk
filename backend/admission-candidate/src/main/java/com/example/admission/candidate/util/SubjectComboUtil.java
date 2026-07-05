package com.example.admission.candidate.util;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 山东6选3选科组合工具类.
 *
 * <p>山东省高考采用"3+3"模式，考生在物理、化学、生物、历史、地理、政治6科中任选3科，
 * 共20种组合。每种组合对应唯一索引（0-19），用于快速匹配招生计划的选科要求。</p>
 *
 * <h3>20种组合索引映射</h3>
 * <pre>
 *  0: 物理+化学+生物    1: 物理+化学+政治    2: 物理+化学+历史    3: 物理+化学+地理
 *  4: 物理+生物+政治    5: 物理+生物+历史    6: 物理+生物+地理    7: 物理+政治+历史
 *  8: 物理+政治+地理    9: 物理+历史+地理   10: 化学+生物+政治   11: 化学+生物+历史
 * 12: 化学+生物+地理   13: 化学+政治+历史   14: 化学+政治+地理   15: 化学+历史+地理
 * 16: 生物+政治+历史   17: 生物+政治+地理   18: 生物+历史+地理   19: 政治+历史+地理
 * </pre>
 *
 * <p>注意：此映射顺序与 config 中的 subject-combos 配置保持一致。</p>
 *
 * @author admission-candidate
 */
@Slf4j
public final class SubjectComboUtil {

    /** 6个科目的标准名称 */
    private static final List<String> SUBJECTS = List.of("物理", "化学", "生物", "历史", "地理", "政治");

    /**
     * 20种组合，每种组合为3个科目名称的有序列表.
     * <p>索引顺序与 application.yml 中 app.candidate.subject-combos 配置一致。</p>
     */
    private static final List<List<String>> COMBOS = List.of(
            List.of("物理", "化学", "生物"),  // 0: 物化生
            List.of("物理", "化学", "政治"),  // 1: 物化政
            List.of("物理", "化学", "历史"),  // 2: 物化史
            List.of("物理", "化学", "地理"),  // 3: 物化地
            List.of("物理", "生物", "政治"),  // 4: 物生政
            List.of("物理", "生物", "历史"),  // 5: 物生史
            List.of("物理", "生物", "地理"),  // 6: 物生地
            List.of("物理", "政治", "历史"),  // 7: 物政史
            List.of("物理", "政治", "地理"),  // 8: 物政地
            List.of("物理", "历史", "地理"),  // 9: 物史地
            List.of("化学", "生物", "政治"),  // 10: 化生政
            List.of("化学", "生物", "历史"),  // 11: 化生史
            List.of("化学", "生物", "地理"),  // 12: 化生地
            List.of("化学", "政治", "历史"),  // 13: 化政史
            List.of("化学", "政治", "地理"),  // 14: 化政地
            List.of("化学", "历史", "地理"),  // 15: 化史地
            List.of("生物", "政治", "历史"),  // 16: 生政史
            List.of("生物", "政治", "地理"),  // 17: 生政地
            List.of("生物", "历史", "地理"),  // 18: 生史地
            List.of("政治", "历史", "地理")   // 19: 政史地
    );

    /** 20种组合的简称（取每科首字） */
    private static final List<String> COMBO_NAMES = List.of(
            "物化生", "物化政", "物化史", "物化地",
            "物生政", "物生史", "物生地",
            "物政史", "物政地", "物史地",
            "化生政", "化生史", "化生地",
            "化政史", "化政地", "化史地",
            "生政史", "生政地", "生史地",
            "政史地"
    );

    /** 简写名称到组合索引的映射（O(1) 查询） */
    private static final Map<String, Integer> NAME_TO_INDEX = new LinkedHashMap<>();
    static {
        for (int i = 0; i < COMBO_NAMES.size(); i++) {
            NAME_TO_INDEX.put(COMBO_NAMES.get(i), i);
        }
    }

    private SubjectComboUtil() {
    }

    /**
     * 根据3门科目名称获取组合索引.
     *
     * <p>科目名称支持标准中文名（如"物理"）和简称（如"物"）。
     * 传入的科目顺序无关，内部会排序后匹配。</p>
     *
     * @param subjects 3门科目名称列表
     * @return 组合索引 (0-19)，匹配失败或参数无效时返回 -1
     */
    public static int getComboIndex(List<String> subjects) {
        if (subjects == null || subjects.size() != 3) {
            return -1;
        }

        // 标准化科目名称为集合
        Set<String> normalizedSet = new LinkedHashSet<>();
        for (String s : subjects) {
            String normalized = normalize(s);
            if (normalized == null) {
                return -1;
            }
            normalizedSet.add(normalized);
        }

        // 必须有恰好3门不同科目
        if (normalizedSet.size() != 3) {
            return -1;
        }

        // 与20种组合逐一比对
        List<String> sorted = new ArrayList<>(normalizedSet);
        Collections.sort(sorted);

        for (int i = 0; i < COMBOS.size(); i++) {
            List<String> combo = new ArrayList<>(COMBOS.get(i));
            Collections.sort(combo);
            if (sorted.equals(combo)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 根据组合索引获取科目列表.
     *
     * @param index 组合索引 (0-19)
     * @return 3门科目名称列表，索引无效时返回空列表
     */
    public static List<String> getComboByIndex(int index) {
        if (index < 0 || index >= COMBOS.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(COMBOS.get(index));
    }

    /**
     * 判断指定的3门科目是否为有效的山东6选3组合.
     *
     * @param subjects 科目名称列表
     * @return true 如果是20种有效组合之一
     */
    public static boolean isValidCombo(List<String> subjects) {
        return getComboIndex(subjects) >= 0;
    }

    /**
     * 根据组合索引获取简称（如 "物化生"）.
     *
     * @param index 组合索引 (0-19)
     * @return 组合简称，索引无效时返回 "未知"
     */
    public static String getComboName(int index) {
        if (index < 0 || index >= COMBO_NAMES.size()) {
            return "未知";
        }
        return COMBO_NAMES.get(index);
    }

    /**
     * 获取全部20种组合的索引和名称列表.
     *
     * @return 组合列表，每项为 Map.Entry(index, name)
     */
    public static List<Map.Entry<Integer, String>> getAllCombos() {
        List<Map.Entry<Integer, String>> combos = new ArrayList<>();
        for (int i = 0; i < COMBO_NAMES.size(); i++) {
            combos.add(new AbstractMap.SimpleEntry<>(i, COMBO_NAMES.get(i)));
        }
        return combos;
    }

    /**
     * 标准化科目名称.
     * <p>支持完整名称（"物理"）和简称（"物"），统一转为完整名称。</p>
     *
     * @param name 名称 科目名称
     * @return 标准化后的完整名称，无法识别时返回 null
     */
    private static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        // 完整名称直接匹配
        if (SUBJECTS.contains(trimmed)) {
            return trimmed;
        }
        // 按首字匹配简称
        if (trimmed.length() == 1) {
            for (String s : SUBJECTS) {
                if (s.startsWith(trimmed)) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * 获取组合总数（固定为20）.
     *
     * @return 20
     */
    public static int getComboCount() {
        return COMBOS.size();
    }
}

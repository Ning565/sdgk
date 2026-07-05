package com.example.admission.dataimport.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 山东6选3选科位图工具.
 *
 * <p>山东省高考采用"3+3"模式，考生在物理、化学、生物、历史、地理、政治6科中任选3科，
 * 共20种组合。每种组合对应一个位图索引位（0-19），用于快速匹配招生计划的选科要求。</p>
 *
 * <h3>20种组合索引映射</h3>
 * <pre>
 *  0: 物理+化学+生物    1: 物理+化学+政治    2: 物理+化学+地理    3: 物理+化学+历史
 *  4: 物理+生物+政治    5: 物理+生物+地理    6: 物理+生物+历史    7: 物理+政治+地理
 *  8: 物理+政治+历史    9: 物理+地理+历史   10: 化学+生物+政治   11: 化学+生物+地理
 * 12: 化学+生物+历史   13: 化学+政治+地理   14: 化学+政治+历史   15: 化学+地理+历史
 * 16: 生物+政治+地理   17: 生物+政治+历史   18: 生物+地理+历史   19: 政治+地理+历史
 * </pre>
 *
 * <h3>选科规则JSON格式</h3>
 * <pre>{@code
 * {
 *   "displayText": "物理,化学(2门科目考生均须选考方可报考)",
 *   "matchType": "all",
 *   "subjects": ["物理", "化学"]
 * }
 * }</pre>
 * matchType: all(必须全选) / any(任选其一) / none(排除) / custom(自定义组合)
 *
 * @author admission-system
 */
public final class SubjectBitmapUtil {

    private static final Logger log = LoggerFactory.getLogger(SubjectBitmapUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 6个科目的标准名称数组 */
    private static final String[] SUBJECTS = {"物理", "化学", "生物", "历史", "地理", "政治"};

    /** 20种组合的三科索引数组，每个组合是一个长度为3的int数组，值为科目在SUBJECTS中的索引 */
    private static final int[][] COMBO_SUBJECT_INDICES = {
        {0, 1, 2},  // 0: 物理 化学 生物
        {0, 1, 5},  // 1: 物理 化学 政治
        {0, 1, 4},  // 2: 物理 化学 地理
        {0, 1, 3},  // 3: 物理 化学 历史
        {0, 2, 5},  // 4: 物理 生物 政治
        {0, 2, 4},  // 5: 物理 生物 地理
        {0, 2, 3},  // 6: 物理 生物 历史
        {0, 5, 4},  // 7: 物理 政治 地理
        {0, 5, 3},  // 8: 物理 政治 历史
        {0, 4, 3},  // 9: 物理 地理 历史
        {1, 2, 5},  // 10: 化学 生物 政治
        {1, 2, 4},  // 11: 化学 生物 地理
        {1, 2, 3},  // 12: 化学 生物 历史
        {1, 5, 4},  // 13: 化学 政治 地理
        {1, 5, 3},  // 14: 化学 政治 历史
        {1, 4, 3},  // 15: 化学 地理 历史
        {2, 5, 4},  // 16: 生物 政治 地理
        {2, 5, 3},  // 17: 生物 政治 历史
        {2, 4, 3},  // 18: 生物 地理 历史
        {5, 4, 3},  // 19: 政治 地理 历史
    };

    /** 科目名称到索引的映射 */
    private static final Map<String, Integer> SUBJECT_INDEX_MAP = new LinkedHashMap<>();
    static {
        for (int i = 0; i < SUBJECTS.length; i++) {
            SUBJECT_INDEX_MAP.put(SUBJECTS[i], i);
        }
    }

    private SubjectBitmapUtil() {
    }

    /**
     * 根据选科规则JSON计算符合条件的组合位图.
     *
     * <p>遍历全部20种组合，对每种组合判断是否满足规则，
     * 满足则将该组合对应的位设为1。</p>
     *
     * @param subjectRuleJson 选科规则JSON字符串，可为null
     * @return 符合条件的组合位图（Long类型，低20位有效），规则为空时返回0xFFFFF（全部20种组合均符合）
     */
    public static Long computeEligibleBitmap(String subjectRuleJson) {
        if (subjectRuleJson == null || subjectRuleJson.isBlank()) {
            // 无选科要求 = 所有组合均可
            return 0xFFFFFL;
        }
        try {
            Map<String, Object> rule = objectMapper.readValue(subjectRuleJson,
                    new TypeReference<Map<String, Object>>() {});
            String matchType = Optional.ofNullable(rule.get("matchType"))
                    .map(Object::toString).orElse("all");
            @SuppressWarnings("unchecked")
            List<String> subjects = (List<String>) rule.getOrDefault("subjects", Collections.emptyList());

            long bitmap = 0L;
            for (int comboIndex = 0; comboIndex < 20; comboIndex++) {
                if (matchComboRule(comboIndex, matchType, subjects)) {
                    bitmap |= (1L << comboIndex);
                }
            }
            return bitmap;
        } catch (Exception e) {
            log.warn("Failed to parse subject rule JSON: {}", subjectRuleJson, e);
            // 解析失败时保守处理：视为无限制
            return 0xFFFFFL;
        }
    }

    /**
     * 判断指定组合是否满足选科规则.
     *
     * @param comboIndex 组合索引 (0-19)
     * @param matchType  匹配类型: all / any / none / custom
     * @param subjects   规则中要求的科目列表
     * @return true 如果该组合满足规则
     */
    private static boolean matchComboRule(int comboIndex, String matchType, List<String> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return true;
        }
        int[] comboScores = COMBO_SUBJECT_INDICES[comboIndex];
        Set<String> comboSubjectNames = new HashSet<>();
        for (int idx : comboScores) {
            comboSubjectNames.add(SUBJECTS[idx]);
        }

        switch (matchType.toLowerCase()) {
            case "all":
                // 必须包含所有要求的科目
                for (String subject : subjects) {
                    if (!comboSubjectNames.contains(subject)) {
                        return false;
                    }
                }
                return true;
            case "any":
                // 包含任一要求的科目即可
                for (String subject : subjects) {
                    if (comboSubjectNames.contains(subject)) {
                        return true;
                    }
                }
                return false;
            case "none":
                // 不能包含任何列出的科目
                for (String subject : subjects) {
                    if (comboSubjectNames.contains(subject)) {
                        return false;
                    }
                }
                return true;
            case "custom":
                // 自定义组合：subjects中直接列出的是允许的组合名称
                // 格式如: ["物化生", "物化地", ...]
                return subjects.contains(getComboName(comboIndex));
            default:
                log.warn("Unknown subject match type: {}, treating as all", matchType);
                for (String subject : subjects) {
                    if (!comboSubjectNames.contains(subject)) {
                        return false;
                    }
                }
                return true;
        }
    }

    /**
     * 判断指定位图是否包含指定组合索引.
     *
     * @param bitmap     组合位图
     * @param comboIndex 组合索引 (0-19)
     * @return true 如果该组合在位图中被标记为符合
     */
    public static boolean matchCombo(Long bitmap, int comboIndex) {
        if (bitmap == null || comboIndex < 0 || comboIndex > 19) {
            return false;
        }
        return (bitmap & (1L << comboIndex)) != 0;
    }

    /**
     * 根据三科科目名称获取组合索引.
     *
     * @param subjects 三科科目名称数组，顺序无关
     * @return 组合索引 (0-19)，未匹配时返回 -1
     */
    public static int getComboIndex(String[] subjects) {
        if (subjects == null || subjects.length != 3) {
            return -1;
        }
        int[] indices = new int[3];
        for (int i = 0; i < 3; i++) {
            Integer idx = SUBJECT_INDEX_MAP.get(subjects[i]);
            if (idx == null) {
                return -1;
            }
            indices[i] = idx;
        }
        Arrays.sort(indices);
        for (int i = 0; i < 20; i++) {
            int[] sorted = COMBO_SUBJECT_INDICES[i].clone();
            Arrays.sort(sorted);
            if (Arrays.equals(sorted, indices)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取组合索引对应的中文名称.
     *
     * @param comboIndex 组合索引 (0-19)
     * @return 组合名称，如"物化生"
     */
    public static String getComboName(int comboIndex) {
        if (comboIndex < 0 || comboIndex >= 20) {
            return "未知";
        }
        int[] indices = COMBO_SUBJECT_INDICES[comboIndex];
        StringBuilder sb = new StringBuilder(3);
        for (int idx : indices) {
            sb.append(SUBJECTS[idx].charAt(0));
        }
        return sb.toString();
    }

    /**
     * 获取全部20种组合的索引和名称列表.
     *
     * @return 组合列表，每项为 [index, name]
     */
    public static List<Map.Entry<Integer, String>> getAllCombos() {
        List<Map.Entry<Integer, String>> combos = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            combos.add(new AbstractMap.SimpleEntry<>(i, getComboName(i)));
        }
        return combos;
    }

    /**
     * 统计位图中符合的组合数量.
     *
     * @param bitmap 组合位图
     * @return 符合条件的组合数量 (0-20)
     */
    public static int countEligibleCombos(Long bitmap) {
        if (bitmap == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < 20; i++) {
            if ((bitmap & (1L << i)) != 0) {
                count++;
            }
        }
        return count;
    }
}

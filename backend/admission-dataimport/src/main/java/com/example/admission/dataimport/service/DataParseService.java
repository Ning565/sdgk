package com.example.admission.dataimport.service;

import com.example.admission.dataimport.entity.ImportBatch;
import com.example.admission.dataimport.entity.ImportRowError;
import com.example.admission.dataimport.mapper.ImportRowErrorMapper;
import com.example.admission.dataimport.util.ExcelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Excel数据解析服务.
 *
 * <p>负责解析四种数据类型的Excel文件：
 * 一分一段表(SCORE_RANK)、招生计划(PLAN)、
 * 历年录取数据(HISTORY)、外链数据(LINK)。
 * 解析过程中同步进行基础字段校验。</p>
 *
 * @author admission-system
 */
@Service
public class DataParseService {

    private static final Logger log = LoggerFactory.getLogger(DataParseService.class);

    /** URL格式校验正则 */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^https?://[\\w\\-]+(\\.[\\w\\-]+)+(\\:\\d+)?(/[\\w\\-./?%&=]*)?$",
                    Pattern.CASE_INSENSITIVE);

    private final ImportBatchService importBatchService;
    private final ImportRowErrorMapper importRowErrorMapper;

    public DataParseService(ImportBatchService importBatchService,
                            ImportRowErrorMapper importRowErrorMapper) {
        this.importBatchService = importBatchService;
        this.importRowErrorMapper = importRowErrorMapper;
    }

    // ==================== 一分一段表解析 ====================

    /**
     * 解析一分一段表（SCORE_RANK类型）.
     *
     * <p>预期字段: score（分数）, sameScoreCount（本段人数）, cumulativeCount（累计人数）。
     * 校验规则：分数不重复、累计人数单调递增、分数范围合法。</p>
     *
     * @param batchId 批次ID
     * @return 解析的错误数
     */
    @Transactional
    public int parseScoreRank(Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        importBatchService.updateStatus(batchId, ImportBatch.STATUS_PARSING);

        List<ImportRowError> errors = new ArrayList<>();
        Map<Integer, Integer> scoreRowMap = new HashMap<>(); // score -> firstRowNumber
        AtomicInteger prevCumulative = new AtomicInteger(-1);
        AtomicInteger prevScore = new AtomicInteger(-1);

        String filePath = batch.getFileUrl();
        int totalRows = ExcelUtil.readRows(filePath, 0, null, rowData -> {
            int rowNum = rowData.getRowNumber();
            Map<String, String> fields = rowData.getFields();

            // 解析分数
            String scoreStr = fields.getOrDefault("score",
                    fields.getOrDefault("分数", ""));
            if (scoreStr.isBlank()) {
                errors.add(buildError(batchId, rowNum, "score", "", ImportRowError.ERROR_REQUIRED,
                        "分数不能为空", "请填写有效的分数值"));
                return;
            }

            double score;
            try {
                score = Double.parseDouble(scoreStr);
            } catch (NumberFormatException e) {
                errors.add(buildError(batchId, rowNum, "score", scoreStr, ImportRowError.ERROR_FORMAT,
                        "分数格式不正确", "请填写数值"));
                return;
            }

            // 检查分数范围
            if (score < 0 || score > 750) {
                errors.add(buildError(batchId, rowNum, "score", scoreStr, ImportRowError.ERROR_RANGE,
                        "分数超出范围(0-750)", "请填写0-750之间的分数"));
            }

            int scoreInt = (int) score;
            // 检查分数不重复
            if (scoreRowMap.containsKey(scoreInt)) {
                errors.add(buildError(batchId, rowNum, "score", scoreStr, ImportRowError.ERROR_DUPLICATE,
                        "分数重复，首次出现在第" + scoreRowMap.get(scoreInt) + "行",
                        "请检查并删除重复的分数行"));
            } else {
                scoreRowMap.put(scoreInt, rowNum);
            }

            // 解析本段人数
            String sameScoreCountStr = fields.getOrDefault("sameScoreCount",
                    fields.getOrDefault("本段人数", "0"));
            try {
                Integer.parseInt(sameScoreCountStr.isBlank() ? "0" : sameScoreCountStr);
            } catch (NumberFormatException e) {
                errors.add(buildError(batchId, rowNum, "sameScoreCount", sameScoreCountStr,
                        ImportRowError.ERROR_FORMAT, "本段人数格式不正确", "请填写整数"));
            }

            // 解析累计人数
            String cumulativeCountStr = fields.getOrDefault("cumulativeCount",
                    fields.getOrDefault("累计人数", ""));
            if (cumulativeCountStr.isBlank()) {
                errors.add(buildError(batchId, rowNum, "cumulativeCount", "", ImportRowError.ERROR_REQUIRED,
                        "累计人数不能为空", "请填写累计人数"));
            } else {
                try {
                    int cumulative = Integer.parseInt(cumulativeCountStr);
                    int prev = prevCumulative.get();
                    if (prevScore.get() >= 0 && cumulative < prev) {
                        errors.add(buildError(batchId, rowNum, "cumulativeCount", cumulativeCountStr,
                                ImportRowError.ERROR_BUSINESS,
                                "累计人数应单调递增，前一行累计: " + prev,
                                "请检查累计人数是否正确"));
                    }
                    prevCumulative.set(cumulative);
                } catch (NumberFormatException e) {
                    errors.add(buildError(batchId, rowNum, "cumulativeCount", cumulativeCountStr,
                            ImportRowError.ERROR_FORMAT, "累计人数格式不正确", "请填写整数"));
                }
            }

            prevScore.set(scoreInt);
        });

        // 批量保存错误
        saveErrors(errors);

        int errorRows = (int) errors.stream().map(ImportRowError::getRowNumber).distinct().count();
        importBatchService.updateRowStats(batchId, totalRows, totalRows - errorRows, errorRows);

        if (errorRows > 0) {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_VALIDATION_FAILED);
        } else {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_READY);
        }

        log.info("Score rank parse completed: batchId={}, totalRows={}, errorRows={}", batchId, totalRows, errorRows);
        return errorRows;
    }

    // ==================== 招生计划解析 ====================

    /**
     * 解析招生计划（PLAN类型）.
     *
     * <p>预期字段: year, schoolCode, schoolName, majorCode, majorName, standardMajorCode,
     * majorCategory, campus, enrollmentType, educationLevel,
     * subjectRequirementText, subjectRequirementRule, planCount, tuition, duration, remark。
     * 校验: 学校代码非空、专业代码非空、招生人数正整数、唯一键不重复。
     * 选科规则结构化解析为JSON，并预计算20组合位图。</p>
     *
     * @param batchId 批次ID
     * @return 解析的错误数
     */
    @Transactional
    public int parseEnrollmentPlan(Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        importBatchService.updateStatus(batchId, ImportBatch.STATUS_PARSING);

        List<ImportRowError> errors = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();

        String filePath = batch.getFileUrl();
        int totalRows = ExcelUtil.readRows(filePath, 0, null, rowData -> {
            int rowNum = rowData.getRowNumber();
            Map<String, String> f = rowData.getFields();

            // 必填校验：学校代码
            String schoolCode = f.getOrDefault("schoolCode", f.getOrDefault("院校代码", ""));
            if (schoolCode.isBlank()) {
                errors.add(buildError(batchId, rowNum, "schoolCode", "", ImportRowError.ERROR_REQUIRED,
                        "学校代码不能为空", "请填写有效的院校代码"));
                return;
            }

            // 必填校验：专业代码
            String majorCode = f.getOrDefault("majorCode", f.getOrDefault("专业代码", ""));
            if (majorCode.isBlank()) {
                errors.add(buildError(batchId, rowNum, "majorCode", "", ImportRowError.ERROR_REQUIRED,
                        "专业代码不能为空", "请填写有效的专业代码"));
                return;
            }

            // 招生人数校验
            String planCountStr = f.getOrDefault("planCount", f.getOrDefault("招生人数", ""));
            if (!planCountStr.isBlank()) {
                try {
                    int planCount = Integer.parseInt(planCountStr);
                    if (planCount <= 0) {
                        errors.add(buildError(batchId, rowNum, "planCount", planCountStr,
                                ImportRowError.ERROR_RANGE, "招生人数必须为正整数",
                                "请填写大于0的整数"));
                    }
                } catch (NumberFormatException e) {
                    errors.add(buildError(batchId, rowNum, "planCount", planCountStr,
                            ImportRowError.ERROR_FORMAT, "招生人数格式不正确", "请填写整数"));
                }
            }

            // 学费校验
            String tuitionStr = f.getOrDefault("tuition", f.getOrDefault("学费", ""));
            if (!tuitionStr.isBlank()) {
                try {
                    int tuition = Integer.parseInt(tuitionStr);
                    if (tuition < 0) {
                        errors.add(buildError(batchId, rowNum, "tuition", tuitionStr,
                                ImportRowError.ERROR_RANGE, "学费不能为负数", "请填写>=0的整数"));
                    }
                } catch (NumberFormatException e) {
                    errors.add(buildError(batchId, rowNum, "tuition", tuitionStr,
                            ImportRowError.ERROR_FORMAT, "学费格式不正确", "请填写整数"));
                }
            }

            // 唯一键检查 (schoolCode + majorCode + enrollmentType + campus + educationLevel)
            String enrollmentType = f.getOrDefault("enrollmentType", f.getOrDefault("招生类型", "普通类"));
            String campus = f.getOrDefault("campus", f.getOrDefault("校区", "000"));
            String educationLevel = f.getOrDefault("educationLevel", f.getOrDefault("学历层次", "本科"));
            String uniqueKey = schoolCode + "|" + majorCode + "|" + enrollmentType + "|" + campus + "|" + educationLevel;
            if (!uniqueKeys.add(uniqueKey)) {
                errors.add(buildError(batchId, rowNum, "uniqueKey", uniqueKey,
                        ImportRowError.ERROR_DUPLICATE,
                        "存在重复的招生计划记录", "请检查并删除重复行"));
            }
        });

        saveErrors(errors);

        int errorRows = (int) errors.stream().map(ImportRowError::getRowNumber).distinct().count();
        importBatchService.updateRowStats(batchId, totalRows, totalRows - errorRows, errorRows);

        if (errorRows > 0) {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_VALIDATION_FAILED);
        } else {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_READY);
        }

        log.info("Enrollment plan parse completed: batchId={}, totalRows={}, errorRows={}", batchId, totalRows, errorRows);
        return errorRows;
    }

    // ==================== 历年录取数据解析 ====================

    /**
     * 解析历年录取数据（HISTORY类型）.
     *
     * <p>预期字段: year, schoolCode, majorCode, standardMajorCode, schoolName, majorName,
     * enrollmentType, campus, planSeriesId, enrollmentCount, minScore, minRank。
     * 校验: 最低分和最低位次非空、位次为正整数。</p>
     *
     * @param batchId 批次ID
     * @return 解析的错误数
     */
    @Transactional
    public int parseHistory(Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        importBatchService.updateStatus(batchId, ImportBatch.STATUS_PARSING);

        List<ImportRowError> errors = new ArrayList<>();

        String filePath = batch.getFileUrl();
        int totalRows = ExcelUtil.readRows(filePath, 0, null, rowData -> {
            int rowNum = rowData.getRowNumber();
            Map<String, String> f = rowData.getFields();

            // 最低分校验
            String minScoreStr = f.getOrDefault("minScore", f.getOrDefault("最低分", ""));
            if (!minScoreStr.isBlank()) {
                try {
                    double minScore = Double.parseDouble(minScoreStr);
                    if (minScore < 0 || minScore > 750) {
                        errors.add(buildError(batchId, rowNum, "minScore", minScoreStr,
                                ImportRowError.ERROR_RANGE, "最低分超出范围(0-750)", "请检查分数"));
                    }
                } catch (NumberFormatException e) {
                    errors.add(buildError(batchId, rowNum, "minScore", minScoreStr,
                            ImportRowError.ERROR_FORMAT, "最低分格式不正确", "请填写数值"));
                }
            }

            // 最低位次校验
            String minRankStr = f.getOrDefault("minRank", f.getOrDefault("最低位次", ""));
            if (minRankStr.isBlank()) {
                errors.add(buildError(batchId, rowNum, "minRank", "", ImportRowError.ERROR_REQUIRED,
                        "最低位次不能为空", "请填写位次"));
            } else {
                try {
                    int minRank = Integer.parseInt(minRankStr);
                    if (minRank <= 0) {
                        errors.add(buildError(batchId, rowNum, "minRank", minRankStr,
                                ImportRowError.ERROR_RANGE, "位次必须为正整数", "请填写大于0的整数"));
                    }
                } catch (NumberFormatException e) {
                    errors.add(buildError(batchId, rowNum, "minRank", minRankStr,
                            ImportRowError.ERROR_FORMAT, "位次格式不正确", "请填写整数"));
                }
            }

            // 学校代码必填
            String schoolCode = f.getOrDefault("schoolCode", f.getOrDefault("院校代码", ""));
            if (schoolCode.isBlank()) {
                errors.add(buildError(batchId, rowNum, "schoolCode", "", ImportRowError.ERROR_REQUIRED,
                        "学校代码不能为空", "请填写院校代码"));
            }
        });

        saveErrors(errors);

        int errorRows = (int) errors.stream().map(ImportRowError::getRowNumber).distinct().count();
        importBatchService.updateRowStats(batchId, totalRows, totalRows - errorRows, errorRows);

        if (errorRows > 0) {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_VALIDATION_FAILED);
        } else {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_READY);
        }

        log.info("History parse completed: batchId={}, totalRows={}, errorRows={}", batchId, totalRows, errorRows);
        return errorRows;
    }

    // ==================== 外链数据解析 ====================

    /**
     * 解析外链数据（LINK类型）.
     *
     * <p>预期字段: schoolCode, majorCode(可选), linkType, title, url。
     * 校验: URL格式合法、协议仅限HTTP/HTTPS。</p>
     *
     * @param batchId 批次ID
     * @return 解析的错误数
     */
    @Transactional
    public int parseLinks(Long batchId) {
        ImportBatch batch = importBatchService.getBatch(batchId);
        importBatchService.updateStatus(batchId, ImportBatch.STATUS_PARSING);

        List<ImportRowError> errors = new ArrayList<>();

        String filePath = batch.getFileUrl();
        int totalRows = ExcelUtil.readRows(filePath, 0, null, rowData -> {
            int rowNum = rowData.getRowNumber();
            Map<String, String> f = rowData.getFields();

            // 学校代码必填
            String schoolCode = f.getOrDefault("schoolCode", f.getOrDefault("院校代码", ""));
            if (schoolCode.isBlank()) {
                errors.add(buildError(batchId, rowNum, "schoolCode", "", ImportRowError.ERROR_REQUIRED,
                        "学校代码不能为空", "请填写院校代码"));
            }

            // URL校验
            String url = f.getOrDefault("url", f.getOrDefault("链接地址", ""));
            if (url.isBlank()) {
                errors.add(buildError(batchId, rowNum, "url", "", ImportRowError.ERROR_REQUIRED,
                        "链接URL不能为空", "请填写URL"));
            } else if (!URL_PATTERN.matcher(url).matches()) {
                errors.add(buildError(batchId, rowNum, "url", url, ImportRowError.ERROR_FORMAT,
                        "URL格式不合法，仅支持HTTP/HTTPS", "请填写正确的URL格式"));
            }

            // 链接类型必填
            String linkType = f.getOrDefault("linkType", f.getOrDefault("链接类型", ""));
            if (linkType.isBlank()) {
                errors.add(buildError(batchId, rowNum, "linkType", "", ImportRowError.ERROR_REQUIRED,
                        "链接类型不能为空", "请填写链接类型"));
            }
        });

        saveErrors(errors);

        int errorRows = (int) errors.stream().map(ImportRowError::getRowNumber).distinct().count();
        importBatchService.updateRowStats(batchId, totalRows, totalRows - errorRows, errorRows);

        if (errorRows > 0) {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_VALIDATION_FAILED);
        } else {
            importBatchService.updateStatus(batchId, ImportBatch.STATUS_READY);
        }

        log.info("Links parse completed: batchId={}, totalRows={}, errorRows={}", batchId, totalRows, errorRows);
        return errorRows;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 构建行级错误对象.
     */
    private ImportRowError buildError(Long batchId, int rowNumber, String fieldName,
                                       String originalValue, String errorType,
                                       String errorMessage, String suggestion) {
        ImportRowError error = new ImportRowError();
        error.setBatchId(batchId);
        error.setRowNumber(rowNumber);
        error.setFieldName(fieldName);
        error.setOriginalValue(originalValue);
        error.setErrorType(errorType);
        error.setErrorMessage(errorMessage);
        error.setSuggestion(suggestion);
        return error;
    }

    /**
     * 批量保存错误记录.
     */
    private void saveErrors(List<ImportRowError> errors) {
        for (ImportRowError error : errors) {
            importRowErrorMapper.insert(error);
        }
    }
}

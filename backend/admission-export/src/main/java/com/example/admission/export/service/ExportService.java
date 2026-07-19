package com.example.admission.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admission.auth.entity.UserAccount;
import com.example.admission.auth.service.AuthService;
import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.candidate.service.CandidateService;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.catalog.entity.School;
import com.example.admission.catalog.entity.StandardMajor;
import com.example.admission.catalog.mapper.StandardMajorMapper;
import com.example.admission.catalog.service.EnrollmentPlanService;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.common.TraceContext;
import com.example.admission.export.dto.ExportRequest;
import com.example.admission.export.dto.ExportResponse;
import com.example.admission.export.entity.ExportRecord;
import com.example.admission.export.mapper.ExportRecordMapper;
import com.example.admission.recommendation.service.SpecializedModelRecommendationService;
import com.example.admission.volunteer.entity.VolunteerForm;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteer.mapper.VolunteerFormMapper;
import com.example.admission.volunteer.mapper.VolunteerItemMapper;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;
import com.example.admission.volunteercheck.entity.VolunteerCheckRun;
import com.example.admission.volunteercheck.mapper.VolunteerCheckIssueMapper;
import com.example.admission.volunteercheck.mapper.VolunteerCheckRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Excel 导出服务.
 * 使用 Apache POI XSSFWorkbook 在内存中生成三 Sheet Excel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final VolunteerFormMapper volunteerFormMapper;
    private final VolunteerItemMapper volunteerItemMapper;
    private final ExportRecordMapper exportRecordMapper;
    private final VolunteerCheckRunMapper checkRunMapper;
    private final VolunteerCheckIssueMapper checkIssueMapper;
    private final AuthService authService;
    private final CandidateService candidateService;
    private final EnrollmentPlanService enrollmentPlanService;
    private final StandardMajorMapper standardMajorMapper;
    private final SpecializedModelRecommendationService specializedModelRecommendationService;

    @Value("${app.export.base-dir:data/export}")
    private String exportBaseDir;

    /** 公式注入风险字符 */
    private static final Set<Character> FORMULA_CHARS = Set.of('=', '+', '-', '@');

    /**
     * 导出志愿表为 Excel.
     *
     * @param formId 志愿表 ID            志愿表ID
     * @param req 请求对象uest HTTP 请求           导出请求（含确认标志）
     * @return 导出响应
     */
    @Transactional
    public ExportResponse export(Long formId, ExportRequest request) {
        UserAccount user = authService.checkLogin();
        Long userId = user.getId();

        // 1. 获取并校验志愿表
        VolunteerForm form = volunteerFormMapper.selectById(formId);
        if (form == null || !form.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_NOT_FOUND);
        }

        // 2. 获取志愿项
        List<VolunteerItem> items = volunteerItemMapper.selectList(
                new LambdaQueryWrapper<VolunteerItem>()
                        .eq(VolunteerItem::getFormId, formId)
                        .orderByAsc(VolunteerItem::getSortOrder)
        );

        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.EXPORT_DATA_EMPTY);
        }

        // 3. 检查是否有错误级检查结果
        boolean hasErrors = hasErrorCheckResults(formId);
        if (hasErrors && !request.isConfirmWithErrors()) {
            throw new BusinessException(ErrorCode.EXPORT_CONFIRM_REQUIRED);
        }

        // 4. 获取考生档案 + 计划详情 + 检查结果
        CandidateProfile profile = candidateService.getProfile(form.getYear());
        List<Long> planIds = items.stream().map(VolunteerItem::getPlanId).filter(Objects::nonNull).distinct().toList();
        Map<Long, EnrollmentPlan> planMap = enrollmentPlanService.listPlansByIds(planIds).stream()
                .collect(Collectors.toMap(EnrollmentPlan::getId, p -> p, (a, b) -> a));
        Map<Long, SpecializedModelRecommendationService.HistoricalRanks> modelRankMap =
                specializedModelRecommendationService.findHistoricalRanks(new HashSet<>(planIds));
        List<Long> schoolIds = Stream.concat(
                        items.stream().map(VolunteerItem::getSchoolId),
                        planMap.values().stream().map(EnrollmentPlan::getSchoolId))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, School> schoolMap = enrollmentPlanService.listSchoolsByIds(schoolIds).stream()
                .collect(Collectors.toMap(School::getId, s -> s, (a, b) -> a));
        Map<String, StandardMajor> standardMajorMap = loadStandardMajorMap(planMap.values());

        // 5. 生成 Excel
        String fileName = generateFileName(form.getName());
        Path dirPath = Paths.get(exportBaseDir, String.valueOf(formId));
        String filePath;
        try {
            Files.createDirectories(dirPath);
            filePath = dirPath.resolve(fileName).toString();
            generateExcel(filePath, form, items, profile, planMap, schoolMap, standardMajorMap, modelRankMap);
            generateHtml(toHtmlPath(filePath), form, items, profile, planMap, schoolMap,
                    standardMajorMap, modelRankMap);
        } catch (IOException e) {
            log.error("Failed to generate Excel: formId={}", formId, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED, "生成Excel文件失败: " + e.getMessage());
        }

        // 6. 记录导出记录
        File file = new File(filePath);
        ExportRecord record = new ExportRecord();
        record.setUserId(userId);
        record.setFormId(formId);
        record.setFileName(fileName);
        record.setFilePath(filePath);
        record.setFileSize(file.length());
        record.setConfirmedWithErrors(request.isConfirmWithErrors());
        record.setTraceId(TraceContext.getTraceId());
        record.setCreatedAt(LocalDateTime.now());
        exportRecordMapper.insert(record);

        log.info("Export completed: formId={}, recordId={}, fileName={}", formId, record.getId(), fileName);

        return ExportResponse.builder()
                .fileUrl("/api/v1/exports/" + record.getId())
                .fileName(fileName)
                .htmlFileUrl("/api/v1/exports/" + record.getId() + "/html")
                .htmlFileName(toHtmlFileName(fileName))
                .exportRecordId(record.getId())
                .exportedAt(record.getCreatedAt())
                .build();
    }

    /**
     * 获取导出记录列表.
     */
    public List<ExportResponse> listExports(Long formId) {
        Long userId = authService.checkLogin().getId();
        VolunteerForm form = volunteerFormMapper.selectById(formId);
        if (form == null || !form.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VOLUNTEER_FORM_NOT_FOUND);
        }

        List<ExportRecord> records = exportRecordMapper.selectList(
                new LambdaQueryWrapper<ExportRecord>()
                        .eq(ExportRecord::getFormId, formId)
                        .orderByDesc(ExportRecord::getCreatedAt)
        );

        return records.stream()
                .map(r -> ExportResponse.builder()
                        .fileUrl("/api/v1/exports/" + r.getId())
                        .fileName(r.getFileName())
                        .htmlFileUrl("/api/v1/exports/" + r.getId() + "/html")
                        .htmlFileName(toHtmlFileName(r.getFileName()))
                        .exportRecordId(r.getId())
                        .exportedAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取导出文件.
     */
    public ExportRecord getExportRecord(Long exportRecordId) {
        ExportRecord record = exportRecordMapper.selectById(exportRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.EXPORT_FILE_NOT_FOUND);
        }
        Long userId = authService.checkLogin().getId();
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return record;
    }

    public File getHtmlFile(ExportRecord record) {
        return new File(toHtmlPath(record.getFilePath()));
    }

    // ==================== Excel 生成 ====================

    private void generateExcel(String filePath, VolunteerForm form, List<VolunteerItem> items,
                                CandidateProfile profile, Map<Long, EnrollmentPlan> planMap,
                                Map<Long, School> schoolMap,
                                Map<String, StandardMajor> standardMajorMap,
                                Map<Long, SpecializedModelRecommendationService.HistoricalRanks> modelRankMap)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Sheet 1: 考生信息
            createCandidateSheet(workbook, form, profile);

            // Sheet 2: 志愿表
            createVolunteerSheet(workbook, form, items, planMap, schoolMap, standardMajorMap, modelRankMap);

            // Sheet 3: 检查结果
            createCheckSheet(workbook, form.getId(), planMap);

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }
    }

    private void createCandidateSheet(XSSFWorkbook workbook, VolunteerForm form, CandidateProfile profile) {
        Sheet sheet = workbook.createSheet("考生信息");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        int rowIdx = 0;
        // 标题行
        Row headerRow = sheet.createRow(rowIdx++);
        createCell(headerRow, 0, "项目", headerStyle);
        createCell(headerRow, 1, "内容", headerStyle);

        String[][] infoRows = {
                {"高考年份", form.getYear() != null ? String.valueOf(form.getYear()) : ""},
                {"考生分数", profile != null && profile.getScore() != null ? String.valueOf(profile.getScore()) : ""},
                {"省排名位次", profile != null && profile.getRank() != null ? String.valueOf(profile.getRank()) : ""},
                {"选考科目", profile != null && profile.getSubjects() != null ? String.join("、", profile.getSubjects()) : ""},
                {"意向地域", profile != null && profile.getPreferredRegions() != null ? String.join("、", profile.getPreferredRegions()) : ""},
                {"意向专业", profile != null && profile.getPreferredMajors() != null ? String.join("、", profile.getPreferredMajors()) : ""},
                {"排除专业", profile != null && profile.getExcludedMajors() != null ? String.join("、", profile.getExcludedMajors()) : ""},
                {"学费上限", profile != null && profile.getTuitionMax() != null ? profile.getTuitionMax() + " 元/年" : ""},
                {"导出时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))},
                {"数据版本", "v1.0"},
                {"模型版本", "v1.0"},
                {"风险声明", "本志愿表仅供参考，不构成任何录取承诺。请以山东省教育招生考试院官方信息为准。"},
        };

        for (String[] row : infoRows) {
            Row r = sheet.createRow(rowIdx++);
            createCell(r, 0, row[0], dataStyle);
            createCell(r, 1, row[1], dataStyle);
        }

        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 60 * 256);
    }

    private void createVolunteerSheet(XSSFWorkbook workbook, VolunteerForm form, List<VolunteerItem> items,
                                       Map<Long, EnrollmentPlan> planMap,
                                       Map<Long, School> schoolMap,
                                       Map<String, StandardMajor> standardMajorMap,
                                       Map<Long, SpecializedModelRecommendationService.HistoricalRanks> modelRankMap) {
        Sheet sheet = workbook.createSheet("志愿表");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        int currentYear = form.getYear() != null ? form.getYear() : LocalDateTime.now().getYear();
        String[] headers = {"序号", "层级（冲稳保）", "院校代号", "院校", "专业代码",
                "专业", "办学性质", "选科要求", "计划人数",
                (currentYear - 1) + "年录取人数", (currentYear - 2) + "年录取人数",
                (currentYear - 3) + "年录取人数",
                "学费", "专业类",
                (currentYear - 1) + "年最低位次", (currentYear - 2) + "年最低位次",
                (currentYear - 3) + "年最低位次"};

        int rowIdx = 0;
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        Row titleRow = sheet.createRow(rowIdx++);
        titleRow.setHeightInPoints(34);
        createCell(titleRow, 0, text(form.getName(), "高考志愿方案"), titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));
        Row subtitleRow = sheet.createRow(rowIdx++);
        subtitleRow.setHeightInPoints(25);
        createCell(subtitleRow, 0,
                String.format("%d 年 · 共 %d 个志愿 · 导出于 %s", currentYear, items.size(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))), subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.length - 1));
        rowIdx++;
        int headerRowIndex = rowIdx;
        Row headerRow = sheet.createRow(rowIdx++);
        headerRow.setHeightInPoints(28);
        for (int i = 0; i < headers.length; i++) {
            createCell(headerRow, i, headers[i], headerStyle);
        }

        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            VolunteerItem item = items.get(itemIndex);
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(25);
            EnrollmentPlan plan = planMap.get(item.getPlanId());
            CellStyle rowStyle = dataStyle;
            int col = 0;

            createCell(row, col++, String.valueOf(itemIndex + 1), rowStyle);
            createCell(row, col++, text(item.getLabel()), rowStyle);
            createCell(row, col++, text(item.getSchoolCode(), plan != null ? plan.getSchoolCode() : null), rowStyle);
            createCell(row, col++, text(item.getSchoolName(), plan != null ? plan.getSchoolName() : null), rowStyle);
            SpecializedModelRecommendationService.HistoricalRanks modelHistory = modelRankMap.get(item.getPlanId());
            createCell(row, col++, resolveMajorCode(item, plan, modelHistory), rowStyle);
            createCell(row, col++, text(item.getMajorName(), plan != null ? plan.getMajorName() : null), rowStyle);
            createCell(row, col++, resolveSchoolType(item, schoolMap), rowStyle);
            createCell(row, col++, text(item.getSubjectRequirementText(),
                    plan != null ? plan.getSubjectRequirementText() : null, "不限"), rowStyle);
            createCell(row, col++, formatPlanCount(item.getPlanCount(),
                    plan != null ? plan.getPlanCount() : null), rowStyle);
            boolean newPlan = "NEW".equalsIgnoreCase(text(item.getPlanStatus(),
                    plan != null ? plan.getPlanStatus() : null));
            HistoricalPlanCountValues historicalPlanCounts = resolveHistoricalPlanCounts(modelHistory);
            createCell(row, col++, formatHistoricalPlanCount(historicalPlanCounts.lastYear(), newPlan), rowStyle);
            createCell(row, col++, formatHistoricalPlanCount(historicalPlanCounts.twoYearsAgo(), newPlan), rowStyle);
            createCell(row, col++, formatHistoricalPlanCount(historicalPlanCounts.threeYearsAgo(), newPlan), rowStyle);
            createCell(row, col++, formatTuition(item.getTuition(),
                    plan != null ? plan.getTuition() : null), rowStyle);
            createCell(row, col++, getMajorSubcategory(plan, standardMajorMap), rowStyle);

            HistoricalRankValues ranks = resolveHistoricalRanks(item, plan, modelRankMap);
            createCell(row, col++, formatHistoricalRank(ranks.lastYear(), newPlan), rowStyle);
            createCell(row, col++, formatHistoricalRank(ranks.twoYearsAgo(), newPlan), rowStyle);
            createCell(row, col, formatHistoricalRank(ranks.threeYearsAgo(), newPlan), rowStyle);
        }

        // 设置列宽
        int[] widths = {8, 14, 14, 25, 14, 30, 12, 20, 12,
                16, 16, 16, 14, 20, 18, 18, 18};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, headerRowIndex + 1);
        sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, rowIdx - 1, 0, headers.length - 1));
        sheet.setZoom(85);
        sheet.setDisplayGridlines(false);
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
    }

    static String formatHistoricalRank(Integer rank, boolean newPlan) {
        if (newPlan) {
            return "新增";
        }
        return rank != null ? String.valueOf(rank) : "-";
    }

    static String formatHistoricalPlanCount(Integer count, boolean newPlan) {
        if (newPlan) {
            return "新增";
        }
        return count != null ? count + "人" : "-";
    }

    private String resolveMajorCode(
            VolunteerItem item,
            EnrollmentPlan plan,
            SpecializedModelRecommendationService.HistoricalRanks modelHistory) {
        return text(item.getMajorCode(),
                plan != null ? plan.getMajorCode() : null,
                modelHistory != null ? modelHistory.majorCode() : null);
    }

    private HistoricalPlanCountValues resolveHistoricalPlanCounts(
            SpecializedModelRecommendationService.HistoricalRanks modelHistory) {
        return new HistoricalPlanCountValues(
                modelHistory != null ? modelHistory.lastYearPlanCount() : null,
                modelHistory != null ? modelHistory.twoYearPlanCount() : null,
                modelHistory != null ? modelHistory.threeYearPlanCount() : null);
    }

    private record HistoricalPlanCountValues(
            Integer lastYear,
            Integer twoYearsAgo,
            Integer threeYearsAgo
    ) {
    }

    private HistoricalRankValues resolveHistoricalRanks(
            VolunteerItem item,
            EnrollmentPlan plan,
            Map<Long, SpecializedModelRecommendationService.HistoricalRanks> modelRankMap) {
        SpecializedModelRecommendationService.HistoricalRanks modelRanks =
                modelRankMap.get(item.getPlanId());
        return new HistoricalRankValues(
                firstNonNull(item.getLastYearMinRank(),
                        plan != null ? plan.getLastYearMinRank() : null,
                        modelRanks != null ? modelRanks.lastYearMinRank() : null),
                firstNonNull(item.getTwoYearMinRank(),
                        plan != null ? plan.getTwoYearMinRank() : null,
                        modelRanks != null ? modelRanks.twoYearMinRank() : null),
                firstNonNull(item.getThreeYearMinRank(),
                        plan != null ? plan.getThreeYearMinRank() : null,
                        modelRanks != null ? modelRanks.threeYearMinRank() : null));
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private record HistoricalRankValues(
            Integer lastYear,
            Integer twoYearsAgo,
            Integer threeYearsAgo
    ) {
    }

    void generateHtml(String filePath, VolunteerForm form, List<VolunteerItem> items,
                      CandidateProfile profile, Map<Long, EnrollmentPlan> planMap,
                      Map<Long, School> schoolMap,
                      Map<String, StandardMajor> standardMajorMap,
                      Map<Long, SpecializedModelRecommendationService.HistoricalRanks> modelRankMap)
            throws IOException {
        int year = form.getYear() != null ? form.getYear() : LocalDateTime.now().getYear();
        long reachCount = items.stream().filter(i -> "冲".equals(i.getLabel())).count();
        long matchCount = items.stream().filter(i -> "稳".equals(i.getLabel())).count();
        long safeCount = items.stream().filter(i -> "保".equals(i.getLabel())).count();
        StringBuilder rows = new StringBuilder();

        for (int index = 0; index < items.size(); index++) {
            VolunteerItem item = items.get(index);
            EnrollmentPlan plan = planMap.get(item.getPlanId());
            String schoolName = text(item.getSchoolName(), plan != null ? plan.getSchoolName() : null);
            String majorName = text(item.getMajorName(), plan != null ? plan.getMajorName() : null);
            String schoolType = resolveSchoolType(item, schoolMap);
            String label = text(item.getLabel(), "未分类");
            boolean newPlan = "NEW".equalsIgnoreCase(text(item.getPlanStatus(),
                    plan != null ? plan.getPlanStatus() : null));
            HistoricalRankValues ranks = resolveHistoricalRanks(item, plan, modelRankMap);
            SpecializedModelRecommendationService.HistoricalRanks modelHistory = modelRankMap.get(item.getPlanId());
            HistoricalPlanCountValues historicalPlanCounts = resolveHistoricalPlanCounts(modelHistory);
            String majorCode = resolveMajorCode(item, plan, modelHistory);
            String searchText = String.join(" ", schoolName, majorName, text(item.getSchoolCode()),
                    majorCode, schoolType);

            rows.append("<tr data-label=\"").append(htmlEscape(label)).append("\" data-type=\"")
                    .append(htmlEscape(schoolType)).append("\" data-search=\"")
                    .append(htmlEscape(searchText.toLowerCase(Locale.ROOT))).append("\">")
                    .append("<td class=\"seq\">").append(index + 1).append("</td>")
                    .append("<td><span class=\"tag ").append(labelClass(label)).append("\">")
                    .append(htmlEscape(label)).append("</span></td>")
                    .append("<td><div class=\"school\">").append(htmlEscape(schoolName)).append("</div><small>")
                    .append(htmlEscape(text(item.getSchoolCode(), plan != null ? plan.getSchoolCode() : null))).append("</small></td>")
                    .append("<td><div class=\"major\">").append(htmlEscape(majorName)).append("</div><small>")
                    .append(htmlEscape(majorCode)).append("</small></td>")
                    .append("<td><span class=\"nature ").append(natureClass(schoolType)).append("\">")
                    .append(htmlEscape(schoolType)).append("</span></td>")
                    .append("<td>").append(htmlEscape(text(item.getSubjectRequirementText(),
                            plan != null ? plan.getSubjectRequirementText() : null, "不限"))).append("</td>")
                    .append("<td>").append(htmlEscape(formatPlanCount(item.getPlanCount(),
                            plan != null ? plan.getPlanCount() : null))).append("</td>")
                    .append("<td>").append(htmlEscape(formatHistoricalPlanCount(historicalPlanCounts.lastYear(), newPlan))).append("</td>")
                    .append("<td>").append(htmlEscape(formatHistoricalPlanCount(historicalPlanCounts.twoYearsAgo(), newPlan))).append("</td>")
                    .append("<td>").append(htmlEscape(formatHistoricalPlanCount(historicalPlanCounts.threeYearsAgo(), newPlan))).append("</td>")
                    .append("<td>").append(htmlEscape(formatTuition(item.getTuition(),
                            plan != null ? plan.getTuition() : null))).append("</td>")
                    .append("<td>").append(htmlEscape(getMajorSubcategory(plan, standardMajorMap))).append("</td>")
                    .append("<td class=\"rank\">").append(htmlEscape(formatHistoricalRank(ranks.lastYear(), newPlan))).append("</td>")
                    .append("<td class=\"rank\">").append(htmlEscape(formatHistoricalRank(ranks.twoYearsAgo(), newPlan))).append("</td>")
                    .append("<td class=\"rank\">").append(htmlEscape(formatHistoricalRank(ranks.threeYearsAgo(), newPlan))).append("</td></tr>");
        }

        String template = """
                <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>__TITLE__</title><style>
                :root{--navy:#102a43;--blue:#1f5f8b;--teal:#0f8b8d;--paper:#fff;--ink:#243b53;--muted:#627d98;--line:#d9e2ec;--bg:#f3f7fa}
                *{box-sizing:border-box}body{margin:0;background:linear-gradient(145deg,#eef5f8 0%,#f8fafc 45%,#edf7f6 100%);color:var(--ink);font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
                .page{max-width:1540px;margin:0 auto;padding:34px 28px 54px}.hero{position:relative;overflow:hidden;background:linear-gradient(125deg,#102a43,#174f70 58%,#0f8b8d);color:white;border-radius:24px;padding:34px 38px;box-shadow:0 18px 45px #183b5625}
                .hero:after{content:"";position:absolute;width:340px;height:340px;border:70px solid #ffffff10;border-radius:50%;right:-110px;top:-170px}.eyebrow{letter-spacing:.18em;opacity:.72;font-size:12px}.hero h1{font-size:30px;line-height:1.25;margin:9px 0 12px}.meta{display:flex;gap:22px;flex-wrap:wrap;color:#d9edf4}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:18px 0}.stat{background:#ffffffd9;backdrop-filter:blur(10px);border:1px solid #fff;border-radius:16px;padding:18px 20px;box-shadow:0 8px 24px #183b5610}.stat b{display:block;font-size:25px;color:var(--navy)}.stat span{color:var(--muted)}
                .toolbar{position:sticky;top:0;z-index:5;display:grid;grid-template-columns:minmax(260px,1fr) 180px 180px auto;gap:12px;background:#f8fafcee;padding:15px 0;backdrop-filter:blur(12px)}input,select{width:100%;height:44px;border:1px solid var(--line);border-radius:12px;background:white;padding:0 14px;color:var(--ink);outline:none}input:focus,select:focus{border-color:var(--teal);box-shadow:0 0 0 3px #0f8b8d18}.result{align-self:center;text-align:right;color:var(--muted);white-space:nowrap}
                .table-card{background:var(--paper);border:1px solid #d9e2ecaa;border-radius:18px;overflow:auto;box-shadow:0 12px 35px #183b5610}table{border-collapse:separate;border-spacing:0;width:100%;min-width:1580px}thead th{position:sticky;top:0;z-index:2;background:var(--navy);color:white;text-align:left;padding:13px 12px;font-size:13px;white-space:nowrap}tbody td{padding:13px 12px;border-bottom:1px solid #edf2f7;vertical-align:middle;background:#fff}tbody tr:hover td{background:#edf8f7}.seq,.rank{text-align:center;font-variant-numeric:tabular-nums}.school,.major{font-weight:650;color:#183b56}small{color:var(--muted)}.tag,.nature{display:inline-flex;padding:3px 10px;border-radius:999px;font-weight:650;white-space:nowrap}.reach{background:#fff0f0;color:#c53030}.match{background:#fff8df;color:#9c6500}.safe{background:#e8f7f1;color:#13795b}.neutral{background:#edf2f7;color:#526779}.public{background:#e7f2fb;color:#1f5f8b}.private{background:#f5eefa;color:#7b4a99}.empty{padding:55px;text-align:center;color:var(--muted);display:none}.foot{text-align:center;color:var(--muted);margin-top:22px;font-size:12px}
                @media(max-width:800px){.page{padding:18px 12px}.hero{padding:26px 22px;border-radius:18px}.hero h1{font-size:24px}.stats{grid-template-columns:repeat(2,1fr)}.toolbar{grid-template-columns:1fr 1fr}.toolbar input{grid-column:1/-1}.result{text-align:left}.stat{padding:14px}}
                @media print{body{background:white}.page{max-width:none;padding:0}.hero{box-shadow:none}.toolbar{display:none}.table-card{box-shadow:none;border:0}thead th{position:static}.foot{margin-top:10px}}
                </style></head><body><main class="page"><section class="hero"><div class="eyebrow">GAOKAO VOLUNTEER PORTFOLIO</div><h1>__TITLE__</h1><div class="meta"><span>__YEAR__ 年高考</span><span>考生分数：__SCORE__</span><span>省排名：__RANK__</span><span>导出时间：__TIME__</span></div></section>
                <section class="stats"><div class="stat"><b>__TOTAL__</b><span>志愿总数</span></div><div class="stat"><b>__REACH__</b><span>冲刺志愿</span></div><div class="stat"><b>__MATCH__</b><span>稳妥志愿</span></div><div class="stat"><b>__SAFE__</b><span>保底志愿</span></div></section>
                <section class="toolbar"><input id="search" type="search" placeholder="搜索院校、专业、代码…"><select id="type"><option value="">全部办学性质</option><option>公办</option><option>民办</option><option>独立学院</option><option>中外合作办学</option></select><select id="label"><option value="">全部冲稳保</option><option>冲</option><option>稳</option><option>保</option><option>未分类</option></select><div class="result">显示 <b id="visible">__TOTAL__</b> / __TOTAL__ 条</div></section>
                <section class="table-card"><table><thead><tr><th>序号</th><th>冲稳保</th><th>院校</th><th>专业</th><th>办学性质</th><th>选科要求</th><th>计划</th><th>__Y1__ 录取人数</th><th>__Y2__ 录取人数</th><th>__Y3__ 录取人数</th><th>学费/年</th><th>专业类</th><th>__Y1__ 位次</th><th>__Y2__ 位次</th><th>__Y3__ 位次</th></tr></thead><tbody id="rows">__ROWS__</tbody></table><div id="empty" class="empty">没有符合当前筛选条件的志愿</div></section>
                <div class="foot">本志愿方案仅供参考，不构成录取承诺。请以山东省教育招生考试院官方信息为准。</div></main>
                <script>const q=document.querySelector('#search'),t=document.querySelector('#type'),l=document.querySelector('#label'),rs=[...document.querySelectorAll('#rows tr')],v=document.querySelector('#visible'),e=document.querySelector('#empty');function filter(){const s=q.value.trim().toLowerCase();let n=0;rs.forEach(r=>{const ok=(!s||r.dataset.search.includes(s))&&(!t.value||r.dataset.type===t.value)&&(!l.value||r.dataset.label===l.value);r.hidden=!ok;if(ok)n++});v.textContent=n;e.style.display=n?'none':'block'}[q,t,l].forEach(x=>x.addEventListener('input',filter));</script></body></html>
                """;
        String html = template
                .replace("__TITLE__", htmlEscape(text(form.getName(), "高考志愿方案")))
                .replace("__YEAR__", String.valueOf(year))
                .replace("__SCORE__", profile != null && profile.getScore() != null ? htmlEscape(String.valueOf(profile.getScore())) : "-")
                .replace("__RANK__", profile != null && profile.getRank() != null ? htmlEscape(String.valueOf(profile.getRank())) : "-")
                .replace("__TIME__", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .replace("__TOTAL__", String.valueOf(items.size()))
                .replace("__REACH__", String.valueOf(reachCount))
                .replace("__MATCH__", String.valueOf(matchCount))
                .replace("__SAFE__", String.valueOf(safeCount))
                .replace("__Y1__", String.valueOf(year - 1))
                .replace("__Y2__", String.valueOf(year - 2))
                .replace("__Y3__", String.valueOf(year - 3))
                .replace("__ROWS__", rows.toString());
        Files.writeString(Paths.get(filePath), html, StandardCharsets.UTF_8);
    }

    private String resolveSchoolType(VolunteerItem item, Map<Long, School> schoolMap) {
        School school = item.getSchoolId() != null ? schoolMap.get(item.getSchoolId()) : null;
        return formatSchoolType(text(item.getSchoolType(), school != null ? school.getSchoolType() : null));
    }

    static String formatSchoolType(String value) {
        if (value == null || value.isBlank()) return "未知";
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PUBLIC" -> "公办";
            case "PRIVATE" -> "民办";
            default -> value.trim();
        };
    }

    static String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String labelClass(String label) {
        return switch (label) {
            case "冲" -> "reach";
            case "稳" -> "match";
            case "保" -> "safe";
            default -> "neutral";
        };
    }

    private String natureClass(String schoolType) {
        if (schoolType.contains("公办")) return "public";
        if (schoolType.contains("民办")) return "private";
        return "neutral";
    }

    private void createCheckSheet(XSSFWorkbook workbook, Long formId, Map<Long, EnrollmentPlan> planMap) {
        Sheet sheet = workbook.createSheet("检查结果");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        String[] headers = {"问题级别", "志愿序号", "学校", "专业", "问题类型", "问题说明", "建议"};

        int rowIdx = 0;
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            createCell(headerRow, i, headers[i], headerStyle);
        }

        // 获取最新检查结果
        VolunteerCheckRun latestRun = checkRunMapper.selectOne(
                new LambdaQueryWrapper<VolunteerCheckRun>()
                        .eq(VolunteerCheckRun::getFormId, formId)
                        .orderByDesc(VolunteerCheckRun::getCheckTime)
                        .last("LIMIT 1")
        );

        if (latestRun != null) {
            List<VolunteerCheckIssue> issues = checkIssueMapper.selectList(
                    new LambdaQueryWrapper<VolunteerCheckIssue>()
                            .eq(VolunteerCheckIssue::getCheckRunId, latestRun.getId())
                            .orderByAsc(VolunteerCheckIssue::getSortOrder)
            );

            for (VolunteerCheckIssue issue : issues) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;

                String levelName = issue.getLevel();
                EnrollmentPlan plan = issue.getPlanId() != null ? planMap.get(issue.getPlanId()) : null;

                createCell(row, col++, levelName, dataStyle);
                createCell(row, col++, issue.getSortOrder() != null ? String.valueOf(issue.getSortOrder()) : "-", dataStyle);
                createCell(row, col++, plan != null ? plan.getSchoolName() : "", dataStyle);
                createCell(row, col++, plan != null ? plan.getMajorName() : "", dataStyle);
                createCell(row, col++, issue.getRuleCode(), dataStyle);
                createCell(row, col++, issue.getMessage() != null ? issue.getMessage() : "", dataStyle);
                createCell(row, col, issue.getSuggestion() != null ? issue.getSuggestion() : "", dataStyle);
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
        sheet.setColumnWidth(5, 40 * 256);
        sheet.setColumnWidth(6, 40 * 256);
    }

    private Map<String, StandardMajor> loadStandardMajorMap(Collection<EnrollmentPlan> plans) {
        List<String> codes = plans.stream()
                .map(EnrollmentPlan::getStandardMajorCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Collections.emptyMap();
        }
        return standardMajorMapper.selectList(
                        new LambdaQueryWrapper<StandardMajor>().in(StandardMajor::getMajorCode, codes)
                ).stream()
                .collect(Collectors.toMap(StandardMajor::getMajorCode, major -> major, (a, b) -> a));
    }

    private String getMajorSubcategory(EnrollmentPlan plan, Map<String, StandardMajor> standardMajorMap) {
        if (plan == null) {
            return "";
        }
        StandardMajor standardMajor = standardMajorMap.get(plan.getStandardMajorCode());
        return text(
                standardMajor != null ? standardMajor.getSubcategoryName() : null,
                plan.getMajorCategory()
        );
    }

    private String formatPlanCount(Integer itemPlanCount, Integer planPlanCount) {
        Integer count = itemPlanCount != null ? itemPlanCount : planPlanCount;
        return count != null ? count + "人" : "";
    }

    private String formatTuition(BigDecimal itemTuition, BigDecimal planTuition) {
        BigDecimal tuition = itemTuition != null ? itemTuition : planTuition;
        return tuition != null ? tuition.stripTrailingZeros().toPlainString() : "";
    }

    private String text(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    // ==================== Cell 工具方法 ====================

    /**
     * 创建 Cell 并写入安全文本（公式注入防护）.
     */
    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value != null && !value.isEmpty()) {
            cell.setCellValue(safeCellText(value));
        }
    }

    static String safeCellText(String value) {
        if (value == null || value.isEmpty()) return value;
        // 公式注入防护；单独的占位符“-”不是公式，不应显示多余单引号。
        return value.length() > 1 && FORMULA_CHARS.contains(value.charAt(0))
                ? "'" + value
                : value;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBottomBorderColor(IndexedColors.TEAL.getIndex());
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 20);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setIndention((short) 1);
        return style;
    }

    private CellStyle createSubtitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setIndention((short) 1);
        return style;
    }

    private CellStyle createDataStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    // ==================== 内部方法 ====================

    private boolean hasErrorCheckResults(Long formId) {
        VolunteerCheckRun latestRun = checkRunMapper.selectOne(
                new LambdaQueryWrapper<VolunteerCheckRun>()
                        .eq(VolunteerCheckRun::getFormId, formId)
                        .orderByDesc(VolunteerCheckRun::getCheckTime)
                        .last("LIMIT 1")
        );
        return latestRun != null && latestRun.getErrorCount() != null && latestRun.getErrorCount() > 0;
    }

    private String generateFileName(String formName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeName = formName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return "山东高考志愿表_" + safeName + "_" + timestamp + ".xlsx";
    }

    private String toHtmlPath(String excelPath) {
        return excelPath.endsWith(".xlsx")
                ? excelPath.substring(0, excelPath.length() - 5) + ".html"
                : excelPath + ".html";
    }

    private String toHtmlFileName(String excelFileName) {
        return excelFileName.endsWith(".xlsx")
                ? excelFileName.substring(0, excelFileName.length() - 5) + ".html"
                : excelFileName + ".html";
    }
}

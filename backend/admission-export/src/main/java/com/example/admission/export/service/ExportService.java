package com.example.admission.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admission.auth.entity.UserAccount;
import com.example.admission.auth.service.AuthService;
import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.candidate.service.CandidateService;
import com.example.admission.catalog.entity.EnrollmentPlan;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
        Map<String, StandardMajor> standardMajorMap = loadStandardMajorMap(planMap.values());

        // 5. 生成 Excel
        String fileName = generateFileName(form.getName());
        Path dirPath = Paths.get(exportBaseDir, String.valueOf(formId));
        String filePath;
        try {
            Files.createDirectories(dirPath);
            filePath = dirPath.resolve(fileName).toString();
            generateExcel(filePath, form, items, profile, planMap, standardMajorMap);
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

    // ==================== Excel 生成 ====================

    private void generateExcel(String filePath, VolunteerForm form, List<VolunteerItem> items,
                                CandidateProfile profile, Map<Long, EnrollmentPlan> planMap,
                                Map<String, StandardMajor> standardMajorMap) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Sheet 1: 考生信息
            createCandidateSheet(workbook, form, profile);

            // Sheet 2: 志愿表
            createVolunteerSheet(workbook, items, planMap, standardMajorMap);

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

    private void createVolunteerSheet(XSSFWorkbook workbook, List<VolunteerItem> items,
                                       Map<Long, EnrollmentPlan> planMap,
                                       Map<String, StandardMajor> standardMajorMap) {
        Sheet sheet = workbook.createSheet("志愿表");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        String[] headers = {"序号", "层级（冲稳保）", "院校代号", "院校", "专业代码",
                "专业", "选科要求", "计划人数", "学费", "专业类"};

        int rowIdx = 0;
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            createCell(headerRow, i, headers[i], headerStyle);
        }

        for (VolunteerItem item : items) {
            Row row = sheet.createRow(rowIdx++);
            EnrollmentPlan plan = planMap.get(item.getPlanId());
            int col = 0;

            createCell(row, col++, String.valueOf(rowIdx - 1), dataStyle);
            createCell(row, col++, text(item.getLabel()), dataStyle);
            createCell(row, col++, text(item.getSchoolCode(), plan != null ? plan.getSchoolCode() : null), dataStyle);
            createCell(row, col++, text(item.getSchoolName(), plan != null ? plan.getSchoolName() : null), dataStyle);
            createCell(row, col++, text(item.getMajorCode(), plan != null ? plan.getMajorCode() : null), dataStyle);
            createCell(row, col++, text(item.getMajorName(), plan != null ? plan.getMajorName() : null), dataStyle);
            createCell(row, col++, text(item.getSubjectRequirementText(),
                    plan != null ? plan.getSubjectRequirementText() : null, "不限"), dataStyle);
            createCell(row, col++, formatPlanCount(item.getPlanCount(),
                    plan != null ? plan.getPlanCount() : null), dataStyle);
            createCell(row, col++, formatTuition(item.getTuition(),
                    plan != null ? plan.getTuition() : null), dataStyle);
            createCell(row, col, getMajorSubcategory(plan, standardMajorMap), dataStyle);
        }

        // 设置列宽
        int[] widths = {10, 16, 16, 26, 16, 30, 20, 14, 14, 22};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
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
            // 公式注入防护: 以 = + - @ 开头的文本前加单引号
            char firstChar = value.charAt(0);
            if (FORMULA_CHARS.contains(firstChar)) {
                value = "'" + value;
            }
            cell.setCellValue(value);
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
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
}

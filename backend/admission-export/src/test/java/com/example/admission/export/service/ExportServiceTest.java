package com.example.admission.export.service;

import com.example.admission.volunteer.entity.VolunteerForm;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.recommendation.service.SpecializedModelRecommendationService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void historicalRankUsesActualRankWhenAvailable() {
        assertEquals("123456", ExportService.formatHistoricalRank(123456, false));
    }

    @Test
    void historicalRankUsesDashWhenOrdinaryPlanHasNoData() {
        assertEquals("-", ExportService.formatHistoricalRank(null, false));
    }

    @Test
    void historicalRankUsesNewForEveryNewPlanColumn() {
        assertEquals("新增", ExportService.formatHistoricalRank(123456, true));
        assertEquals("新增", ExportService.formatHistoricalRank(null, true));
    }

    @Test
    void historicalPlanCountUsesPeopleUnitAndNewFallback() {
        assertEquals("40人", ExportService.formatHistoricalPlanCount(40, false));
        assertEquals("-", ExportService.formatHistoricalPlanCount(null, false));
        assertEquals("新增", ExportService.formatHistoricalPlanCount(null, true));
    }

    @Test
    void volunteerSheetExportsMajorCodeAndThreeHistoricalPlanCounts() throws Exception {
        VolunteerForm form = new VolunteerForm();
        form.setName("测试志愿表");
        form.setYear(2026);
        VolunteerItem item = new VolunteerItem();
        item.setPlanId(8058871231L);
        item.setSchoolCode("E666");
        item.setSchoolName("测试职业学院");
        item.setMajorName("智能机器人技术");
        item.setPlanCount(80);

        ExportService service = new ExportService(null, null, null, null, null,
                null, null, null, null, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ReflectionTestUtils.invokeMethod(service, "createVolunteerSheet", workbook, form,
                    List.of(item, item), Map.of(), Map.of(), Map.of(), Map.of(item.getPlanId(),
                            new SpecializedModelRecommendationService.HistoricalRanks(
                                    "34", 520308, 515388, 502374, 60, 55, 31)));

            var sheet = workbook.getSheet("志愿表");
            var header = sheet.getRow(3);
            var data = sheet.getRow(4);
            assertEquals("专业代码", header.getCell(4).getStringCellValue());
            assertEquals("2025年录取人数", header.getCell(9).getStringCellValue());
            assertEquals("2024年录取人数", header.getCell(10).getStringCellValue());
            assertEquals("2023年录取人数", header.getCell(11).getStringCellValue());
            assertEquals("34", data.getCell(4).getStringCellValue());
            assertEquals("60人", data.getCell(9).getStringCellValue());
            assertEquals("55人", data.getCell(10).getStringCellValue());
            assertEquals("31人", data.getCell(11).getStringCellValue());
            assertEquals("2023年最低位次", header.getCell(16).getStringCellValue());
            assertEquals(FillPatternType.NO_FILL, sheet.getRow(4).getCell(0).getCellStyle().getFillPattern());
            assertEquals(FillPatternType.NO_FILL, sheet.getRow(5).getCell(0).getCellStyle().getFillPattern());
        }
    }

    @Test
    void schoolTypeCodesAreRenderedInChinese() {
        assertEquals("公办", ExportService.formatSchoolType("PUBLIC"));
        assertEquals("民办", ExportService.formatSchoolType("private"));
        assertEquals("独立学院", ExportService.formatSchoolType("独立学院"));
        assertEquals("未知", ExportService.formatSchoolType(null));
    }

    @Test
    void htmlContentIsEscaped() {
        assertEquals("&lt;script&gt;&quot;x&quot;&amp;&#39;y&#39;&lt;/script&gt;",
                ExportService.htmlEscape("<script>\"x\"&'y'</script>"));
    }

    @Test
    void dashPlaceholderDoesNotReceiveFormulaEscapePrefix() {
        assertEquals("-", ExportService.safeCellText("-"));
        assertEquals("'-1+2", ExportService.safeCellText("-1+2"));
    }

    @Test
    void interactiveHtmlContainsFiltersAndEscapedVolunteerData() throws Exception {
        VolunteerForm form = new VolunteerForm();
        form.setName("测试<script>");
        form.setYear(2026);
        VolunteerItem item = new VolunteerItem();
        item.setPlanId(1L);
        item.setSchoolName("山东测试大学");
        item.setMajorName("计算机科学与技术");
        item.setSchoolType("PUBLIC");
        item.setLabel("稳");

        ExportService service = new ExportService(null, null, null, null, null,
                null, null, null, null, null);
        Path htmlFile = tempDir.resolve("volunteer.html");
        service.generateHtml(htmlFile.toString(), form, List.of(item), null,
                Map.of(), Map.of(), Map.of(), Map.of(1L,
                        new SpecializedModelRecommendationService.HistoricalRanks(12345, 23456, 34567)));

        String html = Files.readString(htmlFile);
        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(html.contains("id=\"search\"")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(html.contains("id=\"type\"")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(html.contains("山东测试大学")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(html.contains("23456")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(html.contains("34567")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(html.contains("测试&lt;script&gt;")),
                () -> org.junit.jupiter.api.Assertions.assertFalse(html.contains("测试<script>"))
        );
    }
}

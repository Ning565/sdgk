package com.example.admission.dataimport.util;

import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Excel流式读写工具类.
 *
 * <p>使用POI SAX（XSSFWorkbook逐行读取）进行大文件读取，
 * 使用SXSSF进行大文件写入。支持解析合并单元格标题。</p>
 *
 * @author admission-system
 */
public final class ExcelUtil {

    private static final Logger log = LoggerFactory.getLogger(ExcelUtil.class);

    /** 最大行数限制（不含表头） */
    private static final int DEFAULT_MAX_ROWS = 50000;

    private ExcelUtil() {
    }

    /**
     * 流式读取Excel文件，逐行回调处理.
     *
     * <p>每次读取一行数据（Map格式，key=列头名称，value=单元格字符串值），
     * 通过consumer回调给调用方处理。适用于大文件解析场景。</p>
     *
     * @param filePath     Excel文件路径
     * @param headerRowNum 表头所在行号（0-based），默认0
     * @param maxRows      最大读取行数（不含表头），默认50000
     * @param consumer     行处理回调，接收行号和字段值Map
     * @return 读取的总数据行数
     */
    public static int readRows(String filePath, Integer headerRowNum, Integer maxRows,
                                Consumer<RowData> consumer) {
        int headerRow = headerRowNum != null ? headerRowNum : 0;
        int maxDataRows = maxRows != null ? maxRows : DEFAULT_MAX_ROWS;

        File file = new File(filePath);
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_EMPTY, "文件不存在: " + filePath);
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.IMPORT_FORMAT_ERROR, "Excel文件中没有Sheet");
            }

            // 解析表头
            Row headerRowObj = sheet.getRow(headerRow);
            if (headerRowObj == null) {
                throw new BusinessException(ErrorCode.IMPORT_FORMAT_ERROR, "表头行不存在，行号: " + headerRow);
            }

            List<String> headers = new ArrayList<>();
            List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
            for (int col = 0; col < headerRowObj.getLastCellNum(); col++) {
                Cell cell = headerRowObj.getCell(col);
                String headerValue = getCellStringValue(cell, mergedRegions, headerRow, col, sheet);
                headers.add(headerValue != null ? headerValue.trim() : "COL_" + col);
            }

            // 逐行读取数据
            int dataRowCount = 0;
            int lastRowNum = sheet.getLastRowNum();
            for (int rowIdx = headerRow + 1; rowIdx <= lastRowNum && dataRowCount < maxDataRows; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                Map<String, String> rowData = new LinkedHashMap<>();
                for (int col = 0; col < headers.size(); col++) {
                    Cell cell = row.getCell(col);
                    String value = getCellStringValue(cell, Collections.emptyList(), rowIdx, col, sheet);
                    rowData.put(headers.get(col), value != null ? value.trim() : "");
                }

                consumer.accept(new RowData(rowIdx + 1, rowData)); // 行号从1开始（便于用户理解）
                dataRowCount++;
            }

            log.info("Excel read completed: file={}, headerRow={}, dataRows={}", filePath, headerRow, dataRowCount);
            return dataRowCount;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read Excel file: {}", filePath, e);
            throw new BusinessException(ErrorCode.IMPORT_PARSE_ERROR, "Excel文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取第一个Sheet的表头.
     *
     * @param filePath     Excel文件路径
     * @param headerRowNum 表头行号（0-based）
     * @return 表头字段名列表
     */
    public static List<String> readHeaders(String filePath, int headerRowNum) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_EMPTY, "文件不存在: " + filePath);
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(headerRowNum);
            if (headerRow == null) {
                return Collections.emptyList();
            }

            List<String> headers = new ArrayList<>();
            for (int col = 0; col < headerRow.getLastCellNum(); col++) {
                Cell cell = headerRow.getCell(col);
                String value = getCellStringValue(cell, sheet.getMergedRegions(), headerRowNum, col, sheet);
                headers.add(value != null ? value.trim() : "COL_" + col);
            }
            return headers;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read headers from: {}", filePath, e);
            throw new BusinessException(ErrorCode.IMPORT_PARSE_ERROR, "读取表头失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导出错误明细为Excel文件.
     *
     * @param headers     列头
     * @param rows        数据行列表（每行是一个字符串数组）
     * @param outputPath  输出文件路径
     */
    public static void writeErrorExcel(List<String> headers, List<List<String>> rows, String outputPath) {
        File parentDir = new File(outputPath).getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             FileOutputStream fos = new FileOutputStream(outputPath)) {

            Sheet sheet = workbook.createSheet("错误明细");

            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // 写表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 写数据行
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                Row dataRow = sheet.createRow(rowIdx + 1);
                List<String> rowData = rows.get(rowIdx);
                for (int col = 0; col < rowData.size(); col++) {
                    Cell cell = dataRow.createCell(col);
                    cell.setCellValue(rowData.get(col) != null ? rowData.get(col) : "");
                    cell.setCellStyle(dataStyle);
                }
            }

            // 自动调整列宽（近似）
            for (int i = 0; i < headers.size(); i++) {
                sheet.setColumnWidth(i, Math.min(headers.get(i).length() * 256 * 2, 256 * 50));
            }

            workbook.write(fos);
            log.info("Error Excel written to: {}, rows={}", outputPath, rows.size());

        } catch (Exception e) {
            log.error("Failed to write error Excel: {}", outputPath, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED, "导出错误明细失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取单元格的字符串值（处理合并单元格）.
     *
     * @param cell         单元格对象（可为null）
     * @param mergedRegions 合并区域列表
     * @param row          当前行号
     * @param col          当前列号
     * @param sheet        所属Sheet（用于在cell为null时查找合并区域左上角的值）
     */
    private static String getCellStringValue(Cell cell, List<CellRangeAddress> mergedRegions,
                                              int row, int col, Sheet sheet) {
        if (cell == null) {
            // 检查是否属于合并单元格区域
            if (sheet != null) {
                for (CellRangeAddress region : mergedRegions) {
                    if (region.isInRange(row, col)) {
                        // 返回合并区域左上角单元格的值
                        Row firstRow = sheet.getRow(region.getFirstRow());
                        if (firstRow == null) {
                            return null;
                        }
                        Cell topLeftCell = firstRow.getCell(region.getFirstColumn());
                        return getCellStringValue(topLeftCell,
                                Collections.emptyList(), region.getFirstRow(), region.getFirstColumn(), null);
                    }
                }
            }
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                double numVal = cell.getNumericCellValue();
                // 判断是否为整数
                if (numVal == Math.floor(numVal) && !Double.isInfinite(numVal)) {
                    return String.valueOf((long) numVal);
                }
                return String.valueOf(numVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * 判断行是否为空.
     */
    private static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int col = 0; col < row.getLastCellNum(); col++) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !getCellStringValue(cell, Collections.emptyList(), row.getRowNum(), col, null).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 创建表头样式.
     */
    private static CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
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
        return style;
    }

    /**
     * 创建数据行样式.
     */
    private static CellStyle createDataStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ========== 内部类 ==========

    /**
     * 单行数据载体，包含行号和字段值映射.
     */
    public static class RowData {
        private final int rowNumber; // 1-based
        private final Map<String, String> fields;

        public RowData(int rowNumber, Map<String, String> fields) {
            this.rowNumber = rowNumber;
            this.fields = fields;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public Map<String, String> getFields() {
            return fields;
        }

        public String get(String fieldName) {
            return fields.getOrDefault(fieldName, "");
        }

        @Override
        public String toString() {
            return "RowData{row=" + rowNumber + ", fields=" + fields + '}';
        }
    }
}

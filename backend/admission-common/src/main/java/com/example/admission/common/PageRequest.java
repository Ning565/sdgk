package com.example.admission.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 标准分页请求。
 */
public class PageRequest {

    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 200, message = "每页数量最大为200")
    private int size = 20;

    private String sortField;
    private String sortOrder = "desc"; // asc（升序）| desc（降序）

    public PageRequest() {
    }

    public PageRequest(int page, int size) {
        this.page = page;
        this.size = size;
    }

    /**
     * 返回 SQL 查询的偏移量（从 0 开始）。
     */
    public int getOffset() {
        return (page - 1) * size;
    }

    // --- Getter / Setter ---

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}

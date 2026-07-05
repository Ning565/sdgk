package com.example.admission.common;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 标准分页响应。
 *
 * @param <T> 分页中元素的类型
 */
public class PageResponse<T> {

    private List<T> records;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    public PageResponse() {
    }

    private PageResponse(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = size > 0 ? (int) ((total + size - 1) / size) : 0;
    }

    public static <T> PageResponse<T> of(List<T> records, long total, int page, int size) {
        return new PageResponse<>(records, total, page, size);
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(Collections.emptyList(), 0, page, size);
    }

    /**
     * 使用 mapper 函数转换记录列表。
     */
    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = this.records.stream().map(mapper).collect(Collectors.toList());
        return new PageResponse<>(mapped, this.total, this.page, this.size);
    }

    // --- Getters ---

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    // --- Setters (for serialization frameworks) ---

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}

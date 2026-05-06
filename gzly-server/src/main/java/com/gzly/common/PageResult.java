package com.gzly.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果 — 与前端 PageResult<T> 对齐
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;

    public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
        return new PageResult<>(items, total, page, pageSize);
    }
}

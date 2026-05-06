package com.gzly.common;

import lombok.Data;

/**
 * 分页查询参数
 */
@Data
public class PageQuery {
    private Integer page = 1;
    private Integer pageSize = 20;

    public int getOffset() {
        return (Math.max(page, 1) - 1) * pageSize;
    }
}

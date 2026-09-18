package com.qms.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页返回体
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<T> records;

    public static <T> PageResult<T> empty(long pageNo, long pageSize) {
        PageResult<T> p = new PageResult<>();
        p.setTotal(0);
        p.setPageNo(pageNo);
        p.setPageSize(pageSize);
        p.setRecords(Collections.emptyList());
        return p;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> p = new PageResult<>();
        p.setTotal(page.getTotal());
        p.setPageNo(page.getCurrent());
        p.setPageSize(page.getSize());
        p.setRecords(page.getRecords());
        return p;
    }

    public static <S, T> PageResult<T> of(IPage<S> page, List<T> records) {
        PageResult<T> p = new PageResult<>();
        p.setTotal(page.getTotal());
        p.setPageNo(page.getCurrent());
        p.setPageSize(page.getSize());
        p.setRecords(records);
        return p;
    }
}

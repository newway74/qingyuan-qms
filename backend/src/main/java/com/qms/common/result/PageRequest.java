package com.qms.common.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一分页请求参数
 */
@Data
public class PageRequest implements Serializable {

    @Min(value = 1, message = "页码最小为1")
    private long pageNo = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 200, message = "每页条数最大为200")
    private long pageSize = 10;

    private String orderBy;

    private boolean asc = false;
}

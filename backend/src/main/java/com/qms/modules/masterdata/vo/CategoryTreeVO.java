package com.qms.modules.masterdata.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryTreeVO {

    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer sort;
    private Integer status;
    private List<CategoryTreeVO> children = new ArrayList<>();
}

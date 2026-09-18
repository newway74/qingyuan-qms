package com.qms.modules.standard.vo;

import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import lombok.Data;

import java.util.List;

@Data
public class TemplateDetailVO {

    private StandardTemplate template;

    private String categoryName;

    private List<StandardItem> items;
}

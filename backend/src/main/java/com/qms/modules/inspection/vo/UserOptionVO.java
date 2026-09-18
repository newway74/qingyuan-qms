package com.qms.modules.inspection.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 人员下拉选项。
 */
@Data
@AllArgsConstructor
public class UserOptionVO {

    private Long id;
    private String username;
    private String realName;
}

package com.qms.modules.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserInfoVO {

    private Long userId;
    private String username;
    private String realName;
    private Long deptId;
    private String deptName;
    private String phone;
    private List<String> roles;
    private List<String> permissions;
    private String dataScope;
}

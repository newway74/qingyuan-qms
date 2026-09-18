package com.qms.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志：只增
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    @TableId
    private Long id;

    private String username;

    /** LOCAL/SSO */
    private String loginType;

    /** 1成功 0失败 */
    private Integer success;

    private String ip;

    private String userAgent;

    private String failReason;

    private LocalDateTime createdAt;
}

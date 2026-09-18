package com.qms.common.result;

import lombok.Getter;

/**
 * 统一错误码。命名规范：模块_错误语义
 */
@Getter
public enum ResultCode {

    // 通用
    SUCCESS("0", "ok"),
    SYSTEM_ERROR("SYS_ERROR", "系统繁忙，请稍后再试"),
    PARAM_INVALID("PARAM_INVALID", "参数校验失败"),
    PARAM_MISSING("PARAM_MISSING", "必要参数缺失"),
    DATA_NOT_FOUND("DATA_NOT_FOUND", "数据不存在"),
    DATA_DUPLICATED("DATA_DUPLICATED", "数据已存在"),
    CONCURRENT_VERSION_CONFLICT("CONCURRENT_VERSION_CONFLICT", "数据已被他人修改，请刷新后重试"),
    REPEAT_SUBMIT("REPEAT_SUBMIT", "请勿重复提交"),

    // 鉴权
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未登录或登录已过期"),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "无效的访问令牌"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "没有操作权限"),
    AUTH_BAD_CREDENTIAL("AUTH_BAD_CREDENTIAL", "用户名或密码错误"),
    AUTH_USER_DISABLED("AUTH_USER_DISABLED", "账号已停用"),
    AUTH_PASSWORD_CONFIRM_FAIL("AUTH_PASSWORD_CONFIRM_FAIL", "二次密码校验失败"),

    // 业务状态
    BIZ_STATE_INVALID("BIZ_STATE_INVALID", "当前状态不允许该操作"),
    BIZ_REQUIRED_ITEM_MISSING("BIZ_REQUIRED_ITEM_MISSING", "存在未录入的必检项"),
    BIZ_JUDGE_VETO_FAIL("BIZ_JUDGE_VETO_FAIL", "严重A类项不合格，不可判定合格或让步接收"),
    BIZ_DATE_INVALID("BIZ_DATE_INVALID", "日期逻辑不合法"),

    // 附件
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED", "不支持的文件类型"),
    FILE_TOO_LARGE("FILE_TOO_LARGE", "文件大小超过限制"),
    FILE_UPLOAD_FAIL("FILE_UPLOAD_FAIL", "文件上传失败"),

    // 外部适配
    INTEGRATION_UNAVAILABLE("INTEGRATION_UNAVAILABLE", "外部服务暂不可用");

    private final String code;
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}

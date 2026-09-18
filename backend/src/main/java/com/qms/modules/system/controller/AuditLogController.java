package com.qms.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.system.entity.SysAuditLog;
import com.qms.modules.system.entity.SysLoginLog;
import com.qms.modules.system.mapper.SysAuditLogMapper;
import com.qms.modules.system.mapper.SysLoginLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "审计日志")
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final SysAuditLogMapper auditLogMapper;
    private final SysLoginLogMapper loginLogMapper;

    @Operation(summary = "操作审计日志分页查询（只读）")
    @GetMapping("/logs")
    @PreAuthorize("@perm.has('audit:list')")
    public R<PageResult<SysAuditLog>> auditLogs(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<SysAuditLog>()
                .eq(username != null && !username.isBlank(), SysAuditLog::getUsername, username)
                .eq(module != null && !module.isBlank(), SysAuditLog::getModule, module)
                .eq(bizType != null && !bizType.isBlank(), SysAuditLog::getBizType, bizType)
                .eq(bizId != null, SysAuditLog::getBizId, bizId)
                .ge(from != null, SysAuditLog::getCreatedAt, from)
                .le(to != null, SysAuditLog::getCreatedAt, to)
                .orderByDesc(SysAuditLog::getId);
        return R.ok(PageResult.of(auditLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper)));
    }

    @Operation(summary = "登录日志分页查询（只读）")
    @GetMapping("/login-logs")
    @PreAuthorize("@perm.has('audit:login')")
    public R<PageResult<SysLoginLog>> loginLogs(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String username) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .eq(username != null && !username.isBlank(), SysLoginLog::getUsername, username)
                .orderByDesc(SysLoginLog::getId);
        return R.ok(PageResult.of(loginLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper)));
    }
}

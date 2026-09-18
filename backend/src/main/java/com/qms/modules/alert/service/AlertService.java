package com.qms.modules.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.modules.alert.entity.Alert;
import com.qms.modules.alert.mapper.AlertMapper;
import com.qms.modules.message.service.MessageService;
import com.qms.modules.todo.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 预警服务：去重落账（dedup_key 唯一）、等级升级、状态解除联动待办/消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    public static final String BIZ_TYPE = "qc_alert";

    private final AlertMapper alertMapper;
    private final TodoService todoService;
    private final MessageService messageService;

    public PageResult<Alert> page(PageRequest request, String alertType, Integer status) {
        Page<Alert> page = alertMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()),
                new LambdaQueryWrapper<Alert>()
                        .eq(alertType != null && !alertType.isBlank(), Alert::getAlertType, alertType)
                        .eq(status != null, Alert::getStatus, status)
                        .orderByDesc(Alert::getTriggeredAt)
                        .orderByDesc(Alert::getId));
        return PageResult.of(page, page.getRecords());
    }

    /** 人工处理预警（状态解除的自动核销走 resolve） */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "预警不存在");
        }
        if (alert.getStatus() == 1) {
            return;
        }
        close(alert);
    }

    /**
     * 扫描命中：未处理则升级文案/等级并保持不重复；不存在则落账并驱动待办+消息。
     *
     * @return true=本次新生成
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean raise(String alertType, String bizType, Long bizId, String targetRole,
                         int level, String message, String dedupKey) {
        Alert existing = alertMapper.selectOne(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getDedupKey, dedupKey)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            if (existing.getStatus() == 0
                    && (!Integer.valueOf(level).equals(existing.getLevel()) || !message.equals(existing.getMessage()))) {
                existing.setLevel(level);
                existing.setMessage(message);
                alertMapper.updateById(existing);
            }
            return false;
        }
        Alert alert = new Alert();
        alert.setAlertType(alertType);
        alert.setBizType(bizType);
        alert.setBizId(bizId);
        alert.setTargetRole(targetRole);
        alert.setMessage(message);
        alert.setLevel(level);
        alert.setStatus(0);
        alert.setDedupKey(dedupKey);
        alert.setTriggeredAt(now);
        alert.setTenantId(SecurityUtils.getTenantId());
        alertMapper.insert(alert);

        // 预警驱动角色待办与站内消息
        String title = "【" + alertTypeName(alertType) + "】" + message;
        todoService.createRoleTodo(targetRole, "ALERT", BIZ_TYPE, alert.getId(), title, null, null);
        messageService.pushToRole(targetRole, "ALERT", title, null, BIZ_TYPE, alert.getId());
        return true;
    }

    /** 按去重键解除（预警条件不再成立） */
    @Transactional(rollbackFor = Exception.class)
    public int resolveByDedupPrefix(String alertType, String bizType, Long bizId) {
        var alerts = alertMapper.selectList(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getAlertType, alertType)
                .eq(Alert::getBizType, bizType)
                .eq(Alert::getBizId, bizId)
                .eq(Alert::getStatus, 0));
        for (Alert alert : alerts) {
            close(alert);
        }
        return alerts.size();
    }

    private void close(Alert alert) {
        alert.setStatus(1);
        alert.setHandledAt(LocalDateTime.now());
        alert.setHandlerId(SecurityUtils.getCurrentUserId());
        alertMapper.updateById(alert);
        todoService.handle(BIZ_TYPE, alert.getId());
    }

    /** 预警类型中文名（消息标题/页面展示用） */
    public static String alertTypeName(String alertType) {
        return switch (alertType) {
            case "SLA_WARNING" -> "SLA临期";
            case "SLA_OVERDUE" -> "SLA超期";
            case "RETAIN_EXPIRE" -> "留样到期";
            case "LICENSE_EXPIRE" -> "证照到期";
            case "NEAR_EXPIRY" -> "批次近效期";
            case "BATCH_EXPIRED" -> "批次过期";
            default -> alertType;
        };
    }
}

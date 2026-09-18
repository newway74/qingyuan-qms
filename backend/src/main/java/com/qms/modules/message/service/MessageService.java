package com.qms.modules.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.security.LoginUser;
import com.qms.modules.message.entity.Message;
import com.qms.modules.message.mapper.MessageMapper;
import com.qms.modules.message.notify.MessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息：发送（经适配接口）、我的消息查询、已读维护。
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final MessageSender messageSender;

    @Transactional(rollbackFor = Exception.class)
    public void pushToUser(Long userId, String msgType, String title, String content,
                           String bizType, Long bizId) {
        messageSender.send(userId, null, msgType, title, content, bizType, bizId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void pushToRole(String roleCode, String msgType, String title, String content,
                           String bizType, Long bizId) {
        messageSender.send(null, roleCode, msgType, title, content, bizType, bizId);
    }

    /** 当前用户可见消息：个人消息 + 本人所属角色的角色消息 */
    public PageResult<Message> pageMy(PageRequest request, Integer isRead, String msgType) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getReceiverId, loginUser.getUserId())
                        .or(m -> m.isNull(Message::getReceiverId)
                                .in(loginUser.getRoles() != null && !loginUser.getRoles().isEmpty(),
                                        Message::getRoleCode, loginUser.getRoles())))
                .eq(isRead != null, Message::getIsRead, isRead)
                .eq(msgType != null && !msgType.isBlank(), Message::getMsgType, msgType)
                .orderByDesc(Message::getCreatedAt)
                .orderByDesc(Message::getId);
        Page<Message> page = messageMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords());
    }

    public long unreadCount() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getIsRead, 0)
                .and(w -> w.eq(Message::getReceiverId, loginUser.getUserId())
                        .or(m -> m.isNull(Message::getReceiverId)
                                .in(loginUser.getRoles() != null && !loginUser.getRoles().isEmpty(),
                                        Message::getRoleCode, loginUser.getRoles()))));
        return count == null ? 0 : count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        Message message = messageMapper.selectById(id);
        if (message == null || !canSee(message)) {
            throw new BizException(ResultCode.AUTH_FORBIDDEN);
        }
        if (message.getIsRead() == 1) {
            return;
        }
        message.setIsRead(1);
        message.setReadAt(LocalDateTime.now());
        messageMapper.updateById(message);
    }

    @Transactional(rollbackFor = Exception.class)
    public int readAll() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        List<Message> unread = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getIsRead, 0)
                .and(w -> w.eq(Message::getReceiverId, loginUser.getUserId())
                        .or(m -> m.isNull(Message::getReceiverId)
                                .in(loginUser.getRoles() != null && !loginUser.getRoles().isEmpty(),
                                        Message::getRoleCode, loginUser.getRoles()))));
        LocalDateTime now = LocalDateTime.now();
        for (Message message : unread) {
            message.setIsRead(1);
            message.setReadAt(now);
            messageMapper.updateById(message);
        }
        return unread.size();
    }

    private boolean canSee(Message message) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser.getUserId().equals(message.getReceiverId())) {
            return true;
        }
        return message.getReceiverId() == null && message.getRoleCode() != null
                && loginUser.getRoles() != null && loginUser.getRoles().contains(message.getRoleCode());
    }
}

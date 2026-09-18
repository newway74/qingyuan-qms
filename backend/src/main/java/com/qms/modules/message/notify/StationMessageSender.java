package com.qms.modules.message.notify;

import com.qms.common.utils.SecurityUtils;
import com.qms.modules.message.entity.Message;
import com.qms.modules.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 站内信落库实现。
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class StationMessageSender implements MessageSender {

    private final MessageMapper messageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(Long userId, String roleCode, String msgType, String title, String content,
                     String bizType, Long bizId) {
        Message message = new Message();
        message.setReceiverId(userId);
        message.setRoleCode(roleCode);
        message.setMsgType(msgType);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setIsRead(0);
        message.setTenantId(SecurityUtils.getTenantId());
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }
}

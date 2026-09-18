package com.qms.modules.message.notify;

/**
 * 消息发送适配接口：当前仅站内信实现（StationMessageSender）。
 * 后续接入企业微信/邮件/短信时新增实现即可，业务代码无感知。
 */
public interface MessageSender {

    /**
     * @param userId  接收用户（个人消息）
     * @param roleCode 接收角色（角色消息，与 userId 二选一）
     */
    void send(Long userId, String roleCode, String msgType, String title, String content,
              String bizType, Long bizId);
}

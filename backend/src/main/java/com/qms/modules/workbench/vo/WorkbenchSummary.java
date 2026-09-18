package com.qms.modules.workbench.vo;

import java.util.List;
import java.util.Map;

/**
 * 角色工作台聚合视图。
 *
 * @param todoTotal       未处理待办总数
 * @param unreadMessages  未读消息数
 * @param todoByType      未处理待办按类型分组计数
 * @param cards           角色化卡片（按角色集合计算，key 由前端映射跳转与图标）
 */
public record WorkbenchSummary(
        long todoTotal,
        long unreadMessages,
        Map<String, Long> todoByType,
        List<Card> cards) {

    /**
     * @param key   卡片标识（前端决定路由/图标/颜色）
     * @param label 卡片标题
     * @param value 数量
     * @param tone  normal/warning/danger 视觉级别
     */
    public record Card(String key, String label, long value, String tone) {
    }
}

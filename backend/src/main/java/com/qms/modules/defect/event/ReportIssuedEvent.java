package com.qms.modules.defect.event;

/**
 * 检验报告签发事件（检验模块 → 不合格闭环模块解耦）。
 *
 * @param reportId 已置为 ISSUED 的报告ID
 */
public record ReportIssuedEvent(Long reportId) {
}

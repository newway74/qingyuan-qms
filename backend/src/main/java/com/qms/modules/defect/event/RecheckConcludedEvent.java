package com.qms.modules.defect.event;

/**
 * 复检子任务复核完成事件：检验模块 → 不合格闭环模块（避免模块间循环依赖）。
 * 在复检报告生成后、事务提交前发布，闭环监听与业务同事务回滚。
 *
 * @param childTaskId  复检子任务id
 * @param parentTaskId 原任务id
 * @param qualified    复检结论是否合格（QUALIFIED/CONCESSION 视为合格闭环）
 */
public record RecheckConcludedEvent(Long childTaskId, Long parentTaskId, boolean qualified) {
}

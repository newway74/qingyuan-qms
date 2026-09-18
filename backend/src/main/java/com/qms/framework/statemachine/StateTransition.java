package com.qms.framework.statemachine;

import java.util.Set;

/**
 * 状态迁移定义：current --(action, 允许角色集合)--> target
 *
 * @param current 当前状态
 * @param action  动作
 * @param target  目标状态
 * @param roles   允许执行的角色 code 集合；为空表示不限制角色（仍受权限码控制）
 */
public record StateTransition(String current, String action, String target, Set<String> roles) {

    public static StateTransition of(String current, String action, String target) {
        return new StateTransition(current, action, target, Set.of());
    }

    public static StateTransition of(String current, String action, String target, Set<String> roles) {
        return new StateTransition(current, action, target, roles);
    }
}

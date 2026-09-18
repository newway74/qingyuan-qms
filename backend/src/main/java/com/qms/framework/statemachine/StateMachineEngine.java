package com.qms.framework.statemachine;

import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通用单据状态机。
 * 各业务模块在启动时以 register(bizType, transitions...) 注册；
 * Service 执行状态流转前必须调用 validate / nextState，非法动作抛 BIZ_STATE_INVALID。
 */
@Slf4j
@Component
public class StateMachineEngine {

    /** bizType -> (currentState -> (action -> transition)) */
    private final Map<String, Map<String, Map<String, StateTransition>>> registry = new HashMap<>();

    public void register(String bizType, Set<StateTransition> transitions) {
        Map<String, Map<String, StateTransition>> byCurrent = new HashMap<>();
        for (StateTransition t : transitions) {
            byCurrent.computeIfAbsent(t.current(), k -> new HashMap<>()).put(t.action(), t);
        }
        registry.put(bizType, byCurrent);
        log.info("状态机注册完成: bizType={}, 迁移数={}", bizType, transitions.size());
    }

    public void validate(String bizType, String currentState, String action, Set<String> userRoles) {
        nextState(bizType, currentState, action, userRoles);
    }

    public String nextState(String bizType, String currentState, String action, Set<String> userRoles) {
        Map<String, Map<String, StateTransition>> byCurrent = registry.get(bizType);
        if (byCurrent == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "状态机未定义: " + bizType);
        }
        Map<String, StateTransition> actions = byCurrent.get(currentState);
        StateTransition transition = actions == null ? null : actions.get(action);
        if (transition == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "当前状态[" + currentState + "]不允许执行操作[" + action + "]");
        }
        Set<String> allowed = transition.roles();
        if (!allowed.isEmpty() && (userRoles == null || userRoles.stream().noneMatch(allowed::contains))) {
            throw new BizException(ResultCode.AUTH_FORBIDDEN,
                    "当前角色不允许执行操作[" + action + "]");
        }
        return transition.target();
    }

    /** 查询某状态下允许的动作（前端按钮可用此接口渲染） */
    public Set<String> allowedActions(String bizType, String currentState, Set<String> userRoles) {
        Map<String, Map<String, StateTransition>> byCurrent = registry.get(bizType);
        if (byCurrent == null) {
            return Set.of();
        }
        Map<String, StateTransition> actions = byCurrent.get(currentState);
        if (actions == null) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        actions.forEach((action, t) -> {
            if (t.roles().isEmpty() || (userRoles != null && userRoles.stream().anyMatch(t.roles()::contains))) {
                result.add(action);
            }
        });
        return result;
    }
}

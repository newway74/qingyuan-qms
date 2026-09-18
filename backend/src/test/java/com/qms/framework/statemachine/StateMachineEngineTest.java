package com.qms.framework.statemachine;

import com.qms.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用状态机：合法流转、非法动作、角色限制、动作查询。
 */
class StateMachineEngineTest {

    private StateMachineEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StateMachineEngine();
        engine.register("qc_sampling", Set.of(
                StateTransition.of("DRAFT", "SUBMIT", "RECEIVE", Set.of("SAMPLER")),
                StateTransition.of("DRAFT", "CANCEL", "CANCELED", Set.of("SAMPLER")),
                StateTransition.of("RECEIVE", "RECEIVE", "RECEIVED", Set.of("SAMPLER")),
                StateTransition.of("RECEIVE", "BACK_TO_SAMPLING", "DRAFT", Set.of("SAMPLER"))
        ));
    }

    @Test
    void happy_path_returns_target_state() {
        assertEquals("RECEIVE", engine.nextState("qc_sampling", "DRAFT", "SUBMIT", Set.of("SAMPLER")));
        engine.validate("qc_sampling", "DRAFT", "SUBMIT", Set.of("SAMPLER"));
    }

    @Test
    void unregistered_biz_type_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> engine.nextState("not_exist", "DRAFT", "SUBMIT", Set.of("SAMPLER")));
        assertTrue(ex.getMessage().contains("状态机未定义"));
    }

    @Test
    void unknown_action_in_current_state_throws() {
        // RECEIVE 节点不允许 CANCEL
        BizException ex = assertThrows(BizException.class,
                () -> engine.nextState("qc_sampling", "RECEIVE", "CANCEL", Set.of("SAMPLER")));
        assertTrue(ex.getMessage().contains("不允许执行操作"));
    }

    @Test
    void unknown_state_throws_same_as_illegal_action() {
        assertThrows(BizException.class,
                () -> engine.nextState("qc_sampling", "GHOST", "SUBMIT", Set.of("SAMPLER")));
    }

    @Nested
    @DisplayName("角色限制")
    class Roles {

        @Test
        void role_not_allowed_throws_forbidden() {
            BizException ex = assertThrows(BizException.class,
                    () -> engine.nextState("qc_sampling", "DRAFT", "SUBMIT", Set.of("INSPECTOR")));
            assertEquals("AUTH_FORBIDDEN", ex.getCode());
        }

        @Test
        void null_roles_throws_when_transition_restricted() {
            assertThrows(BizException.class,
                    () -> engine.nextState("qc_sampling", "DRAFT", "SUBMIT", null));
        }

        @Test
        void empty_role_set_on_transition_means_any_authenticated_user() {
            engine.register("free_biz", Set.of(StateTransition.of("S1", "GO", "S2")));
            assertEquals("S2", engine.nextState("free_biz", "S1", "GO", null));
            assertEquals("S2", engine.nextState("free_biz", "S1", "GO", Set.of("ANYONE")));
        }

        @Test
        void admin_role_can_act_when_listed() {
            assertEquals("RECEIVED", engine.nextState("qc_sampling", "RECEIVE", "RECEIVE",
                    Set.of("ADMIN", "QA_MANAGER", "SAMPLER")));
        }
    }

    @Nested
    @DisplayName("allowedActions 按钮渲染")
    class AllowedActions {

        @Test
        void filters_by_role() {
            Set<String> samplerActions = engine.allowedActions("qc_sampling", "DRAFT", Set.of("SAMPLER"));
            assertEquals(Set.of("SUBMIT", "CANCEL"), samplerActions);
            assertEquals(Set.of(), engine.allowedActions("qc_sampling", "DRAFT", Set.of("INSPECTOR")));
        }

        @Test
        void unknown_biz_or_state_returns_empty() {
            assertEquals(Set.of(), engine.allowedActions("nope", "DRAFT", Set.of("SAMPLER")));
            assertEquals(Set.of(), engine.allowedActions("qc_sampling", "GHOST", Set.of("SAMPLER")));
            assertEquals(Set.of(), engine.allowedActions("qc_sampling", "DRAFT", null));
        }

        @Test
        void unrestricted_transition_always_listed() {
            engine.register("free_biz", Set.of(StateTransition.of("S1", "GO", "S2")));
            assertEquals(Set.of("GO"), engine.allowedActions("free_biz", "S1", null));
        }
    }
}

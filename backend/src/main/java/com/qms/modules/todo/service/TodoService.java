package com.qms.modules.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.security.LoginUser;
import com.qms.modules.message.service.MessageService;
import com.qms.modules.todo.entity.Todo;
import com.qms.modules.todo.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一待办服务：业务单据状态流转时挂载/核销待办；落账同时推送站内消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoMapper todoMapper;
    private final MessageService messageService;

    @Transactional(rollbackFor = Exception.class)
    public void createUserTodo(Long userId, String todoType, String bizType, Long bizId,
                               String title, String bizNo, LocalDateTime deadline) {
        create(userId, null, todoType, bizType, bizId, title, bizNo, deadline);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createRoleTodo(String roleCode, String todoType, String bizType, Long bizId,
                               String title, String bizNo, LocalDateTime deadline) {
        create(null, roleCode, todoType, bizType, bizId, title, bizNo, deadline);
    }

    private void create(Long userId, String roleCode, String todoType, String bizType, Long bizId,
                        String title, String bizNo, LocalDateTime deadline) {
        // 同业务同类型未处理待办去重
        Long exists = todoMapper.selectCount(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getBizType, bizType)
                .eq(Todo::getBizId, bizId)
                .eq(Todo::getTodoType, todoType)
                .eq(userId != null, Todo::getUserId, userId)
                .eq(roleCode != null, Todo::getRoleCode, roleCode)
                .eq(Todo::getStatus, 0));
        if (exists != null && exists > 0) {
            return;
        }
        Todo todo = new Todo();
        todo.setUserId(userId);
        todo.setRoleCode(roleCode);
        todo.setTodoType(todoType);
        todo.setBizType(bizType);
        todo.setBizId(bizId);
        todo.setTitle(title);
        todo.setBizNo(bizNo);
        todo.setPriority(deadline != null && deadline.isBefore(LocalDateTime.now().plusHours(24)) ? 1 : 0);
        todo.setStatus(0);
        todo.setDeadline(deadline);
        todo.setTenantId(SecurityUtils.getTenantId());
        todo.setCreatedAt(LocalDateTime.now());
        todoMapper.insert(todo);

        // 待办即消息：实际落账才推送，天然不重复
        String content = bizNo != null ? "单号：" + bizNo : null;
        if (userId != null) {
            messageService.pushToUser(userId, "TODO", title, content, bizType, bizId);
        } else {
            messageService.pushToRole(roleCode, "TODO", title, content, bizType, bizId);
        }
    }

    /** 核销某业务单据的全部未处理待办 */
    @Transactional(rollbackFor = Exception.class)
    public void handle(String bizType, Long bizId) {
        var todos = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getBizType, bizType)
                .eq(Todo::getBizId, bizId)
                .eq(Todo::getStatus, 0));
        LocalDateTime now = LocalDateTime.now();
        Long handlerId = SecurityUtils.getCurrentUserId();
        for (Todo todo : todos) {
            todo.setStatus(1);
            todo.setHandledAt(now);
            todo.setHandlerId(handlerId);
            todoMapper.updateById(todo);
        }
    }

    // ---------------- 工作台查询 ----------------

    /** 当前用户可见待办：个人待办 + 本人所属角色的角色待办 */
    public PageResult<Todo> pageMy(PageRequest request, Integer status, String todoType) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Page<Todo> page = todoMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()),
                visibleWrapper(loginUser)
                        .eq(status != null, Todo::getStatus, status)
                        .eq(todoType != null && !todoType.isBlank(), Todo::getTodoType, todoType)
                        .orderByAsc(Todo::getPriority)
                        .orderByAsc(Todo::getDeadline)
                        .orderByDesc(Todo::getCreatedAt));
        return PageResult.of(page, page.getRecords());
    }

    /** 未处理待办按类型分组计数（工作台卡片），key=todoType */
    public Map<String, Long> openCountByType() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        List<Todo> todos = todoMapper.selectList(visibleWrapper(loginUser).eq(Todo::getStatus, 0));
        Map<String, Long> result = new LinkedHashMap<>();
        for (Todo todo : todos) {
            result.merge(todo.getTodoType(), 1L, Long::sum);
        }
        return result;
    }

    /** 未处理待办总数（角标） */
    public long openTotal() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long count = todoMapper.selectCount(visibleWrapper(loginUser).eq(Todo::getStatus, 0));
        return count == null ? 0 : count;
    }

    private LambdaQueryWrapper<Todo> visibleWrapper(LoginUser loginUser) {
        return new LambdaQueryWrapper<Todo>()
                .and(w -> w.eq(Todo::getUserId, loginUser.getUserId())
                        .or(o -> o.isNull(Todo::getUserId)
                                .in(loginUser.getRoles() != null && !loginUser.getRoles().isEmpty(),
                                        Todo::getRoleCode, loginUser.getRoles())));
    }
}

package com.qms.modules.workbench.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.message.entity.Message;
import com.qms.modules.message.service.MessageService;
import com.qms.modules.todo.entity.Todo;
import com.qms.modules.todo.service.TodoService;
import com.qms.modules.workbench.service.WorkbenchService;
import com.qms.modules.workbench.vo.WorkbenchSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "品控工作台：待办与消息")
@RestController
@RequestMapping("/api/v1/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final TodoService todoService;
    private final MessageService messageService;
    private final WorkbenchService workbenchService;

    @Operation(summary = "角色工作台聚合（卡片+待办/消息计数）")
    @GetMapping("/summary")
    @PreAuthorize("@perm.has('workbench:view')")
    public R<WorkbenchSummary> summary() {
        return R.ok(workbenchService.summary());
    }

    // ---------------- 待办 ----------------

    @Operation(summary = "我的待办（个人 + 所属角色）")
    @GetMapping("/todos")
    @PreAuthorize("@perm.has('workbench:view')")
    public R<PageResult<Todo>> todos(PageRequest request,
                                     @RequestParam(required = false) Integer status,
                                     @RequestParam(required = false) String todoType) {
        return R.ok(todoService.pageMy(request, status, todoType));
    }

    @Operation(summary = "未处理待办计数（总数 + 按类型分组）")
    @GetMapping("/todos/counts")
    @PreAuthorize("@perm.has('workbench:view')")
    public R<Map<String, Object>> todoCounts() {
        return R.ok(Map.of(
                "total", todoService.openTotal(),
                "byType", todoService.openCountByType()));
    }

    // ---------------- 消息 ----------------

    @Operation(summary = "我的站内消息")
    @GetMapping("/messages")
    @PreAuthorize("@perm.has('message:list')")
    public R<PageResult<Message>> messages(PageRequest request,
                                           @RequestParam(required = false) Integer isRead,
                                           @RequestParam(required = false) String msgType) {
        return R.ok(messageService.pageMy(request, isRead, msgType));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/messages/unread-count")
    @PreAuthorize("@perm.has('message:list')")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("count", messageService.unreadCount()));
    }

    @Operation(summary = "消息标记已读")
    @PostMapping("/messages/{id}/read")
    @PreAuthorize("@perm.has('message:read')")
    public R<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return R.ok();
    }

    @Operation(summary = "全部消息标记已读")
    @PostMapping("/messages/read-all")
    @PreAuthorize("@perm.has('message:read')")
    public R<Map<String, Integer>> readAll() {
        return R.ok(Map.of("updated", messageService.readAll()));
    }
}

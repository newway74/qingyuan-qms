package com.qms.modules.inspection.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.inspection.dto.ResultBatchSaveRequest;
import com.qms.modules.inspection.dto.TaskAssignRequest;
import com.qms.modules.inspection.dto.TaskReviewRequest;
import com.qms.modules.inspection.dto.TaskSubmitRequest;
import com.qms.modules.inspection.entity.InspectionResult;
import com.qms.modules.inspection.service.InspectionTaskService;
import com.qms.modules.inspection.vo.TaskDetailVO;
import com.qms.modules.inspection.vo.TaskListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "检验任务")
@RestController
@RequestMapping("/api/v1/inspection/tasks")
@RequiredArgsConstructor
public class InspectionTaskController {

    private final InspectionTaskService taskService;

    @Operation(summary = "任务分页（view=PENDING_ASSIGN/MINE/ALL）")
    @GetMapping
    @PreAuthorize("@perm.has('inspection:task:list')")
    public R<PageResult<TaskListVO>> page(PageRequest request,
                                          @RequestParam(required = false, defaultValue = "ALL") String view,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String taskNo) {
        return R.ok(taskService.page(request, view, status, taskNo));
    }

    @Operation(summary = "按角色查询启用人员（分配下拉）")
    @GetMapping("/users")
    @PreAuthorize("@perm.has('inspection:task:list')")
    public R<List<com.qms.modules.inspection.vo.UserOptionVO>> users(
            @RequestParam String roleCode) {
        return R.ok(taskService.usersByRole(roleCode));
    }

    @Operation(summary = "任务工作台详情（模板项+结果+报告+可执行动作）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('inspection:task:view')")
    public R<TaskDetailVO> detail(@PathVariable Long id) {
        return R.ok(taskService.detail(id));
    }

    @Operation(summary = "任务结果列表")
    @GetMapping("/{id}/results")
    @PreAuthorize("@perm.has('inspection:result:list')")
    public R<List<InspectionResult>> results(@PathVariable Long id) {
        return R.ok(taskService.listResults(id));
    }

    @Operation(summary = "分配检验员/复核人（计算SLA，落待办）")
    @PostMapping("/assign")
    @PreAuthorize("@perm.has('inspection:task:assign')")
    public R<Void> assign(@Valid @RequestBody TaskAssignRequest request) {
        taskService.assign(request);
        return R.ok();
    }

    @Operation(summary = "开始检验（初始化逐项结果快照）")
    @PostMapping("/{id}/start")
    @PreAuthorize("@perm.has('inspection:task:handle')")
    public R<Void> start(@PathVariable Long id) {
        taskService.start(id);
        return R.ok();
    }

    @Operation(summary = "批量保存逐项结果（自动判定）")
    @PostMapping("/{id}/results")
    @PreAuthorize("@perm.has('inspection:result:save')")
    public R<Void> saveResults(@PathVariable Long id,
                               @Valid @RequestBody ResultBatchSaveRequest request) {
        taskService.saveResults(id, request);
        return R.ok();
    }

    @Operation(summary = "提交复核（必检校验+密码二次认证+检验员签名）")
    @PostMapping("/{id}/submit")
    @PreAuthorize("@perm.has('inspection:task:submit')")
    public R<Void> submit(@PathVariable Long id,
                          @Valid @RequestBody TaskSubmitRequest request,
                          HttpServletRequest httpRequest) {
        taskService.submit(id, request.getPassword(), clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return R.ok();
    }

    @Operation(summary = "复核通过/驳回（双签，A类改判硬拦截）")
    @PostMapping("/{id}/review")
    @PreAuthorize("@perm.has('inspection:task:review')")
    public R<Void> review(@PathVariable Long id,
                          @Valid @RequestBody TaskReviewRequest request,
                          HttpServletRequest httpRequest) {
        taskService.review(id, request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return R.ok();
    }

    @Operation(summary = "闭环任务（合格/让步；不合格须不合格单已闭环）")
    @PostMapping("/{id}/close")
    @PreAuthorize("@perm.has('inspection:task:assign')")
    public R<Void> close(@PathVariable Long id) {
        taskService.close(id);
        return R.ok();
    }

    @Operation(summary = "发起复检（原任务转复检中，新建子任务，原任务/报告不可变）")
    @PostMapping("/{id}/recheck")
    @PreAuthorize("@perm.has('inspection:task:recheck')")
    public R<Long> recheck(@PathVariable Long id,
                           @Valid @RequestBody com.qms.modules.defect.dto.TaskRecheckRequest request) {
        return R.ok(taskService.recheck(id, request.getReason()).getId());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package com.qms.modules.defect.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.defect.dto.RectificationCreateRequest;
import com.qms.modules.defect.dto.RectificationReplyRequest;
import com.qms.modules.defect.dto.RectificationVerifyRequest;
import com.qms.modules.defect.service.RectificationService;
import com.qms.modules.defect.vo.RectificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "供应商整改")
@RestController
@RequestMapping("/api/v1/rectifications")
@RequiredArgsConstructor
public class RectificationController {

    private final RectificationService rectificationService;

    @Operation(summary = "整改单分页")
    @GetMapping
    @PreAuthorize("@perm.has('rect:list')")
    public R<PageResult<RectificationVO>> page(PageRequest request,
                                               @RequestParam(required = false) String rectifyNo,
                                               @RequestParam(required = false) String status) {
        return R.ok(rectificationService.page(request, rectifyNo, status));
    }

    @Operation(summary = "整改单详情")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('rect:view')")
    public R<RectificationVO> detail(@PathVariable Long id) {
        return R.ok(rectificationService.detail(id));
    }

    @Operation(summary = "签发整改单")
    @PostMapping
    @PreAuthorize("@perm.has('rect:save')")
    public R<Long> create(@Valid @RequestBody RectificationCreateRequest request) {
        return R.ok(rectificationService.create(request));
    }

    @Operation(summary = "供应商回复纠正措施")
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('rect:save')")
    public R<Void> reply(@PathVariable Long id,
                         @Valid @RequestBody RectificationReplyRequest request) {
        rectificationService.reply(id, request);
        return R.ok();
    }

    @Operation(summary = "质量主管验证（通过即发起复检，密码+签名）")
    @PostMapping("/{id}/verify")
    @PreAuthorize("@perm.has('rect:verify')")
    public R<Void> verify(@PathVariable Long id,
                          @Valid @RequestBody RectificationVerifyRequest request,
                          HttpServletRequest httpRequest) {
        rectificationService.verify(id, request, clientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return R.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package com.qms.modules.attachment.controller;

import com.qms.common.result.R;
import com.qms.modules.attachment.entity.Attachment;
import com.qms.modules.attachment.service.AttachmentService;
import com.qms.modules.attachment.storage.StorageObject;
import com.qms.modules.attachment.vo.AttachmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "附件管理")
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "上传附件（图片/PDF，大小与类型服务端校验）")
    @PostMapping("/upload")
    public R<AttachmentVO> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam String bizType,
                                  @RequestParam(required = false) Long bizId) {
        return R.ok(attachmentService.upload(file, bizType, bizId));
    }

    @Operation(summary = "下载附件")
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        Attachment attachment = attachmentService.getRequired(id);
        StorageObject obj = attachmentService.openStream(attachment);
        String fileName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        MediaType mediaType = attachment.getContentType() != null
                ? MediaType.parseMediaType(attachment.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fileName)
                .contentType(mediaType)
                .body(new InputStreamResource(obj.inputStream()));
    }

    @Operation(summary = "按业务单据查询附件列表")
    @GetMapping
    public R<List<AttachmentVO>> listByBiz(@RequestParam String bizType, @RequestParam Long bizId) {
        return R.ok(attachmentService.listByBiz(bizType, bizId));
    }
}

package com.qms.modules.attachment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 附件上传限制。
 */
@Data
@Component
@ConfigurationProperties(prefix = "qms.attachment")
public class AttachmentProperties {

    private int maxSizeMb = 20;

    private String allowedExt = "jpg,jpeg,png,webp,pdf";

    public List<String> allowedExtList() {
        return Arrays.stream(allowedExt.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .toList();
    }
}

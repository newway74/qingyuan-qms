package com.qms.modules.attachment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 附件存储配置。provider=local 开箱即用；minio 对接对象存储；后续可扩展 oss。
 * 业务代码只依赖 ObjectStorage 适配接口，不感知具体实现。
 */
@Data
@Component
@ConfigurationProperties(prefix = "qms.storage")
public class StorageProperties {

    /** local / minio */
    private String provider = "local";

    private Local local = new Local();

    private Minio minio = new Minio();

    @Data
    public static class Local {
        private String dir = "./data/attachments";
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }
}

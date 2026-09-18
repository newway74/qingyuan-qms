package com.qms.modules.attachment.storage;

import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.modules.attachment.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO 对象存储实现。切换方式：STORAGE_PROVIDER=minio 并配置连接参数，业务代码零改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "qms.storage.provider", havingValue = "minio")
public class MinioObjectStorage implements ObjectStorage {

    private final StorageProperties properties;

    private MinioClient client;

    @PostConstruct
    public void init() {
        StorageProperties.Minio cfg = properties.getMinio();
        client = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(cfg.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(cfg.getBucket()).build());
            }
            log.info("MinIO 附件存储就绪: {}/{}", cfg.getEndpoint(), cfg.getBucket());
        } catch (Exception e) {
            log.error("MinIO 初始化失败: {}", e.getMessage());
            throw new BizException(ResultCode.INTEGRATION_UNAVAILABLE, "MinIO 不可用: " + e.getMessage());
        }
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.FILE_UPLOAD_FAIL, "MinIO 上传失败: " + e.getMessage());
        }
    }

    @Override
    public StorageObject download(String objectKey) {
        try {
            InputStream in = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
            return new StorageObject(in, -1, "application/octet-stream");
        } catch (Exception e) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "附件读取失败: " + e.getMessage());
        }
    }

    @Override
    public String type() {
        return "MINIO";
    }

    @Override
    public String bucket() {
        return properties.getMinio().getBucket();
    }
}

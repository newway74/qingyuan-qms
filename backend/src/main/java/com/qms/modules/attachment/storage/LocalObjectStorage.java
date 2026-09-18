package com.qms.modules.attachment.storage;

import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.modules.attachment.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储（开发/演示开箱即用；生产用 MinIO）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "qms.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private final StorageProperties properties;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        root = Paths.get(properties.getLocal().getDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("附件本地存储根目录: {}", root);
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_UPLOAD_FAIL, "本地存储写入失败: " + e.getMessage());
        }
    }

    @Override
    public StorageObject download(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.exists(target)) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "附件文件不存在");
        }
        try {
            String ct = Files.probeContentType(target);
            return new StorageObject(Files.newInputStream(target), Files.size(target),
                    ct != null ? ct : "application/octet-stream");
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_UPLOAD_FAIL, "附件读取失败: " + e.getMessage());
        }
    }

    @Override
    public String type() {
        return "LOCAL";
    }

    @Override
    public String bucket() {
        return "local-fs";
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法对象键");
        }
        return target;
    }
}

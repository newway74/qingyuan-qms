package com.qms.modules.attachment.storage;

import java.io.InputStream;

/**
 * 对象存储适配接口（本地磁盘 / MinIO / 预留 OSS）。业务模块仅依赖本接口。
 */
public interface ObjectStorage {

    /**
     * 上传对象。
     *
     * @param objectKey   对象键（相对路径，如 inspection/2026/xxx.png）
     * @param in          内容流
     * @param size        字节数
     * @param contentType MIME 类型
     */
    void upload(String objectKey, InputStream in, long size, String contentType);

    /** 下载对象 */
    StorageObject download(String objectKey);

    /** 存储类型标识：LOCAL / MINIO */
    String type();

    /** 桶/根目录标识（落 qc_attachment.bucket） */
    String bucket();
}

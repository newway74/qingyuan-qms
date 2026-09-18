package com.qms.modules.attachment.storage;

import java.io.InputStream;

/**
 * 下载对象载体。
 */
public record StorageObject(InputStream inputStream, long size, String contentType) {
}

package com.qms.modules.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.modules.attachment.config.AttachmentProperties;
import com.qms.modules.attachment.entity.Attachment;
import com.qms.modules.attachment.mapper.AttachmentMapper;
import com.qms.modules.attachment.storage.ObjectStorage;
import com.qms.modules.attachment.storage.StorageObject;
import com.qms.modules.attachment.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 附件服务：类型/大小校验、SHA256 留痕、写入对象存储适配层（本地/MinIO 可切换）。
 * 附件元数据只增不删。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final AttachmentMapper attachmentMapper;
    private final AttachmentProperties attachmentProperties;
    private final ObjectStorage objectStorage;

    @Transactional(rollbackFor = Exception.class)
    public AttachmentVO upload(MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, "上传文件为空");
        }
        if (bizType == null || bizType.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "bizType不能为空");
        }
        long maxBytes = (long) attachmentProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BizException(ResultCode.FILE_TOO_LARGE,
                    "文件超过 " + attachmentProperties.getMaxSizeMb() + "MB 限制");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extOf(original);
        if (!attachmentProperties.allowedExtList().contains(ext.toLowerCase())) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED,
                    "仅支持 " + attachmentProperties.getAllowedExt() + " 格式");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_UPLOAD_FAIL, "文件读取失败");
        }
        String sha256 = sha256(bytes);
        String objectKey = bizType.toLowerCase() + "/" + LocalDate.now().format(DAY_FMT)
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        objectStorage.upload(objectKey, new java.io.ByteArrayInputStream(bytes), bytes.length,
                file.getContentType());

        Attachment attachment = new Attachment();
        attachment.setBizType(bizType);
        attachment.setBizId(bizId == null ? 0L : bizId);
        attachment.setFileName(original);
        attachment.setFileExt(ext);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachment.setStorageType(objectStorage.type());
        attachment.setBucket(objectStorage.bucket());
        attachment.setObjectKey(objectKey);
        attachment.setSha256(sha256);
        attachment.setUploadedBy(SecurityUtils.getCurrentUserId());
        attachment.setTenantId(SecurityUtils.getTenantId());
        attachmentMapper.insert(attachment);
        log.info("附件上传成功 id={} biz={}/{} key={} sha256={}",
                attachment.getId(), bizType, attachment.getBizId(), objectKey, sha256);

        AttachmentVO vo = new AttachmentVO();
        BeanUtils.copyProperties(attachment, vo);
        return vo;
    }

    /** 服务端生成文件落库（如 PDF 质检报告） */
    @Transactional(rollbackFor = Exception.class)
    public Attachment uploadBytes(String bizType, Long bizId, String fileName, byte[] bytes,
                                  String contentType, String ext) {
        String sha256 = sha256(bytes);
        String objectKey = bizType.toLowerCase() + "/" + LocalDate.now().format(DAY_FMT)
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        objectStorage.upload(objectKey, new java.io.ByteArrayInputStream(bytes), bytes.length, contentType);

        Attachment attachment = new Attachment();
        attachment.setBizType(bizType);
        attachment.setBizId(bizId == null ? 0L : bizId);
        attachment.setFileName(fileName);
        attachment.setFileExt(ext);
        attachment.setFileSize((long) bytes.length);
        attachment.setContentType(contentType);
        attachment.setStorageType(objectStorage.type());
        attachment.setBucket(objectStorage.bucket());
        attachment.setObjectKey(objectKey);
        attachment.setSha256(sha256);
        attachment.setUploadedBy(SecurityUtils.getCurrentUserId());
        attachment.setTenantId(SecurityUtils.getTenantId());
        attachmentMapper.insert(attachment);
        return attachment;
    }

    /** 业务单据落库后回填附件归属（如先传凭证后建结果行） */
    @Transactional(rollbackFor = Exception.class)
    public void rebind(Long attachmentId, String bizType, Long bizId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "附件不存在");
        }
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachmentMapper.updateById(attachment);
    }

    public Attachment getRequired(Long id) {
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "附件不存在");
        }
        return attachment;
    }

    public StorageObject openStream(Attachment attachment) {
        return objectStorage.download(attachment.getObjectKey());
    }

    public List<AttachmentVO> listByBiz(String bizType, Long bizId) {
        List<Attachment> list = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getBizType, bizType)
                .eq(Attachment::getBizId, bizId)
                .orderByDesc(Attachment::getId));
        return list.stream().map(a -> {
            AttachmentVO vo = new AttachmentVO();
            BeanUtils.copyProperties(a, vo);
            return vo;
        }).toList();
    }

    private String extOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase();
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}

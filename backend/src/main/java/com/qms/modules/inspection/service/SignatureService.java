package com.qms.modules.inspection.service;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.qms.common.utils.SecurityUtils;
import com.qms.modules.inspection.entity.SignatureRecord;
import com.qms.modules.inspection.mapper.SignatureRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 电子签名服务：对单据快照计算 SHA256，连同签名人、IP、UA、服务端时间落只增签名表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureService {

    /** 快照规范化：key 排序，保证同一内容哈希稳定可复算 */
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .build()
            .configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final SignatureRecordMapper signatureRecordMapper;

    public String canonicalHash(Object snapshot) {
        try {
            String json = CANONICAL_MAPPER.writeValueAsString(snapshot);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("快照哈希计算失败", e);
        }
    }

    /**
     * 记录一次电子签名。
     *
     * @param bizType   TASK_SUBMIT/TASK_REVIEW/REPORT/DEFECT_APPROVAL
     * @param purpose   签名用途中文说明
     * @param snapshot  快照对象（参与哈希）
     * @param ip        客户端 IP
     * @param userAgent 客户端 UA
     */
    @Transactional(rollbackFor = Exception.class)
    public SignatureRecord record(String bizType, Long bizId, String purpose,
                                  Object snapshot, String ip, String userAgent) {
        SignatureRecord record = new SignatureRecord();
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setUserId(SecurityUtils.getCurrentUserId());
        record.setUsername(SecurityUtils.getCurrentUsername());
        record.setSignPurpose(purpose);
        record.setSnapshotHash(canonicalHash(snapshot));
        record.setIp(ip);
        record.setUserAgent(userAgent);
        record.setSignedAt(LocalDateTime.now());
        signatureRecordMapper.insert(record);
        return record;
    }
}

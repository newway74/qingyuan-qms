package com.qms.framework.audit;

import com.qms.modules.system.entity.SysLoginLog;
import com.qms.modules.system.mapper.SysLoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录日志落库（成功/失败均记录），只增。
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    @Async("auditExecutor")
    public void record(String username, String loginType, boolean success,
                       String ip, String userAgent, String failReason) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername(username);
        log.setLoginType(loginType);
        log.setSuccess(success ? 1 : 0);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setFailReason(failReason);
        log.setCreatedAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }
}

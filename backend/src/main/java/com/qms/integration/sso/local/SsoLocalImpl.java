package com.qms.integration.sso.local;

import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.integration.sso.SsoAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SSO 本地占位实现：公司统一 SSO 未接入前使用。
 * 不做真实票据交换，仅明确抛出提示，避免外部系统被写死在业务代码里。
 */
@Component
@ConditionalOnProperty(name = "qms.integration.sso", havingValue = "local", matchIfMissing = true)
public class SsoLocalImpl implements SsoAdapter {

    @Override
    public SsoIdentity exchange(String credential) {
        // 占位：阶段6在此处实现与公司 SSO 的对接；当前统一走本地账号密码登录
        throw new BizException(ResultCode.INTEGRATION_UNAVAILABLE,
                "统一SSO未接入（当前为本地占位实现），请使用账号密码登录");
    }

    @Override
    public boolean isBound(String username) {
        return false;
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public String toString() {
        return "SsoLocalImpl(placeholder)";
    }

    static Map<String, Object> empty() {
        return Map.of();
    }
}

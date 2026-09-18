package com.qms.integration.sso;

import java.util.Map;

/**
 * 统一 SSO 适配接口。
 * 业务代码只依赖本接口；本地实现见 local/SsoLocalImpl。
 * 后续接入公司 SSO（OIDC/CAS/内部协议）时新增 RemoteSsoAdapter，
 * 通过 qms.integration.sso=remote 配置切换，不改业务代码。
 */
public interface SsoAdapter {

    /**
     * 用 SSO 凭证（ticket/code）换取本地可识别的用户身份信息。
     *
     * @param credential SSO 票据/授权码
     * @return 用户属性（username、realName、phone、deptCode、roles 等）
     */
    SsoIdentity exchange(String credential);

    /**
     * 本地账号是否已与 SSO 主体绑定
     */
    boolean isBound(String username);

    /**
     * 适配标识：local / remote
     */
    String provider();

    record SsoIdentity(String username, String realName, String phone, String deptCode,
                       Map<String, Object> attributes) {
    }
}

package com.agri_system.domain.auth.model;

import java.util.UUID;

/** 服务器核实的客户授权来源。随机许可不是登录凭据，不含平台身份值或token。 */
public record ClientAuthorization(long customerId, long customerAuthVersion, String kind, String appId,
        Long identityId, Long identityAuthVersion, String authorizationId) {
    public ClientAuthorization {
        if (customerId < 1 || customerAuthVersion < 1) throw invalid();
        try {
            var id = UUID.fromString(authorizationId);
            if (!id.toString().equals(authorizationId) || id.version() != 4 || id.variant() != 2) throw invalid();
        } catch (RuntimeException bad) { throw invalid(); }
        if ("WECHAT".equals(kind)) {
            if (appId == null || !appId.matches("wx[a-zA-Z0-9]{16}") || identityId == null || identityId < 1
                    || identityAuthVersion == null || identityAuthVersion < 1) throw invalid();
        } else if (!"DEV".equals(kind) || appId != null || identityId != null || identityAuthVersion != null) {
            throw invalid();
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("客户原会话授权无效，请重新登录"); }
    @Override public String toString() { return "ClientAuthorization[授权来源隐藏]"; }
}

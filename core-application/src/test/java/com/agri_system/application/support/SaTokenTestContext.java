package com.agri_system.application.support;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 为纯 Mockito 单测提供 Sa-Token 线程本地上下文。
 * <p>
 * 应用服务层在重构后统一从 Sa-Token 解析登录态（StpUtil.isLogin / getLoginIdAsString），
 * 单测若不挂上下文会抛 InvalidContextException。在 @BeforeEach 调用 setUp()、@AfterEach 调用 tearDown() 即可。
 */
public final class SaTokenTestContext {

    private SaTokenTestContext() {
    }

    public static void setUp() {
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
        SaTokenContextForThreadLocalStorage.setBox(new MockSaRequest(), new MockSaResponse(), new MockSaStorage());
    }

    public static void tearDown() {
        try {
            SaTokenContextForThreadLocalStorage.clearBox();
        } catch (Exception ignored) {
        }
    }

    private static final class MockSaStorage implements SaStorage {
        private final Map<String, Object> storage = new HashMap<>();

        @Override
        public Object getSource() {
            return storage;
        }

        @Override
        public Object get(String key) {
            return storage.get(key);
        }

        @Override
        public SaStorage set(String key, Object value) {
            storage.put(key, value);
            return this;
        }

        @Override
        public SaStorage delete(String key) {
            storage.remove(key);
            return this;
        }
    }

    private static final class MockSaRequest implements SaRequest {
        @Override
        public Object getSource() {
            return this;
        }

        @Override
        public String getParam(String name) {
            return null;
        }

        @Override
        public List<String> getParamNames() {
            return List.of();
        }

        @Override
        public Map<String, String> getParamMap() {
            return Map.of();
        }

        @Override
        public String getHeader(String name) {
            return null;
        }

        @Override
        public String getCookieValue(String name) {
            return null;
        }

        @Override
        public String getRequestPath() {
            return "/test";
        }

        @Override
        public String getUrl() {
            return "/test";
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public Object forward(String path) {
            return null;
        }
    }

    private static final class MockSaResponse implements SaResponse {
        @Override
        public Object getSource() {
            return this;
        }

        @Override
        public SaResponse setStatus(int sc) {
            return this;
        }

        @Override
        public SaResponse setHeader(String name, String value) {
            return this;
        }

        @Override
        public SaResponse addHeader(String name, String value) {
            return this;
        }

        @Override
        public Object redirect(String url) {
            return null;
        }
    }
}

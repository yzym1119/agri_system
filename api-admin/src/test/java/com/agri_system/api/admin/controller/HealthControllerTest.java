package com.agri_system.api.admin.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 健康检查契约：/health 为轻量存活（无依赖 IO），/health/ready 真实探测数据库与 Redis；
 * Redis 探测必须经模板托管回调（连接由模板借还，防泄漏）。
 */
@ExtendWith(MockitoExtension.class)
class HealthControllerTest {
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private StringRedisTemplate redisTemplate;

    @Test
    void livenessIsLightweightAndAlwaysUp() {
        var response = new HealthController(jdbcTemplate, redisTemplate).live();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        // 存活检查不得触碰任何依赖
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate, redisTemplate);
    }

    @Test
    void readinessAllDependenciesUpReturns200() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        lenient().when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        var response = new HealthController(jdbcTemplate, redisTemplate).ready();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("UP", response.getBody().get("database"));
        assertEquals("UP", response.getBody().get("redis"));
    }

    @Test
    void readinessDatabaseFailureReturns503() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenThrow(new RuntimeException("db down"));
        lenient().when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        var response = new HealthController(jdbcTemplate, redisTemplate).ready();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("database"));
    }

    @Test
    void readinessRedisFailureOrAbnormalReplyReturns503() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        // Redis 异常应答（非 PONG）不算健康
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("unexpected");

        var response = new HealthController(jdbcTemplate, redisTemplate).ready();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("redis"));
        assertTrue(response.getBody().get("database").equals("UP"));
    }
}

package com.agri_system.api.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查：区分存活(liveness)与就绪(readiness)两级。
 *
 * <ul>
 *   <li>{@code GET /health}：轻量存活检查，不触碰任何依赖、无 IO。</li>
 *   <li>{@code GET /health/ready}：依赖就绪检查，真实探测数据库与 Redis，任一失败返回 503。</li>
 * </ul>
 *
 * <p>Redis 探测经 {@code redisTemplate.execute(RedisCallback)} 托管回调：
 * 连接的获取与释放由模板负责，杜绝 {@code getConnection()} 裸调用造成的连接泄漏。</p>
 */
@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "存活检查(轻量)与依赖就绪检查(数据库+Redis)")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    @Operation(summary = "存活检查", description = "轻量探活：不校验外部依赖，无 IO 开销")
    public ResponseEntity<Map<String, Object>> live() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "api-client");
        result.put("status", "UP");
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ready")
    @Operation(summary = "就绪检查", description = "真实探测数据库与 Redis，任一失败返回 503")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "api-client");
        result.put("timestamp", LocalDateTime.now());

        String database = checkDatabase();
        String redis = checkRedis();
        result.put("database", database);
        result.put("redis", redis);

        boolean healthy = "UP".equals(database) && "UP".equals(redis);
        result.put("status", healthy ? "UP" : "DOWN");
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    /** 经 RedisTemplate 托管回调执行 PING：连接由模板借还，避免手动 getConnection 泄漏。 */
    private String checkRedis() {
        try {
            String pong = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>)
                    connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}

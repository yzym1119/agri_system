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
 * 健康检查（含依赖探测）。
 *
 * <p>固定 "UP" 不校验依赖会让容器健康检查在数据库、Redis 故障时误报可用。
 * 此处实际探测数据库连通性与 Redis PING；任一依赖失败返回 503。</p>
 */
@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "系统健康检查接口（含数据库与Redis依赖探测）")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    @Operation(summary = "健康检查", description = "返回系统及依赖健康状态")
    public ResponseEntity<Map<String, Object>> health() {
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

    private String checkRedis() {
        try {
            if (redisTemplate.getConnectionFactory() == null) return "DOWN";
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}

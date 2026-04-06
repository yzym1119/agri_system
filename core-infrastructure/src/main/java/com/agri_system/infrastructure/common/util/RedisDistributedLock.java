package com.agri_system.infrastructure.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具类
 *
 * @author AI Generated
 */
@Component
public class RedisDistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLock.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey 锁的key
     * @param requestId 请求标识（通常是UUID或线程ID）
     * @param expireTime 锁的过期时间（秒）
     * @return true=获取成功，false=获取失败
     */
    public boolean tryLock(String lockKey, String requestId, long expireTime) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, requestId, Duration.ofSeconds(expireTime));
        boolean success = Boolean.TRUE.equals(result);
        if (success) {
            logger.info("Successfully acquired distributed lock: key={}, requestId={}", lockKey, requestId);
        } else {
            logger.warn("Failed to acquire distributed lock: key={}, requestId={}", lockKey, requestId);
        }
        return success;
    }

    /**
     * 释放分布式锁
     *
     * @param lockKey 锁的key
     * @param requestId 请求标识
     * @return true=释放成功，false=释放失败（可能是锁已被其他线程持有）
     */
    public boolean releaseLock(String lockKey, String requestId) {
        String currentHolder = redisTemplate.opsForValue().get(lockKey);
        if (requestId.equals(currentHolder)) {
            redisTemplate.delete(lockKey);
            logger.info("Successfully released distributed lock: key={}, requestId={}", lockKey, requestId);
            return true;
        } else {
            logger.warn("Failed to release distributed lock: key={}, requestId={} (held by: {})", lockKey, requestId, currentHolder);
            return false;
        }
    }

    /**
     * 获取锁的当前持有者
     *
     * @param lockKey 锁的key
     * @return 当前持有者的requestId，如果锁不存在则返回null
     */
    public String getLockHolder(String lockKey) {
        return redisTemplate.opsForValue().get(lockKey);
    }
}

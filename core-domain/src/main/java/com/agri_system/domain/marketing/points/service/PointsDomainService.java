package com.agri_system.domain.marketing.points.service;

import com.agri_system.domain.marketing.points.model.PointsGoods;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 积分商城领域服务
 */
public class PointsDomainService {

    private final ConcurrentHashMap<Long, ReentrantLock> goodsLocks = new ConcurrentHashMap<>();

    public Lock lockGoods(Long goodsId) {
        if (goodsId == null) {
            throw new IllegalArgumentException("goodsId 不能为空");
        }
        ReentrantLock lock = goodsLocks.computeIfAbsent(goodsId, id -> new ReentrantLock());
        lock.lock();
        return lock;
    }

    public void unlockGoods(Lock lock) {
        if (lock != null) {
            lock.unlock();
        }
    }

    public void ensureOnSale(PointsGoods goods) {
        if (goods == null || !goods.isOnSale()) {
            throw new IllegalStateException("积分商品未上架或不存在");
        }
    }

    public void ensureStock(PointsGoods goods, int quantity) {
        if (goods == null) {
            throw new IllegalArgumentException("积分商品不存在");
        }
        if (goods.getStock() == null || goods.getStock() < quantity) {
            throw new IllegalArgumentException("积分商品库存不足");
        }
    }
}


package com.agri_system.application.points.support;

import com.agri_system.domain.marketing.points.model.PointsExchangeOrder;
import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import com.agri_system.domain.marketing.points.repository.PointsGoodsRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
/**
 * 测试专用的内存版仓储实现，避免引入真实数据库依赖。
 */
public final class InMemoryPointsRepositorySupport {

    private InMemoryPointsRepositorySupport() {
    }

    /**
     * 内存版积分商品仓储。
     */
    public static class GoodsRepository implements PointsGoodsRepository {

        private final Map<Long, PointsGoods> storage = new ConcurrentHashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        public PointsGoods add(PointsGoods goods) {
            save(goods);
            return goods;
        }

        @Override
        public synchronized PointsGoods save(PointsGoods goods) {
            if (goods.getId() == null) {
                goods.setId(idGenerator.getAndIncrement());
            }
            storage.put(goods.getId(), goods);
            return goods;
        }

        @Override
        public synchronized void deleteById(Long id) {
            storage.remove(id);
        }

        @Override
        public synchronized Optional<PointsGoods> findById(Long id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public synchronized Optional<PointsGoods> findByIdWithLock(Long id) {
            return findById(id);
        }

        @Override
        public synchronized List<PointsGoods> findAllOnSale() {
            return storage.values().stream()
                    .filter(PointsGoods::isOnSale)
                    .sorted(Comparator.comparing(PointsGoods::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(PointsGoods::getId))
                    .collect(Collectors.toList());
        }

        @Override
        public synchronized List<PointsGoods> findAll() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public synchronized List<PointsGoods> findByIds(List<Long> ids) {
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream()
                    .map(storage::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 内存版积分兑换订单仓储。
     */
    public static class OrderRepository implements PointsExchangeOrderRepository {

        private final Map<Long, PointsExchangeOrder> storage = new ConcurrentHashMap<>();
        private final Map<String, PointsExchangeOrder> pickupIndex = new ConcurrentHashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public synchronized PointsExchangeOrder save(PointsExchangeOrder order) {
            if (order.getId() == null) {
                order.setId(idGenerator.getAndIncrement());
            }
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(LocalDateTime.now());
            }
            order.setUpdatedAt(LocalDateTime.now());
            storage.put(order.getId(), order);
            if (order.getPickupCode() != null) {
                pickupIndex.put(order.getPickupCode(), order);
            }
            return order;
        }

        @Override
        public synchronized Optional<PointsExchangeOrder> findById(Long id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public synchronized Optional<PointsExchangeOrder> findByIdWithLock(Long id) {
            return findById(id);
        }

        @Override
        public synchronized Optional<PointsExchangeOrder> findByPickupCode(String pickupCode) {
            return Optional.ofNullable(pickupIndex.get(pickupCode));
        }

        @Override
        public synchronized Optional<PointsExchangeOrder> findByPickupCodeForUpdate(String pickupCode) {
            return findByPickupCode(pickupCode);
        }

        @Override
        public synchronized List<PointsExchangeOrder> findByCustomer(Long customerId, int offset, int limit) {
            return filterAndPage(storage.values().stream()
                    .filter(order -> java.util.Objects.equals(order.getCustomerId(), customerId))
                    .collect(Collectors.toList()), offset, limit);
        }

        @Override
        public synchronized long countByCustomer(Long customerId) {
            return storage.values().stream()
                    .filter(order -> java.util.Objects.equals(order.getCustomerId(), customerId))
                    .count();
        }

        @Override
        public synchronized List<PointsExchangeOrder> query(Long customerId, Integer status, int offset, int limit) {
            return filterAndPage(storage.values().stream()
                    .filter(order -> customerId == null || java.util.Objects.equals(order.getCustomerId(), customerId))
                    .filter(order -> status == null || java.util.Objects.equals(order.getStatus(), status))
                    .collect(Collectors.toList()), offset, limit);
        }

        @Override
        public synchronized long count(Long customerId, Integer status) {
            return storage.values().stream()
                    .filter(order -> customerId == null || java.util.Objects.equals(order.getCustomerId(), customerId))
                    .filter(order -> status == null || java.util.Objects.equals(order.getStatus(), status))
                    .count();
        }

        @Override
        public synchronized long countByGoodsId(Long goodsId) {
            return storage.values().stream()
                    .filter(order -> java.util.Objects.equals(order.getPointsGoodsId(), goodsId))
                    .count();
        }

        @Override
        public synchronized long countActiveByCustomerId(Long customerId) {
            if (customerId == null) {
                return 0;
            }
            // 活跃状态: 10-待审核, 20-备货中, 30-待自提
            Set<Integer> activeStatuses = Set.of(10, 20, 30);
            return storage.values().stream()
                    .filter(order -> java.util.Objects.equals(order.getCustomerId(), customerId))
                    .filter(order -> order.getStatus() != null && activeStatuses.contains(order.getStatus()))
                    .count();
        }

        private List<PointsExchangeOrder> filterAndPage(List<PointsExchangeOrder> source, int offset, int limit) {
            source.sort(Comparator.comparing(PointsExchangeOrder::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed());
            if (offset >= source.size()) {
                return List.of();
            }
            int toIndex = Math.min(source.size(), offset + limit);
            return new ArrayList<>(source.subList(offset, toIndex));
        }
    }
}






package com.agri_system.application.points.service;

import com.agri_system.application.common.PageResult;
import com.agri_system.application.points.dto.*;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.marketing.points.model.PointsExchangeOrder;
import com.agri_system.domain.marketing.points.model.PointsExchangeStatus;
import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import com.agri_system.domain.marketing.points.repository.PointsGoodsRepository;
import com.agri_system.domain.marketing.points.service.PointsDomainService;
import com.agri_system.infrastructure.common.error.SystemErrorCode;
import com.agri_system.infrastructure.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointsFulfillmentService {

    private final PointsExchangeOrderRepository orderRepository;
    private final PointsGoodsRepository goodsRepository;
    private final WalletApplicationService walletService;
    private final PointsDomainService domainService;

    @Transactional(readOnly = true)
    public PageResult<PointsExchangeOrderVO> listOrders(PointsOrderQuery query) {
        int pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 20 : query.getPageSize();
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int offset = (pageNum - 1) * pageSize;
        List<PointsExchangeOrder> orders = orderRepository.query(query.getCustomerId(), query.getStatus(), offset, pageSize);
        long total = orderRepository.count(query.getCustomerId(), query.getStatus());
        Map<Long, PointsGoods> goodsMap = loadGoods(orders);
        List<PointsExchangeOrderVO> vos = orders.stream()
                .map(order -> toOrderVO(order, goodsMap.get(order.getPointsGoodsId())))
                .collect(Collectors.toList());
        return new PageResult<>(total, vos);
    }

    @Transactional
    public void audit(PointsAuditCommand command) {
        PointsExchangeOrder order = orderRepository.findByIdWithLock(command.getOrderId())
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "兑换订单不存在"));
        if (Boolean.TRUE.equals(command.getPass())) {
            order.markAuditPassed();
            order.setRemark(command.getRemark());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            return;
        }

        order.markAuditRejected();
        order.setRemark(command.getRemark());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        PointsGoods goods = goodsRepository.findByIdWithLock(order.getPointsGoodsId())
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "积分商品不存在"));
        Lock lock = domainService.lockGoods(goods.getId());
        try {
            goods.restoreStock(order.getQuantity());
            goodsRepository.save(goods);
        } finally {
            domainService.unlockGoods(lock);
        }

        walletService.refundPoints(order.getCustomerId(),
                BigDecimal.valueOf(order.getPoints()),
                "POINTS_EXCHANGE",
                order.getId(),
                null,
                "积分兑换审核拒绝退回");
    }

    @Transactional
    public void notifyPickup(PointsNotifyCommand command) {
        PointsExchangeOrder order = orderRepository.findByIdWithLock(command.getOrderId())
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "兑换订单不存在"));
        order.markNotify();
        order.setRemark(command.getRemark());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Transactional
    public PointsExchangeOrderVO verify(PointsVerifyCommand command) {
        PointsExchangeOrder order = orderRepository.findByPickupCodeForUpdate(command.getPickupCode())
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "提货码不存在"));
        if (!PointsExchangeStatus.WAITING_PICKUP.equals(order.status())) {
            throw new BizException(SystemErrorCode.DATA_CONFLICT, "订单状态不允许核销");
        }
        order.markFinished();
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        PointsGoods goods = goodsRepository.findById(order.getPointsGoodsId()).orElse(null);
        return toOrderVO(order, goods);
    }

    @Transactional
    public PointsGoodsVO createGoods(PointsGoodsSaveCommand command) {
        PointsGoods goods = new PointsGoods();
        // 如果 code 为空，自动生成
        if (command.getCode() == null || command.getCode().isBlank()) {
            command.setCode(generateGoodsCode());
        }
        applyGoods(goods, command);
        LocalDateTime now = LocalDateTime.now();
        goods.setCreatedAt(now);
        goods.setUpdatedAt(now);
        goodsRepository.save(goods);
        return toGoodsVO(goods);
    }

    @Transactional
    public PointsGoodsVO updateGoods(Long id, PointsGoodsSaveCommand command) {
        PointsGoods goods = goodsRepository.findByIdWithLock(id)
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "积分商品不存在"));
        Lock lock = domainService.lockGoods(goods.getId());
        try {
            applyGoods(goods, command);
            goods.setUpdatedAt(LocalDateTime.now());
            goodsRepository.save(goods);
        } finally {
            domainService.unlockGoods(lock);
        }
        return toGoodsVO(goods);
    }

    @Transactional
    public void deleteGoods(Long id) {
        long orderCount = orderRepository.countByGoodsId(id);
        if (orderCount > 0) {
            throw new BizException(SystemErrorCode.DATA_CONFLICT, "该积分商品已产生兑换记录，不能删除");
        }

        goodsRepository.deleteById(id);
    }

    @Transactional
    public void switchGoods(Long id, boolean onSale) {
        PointsGoods goods = goodsRepository.findByIdWithLock(id)
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "积分商品不存在"));
        Lock lock = domainService.lockGoods(goods.getId());
        try {
            goods.setStatus(onSale ? 1 : 2);
            goodsRepository.save(goods);
        } finally {
            domainService.unlockGoods(lock);
        }
    }

    @Transactional(readOnly = true)
    public List<PointsGoodsVO> listAllGoods() {
        return goodsRepository.findAll().stream()
                .map(this::toGoodsVO)
                .collect(Collectors.toList());
    }

    private void applyGoods(PointsGoods goods, PointsGoodsSaveCommand command) {
        goods.setCode(command.getCode());
        goods.setName(command.getName());
        goods.setPoints(command.getPoints());
        goods.setStock(command.getStock());
        goods.setImageUrl(command.getImageUrl());
        goods.setDescription(command.getDescription());
        goods.setSortOrder(command.getSortOrder() != null ? command.getSortOrder() : 0);
        goods.setStatus(command.getStatus() != null ? command.getStatus() : 1);
    }
    
    /**
     * 生成礼品编码
     */
    private String generateGoodsCode() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "PG" + timestamp + random;
    }

    private Map<Long, PointsGoods> loadGoods(List<PointsExchangeOrder> orders) {
        Set<Long> goodsIds = orders.stream()
                .map(PointsExchangeOrder::getPointsGoodsId)
                .collect(Collectors.toSet());
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        List<PointsGoods> goodsList = goodsRepository.findByIds(List.copyOf(goodsIds));
        return goodsList.stream().collect(Collectors.toMap(PointsGoods::getId, Function.identity()));
    }

    private PointsExchangeOrderVO toOrderVO(PointsExchangeOrder order, PointsGoods goods) {
        PointsExchangeOrderVO vo = new PointsExchangeOrderVO();
        vo.setId(order.getId());
        vo.setExchangeNo(order.getExchangeNo());
        vo.setCustomerId(order.getCustomerId());
        vo.setGoodsId(order.getPointsGoodsId());
        if (goods != null) {
            vo.setGoodsName(goods.getName());
            vo.setGoodsImage(goods.getImageUrl());
        }
        vo.setPoints(order.getPoints());
        vo.setQuantity(order.getQuantity());
        vo.setStatus(order.getStatus());
        PointsExchangeStatus status = order.status();
        vo.setStatusDesc(status != null ? status.getDesc() : "未知状态");
        vo.setPickupCode(order.getPickupCode());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }

    private PointsGoodsVO toGoodsVO(PointsGoods goods) {
        PointsGoodsVO vo = new PointsGoodsVO();
        vo.setId(goods.getId());
        vo.setCode(goods.getCode());
        vo.setName(goods.getName());
        vo.setPoints(goods.getPoints());
        vo.setStock(goods.getStock());
        vo.setImageUrl(goods.getImageUrl());
        vo.setDescription(goods.getDescription());
        vo.setStatus(goods.getStatus());
        vo.setSortOrder(goods.getSortOrder());
        return vo;
    }
}

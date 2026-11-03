package com.agri_system.application.points.service;

import com.agri_system.application.common.PageResult;
import com.agri_system.application.points.dto.PointsExchangeCommand;
import com.agri_system.application.points.dto.PointsExchangeOrderVO;
import com.agri_system.application.points.dto.PointsGoodsVO;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.marketing.points.model.PointsExchangeOrder;
import com.agri_system.domain.marketing.points.model.PointsExchangeStatus;
import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import com.agri_system.domain.marketing.points.repository.PointsGoodsRepository;
import com.agri_system.domain.marketing.points.service.PointsDomainService;
import com.agri_system.domain.common.error.SystemErrorCode;
import com.agri_system.domain.common.exception.BizException;
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
public class PointsMallService {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PointsGoodsRepository goodsRepository;
    private final PointsExchangeOrderRepository orderRepository;
    private final WalletApplicationService walletService;
    private final PointsDomainService domainService;

    @Transactional(readOnly = true)
    public List<PointsGoodsVO> listGoods() {
        return goodsRepository.findAllOnSale()
                .stream()
                .map(this::toGoodsVO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PointsGoodsVO detail(Long goodsId) {
        PointsGoods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "积分商品不存在"));
        return toGoodsVO(goods);
    }

    @Transactional
    public PointsExchangeOrderVO exchange(Long customerId, PointsExchangeCommand command) {
        PointsGoods goods = goodsRepository.findByIdWithLock(command.getGoodsId())
                .orElseThrow(() -> new BizException(SystemErrorCode.DATA_NOT_FOUND, "积分商品不存在"));
        Lock goodsLock = domainService.lockGoods(goods.getId());
        try {
            domainService.ensureOnSale(goods);
            int quantity = command.getQuantity() == null || command.getQuantity() < 1 ? 1 : command.getQuantity();
            domainService.ensureStock(goods, quantity);
            goods.deductStock(quantity);
            goodsRepository.save(goods);

            BigDecimal pointsToDeduct = BigDecimal.valueOf((long) goods.getPoints() * quantity);
            walletService.deductPoints(customerId, pointsToDeduct,
                    "POINTS_EXCHANGE", null, customerId, "积分兑换");

            PointsExchangeOrder order = buildOrder(customerId, goods, quantity, command);
            orderRepository.save(order);
            return toOrderVO(order, goods);
        } finally {
            domainService.unlockGoods(goodsLock);
        }
    }

    @Transactional(readOnly = true)
    public PageResult<PointsExchangeOrderVO> listMyOrders(Long customerId, Integer pageNum, Integer pageSize) {
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int offset = (page - 1) * size;
        List<PointsExchangeOrder> orders = orderRepository.findByCustomer(customerId, offset, size);
        long total = orderRepository.countByCustomer(customerId);
        Map<Long, PointsGoods> goodsMap = loadGoods(orders);
        List<PointsExchangeOrderVO> vos = orders.stream()
                .map(order -> toOrderVO(order, goodsMap.get(order.getPointsGoodsId())))
                .collect(Collectors.toList());
        return new PageResult<>(total, vos);
    }

    private Map<Long, PointsGoods> loadGoods(List<PointsExchangeOrder> orders) {
        Set<Long> goodsIds = orders.stream()
                .map(PointsExchangeOrder::getPointsGoodsId)
                .collect(Collectors.toSet());
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        List<PointsGoods> goodsList = goodsRepository.findByIds(List.copyOf(goodsIds));
        return goodsList.stream()
                .collect(Collectors.toMap(PointsGoods::getId, Function.identity()));
    }

    private PointsExchangeOrder buildOrder(Long customerId, PointsGoods goods, int quantity, PointsExchangeCommand command) {
        PointsExchangeOrder order = new PointsExchangeOrder();
        order.setExchangeNo(generateExchangeNo());
        order.setCustomerId(customerId);
        order.setPointsGoodsId(goods.getId());
        order.setPoints(goods.getPoints() * quantity);
        order.setQuantity(quantity);
        order.setReceiverName(command.getReceiverName());
        order.setReceiverPhone(command.getReceiverPhone());
        order.setReceiverAddress(command.getReceiverAddress());
        order.setStatus(PointsExchangeStatus.PENDING_REVIEW.getCode());
        order.setPickupCode(generatePickupCode());
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    private String generateExchangeNo() {
        return "PE" + NO_FORMAT.format(LocalDateTime.now()) + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private String generatePickupCode() {
        for (int i = 0; i < 5; i++) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
            if (orderRepository.findByPickupCode(code).isEmpty()) {
                return code;
            }
        }
        throw new BizException(SystemErrorCode.SYSTEM_ERROR, "生成提货码失败，请重试");
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

    private PointsExchangeOrderVO toOrderVO(PointsExchangeOrder order, PointsGoods goods) {
        PointsExchangeOrderVO vo = new PointsExchangeOrderVO();
        vo.setId(order.getId());
        vo.setExchangeNo(order.getExchangeNo());
        vo.setCustomerId(order.getCustomerId());
        vo.setGoodsId(order.getPointsGoodsId());
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
        if (goods != null) {
            vo.setGoodsName(goods.getName());
            vo.setGoodsImage(goods.getImageUrl());
        }
        return vo;
    }
}


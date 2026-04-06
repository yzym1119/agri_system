package com.agri_system.application.points.service;

import com.agri_system.application.points.dto.PointsAuditCommand;
import com.agri_system.application.points.support.InMemoryPointsRepositorySupport;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.marketing.points.model.PointsExchangeOrder;
import com.agri_system.domain.marketing.points.model.PointsExchangeStatus;
import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.agri_system.domain.marketing.points.service.PointsDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditRejectTest {

    @Mock
    private WalletApplicationService walletApplicationService;

    private InMemoryPointsRepositorySupport.GoodsRepository goodsRepository;
    private InMemoryPointsRepositorySupport.OrderRepository orderRepository;
    private PointsFulfillmentService pointsFulfillmentService;

    @BeforeEach
    void setUp() {
        goodsRepository = new InMemoryPointsRepositorySupport.GoodsRepository();
        orderRepository = new InMemoryPointsRepositorySupport.OrderRepository();
        PointsDomainService domainService = new PointsDomainService();
        pointsFulfillmentService = new PointsFulfillmentService(orderRepository, goodsRepository, walletApplicationService, domainService);

        PointsGoods goods = new PointsGoods();
        goods.setId(1L);
        goods.setCode("G002");
        goods.setName("审核礼品");
        goods.setPoints(300);
        goods.setStock(0); // 库存已在下单时扣减
        goods.setStatus(1);
        goodsRepository.add(goods);

        PointsExchangeOrder order = new PointsExchangeOrder();
        order.setId(10L);
        order.setExchangeNo("PE20240001");
        order.setCustomerId(9001L);
        order.setPointsGoodsId(1L);
        order.setPoints(300);
        order.setQuantity(1);
        order.setPickupCode("123456");
        order.setStatus(PointsExchangeStatus.PENDING_REVIEW.getCode());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Test
    void auditRejectShouldRestoreStockAndRefundPoints() {
        when(walletApplicationService.refundPoints(eq(9001L),
                eq(BigDecimal.valueOf(300)),
                eq("POINTS_EXCHANGE"),
                eq(10L),
                isNull(),
                eq("积分兑换审核拒绝退回"))).thenReturn(BigDecimal.valueOf(300));

        PointsAuditCommand command = new PointsAuditCommand();
        command.setOrderId(10L);
        command.setPass(false);
        command.setRemark("库存不足驳回");

        pointsFulfillmentService.audit(command);

        assertEquals(PointsExchangeStatus.REJECTED.getCode(),
                orderRepository.findById(10L).orElseThrow().getStatus());
        assertEquals(1, goodsRepository.findById(1L).orElseThrow().getStock());

        verify(walletApplicationService, times(1)).refundPoints(eq(9001L),
                eq(BigDecimal.valueOf(300)),
                eq("POINTS_EXCHANGE"),
                eq(10L),
                isNull(),
                eq("积分兑换审核拒绝退回"));
    }
}






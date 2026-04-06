package com.agri_system.application.points.service;

import com.agri_system.application.points.dto.PointsExchangeCommand;
import com.agri_system.application.points.dto.PointsExchangeOrderVO;
import com.agri_system.application.points.support.InMemoryPointsRepositorySupport;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.marketing.points.model.PointsExchangeStatus;
import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.agri_system.domain.marketing.points.service.PointsDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NormalUserExchangeTest {

    @Mock
    private WalletApplicationService walletApplicationService;

    private InMemoryPointsRepositorySupport.GoodsRepository goodsRepository;
    private InMemoryPointsRepositorySupport.OrderRepository orderRepository;
    private PointsMallService pointsMallService;

    @BeforeEach
    void setUp() {
        goodsRepository = new InMemoryPointsRepositorySupport.GoodsRepository();
        orderRepository = new InMemoryPointsRepositorySupport.OrderRepository();
        PointsDomainService domainService = new PointsDomainService();
        pointsMallService = new PointsMallService(goodsRepository, orderRepository, walletApplicationService, domainService);

        PointsGoods goods = new PointsGoods();
        goods.setId(1L);
        goods.setCode("G001");
        goods.setName("普通礼品");
        goods.setPoints(100);
        goods.setStock(10);
        goods.setStatus(1);
        goodsRepository.add(goods);
    }

    @Test
    void normalUserExchangeSuccess() {
        PointsExchangeCommand command = new PointsExchangeCommand();
        command.setGoodsId(1L);
        command.setQuantity(1);
        command.setReceiverName("张三");
        command.setReceiverPhone("13800000000");
        command.setReceiverAddress("测试地址");

        when(walletApplicationService.deductPoints(eq(1001L),
                eq(BigDecimal.valueOf(100)),
                eq("POINTS_EXCHANGE"),
                isNull(),
                eq(1001L),
                eq("积分兑换"))).thenReturn(BigDecimal.valueOf(900));

        PointsExchangeOrderVO result = pointsMallService.exchange(1001L, command);

        assertNotNull(result.getPickupCode());
        assertEquals(PointsExchangeStatus.PENDING_REVIEW.getCode(), result.getStatus());
        assertEquals(9, goodsRepository.findById(1L).orElseThrow().getStock());
        assertEquals(1, orderRepository.countByCustomer(1001L));

        verify(walletApplicationService, times(1)).deductPoints(eq(1001L),
                eq(BigDecimal.valueOf(100)),
                eq("POINTS_EXCHANGE"),
                isNull(),
                eq(1001L),
                eq("积分兑换"));
    }
}



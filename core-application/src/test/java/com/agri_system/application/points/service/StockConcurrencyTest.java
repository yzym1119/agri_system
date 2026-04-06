package com.agri_system.application.points.service;

import com.agri_system.application.points.dto.PointsExchangeCommand;
import com.agri_system.application.points.support.InMemoryPointsRepositorySupport;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.agri_system.domain.marketing.points.service.PointsDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockConcurrencyTest {

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
        goods.setName("限量礼品");
        goods.setPoints(200);
        goods.setStock(1);
        goods.setStatus(1);
        goodsRepository.add(goods);
    }

    @Test
    void onlyOneExchangeSucceedsWhenStockIsOne() throws Exception {
        when(walletApplicationService.deductPoints(anyLong(),
                any(BigDecimal.class),
                anyString(),
                any(),
                anyLong(),
                anyString())).thenReturn(BigDecimal.ZERO);

        ExecutorService executorService = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long customerId = 2000L + i;
            tasks.add(() -> {
                PointsExchangeCommand command = new PointsExchangeCommand();
                command.setGoodsId(1L);
                command.setQuantity(1);
                command.setReceiverName("客户" + customerId);
                command.setReceiverPhone("1380000" + customerId);
                command.setReceiverAddress("地址" + customerId);
                try {
                    pointsMallService.exchange(customerId, command);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            });
        }

        List<Future<Boolean>> futures = executorService.invokeAll(tasks);
        executorService.shutdown();

        long successCount = futures.stream().filter(future -> {
            try {
                return Boolean.TRUE.equals(future.get());
            } catch (Exception e) {
                return false;
            }
        }).count();

        assertEquals(1, successCount, "库存为1时仅允许一笔兑换成功");
        assertEquals(0, goodsRepository.findById(1L).orElseThrow().getStock());
        assertEquals(1, orderRepository.count(null, null));
        verify(walletApplicationService, times(1)).deductPoints(anyLong(),
                any(BigDecimal.class),
                anyString(),
                any(),
                anyLong(),
                anyString());
    }
}






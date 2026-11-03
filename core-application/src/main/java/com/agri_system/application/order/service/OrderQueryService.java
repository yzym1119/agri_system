package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.domain.order.model.*;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.application.order.service.OrderPaymentDisplayPolicy.PaymentSummary;
import static com.agri_system.application.order.service.OrderPaymentDisplayPolicy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** 订单读取编排；不承载创建、支付、退款或履约写入，不新增事务边界。 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderPaymentRepository paymentRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final OrderPaymentService paymentService;

    public com.mybatisflex.core.paginate.Page<OrderPageVO> queryOrderPage(OrderPageQuery query) {
        // 1. 查询订单分页数据
        com.mybatisflex.core.paginate.Page<Order> page = query.getDeliveryStatus() == null
                ? orderRepository.findPage(query.getPageNum(), query.getPageSize(), query.getKeyword(), query.getStoreId(),
                    query.getOrderType(), query.getDeliveryType(), query.getOrderStatus(), query.getStartTime(), query.getEndTime(), query.getCustomerId())
                : orderRepository.findPage(query.getPageNum(), query.getPageSize(), query.getKeyword(), query.getStoreId(),
                    query.getOrderType(), query.getDeliveryType(), query.getOrderStatus(), query.getStartTime(), query.getEndTime(), query.getCustomerId(), query.getDeliveryStatus());

        List<Long> fullyRefundedIds=page.getRecords().stream().filter(order -> Integer.valueOf(8).equals(order.getOrderStatus()))
                .map(Order::getId).toList();
        java.util.Set<Long> returnableIds=fullyRefundedIds.isEmpty()?java.util.Set.of():itemRepository.findOrderIdsWithReturnableItems(fullyRefundedIds);

        // 只复用本次分页内的显示资料（包含查无结果），不跨请求缓存或改变仓储过滤。
        Map<Long, Optional<Store>> pageStores = new HashMap<>();
        Map<Long, Optional<Customer>> pageCustomers = new HashMap<>();

        // 2. 转换为VO
        List<OrderPageVO> voList = page.getRecords().stream().map(order -> {
            OrderPageVO vo = new OrderPageVO();
            List<OrderPayment> payments = paymentRepository.findByOrderId(order.getId());
            PaymentSummary paymentSummary = summarizePayment(order, payments);
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStoreId(order.getStoreId());
            vo.setCustomerId(order.getCustomerId());
            vo.setOrderType(order.getOrderType());
            OrderDelivery delivery = deliveryRepository.findByOrderId(order.getId()).orElse(null);
            vo.setDeliveryType(delivery != null ? delivery.getDeliveryType()
                    : (java.util.Objects.equals(order.getOrderType(), 2) ? 1 : null));
            vo.setDeliveryStatus(delivery != null ? delivery.getDeliveryStatus() : null);
            vo.setCanShip(com.agri_system.domain.order.service.OrderDeliveryEligibility.canDeliver(order,delivery,true,afterSalesRepository,orderRepository));
            vo.setCanConfirmReceipt(com.agri_system.domain.order.service.OrderDeliveryEligibility.canDeliver(order,delivery,false,afterSalesRepository,orderRepository));
            vo.setOrderStatus(order.getOrderStatus());
            if (Integer.valueOf(8).equals(order.getOrderStatus())) vo.setHasReturnableItems(returnableIds.contains(order.getId()));
            vo.setTotalAmount(order.getTotalAmount());
            vo.setDiscountAmount(order.getDiscountAmount());
            vo.setCouponAmount(order.getCouponAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setReceivedAmount(paymentSummary.receivedAmount);
            vo.setCreditAmount(paymentSummary.creditAmount);
        var finance=paymentService.financialSummary(order);
        vo.setCreditReductionAmount(finance.creditReductionAmount());vo.setCashRefundedAmount(finance.cashRefundedAmount());
        vo.setRemainingCreditAmount(finance.remainingCreditAmount());vo.setRepaidAmount(finance.repaidAmount());
            vo.setReceiverName(order.getReceiverName());
            vo.setReceiverPhone(order.getReceiverPhone());
            vo.setReceiverAddress(order.getReceiverAddress());
            vo.setPickupCode(order.getPickupCode());
            vo.setCreatedAt(order.getCreatedAt());

            // 查询门店名称
            if (order.getStoreId() != null) {
                pageStores.computeIfAbsent(order.getStoreId(), storeRepository::findByIdIncludingDeleted).ifPresent(store -> {
                    vo.setStoreName(store.getName());
                });
            }

            // 查询客户信息
            if (order.getCustomerId() != null) {
                pageCustomers.computeIfAbsent(order.getCustomerId(), customerRepository::findByIdIncludingDeleted).ifPresent(customer -> {
                    vo.setCustomerName(customer.getName());
                    vo.setCustomerPhone(customer.getPhone());
                });
            }

            // 主支付方式（若记录在订单上），若订单表未记录则回退到支付流水的第一条记录
            if (order.getPaymentMethod() != null) {
                vo.setPaymentMethod(order.getPaymentMethod());
                vo.setPaymentMethodName(resolveMainPaymentMethodName(order.getPaymentMethod()));
            } else {
                if (payments != null && !payments.isEmpty()) {
                    OrderPayment firstPayment = payments.get(0);
                    vo.setPaymentMethod(firstPayment.getPaymentType());
                    vo.setPaymentMethodName(resolvePaymentRecordDisplayName(firstPayment));
                }
            }

            // 查询订单明细（仅前几个，用于概览）
            List<OrderItem> items = itemRepository.findByOrderId(order.getId());
            if (items != null && !items.isEmpty()) {
                vo.setItems(items.stream().limit(3).map(item -> {
                    OrderItemVO itemVO = new OrderItemVO();
                    itemVO.setId(item.getId());
                    itemVO.setProductId(item.getProductId());
                    itemVO.setProductName(item.getProductName());
                    itemVO.setQuantity(item.getQuantity());
                    itemVO.setUnit(item.getUnit());
                    itemVO.setUnitPrice(item.getUnitPrice());
                    itemVO.setTotalPrice(item.getTotalPrice());
                    return itemVO;
                }).collect(Collectors.toList()));
            }

            return vo;
        }).collect(Collectors.toList());

        // 3. 构建分页结果
        com.mybatisflex.core.paginate.Page<OrderPageVO> result = new com.mybatisflex.core.paginate.Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());

        return result;
    }

}

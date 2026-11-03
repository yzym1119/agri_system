package com.agri_system.application.order.service;
import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.order.service.OrderRefundHistory.RefundAggregate;
import com.agri_system.domain.store.model.Staff;
import java.math.BigDecimal;
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
    private final com.agri_system.domain.product.repository.ProductRepository productRepository;
    private final com.agri_system.domain.store.repository.StaffRepository staffRepository;
    private final com.agri_system.domain.after_sales.repository.AfterSalesItemRepository afterSalesItemRepository;
    private final com.agri_system.application.inventory.service.InventoryApplicationService inventoryService;

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

    public OrderDetailVO getOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));

        List<OrderItem> items = itemRepository.findByOrderId(orderId);
        List<com.agri_system.domain.order.model.OrderPayment> payments = paymentRepository.findByOrderId(orderId);
        java.util.Map<Long, RefundAggregate> refundAggregates = loadOrderItemRefundAggregates(orderId);

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStoreId(order.getStoreId());
        vo.setCustomerId(order.getCustomerId());
        vo.setOrderType(order.getOrderType());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setCouponAmount(order.getCouponAmount());
        vo.setPayAmount(order.getPayAmount());
        PaymentSummary paymentSummary = summarizePayment(order, payments);
        vo.setReceivedAmount(paymentSummary.receivedAmount);
        vo.setCreditAmount(paymentSummary.creditAmount);
        var finance=paymentService.financialSummary(order);
        vo.setCreditReductionAmount(finance.creditReductionAmount());vo.setCashRefundedAmount(finance.cashRefundedAmount());
        vo.setRemainingCreditAmount(finance.remainingCreditAmount());vo.setRepaidAmount(finance.repaidAmount());
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPaymentMethodName(resolveMainPaymentMethodName(order.getPaymentMethod()));
        vo.setPickupCode(order.getPickupCode());
        vo.setCreatedAt(order.getCreatedAt());

        // 转换明细
        Map<Long, BigDecimal> refundBases = RefundAmountPolicy.refundableBaseAmounts(order, items);
        vo.setItems(items.stream().map(item -> {
            OrderItemVO itemVO = new OrderItemVO();
            itemVO.setId(item.getId());
            itemVO.setProductId(item.getProductId());
            itemVO.setPackageId(item.getPackageId());
            itemVO.setPackageGroupNo(item.getPackageGroupNo());
            itemVO.setPackageName(item.getPackageName());
            itemVO.setPackageQuantity(item.getPackageQuantity());
            itemVO.setPackageUnitPrice(item.getPackageUnitPrice());
            itemVO.setItemType(item.getItemType());
            itemVO.setProductName(item.getProductName());
            itemVO.setQuantity(item.getQuantity());
            itemVO.setUnit(item.getUnit());
            enrichQuantityMetadata(itemVO, item);
            itemVO.setUnitPrice(item.getUnitPrice());
            itemVO.setTotalPrice(item.getTotalPrice());
            RefundAggregate refundAggregate = refundAggregates.get(item.getId());
            BigDecimal refundedQuantity = refundAggregate != null ? refundAggregate.quantity : BigDecimal.ZERO;
            BigDecimal refundedAmount = refundAggregate != null ? refundAggregate.amount : BigDecimal.ZERO;
            itemVO.setRefundedQuantity(refundedQuantity);
            itemVO.setRefundedAmount(refundedAmount);
            itemVO.setRefundableQuantity(safeSubtract(item.getQuantity(), refundedQuantity));
            itemVO.setRefundableAmount(safeSubtract(refundBases.getOrDefault(item.getId(), item.getTotalPrice()), refundedAmount));
            // attempt to enrich with product snapshot data (specification, manufacturer) when available
            try {
                productRepository.findById(item.getProductId()).ifPresent(product -> {
                    // product may have specification and manufacturer fields
                    try {
                        java.lang.reflect.Method mSpec = product.getClass().getMethod("getSpecification");
                        Object specVal = mSpec.invoke(product);
                        if (specVal != null) itemVO.setSpecification(String.valueOf(specVal));
                    } catch (Exception ignore) {}
                    try {
                        java.lang.reflect.Method mManu = product.getClass().getMethod("getManufacturer");
                        Object manuVal = mManu.invoke(product);
                        if (manuVal != null) itemVO.setManufacturer(String.valueOf(manuVal));
                    } catch (Exception ignore) {}
                });
            } catch (Exception ignored) {
                // ignore enrichment errors
            }
            return itemVO;
        }).collect(Collectors.toList()));

        // 转换支付明细
        vo.setPayments(payments.stream().map(payment -> {
            OrderPaymentVO paymentVO = new OrderPaymentVO();
            paymentVO.setId(payment.getId());
            paymentVO.setPaymentType(payment.getPaymentType());
            paymentVO.setPaymentTypeName(resolvePaymentRecordDisplayName(payment));
            paymentVO.setAmount(payment.getAmount());
            paymentVO.setCouponId(payment.getCouponId());
            paymentVO.setOuterTransNo(payment.getOuterTransNo());
            paymentVO.setCreatedAt(payment.getCreatedAt());
            return paymentVO;
        }).collect(Collectors.toList()));

        // 查询门店名称
        if (order.getStoreId() != null) {
            storeRepository.findByIdIncludingDeleted(order.getStoreId()).ifPresent(store -> {
                vo.setStoreName(store.getName());
            });
        }

        // 查询客户信息
        if (order.getCustomerId() != null) {
            customerRepository.findByIdIncludingDeleted(order.getCustomerId()).ifPresent(customer -> {
                vo.setCustomerName(customer.getName());
                vo.setCustomerPhone(customer.getPhone());
            });
        }

        // 查询配送信息
        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> {
            OrderDeliveryVO deliveryVO = new OrderDeliveryVO();
            deliveryVO.setLogisticsCompany(delivery.getLogisticsCompany());
            deliveryVO.setTrackingNo(delivery.getTrackingNo());
            deliveryVO.setDeliveryStatus(delivery.getDeliveryStatus());
            deliveryVO.setDeliveryType(delivery.getDeliveryType());
            deliveryVO.setDeliveryStaffId(delivery.getDeliveryStaffId());
            deliveryVO.setDeliveryAt(delivery.getDeliveryAt());
            deliveryVO.setReceivedAt(delivery.getReceivedAt());
            vo.setCanShip(com.agri_system.domain.order.service.OrderDeliveryEligibility.canDeliver(order,delivery,true,afterSalesRepository,orderRepository));
            vo.setCanConfirmReceipt(com.agri_system.domain.order.service.OrderDeliveryEligibility.canDeliver(order,delivery,false,afterSalesRepository,orderRepository));
            if (vo.isCanConfirmReceipt()) {
                var deliveryItems = orderRepository.findRemainingDeliveryItems(orderId);
                vo.setDeliveryItems(deliveryItems);
                vo.setDeliveryConfirmationKey(deliveryConfirmationKey(order, delivery, deliveryItems));
            }
            if (delivery.getDeliveryStaffId() != null) {
                staffRepository.findById(delivery.getDeliveryStaffId())
                        .filter(Staff::isActive)
                        .filter(staff -> !staff.hasBeenDeleted())
                        .ifPresent(staff -> deliveryVO.setDeliveryStaffName(staff.getName()));
            }
            vo.setDelivery(deliveryVO);
        });

        // 设置收货信息
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        // 操作员仅按原POS员工事实解析；线上createdBy是客户编号。
        vo.setRemark(order.getRemark());
        Long operatorId = Integer.valueOf(1).equals(order.getOrderType())
                ? (order.getStaffId() != null ? order.getStaffId() : order.getCreatedBy()) : null;
        vo.setOperatorStaffId(operatorId);
        if (operatorId != null) {
            try {
                staffRepository.findById(operatorId).ifPresent(staff -> vo.setOperatorName(staff.getName()));
            } catch (Exception ignored) {
                // 姓名补充失败不改变原操作员工编号。
            }
        }

        // 查询售后记录
        List<com.agri_system.domain.after_sales.model.AfterSales> afterSalesList =
                afterSalesRepository.findByOrderId(orderId);
        if (afterSalesList != null && !afterSalesList.isEmpty()) {
            vo.setAfterSalesList(afterSalesList.stream().map(afterSales -> {
                com.agri_system.application.order.dto.vo.AfterSalesVO afterSalesVO =
                        new com.agri_system.application.order.dto.vo.AfterSalesVO();
                afterSalesVO.setId(afterSales.getId());
                afterSalesVO.setAfterSalesNo(afterSales.getAfterSalesNo());
                afterSalesVO.setType(afterSales.getType());
                afterSalesVO.setStatus(afterSales.getStatus());
                afterSalesVO.setRefundAmount(afterSales.getRefundAmount());
                afterSalesVO.setCreditReductionAmount(afterSales.getCreditReductionAmount());afterSalesVO.setCashRefundAmount(afterSales.getCashRefundAmount());
                afterSalesVO.setReason(afterSales.getReason());
                afterSalesVO.setCreatedAt(afterSales.getCreatedAt());
                afterSalesVO.setRefundTime(afterSales.getRefundTime());
                return afterSalesVO;
            }).collect(Collectors.toList()));
        }

        return vo;
    }

    public OrderDetailVO getOrderDetailForCustomer(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        if (order.getCustomerId() == null || !order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权查看此订单");
        }
        return getOrderDetail(orderId);
    }

    private void enrichQuantityMetadata(OrderItemVO vo, OrderItem item) {
        var metadata = inventoryService.returnQuantityMetadata(item);
        if (metadata == null) return;
        vo.setBaseUnitName(metadata.baseUnitName());
        vo.setAllowFractionalBaseQuantity(metadata.allowFractionalBaseQuantity());
        vo.setOriginalToBaseRatio(metadata.originalToBaseRatio());
    }

    private Map<Long,RefundAggregate> loadOrderItemRefundAggregates(Long orderId) {
        return OrderRefundHistory.load(orderId,null,afterSalesRepository,afterSalesItemRepository);
    }
    private String deliveryConfirmationKey(Order order,OrderDelivery delivery,List<com.agri_system.domain.order.model.DeliveryItemQuantity> items) {
        return OrderDeliveryConfirmation.key(order,delivery,items);
    }
    private BigDecimal defaultAmount(BigDecimal value) { return value != null ? value : BigDecimal.ZERO; }
    private BigDecimal safeSubtract(BigDecimal total,BigDecimal used) {
        BigDecimal result=defaultAmount(total).subtract(defaultAmount(used));
        return result.compareTo(BigDecimal.ZERO)<0?BigDecimal.ZERO:result;
    }
}

package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.OrderCreateCommand;
import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.domain.order.model.*;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/** 线上待支付订单创建；不承载POS结算、实际收款或出库。 */
@Service
@RequiredArgsConstructor
public class OnlineOrderCreationService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final OrderDomainService orderDomainService;
    private final InventoryApplicationService inventoryService;
    private final ProductRepository productRepository;
    private final ProductPricingService productPricingService;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;

    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateCommand command, Long customerId) {
        Integer deliveryType = validateAndResolveCreateDeliveryType(command);
        Store store = validateAndLoadCreateStore(command);
        Long finalCustomerId = customerId != null ? customerId : command.getCustomerId();
        Customer customer = loadCreateOrderCustomer(finalCustomerId);
        String receiverName = resolveCreateOrderReceiverName(command, customer);

        // 1. 生成订单号
        String orderNo = orderDomainService.generateOrderNo();

        // 2. 创建订单实体
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStoreId(store.getId());
        order.setCustomerId(finalCustomerId);
        order.setOrderType(command.getOrderType());
        order.setOrderStatus(1); // 待支付
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setReceiverName(receiverName);
        // 小程序下单时，收货地址信息固化到订单的 receiver_* 字段
        order.setReceiverAddress(command.getDeliveryAddress());
        order.setReceiverPhone(command.getDeliveryPhone());
        order.setRemark(command.getRemark());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setCreatedBy(customerId);

        // 3. 创建订单明细并计算总金额
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortOrder = 0;

        for (OrderItemDTO itemDTO : command.getItems()) {
            if (itemDTO.getPackageId() != null || Integer.valueOf(2).equals(itemDTO.getItemType())) {
                throw new IllegalArgumentException("套餐销售价格尚未统一，请按单品开单");
            }
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 商品不存在"));

            String itemUnit = StringUtils.hasText(itemDTO.getUnit()) ? itemDTO.getUnit() : product.getMainUnit();
            inventoryService.validateSaleQuantity(product.getId(), itemUnit, itemDTO.getQuantity());
            // 客户端下单不信任前端传入单价，统一由后端价格体系计算
            BigDecimal unitPrice = productPricingService.resolveOrderItemUnitPrice(
                    product,
                    order.getStoreId(),
                    order.getCustomerId(),
                    itemUnit
            );

            BigDecimal itemTotal = unitPrice.multiply(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            if (itemTotal.signum() <= 0) throw new IllegalArgumentException("明细金额不足一分，请调整销售数量");
            totalAmount = totalAmount.add(itemTotal);

            OrderItem item = new OrderItem();
            item.setOrderId(null); // 先不设置，保存订单后设置
            item.setProductId(itemDTO.getProductId());
            item.setPackageId(itemDTO.getPackageId());
            item.setItemType(1); // 商品
            item.setProductName(product.getName());
            item.setQuantity(itemDTO.getQuantity());
            item.setUnit(itemUnit);
            item.setUnitPrice(unitPrice);
            item.setCostPriceSnapshot(product.getAvgCostPrice());
            item.setTotalPrice(itemTotal);
            item.setAllowSpecialFund(product.getAllowSpecialFund() != null ? product.getAllowSpecialFund() : 0);
            item.setSortOrder(sortOrder++);
            items.add(item);
        }

        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount); // 初始实付金额等于总金额

        // 4. 保存订单
        order = orderRepository.save(order);

        // 5. 保存订单明细
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            itemRepository.save(item);
        }

        if (order.getOrderType() == 3) {
            OrderDelivery delivery = new OrderDelivery();
            delivery.setOrderId(order.getId());
            delivery.setDeliveryNo("DEL" + System.currentTimeMillis());
            delivery.setDeliveryAddress(order.getReceiverAddress());
            delivery.setDeliveryPhone(order.getReceiverPhone());
            delivery.setDeliveryType(deliveryType);
            delivery.setDeliveryStatus(1);
            delivery.setAuditStatus(1);
            delivery.setCreatedAt(LocalDateTime.now());
            delivery.setUpdatedAt(LocalDateTime.now());
            deliveryRepository.save(delivery);
        }

        // 6. 如果是自提订单，生成自提码
        if (command.getOrderType() == 2) {
            String pickupCode = orderDomainService.generatePickupCode();
            order.setPickupCode(pickupCode);
            orderRepository.save(order);
        }

        return order.getId();
    }

    private Integer validateAndResolveCreateDeliveryType(OrderCreateCommand command) {
        if (command.getOrderType() == null || (command.getOrderType() != 2 && command.getOrderType() != 3)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 小程序订单类型仅支持自提或配送");
        }
        if (command.getOrderType() == 2) {
            if (command.getDeliveryType() != null && command.getDeliveryType() != 1) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 自提订单不支持同城或快递配送方式");
            }
            return 1;
        }
        if (!StringUtils.hasText(command.getDeliveryAddress()) || !StringUtils.hasText(command.getDeliveryPhone())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 配送订单必须填写收货地址和联系电话");
        }
        if (command.getDeliveryType() == null) {
            return 3;
        }
        if (command.getDeliveryType() != 2 && command.getDeliveryType() != 3) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 配送方式仅支持同城配送或快递配送");
        }
        return command.getDeliveryType();
    }

    private Store validateAndLoadCreateStore(OrderCreateCommand command) {
        Store store = storeRepository.findById(command.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 门店不存在或已删除"));
        if (!store.isEnabled()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 门店已停用，暂不可下单");
        }
        if (command.getOrderType() != null && command.getOrderType() == 2 && !store.supportsPickup()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 当前门店不支持自提");
        }
        return store;
    }

    private Customer loadCreateOrderCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 客户不存在或已删除"));
        if (!customer.isActive()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 客户账号已停用，暂不可下单");
        }
        return customer;
    }

    private String resolveCreateOrderReceiverName(OrderCreateCommand command, Customer customer) {
        String receiverName = StringUtils.hasText(command.getReceiverName())
                ? command.getReceiverName().trim()
                : null;
        if (!StringUtils.hasText(receiverName) && customer != null && StringUtils.hasText(customer.getName())) {
            receiverName = customer.getName().trim();
        }
        if (command.getOrderType() != null && command.getOrderType() == 3 && !StringUtils.hasText(receiverName)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 配送订单必须填写收货人姓名");
        }
        return receiverName;
    }

}

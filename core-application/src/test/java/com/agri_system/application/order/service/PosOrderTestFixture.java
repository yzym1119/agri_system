package com.agri_system.application.order.service;

import static com.agri_system.application.order.service.OrderPaymentDisplayPolicy.summarizePayment;
import static com.agri_system.application.order.service.OrderPaymentDisplayPolicy.resolveMainPaymentMethodName;
import static com.agri_system.application.order.service.OrderPaymentDisplayPolicy.resolvePaymentRecordDisplayName;
import com.agri_system.application.order.service.OrderPaymentDisplayPolicy.PaymentSummary;
import com.agri_system.application.order.service.OrderRefundHistory.RefundAggregate;
import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderPaymentIntent;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentIntentRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

public final class PosOrderTestFixture {
    private PosOrderTestFixture() {}
    public static OrderApplicationService withPos(OrderApplicationService facade) {
        ReflectionTestUtils.setField(facade,"posOrderCreationService",new PosOrderCreationService(
                (OrderRepository)ReflectionTestUtils.getField(facade,"orderRepository"),
                (OrderItemRepository)ReflectionTestUtils.getField(facade,"itemRepository"),
                (OrderPaymentRepository)ReflectionTestUtils.getField(facade,"paymentRepository"),
                (OrderDeliveryRepository)ReflectionTestUtils.getField(facade,"deliveryRepository"),
                (OrderDomainService)ReflectionTestUtils.getField(facade,"orderDomainService"),
                (OrderPaymentService)ReflectionTestUtils.getField(facade,"paymentService"),
                (InventoryApplicationService)ReflectionTestUtils.getField(facade,"inventoryService"),
                (WalletApplicationService)ReflectionTestUtils.getField(facade,"walletService"),
                (CreditApplicationService)ReflectionTestUtils.getField(facade,"creditService"),
                (ProductRepository)ReflectionTestUtils.getField(facade,"productRepository"),
                (ProductPricingService)ReflectionTestUtils.getField(facade,"productPricingService"),
                (com.agri_system.application.productpackage.service.PackagePricingService)ReflectionTestUtils.getField(facade,"packagePricingService"),
                (StaffRepository)ReflectionTestUtils.getField(facade,"staffRepository"),
                (CustomerRepository)ReflectionTestUtils.getField(facade,"customerRepository"),
                (ApplicationEventPublisher)ReflectionTestUtils.getField(facade,"eventPublisher"),
                (WalletAuthCodeRepository)ReflectionTestUtils.getField(facade,"walletAuthCodeRepository"),
                (PickupFulfillmentService)ReflectionTestUtils.getField(facade,"pickupFulfillmentService"),
                (ManualPriceAuditService)ReflectionTestUtils.getField(facade,"manualPriceAuditService")));
        return facade;
    }
}

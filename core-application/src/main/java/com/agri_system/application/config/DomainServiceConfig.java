package com.agri_system.application.config;

import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.service.CreditDomainService;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.repository.InventoryTransactionRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryMovementRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryBalanceFeatureStatusRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.inventory.service.InventoryDomainService;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import com.agri_system.domain.marketing.points.service.PointsDomainService;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.performance.service.PerformanceDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.service.ProductDomainService;
import com.agri_system.domain.productpackage.service.ProductPackageDomainService;
import com.agri_system.domain.purchase.repository.PurchaseItemRepository;
import com.agri_system.domain.purchase.repository.PurchaseRepository;
import com.agri_system.domain.purchase.service.PurchaseDomainService;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.service.StoreDomainService;
import com.agri_system.domain.system.auth.repository.SysMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域服务配置类
 * 将领域层的服务注册为 Spring Bean
 * 
 * @author AI Generated
 */
@Configuration
public class DomainServiceConfig {
    
    @Bean
    public InventoryDomainService inventoryDomainService(
            InventoryBatchRepository batchRepository,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository transactionRepository) {
        return new InventoryDomainService(batchRepository, inventoryRepository, transactionRepository);
    }

    @Bean
    public BatchBalanceService batchBalanceService(
            InventoryLocationRepository locationRepository,
            InventoryBatchBalanceRepository balanceRepository,
            InventoryMovementRepository movementRepository,
            InventoryReceiptBatchRepository receiptBatchRepository,
            InventoryBalanceFeatureStatusRepository featureStatusRepository,
            InventoryRepository inventoryRepository) {
        return new BatchBalanceService(locationRepository, balanceRepository, movementRepository,
                receiptBatchRepository, featureStatusRepository, inventoryRepository);
    }
    
    @Bean
    public StoreDomainService storeDomainService(
            StaffRepository staffRepository, 
            SysRoleRepository roleRepository,
            SysStaffRoleRepository staffRoleRepository,
            SysMenuRepository menuRepository) {
        return new StoreDomainService(staffRepository, roleRepository, staffRoleRepository, menuRepository);
    }
    
    @Bean
    public PurchaseDomainService purchaseDomainService(
            PurchaseRepository purchaseRepository,
            PurchaseItemRepository itemRepository) {
        return new PurchaseDomainService(purchaseRepository, itemRepository);
    }
    
    @Bean
    public ProductDomainService productDomainService() {
        return new ProductDomainService();
    }
    
    @Bean
    public ProductPackageDomainService productPackageDomainService(ProductRepository productRepository) {
        return new ProductPackageDomainService(productRepository);
    }
    
    @Bean
    public OrderDomainService orderDomainService() {
        return new OrderDomainService();
    }

    @Bean
    public AfterSalesDomainService afterSalesDomainService() {
        return new AfterSalesDomainService();
    }

    @Bean
    public CustomerDomainService customerDomainService(CustomerWalletRepository walletRepository,
                                                       CreditAccountRepository creditAccountRepository,
                                                       OrderRepository orderRepository,
                                                       AfterSalesRepository afterSalesRepository,
                                                       PointsExchangeOrderRepository pointsExchangeOrderRepository) {
        return new CustomerDomainService(
                walletRepository,
                creditAccountRepository,
                orderRepository,
                afterSalesRepository,
                pointsExchangeOrderRepository
        );
    }

    @Bean
    public CreditDomainService creditDomainService(CreditAccountRepository creditAccountRepository) {
        return new CreditDomainService(creditAccountRepository);
    }

    @Bean
    public PerformanceDomainService performanceDomainService() {
        return new PerformanceDomainService();
    }

    @Bean
    public PointsDomainService pointsDomainService() {
        return new PointsDomainService();
    }
}

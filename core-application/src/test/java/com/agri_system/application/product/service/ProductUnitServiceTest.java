package com.agri_system.application.product.service;

import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.repository.ProductUnitRepository;
import com.agri_system.application.product.dto.command.ProductUnitUpsertCommand;
import com.agri_system.application.support.SaTokenTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProductUnitServiceTest {

    @Mock
    private ProductUnitRepository unitRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductUnitService service;

    @BeforeEach
    void setUp() {
        SaTokenTestContext.setUp();
    }

    @AfterEach
    void tearDown() {
        SaTokenTestContext.tearDown();
    }

    @Test
    void convertsOnePurchaseCaseTo288BaseBottlesWithoutChangingOriginalUnit() {
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of(
                base("瓶"), unit("件", "288", true, false)));

        UnitConversionSnapshot snapshot = service.toBaseQuantity(10L, "件", new BigDecimal("1"));

        assertThat(snapshot.baseQuantity()).isEqualByComparingTo("288");
        assertThat(snapshot.unitName()).isEqualTo("件");
        assertThat(snapshot.originalQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void rejectsAUnitDisabledForSaleButDoesNotAssumeWhichUnitIsBase() {
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of(
                base("瓶"), unit("件", "288", true, false)));

        assertThatThrownBy(() -> service.requireEnabledUnit(10L, "件", ProductUnitService.UnitOperation.SALE))
                .hasMessageContaining("不允许销售");
    }

    @Test
    void enforcesPurchaseInventoryAndTransferOperationFlags() {
        ProductUnit unit = base("瓶");
        unit.setAllowPurchase(0);
        unit.setAllowInventory(0);
        unit.setAllowTransfer(0);
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of(unit));
        assertThatThrownBy(() -> service.requireEnabledUnit(10L, "瓶", ProductUnitService.UnitOperation.PURCHASE))
                .hasMessageContaining("不允许采购");
        assertThatThrownBy(() -> service.requireEnabledUnit(10L, "瓶", ProductUnitService.UnitOperation.INVENTORY))
                .hasMessageContaining("不允许库存");
        assertThatThrownBy(() -> service.requireEnabledUnit(10L, "瓶", ProductUnitService.UnitOperation.TRANSFER))
                .hasMessageContaining("不允许调拨");
    }

    @Test
    void rejectsDuplicateEnabledBaseUnitsBeforeWriting() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(new Product()));
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(
                unitCommand("瓶", "1", true), unitCommand("支", "1", true))))
                .hasMessageContaining("基准单位");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void updatesExistingIdInPlaceAndInsertsOnlyNewRows() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(new Product()));
        ProductUnit existing = persisted(101L, 10L, "瓶", 1, 1);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(existing));

        ProductUnitUpsertCommand retained = unitCommand("瓶", "1", true);
        retained.setId(101L);
        ProductUnitUpsertCommand added = unitCommand("件", "288", false);
        service.replaceUnits(10L, List.of(retained, added));

        verify(unitRepository).save(existing);
        verify(unitRepository, times(2)).save(any(ProductUnit.class));
        assertThat(existing.getId()).isEqualTo(101L);
        verify(unitRepository, never()).deleteByProductId(10L);
    }

    @Test
    void removesMissingRowsByDisablingAndKeepsTheirIds() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(new Product()));
        ProductUnit retained = persisted(101L, 10L, "瓶", 1, 1);
        ProductUnit removed = persisted(102L, 10L, "件", 288, 1);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(retained, removed));
        ProductUnitUpsertCommand command = unitCommand("瓶", "1", true);
        command.setId(101L);

        service.replaceUnits(10L, List.of(command));

        assertThat(removed.getId()).isEqualTo(102L);
        assertThat(removed.getStatus()).isZero();
        verify(unitRepository).save(removed);
        verify(unitRepository, never()).deleteByProductId(10L);
    }

    @Test
    void rejectsCrossSkuUnknownAndRepeatedIdsWithoutWriting() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(new Product()));
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(persisted(101L, 10L, "瓶", 1, 1)));
        ProductUnit external = persisted(202L, 20L, "瓶", 1, 1);
        when(unitRepository.findById(202L)).thenReturn(Optional.of(external));
        when(unitRepository.findById(999L)).thenReturn(Optional.empty());

        ProductUnitUpsertCommand crossSku = unitCommand("瓶", "1", true);
        crossSku.setId(202L);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(crossSku)))
                .hasMessageContaining("不属于当前商品");
        ProductUnitUpsertCommand unknown = unitCommand("瓶", "1", true);
        unknown.setId(999L);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(unknown)))
                .hasMessageContaining("不存在");
        ProductUnitUpsertCommand repeated = unitCommand("瓶", "1", true);
        repeated.setId(101L);
        ProductUnitUpsertCommand repeatedAgain = unitCommand("支", "2", false);
        repeatedAgain.setId(101L);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(repeated, repeatedAgain)))
                .hasMessageContaining("重复");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void rejectsDisabledBaseWithNonOneCoefficientBeforeWriting() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(new Product()));
        ProductUnitUpsertCommand disabledBase = unitCommand("瓶", "2", true);
        disabledBase.setStatus(0);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(disabledBase)))
                .hasMessageContaining("系数必须为1");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void managementGetIncludesDisabledPersistedRows() {
        ProductUnit disabled = persisted(101L, 10L, "瓶", 1, 0);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(disabled));
        assertThat(service.getManagementUnits(10L)).containsExactly(disabled);
        assertThat(service.getUnitModelStatus(10L)).isEqualTo("NEW_CHAIN_ENABLED");
    }

    @Test
    void reusesDisabledSameNameRowWhenIdIsOmitted() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(new Product()));
        ProductUnit disabled = persisted(101L, 10L, "瓶", 1, 0);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(disabled));
        service.replaceUnits(10L, List.of(unitCommand("瓶", "1", true)));
        assertThat(disabled.getStatus()).isOne();
        verify(unitRepository).save(disabled);
    }

    @Test
    void projectsLegacyUnitsAsReadOnlyUnconfirmedWithoutPersisting() {
        Product product = new Product();
        product.setId(10L);
        product.setMainUnit("瓶");
        product.setAuxUnit("件");
        product.setMainToAuxRatio(new BigDecimal("288"));
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of());
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        List<ProductUnit> units = service.getUnits(10L);

        assertThat(units).hasSize(2);
        assertThat(units).allMatch(unit -> unit.isReadOnly()
                && "LEGACY_UNCONFIRMED".equals(unit.getProjectionStatus()));
    }

    private static ProductUnit base(String name) {
        return unit(name, "1", true, true);
    }

    private static ProductUnit unit(String name, String coefficient, boolean purchase, boolean sale) {
        ProductUnit unit = new ProductUnit();
        unit.setProductId(10L);
        unit.setUnitName(name);
        unit.setBaseQuantityPerUnit(new BigDecimal(coefficient));
        unit.setIsBaseUnit(0);
        unit.setAllowPurchase(purchase ? 1 : 0);
        unit.setAllowSale(sale ? 1 : 0);
        unit.setAllowInventory(1);
        unit.setAllowTransfer(1);
        unit.setStatus(1);
        return unit;
    }

    private static ProductUnitUpsertCommand unitCommand(String name, String coefficient, boolean base) {
        ProductUnitUpsertCommand command = new ProductUnitUpsertCommand();
        command.setUnitName(name);
        command.setBaseQuantityPerUnit(new BigDecimal(coefficient));
        command.setIsBaseUnit(base ? 1 : 0);
        return command;
    }

    private static ProductUnit persisted(Long id, Long productId, String name, int coefficient, int status) {
        ProductUnit unit = unit(name, Integer.toString(coefficient), true, true);
        unit.setId(id);
        unit.setProductId(productId);
        unit.setStatus(status);
        return unit;
    }
}

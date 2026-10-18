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
    void rejectsFractionalWholeBaseItemsAfterExactConversion() {
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of(base("瓶"), unit("箱", "12", true, true)));
        assertThat(service.toBaseQuantity(10L, "箱", new BigDecimal("0.5")).baseQuantity()).isEqualByComparingTo("6");
        assertThatThrownBy(() -> service.toBaseQuantity(10L, "箱", new BigDecimal("0.1")))
                .hasMessageContaining("整数");
        assertThatThrownBy(() -> service.toBaseQuantity(10L, "瓶", new BigDecimal("1.000000001")))
                .hasMessageContaining("整数");
    }

    @Test
    void allowsMeasuredBaseFractionsButNeverRoundsAwayStoragePrecision() {
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of(base("公斤")));
        assertThat(service.toBaseQuantity(10L, "公斤", new BigDecimal("0.125")).baseQuantity()).isEqualByComparingTo("0.125");
        assertThatThrownBy(() -> service.toBaseQuantity(10L, "公斤", new BigDecimal("0.000000001")))
                .hasMessageContaining("精度");
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
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(
                unitCommand("瓶", "1", true), unitCommand("支", "1", true))))
                .hasMessageContaining("基准单位");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void updatesExistingIdInPlaceAndInsertsOnlyNewRows() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
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
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
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
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
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
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
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
        assertThat(service.getUnitModelStatus(10L)).isEqualTo("NEW_CHAIN_NO_ENABLED_UNITS");
    }

    @Test
    void reusesDisabledSameNameRowWhenIdIsOmitted() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
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
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        List<ProductUnit> units = service.getManagementUnits(10L);

        assertThat(units).hasSize(2);
        assertThat(units).allMatch(unit -> unit.isReadOnly()
                && "LEGACY_UNCONFIRMED".equals(unit.getProjectionStatus()));
    }

    @Test
    void configuredSkuCannotSwitchBaseUnitId() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
        ProductUnit base = persisted(101L, 10L, "瓶", 1, 1);
        ProductUnit other = persisted(102L, 10L, "件", 288, 1);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(base, other));
        ProductUnitUpsertCommand oldBase = unitCommand("瓶", "1", false);
        oldBase.setId(101L);
        ProductUnitUpsertCommand newBase = unitCommand("件", "1", true);
        newBase.setId(102L);

        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(oldBase, newBase)))
                .hasMessageContaining("基准单位");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void configuredSkuCannotRemoveOrDisableCurrentBaseUnit() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
        ProductUnit base = persisted(101L, 10L, "瓶", 1, 1);
        ProductUnit other = persisted(102L, 10L, "件", 288, 1);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(base, other));
        ProductUnitUpsertCommand disabledBase = unitCommand("瓶", "1", true);
        disabledBase.setId(101L);
        disabledBase.setStatus(0);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(disabledBase)))
                .hasMessageContaining("基准单位");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void configuredSkuWithAllUnitsDisabledDoesNotFallbackToLegacy() {
        ProductUnit disabled = persisted(101L, 10L, "瓶", 1, 0);
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of());
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(disabled));

        assertThatThrownBy(() -> service.getUnits(10L)).hasMessageContaining("无启用单位");
        assertThatThrownBy(() -> service.requireEnabledUnit(10L, "瓶", ProductUnitService.UnitOperation.SALE))
                .hasMessageContaining("无启用单位");
        assertThat(service.getUnitModelStatus(10L)).isEqualTo("NEW_CHAIN_NO_ENABLED_UNITS");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void initializesPersistentUnitsWithExactDeclaredConversion() {
        Product product = new Product();
        product.setId(10L);
        product.setMainUnit("箱");
        product.setAuxUnit("瓶");
        product.setMainToAuxRatio(new BigDecimal("20"));
        service.initializeUnits(product);
        verify(unitRepository).save(org.mockito.ArgumentMatchers.argThat(unit ->
                "瓶".equals(unit.getUnitName()) && unit.isBase() && !unit.isReadOnly()));
        verify(unitRepository).save(org.mockito.ArgumentMatchers.argThat(unit ->
                "箱".equals(unit.getUnitName()) && unit.getBaseQuantityPerUnit().compareTo(new BigDecimal("20")) == 0));
    }

    @Test
    void initializesSingleMainUnitAsBase() {
        Product product = new Product();
        product.setId(10L);
        product.setMainUnit("瓶");
        service.initializeUnits(product);
        verify(unitRepository).save(org.mockito.ArgumentMatchers.argThat(unit ->
                "瓶".equals(unit.getUnitName()) && unit.isBase()
                        && unit.getBaseQuantityPerUnit().compareTo(BigDecimal.ONE) == 0));
    }

    @Test
    void rejectsMissingOrInexactAuxiliaryConversionBeforeWriting() {
        Product product = new Product();
        product.setId(10L);
        product.setMainUnit("箱");
        product.setAuxUnit("瓶");
        assertThatThrownBy(() -> service.initializeUnits(product)).hasMessageContaining("比例");
        product.setMainToAuxRatio(new BigDecimal("0.123456789"));
        assertThatThrownBy(() -> service.initializeUnits(product)).hasMessageContaining("精确");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void businessReadsCannotUseUnpersistedLegacyProjection() {
        assertThatThrownBy(() -> service.getUnits(10L)).hasMessageContaining("持久化单位");
    }

    @Test
    void configuredUnitCoefficientCannotBeRedefined() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("瓶")));
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(
                persisted(101L, 10L, "瓶", 1, 1), persisted(102L, 10L, "箱", 20, 1)));
        ProductUnitUpsertCommand base = unitCommand("瓶", "1", true);
        base.setId(101L);
        ProductUnitUpsertCommand box = unitCommand("箱", "24", false);
        box.setId(102L);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(base, box))).hasMessageContaining("换算系数不可变更");
        verify(unitRepository, never()).save(any());
    }

    @Test
    void rejectsDisablingMainUnitWhenAuxiliaryBaseRemainsEnabledBeforeWriting() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("箱")));
        ProductUnit base = persisted(101L, 10L, "瓶", 1, 1);
        ProductUnit main = persisted(102L, 10L, "箱", 20, 1);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(base, main));
        ProductUnitUpsertCommand baseCommand = unitCommand("瓶", "1", true); baseCommand.setId(101L);
        ProductUnitUpsertCommand mainCommand = unitCommand("箱", "20", false); mainCommand.setId(102L); mainCommand.setStatus(0);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(baseCommand, mainCommand)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("主单位不可移除或停用");
        assertThat(base.getStatus()).isOne(); assertThat(main.getStatus()).isOne();
        verify(unitRepository, never()).save(any());
    }

    @Test
    void rejectsOmittingMainUnitWhenAuxiliaryBaseRemainsEnabledBeforeWriting() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("箱")));
        ProductUnit base = persisted(101L, 10L, "瓶", 1, 1);
        ProductUnit main = persisted(102L, 10L, "箱", 20, 1);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(base, main));
        ProductUnitUpsertCommand baseCommand = unitCommand("瓶", "1", true); baseCommand.setId(101L);
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(baseCommand)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("主单位不可移除或停用");
        assertThat(main.getStatus()).isOne(); verify(unitRepository, never()).save(any());
    }

    @Test
    void allowsDisablingOtherUnitWhenMainAndAuxiliaryBaseRemainEnabled() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithMainUnit("箱")));
        ProductUnit base = persisted(101L, 10L, "瓶", 1, 1);
        ProductUnit main = persisted(102L, 10L, "箱", 20, 1);
        ProductUnit other = persisted(103L, 10L, "件", 100, 1);
        when(unitRepository.findByProductId(10L)).thenReturn(List.of(base, main, other));
        ProductUnitUpsertCommand baseCommand = unitCommand("瓶", "1", true); baseCommand.setId(101L);
        ProductUnitUpsertCommand mainCommand = unitCommand("箱", "20", false); mainCommand.setId(102L);
        ProductUnitUpsertCommand otherCommand = unitCommand("件", "100", false); otherCommand.setId(103L); otherCommand.setStatus(0);
        service.replaceUnits(10L, List.of(baseCommand, mainCommand, otherCommand));
        assertThat(main.getStatus()).isOne(); assertThat(base.getStatus()).isOne(); assertThat(other.getStatus()).isZero();
        verify(unitRepository).save(other); verify(unitRepository, times(3)).save(any());
    }

    private static Product productWithMainUnit(String main) {
        Product product = new Product(); product.setId(10L); product.setMainUnit(main); return product;
    }

    private static ProductUnit base(String name) {
        ProductUnit unit = unit(name, "1", true, true);
        unit.setIsBaseUnit(1);
        return unit;
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
        unit.setIsBaseUnit(coefficient == 1 ? 1 : 0);
        unit.setStatus(status);
        return unit;
    }
}

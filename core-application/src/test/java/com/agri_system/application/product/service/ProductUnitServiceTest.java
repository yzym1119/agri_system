package com.agri_system.application.product.service;

import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.repository.ProductUnitRepository;
import com.agri_system.application.product.dto.command.ProductUnitUpsertCommand;
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

@ExtendWith(MockitoExtension.class)
class ProductUnitServiceTest {

    @Mock
    private ProductUnitRepository unitRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductUnitService service;

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
    void rejectsDuplicateEnabledBaseUnitsBeforeWriting() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(new Product()));
        assertThatThrownBy(() -> service.replaceUnits(10L, List.of(
                unitCommand("瓶", "1", true), unitCommand("支", "1", true))))
                .hasMessageContaining("基准单位");
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
}

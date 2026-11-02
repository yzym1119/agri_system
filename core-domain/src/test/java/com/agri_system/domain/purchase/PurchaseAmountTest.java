package com.agri_system.domain.purchase;

import com.agri_system.domain.purchase.model.PurchaseOrderItem;
import com.agri_system.domain.purchase.service.PurchaseDomainService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseAmountTest {
    private PurchaseOrderItem line(String quantity,String price) {
        var item=new PurchaseOrderItem();item.setQuantity(new BigDecimal(quantity));item.setBuyPrice(new BigDecimal(price));return item;
    }
    @Test void fractionalRowsRoundBeforeHeaderSumWithoutChangingUnitCost() {
        var a=line("0.5","0.01");var b=line("0.5","0.01");a.calculateTotalPrice();b.calculateTotalPrice();
        assertEquals(new BigDecimal("0.01"),a.getTotalPrice());
        assertEquals(new BigDecimal("0.02"),new PurchaseDomainService(null,null).calculateTotalAmount(List.of(a,b)));
        assertEquals(new BigDecimal("0.01"),a.getBuyPrice());assertEquals(new BigDecimal("0.5"),a.getQuantity());
    }
    @Test void uncalculatedRowsUseSameHalfUpRule() {
        var item=line("0.15","0.10");
        assertEquals(new BigDecimal("0.02"),new PurchaseDomainService(null,null).calculateTotalAmount(List.of(item)));
        item.calculateTotalPrice();assertEquals(new BigDecimal("0.02"),item.getTotalPrice());
    }
}

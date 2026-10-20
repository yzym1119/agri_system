package com.agri_system.application.purchase.service;

import com.agri_system.application.purchase.dto.query.PurchasePageQuery;
import com.agri_system.application.purchase.dto.vo.PurchaseDetailVO;
import com.agri_system.application.purchase.dto.vo.PurchasePageVO;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceivingReadTest {
    @Test void receivingListCannotBeUsedToQueryCompletedOrDraftOrders() {
        var service=mock(PurchaseApplicationService.class,CALLS_REAL_METHODS);
        var query=new PurchasePageQuery();query.setStatus(3);
        doReturn(new Page<PurchasePageVO>()).when(service).page(query);
        service.receivingPage(query);assertEquals(2,query.getStatus());
    }
    @Test void receivingDetailRejectsNonPendingOrders() {
        var service=mock(PurchaseApplicationService.class,CALLS_REAL_METHODS);
        var detail=new PurchaseDetailVO();detail.setStatus(3);
        doReturn(detail).when(service).getDetail(8L);
        assertThrows(IllegalArgumentException.class,()->service.receivingDetail(8L));
        detail.setStatus(2);assertSame(detail,service.receivingDetail(8L));
    }
}

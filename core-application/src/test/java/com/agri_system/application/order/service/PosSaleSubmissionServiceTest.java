package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.infrastructure.order.repository.PosSaleSubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosSaleSubmissionServiceTest {
    @Test void completedReplayAndChangedContentNeverCreateSecondOrder() throws Exception {
        var f=new Fixture();var c=f.command();var key=UUID.randomUUID().toString();
        when(f.orders.createPOSOrder(c,7L)).thenReturn(42L);
        assertEquals(Long.valueOf(42),f.tx.execute(s->f.service.submit(key,c,7L)));
        assertEquals(Long.valueOf(42),f.tx.execute(s->f.service.submit(key,c,7L)));
        c.setRemark("改内容");assertThrows(IllegalArgumentException.class,()->f.tx.execute(s->f.service.submit(key,c,7L)));
        verify(f.orders,times(1)).createPOSOrder(any(),anyLong());
        assertEquals("COMPLETED",f.service.lookup(key,7L).status());
        assertEquals(42L,f.service.lookup(key,7L).orderId());
    }
    @Test void cancellationCreatesDurableBarrierEvenIfOriginalRequestHasNotArrived() throws Exception {
        var f=new Fixture();String key=UUID.randomUUID().toString();
        assertEquals("UNKNOWN",f.service.lookup(key,7L).status());
        assertEquals("CANCELLED",f.tx.execute(s->f.service.cancel(key,7L)).status());
        assertEquals("CANCELLED",f.tx.execute(s->f.service.cancel(key,7L)).status());
        assertThrows(IllegalArgumentException.class,()->f.tx.execute(s->f.service.submit(key,f.command(),7L)));
        verifyNoInteractions(f.orders);
    }
    @Test void rollbackHasNoSuccessFactAndCancelThenBlocksLateRetry() throws Exception {
        var f=new Fixture();String key=UUID.randomUUID().toString();var c=f.command();
        when(f.orders.createPOSOrder(c,7L)).thenThrow(new IllegalArgumentException("库存不足"));
        assertThrows(IllegalArgumentException.class,()->f.tx.execute(s->f.service.submit(key,c,7L)));
        assertEquals("UNKNOWN",f.service.lookup(key,7L).status());
        assertEquals("CANCELLED",f.tx.execute(s->f.service.cancel(key,7L)).status());
        assertThrows(IllegalArgumentException.class,()->f.tx.execute(s->f.service.submit(key,c,7L)));
        verify(f.orders,times(1)).createPOSOrder(any(),anyLong());
    }
    @Test void completedCancellationReturnsOriginalOrderAndForeignActorsCannotReadOrCancel() throws Exception {
        var f=new Fixture();String key=UUID.randomUUID().toString();var c=f.command();
        when(f.orders.createPOSOrder(c,7L)).thenReturn(42L);f.tx.execute(s->f.service.submit(key,c,7L));
        assertEquals("COMPLETED",f.tx.execute(s->f.service.cancel(key,7L)).status());
        for(long actor:new long[]{8,9}) {
            assertThrows(IllegalArgumentException.class,()->f.service.lookup(key,actor));
            assertThrows(IllegalArgumentException.class,()->f.tx.execute(s->f.service.cancel(key,actor)));
        }
        assertThrows(IllegalArgumentException.class,()->f.service.lookup("not-uuid",7L));
        c.setStoreId(2L);assertThrows(IllegalArgumentException.class,()->f.tx.execute(s->f.service.submit(key,c,7L)));
    }
    @Test void changedActualOrderOwnerRollsBackRequestAndEffects() throws Exception {
        var f=new Fixture();var c=f.command();String key=UUID.randomUUID().toString();
        var other=new com.agri_system.domain.order.model.Order();other.setId(42L);other.setStaffId(7L);other.setStoreId(2L);
        when(f.facts.findById(42L)).thenReturn(Optional.of(other));
        f.jdbc.execute("CREATE TABLE business_effect(id BIGINT PRIMARY KEY)");
        when(f.orders.createPOSOrder(c,7L)).thenAnswer(inv->{f.jdbc.update("INSERT INTO business_effect VALUES(1)");return 42L;});
        assertThrows(IllegalArgumentException.class,()->f.tx.execute(s->f.service.submit(key,c,7L)));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM business_effect",Integer.class));
        assertEquals("UNKNOWN",f.service.lookup(key,7L).status());
    }
    static class Fixture {
        final OrderApplicationService orders=mock(OrderApplicationService.class);
        final StaffRepository staff=mock(StaffRepository.class);
        final TransactionTemplate tx;final PosSaleSubmissionService service;final JdbcTemplate jdbc;
        final com.agri_system.domain.order.repository.OrderRepository facts=mock(com.agri_system.domain.order.repository.OrderRepository.class);
        Fixture() throws Exception {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            jdbc=new JdbcTemplate(ds);
            jdbc.execute(Files.readString(Path.of("../core-infrastructure/src/main/resources/db/migration/V133__pos_sale_submission.sql")));
            tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
            for(long id:new long[]{7,8,9}) {var s=new Staff();s.setId(id);s.setStoreId(id==9?2L:1L);s.setStatus(1);s.setDeleted(0);when(staff.findIdentityById(id)).thenReturn(Optional.of(s));}
            var fact=new com.agri_system.domain.order.model.Order();fact.setId(42L);fact.setStoreId(1L);fact.setStaffId(7L);
            when(facts.findById(42L)).thenReturn(Optional.of(fact));
            service=new PosSaleSubmissionService(new PosSaleSubmissionRepository(jdbc),orders,staff,facts);
        }
        POSOrderCreateCommand command(){var c=new POSOrderCreateCommand();c.setStoreId(1L);c.setStaffId(7L);c.setPaymentType(1);return c;}
    }
}

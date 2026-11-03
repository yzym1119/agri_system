package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.query.InventoryTransferRecordQuery;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferRecordScopeTest {
    private JdbcTemplate jdbc;
    private InventoryApplicationService service;
    private TenantFactory original;
    @BeforeEach void setup() {
        original=TenantManager.getTenantFactory();
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:transfer_scope_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE inventory_transfer(id BIGINT,transfer_no VARCHAR(30),from_store_id BIGINT,to_store_id BIGINT,product_id BIGINT,quantity DECIMAL(18,4),created_by BIGINT,created_at TIMESTAMP,remark VARCHAR(100))");
        jdbc.execute("CREATE TABLE product(id BIGINT,name VARCHAR(100),specification VARCHAR(100))");
        jdbc.execute("CREATE TABLE store(id BIGINT,name VARCHAR(100))");
        jdbc.execute("CREATE TABLE staff(id BIGINT,name VARCHAR(100))");
        jdbc.execute("CREATE TABLE inventory_transfer_detail(transfer_id BIGINT)");
        jdbc.execute("INSERT INTO inventory_transfer VALUES(1,'T1',1,2,99,3,7,'2026-11-01 10:00:00',NULL),(2,'T2',3,1,99,4,7,'2026-11-01 11:00:00',NULL),(3,'T3',2,3,99,5,7,'2026-11-01 12:00:00',NULL)");
        service=mock(InventoryApplicationService.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
    }
    @AfterEach void close(){TenantManager.setTenantFactory(original);jdbc.execute("SHUTDOWN");}
    @Test void 员工默认只见涉及本店的调出与调入且他店过滤不能绕过() {
        TenantManager.setTenantFactory(()->new Object[]{1L});
        var query=new InventoryTransferRecordQuery();query.setPageSize(1);
        var first=service.queryTransferPage(query);
        assertEquals(2,first.getTotalRow());assertEquals(2L,first.getRecords().get(0).getTransferId());
        query.setPageNum(2);assertEquals(1L,service.queryTransferPage(query).getRecords().get(0).getTransferId());
        query.setPageNum(1);query.setFromStoreId(2L);query.setToStoreId(3L);
        assertEquals(0,service.queryTransferPage(query).getTotalRow());
    }
    @Test void 管理员全店但空租户范围不返回全局数据() {
        var query=new InventoryTransferRecordQuery();TenantManager.setTenantFactory(null);
        assertEquals(3,service.queryTransferPage(query).getTotalRow());
        TenantManager.setTenantFactory(()->new Object[0]);
        assertEquals(0,service.queryTransferPage(query).getTotalRow());
    }
}

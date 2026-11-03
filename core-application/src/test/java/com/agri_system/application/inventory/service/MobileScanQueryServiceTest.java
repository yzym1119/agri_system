package com.agri_system.application.inventory.service;

import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 执行真实查询SQL；不以mock候选替代同码、门店和分页行为。 */
class MobileScanQueryServiceTest {
    JdbcTemplate jdbc;
    StaffRepository staff;
    MobileScanQueryService service;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:mobile_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,code VARCHAR(100),barcode VARCHAR(100),name VARCHAR(200),main_unit VARCHAR(50),status INT,deleted INT)");
        jdbc.execute("CREATE TABLE product_unit(id BIGINT PRIMARY KEY,product_id BIGINT,unit_name VARCHAR(50),base_quantity_per_unit DECIMAL(20,8),is_base_unit INT,barcode VARCHAR(100),status INT)");
        jdbc.execute("CREATE TABLE inventory_receipt_batch(id BIGINT PRIMARY KEY,product_id BIGINT,store_id BIGINT,internal_receipt_batch_no VARCHAR(100),supplier_batch_no VARCHAR(100),base_unit_id BIGINT,original_unit_name VARCHAR(50),original_quantity DECIMAL(20,8),production_date DATE,expiry_date DATE)");
        jdbc.execute("CREATE TABLE inventory_location(id BIGINT PRIMARY KEY,store_id BIGINT,code VARCHAR(100),name VARCHAR(100),status INT)");
        jdbc.execute("CREATE TABLE inventory_batch_balance(id BIGINT PRIMARY KEY,store_id BIGINT,product_id BIGINT,batch_id BIGINT,location_id BIGINT,base_quantity DECIMAL(20,8),reserved_base_quantity DECIMAL(20,8))");
        staff=mock(StaffRepository.class); var actor=new Staff();actor.setId(7L);actor.setStatus(1);actor.setStoreId(1L);
        when(staff.findIdentityById(7L)).thenReturn(Optional.of(actor));
        service=new MobileScanQueryService(new com.agri_system.infrastructure.inventory.query.MobileInventoryQueryImpl(jdbc),staff);
        product(1,"P1","商品一","SAME"); product(2,"P2","商品二","SAME");
        jdbc.update("INSERT INTO inventory_location VALUES(1,1,'LOC','本店后仓',1),(2,2,'OTHER','外店秘密',1)");
        batch(10,1,1,"INTERNAL-REAL",12); batch(20,1,2,"INTERNAL-OTHER",20);
    }
    void product(long id,String code,String name,String barcode) {
        jdbc.update("INSERT INTO product VALUES(?,?,?,?, '箱',1,0)",id,code,barcode,name);
        jdbc.update("INSERT INTO product_unit VALUES(?,?, '瓶',1,1,NULL,1),(?,?, '箱',12,0,?,1)",id*10,id,id*10+1,id,barcode);
    }
    void batch(long id,long product,long store,String code,int quantity) {
        jdbc.update("INSERT INTO inventory_receipt_batch VALUES(?,?,?,?,NULL,?,'箱',1,NULL,NULL)",id,product,store,code,product*10);
        jdbc.update("INSERT INTO inventory_batch_balance VALUES(?,?,?,?,?,?,2)",id,store,product,id,store,quantity);
    }
    @Test void duplicateBarcodeRequiresChoiceButDuplicateMainBarcodeIsCollapsed() {
        var r=service.resolve("SAME","INVENTORY_LOOKUP",7L);
        assertEquals("AMBIGUOUS",r.type());assertEquals(2,r.matches().size());assertFalse(r.hasMore());
        assertTrue(r.matches().stream().allMatch(m->m.type().equals("SKU")&&m.unitName().equals("箱")));
    }
    @Test void batchAndLocationRequireActualRecordsNotPrefixOrSkuGuess() {
        assertEquals("RECEIPT_BATCH",service.resolve("INTERNAL-REAL","INVENTORY_LOOKUP",7L).type());
        assertEquals("LOCATION",service.resolve("LOC","INVENTORY_LOOKUP",7L).type());
        assertEquals("UNKNOWN",service.resolve("INTERNAL-FAKE","INVENTORY_LOOKUP",7L).type());
        assertEquals("SKU",service.resolve("P1","INVENTORY_LOOKUP",7L).type());
        jdbc.update("UPDATE product SET barcode='LOC' WHERE id=1");
        assertEquals("AMBIGUOUS",service.resolve("LOC","INVENTORY_LOOKUP",7L).type());
    }
    @Test void foreignLabelsAreIndistinguishableFromUnknownAndFiltersCannotReadForeignStock() {
        assertEquals("UNKNOWN",service.resolve("INTERNAL-OTHER","INVENTORY_LOOKUP",7L).type());
        assertEquals("UNKNOWN",service.resolve("OTHER","INVENTORY_LOOKUP",7L).type());
        assertThrows(IllegalArgumentException.class,()->service.inventory(1L,20L,null,1,7L));
        assertThrows(IllegalArgumentException.class,()->service.locationProducts(2L,1,7L));
    }
    @Test void packagingCollisionAndBoundedCandidatesNeverAutoSelectFirst() {
        jdbc.update("INSERT INTO product_unit VALUES(99,1,'托',120,0,'P1',1)");
        assertEquals(2,service.resolve("P1","INVENTORY_LOOKUP",7L).matches().size());
        for(int i=3;i<25;i++) product(i,"P"+i,"候选"+i,"SAME");
        var r=service.resolve("SAME","INVENTORY_LOOKUP",7L);
        assertEquals("AMBIGUOUS",r.type());assertEquals(20,r.matches().size());assertTrue(r.hasMore());
    }
    @Test void unsupportedPurposeInvalidInputAndInactiveActorAreRejected() {
        for(String bad:List.of("PICKUP","", "SALE")) assertThrows(IllegalArgumentException.class,()->service.resolve("P1",bad,7L));
        assertThrows(IllegalArgumentException.class,()->service.resolve(" ","INVENTORY_LOOKUP",7L));
        assertThrows(IllegalArgumentException.class,()->service.resolve("a".repeat(101),"INVENTORY_LOOKUP",7L));
        when(staff.findIdentityById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,()->service.resolve("P1","INVENTORY_LOOKUP",7L));
        assertThrows(RuntimeException.class,()->service.inventory(1L,null,null,1,7L));
    }
    @Test void stockUsesOnlyOwnLedgerBalancesMainUnitsAndPersistedUnknownDates() {
        var r=service.inventory(1L,null,null,1,7L);
        assertEquals("1",r.storeId());assertEquals("箱",r.mainUnitName());assertEquals("1",r.quantity());
        assertEquals("0.83333333",r.unreservedQuantity());assertEquals(1,r.totalRows());
        var row=r.rows().get(0);assertEquals("12",row.baseQuantity());assertEquals("瓶",row.baseUnitName());
        assertNull(row.productionDate());assertNull(row.expiryDate());
        assertEquals("INTERNAL-REAL",row.internalBatchNo());
        assertEquals("1",service.inventory(1L,10L,1L,1,7L).quantity());
    }
    @Test void paginationTotalsIncludeOffPageRowsAndNeverOtherStores() {
        for(int i=30;i<62;i++) batch(i,1,1,"B"+i,12);
        var first=service.inventory(1L,null,null,1,7L);var next=service.inventory(1L,null,null,2,7L);
        assertEquals(30,first.rows().size());assertEquals(3,next.rows().size());
        assertEquals(33,first.totalRows());assertEquals("33",first.quantity());assertEquals("33",next.quantity());
        assertThrows(IllegalArgumentException.class,()->service.inventory(1L,null,null,3,7L));
    }
    @Test void brokenBaseEvidenceDoesNotGuessCurrentUnitAndEmptyStockRemainsZero() {
        jdbc.update("UPDATE inventory_receipt_batch SET base_unit_id=999 WHERE id=10");
        var r=service.inventory(1L,null,null,1,7L);
        assertNull(r.quantity());assertNull(r.unreservedQuantity());assertNull(r.rows().get(0).baseUnitName());
        assertEquals("0",service.inventory(2L,null,null,1,7L).quantity());
    }
    @Test void locationListsActualStockProductsAndSearchEscapesLiteralWildcards() {
        assertEquals("1",service.locationProducts(1L,1,7L).matches().get(0).productId());
        assertEquals(0,service.searchProducts("%",1,7L).matches().size());
        assertEquals(2,service.searchProducts("商品",1,7L).matches().size());
        jdbc.update("UPDATE product SET status=2 WHERE id=1");
        assertFalse(service.resolve("P1","INVENTORY_LOOKUP",7L).matches().get(0).enabled());
        assertFalse(service.inventory(1L,null,null,1,7L).productEnabled());
    }
}

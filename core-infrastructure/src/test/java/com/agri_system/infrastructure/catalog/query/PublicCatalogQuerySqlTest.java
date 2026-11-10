package com.agri_system.infrastructure.catalog.query;

import org.junit.jupiter.api.*;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicCatalogQuerySqlTest {
    JdbcTemplate jdbc; PublicCatalogQueryImpl query;
    @BeforeEach void setup(){
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:public_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE store(id BIGINT PRIMARY KEY,name VARCHAR(100),phone VARCHAR(30),address VARCHAR(200),status INT,deleted INT,client_visible INT DEFAULT 0,client_publication_version BIGINT DEFAULT 1)");
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,name VARCHAR(100),code VARCHAR(100),specification VARCHAR(100),manufacturer VARCHAR(100),registration_no VARCHAR(100),image_url VARCHAR(1000),detail_text TEXT,storage_conditions VARCHAR(200),shelf_life_days INT,status INT,deleted INT,is_online INT,avg_cost_price DECIMAL(18,2),min_price DECIMAL(18,2),remark VARCHAR(200))");
        query=new PublicCatalogQueryImpl(jdbc);
    }
    @AfterEach void close(){jdbc.execute("SHUTDOWN");}
    @Test void internalEnabledStoresAreNotPublicUntilExplicitlyPublished(){
        jdbc.update("INSERT INTO store VALUES(1,'内部门店','13800000000','内部地址',1,0,0,1),(2,'公开门店','13800000001','真实公开地址',1,0,1,2),(3,'停用店','13800000002','地址',2,0,1,2),(4,'已删店','13800000003','地址',1,1,1,2)");
        var rows=query.stores(null);assertEquals(List.of("2"),rows.stream().map(r->r.storeId()).toList());assertEquals("真实公开地址",rows.get(0).address());
        jdbc.update("UPDATE store SET client_visible=0 WHERE id=2");assertTrue(query.stores(null).isEmpty());
    }
    @Test void productListAndDetailUseTheSameOnlineActiveNotDeletedGuard(){
        product(1,"公开",1,1,0);product(2,"草稿",0,1,0);product(3,"停用",1,2,0);product(4,"已删",1,1,1);
        assertEquals(List.of("1"),query.products(null,null).stream().map(r->r.productId()).toList());
        assertTrue(query.product(1L).isPresent());for(long id:new long[]{2,3,4,9})assertTrue(query.product(id).isEmpty());
        jdbc.update("UPDATE product SET is_online=0 WHERE id=1");assertTrue(query.product(1L).isEmpty());
    }
    @Test void cursorAndLiteralSearchAreAppliedBeforeBoundedPaging(){
        for(int i=1;i<=30;i++)product(i,"商品"+i,i%2==0?1:0,1,0);
        assertEquals(15,query.products(null,null).size());assertEquals("12",query.products(10L,null).get(0).productId());
        product(31,"50%配方_甲",1,1,0);product(32,"50配方乙",1,1,0);
        assertEquals(List.of("31"),query.products(null,"%配方_").stream().map(r->r.productId()).toList());
        for(int i=40;i<70;i++)product(i,"公开商品",1,1,0);assertEquals(21,query.products(null,null).size());
    }
    @Test void publicRecordsHaveOnlyWhitelistedFieldsAndNoInternalPriceOrStaffData(){
        var productNames=Arrays.stream(com.agri_system.domain.catalog.query.PublicCatalogQuery.ProductView.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        assertEquals(List.of("productId","name","specification","manufacturer","registrationNo","imageUrl","detailText","storageConditions","shelfLifeDays"),productNames);
        var storeNames=Arrays.stream(com.agri_system.domain.catalog.query.PublicCatalogQuery.StoreView.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        assertEquals(List.of("storeId","name","phone","address"),storeNames);
    }
    void product(long id,String name,int online,int status,int deleted){jdbc.update("INSERT INTO product(id,name,code,status,deleted,is_online,avg_cost_price,min_price,remark) VALUES(?,?,?,?,?,?,99,88,'内部备注')",id,name,"SKU"+id,status,deleted,online);}
}

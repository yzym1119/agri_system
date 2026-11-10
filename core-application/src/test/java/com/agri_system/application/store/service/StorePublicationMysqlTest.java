package com.agri_system.application.store.service;
import com.agri_system.application.config.JdbcAccessConfig;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import com.agri_system.infrastructure.store.mapper.StoreMapper;
import com.agri_system.infrastructure.store.repository.StoreRepositoryImpl;
import com.agri_system.infrastructure.store.repository.StorePublicationRepositoryImpl;
import com.agri_system.infrastructure.system.log.mapper.SysOperationLogMapper;
import com.agri_system.infrastructure.system.log.repository.SysOperationLogRepositoryImpl;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.*;
import com.mybatisflex.core.tenant.*;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="mysql.it",matches="true")
class StorePublicationMysqlTest {
    JdbcTemplate server,jdbc;String schema;boolean created;TenantFactory previous;TransactionTemplate tx;StorePublicationService service;StorePublicationRepositoryImpl rows;StoreRepositoryImpl stores;StaffRepository staff;SysRoleRepository roles;
    @BeforeEach void setup()throws Exception{
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        String base="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
        server=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));schema="agri_publication_it_"+UUID.randomUUID().toString().replace("-","");server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");created=true;
        var source=new DriverManagerDataSource(base+schema+options,user,password);var init=new JdbcTemplate(source);init.execute("CREATE TABLE staff(id BIGINT PRIMARY KEY)");
        try(var c=source.getConnection()){for(String file:List.of("V1__store.sql","V46__staff_rbac_and_log.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/"+file));}
        init.execute("ALTER TABLE store ADD deleted INT NOT NULL DEFAULT 0,ADD support_pickup INT DEFAULT 0");init.update("INSERT INTO store(id,code,name,phone,address) VALUES(1,'FIXTURE','虚构门店','13800000000','虚构公开地址')");
        try(var c=source.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V147__client_store_publication.sql"));}
        var config=new FlexConfiguration(new Environment("publication",new SpringManagedTransactionFactory(),new FlexDataSource("publication",source,false)));config.setMapUnderscoreToCamelCase(true);var factory=new FlexSqlSessionFactoryBuilder().build(config);config.addMapper(StoreMapper.class);config.addMapper(SysOperationLogMapper.class);config.addMapper(Probe.class);var mapper=new SqlSessionTemplate(factory);jdbc=new JdbcTemplate(config.getEnvironment().getDataSource());tx=new TransactionTemplate(new JdbcAccessConfig().transactionManager(factory));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        rows=new StorePublicationRepositoryImpl(jdbc);stores=new StoreRepositoryImpl(mapper.getMapper(StoreMapper.class));staff=mock(StaffRepository.class);roles=mock(SysRoleRepository.class);var actor=new Staff();actor.setId(9L);actor.setStoreId(1L);actor.setStatus(1);actor.setDeleted(0);when(staff.findIdentityById(9L)).thenReturn(Optional.of(actor));when(roles.findRoleCodesByStaffId(9L)).thenReturn(List.of("MANAGER"));service=new StorePublicationService(rows,staff,roles,new SysOperationLogRepositoryImpl(mapper.getMapper(SysOperationLogMapper.class)));
        tx.execute(s->{assertEquals(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class),mapper.getMapper(Probe.class).id());assertEquals("READ-COMMITTED",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));return null;});
    }
    interface Probe {@org.apache.ibatis.annotations.Select("SELECT CONNECTION_ID()") Long id();}
    @AfterEach void close(){TenantManager.setTenantFactory(previous);if(created){server.execute("DROP DATABASE `"+schema+"`");created=false;}}
    @Test void migrationIsPrivateByDefaultAndDoesNotGrantRolesOrChangeBusinessFields(){
        assertEquals(0,jdbc.queryForObject("SELECT client_visible FROM store WHERE id=1",Integer.class));assertEquals(1L,jdbc.queryForObject("SELECT client_publication_version FROM store WHERE id=1",Long.class));assertEquals("13800000000",jdbc.queryForObject("SELECT phone FROM store WHERE id=1",String.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE permission='store:publish'",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu",Integer.class));
    }
    @Test void publicFlagAndAuditCommitTogetherAndOrdinaryStoreSaveCannotOverwriteFlag(){
        var oldSnapshot=stores.findById(1L).orElseThrow();var preview=service.preview(9L,1L);tx.execute(s->service.setVisible(9L,1L,true,preview.version(),preview.fingerprint()));
        tx.execute(s->{oldSnapshot.setName("虚构更新门店");stores.save(oldSnapshot);return null;});
        assertEquals(1,jdbc.queryForObject("SELECT client_visible FROM store WHERE id=1",Integer.class));assertEquals(2L,jdbc.queryForObject("SELECT client_publication_version FROM store WHERE id=1",Long.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE staff_id=9",Integer.class));assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.setVisible(9L,1L,false,preview.version(),preview.fingerprint())));
    }
    @Test void auditFailureRollsBackJdbcPublicationThroughActualFlexTransaction(){
        var fail=mock(SysOperationLogRepository.class);when(fail.save(any())).thenThrow(new IllegalStateException("虚构审计故障"));var guarded=new StorePublicationService(rows,staff,roles,fail);var p=guarded.preview(9L,1L);
        assertThrows(IllegalStateException.class,()->tx.execute(s->guarded.setVisible(9L,1L,true,p.version(),p.fingerprint())));assertEquals(0,jdbc.queryForObject("SELECT client_visible FROM store WHERE id=1",Integer.class));assertEquals(1L,jdbc.queryForObject("SELECT client_publication_version FROM store WHERE id=1",Long.class));
    }
    @Test void concurrentPublishFromOneVersionWritesOneAuditAndOneVersion()throws Exception{
        var p=service.preview(9L,1L);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{var futures=new ArrayList<java.util.concurrent.Future<Boolean>>();for(int n=0;n<2;n++)futures.add(pool.submit(()->{start.await();try{tx.execute(s->service.setVisible(9L,1L,true,p.version(),p.fingerprint()));return true;}catch(IllegalArgumentException stale){return false;}}));start.countDown();int accepted=0;for(var f:futures)if(f.get(10,java.util.concurrent.TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);assertEquals(2L,jdbc.queryForObject("SELECT client_publication_version FROM store WHERE id=1",Long.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log",Integer.class));}
        finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test void publicReadUsesTheSameRealStoreFlagAndMysqlLiteralProductFilters(){
        var query=new com.agri_system.infrastructure.catalog.query.PublicCatalogQueryImpl(jdbc);assertTrue(query.stores(null).isEmpty());var p=service.preview(9L,1L);tx.execute(s->service.setVisible(9L,1L,true,p.version(),p.fingerprint()));assertEquals("虚构门店",query.stores(null).get(0).name());
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,name VARCHAR(100),code VARCHAR(100),specification VARCHAR(100),manufacturer VARCHAR(100),registration_no VARCHAR(100),image_url VARCHAR(1000),detail_text TEXT,storage_conditions VARCHAR(200),shelf_life_days INT,status INT,deleted INT,is_online INT)");
        jdbc.update("INSERT INTO product(id,name,code,status,deleted,is_online) VALUES(1,'测试50%_配方','SKU1',1,0,1),(2,'未上架','SKU2',1,0,0),(3,'停用','SKU3',2,0,1)");assertEquals(List.of("1"),query.products(null,"%_").stream().map(v->v.productId()).toList());assertTrue(query.product(2L).isEmpty());
        var current=service.preview(9L,1L);tx.execute(s->service.setVisible(9L,1L,false,current.version(),current.fingerprint()));assertTrue(query.stores(null).isEmpty());
    }
}

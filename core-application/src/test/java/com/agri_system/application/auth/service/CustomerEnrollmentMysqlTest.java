package com.agri_system.application.auth.service;

import com.agri_system.application.config.JdbcAccessConfig;
import com.agri_system.application.customer.service.CustomerRegistrationService;
import com.agri_system.application.customer.dto.CustomerCreateCommand;
import com.agri_system.infrastructure.auth.repository.CustomerIdentityRepositoryImpl;
import com.agri_system.infrastructure.auth.repository.CustomerIdentityEnrollmentRepositoryImpl;
import com.agri_system.infrastructure.customer.repository.CustomerRepositoryImpl;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.wallet.repository.CustomerWalletRepositoryImpl;
import com.agri_system.infrastructure.wallet.mapper.CustomerWalletMapper;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.model.Staff;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantManager;
import com.mybatisflex.core.tenant.TenantFactory;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际V145、同Flex JDBC/MyBatis、客户/钱包/身份/申请落库及并发；平台证据为替身。 */
@EnabledIfSystemProperty(named="mysql.it",matches="true")
class CustomerEnrollmentMysqlTest {
    String schema;boolean created;JdbcTemplate server,jdbc;TransactionTemplate tx;TenantFactory previous;
    CustomerIdentityRepositoryImpl identities;CustomerIdentityEnrollmentRepositoryImpl requests;
    CustomerRepositoryImpl customers;CustomerRegistrationService creator;StaffRepository staff;
    static final String APP="wx1111111111111111",PHONE="13800000000";
    @BeforeEach void setup()throws Exception {
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        String base="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/";
        String options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useAffectedRows="+System.getProperty("mysql.it.useAffectedRows","false"),user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
        server=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));schema="agri_enrollment_it_"+UUID.randomUUID().toString().replace("-","");server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");created=true;
        var source=new DriverManagerDataSource(base+schema+options,user,password);jdbc=new JdbcTemplate(source);
        try(var c=source.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V3__customer.sql"));ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V8__wallet.sql"));}
        jdbc.execute("ALTER TABLE customer ADD company_name VARCHAR(100),ADD tax_id VARCHAR(50),ADD invoice_title VARCHAR(100),ADD invoice_bank VARCHAR(100),ADD invoice_address VARCHAR(500),ADD invoice_phone VARCHAR(40),ADD land_area DECIMAL(16,4),ADD crop_type VARCHAR(100),ADD version INT DEFAULT 0,ADD deleted INT DEFAULT 0,ADD total_order_count INT DEFAULT 0,ADD total_order_amount DECIMAL(16,2) DEFAULT 0,ADD last_order_time DATETIME,ADD bind_staff_id BIGINT");
        jdbc.execute("ALTER TABLE customer_wallet ADD frozen_balance DECIMAL(10,2) NOT NULL DEFAULT 0");
        jdbc.execute("CREATE TABLE customer_identity(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT NOT NULL,identity_type VARCHAR(20) NOT NULL,identity_value VARCHAR(200) NOT NULL,session_key VARCHAR(255),status INT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_identity_type_value(identity_type,identity_value),FOREIGN KEY(customer_id) REFERENCES customer(id))");
        jdbc.execute("CREATE TABLE sys_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,parent_id BIGINT,name VARCHAR(80),permission VARCHAR(80),type INT,path VARCHAR(100),icon VARCHAR(40),sort_order INT,visible INT)");
        try(var c=source.getConnection()){for(String file:List.of("V143__wechat_identity_app_scope.sql","V144__customer_identity_auth_version.sql","V145__customer_identity_enrollment.sql","V146__customer_auth_version.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/"+file));}
        var config=new FlexConfiguration(new Environment("enrollment",new SpringManagedTransactionFactory(),new FlexDataSource("enrollment",source,false)));config.setMapUnderscoreToCamelCase(true);
        // 与生产一样先初始化Flex全局配置，再注册实体Mapper；否则首次mapper得到NoKeyGenerator。
        var factory=new FlexSqlSessionFactoryBuilder().build(config);config.addMapper(CustomerMapper.class);config.addMapper(CustomerWalletMapper.class);config.addMapper(ConnectionProbe.class);var mapper=new SqlSessionTemplate(factory);jdbc=new JdbcTemplate(config.getEnvironment().getDataSource());
        tx=new TransactionTemplate(new JdbcAccessConfig().transactionManager(factory));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        customers=new CustomerRepositoryImpl(mapper.getMapper(CustomerMapper.class),jdbc);creator=new CustomerRegistrationService(customers,new CustomerWalletRepositoryImpl(mapper.getMapper(CustomerWalletMapper.class)));
        identities=new CustomerIdentityRepositoryImpl(jdbc);requests=new CustomerIdentityEnrollmentRepositoryImpl(jdbc);staff=mock(StaffRepository.class);
        for(long id:new long[]{1,2}){var actor=new Staff();actor.setId(id);actor.setStatus(1);actor.setDeleted(0);when(staff.findIdentityById(id)).thenReturn(Optional.of(actor));}
        tx.execute(status->{assertEquals(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class),mapper.getMapper(ConnectionProbe.class).id());assertEquals("READ-COMMITTED",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));return null;});
    }
    interface ConnectionProbe {@org.apache.ibatis.annotations.Select("SELECT CONNECTION_ID()")Long id();}
    @AfterEach void close(){TenantManager.setTenantFactory(previous);if(created){server.execute("DROP DATABASE `"+schema+"`");created=false;}}
    ClientOnboardingService proof(String open){var service=mock(ClientOnboardingService.class);when(service.requireVerifiedPhone()).thenReturn(new ClientOnboardingService.VerifiedPhone(APP,open,PHONE,java.time.Instant.now().getEpochSecond()));when(service.requireRegistrationSubject()).thenReturn(new ClientOnboardingService.RegistrationSubject(APP,open));return service;}
    CustomerEnrollmentService enrollment(String open){return new CustomerEnrollmentService(proof(open),requests,identities,customers,creator,tx.getTransactionManager());}
    CustomerEnrollmentService.Register command(){return new CustomerEnrollmentService.Register(UUID.randomUUID().toString(),"验收农户");}
    CustomerEnrollmentService.Result register(String open,CustomerEnrollmentService.Register command){return enrollment(open).register(command);}
    CustomerEnrollmentReviewService review(){return new CustomerEnrollmentReviewService(requests,identities,customers,staff);}
    long existing(String phone){var command=new CustomerCreateCommand();command.setName("原农户");command.setPhone(phone);return tx.execute(status->creator.create(command).getId());}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void customerDisableAndRestoreMustNotResurrectOldWechatSession(){
        var input=command();register("CUSTOMER_LIFECYCLE",input);
        var oldContext=cn.dev33.satoken.SaManager.getSaTokenContext();com.agri_system.application.support.SaTokenTestContext.setUp();
        try {
            var auth=new AuthService(staff,customers,identities,mock(WechatService.class),mock(com.agri_system.domain.system.auth.repository.SysRoleRepository.class),mock(com.agri_system.application.staff.service.StaffAuthorityService.class),mock(org.springframework.data.redis.core.StringRedisTemplate.class),new org.springframework.mock.env.MockEnvironment().withProperty("spring.profiles.active","test"));
            org.springframework.test.util.ReflectionTestUtils.setField(auth,"wechatAppId",APP);
            var platform=new WechatService.WechatLoginResult();platform.setAppId(APP);platform.setOpenId("CUSTOMER_LIFECYCLE");platform.setSessionKey("IT_SESSION_KEY");
            tx.execute(status->auth.loginVerifiedWechat(platform));assertEquals(1L,auth.getCurrentCustomerId());
            tx.execute(status->{var customer=customers.findById(1L).orElseThrow();customer.setStatus(2);customers.save(customer);return null;});
            assertThrows(IllegalArgumentException.class,auth::getCurrentCustomerId);
            tx.execute(status->{var customer=customers.findById(1L).orElseThrow();customer.setStatus(1);customers.save(customer);return null;});
            assertThrows(IllegalArgumentException.class,auth::getCurrentCustomerId,"主档恢复启用不能复活停用前token");
            tx.execute(status->auth.loginVerifiedWechat(platform));assertEquals(1L,auth.getCurrentCustomerId());assertEquals(1L,identities.findById(1L).orElseThrow().getAuthVersion());assertEquals(3,count("customer_wallet"));
        } finally {com.agri_system.application.support.SaTokenTestContext.tearDown();cn.dev33.satoken.SaManager.setSaTokenContext(oldContext);}
    }
    @Test void customerMetadataDoesNotRevokeDevicesButStaleSnapshotCannotUndoDisable(){
        long id=existing(PHONE);var stale=customers.findById(id).orElseThrow();assertEquals(1L,stale.getAuthVersion());
        tx.execute(status->{var current=customers.findById(id).orElseThrow();current.setName("新资料");customers.save(current);return null;});assertEquals(1L,customers.findAuthStateById(id).orElseThrow().authVersion());
        tx.execute(status->{var current=customers.findById(id).orElseThrow();current.setStatus(2);customers.save(current);return null;});
        stale.setName("陈旧覆盖");assertThrows(IllegalStateException.class,()->tx.execute(status->customers.save(stale)));
        var current=customers.findById(id).orElseThrow();assertEquals(2,current.getStatus());assertEquals(2L,current.getAuthVersion());assertEquals("新资料",current.getName());
    }
    @Test void logicalDeletionRevokesOnceAndCannotBeUndoneByOrdinarySave(){
        long id=existing(PHONE);var stale=customers.findById(id).orElseThrow();
        tx.execute(status->{customers.logicDelete(id);return null;});var state=customers.findAuthStateById(id).orElseThrow();assertEquals(1,state.deleted());assertEquals(2L,state.authVersion());assertTrue(customers.findById(id).isEmpty());
        tx.execute(status->{customers.logicDelete(id);return null;});assertEquals(2L,customers.findAuthStateById(id).orElseThrow().authVersion());
        assertThrows(IllegalStateException.class,()->tx.execute(status->customers.save(stale)));assertEquals(3,count("customer_wallet"));
    }
    @Test void authorityChangeAndOrdinaryMetadataRollBackTogetherOnActualFlexConnection(){
        long id=existing(PHONE);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{var c=customers.findById(id).orElseThrow();c.setStatus(2);c.setName("不能提交的修改");customers.save(c);assertEquals(2L,customers.findAuthStateById(id).orElseThrow().authVersion());throw new IllegalStateException("IT_AUTHORITY_ROLLBACK");}));
        var c=customers.findById(id).orElseThrow();assertEquals(1,c.getStatus());assertEquals(1L,c.getAuthVersion());assertEquals("原农户",c.getName());
    }
    @Test void missingStaleAndOverflowEpochNeverBecomeAnotherAuthorizationGeneration(){
        long id=existing(PHONE);var missing=customers.findById(id).orElseThrow();missing.setAuthVersion(null);assertThrows(IllegalStateException.class,()->tx.execute(status->customers.save(missing)));
        jdbc.update("UPDATE customer SET auth_version=? WHERE id=?",Long.MAX_VALUE,id);var full=customers.findById(id).orElseThrow();full.setStatus(2);assertThrows(IllegalStateException.class,()->tx.execute(status->customers.save(full)));
        assertThrows(IllegalStateException.class,()->tx.execute(status->{customers.logicDelete(id);return null;}));assertEquals(1,customers.findAuthStateById(id).orElseThrow().status());assertEquals(Long.MAX_VALUE,customers.findAuthStateById(id).orElseThrow().authVersion());
    }
    @Test void V146AddsInitialEpochWithoutChangingLegacyCustomerOrWalletFacts()throws Exception {
        long id=existing(PHONE);jdbc.update("UPDATE customer_wallet SET balance=123.45 WHERE customer_id=? AND wallet_type=1",id);
        jdbc.execute("ALTER TABLE customer DROP COLUMN auth_version");try(var c=jdbc.getDataSource().getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V146__customer_auth_version.sql"));}
        var original=customers.findById(id).orElseThrow();assertEquals(1L,original.getAuthVersion());assertEquals(1,original.getStatus());assertEquals(PHONE,original.getPhone());assertEquals(3,count("customer_wallet"));assertEquals(0,new java.math.BigDecimal("123.45").compareTo(jdbc.queryForObject("SELECT SUM(balance) FROM customer_wallet",java.math.BigDecimal.class)));
    }
    @Test void unchangedCustomerSaveIsValidWithAffectedRowsDriverSemantics(){
        long id=existing(PHONE);var c=customers.findById(id).orElseThrow();tx.execute(status->customers.save(c));assertEquals(1L,customers.findAuthStateById(id).orElseThrow().authVersion());
    }
    @Test void unknownDeletionStateCannotBeMistakenForAnUnchangedSuccessfulSave(){
        long id=existing(PHONE);jdbc.update("UPDATE customer SET deleted=NULL WHERE id=?",id);var unknown=customers.findByIdIncludingDeleted(id).orElseThrow();unknown.setName("不能静默假保存");
        assertThrows(IllegalStateException.class,()->tx.execute(status->customers.save(unknown)));assertEquals("原农户",jdbc.queryForObject("SELECT name FROM customer WHERE id=?",String.class,id));
    }
    @Test void concurrentAuthoritySnapshotsHaveOneWinnerAndNeverRestoreOldState()throws Exception {
        long id=existing(PHONE);var a=customers.findById(id).orElseThrow();var b=customers.findById(id).orElseThrow();a.setStatus(2);b.setStatus(2);
        var executor=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try {
            java.util.function.Function<com.agri_system.domain.customer.model.Customer,Callable<String>> run=c->()->{gate.await(10,TimeUnit.SECONDS);try{tx.execute(status->customers.save(c));return "SAVED";}catch(IllegalStateException stale){if(!stale.getMessage().startsWith("客户授权状态或版本已改变"))throw stale;return "STALE";}};
            var first=executor.submit(run.apply(a));var second=executor.submit(run.apply(b));var results=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));assertEquals(1,results.stream().filter("SAVED"::equals).count());assertEquals(1,results.stream().filter("STALE"::equals).count());
            var current=customers.findAuthStateById(id).orElseThrow();assertEquals(2,current.status());assertEquals(2L,current.authVersion());
        }finally{executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);}
    }
    @Test void deletionMustWaitForOriginalCustomerLockBeforeCheckingConcurrentBalance()throws Exception {
        long id=existing(PHONE);var walletRepo=mock(com.agri_system.domain.wallet.repository.CustomerWalletRepository.class);
        when(walletRepo.findByCustomerIdAndWalletType(eq(id),anyInt())).thenAnswer(call->{
            int type=call.getArgument(1);var w=new com.agri_system.domain.wallet.model.CustomerWallet();w.setCustomerId(id);w.setWalletType(type);w.setFrozenBalance(java.math.BigDecimal.ZERO);w.setBalance(jdbc.queryForObject("SELECT balance FROM customer_wallet WHERE customer_id=? AND wallet_type=?",java.math.BigDecimal.class,id,type));return Optional.of(w);
        });
        var domain=new com.agri_system.domain.customer.service.CustomerDomainService(walletRepo,mock(com.agri_system.domain.credit.repository.CreditAccountRepository.class),mock(com.agri_system.domain.order.repository.OrderRepository.class),mock(com.agri_system.domain.after_sales.repository.AfterSalesRepository.class),mock(com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository.class));
        var application=new com.agri_system.application.customer.service.CustomerApplicationService(customers,creator,staff,walletRepo,mock(com.agri_system.domain.wallet.repository.WalletTransactionRepository.class),mock(com.agri_system.domain.customer.repository.VipMonthlyRecordRepository.class),mock(com.agri_system.domain.customer.repository.CustomerBenefitLogRepository.class),mock(com.agri_system.domain.coupon.repository.CustomerCouponRepository.class),mock(com.agri_system.domain.product.repository.ProductCategoryRepository.class),domain,mock(com.agri_system.application.credit.service.CreditApplicationService.class));
        var executor=Executors.newFixedThreadPool(2);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            var credit=executor.submit(()->tx.execute(status->{customers.findByIdWithLock(id).orElseThrow();jdbc.update("UPDATE customer_wallet SET balance=123.45 WHERE customer_id=? AND wallet_type=1",id);locked.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("IT_RELEASE_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;}));
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            var deletion=executor.submit(()->{try{tx.execute(status->{application.deleteCustomer(id);return null;});return (Throwable)null;}catch(RuntimeException error){return error;}});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
            while(System.nanoTime()<deadline){if(server.queryForObject("SELECT COUNT(*) FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=? AND OBJECT_NAME='customer' AND LOCK_STATUS='WAITING'",Integer.class,schema)>0){waiting=true;break;}Thread.sleep(10);}
            assertTrue(waiting,"删除流程必须实际等待原客户锁，不以固定睡眠猜并发时机");release.countDown();credit.get(5,TimeUnit.SECONDS);
            assertInstanceOf(IllegalArgumentException.class,deletion.get(5,TimeUnit.SECONDS));var current=customers.findAuthStateById(id).orElseThrow();assertEquals(0,current.deleted());assertEquals(1L,current.authVersion());
        }finally{release.countDown();executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);}
    }
    @Test void unmatchedPhoneCreatesExactlyOneOrdinaryCustomerThreeZeroWalletsIdentityAndResult(){
        var input=command();assertEquals("CREATED",register("NEW",input).status());assertEquals("CREATED",register("NEW",input).status());
        assertEquals(1,count("customer"));assertEquals(3,count("customer_wallet"));assertEquals(1,count("customer_identity"));assertEquals(1,count("customer_identity_enrollment"));assertEquals(0,count("wallet_transaction"));
        assertEquals(0,jdbc.queryForObject("SELECT SUM(balance+frozen_balance) FROM customer_wallet",java.math.BigDecimal.class).signum());
        assertEquals("CREATED",enrollment("NEW").result(input.requestKey()).status());assertThrows(IllegalArgumentException.class,()->enrollment("OTHER").result(input.requestKey()));
        assertThrows(IllegalArgumentException.class,()->register("NEW",new CustomerEnrollmentService.Register(input.requestKey(),"改名")));
    }
    @Test void inactiveAndDeletedPhoneMatchesAreNeverMistakenForNewCustomers(){
        long id=existing(PHONE);jdbc.update("UPDATE customer SET status=2,deleted=1 WHERE id=?",id);
        assertEquals("REVIEW_REQUIRED",register("OLD",command()).status());assertEquals(1,count("customer"));assertEquals(3,count("customer_wallet"));assertEquals(0,count("customer_identity"));
    }
    @Test void legacyInternationalAndSeparatedPhoneMatchesRequireReviewWithoutNewWallets(){
        long id=existing("13900000000");jdbc.update("UPDATE customer SET phone=?,status=2,deleted=1 WHERE id=?","+86 138-0000-0000",id);
        assertEquals("REVIEW_REQUIRED",register("LEGACY_ALIAS",command()).status());
        assertEquals(List.of(id),customers.findIdsByPhoneIncludingDeleted(PHONE));
        assertEquals(1,count("customer"));assertEquals(3,count("customer_wallet"));assertEquals(0,count("customer_identity"));
        assertEquals("+86 138-0000-0000",customers.findByIdIncludingDeleted(id).orElseThrow().getPhone(),"只读匹配不能改历史联系资料");
    }
    @Test void offlineCreationNormalizesNewMainlandNumbersAndCannotDuplicateOnlineCustomer(){
        register("ONLINE_FIRST",command());
        assertThrows(IllegalArgumentException.class,()->existing("+86 138-0000-0000"));
        long another=existing("86 139-0000-0000");assertEquals("13900000000",customers.findById(another).orElseThrow().getPhone());
        assertEquals(2,count("customer"));assertEquals(6,count("customer_wallet"));assertEquals(1,count("customer_identity"));
    }
    @Test void multipleLegacyAliasesRemainSeparateAndNeverSelectOneAutomatically(){
        long first=existing("13900000000"),second=existing("13700000000");
        jdbc.update("UPDATE customer SET phone=? WHERE id=?","8613800000000",first);jdbc.update("UPDATE customer SET phone=?,deleted=1 WHERE id=?","138 0000 0000",second);
        assertEquals(List.of(first,second),customers.findIdsByPhoneIncludingDeleted(PHONE));
        assertEquals("REVIEW_REQUIRED",register("MULTIPLE_ALIAS",command()).status());
        assertThrows(IllegalArgumentException.class,()->existing(PHONE));assertEquals(2,count("customer"));assertEquals(6,count("customer_wallet"));assertEquals(0,count("customer_identity"));
    }
    @Test void onlineAndOfflineConcurrentCreationStillProduceOnlyOneAccount()throws Exception {
        var input=command();var executor=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try {
            var online=executor.submit(()->{gate.await(10,TimeUnit.SECONDS);try{return register("OFFLINE_RACE",input).status();}catch(org.springframework.dao.DuplicateKeyException collision){return "RETRY";}catch(IllegalArgumentException collision){if(!collision.getMessage().startsWith("手机号已有客户档案"))throw collision;return "RETRY";}});
            var offline=executor.submit(()->{gate.await(10,TimeUnit.SECONDS);try{return existing("+86 138-0000-0000");}catch(IllegalArgumentException|org.springframework.dao.DuplicateKeyException collision){return -1L;}});
            String result=online.get(30,TimeUnit.SECONDS);offline.get(30,TimeUnit.SECONDS);
            if("RETRY".equals(result))assertEquals("REVIEW_REQUIRED",register("OFFLINE_RACE",input).status());
            else assertTrue(Set.of("CREATED","REVIEW_REQUIRED").contains(result));
            assertEquals(1,count("customer"));assertEquals(3,count("customer_wallet"));assertTrue(count("customer_identity")<=1);
            assertEquals(PHONE,jdbc.queryForObject("SELECT phone FROM customer",String.class));
        } finally {executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);}
    }
    @Test void nonMainlandAndAmbiguousLegacyContactsAreNotGuessedOrChanged(){
        long landline=existing("0459-1234567"),foreign=existing("+14155552671");
        assertEquals("0459-1234567",customers.findById(landline).orElseThrow().getPhone());assertEquals("+14155552671",customers.findById(foreign).orElseThrow().getPhone());
        assertThrows(IllegalArgumentException.class,()->existing("0459-1234567"));
        assertTrue(com.agri_system.domain.customer.service.CustomerPhoneNumbers.mainland("+86(138)00000000").isEmpty());
        assertEquals("CREATED",register("OTHER_CONTACTS",command()).status());assertEquals(3,count("customer"));assertEquals(9,count("customer_wallet"));
    }
    @RepeatedTest(3) void tenConcurrentFirstRequestsShareOneSubjectAndOneAccount()throws Exception {
        var executor=Executors.newFixedThreadPool(10);var gate=new CyclicBarrier(10);
        try{List<Future<CustomerEnrollmentService.Result>> runs=new ArrayList<>();for(int n=0;n<10;n++)runs.add(executor.submit(()->{gate.await(10,TimeUnit.SECONDS);return register("PARALLEL",command());}));
            var keys=new HashSet<String>();for(var run:runs){var result=run.get(30,TimeUnit.SECONDS);assertEquals("CREATED",result.status());keys.add(result.requestKey());}assertEquals(1,keys.size());
            assertEquals(1,count("customer"));assertEquals(3,count("customer_wallet"));assertEquals(1,count("customer_identity"));assertEquals(1,count("customer_identity_enrollment"));
        }finally{executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);}
    }
    @Test void mappingFailureRollsBackCustomerWalletsAndRequestInSameFlexTransaction(){
        var failing=spy(identities);doThrow(new IllegalStateException("IT_MAPPING_FAILURE")).when(failing).save(any());
        var service=new CustomerEnrollmentService(proof("FAIL"),requests,failing,customers,creator,tx.getTransactionManager());
        assertThrows(IllegalStateException.class,()->service.register(command()));
        for(String table:List.of("customer","customer_wallet","customer_identity","customer_identity_enrollment","wallet_transaction"))assertEquals(0,count(table),table);
    }
    @Test void manualApprovalStoresImmutableAuditWithoutChangingOldMoneyOrContact(){
        long id=existing(PHONE);jdbc.update("UPDATE customer_wallet SET balance=123.45 WHERE customer_id=? AND wallet_type=1",id);
        var input=command();register("CLAIM",input);var row=requests.findByRequestKey(input.requestKey(),false).orElseThrow();
        var decision=new CustomerEnrollmentReviewService.Decision(UUID.randomUUID().toString(),"LINK",id,"IN_PERSON","当面核验确认为本人");
        assertEquals("LINKED",tx.execute(status->review().decide(1L,row.getId(),decision)).status());assertEquals("LINKED",tx.execute(status->review().decide(1L,row.getId(),decision)).status());
        var recovered=review().decisionResult(1L,row.getId(),decision.requestKey());assertEquals("LINKED",recovered.status());assertEquals("LINK",recovered.action());assertEquals(String.valueOf(id),recovered.targetCustomerId());assertEquals(decision.reason(),recovered.reason());
        assertEquals("CONFLICT",review().decisionResult(2L,row.getId(),decision.requestKey()).status());
        var actual=requests.findById(row.getId(),false).orElseThrow();assertEquals(1L,actual.getReviewedBy());assertEquals(decision.requestKey(),actual.getDecisionRequestKey());assertNotNull(actual.getIdentityId());assertEquals(1L,actual.getIdentityAuthVersion());
        assertEquals(1,count("customer"));assertEquals(3,count("customer_wallet"));assertEquals(1,count("customer_identity"));assertEquals(0,count("wallet_transaction"));assertEquals(PHONE,customers.findById(id).orElseThrow().getPhone());
        assertEquals(0,new java.math.BigDecimal("123.45").compareTo(jdbc.queryForObject("SELECT balance FROM customer_wallet WHERE customer_id=? AND wallet_type=1",java.math.BigDecimal.class,id)));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->review().decide(2L,row.getId(),decision)));
    }
    @Test void rejectionReleasesOnlyActiveAnchorAndKeepsOldRequestForLaterQueries(){
        existing(PHONE);var first=command();register("REJECT",first);var row=requests.findByRequestKey(first.requestKey(),false).orElseThrow();
        var decision=new CustomerEnrollmentReviewService.Decision(UUID.randomUUID().toString(),"REJECT",null,"UNCONFIRMED","尚不能确认同一人");tx.execute(status->review().decide(1L,row.getId(),decision));
        assertEquals("REJECTED",enrollment("REJECT").result(first.requestKey()).status());assertNull(requests.findById(row.getId(),false).orElseThrow().getActiveSubject());
        assertEquals("REVIEW_REQUIRED",register("REJECT",command()).status());assertEquals("REJECTED",register("REJECT",first).status(),"新申请不能阻断旧UUID重放原结果");assertEquals(2,count("customer_identity_enrollment"));assertEquals(0,count("customer_identity"));assertEquals(1,count("customer"));
    }
    @Test void failedTargetCheckRollsBackDecisionRequestReservation(){
        long id=existing(PHONE);var input=command();register("DISABLED",input);var row=requests.findByRequestKey(input.requestKey(),false).orElseThrow();jdbc.update("UPDATE customer SET status=2 WHERE id=?",id);
        var decision=new CustomerEnrollmentReviewService.Decision(UUID.randomUUID().toString(),"LINK",id,"IN_PERSON","当面核验");assertThrows(IllegalArgumentException.class,()->tx.execute(status->review().decide(1L,row.getId(),decision)));
        var actual=requests.findById(row.getId(),false).orElseThrow();assertNull(actual.getDecisionRequestKey());assertNull(actual.getReviewedBy());assertEquals("REVIEW_REQUIRED",actual.getStatus());assertEquals(0,count("customer_identity"));
    }
    @Test void separateRejectionIsDurableBarrierForLateOriginalLinkWithoutNewSchema(){
        long id=existing(PHONE);var input=command();register("CLOSE_REQUEST",input);var row=requests.findByRequestKey(input.requestKey(),false).orElseThrow();
        var original=new CustomerEnrollmentReviewService.Decision(UUID.randomUUID().toString(),"LINK",id,"IN_PERSON","原关联核验");
        jdbc.update("UPDATE customer SET status=2 WHERE id=?",id);assertThrows(IllegalArgumentException.class,()->tx.execute(status->review().decide(1L,row.getId(),original)));
        assertEquals("UNKNOWN",review().decisionResult(1L,row.getId(),original.requestKey()).status());
        var close=new CustomerEnrollmentReviewService.Decision(UUID.randomUUID().toString(),"REJECT",null,"UNCONFIRMED","员工主动结束申请，请客户重新申请");
        assertEquals("REJECTED",tx.execute(status->review().decide(1L,row.getId(),close)).status());jdbc.update("UPDATE customer SET status=1 WHERE id=?",id);
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->review().decide(1L,row.getId(),original)));assertEquals("REJECTED",review().decisionResult(1L,row.getId(),close.requestKey()).status());
        assertEquals("CONFLICT",review().decisionResult(1L,row.getId(),original.requestKey()).status());assertEquals(0,count("customer_identity"));assertEquals(3,count("customer_wallet"));
        assertEquals("REVIEW_REQUIRED",register("CLOSE_REQUEST",command()).status());
    }
    @Test void repositoryCannotCommitProcessingRowsWithoutActualFlexTransaction(){
        var row=new com.agri_system.domain.auth.model.CustomerIdentityEnrollment();assertThrows(IllegalStateException.class,()->requests.acquire(row));assertEquals(0,count("customer_identity_enrollment"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE permission IN ('customer:identity:query','customer:identity:bind')",Integer.class));
    }
    @Test void originalUuidStillWinsWhenRejectionAndResubmissionHappenAfterInitialLookup()throws Exception {
        existing(PHONE);var original=command();var raced=spy(requests);var first=new java.util.concurrent.atomic.AtomicBoolean(true);var executor=Executors.newSingleThreadExecutor();
        doAnswer(call->{
            Object initially=call.callRealMethod();
            if(first.getAndSet(false)){
                assertTrue(((Optional<?>)initially).isEmpty());
                executor.submit(()->{
                    register("LATE_ORIGINAL",original);var row=requests.findByRequestKey(original.requestKey(),false).orElseThrow();
                    var decision=new CustomerEnrollmentReviewService.Decision(UUID.randomUUID().toString(),"REJECT",null,"UNCONFIRMED","尚不能确认");tx.execute(status->review().decide(1L,row.getId(),decision));
                    register("LATE_ORIGINAL",command());return null;
                }).get(15,TimeUnit.SECONDS);
            }
            return initially;
        }).when(raced).findByRequestKey(original.requestKey(),true);
        try{
            var service=new CustomerEnrollmentService(proof("LATE_ORIGINAL"),raced,identities,customers,creator,tx.getTransactionManager());
            assertEquals("REJECTED",service.register(original).status());assertEquals(2,count("customer_identity_enrollment"));assertEquals(1,count("customer"));assertEquals(0,count("customer_identity"));
        }finally{executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);}
    }

}

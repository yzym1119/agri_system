package com.agri_system.infrastructure.auth.repository;
import com.agri_system.infrastructure.auth.mapper.CustomerIdentityMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="mysql.it",matches="true")
class CustomerIdentityScopeMysqlTest {
    JdbcTemplate server,jdbc;String schema;boolean created;SqlSession session;CustomerIdentityRepositoryImpl repository;
    static final String APP="wx1111111111111111",OTHER="wx2222222222222222";
    @BeforeEach void setup() throws Exception {
        String base="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/";
        String options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
        server=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));schema="agri_customer_identity_it_"+java.util.UUID.randomUUID().toString().replace("-","");server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");created=true;
        var ds=new DriverManagerDataSource(base+schema+options,user,password);jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE customer_identity(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT NOT NULL,identity_type VARCHAR(20) NOT NULL,identity_value VARCHAR(200) NOT NULL,session_key VARCHAR(255),status INT,created_at DATETIME,updated_at DATETIME,UNIQUE KEY uk_identity_type_value(identity_type,identity_value))");
        jdbc.execute("INSERT INTO customer_identity(customer_id,identity_type,identity_value,status) VALUES(7,'WECHAT_OPENID','LegacyOpenId',1)");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V143__wechat_identity_app_scope.sql"));ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V144__customer_identity_auth_version.sql"));}
        var config=new FlexConfiguration(new Environment("identity",new JdbcTransactionFactory(),new FlexDataSource("identity",ds,false)));config.setMapUnderscoreToCamelCase(true);config.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);config.addMapper(CustomerIdentityMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);repository=new CustomerIdentityRepositoryImpl(new JdbcTemplate(config.getEnvironment().getDataSource()));
    }
    @AfterEach void close(){if(session!=null)session.close();if(created){server.execute("DROP DATABASE `"+schema+"`");created=false;}}
    void identity(String app,String value,int status){jdbc.update("INSERT INTO customer_identity(customer_id,identity_type,identity_value,app_id,status) VALUES(7,'WECHAT_OPENID',?,?,?)",value,app,status);}
    @Test void legacyNamespaceIsPreservedAndNeverSilentlyClaimedByCurrentApp(){
        assertEquals("",jdbc.queryForObject("SELECT app_id FROM customer_identity WHERE id=1",String.class));assertEquals(7L,jdbc.queryForObject("SELECT customer_id FROM customer_identity WHERE id=1",Long.class));
        assertTrue(repository.findWechatByAppAndOpenIdForUpdate(APP,"LegacyOpenId").isEmpty());
        assertThrows(IllegalArgumentException.class,()->repository.findByTypeAndValue("WECHAT_OPENID","LegacyOpenId"));
    }
    @Test void binaryOpenIdAndAppScopeAllowDistinctRealIdentitiesWithoutCrossing(){
        identity(APP,"Open_A",1);identity(OTHER,"Open_A",1);identity(APP,"open_a",1);
        assertEquals(APP,repository.findWechatByAppAndOpenIdForUpdate(APP,"Open_A").orElseThrow().getAppId());
        assertNotEquals(repository.findWechatByAppAndOpenIdForUpdate(APP,"Open_A").orElseThrow().getId(),repository.findWechatByAppAndOpenIdForUpdate(APP,"open_a").orElseThrow().getId());
        assertThrows(Exception.class,()->identity(APP,"Open_A",1));assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM customer_identity",Integer.class));
    }
    @Test void activeNotificationCandidatesStayInAppWhileDisabledIdentityCanBeRejectedExplicitly(){
        identity(APP,"ACTIVE",1);identity(APP,"DISABLED",2);identity(OTHER,"OTHER",1);
        assertEquals(1,repository.findActiveWechatByCustomerAndApp(7L,APP).size());
        var disabled=repository.findWechatByAppAndOpenIdForUpdate(APP,"DISABLED").orElseThrow();assertFalse(disabled.isActive());assertEquals(2,repository.findById(disabled.getId()).orElseThrow().getStatus());
    }
    @Test void credentialUpdateSharesFlexTransactionWithMapperAndRollsBackTogether() {
        identity(APP,"TX_TARGET",1);
        String xid=com.mybatisflex.core.transaction.TransactionalManager.startTransactional();
        try{
            var row=repository.findWechatByAppAndOpenIdForUpdate(APP,"TX_TARGET").orElseThrow();row.setSessionKey("IT_ROLLBACK_VALUE");repository.save(row);
            var mapper=session.getMapper(CustomerIdentityMapper.class);
            Integer seen=mapper.selectOneByQueryAs(com.mybatisflex.core.query.QueryWrapper.create().select("COUNT(*)").where("id=?",row.getId()).and("session_key IS NOT NULL"),Integer.class);
            assertEquals(1,seen);
        }finally{com.mybatisflex.core.transaction.TransactionalManager.rollback(xid);}
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM customer_identity WHERE identity_value='TX_TARGET' AND session_key IS NOT NULL",Integer.class));
    }
    @Test void developmentSqlLoggingNeverPrintsOrReadsSessionCredential() {
        identity(APP,"LOG_TARGET",1);String before="IT_PRIVATE_SESSION_BEFORE",after="IT_PRIVATE_SESSION_AFTER";
        jdbc.update("UPDATE customer_identity SET session_key=? WHERE identity_value='LOG_TARGET'",before);
        var output=new java.io.ByteArrayOutputStream();var original=System.out;
        boolean projected=false;
        try(var capture=new java.io.PrintStream(output,true,java.nio.charset.StandardCharsets.UTF_8)){
            System.setOut(capture);
            var loaded=repository.findWechatByAppAndOpenIdForUpdate(APP,"LOG_TARGET").orElseThrow();projected=loaded.getSessionKey()==null;
            loaded.setSessionKey(after);repository.save(loaded);repository.findById(loaded.getId());repository.findActiveWechatByCustomerAndApp(7L,APP);
            var metadata=repository.findById(loaded.getId()).orElseThrow();metadata.setStatus(2);repository.save(metadata);
        }finally{System.setOut(original);}
        String logs=output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(logs.contains(before)||logs.contains(after),"身份凭据不得出现在开发SQL日志中");assertTrue(projected,"会话核验无需读取已有sessionKey");
        assertTrue(after.equals(jdbc.queryForObject("SELECT session_key FROM customer_identity WHERE identity_value='LOG_TARGET'",String.class)));
    }
    @Test void migrationInitializesExistingIdentityWithoutReassigningOrEnablingIt() {
        var legacy=repository.findById(1L).orElseThrow();
        assertEquals(1L,legacy.getAuthVersion());assertEquals(7L,legacy.getCustomerId());
        assertEquals("",legacy.getAppId());assertEquals("LegacyOpenId",legacy.getIdentityValue());
        assertEquals(1,legacy.getStatus());
    }
    @Test void statusAndOwnershipRoundTripsNeverRestoreOldAuthorizationVersion() {
        identity(APP,"LIFECYCLE",1);var row=repository.findWechatByAppAndOpenIdForUpdate(APP,"LIFECYCLE").orElseThrow();
        assertEquals(1L,row.getAuthVersion());
        row.setStatus(2);repository.save(row);assertEquals(2L,row.getAuthVersion());
        row.setStatus(1);repository.save(row);assertEquals(3L,row.getAuthVersion());
        row.setCustomerId(9L);repository.save(row);assertEquals(4L,row.getAuthVersion());
        row.setCustomerId(7L);repository.save(row);assertEquals(5L,row.getAuthVersion());
        assertEquals(5L,repository.findById(row.getId()).orElseThrow().getAuthVersion());
    }
    @Test void changingApplicationTypeOrOpenIdRevokesButCredentialRefreshKeepsOtherDevicesValid() {
        identity(APP,"FIELDS",1);var row=repository.findWechatByAppAndOpenIdForUpdate(APP,"FIELDS").orElseThrow();
        row.setSessionKey("IT_NEW_CREDENTIAL");repository.save(row);assertEquals(1L,row.getAuthVersion());
        row.setUpdatedAt(java.time.LocalDateTime.now());repository.save(row);assertEquals(1L,row.getAuthVersion());
        row.setAppId(OTHER);repository.save(row);assertEquals(2L,row.getAuthVersion());
        row.setIdentityValue("FIELDS_NEW");repository.save(row);assertEquals(3L,row.getAuthVersion());
        row.setIdentityType("OTHER");repository.save(row);assertEquals(4L,row.getAuthVersion());
    }
    @Test void staleOrUnknownSnapshotsCannotUndoRevocationOrChangeCredential() {
        identity(APP,"STALE",1);var old=repository.findWechatByAppAndOpenIdForUpdate(APP,"STALE").orElseThrow();
        var current=repository.findById(old.getId()).orElseThrow();current.setStatus(2);repository.save(current);
        old.setSessionKey("IT_STALE_CREDENTIAL");assertThrows(IllegalStateException.class,()->repository.save(old));
        assertEquals(2,repository.findById(old.getId()).orElseThrow().getStatus());
        assertEquals(2L,repository.findById(old.getId()).orElseThrow().getAuthVersion());
        current.setAuthVersion(null);assertThrows(IllegalArgumentException.class,()->repository.save(current));
        current.setAuthVersion(0L);assertThrows(IllegalArgumentException.class,()->repository.save(current));
        assertNull(jdbc.queryForObject("SELECT session_key FROM customer_identity WHERE identity_value='STALE'",String.class));
    }
    @Test void concurrentLifecycleWritesShareOnePersistedVersionAndRejectTheStaleWriter() throws Exception {
        identity(APP,"RACE",1);var first=repository.findWechatByAppAndOpenIdForUpdate(APP,"RACE").orElseThrow();
        var second=repository.findById(first.getId()).orElseThrow();first.setStatus(2);second.setCustomerId(9L);
        var gate=new java.util.concurrent.CyclicBarrier(2);var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->{gate.await(5,java.util.concurrent.TimeUnit.SECONDS);try{repository.save(first);return true;}catch(IllegalStateException stale){return false;}});
            var b=executor.submit(()->{gate.await(5,java.util.concurrent.TimeUnit.SECONDS);try{repository.save(second);return true;}catch(IllegalStateException stale){return false;}});
            assertNotEquals(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS),"同一版本只能有一次授权变更");
            var actual=repository.findById(first.getId()).orElseThrow();assertEquals(2L,actual.getAuthVersion());
            assertTrue(actual.getStatus()==2&&actual.getCustomerId()==7L||actual.getStatus()==1&&actual.getCustomerId()==9L);
        }finally{executor.shutdownNow();}
    }
    @Test void epochChangeParticipatesInOriginalFlexRollbackAndNeverWrapsOnOverflow() {
        identity(APP,"ROLLBACK_EPOCH",1);var row=repository.findWechatByAppAndOpenIdForUpdate(APP,"ROLLBACK_EPOCH").orElseThrow();
        String xid=com.mybatisflex.core.transaction.TransactionalManager.startTransactional();
        try{row.setStatus(2);repository.save(row);assertEquals(2L,repository.findById(row.getId()).orElseThrow().getAuthVersion());}
        finally{com.mybatisflex.core.transaction.TransactionalManager.rollback(xid);}
        var restored=repository.findById(row.getId()).orElseThrow();assertEquals(1L,restored.getAuthVersion());assertEquals(1,restored.getStatus());
        jdbc.update("UPDATE customer_identity SET auth_version=? WHERE id=?",Long.MAX_VALUE,row.getId());
        var overflow=repository.findById(row.getId()).orElseThrow();overflow.setStatus(2);
        assertThrows(IllegalStateException.class,()->repository.save(overflow));
        assertEquals(Long.MAX_VALUE,repository.findById(row.getId()).orElseThrow().getAuthVersion());assertEquals(1,repository.findById(row.getId()).orElseThrow().getStatus());
    }

}

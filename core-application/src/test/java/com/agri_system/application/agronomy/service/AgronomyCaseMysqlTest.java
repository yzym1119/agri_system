package com.agri_system.application.agronomy.service;

import com.agri_system.application.config.JdbcAccessConfig;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import com.agri_system.infrastructure.agronomy.repository.AgronomyCaseRepositoryImpl;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="mysql.it",matches="true")
class AgronomyCaseMysqlTest {
    JdbcTemplate server,jdbc;String schema;boolean created;TenantFactory previous;
    TransactionTemplate tx;AgronomyCaseService service;AgronomyCaseRepositoryImpl rows;
    StaffRepository staff;SysRoleRepository roles;StaffAuthorityService authority;
    StorePublicationRepositoryImpl stores;SysOperationLogRepository logs;
    Facts facts=new Facts("虚构地区","虚构作物","不清楚");
    @BeforeEach void setup()throws Exception{
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        String base="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/";
        String options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
        server=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));
        schema="agri_case_it_"+UUID.randomUUID().toString().replace("-","");server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");created=true;
        var source=new DriverManagerDataSource(base+schema+options,user,password);var init=new JdbcTemplate(source);
        init.execute("CREATE TABLE staff(id BIGINT PRIMARY KEY)");
        try(var c=source.getConnection()){
            for(String file:List.of("V1__store.sql","V46__staff_rbac_and_log.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/"+file));
            init.execute("ALTER TABLE store ADD deleted INT NOT NULL DEFAULT 0");
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V147__client_store_publication.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V149__agronomy_case.sql"));
        }
        init.update("INSERT INTO store(id,code,name,client_visible) VALUES(1,'VIRTUAL','虚构咨询门店',1),(2,'OTHER','其他虚构门店',1)");
        var config=new FlexConfiguration(new Environment("case",new SpringManagedTransactionFactory(),new FlexDataSource("case",source,false)));config.setMapUnderscoreToCamelCase(true);
        var factory=new FlexSqlSessionFactoryBuilder().build(config);config.addMapper(SysOperationLogMapper.class);config.addMapper(Probe.class);
        var template=new SqlSessionTemplate(factory);jdbc=new JdbcTemplate(config.getEnvironment().getDataSource());tx=new TransactionTemplate(new JdbcAccessConfig().transactionManager(factory));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        rows=new AgronomyCaseRepositoryImpl(jdbc);stores=new StorePublicationRepositoryImpl(jdbc);logs=new SysOperationLogRepositoryImpl(template.getMapper(SysOperationLogMapper.class));
        staff=mock(StaffRepository.class);roles=mock(SysRoleRepository.class);authority=mock(StaffAuthorityService.class);
        for(long id:List.of(9L,10L,11L)){var s=new Staff();s.setId(id);s.setStoreId(id==11?2L:1L);s.setStatus(1);s.setDeleted(0);when(staff.findIdentityById(id)).thenReturn(Optional.of(s));when(roles.findRoleCodesByStaffId(id)).thenReturn(List.of("SALE"));}
        when(authority.loadPermissions(9L)).thenReturn(List.of("agronomy:case:query","agronomy:case:manage","agronomy:case:review"));when(authority.loadPermissions(10L)).thenReturn(List.of("agronomy:case:query"));
        service=new AgronomyCaseService(rows,staff,roles,authority,stores,logs);
        tx.execute(s->{assertEquals(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class),template.getMapper(Probe.class).id());assertEquals("READ-COMMITTED",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));return null;});
    }
    interface Probe {@org.apache.ibatis.annotations.Select("SELECT CONNECTION_ID()") Long id();}
    @AfterEach void close(){TenantManager.setTenantFactory(previous);if(created){server.execute("DROP DATABASE `"+schema+"`");created=false;}}
    AgronomyCaseService.Receipt create(long customer,long store,String key){return tx.execute(s->service.create(customer,new AgronomyCaseService.Create(key,store,"虚构咨询，仅测试软件流程",facts)));}
    AgronomyCaseService.Receipt supplement(String id,String key,String version){return tx.execute(s->service.changeCustomer(101L,Long.valueOf(id),"SUPPLEMENT",new AgronomyCaseService.Change(key,version,"补充虚构情况",null,facts,null)));}
    @Test void concurrentOriginalCreateTenRequestsLeaveOneProblemAndOneOriginalEntry()throws Exception{
        String key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(10);var start=new CountDownLatch(1);
        try{var jobs=new ArrayList<Future<AgronomyCaseService.Receipt>>();for(int n=0;n<10;n++)jobs.add(pool.submit(()->{start.await();return create(101,1,key);}));start.countDown();var expected=jobs.get(0).get(15,TimeUnit.SECONDS);for(var job:jobs)assertEquals(expected,job.get(15,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));assertEquals(expected,service.submissionCustomer(101L,key).receipt());
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void concurrentSameAppendReplaysButDifferentOldConfirmationsHaveOnlyOneWinner()throws Exception{
        String id=create(101,1,UUID.randomUUID().toString()).caseId(),key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();return supplement(id,key,"1");});var b=pool.submit(()->{start.await();return supplement(id,key,"1");});start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<Boolean>>();for(int n=0;n<2;n++){String next=UUID.randomUUID().toString();jobs.add(pool.submit(()->{gate.await();try{supplement(id,next,"2");return true;}catch(IllegalArgumentException stale){return false;}}));}gate.countDown();int winners=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            var detail=service.detailCustomer(101L,Long.valueOf(id),null);assertEquals("3",detail.problem().version());assertEquals("3",detail.problem().factsVersion());assertEquals(3,detail.entries().size());
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void mybatisAuditAndJdbcReplyReallyRollbackOnSameFlexConnection(){
        String id=create(101,1,UUID.randomUUID().toString()).caseId();var brokenLog=mock(SysOperationLogRepository.class);
        doAnswer(i->{logs.save(i.getArgument(0));throw new IllegalStateException("test after MyBatis insert");}).when(brokenLog).save(any());
        var broken=new AgronomyCaseService(rows,staff,roles,authority,stores,brokenLog);String key=UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class,()->tx.execute(s->broken.changeStaff(9L,Long.valueOf(id),"HUMAN_REPLY",new AgronomyCaseService.Change(key,"1","不应留下的虚构回复","测试来源",null,null))));
        assertEquals("1",service.detailCustomer(101L,Long.valueOf(id),null).problem().version());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log",Integer.class));assertEquals("UNKNOWN",service.submissionStaff(9L,key).status());
    }
    @Test void sameUuidAcrossCreationAndLaterMutationCannotCreateOrAppendAnotherFact(){
        String original=UUID.randomUUID().toString();String id=create(101,1,original).caseId();String later=UUID.randomUUID().toString();supplement(id,later,"1");
        assertThrows(IllegalArgumentException.class,()->create(101,1,later));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
        assertThrows(IllegalArgumentException.class,()->supplement(id,original,"2"));
    }
    @Test void sqlFiltersOwnersStoresAssignmentsAndStatusBeforeBoundedPagination(){
        for(int n=0;n<25;n++){create(101,1,UUID.randomUUID().toString());create(202,1,UUID.randomUUID().toString());create(303,2,UUID.randomUUID().toString());}
        var first=service.pageCustomer(101L,null,null);assertEquals(20,first.rows().size());assertNotNull(first.nextAfter());var second=service.pageCustomer(101L,Long.valueOf(first.nextAfter()),null);assertEquals(5,second.rows().size());assertNull(second.nextAfter());
        var all=new HashSet<String>();first.rows().forEach(x->assertTrue(all.add(x.id())));second.rows().forEach(x->assertTrue(all.add(x.id())));assertTrue(service.pageCustomer(101L,null,"WAITING_REVIEW").rows().isEmpty());
        assertEquals(20,service.pageStaff(9L,null,null,null).rows().size());assertTrue(service.pageStaff(10L,null,null,null).rows().isEmpty());assertThrows(IllegalArgumentException.class,()->service.pageStaff(9L,2L,null,null));
        String id=first.rows().get(0).id();tx.execute(s->service.changeStaff(9L,Long.valueOf(id),"ASSIGN",new AgronomyCaseService.Change(UUID.randomUUID().toString(),"1","",null,null,10L)));
        assertEquals(1,service.pageStaff(10L,null,null,null).rows().size());assertEquals(1,service.detailCustomer(101L,Long.valueOf(id),null).entries().size());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE permission LIKE 'agronomy:case:%'",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE permission LIKE 'agronomy:case:%')",Integer.class));
    }
    @Test void entryPagingExcludesAssignmentBeforeLimitAndDoesNotMixNewerVersionIntoOldSnapshot(){
        String id=create(101,1,UUID.randomUUID().toString()).caseId();for(int n=1;n<=24;n++)supplement(id,UUID.randomUUID().toString(),Integer.toString(n));
        tx.execute(s->service.changeStaff(9L,Long.valueOf(id),"ASSIGN",new AgronomyCaseService.Change(UUID.randomUUID().toString(),"25","",null,null,10L)));
        var first=service.detailCustomer(101L,Long.valueOf(id),null);assertEquals(20,first.entries().size());assertEquals("25",first.entries().get(0).version());assertEquals("26",first.problem().version());
        assertEquals(5,service.detailCustomer(101L,Long.valueOf(id),Long.valueOf(first.nextAfter())).entries().size());
        assertEquals(1,rows.entries(Long.valueOf(id),true,1,null).size(),"旧主档只配旧事实，不混入后来追加");
    }
}

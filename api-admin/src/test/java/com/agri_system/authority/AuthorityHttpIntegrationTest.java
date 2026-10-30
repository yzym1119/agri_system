package com.agri_system.authority;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.api.admin.config.DataScopeConfig;
import com.agri_system.api.admin.controller.RoleController;
import com.agri_system.api.admin.controller.StaffAdminController;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.common.service.CodeGenerationApplicationService;
import com.agri_system.application.config.JdbcAccessConfig;
import com.agri_system.application.performance.service.StaffPerformanceApplicationService;
import com.agri_system.application.staff.service.*;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.infrastructure.auth.config.SaTokenConfigure;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import com.agri_system.infrastructure.store.mapper.StaffMapper;
import com.agri_system.infrastructure.store.mapper.StoreMapper;
import com.agri_system.infrastructure.store.repository.StaffRepositoryImpl;
import com.agri_system.infrastructure.store.repository.StoreRepositoryImpl;
import com.agri_system.infrastructure.system.auth.mapper.SysRoleMapper;
import com.agri_system.infrastructure.system.auth.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 真实安全拦截器、RBAC 仓储和 Flex 租户过滤；不使用伪造身份/权限实现。 */
@SpringBootTest(classes = AuthorityHttpIntegrationTest.Config.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:authority_http;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "mybatis-flex.configuration.map-underscore-to-camel-case=true",
        "mybatis-flex.configuration.local-cache-scope=STATEMENT", "sa-token.is-print=false"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthorityHttpIntegrationTest {
    private static final String MYSQL_DATABASE = "agri_authority_it_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean mysqlCreated;
    private static String mysqlServerUrl;
    private static String mysqlUser;
    private static String mysqlPassword;
    private static final cn.dev33.satoken.config.SaTokenConfig OLD_CONFIG = SaManager.getConfig();
    private static final cn.dev33.satoken.context.SaTokenContext OLD_CONTEXT = SaManager.getSaTokenContext();
    private static final StpInterface OLD_STP = SaManager.getStpInterface();
    private static final SaTokenDao OLD_DAO = SaManager.getSaTokenDao();
    private static final TenantFactory OLD_TENANT = TenantManager.getTenantFactory();
    private static final Object OLD_STAFF = staticField("staffRepository");
    private static final Object OLD_ROLE = staticField("roleRepository");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffStpInterface stpInterface;
    @MockBean CodeGenerationApplicationService codes;
    @MockBean StaffPerformanceApplicationService performance;
    @MockBean com.agri_system.application.order.service.PosCatalogApplicationService posCatalog;
    @MockBean com.agri_system.application.store.service.StoreApplicationService businessStores;
    @MockBean com.agri_system.application.purchase.service.PurchaseApplicationService receiving;
    @MockBean com.agri_system.application.product.service.ProductApplicationService productCatalogAdmin;
    @MockBean com.agri_system.application.inventory.service.MobileScanQueryService mobileInventory;

    /** 可选真实 MySQL，同一HTTP套及生产锁代码，仅使用本次成功创建的随机隔离库。 */
    @DynamicPropertySource
    static void mysqlDatasource(DynamicPropertyRegistry registry) throws Exception {
        if (!Boolean.getBoolean("authority.mysql.it")) return;
        mysqlUser=System.getProperty("mysql.it.user", "root");
        mysqlPassword=System.getProperty("mysql.it.password", "");
        String base="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/";
        String options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
        mysqlServerUrl=base+options;
        try(var c=DriverManager.getConnection(mysqlServerUrl,mysqlUser,mysqlPassword);var st=c.createStatement()) {
            st.execute("CREATE DATABASE "+MYSQL_DATABASE+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            mysqlCreated=true;
        }
        registry.add("spring.datasource.url",()->base+MYSQL_DATABASE+options);
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username",()->mysqlUser);
        registry.add("spring.datasource.password",()->mysqlPassword);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {StaffMapper.class, SysRoleMapper.class})
    @Import({SaTokenConfigure.class, DataScopeConfig.class, CurrentUser.class, StaffStpInterface.class,
            StaffAuthorityService.class, StaffLifecycleService.class, StaffSessionOperator.class,
            StaffRepositoryImpl.class, StoreRepositoryImpl.class, SysRoleRepositoryImpl.class,
            SysStaffRoleRepositoryImpl.class, SysRoleMenuRepositoryImpl.class, SysMenuRepositoryImpl.class,
            RoleController.class, StaffAdminController.class, GlobalExceptionHandler.class,
            JdbcAccessConfig.class, Probe.class,
            com.agri_system.api.admin.controller.PosCatalogController.class,
            com.agri_system.api.admin.controller.StoreController.class,
            com.agri_system.api.admin.controller.PurchaseController.class,
            com.agri_system.api.admin.controller.MobileScanController.class,
            com.agri_system.api.admin.controller.ProductController.class})
    static class Config {
        @Bean SaTokenDao saTokenDao() { return new SaTokenDaoDefaultImpl(); }
        @Bean LifecycleIsolationGate lifecycleIsolationGate() { return new LifecycleIsolationGate(); }
    }

    /** 仅测试使用：停用请求持有真实管理员锁，创建请求真实等待该锁；不替换SQL或仓储。 */
    @Intercepts(@Signature(type=Executor.class, method="query", args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    public static class LifecycleIsolationGate implements Interceptor {
        static volatile LifecycleRace race;
        @Override public Object intercept(Invocation invocation) throws Throwable {
            var statement=(MappedStatement)invocation.getArgs()[0];
            var request=(ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
            LifecycleRace active=race;
            if(active==null || request==null || !statement.getId().endsWith("SysRoleMapper.lockAdministratorRoles"))
                return invocation.proceed();
            String actor=request.getRequest().getHeader("X-Authority-Test-Race");
            if("creator".equals(actor)) {
                var connection=((Executor)invocation.getTarget()).getTransaction().getConnection();
                active.creatorIsolation=connection.getTransactionIsolation();
                try(var sql=connection.createStatement();var rs=sql.executeQuery("SELECT CONNECTION_ID()")) {
                    rs.next();active.creatorConnectionId=rs.getLong(1);
                }
                active.creatorAtLock.countDown();
                return invocation.proceed();
            }
            Object result=invocation.proceed();
            if("disabler".equals(actor)) {
                active.disablerHasLock.countDown();
                if(!active.releaseDisabler.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("测试停用角色释放锁超时");
            }
            return result;
        }
    }
    static class LifecycleRace {
        final CountDownLatch disablerHasLock=new CountDownLatch(1);
        final CountDownLatch creatorAtLock=new CountDownLatch(1);
        final CountDownLatch releaseDisabler=new CountDownLatch(1);
        volatile long creatorConnectionId;
        volatile int creatorIsolation;
    }

    @RestController
    static class Probe {
        @Autowired StaffRepository staff;
        @Autowired StaffMapper staffMapper;
        @Autowired StaffAuthorityService authority;
        @Autowired JdbcTemplate jdbc;
        @GetMapping("/auth/__authority__/login")
        Map<String, String> login(@RequestParam String id) {
            StpUtil.login(id);
            if (id.startsWith("STAFF_")) {
                long staffId=Long.parseLong(id.substring(6));
                StpUtil.getSession().set("roleCodes", authority.loadRoleCodes(staffId));
                Long storeId=jdbc.queryForObject("SELECT store_id FROM staff WHERE id=?", Long.class, staffId);
                if(storeId!=null) StpUtil.getSession().set("storeId", storeId);
            }
            return Map.of("token", StpUtil.getTokenValue());
        }
        @GetMapping("/api/admin/__authority__/read")
        @SaCheckPermission("probe:read")
        Map<String, Object> read() {
            return Map.of("code", 0, "ids", staff.findPage(null, null, null, null, 1, 100).getRecords()
                    .stream().map(s -> s.getId()).toList(), "admin", CurrentUser.isAdmin());
        }
        @GetMapping("/api/admin/__authority__/menus")
        Map<String, Object> menus() { return Map.of("code", 0, "menus", authority.getCurrentMenuTree(CurrentUser.staffId())); }
        @GetMapping("/api/admin/__authority__/inventory-view")
        @SaCheckPermission("probe:read")
        Map<String, Object> inventoryView() {
            // 使用生产库存分页相同的Flex DTO投影，让身份查询发生在租户SQL构建内部。
            return Map.of("code",0,"rows",staffMapper.selectListByQueryAs(
                    com.mybatisflex.core.query.QueryWrapper.create().select("id AS product_id","store_id")
                            .from(com.agri_system.domain.store.model.Staff.class),
                    com.agri_system.domain.inventory.vo.InventoryPageVO.class));
        }
    }

    @BeforeEach void seed() {
        CurrentUser.clearRequestCache();
        assertSame(stpInterface, SaManager.getStpInterface(), "必须使用生产权限解析器");
        assertTrue(TenantManager.getTenantFactory().getClass().getName().contains("DataScopeConfig"));
        for(String table:List.of("sys_role_menu","sys_staff_role","sys_menu","sys_role","staff","store"))
            jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("CREATE TABLE staff(id BIGINT AUTO_INCREMENT PRIMARY KEY,code VARCHAR(40),name VARCHAR(80),phone VARCHAR(40),store_id BIGINT,password VARCHAR(100),role VARCHAR(40),position VARCHAR(40),shift_type VARCHAR(40),salary_base DECIMAL(12,2),status INT,deleted INT,hire_date DATE,contract_end_date DATE,emergency_contact VARCHAR(80),emergency_phone VARCHAR(40),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE store(id BIGINT PRIMARY KEY,code VARCHAR(40),name VARCHAR(80),phone VARCHAR(40),address VARCHAR(100),manager_id BIGINT,status INT,support_pickup INT,remark VARCHAR(100),created_at TIMESTAMP,updated_at TIMESTAMP,created_by BIGINT,updated_by BIGINT,deleted INT)");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(80),code VARCHAR(40),status INT,remark VARCHAR(100),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_staff_role(id BIGINT AUTO_INCREMENT PRIMARY KEY,staff_id BIGINT,role_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,parent_id BIGINT,name VARCHAR(80),permission VARCHAR(80),type INT,path VARCHAR(100),icon VARCHAR(40),sort_order INT,visible INT)");
        jdbc.execute("CREATE TABLE sys_role_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,role_id BIGINT,menu_id BIGINT)");
        jdbc.update("INSERT INTO store(id,name,status,deleted) VALUES(1,'A',1,0),(2,'B',1,0),(3,'停用店',2,0)");
        role(1, "SUPER_ADMIN", 1); role(2,"EDITOR",1); role(3,"UPDATER",1); role(4,"CASHIER",1);
        role(5,"ASSIGNER",1); role(6,"ADMIN",1); role(7,"SECOND",1);
        employee(1,1,1,1); employee(2,1,1,2); employee(3,1,1,3); employee(4,1,1,4);
        employee(5,1,1,5); employee(6,2,1,6); employee(7,2,1,4);
        String[] perms={"role:save","role:assign","staff:update","staff:create","staff:disable","staff:enable","probe:read","staff:list","role:view","role:delete"};
        for(int i=0;i<perms.length;i++) {
            jdbc.update("INSERT INTO sys_menu(id,parent_id,name,permission,type,path,sort_order,visible) VALUES(?,0,?,?,2,?,1,1)",i+1,perms[i],perms[i],"/p"+(i+1));
            grant(1,i+1);grant(6,i+1);grant(5,i+1);
        }
        grant(2,1); grant(2,7); grant(3,3);grant(3,4);grant(3,7);grant(4,7);grant(7,9);
        when(codes.generateNextCode(any())).thenReturn("ROLE000088");
    }
    void role(long id,String code,int status){jdbc.update("INSERT INTO sys_role(id,name,code,status) VALUES(?,?,?,?)",id,code,code,status);}
    void employee(long id,long store,int status,long role){jdbc.update("INSERT INTO staff(id,code,name,phone,store_id,password,role,status,deleted) VALUES(?,?,?,?,?,'x','STAFF',?,0)",id,"S"+id,"员工"+id,"1380000000"+id,store,status); jdbc.update("INSERT INTO sys_staff_role(staff_id,role_id) VALUES(?,?)",id,role);}
    void grant(long role,long menu){jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(?,?)",role,menu);}
    String login(long id)throws Exception{return loginId("STAFF_"+id);}
    String loginId(String id)throws Exception{return json.readTree(mvc.perform(get("/auth/__authority__/login").param("id",id)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();}
    JsonNode call(String token,String method,String path,String body)throws Exception{
        MockHttpServletRequestBuilder req=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
        if(token!=null)req.header(SaManager.getConfig().getTokenName(),token);
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(body);
        return json.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    void ok(JsonNode r){assertEquals(0,r.path("code").asInt(-1),r.toString());}
    void reject(JsonNode r){assertTrue(Set.of(10001,10003,10004).contains(r.path("code").asInt(-1)),r.toString());}
    String update(long id,String fields){return "{\"id\":"+id+",\"name\":\"资料更新\",\"phone\":\"13800000000\",\"storeId\":1"+fields+"}";}
    List<Long> roles(long id){return jdbc.queryForList("SELECT role_id FROM sys_staff_role WHERE staff_id=? ORDER BY role_id",Long.class,id);}
    int state(String table,long id){return jdbc.queryForObject("SELECT status FROM "+table+" WHERE id=?",Integer.class,id);}

    void businessPermission(int menuId,String permission) {
        jdbc.update("INSERT INTO sys_menu(id,parent_id,name,permission,type,sort_order,visible) VALUES(?,0,?,?,2,0,1)",menuId,permission,permission);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(4,?)",menuId);
    }

    @Test void 移动查货仅真实库存权限可读且撤权立即拒绝() throws Exception {
        String body="{\"code\":\"P1\",\"purpose\":\"INVENTORY_LOOKUP\"}";
        String token=login(4);
        reject(call(null,"POST","/api/admin/mobile/scan/resolve",body));
        reject(call(loginId("CUST_1"),"POST","/api/admin/mobile/scan/resolve",body));
        reject(call(token,"POST","/api/admin/mobile/scan/resolve",body));
        businessPermission(110,"inventory:view");
        when(mobileInventory.resolve("P1","INVENTORY_LOOKUP",4L)).thenReturn(
                new com.agri_system.application.inventory.service.MobileScanQueryService.Resolution("1","UNKNOWN",List.of(),false));
        assertEquals("UNKNOWN",call(token,"POST","/api/admin/mobile/scan/resolve",body).path("type").asText());
        org.mockito.Mockito.verify(mobileInventory).resolve("P1","INVENTORY_LOOKUP",4L);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=4 AND menu_id=110");
        reject(call(token,"POST","/api/admin/mobile/scan/resolve",body));
        reject(call(token,"GET","/api/admin/mobile/inventory/1",null));
        reject(call(token,"GET","/api/admin/mobile/inventory/products?keyword=x",null));
        reject(call(token,"GET","/api/admin/mobile/locations/1/products",null));
    }

    @Test void 最小收银可读专用目录但不能读管理商品和门店档案() throws Exception {
        businessPermission(101,"pos:order:create");
        when(posCatalog.products(1,1000)).thenReturn(new com.mybatisflex.core.paginate.Page<>());
        when(posCatalog.categories()).thenReturn(List.of());
        String token=login(4);
        assertEquals(200,call(token,"GET","/api/admin/pos/catalog/products",null).path("code").asInt());
        assertEquals(200,call(token,"GET","/api/admin/pos/catalog/categories",null).path("code").asInt());
        reject(call(token,"GET","/api/admin/products",null));
        reject(call(token,"GET","/api/admin/stores",null));
        reject(call(token,"POST","/api/admin/products","{}"));
    }

    @Test void 没有收银权限不能访问目录() throws Exception {
        reject(call(login(4),"GET","/api/admin/pos/catalog/products",null));
        reject(call(null,"GET","/api/admin/pos/catalog/categories",null));
    }

    @Test void 库管仅提货权限可读门店选项而非管理档案() throws Exception {
        businessPermission(102,"order:pickup:verify");
        when(businessStores.businessOptions()).thenReturn(List.of(new com.agri_system.application.store.dto.vo.StoreOptionVO(1L,"A")));
        String token=login(4);
        var result=call(token,"GET","/api/admin/stores/business-options",null);
        assertEquals(200,result.path("code").asInt());assertEquals(1,result.path("data").size());
        assertFalse(result.path("data").get(0).has("phone"));
        reject(call(token,"GET","/api/admin/stores",null));
    }

    @Test void 库管只凭收货权限可读收货单但不能读取采购管理和创建采购() throws Exception {
        businessPermission(103,"purchase:inbound");
        when(receiving.receivingPage(any())).thenReturn(new com.mybatisflex.core.paginate.Page<>());
        var detail=new com.agri_system.application.purchase.dto.vo.PurchaseDetailVO();detail.setId(8L);detail.setStatus(2);
        when(receiving.receivingDetail(8L)).thenReturn(detail);String token=login(4);
        assertEquals(200,call(token,"GET","/api/admin/purchase/receiving-orders",null).path("code").asInt());
        assertEquals(200,call(token,"GET","/api/admin/purchase/receiving-orders/8",null).path("code").asInt());
        reject(call(token,"GET","/api/admin/purchase/list",null));
        reject(call(token,"POST","/api/admin/purchase/create","{}"));
    }

    @Test void 无员工身份不能写角色员工()throws Exception{
        reject(call(null,"POST","/api/admin/roles","{\"name\":\"n\"}"));
        reject(call(loginId("CUST_1"),"PUT","/api/admin/staff/3",update(3,",\"roleId\":1")));
    }
    @Test void 角色POST不能通过已有ID改授权()throws Exception{
        reject(call(login(2),"POST","/api/admin/roles","{\"id\":4,\"name\":\"冒用\",\"menuIds\":[2]}"));
        assertEquals("CASHIER",jdbc.queryForObject("SELECT name FROM sys_role WHERE id=4",String.class));
        assertEquals(List.of(7L),jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=4",Long.class));
    }
    @Test void 角色POST拒客户端管理员编码()throws Exception{
        reject(call(login(2),"POST","/api/admin/roles","{\"name\":\"冒用\",\"code\":\"ADMIN\"}"));
    }
    @Test void 角色基础保存不能授权非空菜单()throws Exception{
        String t=login(2);
        reject(call(t,"POST","/api/admin/roles","{\"name\":\"冒用\",\"menuIds\":[2]}"));
        reject(call(t,"PUT","/api/admin/roles/4","{\"name\":\"冒用\",\"menuIds\":[2]}"));
    }
    @Test void 创建服务端编码且空菜单不改变既有授权()throws Exception{
        String t=login(2); JsonNode r=call(t,"POST","/api/admin/roles","{\"name\":\"普通\",\"menuIds\":[]}");ok(r);
        assertEquals("ROLE000088",jdbc.queryForObject("SELECT code FROM sys_role WHERE id=?",String.class,r.path("data").asLong()));
        ok(call(t,"PUT","/api/admin/roles/4","{\"name\":\"更名\",\"code\":\"ADMIN\",\"menuIds\":null}"));
        assertEquals("CASHIER",jdbc.queryForObject("SELECT code FROM sys_role WHERE id=4",String.class));
        assertEquals(List.of(7L),jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=4",Long.class));
        reject(call(t,"POST","/api/admin/roles/4/menus","{\"menuIds\":[2]}"));
        ok(call(login(5),"POST","/api/admin/roles/4/menus","{\"roleId\":4,\"menuIds\":[7,9]}"));
    }
    @Test void 非法角色员工状态拒绝且不改库()throws Exception{
        reject(call(login(2),"PUT","/api/admin/roles/4","{\"name\":\"n\",\"status\":9}"));assertEquals(1,state("sys_role",4));
        reject(call(login(3),"PUT","/api/admin/staff/4",update(4,",\"roleId\":4,\"status\":0")));assertEquals(1,state("staff",4));
    }
    @Test void 员工编辑不能越权授管理员角色()throws Exception{
        reject(call(login(3),"PUT","/api/admin/staff/3",update(3,",\"roleId\":1")));assertEquals(List.of(3L),roles(3));
    }
    @Test void 创建携角色必须独立授予权限()throws Exception{
        reject(call(login(3),"POST","/api/admin/staff","{\"name\":\"新员\",\"phone\":\"13811111111\",\"storeId\":1,\"password\":\"abc123\",\"roleId\":1}"));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM staff",Integer.class));
    }
    @Test void 编辑缺失或NULL或已有角色保留全部多角色关联()throws Exception{
        jdbc.update("INSERT INTO sys_staff_role(staff_id,role_id) VALUES(4,7)");String t=login(3);
        for(String f:List.of("",",\"roleId\":null",",\"roleId\":4")){
            ok(call(t,"PUT","/api/admin/staff/4",update(4,f)));assertEquals(List.of(4L,7L),roles(4));
        }
    }
    @Test void 授予目标角色必须有效()throws Exception{
        jdbc.update("UPDATE sys_role SET status=0 WHERE id=7");
        reject(call(login(5),"PUT","/api/admin/staff/4",update(4,",\"roleId\":7")));assertEquals(List.of(4L),roles(4));
        ok(call(login(5),"PUT","/api/admin/staff/4",update(4,",\"roleId\":2")));assertEquals(List.of(2L),roles(4));
    }
    @Test void PUT离职不能绕过生命周期权限()throws Exception{
        reject(call(login(3),"PUT","/api/admin/staff/4",update(4,",\"roleId\":4,\"status\":2")));assertEquals(1,state("staff",4));
    }
    @Test void PUT本人离职受保护()throws Exception{
        reject(call(login(1),"PUT","/api/admin/staff/1",update(1,",\"roleId\":1,\"status\":2")));assertEquals(1,state("staff",1));
    }
    @Test void 离职员工旧token立即拒绝()throws Exception{
        String t=login(4);ok(call(t,"GET","/api/admin/__authority__/read",null));
        ok(call(login(1),"PUT","/api/admin/staff/4",update(4,",\"roleId\":4,\"status\":2")));
        assertEquals(10003,call(t,"GET","/api/admin/__authority__/read",null).path("code").asInt());
    }
    @Test void 角色禁用原token失权且其它角色保留()throws Exception{
        String t=login(4);ok(call(t,"GET","/api/admin/__authority__/read",null));
        jdbc.update("INSERT INTO sys_staff_role(staff_id,role_id) VALUES(4,7)");
        ok(call(login(1),"PUT","/api/admin/roles/4","{\"name\":\"停用\",\"status\":0}"));
        reject(call(t,"GET","/api/admin/__authority__/read",null));
        JsonNode menus=call(t,"GET","/api/admin/__authority__/menus",null);ok(menus);
        assertFalse(menus.toString().contains("probe:read"));assertTrue(menus.toString().contains("role:view"));
        ok(call(t,"GET","/api/admin/roles",null));
    }
    @Test void 删除菜单授权同token下一请求生效()throws Exception{
        String t=login(4);ok(call(t,"GET","/api/admin/__authority__/read",null));
        ok(call(login(1),"POST","/api/admin/roles/4/menus","{\"roleId\":4,\"menuIds\":[]}"));
        reject(call(t,"GET","/api/admin/__authority__/read",null));
    }
    @Test void 管理员降权同token不能继续跨店且不信旧Session()throws Exception{
        String t=login(1);JsonNode before=call(t,"GET","/api/admin/__authority__/read",null);ok(before);assertTrue(before.path("ids").toString().contains("7"));
        ok(call(login(6),"PUT","/api/admin/staff/1",update(1,",\"roleId\":4")));
        JsonNode after=call(t,"GET","/api/admin/__authority__/read",null);ok(after);assertFalse(after.path("admin").asBoolean());assertFalse(after.path("ids").toString().contains("7"));
    }
    @Test void 普通员工不能通过更新自身门店跨店()throws Exception{
        String t=login(3);reject(call(t,"PUT","/api/admin/staff/3",update(3,",\"roleId\":3").replace("\"storeId\":1","\"storeId\":2")));
        assertEquals(1L,jdbc.queryForObject("SELECT store_id FROM staff WHERE id=3",Long.class));
        JsonNode read=call(t,"GET","/api/admin/__authority__/read",null);ok(read);assertFalse(read.path("ids").toString().contains("7"));
    }
    @Test void 普通员工不能创建他店员工管理员可合法调店()throws Exception{
        String body="{\"name\":\"新员\",\"phone\":\"13811111111\",\"code\":\"NEW1\",\"storeId\":2,\"password\":\"abc123\",\"roleId\":4}";
        reject(call(login(5),"POST","/api/admin/staff",body));
        String t=login(4);ok(call(login(1),"PUT","/api/admin/staff/4",update(4,",\"roleId\":4").replace("\"storeId\":1","\"storeId\":2")));
        JsonNode read=call(t,"GET","/api/admin/__authority__/read",null);ok(read);assertTrue(read.path("ids").toString().contains("7"),read+" store="+jdbc.queryForObject("SELECT store_id FROM staff WHERE id=4",Long.class));assertFalse(read.path("ids").toString().contains("3"));
        reject(call(login(1),"PUT","/api/admin/staff/4",update(4,",\"roleId\":4").replace("\"storeId\":1","\"storeId\":3")));
    }
    @Test void 无店普通员工不获得全店数据()throws Exception{
        jdbc.update("UPDATE staff SET store_id=NULL WHERE id=4");reject(call(login(4),"GET","/api/admin/__authority__/read",null));
    }
    @Test void 空管理员角色或离职管理员不能替代最后管理员()throws Exception{
        jdbc.update("UPDATE staff SET status=2 WHERE id=6");role(8,"ADMIN",1);String t=login(5);
        reject(call(t,"PUT","/api/admin/roles/1","{\"name\":\"停用\",\"status\":0}"));assertEquals(1,state("sys_role",1));
        reject(call(t,"PUT","/api/admin/staff/1",update(1,",\"roleId\":4")));assertEquals(List.of(1L),roles(1));
        reject(call(t,"POST","/api/admin/staff/1/disable",null));assertEquals(1,state("staff",1));
    }
    @Test void 员工仍持另一管理员角色允许停用其中角色()throws Exception{
        jdbc.update("UPDATE staff SET status=2 WHERE id=6");jdbc.update("INSERT INTO sys_staff_role(staff_id,role_id) VALUES(1,6)");
        ok(call(login(5),"PUT","/api/admin/roles/1","{\"name\":\"停用\",\"status\":0}"));assertEquals(0,state("sys_role",1));
        ok(call(login(1),"GET","/api/admin/__authority__/read",null));
    }
    @RepeatedTest(5) void 双管理员并发互停只能成功一方()throws Exception{
        String a=login(1),b=login(6); ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{
            Future<JsonNode> x=pool.submit(()->{gate.await();return call(a,"POST","/api/admin/staff/6/disable",null);});
            Future<JsonNode> y=pool.submit(()->{gate.await();return call(b,"POST","/api/admin/staff/1/disable",null);});gate.countDown();
            List<JsonNode> out=List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));
            assertEquals(1,out.stream().filter(r->r.path("code").asInt()==0).count(),out.toString());
            out.stream().filter(r->r.path("code").asInt()!=0).forEach(this::reject);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE id IN(1,6) AND status=1",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void 普通员工首次DTO库存投影不会污染内部身份类型且仍隔离门店()throws Exception {
        JsonNode result=call(login(4),"GET","/api/admin/__authority__/inventory-view",null);
        ok(result);assertTrue(result.path("rows").size()>0,"不能用空结果掩盖映射异常");
        for(JsonNode row:result.path("rows")) assertEquals(1L,row.path("storeId").asLong(),result.toString());
    }
    @Test void 数据库离职或删除无需踢token也立即拒绝()throws Exception {
        String t=login(4);ok(call(t,"GET","/api/admin/__authority__/read",null));
        jdbc.update("UPDATE staff SET status=2 WHERE id=4");
        assertEquals(10003,call(t,"GET","/api/admin/__authority__/read",null).path("code").asInt());
        jdbc.update("UPDATE staff SET status=1,deleted=1 WHERE id=4");
        assertEquals(10003,call(t,"GET","/api/admin/__authority__/read",null).path("code").asInt());
    }
    @Test void 管理员角色停用同token失去跨店身份()throws Exception {
        String t=login(1);ok(call(t,"GET","/api/admin/__authority__/read",null));
        jdbc.update("INSERT INTO sys_staff_role(staff_id,role_id) VALUES(1,4)");
        ok(call(login(5),"PUT","/api/admin/roles/1","{\"name\":\"停用\",\"status\":0}"));
        JsonNode r=call(t,"GET","/api/admin/__authority__/read",null);ok(r);
        assertFalse(r.path("admin").asBoolean());assertFalse(r.path("ids").toString().contains("7"));
    }
    @Test void 在职但管理员角色已停用不算最后管理员替代()throws Exception {
        jdbc.update("UPDATE sys_role SET status=0 WHERE id=6");
        JsonNode r=call(login(5),"PUT","/api/admin/staff/1",update(1,",\"roleId\":4"));
        assertEquals(10001,r.path("code").asInt(),r.toString());assertEquals(List.of(1L),roles(1));
    }
    @Test void PUT复职复用独立启用权限()throws Exception {
        jdbc.update("UPDATE staff SET status=2 WHERE id=4");
        JsonNode denied=call(login(3),"PUT","/api/admin/staff/4",update(4,",\"status\":1"));
        assertEquals(10004,denied.path("code").asInt(),denied.toString());assertEquals(2,state("staff",4));
        ok(call(login(1),"PUT","/api/admin/staff/4",update(4,",\"status\":1")));assertEquals(1,state("staff",4));
    }
    @RepeatedTest(3) void 角色停用与员工降权并发共享管理员锁()throws Exception {
        String a=login(1),b=login(6);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try {
            Future<JsonNode> x=pool.submit(()->{gate.await();return call(a,"PUT","/api/admin/roles/6","{\"name\":\"停用\",\"status\":0}");});
            Future<JsonNode> y=pool.submit(()->{gate.await();return call(b,"PUT","/api/admin/staff/1",update(1,",\"roleId\":4"));});
            gate.countDown();List<JsonNode> out=List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));
            assertEquals(1,out.stream().filter(r->r.path("code").asInt()==0).count(),out.toString());
            out.stream().filter(r->r.path("code").asInt()!=0).forEach(this::reject);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(DISTINCT s.id) FROM staff s JOIN sys_staff_role sr ON sr.staff_id=s.id JOIN sys_role r ON r.id=sr.role_id WHERE s.status=1 AND s.deleted=0 AND r.status=1 AND r.code IN('ADMIN','SUPER_ADMIN')",Integer.class));
        } finally {pool.shutdownNow();}
    }
    @Test
    @EnabledIfSystemProperty(named="authority.mysql.it",matches="true")
    void Lifecycle创建等待管理员锁后拒绝刚被停用的目标角色()throws Exception {
        String admin=login(1), creator=login(5);LifecycleRace race=new LifecycleRace();LifecycleIsolationGate.race=race;
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> disabling=pool.submit(()->raceCall(admin,"disabler",put("/api/admin/roles/4"),"{\"name\":\"停用目标\",\"status\":0}"));
            assertTrue(race.disablerHasLock.await(10,TimeUnit.SECONDS),"实际停用请求必须先持有管理员行锁");
            Future<JsonNode> creating=pool.submit(()->raceCall(creator,"creator",post("/api/admin/staff"),"{\"code\":\"RACE_NEW\",\"name\":\"锁后创建\",\"phone\":\"13899999999\",\"storeId\":1,\"password\":\"abc123\",\"roleId\":4}"));
            assertTrue(race.creatorAtLock.await(10,TimeUnit.SECONDS),"真实Lifecycle创建必须走到管理员锁");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
            while(System.nanoTime()<deadline) {
                Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=? AND trx_state='LOCK WAIT'",Integer.class,race.creatorConnectionId);
                if(count!=null && count==1) {waiting=true;break;}
                Thread.sleep(20);
            }
            assertTrue(waiting,"MySQL必须证实创建请求在真实锁等待，不能仅靠线程启动或时序猜测");
            race.releaseDisabler.countDown();
            ok(disabling.get(15,TimeUnit.SECONDS));assertEquals(0,state("sys_role",4));
            JsonNode result=creating.get(15,TimeUnit.SECONDS);
            System.out.println("[Lifecycle MySQL] 创建真实隔离="+race.creatorIsolation+"，目标角色状态="+state("sys_role",4)+"，HTTP="+result);
            assertEquals(10001,result.path("code").asInt(),"等待期间角色已停用，创建必须拒绝："+result);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE code='RACE_NEW'",Integer.class),"拒绝不能留下员工或角色关联");
            assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM sys_staff_role",Integer.class));
            assertEquals(java.sql.Connection.TRANSACTION_READ_COMMITTED,race.creatorIsolation,"真实Lifecycle入口必须使用Authority事务隔离");
        } finally {
            race.releaseDisabler.countDown();pool.shutdownNow();pool.awaitTermination(15,TimeUnit.SECONDS);LifecycleIsolationGate.race=null;
        }
    }
    JsonNode raceCall(String token,String actor,MockHttpServletRequestBuilder request,String body)throws Exception {
        return json.readTree(mvc.perform(request.header(SaManager.getConfig().getTokenName(),token)
                .header("X-Authority-Test-Race",actor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    static Object staticField(String name){try{Field f=CurrentUser.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
    @AfterAll static void restore()throws Exception{
        if(mysqlCreated) {
            try(var c=DriverManager.getConnection(mysqlServerUrl,mysqlUser,mysqlPassword);var st=c.createStatement()) {
                st.execute("DROP DATABASE "+MYSQL_DATABASE); mysqlCreated=false;
                System.out.println("[Authority HTTP] 本次随机MySQL库已清理："+MYSQL_DATABASE);
            }
        }
        CurrentUser.clearRequestCache();TenantManager.setTenantFactory(OLD_TENANT);SaManager.setStpInterface(OLD_STP);SaManager.setSaTokenDao(OLD_DAO);
        SaManager.setSaTokenContext(OLD_CONTEXT);SaManager.setConfig(OLD_CONFIG);
        for(var e:Map.of("staffRepository",Optional.ofNullable(OLD_STAFF),"roleRepository",Optional.ofNullable(OLD_ROLE)).entrySet()){
            Field f=CurrentUser.class.getDeclaredField(e.getKey());f.setAccessible(true);f.set(null,e.getValue().orElse(null));
        }
    }
}

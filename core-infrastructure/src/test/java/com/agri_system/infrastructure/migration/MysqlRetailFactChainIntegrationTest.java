package com.agri_system.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * imp6: 真实 MySQL/InnoDB 集成测试。
 *
 * <p>H2(MODE=MySQL) 无法复刻 InnoDB 的行锁、append-only 触发器(被 {@code /*!80016 *}{@code /} 条件注释在 H2 下跳过)、
 * 以及部分 CHECK/生成列语义,因此 V119-V124 此前只在 H2 验证过。本测试在真实 MySQL 上首次执行这批迁移并验证:
 * <ol>
 *   <li>V119-V124 迁移 DDL 在真实 InnoDB 上可执行(生成列/CHECK/DROP CONSTRAINT/append-only 触发器语法);</li>
 *   <li>V121 的 append-only 触发器与 CHECK 约束在真实 MySQL 上生效;</li>
 *   <li>V123 默认后仓生成列唯一约束拒绝同店第二个默认货位,且生成列计算正确;</li>
 *   <li>V124 幂等键唯一约束拒绝重复、允许多行 NULL,且违约事务干净回滚;</li>
 *   <li>projectInventory 依赖的 {@code SELECT ... FOR UPDATE} 在 InnoDB 下真正加行锁,串行化并发投影。</li>
 * </ol>
 *
 * <p>默认关闭,避免污染常规 {@code mvn test};显式 {@code -Dmysql.it=true} 开启。
 * 使用独立数据库(BeforeAll 建、AfterAll 删),不触碰 dev 库。连接参数可用系统属性覆盖:
 * {@code mysql.it.host/port/user/password/database}(默认 127.0.0.1:3306, root 无密码, agri_retail_it)。
 * MySQL 不可达或无建库权限时通过 {@link org.junit.jupiter.api.Assumptions} 优雅跳过。
 */
@EnabledIfSystemProperty(named = "mysql.it", matches = "true")
class MysqlRetailFactChainIntegrationTest {

    private static final String HOST = System.getProperty("mysql.it.host", "127.0.0.1");
    private static final String PORT = System.getProperty("mysql.it.port", "3306");
    private static final String USER = System.getProperty("mysql.it.user", "root");
    private static final String PASSWORD = System.getProperty("mysql.it.password", "");
    private static final String DB = System.getProperty("mysql.it.database", "agri_retail_it");

    private static final String URL_PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&allowMultiQueries=false";

    /** baseline 118 所需的最小 legacy 表,与 RetailFactChainMigrationTest 一致,另加 FOR UPDATE 用的 inventory 快照表。 */
    private static final String[] LEGACY_DDL = {
            "CREATE TABLE `store` (`id` BIGINT PRIMARY KEY) ENGINE=InnoDB",
            "CREATE TABLE `staff` (`id` BIGINT PRIMARY KEY) ENGINE=InnoDB",
            "CREATE TABLE `supplier` (`id` BIGINT PRIMARY KEY) ENGINE=InnoDB",
            "CREATE TABLE `purchase_order` (`id` BIGINT PRIMARY KEY) ENGINE=InnoDB",
            "CREATE TABLE `purchase_order_item` (`id` BIGINT PRIMARY KEY, `product_id` BIGINT NOT NULL) ENGINE=InnoDB",
            "CREATE TABLE `product` (`id` BIGINT PRIMARY KEY, `main_unit` VARCHAR(20) NOT NULL,"
                    + " `aux_unit` VARCHAR(20), `main_to_aux_ratio` DECIMAL(10,4)) ENGINE=InnoDB",
            "CREATE TABLE `inventory_batch` (`id` BIGINT PRIMARY KEY, `quantity` DECIMAL(18,8) NOT NULL) ENGINE=InnoDB",
            "CREATE TABLE `order_item` (`id` BIGINT PRIMARY KEY, `product_id` BIGINT NOT NULL,"
                    + " `unit` VARCHAR(20) NOT NULL) ENGINE=InnoDB",
            // 合并 master 后 V122(提成冻结索引)进入迁移路径,需其依赖表 staff_performance_log(V118 后最小投影)
            "CREATE TABLE `staff_performance_log` (`id` BIGINT PRIMARY KEY, `staff_id` BIGINT NOT NULL,"
                    + " `status` TINYINT NOT NULL DEFAULT 2, `freeze_until` DATETIME NULL) ENGINE=InnoDB",
            "CREATE TABLE `inventory` (`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `store_id` BIGINT NOT NULL,"
                    + " `product_id` BIGINT NOT NULL, `quantity` DECIMAL(10,4) NOT NULL DEFAULT 0.0000,"
                    + " `unit` VARCHAR(20) NOT NULL, `batch_no` VARCHAR(50), `version` INT NOT NULL DEFAULT 0,"
                    + " `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + " UNIQUE KEY `uk_inventory_store_product` (`store_id`, `product_id`, `batch_no`)) ENGINE=InnoDB"
    };

    private static String serverUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + URL_PARAMS;
    }

    private static String dbUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB + URL_PARAMS;
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(dbUrl(), USER, PASSWORD);
    }

    @BeforeAll
    static void provisionFreshSchemaAndRunMigrations() {
        try (Connection server = DriverManager.getConnection(serverUrl(), USER, PASSWORD);
             Statement statement = server.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + DB + "`");
            statement.execute("CREATE DATABASE `" + DB + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException exception) {
            assumeTrue(false, "真实 MySQL 不可达或无建库权限,跳过 imp6 集成测试: " + exception.getMessage());
        }
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            for (String ddl : LEGACY_DDL) {
                statement.execute(ddl);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("创建最小 legacy 表失败", exception);
        }
        Flyway.configure()
                .dataSource(dbUrl(), USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("118")
                .target(MigrationVersion.LATEST)
                .load()
                .migrate();
    }

    @AfterAll
    static void dropSchema() {
        try (Connection server = DriverManager.getConnection(serverUrl(), USER, PASSWORD);
             Statement statement = server.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + DB + "`");
        } catch (SQLException ignored) {
            // 清理失败不影响测试结论
        }
    }

    @Test
    void migrationsApplyOnRealMysqlUpToV124WithGeneratedColumnsAndIdempotencyColumn() throws SQLException {
        try (Connection connection = open()) {
            assertEquals("124", queryString(connection,
                    "SELECT version FROM flyway_schema_history WHERE success = 1"
                            + " ORDER BY installed_rank DESC LIMIT 1"));
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '"
                    + DB + "' AND TABLE_NAME = 'inventory_location' AND COLUMN_NAME = 'default_location_key'"));
            assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '"
                    + DB + "' AND TABLE_NAME = 'inventory_movement' AND COLUMN_NAME = 'idempotency_key'"));

            // 生成列在真实 MySQL 上按 code 是否为空计算
            disableForeignKeyChecks(connection);
            executeUpdate(connection, "INSERT INTO inventory_location (store_id, location_type, code, name)"
                    + " VALUES (8001, 'STORE_BACKROOM', NULL, '门店后仓')");
            executeUpdate(connection, "INSERT INTO inventory_location (store_id, location_type, code, name)"
                    + " VALUES (8001, 'SALES_FLOOR', 'FLOOR-A', '货架A')");
            assertEquals("STORE_BACKROOM", queryString(connection,
                    "SELECT default_location_key FROM inventory_location WHERE store_id = 8001 AND code IS NULL"));
            // code 非空的显式货位,生成列为 NULL,不落入默认唯一约束
            assertEquals(1, queryInt(connection,
                    "SELECT COUNT(*) FROM inventory_location WHERE store_id = 8001"
                            + " AND code = 'FLOOR-A' AND default_location_key IS NULL"));
        }
    }

    @Test
    void inventoryMovementEnforcesAppendOnlyTriggersAndCheckConstraintsOnRealMysql() throws SQLException {
        try (Connection connection = open()) {
            disableForeignKeyChecks(connection);
            insertMovement(connection, "M-APPEND-1", "IDEM-APPEND-1", "REPLENISHMENT", 1);

            // V121 的 /*!80016 CREATE TRIGGER */ 在 MySQL 9.x 上确实建出 append-only 触发器
            SQLException onUpdate = assertThrows(SQLException.class, () -> executeUpdate(connection,
                    "UPDATE inventory_movement SET base_quantity = base_quantity + 1 WHERE movement_no = 'M-APPEND-1'"));
            assertTrue(onUpdate.getMessage().contains("append-only"),
                    "UPDATE 应被 append-only 触发器拒绝,实际: " + onUpdate.getMessage());
            SQLException onDelete = assertThrows(SQLException.class, () -> executeUpdate(connection,
                    "DELETE FROM inventory_movement WHERE movement_no = 'M-APPEND-1'"));
            assertTrue(onDelete.getMessage().contains("append-only"),
                    "DELETE 应被 append-only 触发器拒绝,实际: " + onDelete.getMessage());

            // V121 重写的 movement_type CHECK 拒绝旧类型
            assertThrows(SQLException.class, () -> insertMovement(connection,
                    "M-BADTYPE", "IDEM-BADTYPE", "RECEIPT", 1));
            // V119 数量 CHECK 拒绝非正数
            assertThrows(SQLException.class, () -> insertMovement(connection,
                    "M-ZEROQTY", "IDEM-ZEROQTY", "REPLENISHMENT", 0));
        }
    }

    @Test
    void defaultBackroomUniqueConstraintRejectsSecondDefaultLocationPerStoreOnRealMysql() throws SQLException {
        try (Connection connection = open()) {
            disableForeignKeyChecks(connection);
            executeUpdate(connection, "INSERT INTO inventory_location (store_id, location_type, code, name)"
                    + " VALUES (9001, 'STORE_BACKROOM', NULL, '门店后仓')");
            // 同店第二个默认(code=NULL)后仓撞 uk_inventory_location_default
            SQLException duplicate = assertThrows(SQLException.class, () -> executeUpdate(connection,
                    "INSERT INTO inventory_location (store_id, location_type, code, name)"
                            + " VALUES (9001, 'STORE_BACKROOM', NULL, '门店后仓重复')"));
            assertTrue(duplicate.getMessage().toLowerCase().contains("duplicate"),
                    "第二个默认后仓应撞唯一约束,实际: " + duplicate.getMessage());
            // 不同门店的默认后仓互不冲突
            executeUpdate(connection, "INSERT INTO inventory_location (store_id, location_type, code, name)"
                    + " VALUES (9002, 'STORE_BACKROOM', NULL, '另一门店后仓')");
            assertEquals(1, queryInt(connection,
                    "SELECT COUNT(*) FROM inventory_location WHERE store_id = 9001 AND code IS NULL"));
        }
    }

    @Test
    void idempotencyKeyUniqueRejectsDuplicateAllowsNullsAndRollsBackCleanlyOnRealMysql() throws SQLException {
        try (Connection connection = open()) {
            disableForeignKeyChecks(connection);
            connection.setAutoCommit(false);
            insertMovement(connection, "M-IDEM-1", "IDEM-DUP", "COUNT_ADJUSTMENT", 1);
            connection.commit();

            // 重复幂等键在真实唯一索引下被拒,且违约事务回滚后不留痕
            assertThrows(SQLException.class, () -> insertMovement(connection, "M-IDEM-2", "IDEM-DUP",
                    "COUNT_ADJUSTMENT", 1));
            connection.rollback();
            assertEquals(0, queryInt(connection,
                    "SELECT COUNT(*) FROM inventory_movement WHERE movement_no = 'M-IDEM-2'"));

            // 补货/拣货不强制幂等键,多行 NULL 必须能在唯一索引下共存
            insertMovement(connection, "M-NULL-1", null, "REPLENISHMENT", 1);
            insertMovement(connection, "M-NULL-2", null, "PICK", 1);
            connection.commit();
            assertTrue(queryInt(connection,
                    "SELECT COUNT(*) FROM inventory_movement WHERE idempotency_key IS NULL") >= 2);
        }
    }

    @Test
    void selectForUpdateAcquiresInnodbRowLockSerializingConcurrentProjection() throws Exception {
        long storeId = 9100L;
        long productId = 9200L;
        try (Connection setup = open()) {
            disableForeignKeyChecks(setup);
            executeUpdate(setup, "INSERT INTO inventory (store_id, product_id, quantity, unit, batch_no)"
                    + " VALUES (" + storeId + ", " + productId + ", 0.0000, '瓶', 'B-FORUPDATE')");
        }
        // 与 InventoryMapper.findByStoreAndProductForUpdate 完全一致的锁 SQL
        String lockSql = "SELECT * FROM inventory WHERE store_id = " + storeId
                + " AND product_id = " + productId + " FOR UPDATE";

        Connection holder = open();
        holder.setAutoCommit(false);
        holder.createStatement().executeQuery(lockSql); // 持有行锁,不提交

        Connection waiter = open();
        waiter.setAutoCommit(false);
        executeUpdate(waiter, "SET SESSION innodb_lock_wait_timeout = 1");
        long start = System.nanoTime();
        SQLException blocked = assertThrows(SQLException.class,
                () -> waiter.createStatement().executeQuery(lockSql));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(blocked.getMessage().toLowerCase().contains("lock wait timeout"),
                "并发投影应因行锁等待超时被串行化,实际: " + blocked.getMessage());
        assertTrue(elapsedMs >= 500, "应等待接近 innodb_lock_wait_timeout 才失败,证明行锁生效,实际 " + elapsedMs + "ms");

        holder.rollback();
        holder.close();
        waiter.rollback();
        waiter.close();

        // 持锁方释放后,新的 FOR UPDATE 立即成功
        try (Connection after = open()) {
            after.setAutoCommit(false);
            executeUpdate(after, "SET SESSION innodb_lock_wait_timeout = 2");
            assertNotNull(after.createStatement().executeQuery(lockSql));
            after.rollback();
        }
    }

    private static void disableForeignKeyChecks(Connection connection) throws SQLException {
        executeUpdate(connection, "SET FOREIGN_KEY_CHECKS = 0");
    }

    private static void executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** 直接插入 inventory_movement;绕过深层 FK 链,仅验证目标约束/锁语义(FK_CHECKS 已在调用方关闭)。 */
    private static void insertMovement(Connection connection, String movementNo, String idempotencyKey,
                                       String movementType, int quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO inventory_movement (movement_no, idempotency_key, batch_id, product_id, store_id,"
                        + " movement_type, original_unit_name, original_quantity, unit_cost, base_quantity)"
                        + " VALUES (?, ?, 1, 1, 1, ?, '瓶', ?, 1, ?)")) {
            statement.setString(1, movementNo);
            statement.setString(2, idempotencyKey);
            statement.setString(3, movementType);
            statement.setInt(4, quantity);
            statement.setInt(5, quantity);
            statement.executeUpdate();
        }
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}

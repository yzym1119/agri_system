package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 把旧主辅单位价格口径一次性转换为持久化基准单位；不改成本、历史订单及旧策略记录。 */
public class V130__simplify_product_pricing extends BaseJavaMigration {
    private record Prices(long id, BigDecimal retail, BigDecimal member, BigDecimal floor) {}

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<Prices> converted = new ArrayList<>();
        // 先检查全部商品，遇到无法确定的计价单位就停止，不猜单位、不猜价格。
        try (Statement statement = connection.createStatement();
             ResultSet products = statement.executeQuery("SELECT id,main_unit,aux_unit,suggested_retail_price,vip_price,min_price FROM product ORDER BY id")) {
            while (products.next()) {
                long id = products.getLong(1);
                String oldPriceUnit = products.getString(3);
                if (oldPriceUnit == null || oldPriceUnit.isBlank()) oldPriceUnit = products.getString(2);
                BigDecimal coefficient;
                try (PreparedStatement units = connection.prepareStatement("SELECT base_quantity_per_unit FROM product_unit WHERE product_id=? AND unit_name=? AND status=1")) {
                    units.setLong(1, id); units.setString(2, oldPriceUnit);
                    try (ResultSet unit = units.executeQuery()) {
                        if (!unit.next()) throw invalid(id, "旧计价单位未配置或未启用: " + oldPriceUnit);
                        coefficient = unit.getBigDecimal(1);
                        if (coefficient == null || coefficient.signum() <= 0 || unit.next()) throw invalid(id, "旧计价单位系数无效或不唯一");
                    }
                }
                BigDecimal retail = convert(products.getBigDecimal(4), coefficient, id);
                BigDecimal member = convert(products.getBigDecimal(5), coefficient, id);
                BigDecimal floor = convert(products.getBigDecimal(6), coefficient, id);
                if (positive(member) && (!positive(retail) || member.compareTo(retail) > 0)) throw invalid(id, "会员价高于零售价，请先核对商品价格");
                if (positive(floor) && (!positive(retail) || floor.compareTo(retail) > 0)) throw invalid(id, "最低价高于零售价，请先核对商品价格");
                if (positive(member) && positive(floor) && member.compareTo(floor) < 0) throw invalid(id, "会员价低于最低价，请先核对商品价格");
                converted.add(new Prices(id, retail, member, floor));
            }
        }
        boolean autoCommit = connection.getAutoCommit();
        if (autoCommit) connection.setAutoCommit(false);
        try {
            try (PreparedStatement update = connection.prepareStatement("UPDATE product SET suggested_retail_price=?,vip_price=?,min_price=? WHERE id=?")) {
                for (Prices price : converted) {
                    update.setBigDecimal(1, price.retail); update.setBigDecimal(2, price.member);
                    update.setBigDecimal(3, price.floor); update.setLong(4, price.id); update.executeUpdate();
                }
            }
            grantNegotiationPermission(connection);
            if (autoCommit) connection.commit();
        } catch (Exception failure) {
            connection.rollback(); throw failure;
        } finally {
            if (autoCommit) connection.setAutoCommit(true);
        }
    }

    private BigDecimal convert(BigDecimal price, BigDecimal coefficient, long id) {
        if (price == null) return null;
        if (price.signum() < 0) throw invalid(id, "商品价格不能为负");
        BigDecimal converted;
        try { converted = price.divide(coefficient, 2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException failure) { throw invalid(id, "旧售价不能精确换算为两位小数基准价，请先核对计价单位"); }
        if (converted.precision() - converted.scale() > 8 || (price.signum() > 0 && converted.signum() == 0)) {
            throw invalid(id, "基准单位价格超出金额范围，请先核对单位与价格");
        }
        return converted;
    }

    private void grantNegotiationPermission(Connection connection) throws SQLException {
        long menuId = 0;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id FROM sys_menu WHERE permission='order:price:override'")) {
            if (result.next()) menuId = result.getLong(1);
        }
        if (menuId == 0) {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(id),0) FROM sys_menu")) {
                result.next(); menuId = Math.max(2710, result.getLong(1) + 1);
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sys_menu(id,parent_id,name,permission,type,sort_order,visible) VALUES(?,1200,'销售议价','order:price:override',2,50,1)")) {
                insert.setLong(1, menuId); insert.executeUpdate();
            }
        }
        // 只默认授予超管，其他角色由老板通过现有角色权限界面分配。
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sys_role_menu(role_id,menu_id) SELECT id,? FROM sys_role WHERE id=1 AND NOT EXISTS(SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=?)")) {
            insert.setLong(1, menuId); insert.setLong(2, menuId); insert.executeUpdate();
        }
    }
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private IllegalStateException invalid(long id, String problem) {
        return new IllegalStateException("价格统一迁移失败，商品 " + id + ": " + problem);
    }
}

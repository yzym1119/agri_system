package com.agri_system.infrastructure.inventory.query;

import com.agri_system.domain.inventory.query.MobileInventoryQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** 员工查货的只读聚合。沿用既有批次/货位账，不提供出入库授权或成本数据。 */
@Repository
@RequiredArgsConstructor
public class MobileInventoryQueryImpl implements MobileInventoryQuery {
    private final JdbcTemplate jdbc;
    private static final int MATCH_LIMIT=20, PAGE_SIZE=30;

    public Resolution resolveInStore(String code,String purpose,Long storeId) {
        long store=storeId;
        if(!"INVENTORY_LOOKUP".equals(purpose)) throw new IllegalArgumentException("当前仅支持查货扫码");
        String value=text(code,100);
        // 每类取上限+1，不能用SKU的第一页或前缀推断唯一性；实际批次和货位按门店取。
        List<Match> all=new ArrayList<>(jdbc.query("""
                SELECT DISTINCT p.id,p.code,p.name,p.status,u.id AS unit_id,u.unit_name
                FROM product p LEFT JOIN product_unit u ON u.product_id=p.id AND u.status=1 AND u.unit_name=p.main_unit
                WHERE p.deleted=0 AND (p.code=? OR p.barcode=?)
                UNION
                SELECT p.id,p.code,p.name,p.status,u.id AS unit_id,u.unit_name
                FROM product p JOIN product_unit u ON u.product_id=p.id AND u.status=1
                WHERE p.deleted=0 AND u.barcode=?
                ORDER BY id,unit_id LIMIT 21
                """,(r,n)->sku(r),value,value,value));
        all.addAll(jdbc.query("""
                SELECT b.id,b.product_id,p.code,p.name,p.status,b.internal_receipt_batch_no
                FROM inventory_receipt_batch b JOIN product p ON p.id=b.product_id AND p.deleted=0
                WHERE b.store_id=? AND b.internal_receipt_batch_no=? ORDER BY b.id LIMIT 21
                """,(r,n)->new Match("RECEIPT_BATCH",id(r,"id"),id(r,"product_id"),r.getString("code"),
                r.getString("name"),null,null,r.getInt("status")==1,r.getString("internal_receipt_batch_no")),store,value));
        all.addAll(jdbc.query("SELECT id,code,name,status FROM inventory_location WHERE store_id=? AND code=? ORDER BY id LIMIT 21",
                (r,n)->new Match("LOCATION",id(r,"id"),null,null,r.getString("name"),null,null,r.getInt("status")==1,r.getString("code")),store,value));
        boolean more=all.size()>MATCH_LIMIT;
        List<Match> matches=List.copyOf(all.subList(0,Math.min(all.size(),MATCH_LIMIT)));
        String type=all.isEmpty()?"UNKNOWN":all.size()==1?all.get(0).type():"AMBIGUOUS";
        return new Resolution(Long.toString(store),type,matches,more);
    }

    public Matches searchProductsInStore(String keyword,int page,Long storeId) {
        long store=storeId;int offset=offset(page,MATCH_LIMIT);
        String literal=text(keyword,100).replace("!","!!").replace("%","!%").replace("_","!_");
        var matches=jdbc.query("""
                SELECT p.id,p.code,p.name,p.status,u.id AS unit_id,u.unit_name
                FROM product p LEFT JOIN product_unit u ON u.product_id=p.id AND u.status=1 AND u.unit_name=p.main_unit
                WHERE p.deleted=0 AND (p.code LIKE ? ESCAPE '!' OR p.name LIKE ? ESCAPE '!')
                ORDER BY p.id LIMIT 21 OFFSET ?
                """,(r,n)->sku(r),"%"+literal+"%","%"+literal+"%",offset);
        return matches(store,page,matches);
    }

    public Matches locationProductsInStore(Long locationId,int page,Long storeId) {
        long store=storeId;requireLocation(locationId,store);int offset=offset(page,MATCH_LIMIT);
        var matches=jdbc.query("""
                SELECT DISTINCT p.id,p.code,p.name,p.status,u.id AS unit_id,u.unit_name
                FROM inventory_batch_balance bb JOIN product p ON p.id=bb.product_id AND p.deleted=0
                LEFT JOIN product_unit u ON u.product_id=p.id AND u.status=1 AND u.unit_name=p.main_unit
                WHERE bb.store_id=? AND bb.location_id=? AND bb.base_quantity>0
                ORDER BY p.id LIMIT 21 OFFSET ?
                """,(r,n)->sku(r),store,locationId,offset);
        return matches(store,page,matches);
    }

    public InventoryView inventoryInStore(Long productId,Long batchId,Long locationId,int page,Long storeId) {
        long store=storeId;positive(productId);int offset=offset(page,PAGE_SIZE);
        var product=jdbc.query("""
                SELECT p.id,p.code,p.name,p.status,p.main_unit,u.base_quantity_per_unit
                FROM product p LEFT JOIN product_unit u ON u.product_id=p.id AND u.status=1 AND u.unit_name=p.main_unit
                WHERE p.id=? AND p.deleted=0
                """,(r,n)->new Product(r.getString("code"),r.getString("name"),r.getInt("status")==1,
                r.getString("main_unit"),r.getBigDecimal("base_quantity_per_unit")),productId);
        if(product.isEmpty()) throw new IllegalArgumentException("商品不存在或不可查询");
        if(locationId!=null) requireLocation(locationId,store);
        if(batchId!=null) {
            positive(batchId);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_receipt_batch WHERE id=? AND store_id=? AND product_id=?",Long.class,batchId,store,productId)!=1)
                throw new IllegalArgumentException("批次不存在或不可查询");
        }
        Product p=product.get(0);
        // 汇总和本页余额来自同一SQL快照；窗口汇总不受LIMIT影响，不把分页当总库存。
        String filter="""
                FROM inventory_batch_balance bb
                LEFT JOIN inventory_receipt_batch b ON b.id=bb.batch_id AND b.store_id=bb.store_id AND b.product_id=bb.product_id
                LEFT JOIN product_unit bu ON bu.id=b.base_unit_id AND bu.product_id=bb.product_id
                    AND bu.status=1 AND bu.is_base_unit=1 AND bu.base_quantity_per_unit=1
                LEFT JOIN inventory_location l ON l.id=bb.location_id AND l.store_id=bb.store_id
                WHERE bb.store_id=? AND bb.product_id=? AND bb.base_quantity>0
                """;
        List<Object> args=new ArrayList<>(List.of(store,productId));
        if(batchId!=null) {filter+=" AND bb.batch_id=?";args.add(batchId);}
        if(locationId!=null) {filter+=" AND bb.location_id=?";args.add(locationId);}
        args.add(offset);
        var rows=jdbc.query("""
                SELECT bb.batch_id,bb.location_id,bb.base_quantity,bb.reserved_base_quantity,
                    b.internal_receipt_batch_no,b.supplier_batch_no,b.original_unit_name,b.original_quantity,
                    b.production_date,b.expiry_date,bu.unit_name AS base_unit_name,l.name AS location_name,l.code AS location_code,l.status AS location_status,
                    SUM(bb.base_quantity) OVER() AS total_quantity,
                    SUM(bb.base_quantity-bb.reserved_base_quantity) OVER() AS total_unreserved,
                    SUM(CASE WHEN bu.id IS NULL THEN 1 ELSE 0 END) OVER() AS invalid_units,
                    COUNT(*) OVER() AS total_rows
                """+filter+" ORDER BY CASE WHEN b.expiry_date IS NULL THEN 1 ELSE 0 END,b.expiry_date,bb.batch_id,bb.location_id LIMIT 30 OFFSET ?",
                (r,n)->new StockRow(new Stock(id(r,"batch_id"),id(r,"location_id"),r.getString("internal_receipt_batch_no"),
                        r.getString("supplier_batch_no"),r.getString("location_name"),r.getString("location_code"),r.getInt("location_status")==1,
                        decimal(r.getBigDecimal("base_quantity")),decimal(r.getBigDecimal("base_quantity").subtract(r.getBigDecimal("reserved_base_quantity"))),
                        r.getString("base_unit_name"),r.getString("original_unit_name"),decimal(r.getBigDecimal("original_quantity")),
                        date(r,"production_date"),date(r,"expiry_date")),r.getBigDecimal("total_quantity"),r.getBigDecimal("total_unreserved"),
                        r.getLong("invalid_units"),r.getLong("total_rows")),args.toArray());
        if(rows.isEmpty()&&page>1) throw new IllegalArgumentException("该页已无库存记录，请返回首页刷新");
        StockRow first=rows.isEmpty()?null:rows.get(0);
        boolean convertible=p.ratio()!=null&&p.ratio().signum()>0&&(first==null||first.invalidUnits()==0);
        return new InventoryView(Long.toString(store),productId.toString(),p.code(),p.name(),p.enabled(),p.mainUnit(),
                convertible?divide(first==null?BigDecimal.ZERO:first.total(),p.ratio()):null,
                convertible?divide(first==null?BigDecimal.ZERO:first.unreserved(),p.ratio()):null,
                page,first==null?0:first.count(),rows.stream().map(StockRow::stock).toList());
    }

    private void requireLocation(Long id,long store) {
        positive(id);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_location WHERE id=? AND store_id=?",Long.class,id,store)!=1)
            throw new IllegalArgumentException("货位不存在或不可查询");
    }
    private static Match sku(ResultSet r) throws SQLException {
        return new Match("SKU",id(r,"id"),id(r,"id"),r.getString("code"),r.getString("name"),id(r,"unit_id"),
                r.getString("unit_name"),r.getInt("status")==1,r.getString("code"));
    }
    private static Matches matches(long store,int page,List<Match> rows) {
        return new Matches(Long.toString(store),page,List.copyOf(rows.subList(0,Math.min(rows.size(),MATCH_LIMIT))),rows.size()>MATCH_LIMIT);
    }
    private static String text(String input,int max) {
        if(input==null||input.isBlank()||input.trim().length()>max||input.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("请输入有效的编码或关键词，最多"+max+"字");
        return input.trim();
    }
    private static void positive(Long id) {if(id==null||id<=0) throw new IllegalArgumentException("编号必须是正整数");}
    private static int offset(int page,int size) {
        if(page<1||page>10000) throw new IllegalArgumentException("页码超出范围");return (page-1)*size;
    }
    private static String id(ResultSet r,String field) throws SQLException {long value=r.getLong(field);return r.wasNull()?null:Long.toString(value);}
    private static String date(ResultSet r,String field) throws SQLException {var value=r.getDate(field);return value==null?null:value.toLocalDate().toString();}
    private static String decimal(BigDecimal value) {return value==null?null:value.stripTrailingZeros().toPlainString();}
    private static String divide(BigDecimal value,BigDecimal ratio) {return decimal(value.divide(ratio,8,RoundingMode.HALF_UP));}

    private record Product(String code,String name,boolean enabled,String mainUnit,BigDecimal ratio) {}
    private record StockRow(Stock stock,BigDecimal total,BigDecimal unreserved,long invalidUnits,long count) {}
}

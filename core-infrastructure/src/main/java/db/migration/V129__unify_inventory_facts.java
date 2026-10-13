package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * 将可证明独立的旧余额迁为收货事实。旧表完整保留，不从缺失余额猜测库存。
 * 必须在应用停写时运行；预检失败只报告具体记录，不执行 DDL/DML。
 * MySQL DDL 隐式提交，所以预检先于 DDL，所有数据转换另置同一事务。
 */
public class V129__unify_inventory_facts extends BaseJavaMigration {
    private record Units(long productId, String main, String aux, BigDecimal factor,
                         Long mainId, Long baseId, boolean initialize) {}
    private record Legacy(long id, long store, long product, String batchNo, Date production,
                          Date expiry, BigDecimal quantity, BigDecimal cost, BigDecimal baseQuantity,
                          BigDecimal baseCost, Timestamp received) {}

    @Override public boolean canExecuteInTransaction() { return false; }

    @Override public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        Map<Long, Units> units = inspectUnits(c);
        reject(c,"SELECT id FROM inventory_batch WHERE quantity < 0 OR (quantity > 0 AND (is_active IS NULL OR is_active <> 1))",
                "inventory_batch 有负数或非有效正余额，须核实实际库存");
        reject(c,"SELECT a.id FROM order_item_batch_allocation a WHERE a.batch_id IS NOT NULL",
                "order_item_batch_allocation 历史分配无法证明已真实扣库；请按单据核对余额并完成有证据的数据修复");
        reject(c,"SELECT b.id FROM inventory_receipt_batch b WHERE NOT EXISTS (SELECT 1 FROM inventory_batch_balance q WHERE q.batch_id=b.id)",
                "inventory_receipt_batch 缺显式余额，不能推断未入库或售罄；请核对后补真实余额（售罄也需显式零余额）");
        reject(c,"SELECT b.id FROM inventory_batch b WHERE b.is_active=1 AND b.quantity>0 AND EXISTS (SELECT 1 FROM inventory_receipt_batch r WHERE r.store_id=b.store_id AND r.product_id=b.product_id)",
                "inventory_batch 与新收货事实重叠，须逐批确认归属，禁止自动双算");
        reject(c,"SELECT i.id FROM inventory i WHERE i.quantity<>0 AND NOT EXISTS (SELECT 1 FROM inventory_batch b WHERE b.store_id=i.store_id AND b.product_id=i.product_id AND b.is_active=1 AND b.quantity>0) AND NOT EXISTS (SELECT 1 FROM inventory_batch_balance b WHERE b.store_id=i.store_id AND b.product_id=i.product_id)",
                "inventory 快照非零但缺少库存事实，须先核实来源");
        reject(c,"SELECT l.id FROM inventory_location l WHERE l.location_type='STORE_BACKROOM' AND l.code IS NULL AND l.status<>1 AND EXISTS (SELECT 1 FROM inventory_batch b WHERE b.store_id=l.store_id AND b.quantity>0 AND b.is_active=1)",
                "inventory_location 默认后仓停用，须先确认迁入货位");
        List<Legacy> batches = inspectLegacy(c, units);
        for (Legacy b : batches) {
            reject(c,"SELECT id FROM inventory_receipt WHERE receipt_no='LEGACY-V129-" + b.id + "'", "迁移收货编号冲突");
            reject(c,"SELECT id FROM inventory_receipt_batch WHERE internal_receipt_batch_no='LEGACY-V129-" + b.id + "'", "迁移批次编号冲突");
            reject(c,"SELECT id FROM inventory_movement WHERE movement_no='LEGACY-V129-"+b.id+"' OR idempotency_key='LEGACY-V129-"+b.id+"'","迁移流水编号或幂等键冲突");
        }
        inspectSchemaData(c);
        inspectSnapshots(c, units, batches);
        alterSchema(c);
        boolean originalAutoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            for (Units u : new ArrayList<>(units.values())) {
                if (!u.initialize) continue;
                long base = insert(c,"INSERT INTO product_unit(product_id,unit_name,base_quantity_per_unit,is_base_unit) VALUES(?,?,1,1)",u.productId,u.aux == null ? u.main : u.aux);
                long main = u.aux == null ? base : insert(c,"INSERT INTO product_unit(product_id,unit_name,base_quantity_per_unit,is_base_unit) VALUES(?,?,?,0)",u.productId,u.main,u.factor);
                units.put(u.productId,new Units(u.productId,u.main,u.aux,u.factor,main,base,false));
            }
            // 门店建立时就有默认后仓，避免不同商品首次入库抢建共享货位。
            for (Object[] store : rows(c, "SELECT id FROM store ORDER BY id")) {
                long storeId = ((Number) store[0]).longValue();
                if (rows(c, "SELECT id FROM inventory_location WHERE store_id=? AND location_type='STORE_BACKROOM' AND code IS NULL", storeId).isEmpty()) {
                    insert(c, "INSERT INTO inventory_location(store_id,location_type,name,status) VALUES(?,'STORE_BACKROOM','门店后仓',1)", storeId);
                }
            }
            for (Legacy b : batches) migrateBatch(c, b, units.get(b.product));
            projectSnapshots(c, units);
            c.commit();
        } catch (Exception failure) {
            c.rollback();
            throw failure;
        } finally { c.setAutoCommit(originalAutoCommit); }
    }

    private Map<Long, Units> inspectUnits(Connection c) throws SQLException {
        Map<Long,Units> result = new LinkedHashMap<>();
        try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT id,main_unit,aux_unit,main_to_aux_ratio FROM product ORDER BY id")) {
            while(rs.next()) {
                long id=rs.getLong(1);
                String main=rs.getString(2), aux=rs.getString(3);
                if(main==null || main.isBlank() || !main.equals(main.trim())) throw invalid("product",id,"主单位名称为空或含首尾空白");
                if(aux!=null && aux.isBlank()) aux=null;
                List<Object[]> existing = rows(c,"SELECT id,unit_name,base_quantity_per_unit,is_base_unit,status FROM product_unit WHERE product_id=?",id);
                BigDecimal factor; Long mainId=null,baseId=null;
                if(existing.isEmpty()) {
                    if(aux!=null && (aux.equals(main) || !aux.equals(aux.trim()))) throw invalid("product",id,"主辅单位重复或辅单位含首尾空白");
                    factor=aux==null ? BigDecimal.ONE : rs.getBigDecimal(4);
                    if(factor==null || factor.signum()<=0) throw invalid("product",id,"主辅换算比例必须大于零");
                } else {
                    factor=null; int baseCount=0;
                    for(Object[] row:existing) {
                        if(row[4]==null || ((Number)row[4]).intValue()!=1) continue;
                        if(main.equals(row[1])) { mainId=((Number)row[0]).longValue(); factor=(BigDecimal)row[2]; }
                        if(((Number)row[3]).intValue()==1) {
                            baseCount++; baseId=((Number)row[0]).longValue();
                            if(BigDecimal.ONE.compareTo((BigDecimal)row[2])!=0) throw invalid("product",id,"基础单位系数必须为1");
                        }
                    }
                    if(mainId==null || baseId==null || baseCount!=1 || factor==null || factor.signum()<=0) throw invalid("product",id,"已有单位缺少启用主单位或基础单位");
                }
                if(aux!=null && !existing.isEmpty()) {
                    BigDecimal ratio=rs.getBigDecimal(4);
                    BigDecimal auxFactor=null;
                    for(Object[] row:existing) if(aux.equals(row[1]) && row[4]!=null && ((Number)row[4]).intValue()==1) auxFactor=(BigDecimal)row[2];
                    if(auxFactor==null || ratio==null || ratio.signum()<=0 || factor.compareTo(auxFactor.multiply(ratio))!=0)
                        throw invalid("product",id,"已有主辅单位系数与商品换算比例不匹配");
                }
                try { exact(factor); } catch(ArithmeticException e) { throw invalid("product",id,"单位系数超出DECIMAL(20,8)"); }
                result.put(id,new Units(id,main,aux,factor,mainId,baseId,existing.isEmpty()));
            }
        }
        return result;
    }

    private List<Legacy> inspectLegacy(Connection c, Map<Long,Units> units) throws SQLException {
        List<Legacy> result=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,store_id,product_id,batch_no,production_date,expire_date,quantity,cost_price,created_at FROM inventory_batch WHERE is_active=1 AND quantity>0 ORDER BY id")) {
            while(r.next()) {
                long id=r.getLong(1); Units u=units.get(r.getLong(3));
                if(u==null) throw invalid("inventory_batch",id,"商品不存在");
                Date production=r.getDate(5),expiry=r.getDate(6);
                if(production!=null && expiry!=null && production.after(expiry)) throw invalid("inventory_batch",id,"生产日期晚于有效期，不能迁入矛盾日期");
                BigDecimal qty=r.getBigDecimal(7),cost=r.getBigDecimal(8);
                if(cost==null || cost.signum()<=0) throw invalid("inventory_batch",id,"成本必须大于零");
                BigDecimal baseQty,baseCost;
                try {
                    baseQty=exact(qty.multiply(u.factor));
                    baseCost=exact(cost.divide(u.factor,8,RoundingMode.HALF_UP));
                    exact(qty); exact(cost);
                } catch(ArithmeticException error) { throw invalid("inventory_batch",id,"基础数量或成本超出DECIMAL(20,8)（基准成本按HALF_UP保留8位）；请核实单位/成本后重试"); }
                result.add(new Legacy(id,r.getLong(2),r.getLong(3),r.getString(4),production,expiry,qty,cost,baseQty,baseCost,r.getTimestamp(9)));
            }
        }
        return result;
    }

    private static BigDecimal exact(BigDecimal n) {
        BigDecimal v=n.setScale(8,RoundingMode.UNNECESSARY);
        if(v.precision()>20 || v.signum()<=0) throw new ArithmeticException("超出库存精度");
        return v;
    }

    private void alterSchema(Connection c) throws SQLException {
        addColumn(c,"inventory_receipt","request_key","VARCHAR(128) NULL");
        addConstraint(c,"inventory_receipt","uk_inventory_receipt_request_key","UNIQUE(request_key)");
        addColumn(c,"inventory_transfer","request_key","VARCHAR(128) NULL");
        addConstraint(c,"inventory_transfer","uk_inventory_transfer_request_key","UNIQUE(request_key)");
        if(hasConstraint(c,"inventory_movement","chk_inventory_movement_type"))
            execute(c,"ALTER TABLE inventory_movement DROP " + (isMySql(c) ? "CHECK " : "CONSTRAINT ") + "chk_inventory_movement_type");
        addConstraint(c,"inventory_movement","chk_inventory_movement_type","CHECK (movement_type IN ('RECEIPT','SALE','REPLENISHMENT','PICK','TRANSFER','COUNT_ADJUSTMENT','RETURN'))");
        if(hasConstraint(c,"inventory_receipt_batch","chk_inventory_receipt_batch_date_status"))
            execute(c,"ALTER TABLE inventory_receipt_batch DROP " + (isMySql(c) ? "CHECK " : "CONSTRAINT ") + "chk_inventory_receipt_batch_date_status");
        addConstraint(c,"inventory_receipt_batch","chk_inventory_receipt_batch_date_status", "CHECK ((date_status='UNKNOWN' AND production_date IS NULL AND expiry_date IS NULL) OR (date_status='PARTIAL' AND ((production_date IS NULL AND expiry_date IS NOT NULL) OR (production_date IS NOT NULL AND expiry_date IS NULL))) OR (date_status='KNOWN' AND production_date IS NOT NULL AND expiry_date IS NOT NULL AND production_date<=expiry_date))");
        addColumn(c,"inventory_receipt_batch","legacy_batch_id","BIGINT NULL");
        addConstraint(c,"inventory_receipt_batch","uk_receipt_legacy_batch","UNIQUE(legacy_batch_id)");
        addConstraint(c,"inventory_receipt_batch","fk_receipt_legacy_batch","FOREIGN KEY(legacy_batch_id) REFERENCES inventory_batch(id)");
        execute(c,"ALTER TABLE inventory_transfer_detail MODIFY COLUMN from_batch_id BIGINT NULL");
        execute(c,"ALTER TABLE inventory_transfer_detail MODIFY COLUMN to_batch_id BIGINT NULL");
        addColumn(c,"inventory_transfer_detail","from_receipt_batch_id","BIGINT NULL");
        addColumn(c,"inventory_transfer_detail","to_receipt_batch_id","BIGINT NULL");
        addConstraint(c,"inventory_transfer_detail","fk_transfer_from_receipt","FOREIGN KEY(from_receipt_batch_id) REFERENCES inventory_receipt_batch(id)");
        addConstraint(c,"inventory_transfer_detail","fk_transfer_to_receipt","FOREIGN KEY(to_receipt_batch_id) REFERENCES inventory_receipt_batch(id)");
        execute(c,"ALTER TABLE inventory_transaction MODIFY COLUMN batch_id BIGINT NULL");
        addColumn(c,"inventory_transaction","receipt_batch_id","BIGINT NULL");
        addConstraint(c,"inventory_transaction","fk_transaction_receipt","FOREIGN KEY(receipt_batch_id) REFERENCES inventory_receipt_batch(id)");
        execute(c,"ALTER TABLE inventory MODIFY COLUMN quantity DECIMAL(20,8) NOT NULL DEFAULT 0");
        for(String table:List.of("inventory_transaction","inventory_transfer_detail","inventory_transfer"))
            execute(c,"ALTER TABLE "+table+" MODIFY COLUMN quantity DECIMAL(20,8) NOT NULL");
        for(String table:List.of("inventory_transaction","inventory_transfer_detail"))
            execute(c,"ALTER TABLE "+table+" MODIFY COLUMN cost_price DECIMAL(20,8) NULL");
        for(String column:List.of("before_quantity","after_quantity","batch_before_qty","batch_after_qty","original_quantity"))
            execute(c,"ALTER TABLE inventory_transaction MODIFY COLUMN "+column+" DECIMAL(20,8) NULL");
        for(String column:List.of("quantity_before","quantity_after","quantity_diff"))
            execute(c,"ALTER TABLE inventory_adjustment MODIFY COLUMN "+column+" DECIMAL(20,8) NOT NULL");
    }

    private static boolean isMySql(Connection c) throws SQLException {
        return c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
    }

    private static boolean hasColumn(Connection c,String table,String column) throws SQLException {
        try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,table,column)) { return r.next(); }
    }

    private static boolean hasConstraint(Connection c,String table,String name) throws SQLException {
        String namespace=isMySql(c) ? "CONSTRAINT_SCHEMA=DATABASE()" : "CONSTRAINT_SCHEMA=SCHEMA()";
        return !rows(c,"SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE "+namespace+" AND TABLE_NAME=? AND CONSTRAINT_NAME=?",table,name).isEmpty();
    }

    private static void addColumn(Connection c,String table,String name,String definition) throws SQLException {
        if(!hasColumn(c,table,name)) execute(c,"ALTER TABLE "+table+" ADD COLUMN "+name+" "+definition);
    }

    private static void addConstraint(Connection c,String table,String name,String definition) throws SQLException {
        if(!hasConstraint(c,table,name)) execute(c,"ALTER TABLE "+table+" ADD CONSTRAINT "+name+" "+definition);
    }

    /** 先检查缩窄精度/NOT NULL及重试时已有列的外键数据，避免可预知的DDL半完成。 */
    private void inspectSchemaData(Connection c) throws SQLException {
        for(String table:List.of("inventory_receipt","inventory_transfer")) validateExistingColumn(c,table,"request_key",Types.VARCHAR,128);
        validateExistingColumn(c,"inventory_receipt_batch","legacy_batch_id",Types.BIGINT,0);
        validateExistingColumn(c,"inventory_transaction","receipt_batch_id",Types.BIGINT,0);
        for(String column:List.of("from_receipt_batch_id","to_receipt_batch_id")) validateExistingColumn(c,"inventory_transfer_detail",column,Types.BIGINT,0);
        Map<String,List<String>> required=Map.of(
                "inventory",List.of("quantity"),
                "inventory_transaction",List.of("quantity"),
                "inventory_transfer_detail",List.of("quantity"),
                "inventory_transfer",List.of("quantity"),
                "inventory_adjustment",List.of("quantity_before","quantity_after","quantity_diff"));
        for(var entry:required.entrySet()) for(String column:entry.getValue())
            reject(c,"SELECT id FROM "+entry.getKey()+" WHERE "+column+" IS NULL OR ABS("+column+")>=1000000000000","DDL列存在空值或DECIMAL(20,8)溢出："+entry.getKey()+"."+column);
        Map<String,List<String>> nullable=Map.of(
                "inventory_transaction",List.of("cost_price","before_quantity","after_quantity","batch_before_qty","batch_after_qty","original_quantity"),
                "inventory_transfer_detail",List.of("cost_price"));
        for(var entry:nullable.entrySet()) for(String column:entry.getValue())
            reject(c,"SELECT id FROM "+entry.getKey()+" WHERE ABS("+column+")>=1000000000000","DDL列DECIMAL(20,8)溢出："+entry.getKey()+"."+column);
        reject(c,"SELECT id FROM inventory_movement WHERE movement_type NOT IN ('RECEIPT','SALE','REPLENISHMENT','PICK','TRANSFER','COUNT_ADJUSTMENT','RETURN')","流水类型无法满足新约束");
        reject(c,"SELECT b.id FROM inventory_batch b WHERE b.is_active=1 AND b.quantity>0 AND (b.created_at IS NULL OR NOT EXISTS(SELECT 1 FROM store s WHERE s.id=b.store_id))","旧批次缺门店或收货时间");
        for(String column:List.of("from_receipt_batch_id","to_receipt_batch_id")) if(hasColumn(c,"inventory_transfer_detail",column))
            reject(c,"SELECT t.id FROM inventory_transfer_detail t WHERE t."+column+" IS NOT NULL AND NOT EXISTS(SELECT 1 FROM inventory_receipt_batch b WHERE b.id=t."+column+")","移库收货批次引用不存在");
        if(hasColumn(c,"inventory_transaction","receipt_batch_id"))
            reject(c,"SELECT t.id FROM inventory_transaction t WHERE t.receipt_batch_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM inventory_receipt_batch b WHERE b.id=t.receipt_batch_id)","库存流水收货批次引用不存在");
        for(String table:List.of("inventory_receipt","inventory_transfer")) if(hasColumn(c,table,"request_key"))
            reject(c,"SELECT MIN(id) FROM "+table+" WHERE request_key IS NOT NULL GROUP BY request_key HAVING COUNT(*)>1","请求幂等键重复："+table);
        if(hasColumn(c,"inventory_receipt_batch","legacy_batch_id")) {
            reject(c,"SELECT b.id FROM inventory_receipt_batch b WHERE b.legacy_batch_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM inventory_batch l WHERE l.id=b.legacy_batch_id)","旧批次引用不存在");
            reject(c,"SELECT MIN(id) FROM inventory_receipt_batch WHERE legacy_batch_id IS NOT NULL GROUP BY legacy_batch_id HAVING COUNT(*)>1","旧批次引用重复");
        }
    }

    private static void validateExistingColumn(Connection c,String table,String column,int type,int length) throws SQLException {
        try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,table,column)) {
            if(r.next() && (r.getInt("DATA_TYPE")!=type || r.getInt("NULLABLE")!=DatabaseMetaData.columnNullable
                    || length>0 && r.getInt("COLUMN_SIZE")!=length))
                throw new SQLException("V129 预检失败：已有列定义不兼容 "+table+"."+column);
        }
    }

    private void inspectSnapshots(Connection c,Map<Long,Units> units,List<Legacy> batches) throws SQLException {
        Map<String,BigDecimal> totals=new HashMap<>();
        Map<String,Long> products=new HashMap<>();
        for(Object[] row:rows(c,"SELECT store_id,product_id,SUM(base_quantity) FROM inventory_batch_balance GROUP BY store_id,product_id")) {
            long product=((Number)row[1]).longValue(); String key=row[0]+":"+product;
            products.put(key,product); totals.put(key,(BigDecimal)row[2]);
        }
        for(Legacy b:batches) { String key=b.store+":"+b.product; products.put(key,b.product); totals.merge(key,b.baseQuantity,BigDecimal::add); }
        for(var entry:totals.entrySet()) {
            long product=products.get(entry.getKey()); Units u=units.get(product);
            if(u==null) throw invalid("product",product,"库存余额商品不存在");
            BigDecimal quantity=entry.getValue().divide(u.factor,8,RoundingMode.HALF_UP);
            if(quantity.precision()>20) throw invalid("product",product,"主单位汇总快照超出DECIMAL(20,8)");
        }
    }

    private void migrateBatch(Connection c, Legacy b, Units u) throws SQLException {
        List<Object[]> locations=rows(c,"SELECT id FROM inventory_location WHERE store_id=? AND location_type='STORE_BACKROOM' AND code IS NULL",b.store);
        long location=locations.isEmpty() ? insert(c,"INSERT INTO inventory_location(store_id,location_type,code,name) VALUES(?,'STORE_BACKROOM',NULL,'门店后仓')",b.store) : ((Number)locations.get(0)[0]).longValue();
        String number="LEGACY-V129-"+b.id;
        long receipt=insert(c,"INSERT INTO inventory_receipt(receipt_no,store_id,status,received_at,remark) VALUES(?,?,'RECEIVED',?,?)",number,b.store,b.received,"旧批次当前剩余余额迁入，原始历史保留于inventory_batch，旧批次ID="+b.id);
        long line=insert(c,"INSERT INTO inventory_receipt_line(receipt_id,line_no,product_id,store_id) VALUES(?,1,?,?)",receipt,b.product,b.store);
        long batch=insert(c,"INSERT INTO inventory_receipt_batch(receipt_line_id,product_id,store_id,supplier_batch_no,internal_receipt_batch_no,original_unit_id,original_unit_name,original_quantity,original_unit_cost,base_unit_id,base_quantity,base_unit_cost,date_status,production_date,expiry_date,received_at,legacy_batch_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                line,b.product,b.store,b.batchNo,number,u.mainId,u.main,b.quantity,b.cost,u.baseId,b.baseQuantity,b.baseCost,b.production==null && b.expiry==null ? "UNKNOWN" : b.production==null || b.expiry==null ? "PARTIAL" : "KNOWN",b.production,b.expiry,b.received,b.id);
        insert(c,"INSERT INTO inventory_batch_balance(batch_id,product_id,store_id,location_id,base_quantity) VALUES(?,?,?,?,?)",batch,b.product,b.store,location,b.baseQuantity);
        insert(c,"INSERT INTO inventory_movement(movement_no,idempotency_key,batch_id,product_id,store_id,to_location_id,movement_type,original_unit_id,original_unit_name,original_quantity,unit_cost,base_quantity,reference_type,reference_id,remark) VALUES(?,?,?,?,?,?,'RECEIPT',?,?,?,?,?,'LEGACY_MIGRATION',?,?)",
                number,number,batch,b.product,b.store,location,u.mainId,u.main,b.quantity,b.baseCost,b.baseQuantity,b.id,"V129旧批次剩余余额迁入，不表示新增采购");
    }

    private void projectSnapshots(Connection c,Map<Long,Units> units) throws SQLException {
        execute(c,"UPDATE inventory SET quantity=0");
        for(Object[] row:rows(c,"SELECT store_id,product_id,SUM(base_quantity) FROM inventory_batch_balance GROUP BY store_id,product_id")) {
            long store=((Number)row[0]).longValue(), product=((Number)row[1]).longValue();
            BigDecimal quantity=((BigDecimal)row[2]).divide(units.get(product).factor,8,RoundingMode.HALF_UP);
            try(PreparedStatement s=c.prepareStatement("UPDATE inventory SET quantity=? WHERE store_id=? AND product_id=?")) {
                s.setBigDecimal(1,quantity);s.setLong(2,store);s.setLong(3,product);
                if(s.executeUpdate()==0) insert(c,"INSERT INTO inventory(store_id,product_id,quantity) VALUES(?,?,?)",store,product,quantity);
            }
        }
    }

    private static SQLException invalid(String table,long id,String reason) { return new SQLException("V129 预检失败："+table+" id="+id+"；"+reason); }
    private static void reject(Connection c,String sql,String reason) throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql+" LIMIT 1")) { if(r.next()) throw new SQLException("V129 预检失败："+reason+"；id="+r.getLong(1)); }
    }
    private static void execute(Connection c,String sql) throws SQLException { try(Statement s=c.createStatement()) { s.execute(sql); } }
    private static List<Object[]> rows(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            bind(s,args);
            try(ResultSet r=s.executeQuery()) {
                List<Object[]> rows=new ArrayList<>();
                while(r.next()) { Object[] row=new Object[r.getMetaData().getColumnCount()];for(int i=0;i<row.length;i++)row[i]=r.getObject(i+1);rows.add(row); }
                return rows;
            }
        }
    }
    private static long insert(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            bind(s,args);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()) { if(!r.next())throw new SQLException("缺少新记录ID");return r.getLong(1); }
        }
    }
    private static void bind(PreparedStatement s,Object[] args) throws SQLException { for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]); }
}

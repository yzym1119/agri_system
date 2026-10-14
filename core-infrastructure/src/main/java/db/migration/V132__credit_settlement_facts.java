package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** 原单还款来源与退货资金拆分；确定事实才回填，历史未知不猜配、不放开可退资金。 */
public class V132__credit_settlement_facts extends BaseJavaMigration {
    private record Account(long id,long orderId,long storeId,BigDecimal credit) {}
    private record OrderAmount(long id,BigDecimal net) {}
    @Override public void migrate(Context context) throws Exception {
        Connection connection=context.getConnection();
        Map<Long,Account> accounts=new HashMap<>();
        List<OrderAmount> amounts=new ArrayList<>();
        // MySQL DDL会隐式提交，必须在任何结构/金额写入前核对所有原应收事实。
        rejectIllegalSourceAmounts(connection,"order_payment","收款记录");
        rejectIllegalSourceAmounts(connection,"credit_repayment_log","还款记录");
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                SELECT ca.id,ca.order_id,ca.customer_id,ca.credit_amount,ca.paid_amount,ca.remaining_amount,
                       o.store_id,o.customer_id,o.payment_method,o.pay_amount,o.total_amount,o.discount_amount,o.coupon_amount,s.id
                FROM credit_account ca LEFT JOIN `order` o ON o.id=ca.order_id LEFT JOIN store s ON s.id=o.store_id ORDER BY ca.id
                """)) {
            while(rows.next()) {
                long id=rows.getLong(1),orderId=rows.getLong(2);
                Long storeId=nullableLong(rows,7),orderCustomer=nullableLong(rows,8);
                if(storeId==null || orderCustomer==null || nullableLong(rows,14)==null) throw invalid(id,"缺少原销售单或确定门店");
                if(rows.getLong(3)!=orderCustomer) throw invalid(id,"客户与原销售单不一致");
                BigDecimal credit=money(rows.getBigDecimal(4),id),paid=money(rows.getBigDecimal(5),id),remaining=money(rows.getBigDecimal(6),id);
                if(credit.signum()<=0 || paid.add(remaining).compareTo(credit)!=0) throw invalid(id,"原赊销、累计实还与余额不守恒");
                BigDecimal net=net(rows.getBigDecimal(11),rows.getBigDecimal(12),rows.getBigDecimal(13),orderId);
                if(credit.compareTo(net)>0) throw invalid(id,"原赊销超过原净成交");
                if(creditSum(connection,orderId).compareTo(credit)!=0) throw invalid(id,"赊销支付事实与原账户金额不一致");
                if(nonCouponSum(connection,orderId).compareTo(net)!=0) throw invalid(id,"原收款与赊销合计不等于原净应结算额");
                if(accounts.putIfAbsent(orderId,new Account(id,orderId,storeId,credit))!=null) throw invalid(id,"同一销售单存在重复应收账户");
            }
        }
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                SELECT id,pay_amount,total_amount,discount_amount,coupon_amount FROM `order` WHERE payment_method=6 ORDER BY id
                """)) {
            while(rows.next()) {
                long orderId=rows.getLong(1);BigDecimal original=rows.getBigDecimal(2);
                BigDecimal net=net(rows.getBigDecimal(3),rows.getBigDecimal(4),rows.getBigDecimal(5),orderId);
                if(original==null || original.signum()<0) throw new IllegalStateException("账款迁移失败，销售单 "+orderId+" 原应结算金额无效");
                if(original.signum()==0 && net.signum()>0) {
                    Account account=accounts.get(orderId);
                    if(account==null || account.credit().compareTo(net)!=0 || creditSum(connection,orderId).compareTo(net)!=0) {
                        throw new IllegalStateException("账款迁移失败，销售单 "+orderId+" 旧零金额无法由原应收及赊销支付事实确认");
                    }
                    amounts.add(new OrderAmount(orderId,net));
                } else if(original.compareTo(net)!=0) {
                    throw new IllegalStateException("账款迁移失败，销售单 "+orderId+" 原净应结算金额不一致");
                }
            }
        }
        try(Statement statement=connection.createStatement();ResultSet invalid=statement.executeQuery("SELECT id FROM after_sales WHERE refund_amount IS NULL OR refund_amount<0")) {
            if(invalid.next()) throw new IllegalStateException("账款迁移失败，售后 "+invalid.getLong(1)+" 历史成交冲销金额无效");
        }
        execute(connection,"ALTER TABLE credit_account ADD COLUMN store_id BIGINT NULL");
        execute(connection,"ALTER TABLE credit_repayment_log ADD COLUMN credit_account_id BIGINT NULL");
        execute(connection,"ALTER TABLE credit_repayment_log ADD COLUMN request_key VARCHAR(64) NULL");
        execute(connection,"ALTER TABLE credit_repayment_log ADD COLUMN refunded_amount DECIMAL(10,2) NULL");
        execute(connection,"ALTER TABLE order_payment ADD COLUMN refunded_amount DECIMAL(10,2) NULL");
        execute(connection,"ALTER TABLE after_sales ADD COLUMN credit_reduction_amount DECIMAL(10,2) NULL");
        execute(connection,"ALTER TABLE after_sales ADD COLUMN cash_refund_amount DECIMAL(10,2) NULL");
        execute(connection,"ALTER TABLE after_sales ADD COLUMN refund_quote_id VARCHAR(64) NULL");
        execute(connection,"ALTER TABLE after_sales ADD COLUMN refund_request_hash VARCHAR(64) NULL");
        try(PreparedStatement update=connection.prepareStatement("UPDATE credit_account SET store_id=? WHERE id=?")) {
            for(Account account:accounts.values()) {update.setLong(1,account.storeId());update.setLong(2,account.id());update.executeUpdate();}
        }
        try(PreparedStatement update=connection.prepareStatement("UPDATE `order` SET pay_amount=? WHERE id=?")) {
            for(OrderAmount amount:amounts) {update.setBigDecimal(1,amount.net());update.setLong(2,amount.id());update.executeUpdate();}
        }
        // 只有未发生过有金额历史售后的原支付可确定从未退款；旧客户级还款一律保持NULL来源。
        execute(connection,"UPDATE order_payment SET refunded_amount=0 WHERE order_id NOT IN (SELECT order_id FROM after_sales WHERE status=50 AND refund_amount>0)");
        execute(connection,"UPDATE after_sales SET credit_reduction_amount=0,cash_refund_amount=0 WHERE status=50 AND refund_amount=0");
        execute(connection,"ALTER TABLE credit_account MODIFY COLUMN store_id BIGINT NOT NULL");
        execute(connection,"ALTER TABLE credit_account ADD CONSTRAINT uk_credit_account_order UNIQUE(order_id)");
        execute(connection,"ALTER TABLE credit_account ADD CONSTRAINT fk_credit_account_store FOREIGN KEY(store_id) REFERENCES store(id)");
        execute(connection,"ALTER TABLE credit_repayment_log ADD CONSTRAINT fk_credit_repayment_account FOREIGN KEY(credit_account_id) REFERENCES credit_account(id)");
        // 每批仅首条分配写请求身份，防跨客户重复，不使用门店排他锁。
        execute(connection,"ALTER TABLE credit_repayment_log ADD CONSTRAINT uk_credit_store_request UNIQUE(store_id,request_key)");
        // 使用完整SHA-256身份，不截断前缀，不复用业务售后号或审核备注。
        execute(connection,"ALTER TABLE after_sales ADD CONSTRAINT uk_after_sales_refund_quote UNIQUE(refund_quote_id)");
        execute(connection,"CREATE INDEX idx_credit_store_customer ON credit_account(store_id,customer_id)");
        execute(connection,"CREATE INDEX idx_credit_repayment_account ON credit_repayment_log(credit_account_id,created_at)");
        // 批次当前读必须直接定位店铺和还款号，避免按店扫描锁住其他客户的来源。
        execute(connection,"CREATE INDEX idx_credit_repayment_batch ON credit_repayment_log(store_id,repayment_no)");
        execute(connection,"ALTER TABLE credit_repayment_log ADD CONSTRAINT chk_credit_repayment_refund CHECK(refunded_amount IS NULL OR (refunded_amount>=0 AND refunded_amount<=amount))");
        execute(connection,"ALTER TABLE credit_repayment_log ADD CONSTRAINT chk_credit_request_source CHECK(request_key IS NULL OR (credit_account_id IS NOT NULL AND refunded_amount IS NOT NULL))");
        execute(connection,"ALTER TABLE order_payment ADD CONSTRAINT chk_order_payment_refund CHECK(refunded_amount IS NULL OR (refunded_amount>=0 AND refunded_amount<=amount))");
        execute(connection,"""
                ALTER TABLE after_sales ADD CONSTRAINT chk_after_sales_financial_split CHECK(
                    (credit_reduction_amount IS NULL AND cash_refund_amount IS NULL) OR
                    (credit_reduction_amount IS NOT NULL AND cash_refund_amount IS NOT NULL AND credit_reduction_amount>=0 AND cash_refund_amount>=0
                        AND credit_reduction_amount+cash_refund_amount=refund_amount))
                """);
        execute(connection,"""
                ALTER TABLE after_sales ADD CONSTRAINT chk_after_sales_refund_identity CHECK(
                    (refund_quote_id IS NULL AND refund_request_hash IS NULL) OR
                    (refund_quote_id IS NOT NULL AND refund_request_hash IS NOT NULL
                        AND CHAR_LENGTH(refund_quote_id)=64 AND CHAR_LENGTH(refund_request_hash)=64))
                """);
    }
    private static BigDecimal creditSum(Connection connection,long orderId) throws SQLException {
        try(PreparedStatement query=connection.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM order_payment WHERE order_id=? AND payment_type=6")) {
            query.setLong(1,orderId);try(ResultSet rows=query.executeQuery()){rows.next();return rows.getBigDecimal(1);}
        }
    }
    private static BigDecimal nonCouponSum(Connection connection,long orderId) throws SQLException {
        try(PreparedStatement query=connection.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM order_payment WHERE order_id=? AND (payment_type<>5 OR payment_type IS NULL)")) {
            query.setLong(1,orderId);try(ResultSet rows=query.executeQuery()){rows.next();return rows.getBigDecimal(1);}
        }
    }
    private static void rejectIllegalSourceAmounts(Connection connection,String table,String label) throws SQLException {
        // 表名来自此迁移常量，不接受用户输入。
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT id FROM "+table+" WHERE amount IS NULL OR amount<0 ORDER BY id")) {
            if(rows.next()) throw new IllegalStateException("账款迁移失败，"+label+" "+rows.getLong(1)+" 金额为空或为负");
        }
    }
    private static BigDecimal money(BigDecimal value,long id) {
        if(value==null || value.signum()<0 || value.scale()>2) throw invalid(id,"账款金额无效");return value;
    }
    private static BigDecimal net(BigDecimal total,BigDecimal discount,BigDecimal coupon,long orderId) {
        if(total==null || total.signum()<0 || discount!=null && discount.signum()<0 || coupon!=null && coupon.signum()<0) throw new IllegalStateException("账款迁移失败，销售单 "+orderId+" 缺少有效净成交");
        BigDecimal net=total.subtract(discount==null?BigDecimal.ZERO:discount).subtract(coupon==null?BigDecimal.ZERO:coupon);
        if(net.signum()<0) throw new IllegalStateException("账款迁移失败，销售单 "+orderId+" 净成交金额为负");return net;
    }
    private static Long nullableLong(ResultSet rows,int column) throws SQLException {long value=rows.getLong(column);return rows.wasNull()?null:value;}
    private static void execute(Connection connection,String sql) throws SQLException {try(Statement statement=connection.createStatement()){statement.execute(sql);}}
    private static IllegalStateException invalid(long id,String problem){return new IllegalStateException("账款迁移失败，应收账户 "+id+"："+problem);}
}

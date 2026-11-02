package com.agri_system.infrastructure.migration;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import java.sql.Connection;
import java.sql.SQLException;

/** 计划升级至V137或更高时，开始迁移及执行V137前拦住非空旧绩效；升级仍必须停写。 */
public final class LegacyPerformanceDropGuard implements Callback {
    @Override public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE || event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null
                && MigrationVersion.fromVersion("137").equals(context.getMigrationInfo().getVersion());
    }
    @Override public boolean canHandleInTransaction(Event event, Context context) { return true; }
    @Override public void handle(Event event, Context context) {
        var target=context.getConfiguration().getTarget();
        if(target != null && target.compareTo(MigrationVersion.fromVersion("137")) < 0) return;
        var connection=context.getConnection();
        try {
            if(!tableExists(connection,"staff_performance")) return;
            var history=context.getConfiguration().getTable();
            if(tableExists(connection,history)) {
                try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT 1 FROM `"+history.replace("`","``")+"` WHERE version='137' AND success=1 LIMIT 1")) {
                    if(rows.next()) return;
                }
            }
            try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT 1 FROM staff_performance LIMIT 1")) {
                if(rows.next()) throw new FlywayException("旧绩效表 staff_performance 非空，已停止迁移；请先停写、备份并核对归档，禁止直接删表或 repair 跳过检查");
            }
        } catch(SQLException e) { throw new FlywayException("无法核对旧绩效历史，已停止迁移",e); }
    }
    private boolean tableExists(Connection connection,String table) throws SQLException {
        try(var query=connection.prepareStatement("SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
            query.setString(1,table);try(var rows=query.executeQuery()){return rows.next();}
        }
    }
    @Override public String getCallbackName() { return "legacy-performance-drop-guard"; }
}

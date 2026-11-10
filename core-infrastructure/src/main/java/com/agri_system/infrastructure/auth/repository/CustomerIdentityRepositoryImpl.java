package com.agri_system.infrastructure.auth.repository;

import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/** 同Flex连接的敏感身份读写；手动绑定避免MyBatis StdOut及参数跟踪打印凭据。 */
@Repository
@RequiredArgsConstructor
public class CustomerIdentityRepositoryImpl implements CustomerIdentityRepository {
    private final JdbcTemplate jdbc;
    /** 会话和通知无需读取已有sessionKey。 */
    private static final String FIELDS="id,customer_id,identity_type,app_id,identity_value,status,auth_version,created_at,updated_at";

    @Override public Optional<CustomerIdentity> findById(Long id) {
        if(id==null||id<=0)throw new IllegalArgumentException("身份编号无效");
        return one(query("id=?",id));
    }
    @Override public Optional<CustomerIdentity> findWechatByAppAndOpenIdForUpdate(String appId,String openId) {
        requireApp(appId);if(openId==null||openId.isBlank())throw new IllegalArgumentException("微信身份缺失");
        return one(query("identity_type='WECHAT_OPENID' AND app_id=? AND identity_value=? FOR UPDATE",appId,openId));
    }
    @Override public List<CustomerIdentity> findActiveWechatByCustomerAndApp(Long customerId,String appId) {
        requireApp(appId);if(customerId==null||customerId<=0)throw new IllegalArgumentException("客户编号无效");
        return query("customer_id=? AND identity_type='WECHAT_OPENID' AND app_id=? AND status=1",customerId,appId);
    }
    @Override public List<CustomerIdentity> findWechatByCustomerAndApp(Long customerId,String appId) {
        requireApp(appId);if(customerId==null||customerId<=0)throw new IllegalArgumentException("客户编号无效");
        return query("customer_id=? AND identity_type='WECHAT_OPENID' AND app_id=?",customerId,appId);
    }
    @Override public Optional<CustomerIdentity> findByTypeAndValue(String type,String value) {
        requireNonWechat(type);return one(query("identity_type=? AND identity_value=? AND app_id=''",type,value));
    }
    @Override public Optional<CustomerIdentity> findByCustomerIdAndType(Long customerId,String type) {
        requireNonWechat(type);return one(query("customer_id=? AND identity_type=? AND app_id=''",customerId,type));
    }
    private static void requireApp(String appId){if(appId==null||!appId.matches("wx[a-zA-Z0-9]{16}"))throw new IllegalArgumentException("微信身份必须明确有效AppID");}
    private static void requireNonWechat(String type){if("WECHAT_OPENID".equals(type))throw new IllegalArgumentException("微信身份查询必须明确AppID");}
    private static Optional<CustomerIdentity> one(List<CustomerIdentity> rows){if(rows.size()>1)throw new IllegalStateException("身份记录不唯一，请核验");return rows.stream().findFirst();}

    private List<CustomerIdentity> query(String predicate,Object... values) {
        return jdbc.execute((ConnectionCallback<List<CustomerIdentity>>) connection->{
            try(var statement=connection.prepareStatement("SELECT "+FIELDS+" FROM customer_identity WHERE "+predicate)){
                for(int n=0;n<values.length;n++)statement.setObject(n+1,values[n]);
                try(var rows=statement.executeQuery()){
                    var result=new java.util.ArrayList<CustomerIdentity>();while(rows.next())result.add(map(rows));return List.copyOf(result);
                }
            }
        });
    }
    private static CustomerIdentity map(ResultSet row) throws SQLException {
        var identity=new CustomerIdentity();identity.setId(row.getLong("id"));long customer=row.getLong("customer_id");identity.setCustomerId(row.wasNull()?null:customer);
        identity.setIdentityType(row.getString("identity_type"));identity.setAppId(row.getString("app_id"));identity.setIdentityValue(row.getString("identity_value"));
        int status=row.getInt("status");identity.setStatus(row.wasNull()?null:status);
        long version=row.getLong("auth_version");identity.setAuthVersion(row.wasNull()?null:version);
        var created=row.getTimestamp("created_at");var updated=row.getTimestamp("updated_at");identity.setCreatedAt(created==null?null:created.toLocalDateTime());identity.setUpdatedAt(updated==null?null:updated.toLocalDateTime());return identity;
    }
    @Override public CustomerIdentity save(CustomerIdentity identity) {
        if(identity==null||identity.getCustomerId()==null||identity.getCustomerId()<=0||identity.getIdentityType()==null||identity.getIdentityType().isBlank()
                ||identity.getIdentityValue()==null||identity.getIdentityValue().isBlank()||identity.getIdentityValue().length()>200
                ||identity.getStatus()==null||(identity.getStatus()!=1&&identity.getStatus()!=2)||identity.getSessionKey()!=null&&identity.getSessionKey().length()>255)
            throw new IllegalArgumentException("身份资料不完整或无效");
        String app=identity.getAppId()==null?"":identity.getAppId();
        if(identity.isWechatOpenId()&&(identity.getId()==null||!app.isEmpty()))requireApp(app);
        if(identity.getId()!=null&&identity.getId()<=0)throw new IllegalArgumentException("身份编号无效");
        boolean insert=identity.getId()==null;
        if(identity.getAuthVersion()!=null&&identity.getAuthVersion()<=0||!insert&&identity.getAuthVersion()==null
                ||insert&&identity.getAuthVersion()!=null&&identity.getAuthVersion()!=1L)
            throw new IllegalArgumentException("身份授权版本无效，请重新读取");
        // 凭据刷新保持版本；授权变更单调递增。即使调用方没有外层事务，后续CAS也拒绝陈旧快照。
        long expected=insert?1L:identity.getAuthVersion();
        CustomerIdentity previous=insert?null:one(query("id=? FOR UPDATE",identity.getId()))
                .orElseThrow(()->new IllegalStateException("身份记录已不存在"));
        if(previous!=null&&!Long.valueOf(expected).equals(previous.getAuthVersion()))throw stale();
        boolean authorizationChanged=previous!=null&&(!java.util.Objects.equals(previous.getCustomerId(),identity.getCustomerId())
                ||!java.util.Objects.equals(previous.getIdentityType(),identity.getIdentityType())
                ||!java.util.Objects.equals(previous.getAppId(),app)
                ||!java.util.Objects.equals(previous.getIdentityValue(),identity.getIdentityValue())
                ||!java.util.Objects.equals(previous.getStatus(),identity.getStatus()));
        if(authorizationChanged&&expected==Long.MAX_VALUE)throw new IllegalStateException("身份授权版本已达上限，请人工处理");
        long next=authorizationChanged?expected+1:expected;
        return jdbc.execute((ConnectionCallback<CustomerIdentity>) connection->{
            String sql=insert?"INSERT INTO customer_identity(customer_id,identity_type,app_id,identity_value,session_key,status,auth_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,COALESCE(?,CURRENT_TIMESTAMP),COALESCE(?,CURRENT_TIMESTAMP))"
                    :"UPDATE customer_identity SET customer_id=?,identity_type=?,app_id=?,identity_value=?,session_key=COALESCE(?,session_key),status=?,auth_version=?,updated_at=COALESCE(?,CURRENT_TIMESTAMP) WHERE id=? AND auth_version=?";
            try(var statement=insert?connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS):connection.prepareStatement(sql)){
                statement.setLong(1,identity.getCustomerId());statement.setString(2,identity.getIdentityType());statement.setString(3,app);statement.setString(4,identity.getIdentityValue());statement.setString(5,identity.getSessionKey());statement.setInt(6,identity.getStatus());
                statement.setLong(7,next);
                if(insert){statement.setTimestamp(8,timestamp(identity.getCreatedAt()));statement.setTimestamp(9,timestamp(identity.getUpdatedAt()));}
                else{statement.setTimestamp(8,timestamp(identity.getUpdatedAt()));statement.setLong(9,identity.getId());statement.setLong(10,expected);}
                int changed=statement.executeUpdate();
                if(insert){try(var keys=statement.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("身份保存未返回编号");identity.setId(keys.getLong(1));}}
                else if(changed==0){try(var check=connection.prepareStatement("SELECT id FROM customer_identity WHERE id=? AND auth_version=?")){check.setLong(1,identity.getId());check.setLong(2,expected);try(var row=check.executeQuery()){if(authorizationChanged||!row.next())throw stale();}}}
                identity.setAppId(app);identity.setAuthVersion(next);return identity;
            }
        });
    }
    private static IllegalStateException stale(){return new IllegalStateException("身份关联或授权版本已改变，请重新读取核验");}
    private static Timestamp timestamp(java.time.LocalDateTime time){return time==null?null:Timestamp.valueOf(time);}
}

package com.agri_system.application.store.service;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import com.agri_system.domain.system.log.model.SysOperationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
@Service
@RequiredArgsConstructor
public class StorePublicationService {
    private final StorePublicationRepository rows;
    private final StaffRepository staff;
    private final SysRoleRepository roles;
    private final SysOperationLogRepository logs;
    public record Preview(String storeId,String name,String phone,String address,boolean visible,String version,String fingerprint,boolean canPublish) {}
    public Preview preview(Long actorId,Long storeId){actor(actorId,storeId);return view(row(storeId,false));}
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Preview setVisible(Long actorId,Long storeId,boolean visible,String expectedVersion,String fingerprint){
        actor(actorId,storeId);var current=row(storeId,true);long version;
        try{version=Long.parseLong(expectedVersion);}catch(RuntimeException malformed){throw stale();}
        if(version<=0||current.version()!=version||fingerprint==null||!fingerprint.matches("[0-9a-f]{64}")||!hash(current).equals(fingerprint))throw stale();
        if(visible&&!canPublish(current))throw new IllegalArgumentException("请先启用门店并完善名称、联系电话和地址，再确认公开");
        if((current.visible()==1)==visible)return view(current);
        if(version==Long.MAX_VALUE)throw new IllegalStateException("门店公开版本已达上限，请人工处理");
        if(!rows.setVisible(storeId,version,visible?1:0))throw stale();
        var log=new SysOperationLog();log.setStaffId(actorId);log.setModule("门店公开");log.setAction(visible?"公开门店资料":"撤回门店资料");log.setStatus(1);log.setCreatedAt(LocalDateTime.now());log.setMethod("POST");log.setRequestUri("/api/admin/stores/"+storeId+"/publication");log.setRequestParams("门店="+storeId+"; 原公开版本="+version+"; 资料指纹="+fingerprint);logs.save(log);
        return view(new StorePublicationRepository.State(current.id(),current.name(),current.phone(),current.address(),current.status(),current.deleted(),visible?1:0,version+1));
    }
    private void actor(Long id,Long storeId){
        if(id==null||id<=0||storeId==null||storeId<=0)throw new IllegalArgumentException("需要有效员工及门店");
        var actor=staff.findIdentityById(id).orElseThrow(()->new IllegalArgumentException("员工登录已失效"));
        if(!id.equals(actor.getId())||!actor.isActive()||!Integer.valueOf(0).equals(actor.getDeleted()))throw new IllegalArgumentException("员工登录已失效");
        var codes=roles.findRoleCodesByStaffId(id);boolean admin=codes.contains("ADMIN")||codes.contains("SUPER_ADMIN");
        if(!admin&&!Objects.equals(actor.getStoreId(),storeId))throw new IllegalArgumentException("只能设置本人门店的公开资料");
    }
    private StorePublicationRepository.State row(Long id,boolean lock){
        var r=rows.find(id,lock).orElseThrow(()->new IllegalArgumentException("门店不存在或已删除"));
        if(!id.equals(r.id())||!Integer.valueOf(0).equals(r.deleted())||r.version()==null||r.version()<=0||r.visible()==null||(r.visible()!=0&&r.visible()!=1))throw new IllegalArgumentException("门店公开资料待核对");return r;
    }
    private static boolean canPublish(StorePublicationRepository.State r){return Integer.valueOf(1).equals(r.status())&&text(r.name())&&text(r.address())&&r.phone()!=null&&r.phone().matches("[+0-9() -]{7,30}")&&r.phone().replaceAll("[() -]","").matches("\\+?[0-9]{7,20}");}
    private static boolean text(String s){return s!=null&&!s.isBlank();}
    private static Preview view(StorePublicationRepository.State r){return new Preview(r.id().toString(),r.name(),r.phone(),r.address(),r.visible()==1,r.version().toString(),hash(r),canPublish(r));}
    private static String hash(StorePublicationRepository.State r){
        var input=new StringBuilder();for(Object value:new Object[]{r.id(),r.name(),r.phone(),r.address(),r.status(),r.deleted(),r.visible(),r.version()}){if(value==null){input.append('N');continue;}String part=value.toString();input.append('S').append(part.length()).append(':').append(part);}
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static IllegalArgumentException stale(){return new IllegalArgumentException("门店资料或公开状态已变化，请重新读取并确认");}
}

package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.infrastructure.order.repository.PosSaleSubmissionRepository;
import com.agri_system.infrastructure.order.repository.PosSaleSubmissionRepository.Submission;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PosSaleSubmissionService {
    private final PosSaleSubmissionRepository requests;
    private final OrderApplicationService orders;
    private final StaffRepository staffRepository;
    private final com.agri_system.domain.order.repository.OrderRepository orderRepository;
    public record Result(String requestKey,String status,Long orderId) {}
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Long submit(String key,POSOrderCreateCommand command,Long actor) {
        Staff staff=actor(actor);validateKey(key);
        if(!Objects.equals(staff.getStoreId(),command.getStoreId()) || !Objects.equals(actor,command.getStaffId()))
            throw new IllegalArgumentException("只能提交当前员工本门店的收银请求");
        String hash=hash(command);
        requests.reserve(key,staff.getStoreId(),actor);
        Submission request=owned(key,staff,true);
        if("CANCELLED".equals(request.status())) throw new IllegalArgumentException("本次收银请求已结束，不能再次成交");
        if("COMPLETED".equals(request.status())) {
            if(!hash.equals(request.requestHash())) throw new IllegalArgumentException("同一收银请求不能修改商品、金额或支付内容");
            return request.orderId();
        }
        // 同一请求行锁先于原开单的钱货事务；成功结果与订单、支付、库存一并提交/回滚。
        Long order=orders.createPOSOrder(command,actor);
        var fact=orderRepository.findById(order).orElseThrow(()->new IllegalStateException("成交订单读取失败"));
        if(!Objects.equals(fact.getStoreId(),staff.getStoreId()) || !Objects.equals(fact.getStaffId(),actor))
            throw new IllegalArgumentException("开单期间员工门店已变化，请重新登录核对");
        requests.complete(key,hash,order);
        return order;
    }
    public Result lookup(String key,Long actor) {
        Staff staff=actor(actor);validateKey(key);
        return requests.find(key,false).map(r->{checkOwner(r,staff);return result(r);})
            .orElseGet(()->new Result(key,"UNKNOWN",null));
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Result cancel(String key,Long actor) {
        Staff staff=actor(actor);validateKey(key);
        // 不存在也插入取消屏障：原提交仍在途时，谁先取得唯一请求行锁谁决定结果。
        requests.reserve(key,staff.getStoreId(),actor);
        Submission request=owned(key,staff,true);
        if(!"PENDING".equals(request.status())) return result(request);
        requests.cancel(key);
        return new Result(key,"CANCELLED",null);
    }
    private Submission owned(String key,Staff staff,boolean lock) {
        Submission r=requests.find(key,lock).orElseThrow(()->new IllegalStateException("收银请求读取失败"));checkOwner(r,staff);return r;
    }
    private void checkOwner(Submission r,Staff staff) {
        if(!Objects.equals(r.staffId(),staff.getId()) || !Objects.equals(r.storeId(),staff.getStoreId()))
            throw new IllegalArgumentException("不能读取或处理其他员工、门店的收银请求");
    }
    private Staff actor(Long actor) {
        if(actor==null) throw new IllegalArgumentException("请重新登录后核对收银请求");
        Staff s=staffRepository.findIdentityById(actor).filter(Staff::isActive)
            .orElseThrow(()->new IllegalArgumentException("当前员工不可用"));
        if(s.getStoreId()==null)throw new IllegalArgumentException("当前员工未分配门店");return s;
    }
    private void validateKey(String key) {
        try {if(key==null || !UUID.fromString(key).toString().equals(key))throw new IllegalArgumentException();}
        catch(IllegalArgumentException ex){throw new IllegalArgumentException("收银请求号格式不正确");}
    }
    private Result result(Submission r){return new Result(r.requestKey(),r.status(),r.orderId());}
    private String hash(POSOrderCreateCommand command) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build().writeValueAsBytes(command)));}
        catch(Exception ex){throw new IllegalArgumentException("无法核对收银请求内容",ex);}
    }
}

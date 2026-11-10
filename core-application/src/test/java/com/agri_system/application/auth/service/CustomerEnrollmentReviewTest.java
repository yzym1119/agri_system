package com.agri_system.application.auth.service;

import com.agri_system.domain.auth.model.CustomerIdentityEnrollment;
import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.repository.CustomerIdentityEnrollmentRepository;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.model.Staff;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerEnrollmentReviewTest {
    final CustomerIdentityEnrollmentRepository requests=mock(CustomerIdentityEnrollmentRepository.class);
    final CustomerIdentityRepository identities=mock(CustomerIdentityRepository.class);
    final CustomerRepository customers=mock(CustomerRepository.class);
    final StaffRepository staff=mock(StaffRepository.class);
    CustomerEnrollmentReviewService service(){return new CustomerEnrollmentReviewService(requests,identities,customers,staff);}
    CustomerIdentityEnrollment pending(){
        var row=new CustomerIdentityEnrollment();row.setId(7L);row.setStatus("REVIEW_REQUIRED");row.setRequestKey("11111111-1111-4111-8111-111111111111");row.setAppId("wx1111111111111111");row.setOpenId("IT_OPEN");row.setPhone("13800000000");row.setActiveSubject("scope");
        when(requests.findById(7L,true)).thenReturn(Optional.of(row));when(requests.findById(7L,false)).thenReturn(Optional.of(row));
        for(long id:new long[]{1,2}){var actor=new Staff();actor.setId(id);actor.setStatus(1);actor.setDeleted(0);when(staff.findIdentityById(id)).thenReturn(Optional.of(actor));}
        return row;
    }
    CustomerEnrollmentReviewService.Decision link(String reason){return new CustomerEnrollmentReviewService.Decision("22222222-2222-4222-8222-222222222222","LINK",99L,"IN_PERSON",reason);}
    @Test void verifiedHumanDecisionBindsOnceAndReplaysOnlyExactActorAndContent(){
        var row=pending();var target=new Customer();target.setId(99L);target.setStatus(1);target.setDeleted(0);target.setPhone("13900000000");when(customers.findByIdWithLock(99L)).thenReturn(Optional.of(target));
        when(identities.save(any())).thenAnswer(call->{CustomerIdentity i=call.getArgument(0);i.setId(8L);i.setAuthVersion(1L);return i;});
        assertEquals("LINKED",service().decide(1L,7L,link("当面核验确认同人")).status());
        assertEquals("LINKED",service().decide(1L,7L,link("当面核验确认同人")).status());
        assertThrows(IllegalArgumentException.class,()->service().decide(2L,7L,link("当面核验确认同人")));
        assertThrows(IllegalArgumentException.class,()->service().decide(1L,7L,link("改了原因")));
        verify(identities,times(1)).save(any());verify(requests,times(1)).claimDecision(row);assertEquals(1L,row.getReviewedBy());assertEquals("13900000000",target.getPhone());verify(customers,never()).save(any());
    }
    @Test void rejectionRecordsReasonWithoutTouchingIdentityOrCustomer(){
        var row=pending();var decision=new CustomerEnrollmentReviewService.Decision("22222222-2222-4222-8222-222222222222","REJECT",null,"UNCONFIRMED","无法核验为同一人");
        assertEquals("REJECTED",service().decide(1L,7L,decision).status());assertNull(row.getActiveSubject());verifyNoInteractions(identities,customers);
    }
    @Test void disabledTargetOrExistingWechatMappingNeverGetsOverwritten(){
        pending();var target=new Customer();target.setId(99L);target.setStatus(2);when(customers.findByIdWithLock(99L)).thenReturn(Optional.of(target));
        assertThrows(IllegalArgumentException.class,()->service().decide(1L,7L,link("当面确认")));verify(identities,never()).save(any());
        pending();target.setStatus(1);when(identities.findWechatByCustomerAndApp(99L,"wx1111111111111111")).thenReturn(List.of(new CustomerIdentity()));
        assertThrows(IllegalArgumentException.class,()->service().decide(1L,7L,link("当面确认")));verify(identities,never()).save(any());
    }
    @Test void originalDecisionLookupDistinguishesUnknownOwnResultAndOtherOperators(){
        var row=pending();String key=link("核验").requestKey();
        var unknown=service().decisionResult(1L,7L,key);assertEquals("UNKNOWN",unknown.status());assertEquals(key,unknown.requestKey());assertNull(unknown.reason());
        var reject=new CustomerEnrollmentReviewService.Decision(key,"REJECT",null,"UNCONFIRMED","无法确认同人");service().decide(1L,7L,reject);
        var result=service().decisionResult(1L,7L,key);assertEquals("REJECTED",result.status());assertEquals("7",result.enrollmentId());assertEquals("REJECT",result.action());assertEquals("无法确认同人",result.reason());assertNull(result.targetCustomerId());
        var foreign=service().decisionResult(2L,7L,key);assertEquals("CONFLICT",foreign.status());assertNull(foreign.reason());
        assertEquals("CONFLICT",service().decisionResult(1L,7L,"33333333-3333-4333-8333-333333333333").status());
        verifyNoInteractions(identities,customers);verify(requests,times(1)).finishDecision(row);
    }
    @Test void corruptOrIncompleteFinalAuditCannotBeReportedAsConfirmedDecision(){
        var row=pending();var reject=new CustomerEnrollmentReviewService.Decision(link("核验").requestKey(),"REJECT",null,"UNCONFIRMED","无法确认");service().decide(1L,7L,reject);
        row.setDecisionReason("与原决定摘要不符");assertThrows(IllegalStateException.class,()->service().decisionResult(1L,7L,reject.requestKey()));
        assertThrows(IllegalArgumentException.class,()->service().decisionResult(1L,7L,"invalid"));
    }
    @Test void candidateSearchRequiresSpecificKeywordAndReturnsOnlyMaskedBasicFields(){
        pending();var candidate=new Customer();candidate.setId(99L);candidate.setName("原客户");candidate.setPhone("+86 138-0000-0000");candidate.setStatus(1);candidate.setDeleted(0);
        when(customers.findActiveByKeyword("原客户",20)).thenReturn(List.of(candidate));
        assertThrows(IllegalArgumentException.class,()->service().searchCandidates(1L," "));
        var result=service().searchCandidates(1L," 原客户 ");assertEquals(1,result.size());assertEquals("99",result.get(0).id());assertEquals("138****0000",result.get(0).phoneMasked());
        verify(customers,never()).findById(any());verifyNoInteractions(identities);
    }
}

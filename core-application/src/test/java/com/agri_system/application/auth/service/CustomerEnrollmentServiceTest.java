package com.agri_system.application.auth.service;

import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.model.CustomerIdentityEnrollment;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.domain.auth.repository.CustomerIdentityEnrollmentRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.application.customer.service.CustomerRegistrationService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerEnrollmentServiceTest {
    final ClientOnboardingService onboarding=mock(ClientOnboardingService.class);
    final CustomerIdentityEnrollmentRepository requests=mock(CustomerIdentityEnrollmentRepository.class);
    final CustomerIdentityRepository identities=mock(CustomerIdentityRepository.class);
    final CustomerRepository customers=mock(CustomerRepository.class);
    final CustomerRegistrationService registration=mock(CustomerRegistrationService.class);
    CustomerEnrollmentService service(){var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());return new CustomerEnrollmentService(onboarding,requests,identities,customers,registration,manager);}
    CustomerEnrollmentService.Register command(String name){return new CustomerEnrollmentService.Register("11111111-1111-4111-8111-111111111111",name);}
    void proof(){when(onboarding.requireRegistrationSubject()).thenReturn(new ClientOnboardingService.RegistrationSubject("wx1111111111111111","IT_OPEN"));when(onboarding.requireVerifiedPhone()).thenReturn(new ClientOnboardingService.VerifiedPhone("wx1111111111111111","IT_OPEN","13800000000",java.time.Instant.now().getEpochSecond()));}
    void acquire(){when(requests.acquire(any())).thenAnswer(call->{CustomerIdentityEnrollment row=call.getArgument(0);row.setId(7L);return row;});}
    @Test void anyPhoneMatchIncludingInactiveOrDeletedOnlyCreatesReviewAndNeverAnEmptyDuplicate(){
        proof();acquire();when(customers.findIdsByPhoneIncludingDeleted("13800000000")).thenReturn(List.of(99L));
        var result=service().register(command("张三"));assertEquals("REVIEW_REQUIRED",result.status());assertEquals("CONTACT_STORE",result.nextAction());
        verifyNoInteractions(registration);verify(identities,never()).save(any());
    }
    @Test void genuinelyUnmatchedPhoneReusesCustomerCreatorAndStoresRealAppScopedIdentity(){
        proof();acquire();var customer=new Customer();customer.setId(99L);when(registration.create(any())).thenReturn(customer);
        when(identities.save(any())).thenAnswer(call->{CustomerIdentity row=call.getArgument(0);row.setId(8L);row.setAuthVersion(1L);return row;});
        var result=service().register(command(null));assertEquals("CREATED",result.status());assertEquals("LOGIN",result.nextAction());
        var args=org.mockito.ArgumentCaptor.forClass(com.agri_system.application.customer.dto.CustomerCreateCommand.class);verify(registration).create(args.capture());
        assertEquals("13800000000",args.getValue().getPhone());assertEquals("微信客户",args.getValue().getName());assertNull(args.getValue().getCreatedBy());assertNull(args.getValue().getBindStaffId());
        var identity=org.mockito.ArgumentCaptor.forClass(CustomerIdentity.class);verify(identities).save(identity.capture());assertEquals("wx1111111111111111",identity.getValue().getAppId());assertEquals(99L,identity.getValue().getCustomerId());assertEquals("IT_OPEN",identity.getValue().getIdentityValue());
    }
    @Test void fixedRequestContentIsNotReplacedByRetryAndForeignSubjectCannotReadItsResult(){
        proof();when(requests.acquire(any())).thenAnswer(call->{CustomerIdentityEnrollment supplied=call.getArgument(0);supplied.setDisplayName("原姓名");return supplied;});
        assertThrows(IllegalArgumentException.class,()->service().register(command("另一姓名")));verifyNoInteractions(registration);
        var foreign=new CustomerIdentityEnrollment();foreign.setAppId("wx1111111111111111");foreign.setOpenId("OTHER");foreign.setStatus("REVIEW_REQUIRED");when(requests.findByRequestKey(anyString(),eq(false))).thenReturn(Optional.of(foreign));
        assertThrows(IllegalArgumentException.class,()->service().result(command(null).requestKey()));
    }
    @Test void currentlyBoundIdentityDoesNotCreateAnotherCustomerFromOldPhoneProof(){
        proof();acquire();when(identities.findWechatByAppAndOpenIdForUpdate(anyString(),anyString())).thenReturn(Optional.of(new CustomerIdentity()));
        assertThrows(IllegalArgumentException.class,()->service().register(command(null)));verifyNoInteractions(registration);verify(identities,never()).save(any());
    }
    @Test void onlyConfirmedDeadlockRollbackRetriesOriginalContentAndUnknownCommitNeverRetries(){
        proof();when(customers.findIdsByPhoneIncludingDeleted("13800000000")).thenReturn(List.of(99L));
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);when(manager.getTransaction(any())).thenAnswer(call->new org.springframework.transaction.support.SimpleTransactionStatus());
        var service=new CustomerEnrollmentService(onboarding,requests,identities,customers,registration,manager);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        when(requests.acquire(any())).thenAnswer(call->{if(calls.getAndIncrement()==0)throw new org.springframework.dao.CannotAcquireLockException("IT_DEADLOCK",new java.sql.SQLException("IT_DEADLOCK","40001",1213));CustomerIdentityEnrollment row=call.getArgument(0);row.setId(7L);return row;});
        assertEquals("REVIEW_REQUIRED",service.register(command("原姓名")).status());assertEquals(2,calls.get());verify(manager,times(1)).rollback(any());verify(manager,times(1)).commit(any());
        reset(requests,manager);when(manager.getTransaction(any())).thenAnswer(call->new org.springframework.transaction.support.SimpleTransactionStatus());
        when(requests.acquire(any())).thenAnswer(call->{CustomerIdentityEnrollment row=call.getArgument(0);row.setId(7L);return row;});
        doThrow(new org.springframework.transaction.TransactionSystemException("IT_UNKNOWN_COMMIT",new java.sql.SQLException("IT_CONNECTION_LOST","08S01",0))).when(manager).commit(any());
        assertThrows(org.springframework.transaction.TransactionSystemException.class,()->service.register(command("原姓名")));verify(requests,times(1)).acquire(any());verify(manager,times(1)).commit(any());
    }

    @Test void nativeFlexOuterTransactionMustAlsoBeRejectedBeforeAnyRegistrationWork(){
        proof();acquire();when(customers.findIdsByPhoneIncludingDeleted("13800000000")).thenReturn(List.of(99L));
        String xid=com.mybatisflex.core.transaction.TransactionalManager.startTransactional();
        try{assertThrows(IllegalStateException.class,()->service().register(command("姓名")));}
        finally{com.mybatisflex.core.transaction.TransactionalManager.rollback(xid);}
    }

}

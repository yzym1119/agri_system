package com.agri_system.api.client.config;

import com.agri_system.application.auth.service.AuthService;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantRepository;
import com.agri_system.domain.agronomy.model.AgronomyCase;
import com.agri_system.domain.agronomy.repository.AgronomyCaseRepository;
import com.agri_system.domain.auth.model.ClientAuthorization;
import com.agri_system.domain.customer.model.CustomerAuthState;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgronomyAssistantConfigurationTest {
    @Test void defaultOffAndMissingExplicitQuotaPreventAccidentalActivation() {
        var cases=mock(AgronomyCaseRepository.class);var requests=mock(AgronomyAssistantRepository.class);var stores=mock(StorePublicationRepository.class);
        var customers=mock(CustomerRepository.class);var auth=mock(AuthService.class);var tx=mock(PlatformTransactionManager.class);
        var authority=new ClientAuthorization(101,1,"DEV",null,null,null,UUID.randomUUID().toString());
        when(auth.isCurrentClientAuthorization(authority)).thenReturn(true);
        when(customers.findAuthStateById(101L)).thenReturn(Optional.of(new CustomerAuthState(101L,1,0,1L)));
        var at=LocalDateTime.of(2026,11,11,12,0);
        when(cases.findOwned(7L,101L,false)).thenReturn(Optional.of(new AgronomyCase(7L,101L,1L,UUID.randomUUID().toString(),"0".repeat(64),"test",new AgronomyCase.Facts("","",""),"COLLECTING",1,1,null,at,at)));
        var env=new MockEnvironment();var factory=new AgronomyAssistantConfiguration();
        var c=factory.coordinator(cases,requests,stores,customers,auth,tx,new ObjectMapper(),env);
        assertEquals("DISABLED",c.capability(authority,7L).reason());verifyNoInteractions(requests,stores,tx);
        env.withProperty("agronomy.assistant.enabled","true");
        assertThrows(IllegalStateException.class,()->factory.coordinator(cases,requests,stores,customers,auth,tx,new ObjectMapper(),env));
    }
}

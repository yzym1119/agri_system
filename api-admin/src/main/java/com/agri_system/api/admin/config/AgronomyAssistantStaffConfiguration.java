package com.agri_system.api.admin.config;

import com.agri_system.application.agronomy.service.AgronomyAssistantCoordinator;
import com.agri_system.application.agronomy.service.AgronomyAssistantStaffViewService;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantWorkflow;
import com.agri_system.application.agronomy.service.AgronomyCaseService;
import com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy;
import com.agri_system.application.auth.service.AuthService;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantRepository;
import com.agri_system.domain.agronomy.repository.AgronomyCaseRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** 后台只装配只读AI参考视图：固定关闭的额度策略与拒绝执行的流程，后台不能派出模型或搜索。 */
@Configuration
public class AgronomyAssistantStaffConfiguration {
    @Bean public AgronomyAssistantStaffViewService assistantStaffView(AgronomyCaseService cases,AgronomyCaseRepository rows,
            AgronomyAssistantRepository requests,StorePublicationRepository stores,CustomerRepository customers,AuthService auth,
            PlatformTransactionManager manager,ObjectMapper json) {
        AgronomyAssistantWorkflow refused=(snapshot,calls)->{throw new IllegalStateException("后台不派出AI");};
        var readOnly=new AgronomyAssistantCoordinator(rows,requests,stores,customers,auth,manager,json,Clock.systemUTC(),AssistantBudgetPolicy.disabled(),refused);
        return new AgronomyAssistantStaffViewService(cases,readOnly);
    }
}

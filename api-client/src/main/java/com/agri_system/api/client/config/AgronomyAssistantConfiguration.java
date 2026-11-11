package com.agri_system.api.client.config;

import com.agri_system.application.agronomy.service.AgronomyAssistantCoordinator;
import com.agri_system.application.agronomy.service.ControlledAgronomyAssistantWorkflow;
import com.agri_system.application.auth.service.AuthService;
import com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantWorkflow;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantRepository;
import com.agri_system.domain.agronomy.repository.AgronomyCaseRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.agri_system.infrastructure.agronomy.assistant.DeepSeekAssistantGateway;
import com.agri_system.infrastructure.agronomy.assistant.DeepSeekAssistantSettings;
import com.agri_system.infrastructure.agronomy.assistant.OfficialAgronomySourceGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;
import java.time.Duration;

/** 默认只允许原结果读取；显式配置完整额度/计价及两把密钥后才装配真实外部网关。 */
@Configuration
public class AgronomyAssistantConfiguration {
    @Bean public com.agri_system.application.agronomy.service.AgronomyAssistantAccessService assistantAccessService(AuthService auth,AgronomyAssistantCoordinator coordinator) {
        return new com.agri_system.application.agronomy.service.AgronomyAssistantAccessService(auth,coordinator);
    }
    @Bean public AgronomyAssistantCoordinator coordinator(AgronomyCaseRepository cases,AgronomyAssistantRepository requests,
            StorePublicationRepository stores,CustomerRepository customers,AuthService auth,PlatformTransactionManager manager,
            ObjectMapper json,Environment env) {
        var clock=Clock.systemUTC();AssistantBudgetPolicy policy=AssistantBudgetPolicy.disabled();
        AgronomyAssistantWorkflow workflow=(snapshot,calls)->{throw new IllegalStateException("AI尚未启用");};
        if(env.getProperty("agronomy.assistant.enabled",Boolean.class,false)) {
            policy=new AssistantBudgetPolicy(true,number(env,"daily-model-calls"),number(env,"daily-search-calls"),number(env,"daily-microusd"),
                env.getProperty("agronomy.assistant.model","deepseek-flash"),true,env.getProperty("agronomy.assistant.max-output-tokens",Integer.class,1024),
                number(env,"input-microusd-per-million"),number(env,"output-microusd-per-million"),number(env,"search-microusd"),env.getRequiredProperty("agronomy.assistant.price-version"));
            policy.requireEnabled();policy.modelReservation();
            String modelKey=key(env,"generation-key"),searchKey=key(env,"search-key");
            var gateway=new DeepSeekAssistantGateway(json,new DeepSeekAssistantSettings(true,true,policy.model(),modelKey,searchKey,policy.maxOutputTokens(),Duration.ofSeconds(15)));
            workflow=new ControlledAgronomyAssistantWorkflow(json,clock,policy.execution(),gateway,gateway,new OfficialAgronomySourceGateway(clock,true));
        }
        return new AgronomyAssistantCoordinator(cases,requests,stores,customers,auth,manager,json,clock,policy,workflow);
    }
    private static long number(Environment env,String name) {return env.getRequiredProperty("agronomy.assistant."+name,Long.class);}
    private static String key(Environment env,String name) {
        String value=env.getProperty("agronomy.assistant."+name);
        if(value==null||value.length()>2048||!value.matches("[A-Za-z0-9._~+/-]+=*"))throw new IllegalArgumentException("AI密钥配置无效");
        return value;
    }
}

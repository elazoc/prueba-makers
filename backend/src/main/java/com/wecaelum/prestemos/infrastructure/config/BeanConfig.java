package com.wecaelum.prestemos.infrastructure.config;

import com.wecaelum.prestemos.application.port.out.DomainEventPublisher;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.application.port.out.PasswordHasher;
import com.wecaelum.prestemos.application.port.out.TokenIssuer;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.application.service.AuthService;
import com.wecaelum.prestemos.application.service.LoanCommandService;
import com.wecaelum.prestemos.application.service.LoanQueryService;
import com.wecaelum.prestemos.application.service.UserService;
import com.wecaelum.prestemos.domain.policy.LoanPolicy;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the application services, which carry no Spring stereotypes. Each bean is typed by its class so
 * every use-case interface it implements can be injected.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoanPolicyProperties.class)
public class BeanConfig {

    @Bean
    public LoanPolicy loanPolicy(LoanPolicyProperties properties) {
        return properties.toPolicy();
    }

    @Bean
    public LoanCommandService loanCommandService(
            LoanRepository loans, DomainEventPublisher events, LoanPolicy policy, Clock clock) {
        return new LoanCommandService(loans, events, policy, clock);
    }

    @Bean
    public LoanQueryService loanQueryService(LoanRepository loans) {
        return new LoanQueryService(loans);
    }

    @Bean
    public UserService userService(
            UserRepository users, PasswordHasher hasher, DomainEventPublisher events, Clock clock) {
        return new UserService(users, hasher, events, clock);
    }

    @Bean
    public AuthService authService(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        return new AuthService(users, hasher, tokens);
    }
}

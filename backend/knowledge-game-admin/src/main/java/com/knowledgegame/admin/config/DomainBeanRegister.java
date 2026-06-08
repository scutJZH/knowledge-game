package com.knowledgegame.admin.config;

import com.knowledgegame.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.domain.service.CardTemplateDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域服务 Bean 注册（纯 POJO 领域服务需通过 @Bean 注册到 Spring 容器）
 */
@Configuration
public class DomainBeanRegister {

    @Bean
    public CardTemplateDomainService cardTemplateDomainService(IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        return new CardTemplateDomainService(ipSeriesRepositoryPort);
    }
}

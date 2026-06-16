package com.knowledgegame.core.config;

import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.domain.service.KnowledgeCategoryDomainService;
import com.knowledgegame.core.domain.service.QuestionDomainService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Core 模块自动配置类
 * <p>
 * 注册领域服务 Bean + 扫描基础设施层 Repository 适配器 + JPA Repository + Entity。
 * 通过 Spring Boot 3.x AutoConfiguration.imports 自动加载。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.knowledgegame.core.infrastructure")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
public class KnowledgeGameCoreAutoConfiguration {

    /**
     * 注册卡牌模板领域服务（纯 POJO，需手动注册）
     */
    @Bean
    public CardTemplateDomainService cardTemplateDomainService(IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        return new CardTemplateDomainService(ipSeriesRepositoryPort);
    }

    /**
     * 注册知识点分类领域服务（纯 POJO，需手动注册）
     */
    @Bean
    public KnowledgeCategoryDomainService knowledgeCategoryDomainService(
            KnowledgeCategoryRepositoryPort categoryRepositoryPort,
            QuestionRepository questionRepository) {
        return new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository);
    }

    /**
     * 注册 IP 系列领域服务（跨聚合校验，纯 POJO，需手动注册）
     */
    @Bean
    public IpSeriesDomainService ipSeriesDomainService(CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                                        IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        return new IpSeriesDomainService(cardTemplateRepositoryPort, ipSeriesRepositoryPort);
    }

    /**
     * 注册题目领域服务（纯 POJO，需手动注册）
     */
    @Bean
    public QuestionDomainService questionDomainService(QuestionRepository questionRepository) {
        return new QuestionDomainService(questionRepository);
    }
}

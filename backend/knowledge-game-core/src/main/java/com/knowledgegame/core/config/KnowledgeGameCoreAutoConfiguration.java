package com.knowledgegame.core.config;

import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.port.outbound.PointTransactionRepository;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.domain.service.KnowledgeCategoryDomainService;
import com.knowledgegame.core.domain.service.KnowledgeItemDomainService;
import com.knowledgegame.core.domain.service.PointTransactionService;
import com.knowledgegame.core.domain.service.QuestionDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;

import java.util.List;
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
            QuestionRepository questionRepository,
            KnowledgeItemRepository itemRepository) {
        return new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
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

    /**
     * 注册知识条目领域服务（纯 POJO，需手动注册）
     */
    @Bean
    public KnowledgeItemDomainService knowledgeItemDomainService(
            KnowledgeItemRepository itemRepository,
            KnowledgeCategoryRepositoryPort categoryRepositoryPort) {
        return new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
    }

    /**
     * 注册积分流水领域服务（纯 POJO，需手动注册）
     */
    @Bean
    public PointTransactionService pointTransactionService(
            GroupMemberRepository groupMemberRepository,
            PointTransactionRepository pointTransactionRepository) {
        return new PointTransactionService(groupMemberRepository, pointTransactionRepository);
    }

    /**
     * 注册回收站策略注册中心（自动发现所有 RecycleBinItemStrategy Bean）
     * <p>
     * 本需求交付时 List 为空（无策略 Bean），后续 REQ-104~108 新增策略 Bean 时自动注入。
     */
    @Bean
    public RecycleBinItemStrategyRegistry recycleBinItemStrategyRegistry(
            List<RecycleBinItemStrategy<?>> strategies) {
        return new RecycleBinItemStrategyRegistry(strategies);
    }
}

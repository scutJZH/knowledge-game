package com.knowledgegame.admin.config;

import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.CardTemplateRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.IpSeriesRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.KnowledgeCategoryRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.KnowledgeItemRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.QuestionRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 回收站策略注册（admin 模块）
 * <p>
 * core 模块的策略类不标记 @Component，由 admin 模块在此显式注册 @Bean。
 * 只有提供了 FileCleanupPort 实现的 admin 模块才创建该策略 Bean，app 模块不受影响。
 */
@Configuration
public class RecycleBinConfig {

    @Bean
    public IpSeriesRecycleBinStrategy ipSeriesRecycleBinStrategy(
            IpSeriesDomainService ipSeriesDomainService,
            IpSeriesRepositoryPort ipSeriesRepositoryPort,
            RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
            IpSeriesJpaRepository ipSeriesJpaRepository,
            IpSeriesDeletedJpaRepository ipSeriesDeletedJpaRepository,
            RecycleBinItemJpaRepository recycleBinItemJpaRepository,
            FileCleanupPort fileCleanupPort) {
        return new IpSeriesRecycleBinStrategy(
                ipSeriesDomainService,
                ipSeriesRepositoryPort,
                recycleBinItemRepositoryPort,
                ipSeriesJpaRepository,
                ipSeriesDeletedJpaRepository,
                recycleBinItemJpaRepository,
                fileCleanupPort);
    }

    @Bean
    public CardTemplateRecycleBinStrategy cardTemplateRecycleBinStrategy(
            CardTemplateRepositoryPort cardTemplateRepositoryPort,
            RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
            CardTemplateJpaRepository cardTemplateJpaRepository,
            CardTemplateDeletedJpaRepository cardTemplateDeletedJpaRepository,
            RecycleBinItemJpaRepository recycleBinItemJpaRepository,
            IpSeriesRepositoryPort ipSeriesRepositoryPort,
            FileCleanupPort fileCleanupPort) {
        return new CardTemplateRecycleBinStrategy(
                cardTemplateRepositoryPort,
                recycleBinItemRepositoryPort,
                cardTemplateJpaRepository,
                cardTemplateDeletedJpaRepository,
                recycleBinItemJpaRepository,
                ipSeriesRepositoryPort,
                fileCleanupPort);
    }

    @Bean
    public KnowledgeCategoryRecycleBinStrategy knowledgeCategoryRecycleBinStrategy(
            KnowledgeCategoryRepositoryPort categoryRepositoryPort,
            QuestionRepository questionRepository,
            KnowledgeItemRepository itemRepository,
            RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
            KnowledgeCategoryJpaRepository categoryJpaRepository,
            KnowledgeCategoryDeletedJpaRepository deletedJpaRepository,
            RecycleBinItemJpaRepository recycleBinItemJpaRepository,
            FileCleanupPort fileCleanupPort) {
        return new KnowledgeCategoryRecycleBinStrategy(
                categoryRepositoryPort,
                questionRepository,
                itemRepository,
                recycleBinItemRepositoryPort,
                categoryJpaRepository,
                deletedJpaRepository,
                recycleBinItemJpaRepository,
                fileCleanupPort);
    }

    @Bean
    public QuestionRecycleBinStrategy questionRecycleBinStrategy(
            QuestionRepository questionRepository,
            RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
            QuestionJpaRepository questionJpaRepository,
            QuestionDeletedJpaRepository questionDeletedJpaRepository,
            QuestionCategoryRelationJpaRepository relationJpaRepository,
            RecycleBinItemJpaRepository recycleBinItemJpaRepository,
            KnowledgeCategoryJpaRepository categoryJpaRepository) {
        return new QuestionRecycleBinStrategy(
                questionRepository,
                recycleBinItemRepositoryPort,
                questionJpaRepository,
                questionDeletedJpaRepository,
                relationJpaRepository,
                recycleBinItemJpaRepository,
                categoryJpaRepository);
    }

    @Bean
    public KnowledgeItemRecycleBinStrategy knowledgeItemRecycleBinStrategy(
            KnowledgeItemRepository itemRepository,
            RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
            KnowledgeItemJpaRepository itemJpaRepository,
            KnowledgeItemDeletedJpaRepository itemDeletedJpaRepository,
            KnowledgeItemCategoryRelationJpaRepository relationJpaRepository,
            RecycleBinItemJpaRepository recycleBinItemJpaRepository,
            KnowledgeCategoryJpaRepository categoryJpaRepository,
            FileCleanupPort fileCleanupPort) {
        return new KnowledgeItemRecycleBinStrategy(
                itemRepository,
                recycleBinItemRepositoryPort,
                itemJpaRepository,
                itemDeletedJpaRepository,
                relationJpaRepository,
                recycleBinItemJpaRepository,
                categoryJpaRepository,
                fileCleanupPort);
    }
}

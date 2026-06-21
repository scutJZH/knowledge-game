package com.knowledgegame.admin.config;

import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.IpSeriesRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
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
}

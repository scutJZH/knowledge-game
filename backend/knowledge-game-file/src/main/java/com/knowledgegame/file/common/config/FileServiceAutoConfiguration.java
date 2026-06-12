package com.knowledgegame.file.common.config;

import com.knowledgegame.file.domain.port.outbound.FileInfoRepository;
import com.knowledgegame.file.domain.port.outbound.FileStorageProvider;
import com.knowledgegame.file.domain.service.UploadCredentialService;
import com.knowledgegame.file.application.FileAppService;
import com.knowledgegame.file.infrastructure.adapter.repoadapter.FileInfoRepositoryAdapter;
import com.knowledgegame.file.infrastructure.db.converter.FileInfoConverter;
import com.knowledgegame.file.infrastructure.db.repository.FileInfoJpaRepository;
import com.knowledgegame.file.infrastructure.storage.LocalFileStorageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 文件服务自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(FileProperties.class)
@EntityScan("com.knowledgegame.file.infrastructure.db.entity")
@EnableJpaRepositories("com.knowledgegame.file.infrastructure.db.repository")
public class FileServiceAutoConfiguration {

    @Bean
    public FileStorageProvider fileStorageProvider(FileProperties properties) {
        return new LocalFileStorageProvider(properties.getStorage().getLocal().getStorageDir());
    }

    @Bean
    public UploadCredentialService uploadCredentialService(FileProperties properties) {
        return new UploadCredentialService(properties.getCredential().getExpireMinutes());
    }

    @Bean
    public FileInfoRepository fileInfoRepository(FileInfoJpaRepository jpaRepository) {
        return new FileInfoRepositoryAdapter(jpaRepository, FileInfoConverter.INSTANCE);
    }

    @Bean
    public FileAppService fileAppService(FileStorageProvider storageProvider,
                                         FileInfoRepository fileInfoRepository,
                                         UploadCredentialService credentialService,
                                         FileProperties properties) {
        return new FileAppService(storageProvider, fileInfoRepository, credentialService, properties);
    }
}

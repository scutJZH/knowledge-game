package com.knowledgegame.app.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置
 * <p>
 * 注册 JsonNullableModule，支持 update 接口的三态语义：
 * - 字段缺失（undefined）：JsonNullable.undefined()，表示「不更新」
 * - 字段为 null：JsonNullable.of(null)，表示「清空」
 * - 字段有值：JsonNullable.of(value)，表示「更新为新值」
 * <p>
 * 详见 PRD REQ-88 / CLAUDE.md 的 Update API Null Semantics 章节。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonNullableCustomizer() {
        return builder -> builder.modulesToInstall(new JsonNullableModule());
    }
}

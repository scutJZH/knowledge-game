package com.knowledgegame.components.feign.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FeignAutoConfiguration 自动配置加载测试
 */
class FeignAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class));

	@Test
	void shouldLoadFeignAutoConfiguration() {
		contextRunner.run(context -> assertThat(context).hasSingleBean(FeignAutoConfiguration.class));
	}

	@Test
	void contextShouldStartWithoutFailure() {
		contextRunner.run(context -> assertThat(context).hasNotFailed());
	}
}

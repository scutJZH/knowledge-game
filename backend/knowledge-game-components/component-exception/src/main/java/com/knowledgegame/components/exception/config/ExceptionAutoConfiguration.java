package com.knowledgegame.components.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;

/**
 * 异常处理组件自动配置
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class ExceptionAutoConfiguration {
}

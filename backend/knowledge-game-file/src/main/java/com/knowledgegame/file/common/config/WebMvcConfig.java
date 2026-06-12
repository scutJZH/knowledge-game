package com.knowledgegame.file.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：静态资源映射 + CORS
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:*}")
    private String allowedOrigins;

    private final FileProperties fileProperties;

    public WebMvcConfig(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = fileProperties.getStorage().getLocal().getBasePath();
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:" + basePath + "/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/file/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

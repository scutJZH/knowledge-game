package com.knowledgegame.components.log.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志组件配置属性
 */
@ConfigurationProperties(prefix = "knowledgegame.log")
public class LogProperties {

    /**
     * 需要脱敏的字段名列表（不区分大小写匹配）
     */
    private List<String> maskingFields = new ArrayList<>();

    /**
     * 全遮掩字段名关键字（匹配到的字段值全部替换为 ***）
     */
    private List<String> fullMaskKeywords = List.of("password", "secret");

    public List<String> getMaskingFields() {
        return maskingFields;
    }

    public void setMaskingFields(List<String> maskingFields) {
        this.maskingFields = maskingFields;
    }

    public List<String> getFullMaskKeywords() {
        return fullMaskKeywords;
    }

    public void setFullMaskKeywords(List<String> fullMaskKeywords) {
        this.fullMaskKeywords = fullMaskKeywords;
    }
}

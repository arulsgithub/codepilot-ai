package com.codepilot.ai.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * Binds ai.models.<mode>=<provider>:<model>
 * e.g. ai.models.code=groq:openai/gpt-oss-120b
 * Spring binds the enum key case-insensitively, so "ai.models.code" -> ModelMode.CODE.
 */
@ConfigurationProperties(prefix = "ai")
public class ModelRoutingProperties {

    private Map<ModelMode, String> models = new EnumMap<>(ModelMode.class);

    public Map<ModelMode, String> getModels() { return models; }
    public void setModels(Map<ModelMode, String> models) { this.models = models; }
}
package com.codepilot;

import com.codepilot.ai.client.NemotronProperties;
import com.codepilot.ai.model.ModelRoutingProperties;
import com.codepilot.ai.model.ProviderProperties;
import com.codepilot.common.config.SecurityProperties;
import com.codepilot.repo.config.GitWorkspaceProperties;
import com.codepilot.retrieval.RetrievalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        NemotronProperties.class,
        ProviderProperties.class,
        ModelRoutingProperties.class,
        GitWorkspaceProperties.class,
        RetrievalProperties.class,
        SecurityProperties.class
})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
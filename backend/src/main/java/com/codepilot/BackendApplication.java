package com.codepilot;

import com.codepilot.ai.client.NemotronProperties;
import com.codepilot.ai.model.ModelRoutingProperties;
import com.codepilot.ai.model.ProviderProperties;
import com.codepilot.repo.config.GitWorkspaceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        NemotronProperties.class,
        ProviderProperties.class,
        ModelRoutingProperties.class,
        GitWorkspaceProperties.class
})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
package dev.akira.payflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow")
public record PayflowProperties(String environment) {
    
    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(environment);
    }
}

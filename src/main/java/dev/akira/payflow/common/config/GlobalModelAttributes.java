package dev.akira.payflow.common.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import dev.akira.payflow.config.PayflowProperties;

@ControllerAdvice
public class GlobalModelAttributes {
    
    private final PayflowProperties properties;

    public GlobalModelAttributes(PayflowProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute("sandboxMode")
    public boolean sandboxModel() {
        return properties.isSandbox();
    }
}

package dev.akira.payflow.common.config;

import dev.akira.payflow.config.PayflowProperties;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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

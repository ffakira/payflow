package dev.akira.payflow.common.error;

import java.util.Objects;

public class BusinessException extends PayflowException {
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(
            Objects.requireNonNull(errorCode, "Error code cannot be null"),
            Objects.requireNonNull(message, "Message cannot be null")
        );
    }
}

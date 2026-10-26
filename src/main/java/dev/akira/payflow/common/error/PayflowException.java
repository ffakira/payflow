package dev.akira.payflow.common.error;

public abstract class PayflowException extends RuntimeException {
    
    private final ErrorCode errorCode;

    protected PayflowException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}

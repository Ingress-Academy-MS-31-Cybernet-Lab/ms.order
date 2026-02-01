package az.ingress.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String message;

    public BusinessException(ErrorMessage errorMessage, Object... args) {
        super(String.format(errorMessage.getValue(), args));
        this.message = String.format(errorMessage.getValue(), args);
    }
}

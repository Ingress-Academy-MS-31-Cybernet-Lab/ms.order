package az.ingress.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorMessage {
    
    PROMO_NOT_FOUND("Promo not found for code: %s"),
    PROMO_SCOPE_MISMATCH("Promo scope mismatch. Expected: %s"),
    PROMO_MIN_AMOUNT_NOT_MET("Minimum amount requirements not met for promo: %s"),
    PROMO_NOT_APPLICABLE_TO_PRODUCT("Promo code %s is not applicable to product %s"),
    UNEXPECTED_ERROR("Unexpected error occurred");

    private final String value;
}
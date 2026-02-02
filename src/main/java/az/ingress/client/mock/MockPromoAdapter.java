package az.ingress.client.mock;

import az.ingress.client.promo.PromoClient;
import az.ingress.enums.DiscountScope;
import az.ingress.enums.DiscountType;
import az.ingress.model.client.PromoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "client.product.use-mock", havingValue = "true")
public class MockPromoAdapter implements PromoClient {

    private static final Map<String, PromoDto> PROMO_DB = new HashMap<>();

    @PostConstruct
    public void init() {
        // 1. GLOBAL PROMO
        PROMO_DB.put("YAY_KOMPANIYASI", PromoDto.builder()
                .code("YAY_KOMPANIYASI")
                .scope(DiscountScope.ORDER)
                .type(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10.00")) // 10%
                .minOrderAmount(new BigDecimal("100.00"))
                .build());

        // 2. ITEM PROMO
        PROMO_DB.put("APPLE_DEAL_10", PromoDto.builder()
                .code("APPLE_DEAL_10")
                .scope(DiscountScope.PRODUCT)
                .targetProductId("P-1001") // iPhone ID
                .type(DiscountType.FIXED)
                .value(new BigDecimal("50.00"))
                .minOrderAmount(new BigDecimal("0"))
                .build());

        log.info("MOCK Promo DB Initialized: {}", PROMO_DB.keySet());
    }

    @Override
    public PromoDto validateAndGetPromo(String code) {
        if (code == null) return null;
        return PROMO_DB.get(code);
    }
}
